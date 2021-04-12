package org.cqfn.save.preprocessor

import org.cqfn.save.entities.ExecutionRequest
import org.cqfn.save.entities.Project
import org.cqfn.save.preprocessor.config.ConfigProperties
import org.cqfn.save.preprocessor.controllers.DownloadProjectController
import org.cqfn.save.preprocessor.utils.RepositoryVolume
import org.cqfn.save.repository.GitRepository

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.web.reactive.function.BodyInserters

import java.io.File
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import kotlin.io.path.ExperimentalPathApi

@ExperimentalPathApi
@WebFluxTest(controllers = [DownloadProjectController::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DownloadProjectTest(
    @Autowired private val webClient: WebTestClient,
    @Autowired private val configProperties: ConfigProperties,
) : RepositoryVolume {
    @BeforeEach
    fun webClientSetUp() {
        webClient.mutate().responseTimeout(Duration.ofSeconds(2)).build()
    }

    @Test
    fun testBadRequest() {
        val wrongRepo = GitRepository("wrongGit")
        val project = Project("owner", "someName", "type", wrongRepo.url, "descr")
        val request = ExecutionRequest(project, wrongRepo)
        webClient.post()
            .uri("/upload")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(request))
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.ACCEPTED)
        Thread.sleep(2000)  // Time for request to delete directory
        Assertions.assertFalse(File("${configProperties.repository}/${wrongRepo.url.hashCode()}").exists())
    }

    /**
     * This one covers logic of connecting to services
     */
    @Test
    fun testCorrectDownload() {
        val validRepo = GitRepository("https://github.com/cqfn/save-cloud.git")
        val project = Project("owner", "someName", "type", validRepo.url, "descr").apply {
            id = 42L
        }
        val request = ExecutionRequest(project, validRepo)
        mockServerBackend.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("42")
        )
        mockServerOrchestrator.enqueue(
            MockResponse()
                .setResponseCode(200)
        )
        val assertions = CompletableFuture.supplyAsync {
            listOf(
                mockServerBackend.takeRequest(60, TimeUnit.SECONDS),
                mockServerOrchestrator.takeRequest(60, TimeUnit.SECONDS)
            )
        }
        webClient.post()
            .uri("/upload")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(request))
            .exchange()
            .expectStatus()
            .isAccepted
            .expectBody<String>()
            .isEqualTo("Clone pending")
        Assertions.assertTrue(File("${configProperties.repository}/${validRepo.url.hashCode()}").exists())
        assertions.orTimeout(60, TimeUnit.SECONDS).join().forEach { Assertions.assertNotNull(it) }
    }

    @AfterEach
    fun removeTestDir() {
        File(configProperties.repository).deleteRecursively()
    }

    companion object {
        @JvmStatic
        lateinit var mockServerBackend: MockWebServer

        @JvmStatic
        lateinit var mockServerOrchestrator: MockWebServer

        @AfterAll
        fun tearDown() {
            mockServerBackend.shutdown()
            mockServerOrchestrator.shutdown()
        }

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            mockServerBackend = MockWebServer()
            mockServerBackend.start()
            mockServerOrchestrator = MockWebServer()
            mockServerOrchestrator.start()
            registry.add("save.backend") { "http://localhost:${mockServerBackend.port}" }
            registry.add("save.orchestrator") { "http://localhost:${mockServerOrchestrator.port}" }
        }
    }
}
