package com.saveourtool.save.backend.controllers

import com.saveourtool.save.agent.TestExecutionDto
import com.saveourtool.save.agent.TestExecutionExtDto
import com.saveourtool.save.agent.TestSuiteExecutionStatisticDto
import com.saveourtool.save.backend.security.ProjectPermissionEvaluator
import com.saveourtool.save.backend.service.ExecutionService
import com.saveourtool.save.backend.service.TestAnalysisService
import com.saveourtool.save.backend.service.TestExecutionService
import com.saveourtool.save.backend.service.TestIdGeneratorService
import com.saveourtool.save.backend.storage.DebugInfoStorage
import com.saveourtool.save.backend.storage.ExecutionInfoStorage
import com.saveourtool.save.backend.utils.toMonoOrNotFound
import com.saveourtool.save.configs.ApiSwaggerSupport
import com.saveourtool.save.configs.RequiresAuthorizationSourceHeader
import com.saveourtool.save.core.utils.runIf
import com.saveourtool.save.domain.DebugInfoStorageKey
import com.saveourtool.save.domain.TestResultLocation
import com.saveourtool.save.domain.TestResultStatus
import com.saveourtool.save.entities.TestExecution
import com.saveourtool.save.filters.TestExecutionFilters
import com.saveourtool.save.from
import com.saveourtool.save.permission.Permission
import com.saveourtool.save.test.analysis.entities.metadata
import com.saveourtool.save.test.analysis.metrics.TestMetrics
import com.saveourtool.save.utils.blockingToMono
import com.saveourtool.save.utils.flatMapSequence
import com.saveourtool.save.utils.infiniteSequenceOf
import com.saveourtool.save.utils.mapLeft
import com.saveourtool.save.utils.mapRight
import com.saveourtool.save.utils.switchIfEmptyToNotFound
import com.saveourtool.save.utils.switchIfEmptyToResponseException
import com.saveourtool.save.v1

import arrow.core.plus
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.tags.Tags
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import reactor.kotlin.extra.bool.logicalOr

import java.math.BigInteger

/**
 * Controller to work with test execution
 *
 * @param testExecutionService service for test execution
 */
@ApiSwaggerSupport
@Tags(
    Tag(name = "test-executions"),
)
@RestController
@Transactional
@Suppress("LongParameterList")
class TestExecutionController(
    private val testExecutionService: TestExecutionService,
    private val executionService: ExecutionService,
    private val projectPermissionEvaluator: ProjectPermissionEvaluator,
    private val debugInfoStorage: DebugInfoStorage,
    private val executionInfoStorage: ExecutionInfoStorage,
    private val testAnalysisService: TestAnalysisService,
    private val testIdGeneratorService: TestIdGeneratorService,
) {
    /**
     * Returns a page of [TestExecutionDto]s with [executionId]
     *
     * @param executionId an ID of Execution to group TestExecutions
     * @param page a zero-based index of page of data
     * @param size size of page
     * @param filters
     * @param authentication
     * @param checkDebugInfo if true, response will contain information about whether debug info data is available for this test execution
     * @param testAnalysis if `true`, also perform test analysis.
     * @return a list of [TestExecutionDto]s
     */
    @PostMapping("/api/$v1/test-executions")
    @RequiresAuthorizationSourceHeader
    @Suppress("LongParameterList", "TOO_MANY_PARAMETERS", "TYPE_ALIAS")
    fun getTestExecutions(
        @RequestParam executionId: Long,
        @RequestParam page: Int,
        @RequestParam size: Int,
        @RequestBody(required = false) filters: TestExecutionFilters?,
        @RequestParam(required = false, defaultValue = "false") checkDebugInfo: Boolean,
        @RequestParam(required = false, defaultValue = "false") testAnalysis: Boolean,
        authentication: Authentication,
    ): Flux<TestExecutionExtDto> = blockingToMono {
        executionService.findExecution(executionId)
    }
        .switchIfEmptyToNotFound()
        .filterWhen {
            projectPermissionEvaluator.checkPermissions(authentication, it, Permission.READ)
        }
        .zipWhen { execution ->
            /*
             * Test analysis: collect execution metadata.
             */
            Mono.fromCallable(execution::metadata)
        }
        .flatMapSequence { (_, metadata) ->
            log.debug("Request to get test executions on page $page with size $size for execution $executionId")
            testExecutionService.getTestExecutions(executionId, page, size, filters ?: TestExecutionFilters.empty)
                .asSequence()
                .zip(infiniteSequenceOf(metadata))
        }
        .mapRight { testExecution, metadata ->
            /*
             * Test analysis: collect test execution metadata.
             */
            metadata.extendWith(testExecution)
        }
        .mapLeft(TestExecution::toDto)
        .runIf({ checkDebugInfo }) {
            flatMap { (testExecutionDto, metadata) ->
                debugInfoStorage.doesExist(DebugInfoStorageKey(executionId, TestResultLocation.from(testExecutionDto)))
                    .logicalOr(executionInfoStorage.doesExist(executionId))
                    .switchIfEmptyToResponseException(HttpStatus.INTERNAL_SERVER_ERROR) {
                        "Failure while checking for debug info availability."
                    }.map { hasDebugInfo ->
                        testExecutionDto.copy(hasDebugInfo = hasDebugInfo) to metadata
                    }
            }
        }
        .mapRight(testIdGeneratorService::testId)
        .run {
            when {
                /*
                 * Test analysis.
                 */
                testAnalysis -> switchMap { (testExecution, testId) ->
                    Mono.just(testExecution)
                        .zipWith(
                            testAnalysisService.getTestMetrics(testId),
                            ::Pair,
                        )
                        .zipWith(
                            testAnalysisService.analyze(testId).collectList(),
                            Pair<TestExecutionDto, TestMetrics>::plus,
                        )
                }.map { (testExecution, metrics, results) ->
                    testExecution.toExtended(testMetrics = metrics, analysisResults = results)
                }

                else -> map { (testExecution, _) ->
                    testExecution.toExtended()
                }
            }
        }

    /**
     * @param executionId an ID of Execution to group TestExecutions
     * @param status of test
     * @param page a zero-based index of page of data
     * @param size size of page
     * @param authentication
     * @return a list of [TestExecutionDto]s
     */
    @GetMapping(path = ["/api/$v1/testLatestExecutions"])
    @RequiresAuthorizationSourceHeader
    @Suppress("TYPE_ALIAS", "MagicNumber")
    fun getTestExecutionsByStatus(
        @RequestParam executionId: Long,
        @RequestParam status: TestResultStatus,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
        authentication: Authentication,
    ): Mono<List<TestSuiteExecutionStatisticDto>> =
            executionService.findExecution(executionId)
                .toMonoOrNotFound()
                .filterWhen {
                    projectPermissionEvaluator.checkPermissions(authentication, it, Permission.READ)
                }
                .mapNotNull {
                    if (page == null || size == null) {
                        testExecutionService.getTestExecutions(executionId).groupBy { it.test.testSuite.name }.map { (testSuiteName, testExecutions) ->
                            TestSuiteExecutionStatisticDto(testSuiteName, testExecutions.count(), testExecutions.count { it.status == status }, status)
                        }
                    } else {
                        testExecutionService.getByExecutionIdGroupByTestSuite(executionId, status, page, size)?.map {
                            TestSuiteExecutionStatisticDto(it[0] as String, (it[1] as BigInteger).toInt(), (it[2] as BigInteger).toInt(), TestResultStatus.valueOf(it[3] as String))
                        }
                    }
                }

    /**
     * Finds TestExecution by test location, returns 404 if not found
     *
     * @param executionId under this executionId test has been executed
     * @param testResultLocation location of the test
     * @param authentication
     * @return TestExecution
     */
    @PostMapping(path = ["/api/$v1/test-execution"])
    @RequiresAuthorizationSourceHeader
    fun getTestExecutionByLocation(@RequestParam executionId: Long,
                                   @RequestBody testResultLocation: TestResultLocation,
                                   authentication: Authentication,
    ): Mono<TestExecutionDto> = executionService.findExecution(executionId)
        .toMonoOrNotFound()
        .filterWhen {
            projectPermissionEvaluator.checkPermissions(authentication, it, Permission.READ)
        }
        .map {
            testExecutionService.getTestExecution(executionId, testResultLocation)
                .map { it.toDto() }
                .orElseThrow {
                    ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Test execution not found for executionId=$executionId and $testResultLocation"
                    )
                }
        }

    /**
     * Returns number of TestExecutions with this [executionId]
     *
     * @param executionId an ID of Execution to group TestExecutions
     * @param status
     * @param testSuite
     * @param authentication
     */
    @GetMapping(path = ["/api/$v1/testExecution/count"])
    @RequiresAuthorizationSourceHeader
    fun getTestExecutionsCount(
        @RequestParam executionId: Long,
        @RequestParam(required = false) status: TestResultStatus?,
        @RequestParam(required = false) testSuite: String?,
        authentication: Authentication,
    ) =
            executionService.findExecution(executionId)
                .toMonoOrNotFound()
                .filterWhen {
                    projectPermissionEvaluator.checkPermissions(authentication, it, Permission.READ)
                }
                .map {
                    testExecutionService.getTestExecutionsCount(executionId, status, testSuite)
                }

    /**
     * @param containerId id of agent's container
     * @param status status for test executions
     * @return a list of test executions
     */
    @GetMapping("/internal/test-executions/get-by-container-id")
    fun getTestExecutionsForAgentWithStatus(@RequestParam containerId: String,
                                            @RequestParam status: TestResultStatus
    ) = testExecutionService.getTestExecutions(containerId, status)
        .map { it.toDto() }

    /**
     * @param executionId
     */
    @PostMapping("/internal/test-executions/mark-all-as-failed-by-execution-id")
    fun markAllTestExecutionsOfExecutionAsFailed(
        @RequestParam executionId: Long,
    ) = testExecutionService.markAllTestExecutionsOfExecutionAsFailed(executionId)

    /**
     * @param containerId
     */
    @PostMapping("/internal/test-executions/mark-ready-for-testing-as-failed-by-container-id")
    fun markReadyForTestingTestExecutionsOfAgentAsFailed(
        @RequestBody containerId: String,
    ) = testExecutionService.markReadyForTestingTestExecutionsOfAgentAsFailed(containerId)

    /**
     * @param testExecutionsDto
     * @return response
     */
    @PostMapping(value = ["/internal/saveTestResult"])
    fun saveTestResult(@RequestBody testExecutionsDto: List<TestExecutionDto>): ResponseEntity<String> = try {
        if (testExecutionsDto.isEmpty()) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Empty result cannot be saved")
        } else if (testExecutionService.saveTestResult(testExecutionsDto).isEmpty()) {
            ResponseEntity.status(HttpStatus.OK).body("Saved")
        } else {
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Some ids don't exist or cannot be updated")
        }
    } catch (exception: DataAccessException) {
        log.warn("Unable to save test results", exception)
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error to save")
    }

    companion object {
        private val log = LoggerFactory.getLogger(TestExecutionController::class.java)
    }
}
