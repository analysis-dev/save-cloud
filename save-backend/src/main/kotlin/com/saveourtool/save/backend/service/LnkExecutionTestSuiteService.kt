package com.saveourtool.save.backend.service

import com.saveourtool.save.backend.repository.LnkExecutionTestSuiteRepository
import com.saveourtool.save.entities.Execution
import com.saveourtool.save.entities.LnkExecutionTestSuite
import com.saveourtool.save.entities.TestSuite
import org.springframework.stereotype.Service

/**
 * Service of [LnkExecutionTestSuite]
 */
@Service
class LnkExecutionTestSuiteService(
    private val lnkExecutionTestSuiteRepository: LnkExecutionTestSuiteRepository,
) {
    /**
     * @param execution execution that is connected to testSuite
     * @return all [TestSuite]s with [execution]
     */
    fun getAllTestSuitesByExecution(execution: Execution) =
            lnkExecutionTestSuiteRepository.findByExecution(execution)
                .map {
                    it.testSuite
                }

    /**
     * @param executionId execution id that is connected to testSuite
     * @return all [TestSuite]s with [executionId]
     */
    fun getAllTestSuiteIdsByExecutionId(executionId: Long) =
            lnkExecutionTestSuiteRepository.findByExecutionId(executionId)
                .map {
                    it.testSuite.requiredId()
                }

    /**
     * @param testSuiteId manageable test suite
     * @return all [Execution]s with [testSuiteId]
     */
    fun getAllExecutionsByTestSuiteId(testSuiteId: Long) = lnkExecutionTestSuiteRepository.findByTestSuiteId(testSuiteId)
        .map {
            it.execution
        }

    /**
     * @param executionIds IDs of manageable executions
     */
    fun deleteByExecutionIds(executionIds: Collection<Long>) {
        lnkExecutionTestSuiteRepository.deleteAll(lnkExecutionTestSuiteRepository.findByExecutionIdIn(executionIds))
    }

    /**
     * @param [lnkExecutionTestSuite] link execution to testSuites
     */
    fun save(lnkExecutionTestSuite: LnkExecutionTestSuite): LnkExecutionTestSuite = lnkExecutionTestSuiteRepository.save(lnkExecutionTestSuite)

    /**
     * @param [lnkExecutionTestSuites] link execution to testSuites
     */
    fun saveAll(lnkExecutionTestSuites: List<LnkExecutionTestSuite>): List<LnkExecutionTestSuite> = lnkExecutionTestSuiteRepository.saveAll(lnkExecutionTestSuites)
}
