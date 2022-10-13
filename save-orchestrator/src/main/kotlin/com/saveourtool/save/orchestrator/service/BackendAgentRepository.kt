package com.saveourtool.save.orchestrator.service

import com.saveourtool.save.agent.AgentInitConfig
import com.saveourtool.save.domain.TestResultStatus
import com.saveourtool.save.entities.AgentDto
import com.saveourtool.save.entities.AgentStatusDto
import com.saveourtool.save.entities.AgentStatusesForExecution
import com.saveourtool.save.execution.ExecutionStatus
import com.saveourtool.save.execution.ExecutionUpdateDto
import com.saveourtool.save.orchestrator.config.ConfigProperties
import com.saveourtool.save.test.TestBatch
import com.saveourtool.save.utils.*
import org.slf4j.Logger
import org.springframework.http.ResponseEntity

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

internal typealias BodilessResponseEntity = ResponseEntity<Void>

/**
 * Service for work with agents and backend
 */
@Component
class BackendAgentRepository(
    configProperties: ConfigProperties,
) : AgentRepository {
    private val webClientBackend = WebClient.create(configProperties.backendUrl)
    override fun getInitConfig(containerId: String): Mono<AgentInitConfig> = webClientBackend
        .get()
        .uri("/agents/get-init-config?containerId=$containerId")
        .retrieve()
        .bodyToMono()

    override fun getNextTestBatch(containerId: String): Mono<TestBatch> = webClientBackend
        .get()
        .uri("/agents/get-next-test-batch?containerId=$containerId")
        .retrieve()
        .bodyToMono()

    override fun addAgents(agents: List<AgentDto>): Mono<IdList> = webClientBackend
        .post()
        .uri("/agents/insert")
        .bodyValue(agents)
        .retrieve()
        .bodyToMono()

    override fun updateAgentStatusesWithDto(agentStates: List<AgentStatusDto>): Mono<BodilessResponseEntity> =
            webClientBackend
                .post()
                .uri("/updateAgentStatusesWithDto")
                .bodyValue(agentStates)
                .retrieve()
                .toBodilessEntity()

    override fun getReadyForTestingTestExecutions(containerId: String): Mono<TestExecutionList> = webClientBackend.get()
        .uri("/testExecutions/agent/$containerId/${TestResultStatus.READY_FOR_TESTING}")
        .retrieve()
        .bodyToMono()

    override fun getAgentsStatuses(
        containerIds: List<String>,
    ): Mono<AgentStatusList> = webClientBackend
        .get()
        .uri("/agents/statuses?ids=${containerIds.joinToString(separator = DATABASE_DELIMITER)}")
        .retrieve()
        .bodyToMono()

    override fun updateExecutionByDto(
        executionId: Long,
        executionStatus: ExecutionStatus,
        failReason: String?,
    ): Mono<BodilessResponseEntity> =
            webClientBackend.post()
                .uri("/updateExecutionByDto")
                .bodyValue(ExecutionUpdateDto(executionId, executionStatus, failReason))
                .retrieve()
                .toBodilessEntity()

    override fun getAgentsStatusesForSameExecution(containerId: String): Mono<AgentStatusesForExecution> = webClientBackend
        .get()
        .uri("/getAgentsStatusesForSameExecution?agentId=$containerId")
        .retrieve()
        .bodyToMono()

    override fun markTestExecutionsOfAgentsAsFailed(containerIds: Collection<String>, onlyReadyForTesting: Boolean): Mono<BodilessResponseEntity> {
        log.debug("Attempt to mark test executions of agents=$containerIds as failed with internal error")
        return webClientBackend.post()
            .uri("/test-executions/mark-as-failed-by-container-ids?onlyReadyForTesting=$onlyReadyForTesting")
            .bodyValue(containerIds)
            .retrieve()
            .toBodilessEntity()
    }

    companion object {
        private val log: Logger = getLogger<BackendAgentRepository>()
    }
}
