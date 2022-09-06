/**
 * Utilities, which extends http client functionality and provide api for execution submission process
 */

package com.saveourtool.save.api.utils

import com.saveourtool.save.api.authorization.Authorization
import com.saveourtool.save.api.config.WebClientProperties
import com.saveourtool.save.domain.FileInfo
import com.saveourtool.save.domain.ShortFileInfo
import com.saveourtool.save.entities.RunExecutionRequest
import com.saveourtool.save.execution.ExecutionDto
import com.saveourtool.save.utils.LocalDateTimeSerializer
import com.saveourtool.save.utils.extractUserNameAndSource
import com.saveourtool.save.v1

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.json.*
import io.ktor.client.plugins.kotlinx.serializer.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import okio.Path.Companion.toPath

import java.io.File
import java.time.LocalDateTime

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

private val json = Json {
    serializersModule = SerializersModule {
        contextual(LocalDateTime::class, LocalDateTimeSerializer)
    }
}

private object Backend {
    lateinit var url: String
}

/**
 * @property username
 * @property source source (where the user identity is coming from)
 */
private object UserInformation {
    lateinit var username: String
    lateinit var source: String
}

/**
 * @return list of available files from storage
 */
suspend fun HttpClient.getAvailableFilesList(
): List<FileInfo> = getRequestWithAuthAndJsonContentType(
    "${Backend.url}/api/$v1/files/list"
).body()

/**
 * @param file
 * @return FileInfo of uploaded file
 */
@OptIn(InternalAPI::class)
suspend fun HttpClient.uploadAdditionalFile(
    file: String,
): ShortFileInfo = this.post {
    url("${Backend.url}/api/$v1/files/upload")
    header("X-Authorization-Source", UserInformation.source)
    body = MultiPartFormDataContent(formData {
        append(
            key = "file",
            value = File(file).readBytes(),
            headers = Headers.build {
                append(HttpHeaders.ContentDisposition, "filename=${file.toPath().name}")
            }
        )
    })
}.body()

/**
 * Submit execution
 *
 * @param runExecutionRequest execution request
 * @return HttpResponse
 */
@Suppress("TOO_LONG_FUNCTION")
suspend fun HttpClient.submitExecution(runExecutionRequest: RunExecutionRequest): HttpResponse = this.post {
    url("${Backend.url}/api/$v1/run/trigger")
    header("X-Authorization-Source", UserInformation.source)
    header(HttpHeaders.ContentType, ContentType.Application.Json)
    setBody(runExecutionRequest)
}

/**
 * @param projectName
 * @param organizationName
 * @return ExecutionDto
 */
suspend fun HttpClient.getLatestExecution(
    projectName: String,
    organizationName: String
): ExecutionDto = getRequestWithAuthAndJsonContentType(
    "${Backend.url}/api/$v1/latestExecution?name=$projectName&organizationName=$organizationName"
).body()

/**
 * @param executionId
 * @return ExecutionDto
 */
suspend fun HttpClient.getExecutionById(
    executionId: Long
): ExecutionDto = getRequestWithAuthAndJsonContentType(
    "${Backend.url}/api/$v1/executionDto?executionId=$executionId"
).body()

private suspend fun HttpClient.getRequestWithAuthAndJsonContentType(url: String): HttpResponse = this.get {
    url(url)
    header("X-Authorization-Source", UserInformation.source)
    contentType(ContentType.Application.Json)
}

/**
 * @param authorization authorization settings
 * @param webClientProperties http client configuration
 * @return HttpClient instance
 */
fun initializeHttpClient(
    authorization: Authorization,
    webClientProperties: WebClientProperties,
): HttpClient {
    Backend.url = webClientProperties.backendUrl
    val (name, source) = extractUserNameAndSource(authorization.userInformation)
    UserInformation.username = name
    UserInformation.source = source

    return HttpClient(Apache) {
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.NONE
        }
        install(JsonPlugin) {
            serializer = KotlinxSerializer(json)
        }
        install(Auth) {
            basic {
                // by default, ktor will wait for the server to respond with 401,
                // and only then send the authentication header
                // therefore, adding sendWithoutRequest is required
                sendWithoutRequest { true }
                credentials {
                    BasicAuthCredentials(username = authorization.userInformation, password = authorization.token.orEmpty())
                }
            }
        }
    }
}
