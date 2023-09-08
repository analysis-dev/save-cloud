package com.saveourtool.save.cosv.service

import com.saveourtool.save.backend.service.IBackendService
import com.saveourtool.save.cosv.processor.CosvProcessor
import com.saveourtool.save.cosv.repository.CosvRepository
import com.saveourtool.save.cosv.repository.CosvSchema
import com.saveourtool.save.cosv.utils.toJsonArrayOrSingle
import com.saveourtool.save.entities.Organization
import com.saveourtool.save.entities.User
import com.saveourtool.save.entities.cosv.CosvMetadataDto
import com.saveourtool.save.entities.vulnerability.*
import com.saveourtool.save.utils.*

import com.saveourtool.osv4k.*
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

import java.io.InputStream

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer

private typealias ManualCosvSchema = CosvSchema<Unit, Unit, Unit, Unit>

/**
 * Service for vulnerabilities
 */
@Service
class CosvService(
    private val cosvRepository: CosvRepository,
    private val backendService: IBackendService,
    private val cosvProcessor: CosvProcessor,
) {
    private val json = Json {
        prettyPrint = false
    }

    /**
     * Decodes [inputStreams] and saves the result
     *
     * @param inputStreams
     * @param authentication who uploads [inputStream]
     * @param organizationName to which is uploaded
     * @return vulnerability identifiers
     * @throws ResponseStatusException
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun decodeAndSave(
        inputStreams: Flux<InputStream>,
        authentication: Authentication,
        organizationName: String,
    ): Flux<String> {
        val user = backendService.getUserByName(authentication.name)
        val organization = backendService.getOrganizationByName(organizationName)
        val userPermissions = backendService.getUserPermissionsByOrganizationName(authentication, organizationName)

        if (userPermissions.inOrganizations[organizationName]?.canDoBulkUpload != true) {
            return Flux.error(ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to upload COSV files on behalf of this organization: $organizationName"))
        }

        return inputStreams.flatMap { inputStream ->
            decode(json.decodeFromStream<JsonElement>(inputStream), user, organization)
        }
    }

    /**
     * Decodes [content] and saves the result
     *
     * @param content
     * @param authentication who uploads [content]
     * @param organizationName to which is uploaded
     * @return vulnerability identifiers
     * @throws ResponseStatusException
     */
    fun decodeAndSave(
        content: String,
        authentication: Authentication,
        organizationName: String,
    ): Flux<String> {
        val user = backendService.getUserByName(authentication.name)
        val organization = backendService.getOrganizationByName(organizationName)
        val userPermissions = backendService.getUserPermissionsByOrganizationName(authentication, organizationName)

        if (userPermissions.inOrganizations[organizationName]?.canDoBulkUpload != true) {
            return Flux.error(ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to upload COSV file on behalf of this organization: $organizationName"))
        }

        return decode(json.parseToJsonElement(content), user, organization)
    }

    /**
     * Saves OSVs from [jsonElement] in COSV repository (S3 storage)
     *
     * @param jsonElement
     * @param user who uploads content
     * @param organization to which is uploaded
     * @return vulnerability identifier
     */
    private fun decode(
        jsonElement: JsonElement,
        user: User,
        organization: Organization,
    ): Flux<String> = jsonElement.toMono()
        .flatMapIterable { it.toJsonArrayOrSingle() }
        .flatMap { cosvProcessor.process(it.jsonObject, user, organization) }
        .map { it.cosvId }

    /**
     * Finds COSV
     *
     * @param cosvId [VulnerabilityDto.identifier]
     * @return found COSV
     */
    fun findById(
        cosvId: String,
    ): Mono<RawOsvSchema> = cosvRepository.findLatestById(cosvId, serializer<RawOsvSchema>())

    /**
     * Generates COSV from [VulnerabilityDto] and saves it
     *
     * @param vulnerabilityDto as a source for COSV
     * @return [CosvMetadataDto] saved metadata
     */
    fun generateAndSave(
        vulnerabilityDto: VulnerabilityDto,
    ): Mono<CosvMetadataDto> = blockingToMono {
        val user = backendService.getUserByName(vulnerabilityDto.userInfo.name)
        val organization = vulnerabilityDto.organization?.let { backendService.getOrganizationByName(it.name) }
        user to organization
    }.flatMap { (user, organization) ->
        val osv = ManualCosvSchema(
            id = vulnerabilityDto.identifier,
            published = (vulnerabilityDto.creationDateTime ?: getCurrentLocalDateTime()).truncatedToMills(),
            modified = (vulnerabilityDto.lastUpdatedDateTime ?: getCurrentLocalDateTime()).truncatedToMills(),
            severity = listOf(
                Severity(
                    type = SeverityType.CVSS_V3,
                    score = vulnerabilityDto.severity,
                    scoreNum = vulnerabilityDto.progress.toString(),
                )
            ),
            summary = vulnerabilityDto.shortDescription,
            details = vulnerabilityDto.description,
            references = vulnerabilityDto.relatedLink?.let { relatedLink ->
                listOf(
                    Reference(
                        type = ReferenceType.WEB,
                        url = relatedLink,
                    )
                )
            },
            credits = vulnerabilityDto.getAllParticipants().asCredits().takeUnless { it.isEmpty() },
        )
        cosvRepository.save(
            entry = osv,
            serializer = serializer(),
            user = user,
            organization = organization,
        )
    }

    /**
     * @param cosvId
     * @param updater
     * @return [Mono] with new metadata
     */
    fun update(
        cosvId: String,
        updater: (RawOsvSchema) -> Mono<RawOsvSchema>,
    ): Mono<CosvMetadataDto> = cosvRepository.findLatestRawExt(cosvId)
        .blockingMap { rawCosvExt ->
            rawCosvExt to Pair(
                backendService.getUserByName(rawCosvExt.metadata.user.name),
                rawCosvExt.metadata.organization?.let { organization ->
                    backendService.getOrganizationByName(organization.name)
                }
            )
        }
        .flatMap { (rawCosvExt, infoFromDatabase) ->
            val (owner, organization) = infoFromDatabase
            updater(rawCosvExt.cosv)
                .flatMap { newCosv ->
                    cosvRepository.save(
                        entry = newCosv.copy(modified = getCurrentLocalDateTime().truncatedToMills()),
                        serializer = serializer(),
                        user = owner,
                        organization = organization,
                    )
                }
        }
}
