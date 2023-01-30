package com.saveourtool.save.backend

import com.saveourtool.save.backend.configs.ConfigProperties
import com.saveourtool.save.authservice.config.NoopWebSecurityConfig
import com.saveourtool.save.authservice.utils.AuthenticationDetails
import com.saveourtool.save.backend.configs.WebConfig
import com.saveourtool.save.backend.controllers.DownloadFilesController
import com.saveourtool.save.backend.controllers.FileController
import com.saveourtool.save.backend.controllers.internal.FileInternalController
import com.saveourtool.save.backend.repository.*
import com.saveourtool.save.backend.security.ProjectPermissionEvaluator
import com.saveourtool.save.backend.service.*
import com.saveourtool.save.backend.storage.*
import com.saveourtool.save.backend.utils.mutateMockedUser
import com.saveourtool.save.core.result.DebugInfo
import com.saveourtool.save.core.result.Pass
import com.saveourtool.save.domain.*
import com.saveourtool.save.entities.*
import com.saveourtool.save.permission.Permission
import com.saveourtool.save.utils.mapToInputStream
import com.saveourtool.save.v1

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.MockBeans
import org.springframework.context.annotation.Import
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.test.web.reactive.server.expectBodyList
import org.springframework.web.reactive.function.BodyInserters
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.nio.ByteBuffer

import java.nio.file.Path
import java.time.LocalDateTime
import java.util.*
import kotlin.io.path.*

@ActiveProfiles("test")
@WebFluxTest(controllers = [DownloadFilesController::class, FileController::class, FileInternalController::class])
@Import(
    WebConfig::class,
    NoopWebSecurityConfig::class,
    AvatarStorage::class,
    DebugInfoStorage::class,
    ExecutionInfoStorage::class,
    S11nTestConfig::class,
)
@AutoConfigureWebTestClient
@EnableConfigurationProperties(ConfigProperties::class)
@MockBeans(
    MockBean(OrganizationService::class),
    MockBean(UserDetailsService::class),
    MockBean(ExecutionService::class),
    MockBean(AgentService::class),
    MockBean(ProjectPermissionEvaluator::class),
)
class DownloadFilesTest {
    private val organization = Organization.stub(2).apply {
        name = "Example.com"
    }
    private val organization2 = Organization.stub(1).apply {
        name = "Huawei"
    }
    private var testProject: Project = Project.stub(3, organization).apply {
        name = "TheProject"
        url = "example.com"
        description = "This is an example project"
    }
    private var testProject2: Project = Project.stub(1, organization2).apply {
        name = "huaweiName"
        url = "huawei.com"
        description = "test description"
    }
    private var file1: File = File(
        project = testProject,
        name = "test-1.txt",
        uploadedTime = LocalDateTime.now(),
        sizeBytes = 11L,
        isExecutable = false,
    ).apply {
        id = 1L
    }
    private var file2: File = File(
        project = testProject2,
        name = "test-2.txt",
        uploadedTime = LocalDateTime.now(),
        sizeBytes = 22L,
        isExecutable = false,
    ).apply {
        id = 2L
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var fileStorage: FileStorage

    @MockBean
    private lateinit var projectService: ProjectService

    @Test
    @Suppress("TOO_LONG_FUNCTION")
    @WithMockUser(roles = ["USER"])
    fun `should download a file`() {
        mutateMockedUser {
            details = AuthenticationDetails(id = 1)
        }

        val fileContent = "Lorem ipsum"
        whenever(fileStorage.doesExist(file1.toDto()))
            .thenReturn(true.toMono())
        whenever(fileStorage.getFileById(eq(file1.requiredId())))
            .thenReturn(Mono.just(file1.toDto()))
        whenever(fileStorage.download(eq(file1.toDto())))
            .thenReturn(Flux.just(ByteBuffer.wrap(fileContent.toByteArray())))
        whenever(fileStorage.listByProject(eq(file1.project)))
            .thenReturn(Flux.just(file1.toDto()))

        whenever(projectService.findByNameAndOrganizationNameAndCreatedStatus(eq(testProject.name), eq(organization.name)))
            .thenReturn(testProject)
        whenever(projectService.findWithPermissionByNameAndOrganization(any(), eq(testProject.name), eq(organization.name), eq(Permission.READ), anyOrNull(), any()))
            .thenAnswer { Mono.just(testProject) }

        webTestClient.get()
            .uri("/api/$v1/files/download?fileId={fileId}", file1.requiredId())
            .accept(MediaType.APPLICATION_OCTET_STREAM)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .consumeWith {
                Assertions.assertArrayEquals(fileContent.toByteArray(), it.responseBody)
            }

        webTestClient.get()
            .uri("/api/$v1/files/{organizationName}/{projectName}/list", testProject.organization.name, testProject.name)
            .exchange()
            .expectStatus()
            .isOk
            .expectBodyList<FileDto>()
            .hasSize(1)
            .consumeWith<WebTestClient.ListBodySpec<FileDto>> {
                Assertions.assertEquals(
                    listOf(file1.toDto()), it.responseBody
                )
            }
    }

    @Test
    fun `should return 404 for non-existent files`() {
        webTestClient.get()
            .uri("/api/$v1/files/download/invalid-name")
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Suppress("LongMethod")
    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun checkUpload() {
        mutateMockedUser {
            details = AuthenticationDetails(id = 1)
        }

        val fileContent = "Some content"
        whenever(fileStorage.doesExist(argThat { candidateTo(file2) }))
            .thenReturn(Mono.just(false))
        whenever(fileStorage.uploadAndReturnUpdatedKey(argThat { candidateTo(file2) }, argThat { collectToString() == fileContent }))
            .thenReturn(Mono.just(file2.toDto()))

        whenever(projectService.findByNameAndOrganizationNameAndCreatedStatus(eq(testProject2.name), eq(organization2.name)))
            .thenReturn(testProject2)
        whenever(projectService.findWithPermissionByNameAndOrganization(any(), eq(testProject2.name), eq(organization2.name), eq(Permission.WRITE), anyOrNull(), any()))
            .thenAnswer { Mono.just(testProject2) }

        val body = MultipartBodyBuilder().apply {
            val resource = (createTempDirectory() / file2.name).createFile()
                .also { it.writeText(fileContent) }
                .let { FileSystemResource(it) }
            part("file", resource)
        }
            .build()

        webTestClient.post()
            .uri("/api/$v1/files/Huawei/huaweiName/upload")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(body))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody<FileDto>()
            .consumeWith { result ->
                Assertions.assertEquals(
                    file2.toDto(),
                    result.responseBody
                )
            }
    }

    @Test
    fun `should save test data`() {
        val execution: Execution = mock()
        whenever(execution.id).thenReturn(1)

        webTestClient.post()
            .uri("/internal/files/debug-info?executionId=1")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                TestResultDebugInfo(
                    TestResultLocation("suite1", "plugin1", "path/to/test", "Test.test"),
                    DebugInfo("./a.out", "stdout", "stderr", 42L),
                    Pass(null),
                )
            )
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `download save-agent`() {
        setOf(HttpMethod.GET, HttpMethod.POST)
            .forEach { httpMethod ->
                webTestClient.method(httpMethod)
                    .uri("/internal/files/download-save-agent")
                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .consumeWith {
                        Assertions.assertArrayEquals(
                            "content-save-agent.kexe".toByteArray(),
                            it.responseBody
                        )
                    }
            }
    }

    @Test
    fun `download save-cli`() {
        setOf(HttpMethod.GET, HttpMethod.POST)
            .forEach { httpMethod ->
                webTestClient.method(httpMethod)
                    .uri("/internal/files/download-save-cli?version=1.0")
                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .consumeWith {
                        Assertions.assertArrayEquals(
                            "content-save-cli.kexe".toByteArray(),
                            it.responseBody
                        )
                    }

                webTestClient.method(httpMethod)
                    .uri("/internal/files/download-save-cli?version=2.0")
                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                    .exchange()
                    .expectStatus()
                    .isNotFound

            }
    }

    companion object {
        @TempDir internal lateinit var tmpDir: Path

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("backend.fileStorage.location") {
                tmpDir.absolutePathString()
            }
        }

        private fun FileDto.candidateTo(file: File) = name == file.name && projectCoordinates == file.project.toProjectCoordinates()

        private fun Flux<ByteBuffer>.collectToString(): String? = mapToInputStream()
            .map { it.bufferedReader().readText() }
            .block()
    }
}
