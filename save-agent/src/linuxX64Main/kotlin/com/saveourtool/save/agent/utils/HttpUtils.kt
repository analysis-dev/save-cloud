/**
 * Utility methods for HTTP requests
 */

package com.saveourtool.save.agent.utils

import com.saveourtool.save.agent.AgentState
import com.saveourtool.save.agent.SaveAgent
import com.saveourtool.save.core.utils.runIf
import io.ktor.client.*
import io.ktor.client.request.*

import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import kotlinx.coroutines.CancellationException

/**
 * Attempt to send execution data to backend.
 *
 * @param requestToBackend
 * @return a [Result] wrapping response
 */
internal suspend fun SaveAgent.processRequestToBackend(
    requestToBackend: suspend () -> HttpResponse
): Result<HttpResponse> = processRequestToBackendWrapped {
    runCatching { requestToBackend() }
}

/**
 * Attempt to send execution data to backend.
 *
 * @param requestToBackend
 * @return a [Result] wrapping response
 */
internal suspend fun SaveAgent.processRequestToBackendWrapped(
    requestToBackend: suspend () -> Result<HttpResponse>
): Result<HttpResponse> = requestToBackend().runIf({ failureOrNotOk() }) {
    val reason = if (notOk()) {
        state.value = AgentState.BACKEND_FAILURE
        "Backend returned status ${getOrNull()?.status}"
    } else {
        state.value = AgentState.BACKEND_UNREACHABLE
        if (exceptionOrNull() is CancellationException) {
            "Request has been interrupted, switching to ${AgentState.BACKEND_UNREACHABLE} state"
        } else {
            "Backend is unreachable, ${exceptionOrNull()?.message}"
        }
    }
    logErrorCustom("Cannot process request to backed: $reason")
    this
}

/**
 * Perform a POST request to [url] (optionally with body [body] that will be serialized as JSON),
 * accepting application/octet-stream and return result wrapping [HttpResponse]
 *
 * @param url
 * @param body
 * @return result wrapping [HttpResponse]
 */
internal suspend fun HttpClient.download(url: String, body: Any?): Result<HttpResponse> = runCatching {
    post {
        url(url)
        contentType(ContentType.Application.Json)
        accept(ContentType.Application.OctetStream)
        body?.let { setBody(it) }
    }
}

/**
 * @return state of [Result]
 */
internal fun Result<HttpResponse>.failureOrNotOk() = isFailure || notOk()

private fun Result<HttpResponse>.notOk() = isSuccess && !getOrThrow().status.isSuccess()
