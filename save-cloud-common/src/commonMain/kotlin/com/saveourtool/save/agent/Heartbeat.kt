/**
 * Model classes for heartbeating between save agent and the orchestrator
 */

package com.saveourtool.save.agent

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Progress of tests execution
 *
 * @property percentCompletion percentage of completed jobs, integer 0..100
 * @property executionId ID of an execution under which the progress is being tracked
 */
@Serializable
data class ExecutionProgress(
    val percentCompletion: Int,
    val executionId: Long,
) {
    init {
        @Suppress("MAGIC_NUMBER", "MagicNumber")
        require(percentCompletion in 0..100) { "percentCompletion should be in 0..100, but is $percentCompletion" }
    }
}

/**
 * A heartbeat sent from Agent to Orchestrator.
 *
 * @property agentId unique ID of the agent which sent the heartbeat
 * @property state current state of the Agent
 * @property executionProgress current progress of tests execution with this Agent
 * @property timestamp the time of heartbeat posting
 */
@Serializable
data class Heartbeat(
    val agentId: String,
    val state: AgentState,
    val executionProgress: ExecutionProgress,
    val timestamp: Instant,
)

/**
 * A response from Orchestrator to Agent
 */
@Serializable sealed class HeartbeatResponse

/**
 * A response that indicates that agent should wait
 */
@Serializable
object WaitResponse : HeartbeatResponse()

/**
 * A response that indicates that agent should continue what it is doing
 */
@Serializable
object ContinueResponse : HeartbeatResponse()

/**
 * @property config configuration to init agent
 */
@Serializable
data class InitResponse(val config: AgentInitConfig) : HeartbeatResponse()

/**
 * @property config configuration to run agent
 */
@Serializable
data class NewJobResponse(val config: AgentRunConfig) : HeartbeatResponse()

/**
 * A response that indicates that agent should exit gracefully
 */
@Serializable
object TerminateResponse : HeartbeatResponse()
