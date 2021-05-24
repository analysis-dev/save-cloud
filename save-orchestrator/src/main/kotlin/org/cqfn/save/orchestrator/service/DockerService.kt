package org.cqfn.save.orchestrator.service

import org.cqfn.save.entities.Execution
import org.cqfn.save.execution.ExecutionStatus
import org.cqfn.save.execution.ExecutionUpdateDto
import org.cqfn.save.orchestrator.config.ConfigProperties
import org.cqfn.save.orchestrator.docker.ContainerManager

import generated.SAVE_CORE_VERSION
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory

/**
 * A service that uses [ContainerManager] to build and start containers for test execution.
 */
@Service
@OptIn(ExperimentalPathApi::class)
class DockerService(private val configProperties: ConfigProperties) {
    /**
     * [ContainerManager] that is used to access docker daemon API
     */
    internal val containerManager = ContainerManager(configProperties.docker.host)
    private val executionDir = "/run/save-execution"
    private val isAgentStoppingInProgress = AtomicBoolean(false)

    @Autowired
    @Qualifier("webClientBackend")
    private lateinit var webClientBackend: WebClient

    /**
     * Function that builds a base image with test resources and then creates containers with agents.
     *
     * @param execution [Execution] from which this workflow is started
     * @return list of IDs of created containers
     */
    fun buildAndCreateContainers(execution: Execution): List<String> {
        log.info("Building base image for execution.id=${execution.id}")
        val imageId = buildBaseImageForExecution(execution)
        log.info("Built base image for execution.id=${execution.id}")
        return (1..configProperties.agentsCount).map { number ->
            log.info("Building container #$number for execution.id=${execution.id}")
            createContainerForExecution(imageId, "${execution.id}-$number").also {
                log.info("Built container id=$it for execution.id=${execution.id}")
            }
        }
    }

    /**
     * @param execution an [Execution] for which containers are being started
     * @param agentIds list of IDs of agents (==containers) for this execution
     */
    fun startContainersAndUpdateExecution(execution: Execution, agentIds: List<String>) {
        val executionId = requireNotNull(execution.id) { "For project=${execution.project} method has been called with execution with id=null" }
        log.info("Sending request to make execution.id=$executionId RUNNING")
        webClientBackend
            .post()
            .uri("/updateExecution")
            .body(BodyInserters.fromValue(ExecutionUpdateDto(executionId, ExecutionStatus.RUNNING)))
            .retrieve()
            .toBodilessEntity()
            .subscribe()
        agentIds.forEach {
            log.info("Starting container id=$it")
            containerManager.dockerClient.startContainerCmd(it).exec()
        }
        log.info("Successfully started all containers for execution.id=$executionId")
    }

    /**
     * @param agentIds list of IDs of agents to stop
     */
    fun stopAgents(agentIds: List<String>) {
        if (isAgentStoppingInProgress.compareAndSet(false, true)) {
            agentIds.forEach {
                log.info("Stopping agent with id=$it")
                containerManager.dockerClient.stopContainerCmd(it).exec()
                log.info("Agent with id=$it has been stopped")
            }
            isAgentStoppingInProgress.lazySet(false)
        }
    }

    private fun buildBaseImageForExecution(execution: Execution): String {
        val resourcesPath = File(
            configProperties.testResources.basePath,
            execution.resourcesRootPath,
        )
        // include save-agent into the image
        FileUtils.copyInputStreamToFile(
            ClassPathResource(SAVE_AGENT_EXECUTABLE_NAME).inputStream,
            File(resourcesPath, SAVE_AGENT_EXECUTABLE_NAME)
        )
        // include save-cli into the image
        FileUtils.copyInputStreamToFile(
            ClassPathResource(SAVE_CLI_EXECUTABLE_NAME).inputStream,
            File(resourcesPath, SAVE_CLI_EXECUTABLE_NAME)
        )
        val imageId = containerManager.buildImageWithResources(
            imageName = "save-execution:${execution.id}",
            baseDir = resourcesPath,
            resourcesPath = executionDir,
            runCmd = """RUN apt-get update && apt-get install -y libcurl4-openssl-dev && rm -rf /var/lib/apt/lists/*
                    |RUN chmod +x $executionDir/$SAVE_AGENT_EXECUTABLE_NAME
                    |RUN chmod +x $executionDir/$SAVE_CLI_EXECUTABLE_NAME
                """
        )
        return imageId
    }

    private fun createContainerForExecution(imageId: String, containerNumber: String): String {
        val containerId = containerManager.createContainerFromImage(
            imageId,
            executionDir,
            listOf("./$SAVE_AGENT_EXECUTABLE_NAME"),
            "save-execution-$containerNumber",
        )
        val agentPropertiesFile = createTempDirectory("agent")
            .resolve("agent.properties")
            .toFile()
        FileUtils.copyInputStreamToFile(
            ClassPathResource("agent.properties").inputStream,
            agentPropertiesFile
        )
        agentPropertiesFile.writeText(
            agentPropertiesFile.readLines().joinToString(System.lineSeparator()) {
                if (it.startsWith("id=")) "id=$containerId" else it
            }
        )
        containerManager.copyResourcesIntoContainer(
            containerId, executionDir,
            listOf(agentPropertiesFile)
        )
        return containerId
    }

    companion object {
        private val log = LoggerFactory.getLogger(DockerService::class.java)
        private const val SAVE_AGENT_EXECUTABLE_NAME = "save-agent.kexe"
        private const val SAVE_CLI_EXECUTABLE_NAME = "save-$SAVE_CORE_VERSION-linuxX64.kexe"
    }
}
