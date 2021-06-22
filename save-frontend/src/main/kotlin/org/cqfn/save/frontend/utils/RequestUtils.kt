/**
 * Kotlin/JS utilities for Fetch API
 */

package org.cqfn.save.frontend.utils

import org.cqfn.save.entities.Project

import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response

import kotlinx.browser.window
import kotlinx.coroutines.await

/**
 * Perform a mapping operation on a [Response] if it's status is OK or throw an exception otherwise.
 *
 * @param map mapping function
 * @return mapped result
 * @throws IllegalStateException if response status is not OK
 */
suspend fun <T> Response.unsafeMap(map: suspend (Response) -> T) = if (this.ok) {
    map(this)
} else {
    error("$status $statusText")
}

/**
 * Perform GET request.
 *
 * @param url request URL
 * @param headers request headers
 * @return [Response] instance
 */
suspend fun get(url: String, headers: Headers) = request(url, "GET", headers)

/**
 * Perform POST request.
 *
 * @param url request URL
 * @param headers request headers
 * @param body request body
 * @return [Response] instance
 */
suspend fun post(url: String, headers: Headers, body: dynamic) = request(url, "POST", headers, body)

/**
 * Perform an HTTP request using Fetch API. Suspending function that returns a [Response] - a JS promise with result.
 *
 * @param url request URL
 * @param method HTTP request method
 * @param headers HTTP headers
 * @param body request body
 * @return [Response] instance
 */
suspend fun request(url: String,
                    method: String,
                    headers: Headers,
                    body: dynamic = undefined,
): Response = window.fetch(
    input = url,
    RequestInit(
        method = method,
        headers = headers,
        body = body,
    )
)
    .await()

/**
 * @param name
 * @param owner
 */
suspend fun getProject(name: String, owner: String) =
        get("${window.location.origin}/getProject?name=$name&owner=$owner", Headers())
            .json()
            .await()
            .unsafeCast<Project>()
