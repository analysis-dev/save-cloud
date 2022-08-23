package com.saveourtool.save.orchestrator.controller.agents

import com.saveourtool.save.entities.Execution
import com.saveourtool.save.entities.Project
import com.saveourtool.save.execution.ExecutionStatus
import com.saveourtool.save.execution.ExecutionType
import com.saveourtool.save.orchestrator.config.Beans
import com.saveourtool.save.orchestrator.config.ConfigProperties
import com.saveourtool.save.orchestrator.controller.AgentsController
import com.saveourtool.save.orchestrator.docker.DockerPvId
import com.saveourtool.save.orchestrator.runner.AgentRunner
import com.saveourtool.save.orchestrator.runner.EXECUTION_DIR
import com.saveourtool.save.orchestrator.service.AgentService
import com.saveourtool.save.orchestrator.service.DockerService
import com.saveourtool.save.testutils.checkQueues
import com.saveourtool.save.testutils.cleanup
import com.saveourtool.save.testutils.createMockWebServer
import com.saveourtool.save.testutils.enqueue
import com.saveourtool.save.utils.compressAsZipTo

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.MockBeans
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import reactor.core.publisher.Flux

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import kotlin.io.path.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@WebFluxTest(controllers = [AgentsController::class])
@Import(AgentService::class, Beans::class)
@MockBeans(MockBean(AgentRunner::class))
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AgentsControllerTest {
    @Autowired
    lateinit var webClient: WebTestClient

    @Autowired
    private lateinit var configProperties: ConfigProperties
    @MockBean private lateinit var dockerService: DockerService

    @AfterEach
    fun tearDown() {
        val pathToLogs = configProperties.executionLogs
        File(pathToLogs).deleteRecursively()
    }

    @Test
    @Suppress("TOO_LONG_FUNCTION", "LongMethod", "UnsafeCallOnNullableType")
    fun `should build image, query backend and start containers`() {
        val project = Project.stub(null)
        val execution = Execution.stub(project).apply {
            type = ExecutionType.STANDARD
            status = ExecutionStatus.PENDING
            testSuiteIds = "1"
            id = 42L
        }
        val tmpDir = createTempDirectory()
        val tmpFile = createTempFile(tmpDir)
        tmpFile.writeText("test")
        val tmpArchive = createTempFile()
        tmpDir.compressAsZipTo(tmpArchive)
        mockServer.enqueue(
            ".*/test-suites-sources/download-snapshot-by-execution-id.*",
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/octet-stream")
                .setBody(Buffer().readFrom(tmpArchive.inputStream()))
        )
        whenever(dockerService.prepareConfiguration(any())).thenReturn(
            DockerService.RunConfiguration(
                imageTag = "test-image-id",
                runCmd = listOf("sh", "-c", "test-exec-cmd"),
                pvId = DockerPvId("test-pv-id"),
                workingDir = EXECUTION_DIR,
                resourcesPath = Path.of("test-resources-path"),
                resourcesConfiguration = DockerService.RunConfiguration.ResourcesConfiguration(
                    executionId = execution.id!!,
                    additionalFilesString = "",
                    overrideExecCmd = null,
                    overrideExecFlags = null,
                    batchSize = null,
                    batchSeparator = null,
                )
            )
        )
        whenever(dockerService.createContainers(any(), any()))
            .thenReturn(listOf("test-agent-id-1", "test-agent-id-2"))
        whenever(dockerService.startContainersAndUpdateExecution(any(), anyList()))
            .thenReturn(Flux.just(1L, 2L, 3L))
        mockServer.enqueue(
            "/addAgents.*",
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(Json.encodeToString(listOf<Long>(1, 2)))
        )
        mockServer.enqueue("/updateAgentStatuses", MockResponse().setResponseCode(200))
        // /updateExecutionByDto is not mocked, because it's performed by DockerService, and it's mocked in these tests

        webClient
            .post()
            .uri("/initializeAgents")
            .bodyValue(execution)
            .exchange()
            .expectStatus()
            .isAccepted
        Thread.sleep(2_500)  // wait for background task to complete on mocks
        verify(dockerService).prepareConfiguration(any<Execution>())
        verify(dockerService).createContainers(any(), any())
        verify(dockerService).startContainersAndUpdateExecution(any(), anyList())

        tmpFile.deleteExisting()
        tmpDir.deleteExisting()
        tmpArchive.deleteExisting()
    }

    @Test
    fun checkPostResponseIsNotOk() {
        val project = Project.stub(null)
        val execution = Execution.stub(project)

        webClient
            .post()
            .uri("/initializeAgents")
            .bodyValue(execution)
            .exchange()
            .expectStatus()
            .is4xxClientError
    }

    @Test
    fun `should stop agents by id`() {
        webClient
            .post()
            .uri("/stopAgents")
            .body(BodyInserters.fromValue(listOf("id-of-agent")))
            .exchange()
            .expectStatus()
            .isOk
        verify(dockerService).stopAgents(anyList())
    }

    @Test
    fun `should save logs`() {
        val logs = """
            first line
            second line
        """.trimIndent().lines()
        makeRequestToSaveLog(logs)
            .expectStatus()
            .isOk
        val logFile = File(configProperties.executionLogs + File.separator + "agent.log")
        Assertions.assertTrue(logFile.exists())
        Assertions.assertEquals(logFile.readLines(), logs)
    }

    @Test
    fun `check save log if already exist`() {
        val firstLogs = """
            first line
            second line
        """.trimIndent().lines()
        makeRequestToSaveLog(firstLogs)
            .expectStatus()
            .isOk
        val firstLogFile = File(configProperties.executionLogs + File.separator + "agent.log")
        Assertions.assertTrue(firstLogFile.exists())
        Assertions.assertEquals(firstLogFile.readLines(), firstLogs)

        val secondLogs = """
            second line
            first line
        """.trimIndent().lines()
        makeRequestToSaveLog(secondLogs)
            .expectStatus()
            .isOk
            .expectStatus()
            .isOk
        val newFirstLogFile = File(configProperties.executionLogs + File.separator + "agent.log")
        Assertions.assertTrue(newFirstLogFile.exists())
        Assertions.assertEquals(newFirstLogFile.readLines(), firstLogs + secondLogs)
    }

    @Test
    fun `should cleanup execution artifacts`() {
        mockServer.enqueue(
            "/getAgentsIdsForExecution.*",
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(Json.encodeToString(listOf("container-1", "container-2", "container-3")))
        )

        webClient.post()
            .uri("/cleanup?executionId=42")
            .exchange()
            .expectStatus()
            .isOk

        Thread.sleep(2_500)
        verify(dockerService, times(1)).cleanup(anyLong())
    }

    private fun makeRequestToSaveLog(text: List<String>): WebTestClient.ResponseSpec {
        val fileName = "agent.log"
        val filePath = configProperties.executionLogs + File.separator + fileName
        val file = File(filePath)
        if (!file.exists()) {
            Files.createDirectories(Paths.get(configProperties.executionLogs))
            file.createNewFile()
        }

        text.forEach {
            file.appendText(it + "\n")
        }

        val body = MultipartBodyBuilder().apply {
            part(
                "executionLogs",
                file.readBytes()
            )
                .header("Content-Disposition", "form-data; name=executionLogs; filename=$fileName")
        }
            .build()

        return webClient
            .post()
            .uri("/executionLogs")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(body))
            .exchange()
    }

    companion object {
        @OptIn(ExperimentalPathApi::class)
        private val volume: String by lazy {
            createTempDirectory("executionLogs").toAbsolutePath().toString()
        }

        @JvmStatic
        private lateinit var mockServer: MockWebServer

        @AfterEach
        fun cleanup() {
            mockServer.checkQueues()
            mockServer.cleanup()
        }

        @AfterAll
        fun tearDown() {
            mockServer.shutdown()
        }

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            // todo: should be initialized in @BeforeAll, but it gets called after @DynamicPropertySource
            mockServer = createMockWebServer()
            mockServer.start()
            registry.add("orchestrator.backendUrl") { "http://localhost:${mockServer.port}" }
            registry.add("orchestrator.executionLogs") { volume }
        }
    }
}
