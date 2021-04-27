package org.cqfn.save.backend.service

import org.cqfn.save.agent.TestExecutionDto
import org.cqfn.save.backend.repository.TestExecutionRepository
import org.cqfn.save.backend.repository.TestRepository
import org.cqfn.save.backend.repository.TestSuiteRepository
import org.cqfn.save.backend.utils.toLocalDateTime
import org.cqfn.save.domain.TestResultStatus
import org.cqfn.save.entities.TestExecution

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * Service for test result
 */
@Service
class TestExecutionService(private val testExecutionRepository: TestExecutionRepository) {
    private val log = LoggerFactory.getLogger(TestExecutionService::class.java)

    @Autowired
    private lateinit var testRepository: TestRepository

    @Autowired
    private lateinit var testSuiteRepository: TestSuiteRepository

    /**
     * @param testExecutionsDtos
     * @return list of lost tests
     */
    fun saveTestResult(testExecutionsDtos: List<TestExecutionDto>): List<TestExecutionDto> {
        val lostTests: MutableList<TestExecutionDto> = mutableListOf()
        testExecutionsDtos.forEach { testExecDto ->
            val foundTestExec = testExecutionRepository.findById(testExecDto.id)
            foundTestExec.ifPresentOrElse({
                it.run {
                    this.startTime = testExecDto.startTime.toLocalDateTime()
                    this.endTime = testExecDto.endTime.toLocalDateTime()
                    this.status = testExecDto.status
                    testExecutionRepository.save(this)
                }
            },
                {
                    lostTests.add(testExecDto)
                    log.error("Test execution with id=[${testExecDto.id}] was not found in the DB")
                })
        }
        return lostTests
    }

    /**
     * @param testsId
     */
    fun saveTestExecution(testsId: List<Long>) {
        testsId.map { testId ->
            testRepository.findById(testId).ifPresentOrElse({ test ->
                testExecutionRepository.save(TestExecution(test, test.testSuite.id!!, null, TestResultStatus.READY, null, null))
            },
                { log.error("Can't find test with id = $testId to save in testExecution") }
            )
        }
    }
}
