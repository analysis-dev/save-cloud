package org.cqfn.save.backend.repository

import org.cqfn.save.entities.Test
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/**
 * Repository of tests
 */
interface TestRepository : JpaRepository<Test, String> {
    /**
     * Method to retrieve ready batches
     *
     * @return List of Tests
     */
    @Query(value = "select * from test inner join test_execution on test.id = test_execution.test_id and test_execution.status = 'READY'", nativeQuery = true)
    fun retrieveBatches(): List<Test>
}
