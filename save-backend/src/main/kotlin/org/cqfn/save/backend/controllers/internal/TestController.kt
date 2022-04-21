package org.cqfn.save.backend.controllers.internal

import org.cqfn.save.backend.service.TestExecutionService
import org.cqfn.save.backend.service.TestService
import org.cqfn.save.entities.Test
import org.cqfn.save.test.TestDto
import org.cqfn.save.v1
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 *  Controller used to initialize tests
 */
@RestController
@RequestMapping("/internal")
class TestController(
    private val testService: TestService,
    private val testExecutionService: TestExecutionService,
    private val meterRegistry: MeterRegistry,
) {
    /**
     * @param testDtos list of [TestDto]s to save into the DB
     * @param executionId ID of the [Execution], during which these tests will be executed. It might be not required if there are standard test suites
     */
    @PostMapping(path = ["/${v1}/initializeTests"])
    fun initializeTests(@RequestBody testDtos: List<TestDto>, @RequestParam(required = false) executionId: Long?) {
        log.debug("Received the following tests for initialization under executionId=$executionId: $testDtos")
        val testsIds = meterRegistry.timer("save.backend.saveTests").record<List<Long>> {
            testService.saveTests(testDtos)
        }!!
        executionId?.let {
            meterRegistry.timer("save.backend.saveTestExecution").record {
                testExecutionService.saveTestExecution(executionId, testsIds)
            }
        }
    }

    /**
     * @param testSuiteId ID of the [TestSuite], for which all corresponding tests will be returned
     * @return list of tests
     */
    @GetMapping(path = ["/${v1}/getTestsByTestSuiteId"])
    fun getTestsByTestSuiteId(@RequestParam testSuiteId: Long): List<Test> = testService.findTestsByTestSuiteId(testSuiteId)

    /**
     * @param executionId ID of the [Execution], during which these tests will be executed
     * @param testSuiteId ID of the [TestSuite], for which there will be created execution in DB
     */
    @Suppress("UnsafeCallOnNullableType")
    @PostMapping(path = ["/${v1}/saveTestExecutionsForStandardByTestSuiteId"])
    fun saveTestExecutionsForStandardByTestSuiteId(@RequestBody executionId: Long, @RequestParam testSuiteId: Long) {
        val testsIds = testService.findTestsByTestSuiteId(testSuiteId).map { it.id!! }
        log.debug("Received the following test ids for saving test execution under executionId=$executionId: $testsIds")
        testExecutionService.saveTestExecution(executionId, testsIds)
    }

    /**
     * @param agentId
     * @return test batches
     */
    @GetMapping(path = ["/${v1}/getTestBatches"])
    fun testBatches(@RequestParam agentId: String) = testService.getTestBatches(agentId)

    companion object {
        private val log = LoggerFactory.getLogger(TestController::class.java)
    }
}
