package com.saveourtool.save.backend.repository

import com.saveourtool.save.entities.Execution
import com.saveourtool.save.entities.Organization
import com.saveourtool.save.entities.Project
import org.springframework.stereotype.Repository
import java.util.Optional

/**
 * Repository of execution
 */
@Repository
interface ExecutionRepository : BaseEntityRepository<Execution> {
    /**
     * @param name name of project
     * @param organization organization of project
     * @return list of executions
     */
    fun getAllByProjectNameAndProjectOrganization(name: String, organization: Organization): List<Execution>

    /**
     * Get latest (by start time an) execution by project name and organization
     *
     * @param name name of project
     * @param organizationName name of organization of project
     * @return execution or null if it was not found
     */
    @Suppress("IDENTIFIER_LENGTH")
    fun findTopByProjectNameAndProjectOrganizationNameOrderByStartTimeDesc(name: String, organizationName: String): Optional<Execution>

    /**
     * @param project to find execution
     * @return execution
     */
    fun findTopByProjectOrderByStartTimeDesc(project: Project): Execution?

    /**
     * @param id to find execution, which contain this suite id
     * @return list of [Execution]'s
     */
    fun findAllByTestSuiteIdsContaining(id: String): List<Execution>
}
