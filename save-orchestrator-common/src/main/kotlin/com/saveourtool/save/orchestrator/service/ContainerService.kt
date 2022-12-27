package com.saveourtool.save.orchestrator.service

import com.saveourtool.save.agent.AgentEnvName
import com.saveourtool.save.domain.Sdk
import com.saveourtool.save.entities.Execution
import com.saveourtool.save.execution.ExecutionStatus
import com.saveourtool.save.orchestrator.config.ConfigProperties
import com.saveourtool.save.orchestrator.fillAgentPropertiesFromConfiguration
import com.saveourtool.save.orchestrator.runner.ContainerRunner
import com.saveourtool.save.orchestrator.runner.ContainerRunnerException
import com.saveourtool.save.orchestrator.runner.EXECUTION_DIR
import com.saveourtool.save.orchestrator.utils.AgentStatusInMemoryRepository
import com.saveourtool.save.request.RunExecutionRequest
import com.saveourtool.save.utils.info
import com.saveourtool.save.utils.waitReactivelyUntil

import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

import kotlin.io.path.*
import kotlin.jvm.Throws
import kotlin.time.Duration.Companion.milliseconds

/**
 * A service that builds and starts containers for test execution.
 */
@Service
class ContainerService(
    private val configProperties: ConfigProperties,
    private val containerRunner: ContainerRunner,
    private val agentService: AgentService,
    private val agentStatusInMemoryRepository: AgentStatusInMemoryRepository,
) {
    /**
     * Function that builds a base image with test resources
     *
     * @param request [RunExecutionRequest] with info about [Execution] from which this workflow is started
     * @return image ID and execution command for the agent
     */
    fun prepareConfiguration(request: RunExecutionRequest): RunConfiguration {
        val buildResult = prepareConfigurationForExecution(request)
        log.info("For execution.id=${request.executionId} using base image [${buildResult.imageTag}]")
        return buildResult
    }

    /**
     * creates containers with agents
     *
     * @param executionId
     * @param configuration configuration for containers to be created
     * @return list of IDs of created containers
     */
    @Throws(ContainerRunnerException::class)
    fun createAndStartContainers(
        executionId: Long,
        configuration: RunConfiguration,
    ): Unit = containerRunner.createAndStart(
        executionId = executionId,
        configuration = configuration,
        replicas = configProperties.agentsCount,
    )

    /**
     * @param executionId ID of [Execution] for which containers are being started
     * @return Mono of ticks which correspond to attempts to check agents start, completes when agents are either
     * started or timeout is reached.
     */
    @Suppress("TOO_LONG_FUNCTION")
    fun validateContainersAreStarted(executionId: Long): Mono<Void> {
        log.info {
            "Validate that agents are started for execution.id=$executionId"
        }
        // Check, whether the agents were actually started, if yes, all cases will be covered by themselves and HeartBeatInspector,
        // if no, mark execution as failed with internal error here
        return waitReactivelyUntil(
            interval = configProperties.agentsStartCheckIntervalMillis.milliseconds,
            numberOfChecks = configProperties.agentsStartTimeoutMillis / configProperties.agentsStartCheckIntervalMillis,
        ) {
            agentStatusInMemoryRepository.containsAnyByExecutionId(executionId)
        }
            .doOnSuccess { hasStartedContainers ->
                if (!hasStartedContainers) {
                    log.error("Internal error: no agents are started, will mark execution $executionId as failed.")
                    cleanupAllByExecution(executionId)
                    agentService.updateExecution(executionId, ExecutionStatus.ERROR,
                        "Internal error, raise an issue at https://github.com/saveourtool/save-cloud/issues/new"
                    ).then(agentService.markAllTestExecutionsOfExecutionAsFailed(executionId))
                        .subscribe()
                }
            }
            .then()
    }

    /**
     * Check whether the agent with [containerId] is stopped
     *
     * @param containerId id of an container
     * @return true if agent is stopped
     */
    fun isStopped(containerId: String): Boolean = containerRunner.isStopped(containerId)

    /**
     * @param executionId ID of execution
     */
    fun cleanupAllByExecution(executionId: Long) {
        agentStatusInMemoryRepository.tryDeleteAllByExecutionId(executionId)
        containerRunner.cleanupAllByExecution(executionId)
    }

    private fun prepareConfigurationForExecution(request: RunExecutionRequest): RunConfiguration {
        val env = fillAgentPropertiesFromConfiguration(
            configProperties.agentSettings,
            request.saveAgentVersion,
            request.executionId,
        )

        val baseImage = baseImageName(request.sdk)

        /*
         * The command is executed using the user's login shell,
         * so changing 'sh -c' to 'bash -c' below won't affect anything.
         */
        @Language("bash")
        val agentCommand = "set ${getShellOptions()}" +
                " && curl ${getCurlOptions()} ${request.saveAgentUrl} --output $SAVE_AGENT_EXECUTABLE_NAME" +
                " && chmod +x $SAVE_AGENT_EXECUTABLE_NAME" +
                " && ./$SAVE_AGENT_EXECUTABLE_NAME"

        return RunConfiguration(
            imageTag = baseImage,
            runCmd = listOf(
                "sh", "-c", agentCommand
            ),
            env = env,
        )
    }

    /**
     * Information required to start containers with save-agent
     *
     * @property imageTag tag of an image which should be used for a container
     * @property runCmd command that should be run as container's entrypoint.
     * Usually looks like `sh -c "rest of the command"`.
     * @property workingDir
     * @property env environment variables for the container
     */
    data class RunConfiguration(
        val imageTag: String,
        val runCmd: List<String>,
        val workingDir: String = EXECUTION_DIR,
        val env: Map<AgentEnvName, String>,
    )

    companion object {
        private val log = LoggerFactory.getLogger(ContainerService::class.java)
        internal const val SAVE_AGENT_EXECUTABLE_NAME = "save-agent.kexe"

        /**
         * - `set -e` | `set -o errexit`: exit immediately if any command has a non-zero status.
         * - `set -u` | `set -o nounset`: exit immediately if a referenced variable is undefined.
         * - `set -x` | `set -o xtrace`: enable debugging (PS4 followed by command & args).
         *
         * Don't use directly, request via [getShellOptions] instead.
         * @see getShellOptions
         */
        @Language("bash")
        private val shellOptions: Array<out String> = arrayOf(
            "errexit",
            "nounset",
            "xtrace",
        )

        /**
         * `--fail` is necessary so that `curl` exits immediately upon an HTTP 404.
         *
         * Don't use directly, request via [getCurlOptions] instead.
         * @see getCurlOptions
         */
        @Language("bash")
        private val curlOptions: Array<out String> = arrayOf(
            "-vvv",
            "--fail",
            "-X",
            "POST"
        )

        /**
         * @return [shellOptions] as a single string.
         * @see shellOptions
         */
        @Language("bash")
        private fun getShellOptions(): String =
                shellOptions.asSequence().map { option ->
                    "-o $option"
                }.joinToString(separator = " ")

        /**
         * @return [curlOptions] as a single string.
         * @see curlOptions
         */
        @Language("bash")
        private fun getCurlOptions(): String =
                curlOptions.joinToString(separator = " ")
    }
}

/**
 * @param sdk
 * @return name like `save-base:openjdk-11`
 */
internal fun baseImageName(sdk: Sdk) = "ghcr.io/saveourtool/save-base:${sdk.toString().replace(":", "-")}"
