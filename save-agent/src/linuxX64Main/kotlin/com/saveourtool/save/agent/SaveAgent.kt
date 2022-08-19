@file:OptIn(ExperimentalCoroutinesApi::class)

package com.saveourtool.save.agent

import com.saveourtool.save.agent.utils.*
import com.saveourtool.save.agent.utils.readFile
import com.saveourtool.save.agent.utils.requiredEnv
import com.saveourtool.save.agent.utils.sendDataToBackend
import com.saveourtool.save.core.logging.describe
import com.saveourtool.save.core.plugin.Plugin
import com.saveourtool.save.core.result.CountWarnings
import com.saveourtool.save.core.utils.ExecutionResult
import com.saveourtool.save.core.utils.ProcessBuilder
import com.saveourtool.save.core.utils.runIf
import com.saveourtool.save.domain.TestResultDebugInfo
import com.saveourtool.save.plugins.fix.FixPlugin
import com.saveourtool.save.reporter.Report
import com.saveourtool.save.utils.toTestResultDebugInfo
import com.saveourtool.save.utils.toTestResultStatus

import generated.SAVE_CLOUD_VERSION
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer

import kotlin.native.concurrent.AtomicLong
import kotlin.native.concurrent.AtomicReference
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * A main class for SAVE Agent
 * @property config
 * @property coroutineScope a [CoroutineScope] to launch other jobs
 * @property httpClient
 */
@Suppress("AVOID_NULL_CHECKS")
class SaveAgent(internal val config: AgentConfiguration,
                internal val httpClient: HttpClient,
                private val coroutineScope: CoroutineScope,
) {
    /**
     * The current [AgentState] of this agent
     */
    val state = AtomicReference(AgentState.STARTING)

    // fixme (limitation of old MM): can't use atomic reference to Instant here, because when using `Clock.System.now()` as an assigned value
    // Kotlin throws `kotlin.native.concurrent.InvalidMutabilityException: mutation attempt of frozen kotlinx.datetime.Instant...`
    private val executionStartSeconds = AtomicLong()
    private val saveProcessJob: AtomicReference<Job?> = AtomicReference(null)
    private val backgroundContext = newSingleThreadContext("background")
    private val saveProcessContext = newSingleThreadContext("save-process")
    private val reportFormat = Json {
        serializersModule = SerializersModule {
            polymorphic(Plugin.TestFiles::class) {
                subclass(Plugin.Test::class)
                subclass(FixPlugin.FixTestFiles::class)
            }
        }
    }

    /**
     * Starts save-agent and required jobs in the background and then immediately returns
     *
     * @return a descriptor of the main coroutine job
     */
    fun start(): Job {
        logInfoCustom("Starting agent")
        coroutineScope.launch(backgroundContext) {
            state.value = AgentState.BUSY
            sendDataToBackend { saveAdditionalData() }

            logDebugCustom("Will now download tests")
            val executionId = requiredEnv("EXECUTION_ID")
            val targetDirectory = config.testSuitesDir.toPath()
            downloadTestResources(config.backend, targetDirectory, executionId).runIf({ isFailure }) {
                logErrorCustom("Unable to download tests for execution $executionId: ${exceptionOrNull()?.describe()}")
                state.value = AgentState.CRASHED
                return@launch
            }
            logInfoCustom("Downloaded all tests for execution $executionId to $targetDirectory")

            logDebugCustom("Will now download additional resources")
            val additionalFilesList = requiredEnv("ADDITIONAL_FILES_LIST")
            downloadAdditionalResources(config.backend.url, targetDirectory, additionalFilesList, executionId).runIf({ isFailure }) {
                logErrorCustom("Unable to download resources for execution $executionId based on list [$additionalFilesList]: ${exceptionOrNull()?.describe()}")
                state.value = AgentState.CRASHED
                return@launch
            }

            state.value = AgentState.STARTING
        }
        return coroutineScope.launch { startHeartbeats(this) }
    }

    /**
     * Stop this agent by cancelling all jobs on [coroutineScope].
     * [coroutineScope] is the topmost scope for all jobs, so by cancelling it
     * we can gracefully shut down the whole application.
     */
    fun shutdown() {
        coroutineScope.cancel()
    }

    @Suppress("WHEN_WITHOUT_ELSE")  // when with sealed class
    private suspend fun startHeartbeats(coroutineScope: CoroutineScope) {
        logInfoCustom("Scheduling heartbeats")
        while (true) {
            val response = runCatching {
                // TODO: get execution progress here. However, with current implementation JSON report won't be valid until all tests are finished.
                sendHeartbeat(ExecutionProgress(0))
            }
            if (response.isSuccess) {
                when (val heartbeatResponse = response.getOrThrow().also {
                    logDebugCustom("Got heartbeat response $it")
                }) {
                    is NewJobResponse -> coroutineScope.launch {
                        maybeStartSaveProcess(heartbeatResponse.cliArgs)
                    }
                    is WaitResponse -> state.value = AgentState.IDLE
                    is ContinueResponse -> Unit  // do nothing
                    is TerminateResponse -> shutdown()
                }
            } else {
                logErrorCustom("Exception during heartbeat: ${response.exceptionOrNull()?.message}")
                response.exceptionOrNull()?.printStackTrace()
            }
            // todo: start waiting after request was sent, not after response?
            logInfoCustom("Waiting for ${config.heartbeat.intervalMillis} ms")
            delay(config.heartbeat.intervalMillis)
        }
    }

    private fun CoroutineScope.maybeStartSaveProcess(cliArgs: String) {
        if (saveProcessJob.value?.isCompleted == false) {
            logErrorCustom("Shouldn't start new process when there is the previous running")
        } else {
            saveProcessJob.value = launch(saveProcessContext) {
                runCatching {
                    // new job received from Orchestrator, spawning SAVE CLI process
                    startSaveProcess(cliArgs)
                }
                    .exceptionOrNull()
                    ?.let {
                        state.value = AgentState.CLI_FAILED
                        logErrorCustom("Error executing SAVE: ${it.describe()}\n" + it.stackTraceToString())
                    }
            }
        }
    }

    /**
     * @param cliArgs arguments for SAVE process
     * @return Unit
     */
    internal fun CoroutineScope.startSaveProcess(cliArgs: String) {
        // blocking execution of OS process
        state.value = AgentState.BUSY
        executionStartSeconds.value = Clock.System.now().epochSeconds
        val pwd = FileSystem.SYSTEM.canonicalize(".".toPath())
        logInfoCustom("Starting SAVE in $pwd with provided args $cliArgs")
        val executionResult = runSave(cliArgs)
        logInfoCustom("SAVE has completed execution with status ${executionResult.code}")
        val saveCliLogFilePath = config.logFilePath
        val byteArray = FileSystem.SYSTEM.source(saveCliLogFilePath.toPath())
            .buffer()
            .readByteArray()
        val saveCliLogData = String(byteArray).split("\n")

        launchLogSendingJob(byteArray)
        logDebugCustom("SAVE has completed execution, execution logs:")
        saveCliLogData.forEach {
            logDebugCustom("[SAVE] $it")
        }
        when (executionResult.code) {
            0 -> if (saveCliLogData.isEmpty()) {
                state.value = AgentState.CLI_FAILED
            } else {
                handleSuccessfulExit().invokeOnCompletion { cause ->
                    state.value = if (cause == null) AgentState.FINISHED else AgentState.CRASHED
                }
            }
            else -> {
                logErrorCustom("SAVE has exited abnormally with status ${executionResult.code}")
                state.value = AgentState.CLI_FAILED
            }
        }
    }

    @Suppress("MagicNumber")
    private fun runSave(cliArgs: String): ExecutionResult {
        val fullCliCommand = buildString {
            append(config.cliCommand)
            append(" $cliArgs")
            append(" --report-type ${config.save.reportType.name.lowercase()}")
            append(" --result-output ${config.save.resultOutput.name.lowercase()}")
            append(" --report-dir ${config.save.reportDir}")
            append(" --log ${config.save.logType.name.lowercase()}")
        }
        return ProcessBuilder(true, FileSystem.SYSTEM)
            .exec(
                fullCliCommand,
                "",
                config.logFilePath.toPath(),
                1_000_000L
            )
    }

    @Suppress("TOO_MANY_LINES_IN_LAMBDA", "TYPE_ALIAS")
    private fun readExecutionResults(jsonFile: String): Pair<List<TestResultDebugInfo>, List<TestExecutionDto>> {
        val currentTime = Clock.System.now()
        val reports: List<Report> = readExecutionReportFromFile(jsonFile)
        return reports.flatMap { report ->
            report.pluginExecutions.flatMap { pluginExecution ->
                pluginExecution.testResults.map { tr ->
                    val debugInfo = tr.toTestResultDebugInfo(report.testSuite, pluginExecution.plugin)
                    val testResultStatus = tr.status.toTestResultStatus()
                    debugInfo to TestExecutionDto(
                        tr.resources.test.toString(),
                        pluginExecution.plugin,
                        config.resolvedId(),
                        testResultStatus,
                        executionStartSeconds.value,
                        currentTime.epochSeconds,
                        unmatched = debugInfo.getCountWarningsAsLong { it.unmatched },
                        matched = debugInfo.getCountWarningsAsLong { it.matched },
                        expected = debugInfo.getCountWarningsAsLong { it.expected },
                        unexpected = debugInfo.getCountWarningsAsLong { it.unexpected },
                    )
                }
            }
        }
            .unzip()
    }

    @Suppress("MAGIC_NUMBER", "MagicNumber")
    private fun TestResultDebugInfo.getCountWarningsAsLong(getter: (CountWarnings) -> Int?) = this.debugInfo
        ?.countWarnings
        ?.let { getter(it) }
        ?.toLong()
        ?: 0L

    private fun readExecutionReportFromFile(jsonFile: String): List<Report> {
        val jsonFileContent = readFile(jsonFile).joinToString(separator = "")
        return if (jsonFileContent.isEmpty()) {
            throw IllegalStateException("Reading results file $jsonFile has returned empty")
        } else {
            reportFormat.decodeFromString(
                jsonFileContent
            )
        }
    }

    private fun CoroutineScope.launchLogSendingJob(byteArray: ByteArray): Job = launch {
        runCatching {
            sendLogs(byteArray)
        }
            .exceptionOrNull()
            ?.let {
                logErrorCustom("Couldn't send logs, reason: ${it.message}")
            }
    }

    private fun CoroutineScope.handleSuccessfulExit(): Job {
        val jsonReport = "${config.save.reportDir}/save.out.json"
        val result = runCatching {
            readExecutionResults(jsonReport)
        }
        return launch(backgroundContext) {
            if (result.isFailure) {
                val cause = result.exceptionOrNull()
                logErrorCustom(
                    "Couldn't read execution results from JSON report, reason: ${cause?.describe()}" +
                            "\n${cause?.stackTraceToString()}"
                )
            } else {
                val (debugInfos, testExecutionDtos) = result.getOrThrow()
                sendDataToBackend {
                    postExecutionData(testExecutionDtos)
                }
                debugInfos.forEach { debugInfo ->
                    logDebugCustom("Posting debug info for test ${debugInfo.testResultLocation}")
                    sendDataToBackend {
                        sendReport(debugInfo)
                    }
                }
            }
        }
    }

    /**
     * @param byteArray byte array with logs of CLI execution progress that will be sent in a message
     */
    private suspend fun sendLogs(byteArray: ByteArray): HttpResponse =
            httpClient.post {
                url("${config.orchestratorUrl}/executionLogs")
                setBody(MultiPartFormDataContent(formData {
                    append(
                        "executionLogs",
                        byteArray,
                        Headers.build {
                            append(HttpHeaders.ContentType, ContentType.MultiPart.FormData)
                            append(HttpHeaders.ContentDisposition, "filename=${config.resolvedId()}")
                        }
                    )
                }))
            }

    private suspend fun sendReport(testResultDebugInfo: TestResultDebugInfo) = httpClient.post {
        url("${config.backend.url}/${config.backend.filesEndpoint}/debug-info?agentId=${config.resolvedId()}")
        contentType(ContentType.Application.Json)
        setBody(testResultDebugInfo)
    }

    /**
     * @param executionProgress execution progress that will be sent in a heartbeat message
     * @return a [HeartbeatResponse] from Orchestrator
     */
    internal suspend fun sendHeartbeat(executionProgress: ExecutionProgress): HeartbeatResponse {
        logDebugCustom("Sending heartbeat to ${config.orchestratorUrl}")
        // if current state is IDLE or FINISHED, should accept new jobs as a response
        return httpClient.post {
            url("${config.orchestratorUrl}/heartbeat")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(Heartbeat(config.resolvedId(), state.value, executionProgress, Clock.System.now()))
        }
            .body()
    }

    private suspend fun postExecutionData(testExecutionDtos: List<TestExecutionDto>) = httpClient.post {
        logInfoCustom("Posting execution data to backend, ${testExecutionDtos.size} test executions")
        url("${config.backend.url}/${config.backend.executionDataEndpoint}")
        contentType(ContentType.Application.Json)
        setBody(testExecutionDtos)
    }

    private suspend fun saveAdditionalData() = httpClient.post {
        logInfoCustom("Posting additional data to backend")
        url("${config.backend.url}/${config.backend.additionalDataEndpoint}")
        contentType(ContentType.Application.Json)
        setBody(AgentVersion(config.resolvedId(), SAVE_CLOUD_VERSION))
    }
}
