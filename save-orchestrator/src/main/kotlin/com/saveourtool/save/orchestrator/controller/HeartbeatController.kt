/**
 * Heartbeat controller and corresponding logic which accepts heartbeat and depending on the state it returns the needed response
 */

package com.saveourtool.save.orchestrator.controller

import com.saveourtool.save.agent.*
import com.saveourtool.save.agent.AgentState.*
import com.saveourtool.save.entities.AgentStatusDto
import com.saveourtool.save.orchestrator.config.ConfigProperties
import com.saveourtool.save.orchestrator.service.AgentService
import com.saveourtool.save.orchestrator.service.DockerService

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.PropertySource
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2

import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import reactor.kotlin.core.publisher.switchIfEmpty

private val agentsLatestHeartBeatsMap: AgentStatesWithTimeStamps = ConcurrentHashMap()
internal val crashedAgentsList: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

typealias AgentStatesWithTimeStamps = ConcurrentHashMap<String, Pair<String, Instant>>

/**
 * Controller for heartbeat
 *
 * @param agentService
 * @property configProperties
 */
@RestController
class HeartbeatController(private val agentService: AgentService,
                          private val dockerService: DockerService,
                          private val configProperties: ConfigProperties) {
    private val logger = LoggerFactory.getLogger(HeartbeatController::class.java)

    /**
     * This controller accepts heartbeat and depending on the state it returns the needed response
     *
     * 1. Response has IDLE state. Then orchestrator should send new jobs.
     * 2. Response has FINISHED state. Then orchestrator should send new jobs and validate that data has actually been saved successfully.
     * 3. Response has BUSY state. Then orchestrator sends an Empty response.
     * 4. Response has ERROR state. Then orchestrator sends Terminating response.
     *
     * @param heartbeat
     * @return Answer for agent
     */
    @PostMapping("/heartbeat")
    fun acceptHeartbeat(@RequestBody heartbeat: Heartbeat): Mono<String> {
        logger.info("Got heartbeat state: ${heartbeat.state.name} from ${heartbeat.agentId}")
        updateAgentHeartbeatTimeStamps(heartbeat)

        // store new state into DB
        return agentService.updateAgentStatusesWithDto(
            listOf(
                AgentStatusDto(LocalDateTime.now(), heartbeat.state, heartbeat.agentId)
            )
        )
            .then(
                when (heartbeat.state) {
                    // if agent sends the first heartbeat, we try to assign work for it
                    STARTING -> handleVacantAgent(heartbeat.agentId, isStarting = true)
                    // if agent idles, we try to assign work, but also check if it should be terminated
                    IDLE -> handleVacantAgent(heartbeat.agentId, isStarting = false)
                    // if agent has finished its tasks, we check if all data has been saved and either assign new tasks or mark the previous batch as failed
                    FINISHED -> agentService.checkSavedData(heartbeat.agentId).flatMap { isSavingSuccessful ->
                        handleFinishedAgent(heartbeat.agentId, isSavingSuccessful)
                    }
                    BUSY -> Mono.just(ContinueResponse)
                    BACKEND_FAILURE, BACKEND_UNREACHABLE, CLI_FAILED, STOPPED_BY_ORCH -> Mono.just(WaitResponse)
                    CRASHED -> {
                        logger.warn("Agent sent CRASHED status, but should be offline in that case!")
                        Mono.just(WaitResponse)
                    }
                }
            )
            .map {
                Json.encodeToString(HeartbeatResponse.serializer(), it)
            }
    }

    private fun handleVacantAgent(agentId: String, isStarting: Boolean = false): Mono<HeartbeatResponse> =
        agentService.getNewTestsIds(agentId)
            .doOnSuccess {
                if (it is NewJobResponse) {
                    agentService.updateAssignedAgent(agentId, it)
                }
            }
            .zipWhen {
                if (it is WaitResponse && !isStarting) agentService.getAgentsAwaitingStop(agentId)
                else Mono.just(-1 to emptyList())
            }
            .map { (response, executionIdToFinishedAgents) ->
                if (agentId in executionIdToFinishedAgents.second) TerminateResponse
                else response
            }

    private fun handleFinishedAgent(agentId: String, isSavingSuccessful: Boolean): Mono<HeartbeatResponse> {
        return if (isSavingSuccessful) {
            handleVacantAgent(agentId, isStarting = false)
        } else {
            // Agent finished its work, however only part of results were received, other should be marked as failed
            agentService.markTestExecutionsAsFailed(listOf(agentId), FINISHED)
                .subscribeOn(agentService.scheduler)
                .subscribe()
            Mono.just(WaitResponse)
        }
    }

    /**
     * Collect information about the latest heartbeats from agents, in aim to determine crashed one later
     *
     * @param heartbeat
     */
    fun updateAgentHeartbeatTimeStamps(heartbeat: Heartbeat) {
        agentsLatestHeartBeatsMap[heartbeat.agentId] = heartbeat.state.name to heartbeat.timestamp
    }

    /**
     * Consider agent as crashed, if it didn't send heartbeats for some time
     */
    fun determineCrashedAgents() {
        agentsLatestHeartBeatsMap.filter { (currentAgentId, _) ->
            currentAgentId !in crashedAgentsList
        }.forEach { (currentAgentId, stateToLatestHeartBeatPair) ->
            val duration = (Clock.System.now() - stateToLatestHeartBeatPair.second).inWholeMilliseconds
            logger.debug("Latest heartbeat from $currentAgentId was sent: $duration ms ago")
            if (duration >= configProperties.agentsHeartBeatTimeoutMillis) {
                logger.debug("Adding $currentAgentId to list crashed agents")
                crashedAgentsList.add(currentAgentId)
            }
        }
    }

    /**
     * Stop crashed agents and mark corresponding test executions as failed with internal error
     */
    fun processCrashedAgents() {
        if (crashedAgentsList.isEmpty()) {
            return
        }
        logger.debug("Stopping crashed agents: $crashedAgentsList")

        val areAgentsStopped = dockerService.stopAgents(crashedAgentsList)
        if (areAgentsStopped) {
            agentService.markAgentsAndTestExecutionsCrashed(crashedAgentsList)
            logger.warn("All agents are crashed, initialize shutdown sequence")
            if (agentsLatestHeartBeatsMap.keys().toList() == crashedAgentsList.toList()) {
                initiateShutdownSequence(crashedAgentsList.first(), areAllAgentsCrashed = true)
            }
        } else {
            logger.warn("Crashed agents $crashedAgentsList are not stopped after stop command")
        }
    }

    /**
     * If agent was IDLE and there are no new tests - we check if the Execution is completed.
     * We get all agents for the same execution, if they are all done.
     * Then we stop them via DockerService and update necessary statuses in DB via AgentService.
     *
     * @param agentId an ID of the agent from the execution, that will be checked.
     */
    @Suppress("TOO_LONG_FUNCTION")
    private fun initiateShutdownSequence(agentId: String, areAllAgentsCrashed: Boolean) {
        agentService.getAgentsAwaitingStop(agentId).flatMap { (_, finishedAgentIds) ->
            if (finishedAgentIds.isNotEmpty()) {
                // need to retry after some time, because for other agents BUSY state might have not been written completely
                logger.debug("Waiting for ${configProperties.shutdownChecksIntervalMillis} ms to repeat `getAgentsAwaitingStop` call for agentId=$agentId")
                Mono.delay(Duration.ofMillis(configProperties.shutdownChecksIntervalMillis)).then(
                    agentService.getAgentsAwaitingStop(agentId)
                )
            } else {
                Mono.empty()
            }
        }
            .flatMap { (executionId, finishedAgentIds) ->
                if (finishedAgentIds.isNotEmpty()) {
                    finishedAgentIds.forEach {
                        agentsLatestHeartBeatsMap.remove(it)
                        crashedAgentsList.remove(it)
                    }
                    if (!areAllAgentsCrashed) {
                        logger.debug("Agents ids=$finishedAgentIds have completed execution, will make an attempt to terminate them")
                        val areAgentsStopped = dockerService.stopAgents(finishedAgentIds)
                        if (areAgentsStopped) {
                            logger.info("Agents have been stopped, will mark execution id=$executionId and agents $finishedAgentIds as FINISHED")
                            agentService
                                .markAgentsAndExecutionAsFinished(executionId, finishedAgentIds)
                        } else {
                            logger.warn("Agents $finishedAgentIds are not stopped after stop command")
                            Mono.empty()
                        }
                    } else {
                        // In this case agents are already stopped, just update execution status
                        agentService
                            .markExecutionAsFailed(executionId)
                    }
                } else {
                    logger.debug("Agents other than $agentId are still running, so won't try to stop them")
                    Mono.empty()
                }
            }
            .subscribeOn(agentService.scheduler)
            .subscribe()
    }
}

/**
 * Background inspector, which detect crashed agents
 *
 * @property heartbeatController
 */
@Component
@PropertySource("classpath:application.properties")
class HeartBeatInspector(private val heartbeatController: HeartbeatController) {
    @Scheduled(cron = "*/\${orchestrator.heartBeatInspectorInterval} * * * * ?")
    private fun run() {
        heartbeatController.determineCrashedAgents()
        heartbeatController.processCrashedAgents()
    }
}
