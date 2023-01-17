package com.saveourtool.save.backend.service

import com.saveourtool.save.backend.repository.TestRepository
import com.saveourtool.save.backend.repository.TestSuiteRepository
import com.saveourtool.save.entities.TestSuite
import com.saveourtool.save.entities.TestSuitesSource
import com.saveourtool.save.execution.ExecutionStatus
import com.saveourtool.save.filters.TestSuiteFilters
import com.saveourtool.save.permission.Rights
import com.saveourtool.save.testsuite.TestSuiteDto
import com.saveourtool.save.utils.debug
import com.saveourtool.save.utils.orNotFound

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Example
import org.springframework.data.domain.ExampleMatcher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

import java.time.LocalDateTime

/**
 * Service for test suites
 */
@Service
@Suppress("LongParameterList")
class TestSuitesService(
    private val testSuiteRepository: TestSuiteRepository,
    private val testRepository: TestRepository,
    private val testSuitesSourceService: TestSuitesSourceService,
    private val lnkOrganizationTestSuiteService: LnkOrganizationTestSuiteService,
    private val lnkExecutionTestSuiteService: LnkExecutionTestSuiteService,
    private val executionService: ExecutionService,
) {
    /**
     * Save new test suites to DB
     *
     * @param testSuiteDto test suite that should be checked and possibly saved
     * @return saved [TestSuite]
     */
    @Transactional
    @Suppress("TOO_MANY_LINES_IN_LAMBDA", "UnsafeCallOnNullableType")
    fun saveTestSuite(testSuiteDto: TestSuiteDto): TestSuite {
        // FIXME: need to check logic about [dateAdded]
        // It's kind of upsert (insert or update) with key of all fields excluding [dateAdded]
        // This logic will be removed after https://github.com/saveourtool/save-cli/issues/429

        val testSuiteSourceVersion = testSuiteDto.version
        val testSuiteSource = testSuitesSourceService.getByName(testSuiteDto.source.organizationName, testSuiteDto.source.name)
            .apply {
                latestFetchedVersion = testSuiteSourceVersion
            }

        val testSuiteCandidate = TestSuite(
            name = testSuiteDto.name,
            description = testSuiteDto.description,
            source = testSuiteSource,
            version = testSuiteDto.version,
            dateAdded = null,
            language = testSuiteDto.language,
            tags = testSuiteDto.tags?.let(TestSuite::tagsFromList),
            plugins = TestSuite.pluginsByTypes(testSuiteDto.plugins)
        )
        // try to find TestSuite in the DB based on all non-null properties of `testSuite`
        // NB: that's why `dateAdded` is null in the mapping above
        val description = testSuiteCandidate.description
        val testSuite = testSuiteRepository
            .findOne(
                Example.of(testSuiteCandidate.apply { this.description = null })
            )
            .orElseGet {
                // if testSuite is not present in the DB, we will save it with current timestamp
                testSuiteCandidate.apply {
                    dateAdded = LocalDateTime.now()
                    this.description = description
                }
            }
        testSuiteRepository.save(testSuite)
        testSuitesSourceService.update(testSuiteSource)
        lnkOrganizationTestSuiteService.setOrDeleteRights(testSuiteSource.organization, testSuite, Rights.MAINTAIN)
        return testSuite
    }

    /**
     * @param id
     * @return test suite with [id]
     */
    fun findTestSuiteById(id: Long): TestSuite? = testSuiteRepository.findByIdOrNull(id)

    /**
     * @param ids
     * @return List of [TestSuite] by [ids]
     */
    fun findTestSuitesByIds(ids: List<Long>): List<TestSuite> = ids.mapNotNull { id ->
        testSuiteRepository.findByIdOrNull(id)
    }

    /**
     * @param filters
     * @return [List] of [TestSuite] that match [filters]
     */
    @Suppress("TOO_MANY_LINES_IN_LAMBDA")
    fun findTestSuitesMatchingFilters(filters: TestSuiteFilters): List<TestSuite> =
            ExampleMatcher.matchingAll()
                .withMatcher("name", ExampleMatcher.GenericPropertyMatchers.contains().ignoreCase())
                .withMatcher("language", ExampleMatcher.GenericPropertyMatchers.contains().ignoreCase())
                .withMatcher("tags", ExampleMatcher.GenericPropertyMatchers.contains().ignoreCase())
                .withIgnorePaths("description", "source", "version", "dateAdded", "plugins")
                .let {
                    Example.of(
                        TestSuite(
                            filters.name,
                            "",
                            TestSuitesSource.empty,
                            "",
                            null,
                            filters.language,
                            filters.tags
                        ),
                        it
                    )
                }
                .let { testSuiteRepository.findAll(it) }

    /**
     * @param id
     * @return test suite with [id]
     * @throws ResponseStatusException if [TestSuite] is not found by [id]
     */
    fun getById(id: Long) = testSuiteRepository.findByIdOrNull(id)
        .orNotFound { "TestSuite (id=$id) not found" }

    /**
     * @return public [TestSuite]s
     */
    fun getPublicTestSuites() = testSuiteRepository.findByIsPublic(true)

    /**
     * Creates copy of TestSuites found by provided values.
     * New copies have a new version.
     *
     * @param organizationName
     * @param sourceName
     * @param originalVersion
     * @param newVersion
     */
    @Suppress("TOO_MANY_LINES_IN_LAMBDA")
    fun copyToNewVersion(
        organizationName: String,
        sourceName: String,
        originalVersion: String,
        newVersion: String,
    ) {
        val existedTestSuites = testSuiteRepository.findAllBySource_Organization_NameAndSource_NameAndVersion(
            organizationName,
            sourceName,
            originalVersion
        )
        existedTestSuites.map {
            // a copy of existed one but with a new version
            TestSuite(
                name = it.name,
                description = it.description,
                source = it.source,
                version = newVersion,
                dateAdded = it.dateAdded,
                language = it.language,
                tags = it.tags,
                plugins = it.plugins,
                isPublic = it.isPublic,
            )
        }
            .let {
                testSuiteRepository.saveAll(it)
            }
    }

    /**
     * @param source source of the test suite
     * @param version version of snapshot of source
     * @return matched test suites
     */
    fun getBySourceAndVersion(
        source: TestSuitesSource,
        version: String
    ): List<TestSuite> = testSuiteRepository.findAllBySourceAndVersion(source, version)

    /**
     * @param source source of the test suite
     * @return matched test suites
     */
    fun getBySource(
        source: TestSuitesSource,
    ): List<TestSuite> = testSuiteRepository.findAllBySource(source)

    /**
     * Delete testSuites and related tests & test executions from DB
     *
     * @param testSuiteDtos suites, which need to be deleted
     */
    fun deleteTestSuitesDto(testSuiteDtos: List<TestSuiteDto>) {
        doDeleteTestSuite(testSuiteDtos.map { getSavedEntityByDto(it) })
    }

    /**
     * Delete testSuites and related tests & test executions from DB
     *
     * @param source
     * @param version
     */
    fun deleteTestSuite(source: TestSuitesSource, version: String) {
        doDeleteTestSuite(testSuiteRepository.findAllBySourceAndVersion(source, version))
    }

    private fun doDeleteTestSuite(testSuites: List<TestSuite>) {
        val executions = testSuites.flatMap { testSuite ->
            executionService.getExecutionsByTestSuiteId(testSuite.requiredId())
        }.distinctBy { it.requiredId() }
        val allTestSuiteIdsByExecutions = executions.flatMap { lnkExecutionTestSuiteService.getAllTestSuiteIdsByExecutionId(it.requiredId()) }
            .distinct()
            .size
        require(
            allTestSuiteIdsByExecutions == testSuites.size || allTestSuiteIdsByExecutions == 0
        ) {
            "Expected that we remove all test suites related to a single execution at once"
        }
        executions.forEach {
            executionService.updateExecutionStatus(it, ExecutionStatus.OBSOLETE)
        }

        testSuites.forEach { testSuite ->
            // Get test ids related to the current testSuiteId
            val testIds = testRepository.findAllByTestSuiteId(testSuite.requiredId()).map { it.requiredId() }
            testIds.forEach { testId ->
                // Delete tests
                log.debug { "Delete test with id $testId" }
                testRepository.deleteById(testId)
            }
            log.info("Delete test suite ${testSuite.name} with id ${testSuite.requiredId()}")
            testSuiteRepository.deleteById(testSuite.requiredId())
        }
    }

    private fun getSavedEntityByDto(
        dto: TestSuiteDto,
    ): TestSuite = testSuiteRepository.findByNameAndTagsAndSourceAndVersion(
        dto.name,
        dto.tags?.let(TestSuite::tagsFromList),
        testSuitesSourceService.getByName(dto.source.organizationName, dto.source.name),
        dto.version
    ).orNotFound { "TestSuite (name=${dto.name} in ${dto.source.name} with version ${dto.version}) not found" }

    /**
     * @param testSuites list of test suites to be updated
     * @return saved [testSuites]
     */
    fun updateTestSuites(testSuites: List<TestSuite>): List<TestSuite> = testSuiteRepository.saveAll(testSuites)

    companion object {
        private val log = LoggerFactory.getLogger(TestSuitesService::class.java)
    }
}
