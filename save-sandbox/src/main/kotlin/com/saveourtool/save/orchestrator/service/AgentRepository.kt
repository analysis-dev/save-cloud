package com.saveourtool.save.orchestrator.service

import com.saveourtool.save.agent.AgentInitConfig
import com.saveourtool.save.agent.AgentState
import com.saveourtool.save.agent.TestExecutionDto
import com.saveourtool.save.entities.Agent
import com.saveourtool.save.entities.AgentStatus
import com.saveourtool.save.entities.AgentStatusDto
import com.saveourtool.save.entities.AgentStatusesForExecution
import com.saveourtool.save.execution.ExecutionStatus
import com.saveourtool.save.orchestrator.BodilessResponseEntity
import com.saveourtool.save.test.TestBatch
import com.saveourtool.save.test.TestDto

import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

typealias IdList = List<Long>
typealias AgentStatusList = List<AgentStatusDto>
typealias TestExecutionList = List<TestExecutionDto>

/**
 * Repository to work with agents
 */
interface AgentRepository {
    /**
     * Gets config to init agent
     *
     * @param containerId
     * @return [Mono] of [AgentInitConfig]
     */
    fun getInitConfig(containerId: String): Mono<AgentInitConfig>

    /**
     * Gets new tests ids
     *
     * @param agentId
     * @return [Mono] of [TestBatch]
     */
    fun getNextTestBatch(agentId: String): Mono<TestBatch>

    /**
     * Save new agents to the DB and insert their statuses. This logic is performed in two consecutive requests.
     *
     * @param agents list of [Agent]s to save in the DB
     * @return Mono with IDs of saved [Agent]s
     * @throws WebClientResponseException if any of the requests fails
     */
    fun addAgents(agents: List<Agent>): Mono<IdList>

    /**
     * @param agentStates list of [AgentStatusDto] to update/insert in the DB
     * @return a Mono without body
     */
    fun updateAgentStatusesWithDto(agentStates: List<AgentStatusDto>): Mono<BodilessResponseEntity>

    /**
     * Get List of [TestExecutionDto] for agent [agentId] have status READY_FOR_TESTING
     *
     * @param agentId agent for which data is checked
     * @return list of saved [TestExecutionDto]
     */
    fun getReadyForTestingTestExecutions(agentId: String): Mono<TestExecutionList>

    /**
     * Get list of [AgentStatus] for provided values
     *
     * @param executionId id of an [com.saveourtool.save.entities.Execution]
     * @param agentIds ids of agents
     * @return Mono with response from backend
     */
    fun getAgentsStatuses(
        executionId: Long,
        agentIds: List<String>,
    ): Mono<AgentStatusList>

    /**
     * Marks the execution to specified state
     *
     * @param executionId execution that should be updated
     * @param executionStatus new status for execution
     * @param failReason to show to user in case of error status
     * @return a Mono without body
     */
    fun updateExecutionByDto(
        executionId: Long,
        executionStatus: ExecutionStatus,
        failReason: String?,
    ): Mono<BodilessResponseEntity>

    /**
     * @param agentId containerId of an agent
     * @return Mono with [AgentStatusesForExecution]: agent statuses belonged to a single [com.saveourtool.save.entities.Execution]
     */
    fun getAgentsStatusesForSameExecution(agentId: String): Mono<AgentStatusesForExecution>

    /**
     * @param agentId
     * @param testDtos
     * @return a Mono without body
     */
    fun assignAgent(agentId: String, testDtos: List<TestDto>): Mono<BodilessResponseEntity>

    /**
     * Mark agent's test executions as failed
     *
     * @param agentIds the list of agent IDs, for which, according the [status] corresponding test executions should be marked as failed
     * @param status
     * @return a Mono without body
     */
    fun setStatusByAgentIds(agentIds: Collection<String>, status: AgentState): Mono<BodilessResponseEntity>
}
