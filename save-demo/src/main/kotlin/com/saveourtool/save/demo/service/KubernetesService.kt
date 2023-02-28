package com.saveourtool.save.demo.service

import com.saveourtool.save.core.logging.describe
import com.saveourtool.save.demo.DemoAgentConfig
import com.saveourtool.save.demo.DemoRunRequest
import com.saveourtool.save.demo.DemoStatus
import com.saveourtool.save.demo.config.ConfigProperties
import com.saveourtool.save.demo.entity.Demo
import com.saveourtool.save.demo.storage.DemoInternalFileStorage
import com.saveourtool.save.demo.utils.*
import com.saveourtool.save.utils.*

import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.client.KubernetesClient
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

import java.net.ConnectException

import kotlinx.coroutines.reactor.mono
import kotlinx.serialization.json.Json

/**
 * Service for interactions with kubernetes
 */
@Service
@Profile("kubernetes")
class KubernetesService(
    private val kc: KubernetesClient,
    private val configProperties: ConfigProperties,
    private val internalFileStorage: DemoInternalFileStorage,
) {
    private val kubernetesSettings = requireNotNull(configProperties.kubernetes) {
        "Kubernetes settings should be passed in order to use Kubernetes"
    }

    /**
     * @param demo demo entity
     * @param version version of [demo] that should be run in kubernetes pod
     * @return [Mono] of [StringResponse] filled with readable message
     */
    @Suppress("TOO_MANY_LINES_IN_LAMBDA")
    fun start(demo: Demo, version: String = "manual"): Mono<StringResponse> = Mono.fromCallable {
        logger.info("Creating job ${jobNameForDemo(demo)}...")
        try {
            val downloadAgentUrl = internalFileStorage.usingPreSignedUrl { generateUrlToDownload(DemoInternalFileStorage.saveDemoAgent) }
                .orNotFound { "Not found ${DemoInternalFileStorage.saveDemoAgent} in internal storage" }
                .toString()
            kc.startJob(demo, downloadAgentUrl, kubernetesSettings)
            demo
        } catch (kre: KubernetesRunnerException) {
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Could not create job for ${jobNameForDemo(demo)}",
                kre,
            )
        }
    }
        .flatMap {
            mono { configureDemoAgent(it, version) }
        }

    /**
     * @param demo demo entity
     * @return list of [StatusDetails]
     */
    fun stop(demo: Demo): List<StatusDetails> {
        logger.info("Stopping job...")
        return kc.getJobByName(demo).delete()
    }

    /**
     * @param demo demo entity
     * @return [DemoStatus] of [demo] pod
     */
    suspend fun getStatus(demo: Demo): DemoStatus {
        val pod = getPodByDemo(demo)
        val status = retrySilently(RETRY_TIMES_QUICK) {
            demoAgentRequestWrapper(listOf("alive"), pod) { url ->
                logger.info("Sending GET request with url $url")
                httpClient.get(url).status
            }
        }
        return when {
            status == null -> DemoStatus.ERROR
            status == HttpStatusCode.OK -> DemoStatus.RUNNING
            status.isSuccess() -> DemoStatus.STARTING
            else -> DemoStatus.STOPPED
        }
    }

    private suspend fun configureDemoAgent(demo: Demo, version: String, retryNumber: Int = RETRY_TIMES): StringResponse {
        val pod = getPodByDemo(demo)
        logger.info("Configuring save-demo-agent ${demo.projectCoordinates()}")
        val configuration = DemoAgentConfig(
            configProperties.agentConfig.demoUrl,
            demo.toDemoConfiguration(version),
            demo.toRunConfiguration(),
        )
        return demoAgentRequestWrapper(listOf("setup"), pod) { url ->
            sendConfigurationRequestRetrying(url, configuration, retryNumber)
        }
    }

    /**
     * Performs run request with [demoRunRequest] params to [demo]
     *
     * @param demo [Demo] entity
     * @param demoRunRequest params of [DemoRunRequest]
     * @return [HttpResponse] if response is fetched, null if some error occurred
     */
    suspend fun sendRunRequest(
        demo: Demo,
        demoRunRequest: DemoRunRequest,
    ): HttpResponse? {
        val pod = getPodByDemo(demo)
        val response = try {
            demoAgentRequestWrapper(listOf("run"), pod) { url ->
                logger.info("Sending POST request with url $url")
                httpClient.post {
                    url(url)
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    setBody(demoRunRequest)
                }
            }
        } catch (connectException: ConnectException) {
            null
        }
        return response
    }

    private suspend fun sendConfigurationRequestRetrying(
        url: Url,
        configuration: DemoAgentConfig,
        retryNumber: Int,
    ): StringResponse {
        val (requestStatus, errors) = retry(retryNumber) { iteration ->
            logger.debug("$iteration attempts left.")
            logger.info("Sending POST request with url $url")
            httpClient.post {
                url(url)
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(configuration)
            }.status
        }
        return requestStatus?.let {
            if (requestStatus.isSuccess()) {
                logAndRespond(HttpStatusCode.OK, logger::info) {
                    "Job successfully started."
                }
            } else {
                logAndRespond(HttpStatusCode.InternalServerError, logger::error) {
                    "Could not configure demo: save-demo-agent responded with status [$requestStatus]."
                }
            }
        } ?: logAndRespond(HttpStatusCode.InternalServerError, logger::error) {
            val errorsAsString = errors.joinToString("\n", prefix = "\t") { throwable ->
                throwable.describe()
            }
            "Could not configure demo:\n$errorsAsString"
        }
    }

    /**
     * @param demo demo entity
     * @return url of pod with demo
     */
    private suspend fun getPodByDemo(demo: Demo) = retrySilently(RETRY_TIMES) {
        kc.getJobPods(demo).firstOrNull()
    } ?: throw KubernetesRunnerException("Could not run a job in 60 seconds.")

    companion object {
        private val logger = LoggerFactory.getLogger(KubernetesService::class.java)
        private const val RETRY_TIMES = 6
        private const val RETRY_TIMES_QUICK = 3
        private val httpClient = HttpClient(Apache) {
            install(ContentNegotiation) {
                val json = Json { ignoreUnknownKeys = true }
                json(json)
            }
        }
    }
}
