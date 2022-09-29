package com.saveourtool.save.orchestrator.service

import com.saveourtool.save.agent.AgentState
import com.saveourtool.save.agent.Heartbeat
import com.saveourtool.save.entities.AgentStatusDto
import com.saveourtool.save.orchestrator.config.ConfigProperties

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.PropertySource
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

typealias AgentStateWithTimeStamp = Pair<String, Instant>

/**
 * Background inspector, which detect crashed agents
 * TODO: can be used to store data about existing agents on sandbox startup ([#11](https://github.com/saveourtool/save-cloud/issues/11))
 */
@Component
@PropertySource("classpath:application.properties")
class HeartBeatInspector(
    private val configProperties: ConfigProperties,
    private val dockerService: DockerService,
    private val agentService: AgentService,
) {
    private val agentsLatestHeartBeatsMap: ConcurrentMap<String, AgentStateWithTimeStamp> = ConcurrentHashMap()

    /**
     * Collection that stores agents that are acting abnormally and will probably be terminated forcefully
     */
    internal val crashedAgents: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Collect information about the latest heartbeats from agents, in aim to determine crashed one later
     *
     * @param heartbeat
     */
    fun updateAgentHeartbeatTimeStamps(heartbeat: Heartbeat) {
        agentsLatestHeartBeatsMap[heartbeat.agentId] = heartbeat.state.name to heartbeat.timestamp
    }

    /**
     * @param agentId
     */
    fun unwatchAgent(agentId: String) {
        agentsLatestHeartBeatsMap.remove(agentId)
        crashedAgents.remove(agentId)
    }

    /**
     * @param agentId
     */
    fun watchCrashedAgent(agentId: String) {
        crashedAgents.add(agentId)
    }

    /**
     * @param agentId
     */
    fun unwatchCrashedAgent(agentId: String) {
        crashedAgents.remove(agentId)
    }

    /**
     * Consider agent as crashed, if it didn't send heartbeats for some time
     */
    fun determineCrashedAgents() {
        agentsLatestHeartBeatsMap.filter { (currentAgentId, _) ->
            currentAgentId !in crashedAgents
        }.forEach { (currentAgentId, stateToLatestHeartBeatPair) ->
            val duration = (Clock.System.now() - stateToLatestHeartBeatPair.second).inWholeMilliseconds
            logger.debug("Latest heartbeat from $currentAgentId was sent: $duration ms ago")
            if (duration >= configProperties.agentsHeartBeatTimeoutMillis) {
                logger.debug("Adding $currentAgentId to list crashed agents")
                crashedAgents.add(currentAgentId)
            }
        }

        crashedAgents.removeIf { agentId ->
            dockerService.isAgentStopped(agentId)
        }
        agentsLatestHeartBeatsMap.filterKeys { agentId ->
            dockerService.isAgentStopped(agentId)
        }.forEach { (agentId, _) ->
            logger.debug("Agent $agentId is already stopped, will stop watching it")
            agentsLatestHeartBeatsMap.remove(agentId)
        }
    }

    /**
     * Stop crashed agents and mark corresponding test executions as failed with internal error
     */
    fun processCrashedAgents() {
        if (crashedAgents.isEmpty()) {
            return
        }
        logger.debug("Stopping crashed agents: $crashedAgents")

        val areAgentsStopped = dockerService.stopAgents(crashedAgents)
        if (areAgentsStopped) {
            Flux.fromIterable(crashedAgents).flatMap { agentId ->
                agentService.updateAgentStatusesWithDto(
                    AgentStatusDto(LocalDateTime.now(), AgentState.CRASHED, agentId)
                )
            }.blockLast()
            if (agentsLatestHeartBeatsMap.keys.toList() == crashedAgents.toList()) {
                logger.warn("All agents are crashed, initialize shutdown sequence. Crashed agents: $crashedAgents")
                // fixme: should be cleared only for execution
                val agentId = crashedAgents.first()
                agentsLatestHeartBeatsMap.clear()
                crashedAgents.clear()
                agentService.finalizeExecution(agentId)
            }
        } else {
            logger.warn("Crashed agents $crashedAgents are not stopped after stop command")
        }
    }

    @Scheduled(cron = "*/\${sandbox.heart-beat-inspector-interval} * * * * ?")
    private fun run() {
        determineCrashedAgents()
        processCrashedAgents()
    }

    /**
     * Clear all data about agents
     */
    internal fun clear() {
        agentsLatestHeartBeatsMap.clear()
        crashedAgents.clear()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HeartBeatInspector::class.java)
    }
}
