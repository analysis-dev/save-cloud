package com.saveourtool.save.cosv.storage

import com.saveourtool.save.cosv.repository.RawCosvFileRepository
import com.saveourtool.save.cosv.service.OrganizationService
import com.saveourtool.save.cosv.service.UserService
import com.saveourtool.save.entities.cosv.RawCosvFileDto
import com.saveourtool.save.entities.cosv.RawCosvFileStatus
import com.saveourtool.save.entitiescosv.RawCosvFile
import com.saveourtool.save.entitiescosv.RawCosvFile.Companion.toNewEntity
import com.saveourtool.save.s3.S3OperationsProperties
import com.saveourtool.save.storage.concatS3Key
import com.saveourtool.save.storage.key.AbstractS3KeyDtoManager
import com.saveourtool.save.utils.BlockingBridge
import com.saveourtool.save.utils.getByIdOrNotFound

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * S3KeyManager for [RawCosvFileStorage]
 */
@Component
class RawCosvFileS3KeyManager(
    s3OperationsPropertiesProvider: S3OperationsProperties.Provider,
    rawCosvFileRepository: RawCosvFileRepository,
    private val userService: UserService,
    private val organizationService: OrganizationService,
    blockingBridge: BlockingBridge,
) : AbstractS3KeyDtoManager<RawCosvFileDto, RawCosvFile, RawCosvFileRepository>(
    concatS3Key(s3OperationsPropertiesProvider.s3Storage.prefix, "raw-cosv"),
    rawCosvFileRepository,
    blockingBridge,
) {
    override fun findByDto(dto: RawCosvFileDto): RawCosvFile? {
        val organization = organizationService.getOrganizationByName(dto.organizationName)
        val user = userService.getUserByName(dto.userName)
        return repository.findByOrganizationIdAndUserIdAndFileName(
            organizationId = organization.requiredId(),
            userId = user.requiredId(),
            fileName = dto.fileName,
        )
    }

    override fun createNewEntityFromDto(dto: RawCosvFileDto): RawCosvFile =
            dto.toNewEntity({ userName -> userService.getUserByName(userName).requiredId() },
                { organizationName -> organizationService.getOrganizationByName(organizationName).requiredId() }
            )

    /**
     * @param organizationId
     * @param userId
     * @param pageRequest
     * @return all [RawCosvFileDto]s which has provided [RawCosvFileDto.organizationName] and [RawCosvFileDto.userName]
     */
    fun listByOrganizationAndUser(
        organizationId: Long,
        userId: Long,
        pageRequest: PageRequest? = null,
    ): Collection<RawCosvFileDto> = run {
        pageRequest?.let { repository.findAllByOrganizationIdAndUserId(organizationId, userId, it) }
            ?: repository.findAllByOrganizationIdAndUserId(organizationId, userId)
    }.map { it.toDto() }

    /**
     * @param ids
     * @param newStatus
     */
    @Transactional
    fun updateAll(
        ids: Collection<Long>,
        newStatus: RawCosvFileStatus,
    ) {
        repository.saveAll(repository.findAllById(ids).map { entry -> entry.apply { status = newStatus } })
    }

    /**
     * @param id
     * @param newStatus
     * @param statusMessage
     */
    @Transactional
    fun update(
        id: Long,
        newStatus: RawCosvFileStatus,
        statusMessage: String?,
    ) {
        repository.save(
            repository.getByIdOrNotFound(id).apply {
                status = newStatus
                statusMessage?.let { this.statusMessage = it }
            }
        )
    }

    /**
     * @param id
     * @return organizationId to which is uploaded and userId who uploaded
     */
    fun getOrganizationAndOwner(
        id: Long,
    ): Pair<Long, Long> = repository.getByIdOrNotFound(id).let {
        it.organizationId to it.userId
    }

    @Transactional
    override fun updateKeyByContentLength(
        key: RawCosvFileDto,
        contentLength: Long,
    ): RawCosvFileDto = key.contentLength
        ?.let { key }
        ?: run {
            repository.getByIdOrNotFound(key.requiredId())
                .let { entity ->
                    repository.save(entity.apply { this.contentLength = contentLength }).toDto()
                }
        }
}
