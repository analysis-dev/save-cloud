package org.cqfn.save.backend.repository

import org.cqfn.save.entities.TestSuite
import org.cqfn.save.testsuite.TestSuiteType
import org.springframework.data.repository.query.QueryByExampleExecutor
import org.springframework.stereotype.Repository

/**
 * JPA repositories for TestSuite
 */
@Repository
interface TestSuiteRepository : BaseEntityRepository<TestSuite>, QueryByExampleExecutor<TestSuite> {
    /**
     * @param testSuiteType
     * @return list of test suites by type
     */
    fun findAllByTypeIs(testSuiteType: TestSuiteType): List<TestSuite>

    /**
     * @param projectId id of the project associated with test suites
     * @return a list of test suites
     */
    fun findByProjectId(projectId: Long): List<TestSuite>
}
