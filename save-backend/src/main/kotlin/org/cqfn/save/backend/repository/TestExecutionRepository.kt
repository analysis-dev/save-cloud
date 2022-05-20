package org.cqfn.save.backend.repository

import org.cqfn.save.domain.TestResultStatus
import org.cqfn.save.entities.TestExecution
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*
import javax.transaction.Transactional

/**
 * Repository of execution
 */
@Repository
interface TestExecutionRepository : BaseEntityRepository<TestExecution>, JpaSpecificationExecutor<TestExecution> {
    /**
     * @param status
     * @param id
     * @param pageable
     * @return list of test executions
     */
    fun findByStatusAndExecutionId(status: TestResultStatus, id: Long, pageable: Pageable): List<TestExecution>

    /**
     * @param executionId
     * @param pageable
     * @return list of test executions
     */
    fun findByExecutionId(executionId: Long, pageable: Pageable): List<TestExecution>

    /**
     * @param executionId
     * @param status
     * @param pageable
     * @return list of count test with status for test suite
     */
    @Query(
        value = """
            select tt.name, tt.count, tt.passed, tt.status from (
            select t1.name, t1.count, 
                CASE 
                WHEN t2.passed IS NULL 
                THEN 0 
                ELSE t2.passed 
                END as passed,
                CASE 
                WHEN t2.status IS NULL 
                THEN :status
                ELSE t2.status 
                END as status
            from (select ts.name, count(te.id) as count from test_execution te
            join test t
                on te.test_id = t.id
            join test_suite ts
                on ts.id = t.test_suite_id
            where 1=1
                and te.execution_id = :executionId
            group by ts.name) t1
            left outer join (
            select ts.name as name, count(te.id) as passed, te.status from test_execution te
            join test t
                on te.test_id = t.id
            join test_suite ts
                on ts.id = t.test_suite_id
            where 1=1
                and te.execution_id = :executionId
                and te.status = :status
            group by ts.name
            ) t2
            on t1.name = t2.name) tt
        """,
        countQuery = """
            select count(ts.name) from test_execution te
            join test t
                on te.test_id = t.id
            join test_suite ts
                on ts.id = t.test_suite_id
            where te.execution_id = :executionId
            group by ts.name
        """, nativeQuery = true
    )
    fun findByExecutionIdGroupByTestSuite(
        @Param("executionId") executionId: Long,
        @Param("status") status: String,
        pageable: Pageable,
    ): List<Array<*>>?

    /**
     * @param executionId
     * @return list of test executions
     */
    fun findByExecutionId(executionId: Long): List<TestExecution>

    /**
     * @param status
     * @param id
     * @return list of test executions
     */
    @Query(value = """
        SELECT te FROM TestExecution te 
        JOIN Execution e
        ON e = te.execution
        WHERE te.status IN :status and e.id = :id""")
    fun findByStatusListAndExecutionId(status: List<TestResultStatus>, id: Long): List<TestExecution>

    /**
     * Returns a page of [TestExecution]s with [executionId]
     *
     * @param executionId an ID of Execution to group TestExecutions
     * @param status test status
     * @param name suite name
     * @return number of TestExecutions
     */
    @Query(
        """SELECT COUNT(te) FROM TestExecution te
           JOIN Test t ON t.id = te.test
           JOIN Execution e ON e.id = te.execution
           JOIN TestSuite ts ON t.testSuite = ts.id
           WHERE 1 = 1
            and (:status is null or te.status = :status)
            and (:name is null or ts.name = :name)
            and e.id = :executionId"""
    )
    fun countByExecutionIdAndStatusAndTestTestSuiteName(
        @Param("executionId") executionId: Long,
        @Param("status") status: TestResultStatus?,
        @Param("name") name: String?,
    ): Int

    /**
     * Returns a page of [TestExecution]s with [executionId]
     *
     * @param executionId an ID of Execution to group TestExecutions
     * @param status test status
     * @param name suite name
     * @param pageable a request for a page
     * @return a list of [TestExecutionDto]s
     */
    @Query(
        """SELECT te FROM TestExecution te
           JOIN Test t ON t.id = te.test
           JOIN Execution e ON e.id = te.execution
           JOIN TestSuite ts ON t.testSuite = ts.id
           WHERE 1 = 1
            and (:status is null or te.status = :status)
            and (:name is null or ts.name = :name)
            and e.id = :executionId"""
    )
    fun findByExecutionIdAndStatusAndTestTestSuiteName(
        @Param("executionId") executionId: Long,
        @Param("status") status: TestResultStatus?,
        @Param("name") name: String?,
        pageable: Pageable
    ): List<TestExecution>

    /**
     * Returns test executions for agent [agentContainerId] and status [status]
     *
     * @param agentContainerId
     * @param status
     * @return a list of test executions
     */
    fun findByAgentContainerIdAndStatus(agentContainerId: String, status: TestResultStatus): List<TestExecution>

    /**
     * Returns a TestExecution matched by a set of fields
     *
     * @param executionId if of execution
     * @param filePath path to test file
     * @param pluginName name of the plugin from test execution
     * @return Optional TestExecution
     */
    fun findByExecutionIdAndTestPluginNameAndTestFilePath(executionId: Long, pluginName: String, filePath: String): Optional<TestExecution>

    /**
     * Returns a TestExecution matched by a set of fields
     *
     * @param executionId id of execution
     * @param testId id of test
     * @return list of TestExecution's
     */
    fun findByExecutionIdAndTestId(executionId: Long, testId: Long): List<TestExecution>

    /**
     * @param executionId
     * @param agentId
     * @return list of TestExecution's
     */
    @Suppress("TYPE_ALIAS")
    fun findByExecutionIdAndAgentId(executionId: Long, agentId: Long): List<TestExecution>

    /**
     * Delete a TestExecution matched by a set of fields
     *
     * @param executionId id of execution
     * @param testId id of test
     */
    @Transactional
    fun deleteAllByExecutionIdAndTestId(executionId: Long, testId: Long)

    /** Returns a TestExecution matched by the test id
     *
     * @param testId test id
     * @return Optional TestExecution
     */
    fun findByTestId(testId: Long): Optional<TestExecution>

    /**
     * Delete a TestExecution with execution Ids
     *
     * @param executionIds list ids of execution
     */
    @Transactional
    fun deleteByExecutionIdIn(executionIds: List<Long>)

    /**
     * Delete a TestExecution with execution Ids
     *
     * @param id project id
     */
    @Transactional
    fun deleteByExecutionProjectId(id: Long)
}
