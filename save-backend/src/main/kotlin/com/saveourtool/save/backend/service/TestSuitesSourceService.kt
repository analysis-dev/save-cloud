package com.saveourtool.save.backend.service

import com.saveourtool.save.backend.EmptyResponse
import com.saveourtool.save.backend.StringList
import com.saveourtool.save.backend.configs.ConfigProperties
import com.saveourtool.save.backend.repository.TestSuitesSourceRepository
import com.saveourtool.save.domain.EntitySaveStatus
import com.saveourtool.save.entities.Git
import com.saveourtool.save.entities.Organization
import com.saveourtool.save.entities.TestSuitesSource
import com.saveourtool.save.testsuite.TestSuitesSourceDto
import com.saveourtool.save.testsuite.TestSuitesSourceFetchMode
import com.saveourtool.save.utils.orNotFound

import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

/**
 * Service for [com.saveourtool.save.entities.TestSuitesSource]
 */
@Service
class TestSuitesSourceService(
    private val testSuitesSourceRepository: TestSuitesSourceRepository,
    private val organizationService: OrganizationService,
    configProperties: ConfigProperties,
    jackson2WebClientCustomizer: WebClientCustomizer,
) {
    private val preprocessorWebClient = WebClient.builder()
        .apply(jackson2WebClientCustomizer::customize)
        .baseUrl(configProperties.preprocessorUrl)
        .build()

    /**
     * @param organization [TestSuitesSource.organization]
     * @return list of entities of [TestSuitesSource] or null
     */
    fun getAllByOrganization(organization: Organization) =
            testSuitesSourceRepository.findAllByOrganizationId(organization.requiredId())

    /**
     * @param id [TestSuitesSource.id]
     * @return entity of [TestSuitesSource] or null
     */
    fun findById(id: Long): TestSuitesSource? = testSuitesSourceRepository.findByIdOrNull(id)

    /**
     * @param organization [TestSuitesSource.organization]
     * @param name [TestSuitesSource.name]
     * @return entity of [TestSuitesSource] or null
     */
    fun findByName(organization: Organization, name: String) =
            testSuitesSourceRepository.findByOrganizationIdAndName(organization.requiredId(), name)

    /**
     * @param organizationName [TestSuitesSource.organization]
     * @param name [TestSuitesSource.name]
     * @return entity of [TestSuitesSource] or null
     */
    fun findByName(organizationName: String, name: String) =
            testSuitesSourceRepository.findByOrganizationIdAndName(organizationService.getByName(organizationName).requiredId(), name)

    /**
     * @param organizationName [Organization.name] from [TestSuitesSource.organization]
     * @param name [TestSuitesSource.name]
     * @return entity of [TestSuitesSource] or error
     */
    fun getByName(organizationName: String, name: String): TestSuitesSource = findByName(organizationName, name)
        .orNotFound {
            "TestSuitesSource not found by name $name in $organizationName"
        }

    /**
     * @param git
     * @return entity
     */
    fun findByGit(git: Git) = testSuitesSourceRepository.findAllByGit(git)

    /**
     * @param entity
     */
    @Transactional
    fun delete(entity: TestSuitesSource) = testSuitesSourceRepository.delete(entity)

    /**
     * Raw update
     *
     * @param entity [TestSuitesSource] to be updated
     * @return status of updating [TestSuitesSource]
     */
    @Transactional
    fun update(entity: TestSuitesSource): EntitySaveStatus {
        with(entity) {
            requireNotNull(id) {
                "Cannot update entity ($name in ${organization.name}) as it is not saved yet"
            }
        }
        return save(entity)
    }

    /**
     * @param entity
     * @return status of creating [TestSuitesSource]
     */
    @Transactional
    fun createSourceIfNotPresent(
        entity: TestSuitesSource,
    ): EntitySaveStatus {
        require(entity.id == null) {
            "Cannot create a new entity as it is saved already: $entity"
        }
        return save(entity)
    }

    private fun save(entity: TestSuitesSource): EntitySaveStatus {
        findByName(entity.organization, entity.name)?.run {
            if (entity.id != id) {
                return EntitySaveStatus.EXIST
            }
        }
        return try {
            val isUpdate = entity.id != null
            testSuitesSourceRepository.save(entity)
            if (isUpdate) {
                EntitySaveStatus.UPDATED
            } else {
                EntitySaveStatus.NEW
            }
        } catch (e: DataIntegrityViolationException) {
            EntitySaveStatus.CONFLICT
        }
    }

    /**
     * @return list of organizations that have open public test suite sources
     */
    fun getAvailableTestSuiteSources(): List<TestSuitesSource> = testSuitesSourceRepository.findAll()

    /**
     * @param testSuitesSource test suites source which requested to be fetched
     * @param mode mode of fetching, it controls how [version] is used
     * @param version tag, branch or commit (depends on [mode])
     * @return empty response
     */
    fun fetch(
        testSuitesSource: TestSuitesSourceDto,
        mode: TestSuitesSourceFetchMode,
        version: String,
    ): Mono<EmptyResponse> = preprocessorWebClient
        .post()
        .uri("/test-suites-sources/fetch?mode={mode}&version={version}", mode, version)
        .bodyValue(testSuitesSource)
        .retrieve()
        .toBodilessEntity()

    /**
     * @param testSuitesSource test suites source for which a list of tags is requested
     * @return list of all tags
     */
    fun tagList(
        testSuitesSource: TestSuitesSourceDto,
    ): Mono<StringList> = preprocessorWebClient
        .post()
        .uri("/git/tag-list")
        .bodyValue(testSuitesSource.gitDto)
        .retrieve()
        .bodyToMono()

    /**
     * @param testSuitesSource test suites source for which a list of branches is requested
     * @return list of all branches
     */
    fun branchList(
        testSuitesSource: TestSuitesSourceDto,
    ): Mono<StringList> = preprocessorWebClient
        .post()
        .uri("/git/branch-list")
        .bodyValue(testSuitesSource.gitDto)
        .retrieve()
        .bodyToMono()
}
