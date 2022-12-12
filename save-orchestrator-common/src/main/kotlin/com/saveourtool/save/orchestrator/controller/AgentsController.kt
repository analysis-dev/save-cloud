package com.saveourtool.save.orchestrator.controller

import com.saveourtool.save.entities.AgentDto
import com.saveourtool.save.execution.ExecutionStatus
import com.saveourtool.save.orchestrator.config.ConfigProperties
import com.saveourtool.save.orchestrator.runner.ContainerRunner
import com.saveourtool.save.orchestrator.service.AgentService
import com.saveourtool.save.orchestrator.service.ContainerService
import com.saveourtool.save.request.RunExecutionRequest
import com.saveourtool.save.utils.EmptyResponse
import com.saveourtool.save.utils.info

import com.github.dockerjava.api.exception.DockerException
import io.fabric8.kubernetes.client.KubernetesClientException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.doOnError

/**
 * Controller used to start agents with needed information
 */
@RestController
class AgentsController(
    private val configProperties: ConfigProperties,
    private val agentService: AgentService,
    private val containerService: ContainerService,
    private val containerRunner: ContainerRunner,
) {
    /**
     * Schedules tasks to build base images, create a number of containers and put their data into the database.
     *
     * @param request a request to run execution
     * @return OK if everything went fine.
     * @throws ResponseStatusException
     */
    @Suppress("TOO_LONG_FUNCTION", "LongMethod", "UnsafeCallOnNullableType")
    @PostMapping("/initializeAgents")
    fun initialize(@RequestBody request: RunExecutionRequest): Mono<EmptyResponse> {
        val response = Mono.just(ResponseEntity<Void>(HttpStatus.ACCEPTED))
            .subscribeOn(agentService.scheduler)
        return response.doOnSuccess {
            log.info {
                "Starting preparations for launching execution id=${request.executionId}"
            }
            Mono.fromCallable {
                dockerService.prepareConfiguration(request)
            }
                .subscribeOn(agentService.scheduler)
                .map { configuration ->
                    containerService.createAndStartContainers(request.executionId, configuration)
                }
                .onErrorResume({ it is DockerException || it is KubernetesClientException }) { ex ->
                    reportExecutionError(request.executionId, "Unable to create containers", ex)
                }
                .flatMapMany {
                    dockerService.validateContainersAreStarted(request.executionId)
                }
                .subscribe()
        }
    }

    private fun <T> reportExecutionError(
        executionId: Long,
        failReason: String,
        ex: Throwable?
    ): Mono<T> {
        log.error("$failReason for executionId=$executionId, will mark it as ERROR", ex)
        return agentService.updateExecution(executionId, ExecutionStatus.ERROR, failReason)
            .then(Mono.empty())
    }

    /**
     * @param containerIds list of container IDs of agents to stop
     */
    @PostMapping("/stopAgents")
    fun stopAgents(@RequestBody containerIds: List<String>) {
        containerService.stopAgents(containerIds)
    }

    /**
     * Delete containers and images associated with execution [executionId]
     *
     * @param executionId id of execution
     * @return empty response
     */
    @PostMapping("/cleanup")
    fun cleanup(@RequestParam executionId: Long): Mono<EmptyResponse> = Mono.fromCallable {
        containerService.cleanup(executionId)
    }
        .flatMap {
            Mono.just(ResponseEntity.ok().build())
        }

    companion object {
        private val log = LoggerFactory.getLogger(AgentsController::class.java)
    }
}
