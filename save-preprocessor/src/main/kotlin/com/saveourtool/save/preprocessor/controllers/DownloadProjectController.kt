package com.saveourtool.save.preprocessor.controllers

import com.saveourtool.save.entities.*
import com.saveourtool.save.execution.ExecutionInitializationDto
import com.saveourtool.save.execution.ExecutionStatus
import com.saveourtool.save.execution.ExecutionUpdateDto
import com.saveourtool.save.preprocessor.StatusResponse
import com.saveourtool.save.preprocessor.TextResponse
import com.saveourtool.save.preprocessor.config.ConfigProperties
import com.saveourtool.save.preprocessor.config.TestSuitesRepo
import com.saveourtool.save.preprocessor.service.TestsPreprocessorToBackendBridge
import com.saveourtool.save.preprocessor.utils.*
import com.saveourtool.save.testsuite.TestSuitesSourceDto
import com.saveourtool.save.utils.info
import com.saveourtool.save.utils.switchIfEmptyToNotFound

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ReactiveHttpOutputMessage
import org.springframework.http.ResponseEntity
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.reactive.function.client.toEntity
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.kotlin.core.publisher.toMono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import reactor.netty.http.client.HttpClientRequest

import java.time.Duration

import kotlin.io.path.ExperimentalPathApi

typealias Status = Mono<ResponseEntity<HttpStatus>>

/**
 * A Spring controller for git project downloading
 *
 * @property configProperties config properties
 */
@OptIn(ExperimentalPathApi::class)
@RestController
class DownloadProjectController(
    private val configProperties: ConfigProperties,
    private val objectMapper: ObjectMapper,
    kotlinSerializationWebClientCustomizer: WebClientCustomizer,
    private val testSuitesPreprocessorController: TestSuitesPreprocessorController,
    private val testsPreprocessorToBackendBridge: TestsPreprocessorToBackendBridge,
) {
    private val log = LoggerFactory.getLogger(DownloadProjectController::class.java)
    private val webClientBackend = WebClient.builder()
        .baseUrl(configProperties.backend)
        .apply(kotlinSerializationWebClientCustomizer::customize)
        .build()
    private val webClientOrchestrator = WebClient.builder()
        .baseUrl(configProperties.orchestrator)
        .codecs {
            it.defaultCodecs().multipartCodecs().encoder(Jackson2JsonEncoder(objectMapper))
        }
        .apply(kotlinSerializationWebClientCustomizer::customize)
        .build()
    private val scheduler = Schedulers.boundedElastic()
    private val standardTestSuitesRepo by lazy {
        readStandardTestSuitesFile(configProperties.reposFileName, objectMapper)
    }

    /**
     * @param executionRequest Dto of repo information to clone and project info
     * @return response entity with text
     */
    @Suppress("TOO_LONG_FUNCTION")
    @PostMapping("/upload")
    fun upload(
        @RequestBody executionRequest: ExecutionRequest,
    ): Mono<TextResponse> = executionRequest.toAcceptedResponseMono()
        .doOnSuccess {
            val (branch, version) = with(executionRequest.branchOrCommit) {
                if (isNullOrBlank()) {
                    null to null
                } else if (startsWith("origin/")) {
                    replaceFirst("origin/", "") to null
                } else {
                    null to this
                }
            }
            fetchAndTriggerTests(
                executionRequest.project.organization.name,
                executionRequest.gitDto.url,
                executionRequest.testRootPath,
                branch ?: executionRequest.gitDto.detectDefaultBranchName(),
                version,
                executionRequest,
            )
                .subscribeOn(scheduler)
                .subscribe()
        }

    /**
     * @param executionRequestForStandardSuites Dto of binary file, test suites names and project info
     * @return response entity with text
     */
    @PostMapping(value = ["/uploadBin"])
    fun uploadBin(
        @RequestBody executionRequestForStandardSuites: ExecutionRequestForStandardSuites,
    ) = executionRequestForStandardSuites.toAcceptedResponseMono()
        .doOnSuccess {
            Flux.fromIterable(standardTestSuitesRepo.testRootPaths)
                .flatMap { testRootPath ->
                    fetchAndTriggerTests(
                        standardTestSuitesRepo.organizationName,
                        standardTestSuitesRepo.url,
                        testRootPath,
                        standardTestSuitesRepo.branch,
                        null,
                        executionRequestForStandardSuites,
                    )
                }
                .subscribeOn(scheduler)
                .subscribe()
        }

    @Suppress("TOO_MANY_PARAMETERS")
    private fun fetchAndTriggerTests(
        organizationName: String,
        gitUrl: String,
        testRootPath: String,
        branch: String,
        version: String?,
        requestBase: ExecutionRequestBase,
    ): Mono<StatusResponse> {
        // search or create new test suites source by content
        val testSuitesSourceMono = testsPreprocessorToBackendBridge.getOrCreateTestSuitesSource(
            organizationName,
            gitUrl,
            testRootPath,
            branch
        )
        val testSuitesSourceWithVersion = version
            ?.let { testSuitesSourceMono.fetchSpecificTestSuites(it) }
            ?: testSuitesSourceMono.getLatestTestSuites()
        return testSuitesSourceWithVersion.flatMap { (testSuitesSource, version) ->
            triggerTests(testSuitesSource, version, requestBase)
        }
    }

    private fun Mono<TestSuitesSourceDto>.getLatestTestSuites() = this
        // take latest version from backend
        .zipWhen { it.getLatestVersion() }

    private fun Mono<TestSuitesSourceDto>.fetchSpecificTestSuites(providedVersion: String) = this
        // fetch new test suites if required
        .flatMap { testSuitesSource ->
            testSuitesPreprocessorController.fetch(testSuitesSource, providedVersion)
                .map { testSuitesSource }
        }
        .zipWith(Mono.just(providedVersion))

    private fun triggerTests(
        testSuitesSource: TestSuitesSourceDto,
        version: String,
        requestBase: ExecutionRequestBase,
    ) = testsPreprocessorToBackendBridge.getTestSuites(
        testSuitesSource.organizationName,
        testSuitesSource.name,
        version
    )
        .flatMapMany { Flux.fromIterable(it) }
        .filter(requestBase.getTestSuiteFilter())
        .collectList()
        .flatMap { testSuites ->
            updateExecution(
                requestBase,
                testSuites.getSingleVersion(),
                testSuites.map { it.requiredId() },
            )
        }
        .flatMap { it.executeTests() }

    private fun TestSuitesSourceDto.getLatestVersion(): Mono<String> =
            testSuitesPreprocessorController.fetch(this).flatMap {
                testsPreprocessorToBackendBridge.listTestSuitesSourceVersions(this)
                    .flatMap { keys ->
                        keys.maxByOrNull { it.creationTimeInMills }
                            ?.version
                            .toMono()
                    }
                    .switchIfEmptyToNotFound {
                        "Not found any version for $name in $organizationName"
                    }
            }

    // check that all test suites are from same git repo (sources can be different) and have same version (sha1)
    private fun List<TestSuite>.getSingleVersion(): String = this
        .also {
            require(it.isNotEmpty()) {
                "No TestSuite is selected"
            }
        }
        .associateBy { it.source.git.url }
        .also {
            require(it.keys.size == 1) {
                "Only a single git location is supported, but got: ${it.keys}"
            }
        }
        .values
        .map { it.version }
        .distinct()
        .also {
            require(it.size == 1) {
                "Only a single version is supported, but got: $it"
            }
        }
        .single()

    /**
     * Accept execution rerun request
     *
     * @param executionRerunRequest request
     * @return status 202
     */
    @Suppress("UnsafeCallOnNullableType", "TOO_LONG_FUNCTION")
    @PostMapping("/rerunExecution")
    fun rerunExecution(@RequestBody executionRerunRequest: ExecutionRequest) = Mono.fromCallable {
        requireNotNull(executionRerunRequest.executionId) { "Can't rerun execution with unknown id" }
        ResponseEntity("Clone pending", HttpStatus.ACCEPTED)
    }
        .doOnSuccess {
            updateExecutionStatus(executionRerunRequest.executionId!!, ExecutionStatus.PENDING)
                .flatMap { cleanupInOrchestrator(executionRerunRequest.executionId!!) }
                .flatMap { getExecution(executionRerunRequest.executionId!!) }
                .doOnNext {
                    log.info { "Skip initializing tests for execution.id = ${it.id}: it's rerun" }
                }
                .flatMap { it.executeTests() }
                .subscribeOn(scheduler)
                .subscribe()
        }

    /**
     * Controller to download standard test suites
     *
     * @return Empty response entity
     */
    @Suppress("TOO_LONG_FUNCTION", "TYPE_ALIAS")
    @PostMapping("/uploadStandardTestSuite")
    fun uploadStandardTestSuite() = Mono.just(ResponseEntity("Upload standard test suites pending...\n", HttpStatus.ACCEPTED))
        .doOnSuccess {
            Flux.fromIterable(standardTestSuitesRepo.testRootPaths)
                .flatMap { testRootPath ->
                    val testSuitesSourceAsMono = testsPreprocessorToBackendBridge.getOrCreateTestSuitesSource(
                        organizationName = standardTestSuitesRepo.organizationName,
                        gitUrl = standardTestSuitesRepo.url,
                        testRootPath = testRootPath,
                        branch = standardTestSuitesRepo.branch
                    )
                    testSuitesSourceAsMono
                        .flatMap { testSuitesSourceDto ->
                            testSuitesPreprocessorController.fetch(testSuitesSourceDto)
                        }.doOnError {
                            log.error("Error to update test suite with url=${standardTestSuitesRepo.url}, path=$testRootPath")
                        }
                }
                .subscribeOn(scheduler)
                .subscribe()
        }

    /**
     * Execute tests by execution id:
     * - Post request to backend to find all tests by test suite id which are set in execution and create TestExecutions for them
     * - Send a request to orchestrator to initialize agents and start tests execution
     */
    @Suppress(
        "LongParameterList",
        "TOO_MANY_PARAMETERS",
        "UnsafeCallOnNullableType"
    )
    private fun Execution.executeTests(): Mono<StatusResponse> = webClientBackend.post()
        .uri("/executeTestsByExecutionId?executionId=$id")
        .retrieve()
        .toBodilessEntity()
        .then(initializeAgents(this))
        .onErrorResume { ex ->
            val failReason = "Error during preprocessing. Reason: ${ex.message}"
            log.error(
                "$failReason, will mark execution.id=$id as failed; error: ",
                ex
            )
            updateExecutionStatus(id!!, ExecutionStatus.ERROR, failReason)
        }

    private fun getExecution(executionId: Long) = webClientBackend.get()
        .uri("/execution?id=$executionId")
        .retrieve()
        .bodyToMono<Execution>()

    @Suppress("TOO_MANY_PARAMETERS", "LongParameterList")
    private fun updateExecution(
        requestBase: ExecutionRequestBase,
        executionVersion: String,
        testSuiteIds: List<Long>,
    ): Mono<Execution> {
        val executionUpdate = ExecutionInitializationDto(requestBase.project, testSuiteIds, executionVersion, requestBase.execCmd, requestBase.batchSizeForAnalyzer)
        return webClientBackend.makeRequest(BodyInserters.fromValue(executionUpdate), "/updateNewExecution") { spec ->
            spec.onStatus({ status -> status != HttpStatus.OK }) { clientResponse ->
                log.error("Error when making update to execution fro project id = ${requestBase.project.id} ${clientResponse.statusCode()}")
                throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Execution not found"
                )
            }
            spec.bodyToMono()
        }
    }

    @Suppress("MagicNumber")
    private fun cleanupInOrchestrator(executionId: Long) =
            webClientOrchestrator.post()
                .uri("/cleanup?executionId=$executionId")
                .httpRequest {
                    // increased timeout, because orchestrator should finish cleaning up first
                    it.getNativeRequest<HttpClientRequest>()
                        .responseTimeout(Duration.ofSeconds(10))
                }
                .retrieve()
                .toBodilessEntity()

    /**
     * POST request to orchestrator to initiate its work
     */
    private fun initializeAgents(execution: Execution): Status {
        val bodyBuilder = MultipartBodyBuilder().apply {
            part("execution", execution, MediaType.APPLICATION_JSON)
        }

        return webClientOrchestrator
            .post()
            .uri("/initializeAgents")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
            .retrieve()
            .toEntity<HttpStatus>()
    }

    private fun <M, T> WebClient.makeRequest(
        body: BodyInserter<M, ReactiveHttpOutputMessage>,
        uri: String,
        toBody: (WebClient.ResponseSpec) -> Mono<T>
    ): Mono<T> {
        val responseSpec = this
            .post()
            .uri(uri)
            .body(body)
            .retrieve()
            .onStatus({status -> status != HttpStatus.OK }) { clientResponse ->
                log.error("Error when making request to $uri: ${clientResponse.statusCode()}")
                throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Upstream request error"
                )
            }
        return toBody(responseSpec)
    }

    private fun updateExecutionStatus(executionId: Long, executionStatus: ExecutionStatus, failReason: String? = null) =
            webClientBackend.makeRequest(
                BodyInserters.fromValue(ExecutionUpdateDto(executionId, executionStatus, failReason)),
                "/updateExecutionByDto"
            ) { it.toEntity<HttpStatus>() }
                .doOnSubscribe {
                    log.info("Making request to set execution status for id=$executionId to $executionStatus")
                }

    @Suppress("NO_BRACES_IN_CONDITIONALS_AND_LOOPS")
    private fun ExecutionRequestBase.getTestSuiteFilter(): (TestSuite) -> Boolean = when (this) {
        is ExecutionRequest -> {
            { true }
        }
        is ExecutionRequestForStandardSuites -> {
            { it.name in this.testSuites }
        }
    }

    private fun ExecutionRequestBase.toAcceptedResponseMono() =
            Mono.just(ResponseEntity(executionResponseBody(executionId), HttpStatus.ACCEPTED))
}

/**
 * @param name file name to read
 * @param objectMapper
 * @return map repository to paths to test configs
 */
fun readStandardTestSuitesFile(name: String, objectMapper: ObjectMapper) =
        ClassPathResource(name)
            .file
            .let {
                objectMapper.readValue(it, TestSuitesRepo::class.java)!!
            }

/**
 * @param executionId
 * @return response body for execution submission request
 */
@Suppress("UnsafeCallOnNullableType")
fun executionResponseBody(executionId: Long?): String = "Clone pending, execution id is ${executionId!!}"
