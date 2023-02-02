/**
 * Methods to make specific requests to backend
 */

package com.saveourtool.save.frontend.http

import com.saveourtool.save.agent.TestExecutionDto
import com.saveourtool.save.entities.*
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.info.UserInfo
import com.saveourtool.save.utils.AvatarType
import js.core.jso

import org.w3c.fetch.Headers
import org.w3c.fetch.Response
import web.file.File
import web.http.FormData

import kotlinx.browser.window

/**
 * @param name
 * @param organizationName
 * @return project
 */
suspend fun ComponentWithScope<*, *>.getProject(name: String, organizationName: String) = get(
    "$apiUrl/projects/get/organization-name?name=$name&organizationName=$organizationName",
    Headers().apply {
        set("Accept", "application/json")
    },
    loadingHandler = ::classLoadingHandler,
    responseHandler = ::classComponentRedirectOnFallbackResponseHandler,
)
    .runCatching {
        decodeFromJsonString<ProjectDto>()
    }

/**
 * @param name organization name
 * @return organization
 */
suspend fun ComponentWithScope<*, *>.getOrganization(name: String) = get(
    "$apiUrl/organizations/$name",
    Headers().apply {
        set("Accept", "application/json")
    },
    loadingHandler = ::classLoadingHandler,
    responseHandler = ::classComponentRedirectOnFallbackResponseHandler,
)
    .decodeFromJsonString<OrganizationDto>()

/**
 * @param name contest name
 * @return contestDTO
 */
suspend fun ComponentWithScope<*, *>.getContest(name: String) = get(
    "$apiUrl/contests/$name",
    Headers().apply {
        set("Accept", "application/json")
    },
    loadingHandler = ::classLoadingHandler,
    responseHandler = ::classComponentRedirectOnFallbackResponseHandler,
)
    .decodeFromJsonString<ContestDto>()

/**
 * @param name username
 * @return info about user
 */
suspend fun ComponentWithScope<*, *>.getUser(name: String) = get(
    "$apiUrl/users/$name",
    Headers().apply {
        set("Accept", "application/json")
    },
    loadingHandler = ::classLoadingHandler,
)
    .decodeFromJsonString<UserInfo>()

/**
 * @param file image file
 * @param name avatar owner name
 * @param type avatar type
 */
suspend fun ComponentWithScope<*, *>.postImageUpload(
    file: File,
    name: String,
    type: AvatarType,
) {
    val response = post(
        "$apiUrl/avatar/upload?owner=$name&type=$type",
        Headers(),
        FormData().apply {
            append("file", file)
        },
        loadingHandler = ::noopLoadingHandler,
    )
    if (response.ok) {
        window.location.reload()
    }
}

/**
 * Fetch debug info for test execution
 *
 * @param testExecutionDto
 * @return Response
 */
@Suppress("TYPE_ALIAS")
suspend fun ComponentWithScope<*, *>.getDebugInfoFor(
    testExecutionDto: TestExecutionDto,
) = getDebugInfoFor(testExecutionDto, this::get)

/**
 * Fetch debug info for test execution
 *
 * @param testExecutionDto
 * @return Response
 */
suspend fun WithRequestStatusContext.getDebugInfoFor(
    testExecutionDto: TestExecutionDto,
) = getDebugInfoFor(testExecutionDto, this::get)

/**
 * Fetch execution info for test execution
 *
 * @param testExecutionDto
 * @return Response
 */
@Suppress("TYPE_ALIAS")
suspend fun ComponentWithScope<*, *>.getExecutionInfoFor(
    testExecutionDto: TestExecutionDto,
) = get(
    "$apiUrl/files/get-execution-info",
    params = jso<dynamic> {
        executionId = testExecutionDto.executionId
    },
    jsonHeaders,
    ::noopLoadingHandler,
    ::noopResponseHandler
)

@Suppress("TYPE_ALIAS")
private suspend fun getDebugInfoFor(
    testExecutionDto: TestExecutionDto,
    get: suspend (String, dynamic, Headers, suspend (suspend () -> Response) -> Response, (Response) -> Unit) -> Response,
) = get(
    "$apiUrl/files/get-debug-info",
    jso {
        testExecutionId = testExecutionDto.requiredId()
    },
    jsonHeaders,
    ::noopLoadingHandler,
    ::noopResponseHandler,
)
