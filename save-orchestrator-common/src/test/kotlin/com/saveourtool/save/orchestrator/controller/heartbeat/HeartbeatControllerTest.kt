package com.saveourtool.save.orchestrator.controller.heartbeat

import com.saveourtool.save.agent.*
import com.saveourtool.save.domain.TestResultStatus
import com.saveourtool.save.entities.*
import com.saveourtool.save.orchestrator.config.JsonConfig
import com.saveourtool.save.orchestrator.controller.HeartbeatController
import com.saveourtool.save.orchestrator.runner.ContainerRunner
import com.saveourtool.save.orchestrator.service.*
import com.saveourtool.save.orchestrator.utils.AgentStatusInMemoryRepository
import com.saveourtool.save.orchestrator.utils.emptyResponseAsMono
import com.saveourtool.save.test.TestBatch
import com.saveourtool.save.test.TestDto
import io.kotest.matchers.collections.*
import io.kotest.matchers.shouldNot
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.MockBeans
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.web.reactive.function.BodyInserters
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.time.Month
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toJavaDuration

@Suppress("ReactiveStreamsUnusedPublisher")
@WebFluxTest(controllers = [HeartbeatController::class])
@Import(
    AgentService::class,
    HeartBeatInspector::class,
    AgentStatusInMemoryRepository::class,
    JsonConfig::class,
)
@MockBeans(MockBean(ContainerRunner::class))
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnableScheduling
class HeartbeatControllerTest {
    @Autowired lateinit var webClient: WebTestClient
    @Autowired private lateinit var agentService: AgentService
    @MockBean private lateinit var containerService: ContainerService
    @Autowired private lateinit var agentStatusInMemoryRepository: AgentStatusInMemoryRepository
    @MockBean private lateinit var orchestratorAgentService: OrchestratorAgentService

    @BeforeEach
    fun webClientSetUp() {
        webClient = webClient
            .mutate()
            .responseTimeout(2.seconds.toJavaDuration())
            .build()
    }

    @AfterEach
    fun cleanup() {
        verifyNoMoreInteractions(orchestratorAgentService)
        agentStatusInMemoryRepository.clear()
    }

    @Test
    fun checkAcceptingHeartbeat() {
        val heartBeatBusy = Heartbeat("test".toAgentInfo(), AgentState.BUSY, noProgress)

        whenever(orchestratorAgentService.updateAgentStatus(any()))
            .thenReturn(emptyResponseAsMono)
        webClient.post()
            .uri("/heartbeat")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(heartBeatBusy)
            .exchange()
            .expectStatus()
            .isOk
        verify(orchestratorAgentService).updateAgentStatus(any())
    }

    @Test
    fun `should respond with NewJobResponse when there are tests`() {
        val agentContainerId = "container-1"
        val cliArgs = "qwe"
        whenever(orchestratorAgentService.getNextRunConfig(agentContainerId))
            .thenReturn(
                AgentRunConfig(
                    cliArgs = cliArgs,
                    executionDataUploadUrl = "N/A",
                    debugInfoUploadUrl = "N/A"
                ).toMono()
            )

        val monoResponse = agentService.getNextRunConfig(agentContainerId).block() as NewJobResponse

        assertTrue(monoResponse.config.cliArgs.isNotEmpty())
        assertEquals(cliArgs, monoResponse.config.cliArgs)
        verify(orchestratorAgentService).getNextRunConfig(any())
    }

    @Test
    fun `should not shutdown any agents when not all of them are IDLE`() {
        testHeartbeat(
            agentStatusDtos = listOf(
                AgentStatusDto(AgentState.IDLE, "test-1"),
                AgentStatusDto(AgentState.BUSY, "test-2"),
            ),
            heartbeats = listOf(Heartbeat("test-1".toAgentInfo(), AgentState.IDLE, fullProgress).withoutDelay()),
            initConfigs = emptyList(),
            testBatchNullable = emptyList(),
            mockUpdateAgentStatusesCount = 1,
            mockAgentStatusesByExecutionId = true,
        ) { heartbeatResponses ->
            heartbeatResponses shouldNot exist { it is TerminateResponse }
        }
    }

    @Test
    fun `should not shutdown any agents when all agents are IDLE but there are more tests left`() {
        testHeartbeat(
            agentStatusDtos = listOf(
                AgentStatusDto(AgentState.IDLE, "test-1"),
                AgentStatusDto(AgentState.IDLE, "test-2"),
            ),
            heartbeats = listOf(Heartbeat("test-1".toAgentInfo(), AgentState.IDLE, fullProgress).withoutDelay()),
            initConfigs = emptyList(),
            testBatchNullable = listOf(
                TestDto("/path/to/test-1", "WarnPlugin", 1, "hash1", listOf("tag")),
                TestDto("/path/to/test-2", "WarnPlugin", 1, "hash2", listOf("tag")),
                TestDto("/path/to/test-3", "WarnPlugin", 1, "hash3", listOf("tag")),
            ),
            mockUpdateAgentStatusesCount = 2,
        ) { heartbeatResponses ->
            heartbeatResponses shouldNot exist { it is TerminateResponse }
        }
    }

    @Test
    fun `should send Terminate signal to idle agents when there are no tests left`() {
        whenever(containerService.isStopped(any())).thenReturn(true)
        val agentStatusDtos = listOf(
            AgentStatusDto(AgentState.IDLE, "test-1"),
            AgentStatusDto(AgentState.IDLE, "test-2"),
        )
        testHeartbeat(
            agentStatusDtos = agentStatusDtos,
            heartbeats = listOf(Heartbeat("test-1".toAgentInfo(), AgentState.IDLE, fullProgress).withoutDelay()),
            initConfigs = emptyList(),
            testBatchNullable = emptyList(),
            mockUpdateAgentStatusesCount = 2,
            mockAgentStatusesByExecutionId = true,
        ) { heartbeatResponses ->
            heartbeatResponses.shouldHaveSingleElement { it is TerminateResponse }
        }
    }

    @Test
    fun `should not shutdown any agents when they are STARTING`() {
        testHeartbeat(
            agentStatusDtos = listOf(
                AgentStatusDto(AgentState.STARTING, "test-1"),
                AgentStatusDto(AgentState.STARTING, "test-2"),
            ),
            heartbeats = listOf(
                Heartbeat("test-1".toAgentInfo(), AgentState.STARTING, noProgress).withDelay(1.seconds),
                Heartbeat("test-1".toAgentInfo(), AgentState.IDLE, noProgress).withDelay(2.seconds)
            ),
            initConfigs = listOf(initConfig),
            testBatchNullable = listOf(
                TestDto("/path/to/test-1", "WarnPlugin", 1, "hash1", listOf("tag")),
                TestDto("/path/to/test-2", "WarnPlugin", 1, "hash2", listOf("tag")),
                TestDto("/path/to/test-3", "WarnPlugin", 1, "hash3", listOf("tag")),
            ),
            mockUpdateAgentStatusesCount = 3,
            mockAddAgentCount = 1,
        ) { heartbeatResponses ->
            heartbeatResponses shouldNot exist { it is TerminateResponse }
        }
    }

    @Test
    @Suppress("TOO_LONG_FUNCTION", "LongMethod")
    fun `should shutdown agent, which don't sent heartbeat for some time`() {
        testHeartbeat(
            agentStatusDtos = listOf(
                AgentStatusDto(AgentState.STARTING, "test-1"),
                AgentStatusDto(AgentState.BUSY, "test-2"),
            ),
            heartbeats = listOf(
                Heartbeat("test-1".toAgentInfo(), AgentState.STARTING, noProgress).withoutDelay(),
                Heartbeat("test-1".toAgentInfo(), AgentState.IDLE, noProgress).withDelay(1.seconds),
                Heartbeat("test-1".toAgentInfo(), AgentState.BUSY, noProgress).withDelay(2.seconds),
                Heartbeat("test-2".toAgentInfo(), AgentState.BUSY, noProgress).withDelay(3.seconds),
                // 3 absent heartbeats from test-2
                Heartbeat("test-1".toAgentInfo(), AgentState.BUSY, noProgress).withDelay(4.seconds),
                Heartbeat("test-1".toAgentInfo(), AgentState.BUSY, noProgress).withDelay(5.seconds),
                Heartbeat("test-1".toAgentInfo(), AgentState.BUSY, noProgress).withDelay(10.seconds),
            ),
            initConfigs = listOf(initConfig),
            testBatchNullable = listOf(
                TestDto("/path/to/test-1", "WarnPlugin", 1, "hash1", listOf("tag")),
                TestDto("/path/to/test-2", "WarnPlugin", 1, "hash2", listOf("tag")),
                TestDto("/path/to/test-3", "WarnPlugin", 1, "hash3", listOf("tag")),
            ),
            mockUpdateAgentStatusesCount = 8,
            mockAddAgentCount = 1,
        ) {
            agentStatusInMemoryRepository.processCrashed {
                it shouldContainExactly setOf("test-2")
            }
        }
    }

    @Test
    fun `should shutdown all agents, since all of them don't sent heartbeats for some time`() {
        val agentStatusDtos = listOf(
            AgentStatusDto(AgentState.STARTING, "test-1"),
        )
        testHeartbeat(
            agentStatusDtos = agentStatusDtos,
            heartbeats = listOf(
                // heartbeats were sent long time ago
                Heartbeat("test-1".toAgentInfo(), AgentState.STARTING, noProgress).withoutDelay(),
                Heartbeat("test-1".toAgentInfo(), AgentState.IDLE, noProgress).withoutDelay(),
                Heartbeat("test-2".toAgentInfo(), AgentState.BUSY, noProgress).withoutDelay(),
                // some heartbeat from another agent to prevent cleanup of execution
                Heartbeat("test-3".toAgentInfo(), AgentState.BUSY, noProgress).withDelay(5.seconds),
            ),
            initConfigs = listOf(initConfig),
            testBatchNullable = listOf(
                TestDto("/path/to/test-1", "WarnPlugin", 1, "hash1", listOf("tag")),
                TestDto("/path/to/test-2", "WarnPlugin", 1, "hash2", listOf("tag")),
                TestDto("/path/to/test-3", "WarnPlugin", 1, "hash3", listOf("tag")),
            ),
            mockUpdateAgentStatusesCount = 5,
            mockAddAgentCount = 1,
        ) {
            agentStatusInMemoryRepository.processCrashed {
                it shouldContainExactlyInAnyOrder setOf("test-1", "test-2")
            }
        }
    }

    @Test
    fun `should shutdown agents even if there are some already FINISHED`() {
        val agentStatusDtos = listOf(
            AgentStatusDto(AgentState.IDLE, "test-1"),
            AgentStatusDto(AgentState.IDLE, "test-2"),
            AgentStatusDto(AgentState.FINISHED, "test-1", LocalDateTime(2021, Month.JANUARY, 1, 0, 0, 0)),
            AgentStatusDto(AgentState.FINISHED, "test-2", LocalDateTime(2021, Month.JANUARY, 1, 0, 0, 0)),
        )
        testHeartbeat(
            agentStatusDtos = agentStatusDtos,
            heartbeats = listOf(Heartbeat("test-1".toAgentInfo(), AgentState.IDLE, fullProgress).withoutDelay()),
            initConfigs = emptyList(),
            testBatchNullable = emptyList(),
            mockUpdateAgentStatusesCount = 2,
            mockAgentStatusesByExecutionId = true,
        ) { heartbeatResponses ->
            heartbeatResponses.shouldHaveSingleElement { it is TerminateResponse }
        }
    }

    @Suppress("TOO_LONG_FUNCTION")
    @Test
    fun `should mark test executions as failed if agent returned only part of results`() {
        val agentStatusDtos = listOf(
            AgentStatusDto(AgentState.IDLE, "test-1"),
            AgentStatusDto(AgentState.IDLE, "test-2"),
        )

        // if some test execution still have state `READY_FOR_TESTING`, but Agent.state == `FINISHED`
        // that's mean, that part of results is lost
        val testExecutions: List<TestExecutionDto> = listOf(
            TestExecutionDto(
                "testPath63",
                "WarnPlugin",
                "test",
                "test",
                TestResultStatus.READY_FOR_TESTING,
                0,
                0,
                unmatched = 3,
                matched = 2,
                expected = 0,
                unexpected = 0,
            )
        )

        doReturn(Mono.just(testExecutions))
            .whenever(orchestratorAgentService)
            .getReadyForTestingTestExecutions(argThat { this == "test-1" })

        whenever(orchestratorAgentService.markReadyForTestingTestExecutionsOfAgentAsFailed(any()))
            .thenReturn(emptyResponseAsMono)

        testHeartbeat(
            agentStatusDtos = agentStatusDtos,
            heartbeats = listOf(
                Heartbeat("test-1".toAgentInfo(), AgentState.FINISHED, fullProgress).withoutDelay()
            ),
            initConfigs = emptyList(),
            testBatchNullable = null,
            mockUpdateAgentStatusesCount = 1,
        ) {
            // not interested in any checks for heartbeats
            verify(orchestratorAgentService).getReadyForTestingTestExecutions(any())
            verify(orchestratorAgentService).markReadyForTestingTestExecutionsOfAgentAsFailed(any())
        }
    }

    /**
     * Test logic triggered by a heartbeat.
     *
     * @param agentStatusDtos agent statuses that are returned from backend (mocked response)
     * @param heartbeats a [Heartbeat] that is received by sandbox
     * @param testBatchNullable a batch of tests returned from backend (mocked response)
     * @param mockAgentStatusesByExecutionId whether a mocked response for `/getAgentStatusesByExecutionId` should be added to queue
     * @param verification a lambda for test assertions
     */
    @Suppress(
        "TOO_LONG_FUNCTION",
        "TOO_MANY_PARAMETERS",
        "LongParameterList",
        "LongMethod"
    )
    private fun testHeartbeat(
        agentStatusDtos: List<AgentStatusDto>,
        heartbeats: List<HeartbeatWithDelay>,
        initConfigs: List<AgentInitConfig>,
        testBatchNullable: TestBatch?,
        mockUpdateAgentStatusesCount: Int = 0,
        mockAgentStatusesByExecutionId: Boolean = false,
        mockAddAgentCount: Int = 0,
        verification: (heartbeatResponses: List<HeartbeatResponse?>) -> Unit,
    ) {
        val executionId = executionIdCounter.incrementAndGet()
        if (mockAddAgentCount > 0) {
            whenever(orchestratorAgentService.addAgent(anyLong(), any()))
                .thenReturn(emptyResponseAsMono)
        }
        initConfigs.forEach {
            whenever(orchestratorAgentService.getInitConfig(any()))
                .thenReturn(Mono.just(it))
        }
        testBatchNullable?.let { testBatch ->
            val returnValue = if (testBatch.isNotEmpty()) {
                AgentRunConfig(
                    cliArgs = testBatch.joinToString(" ") { it.filePath },
                    executionDataUploadUrl = "N/A",
                    debugInfoUploadUrl = "N/A",
                ).toMono()
            } else {
                Mono.empty()
            }
            whenever(orchestratorAgentService.getNextRunConfig(any()))
                .thenReturn(returnValue)
        }

        repeat(mockUpdateAgentStatusesCount) {
            whenever(orchestratorAgentService.updateAgentStatus(any()))
                .thenReturn(emptyResponseAsMono)
        }
        if (mockAgentStatusesByExecutionId) {
            whenever(orchestratorAgentService.getAgentStatusesByExecutionId(eq(executionId)))
                .thenReturn(Mono.just(agentStatusDtos))
        }

        val heartbeatResponses: MutableList<HeartbeatResponse?> = mutableListOf()
        heartbeats.forEach { (heartbeat, delay) ->
            Thread.sleep(delay.toLong(DurationUnit.MILLISECONDS))
            webClient.post()
                .uri("/heartbeat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(
                    BodyInserters.fromValue(
                        heartbeat.copy(
                            executionProgress = heartbeat.executionProgress.copy(
                                executionId = executionId
                            )
                        )
                    )
                )
                .exchange()
                .expectAll({ responseSpec ->
                    responseSpec.expectBody<HeartbeatResponse>()
                        .consumeWith {
                            heartbeatResponses.add(it.responseBody)
                        }
                },
                    { responseSpec ->
                        responseSpec.expectStatus()
                            .isOk
                    }
                )
        }

        // wait for background tasks
        Thread.sleep(5_000)

        verify(orchestratorAgentService, times(initConfigs.size)).getInitConfig(any())
        heartbeatResponses.filterIsInstance<InitResponse>().shouldHaveSize(initConfigs.size)
        testBatchNullable?.let {
            verify(orchestratorAgentService).getNextRunConfig(any())
        }
        verify(orchestratorAgentService, times(mockUpdateAgentStatusesCount)).updateAgentStatus(any())
        if (mockAgentStatusesByExecutionId) {
            verify(orchestratorAgentService).getAgentStatusesByExecutionId(any())
        }
        repeat(mockAddAgentCount) {
            verify(orchestratorAgentService).addAgent(anyLong(), any())
        }
        verification.invoke(heartbeatResponses)
    }

    companion object {
        private val executionIdCounter = AtomicLong()
        private val noProgress: ExecutionProgress = ExecutionProgress(0, -1L)
        private val fullProgress: ExecutionProgress = ExecutionProgress(100, -1L)
        private val initConfig: AgentInitConfig = AgentInitConfig(
            saveCliUrl = "stub",
            testSuitesSourceSnapshotUrl = "stub",
            additionalFileNameToUrl = mapOf("file" to "stub"),
            saveCliOverrides = SaveCliOverrides(),
        )
        private fun String.toAgentInfo(): AgentInfo = AgentInfo(
            containerId = this,
            containerName = this,
            version = "1.0",
        )

        private data class HeartbeatWithDelay(
            val heartbeat: Heartbeat,
            val delay: Duration,
        )

        private fun Heartbeat.withDelay(delay: Duration): HeartbeatWithDelay = HeartbeatWithDelay(this, delay)

        private fun Heartbeat.withoutDelay(): HeartbeatWithDelay = withDelay(ZERO)

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            // need to disable scheduler tasks in HeartBeatInspector
            registry.add("orchestrator.heart-beat-inspector-cron") { "-" }
            // need to disable graceful shutdown
            registry.add("orchestrator.shutdown.graceful-timeout-seconds") { "6000" }
            registry.add("orchestrator.shutdown.graceful-num-checks") { "1" }
        }
    }
}
