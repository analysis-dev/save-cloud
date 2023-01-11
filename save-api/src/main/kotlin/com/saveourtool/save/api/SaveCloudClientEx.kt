@file:Suppress("TYPE_ALIAS")

package com.saveourtool.save.api

import com.saveourtool.save.agent.TestExecutionExtDto
import com.saveourtool.save.api.errors.SaveCloudError
import com.saveourtool.save.api.impl.DefaultSaveCloudClient
import com.saveourtool.save.domain.FileInfo
import com.saveourtool.save.domain.FileKey
import com.saveourtool.save.entities.ContestDto
import com.saveourtool.save.entities.Organization
import com.saveourtool.save.entities.ProjectDto
import com.saveourtool.save.entities.ProjectStatus.CREATED
import com.saveourtool.save.execution.ExecutionDto
import com.saveourtool.save.permission.Permission.READ
import com.saveourtool.save.request.CreateExecutionRequest
import com.saveourtool.save.testsuite.TestSuiteDto
import arrow.core.Either
import io.ktor.client.plugins.auth.Auth
import io.ktor.http.ContentType
import java.net.URL
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MINUTES
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

/**
 * _SAVE_ REST API client.
 */
interface SaveCloudClientEx {
    /**
     * Lists the top-level organizations registered in this _SAVE_ instance.
     *
     * @return either the list of organizations, or the error if an error has
     *  occurred.
     */
    suspend fun listOrganizations(): Either<SaveCloudError, List<Organization>>

    /**
     * Lists the existing (i.e. [non-deleted][CREATED]) projects within the organization.
     *
     * @param organizationName the organization name.
     * @return either the list of projects, or the error if an error has
     *  occurred.
     * @see Organization.listProjects
     */
    suspend fun listProjects(organizationName: String): Either<SaveCloudError, List<ProjectDto>>

    /**
     * Lists test suites within the organization, [readable][READ] for the current user.
     *
     * @param organizationName the organization name.
     * @return either the list of test suites, or the error if an error has
     *  occurred.
     * @see Organization.listTestSuites
     */
    suspend fun listTestSuites(organizationName: String): Either<SaveCloudError, List<TestSuiteDto>>

    /**
     * Lists uploaded files within the project.
     *
     * @param organizationName the organization name.
     * @param projectName the name of the project.
     * @return either the list of files, or the error if an error has occurred.
     * @see Organization.listFiles
     */
    suspend fun listFiles(organizationName: String, projectName: String): Either<SaveCloudError, List<FileInfo>>

    /**
     * Uploads a local file.
     *
     * @param organizationName the organization name.
     * @param projectName the name of the project.
     * @param file the local file.
     * @param contentType the MIME `Content-Type`, or `null` if unknown.
     * @param stripVersionFromName whether to strip the version number from the
     *   name of the file. For example, `diktat-1.2.3.jar` can be uploaded as
     *   `diktat.jar`.
     * @return the descriptor of the uploaded file.
     * @throws IllegalArgumentException if [file] is not a regular file.
     * @see Organization.uploadFile
     */
    suspend fun uploadFile(
        organizationName: String,
        projectName: String,
        file: Path,
        contentType: ContentType? = null,
        stripVersionFromName: Boolean = false
    ): Either<SaveCloudError, FileInfo>

    /**
     * @param organizationName the organization name.
     * @param projectName the name of the project.
     * @param contestName the optional name of the contest.
     * @return either the list of executions, or the error if an error has
     *  occurred.
     * @see Organization.listExecutions
     */
    suspend fun listExecutions(
        organizationName: String,
        projectName: String,
        contestName: String? = null
    ): Either<SaveCloudError, List<ExecutionDto>>

    /**
     * @param request the test execution request.
     * @param timeoutValue the timeout value.
     * @param timeoutUnit the timeout unit.
     * @return the test execution descriptor.
     */
    suspend fun submitExecution(
        request: CreateExecutionRequest,
        timeoutValue: Long = 5L,
        timeoutUnit: TimeUnit = MINUTES
    ): Either<SaveCloudError, ExecutionDto>

    /**
     * @param id the identifier which uniquely identifies the execution.
     * @return the execution identified by [id].
     */
    suspend fun getExecutionById(id: Long): Either<SaveCloudError, ExecutionDto>

    /**
     * @param organizationName the organization name.
     * @param projectName the name of the project.
     * @param fileName the name of the file to delete.
     * @param fileTimestamp the timestamp of the file to delete.
     * @return [Unit], or the error if an error has occurred.
     * @see Organization.deleteFile
     */
    suspend fun deleteFile(
        organizationName: String,
        projectName: String,
        fileName: String,
        fileTimestamp: Long
    ): Either<SaveCloudError, Unit>

    /**
     * Lists the active contests which the project specified by [projectName]
     * has enrolled in.
     *
     * @param organizationName the organization name.
     * @param projectName the name of the project.
     * @param limit the maxim number of contests returned.
     * @return either the list of active contests, or the error if an error has
     *  occurred.
     * @see Organization.listActiveContests
     */
    suspend fun listActiveContests(
        organizationName: String,
        projectName: String,
        limit: Int = Int.MAX_VALUE
    ): Either<SaveCloudError, List<ContestDto>>

    /**
     * Lists test runs (along with their results) for the batch execution
     * specified by [executionId].
     *
     * @param executionId the identifier which uniquely identifies the batch
     *   execution.
     * @return either the list of test runs, or the error if an error has
     *   occurred.
     * @see ExecutionDto.listTestRuns
     */
    suspend fun listTestRuns(executionId: Long): Either<SaveCloudError, List<TestExecutionExtDto>>

    /**
     * Lists projects within this organization.
     *
     * @return either the list of projects, or the error if an error has
     *  occurred.
     * @see SaveCloudClientEx.listProjects
     */
    suspend fun Organization.listProjects(): Either<SaveCloudError, List<ProjectDto>> =
            listProjects(organizationName = name)

    /**
     * Lists test suites within this organization.
     *
     * @return either the list of test suites, or the error if an error has
     *  occurred.
     * @see SaveCloudClientEx.listTestSuites
     */
    suspend fun Organization.listTestSuites(): Either<SaveCloudError, List<TestSuiteDto>> =
            listTestSuites(organizationName = name)

    /**
     * Lists uploaded files within the project.
     *
     * @param projectName the name of the project.
     * @return either the list of files, or the error if an error has occurred.
     * @see SaveCloudClientEx.listFiles
     */
    suspend fun Organization.listFiles(projectName: String): Either<SaveCloudError, List<FileInfo>> =
            listFiles(organizationName = name, projectName)

    /**
     * Uploads a local file.
     *
     * @param projectName the name of the project.
     * @param file the local file.
     * @param contentType the MIME `Content-Type`, or `null` if unknown.
     * @param stripVersionFromName whether to strip the version number from the
     *   name of the file. For example, `diktat-1.2.3.jar` can be uploaded as
     *   `diktat.jar`.
     * @return the descriptor of the uploaded file.
     * @throws IllegalArgumentException if [file] is not a regular file.
     * @see SaveCloudClientEx.uploadFile
     */
    suspend fun Organization.uploadFile(
        projectName: String,
        file: Path,
        contentType: ContentType? = null,
        stripVersionFromName: Boolean = false
    ): Either<SaveCloudError, FileInfo> =
            uploadFile(
                organizationName = name,
                projectName,
                file,
                contentType,
                stripVersionFromName
            )

    /**
     * @param projectName the name of the project.
     * @param contestName the optional name of the contest.
     * @return either the list of executions, or the error if an error has
     *  occurred.
     * @see SaveCloudClientEx.listExecutions
     */
    suspend fun Organization.listExecutions(
        projectName: String,
        contestName: String? = null,
    ): Either<SaveCloudError, List<ExecutionDto>> =
            listExecutions(
                organizationName = name,
                projectName,
                contestName,
            )

    /**
     * @param projectName the name of the project.
     * @param fileKey the file descriptor.
     * @return [Unit], or the error if an error has occurred.
     * @see SaveCloudClientEx.deleteFile
     */
    suspend fun Organization.deleteFile(
        projectName: String,
        fileKey: FileKey
    ): Either<SaveCloudError, Unit> =
            deleteFile(
                organizationName = name,
                projectName,
                fileKey.name,
                fileKey.uploadedMillis
            )

    /**
     * Lists the active contests which the project specified by [projectName]
     * has enrolled in.
     *
     * @param projectName the name of the project.
     * @param limit the maxim number of contests returned.
     * @return either the list of active contests, or the error if an error has
     *  occurred.
     * @see SaveCloudClientEx.listActiveContests
     */
    suspend fun Organization.listActiveContests(
        projectName: String,
        limit: Int = Int.MAX_VALUE
    ): Either<SaveCloudError, List<ContestDto>> =
            listActiveContests(
                organizationName = name,
                projectName,
                limit
            )

    /**
     * Lists test runs (along with their results) for this batch execution.
     *
     * @return either the list of test runs, or the error if an error has
     *   occurred.
     * @see SaveCloudClientEx.listTestRuns
     */
    suspend fun ExecutionDto.listTestRuns(): Either<SaveCloudError, List<TestExecutionExtDto>> =
            listTestRuns(id)

    /**
     * The factory object.
     */
    companion object Factory {
        private const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 100_000L
        private const val DEFAULT_SOCKET_TIMEOUT_MILLIS = 100_000L

        /**
         * Creates a new client instance.
         *
         * @param backendUrl the URL of a _SAVE_ backend (e.g.:
         *   `http://localhost:5800`) or a _SAVE_ gateway (e.g.:
         *   `http://localhost:5300`).
         * @param ioContext the context to be used for I/O, defaults to
         *   [Dispatchers.IO].
         * @param requestTimeoutMillis HTTP request timeout, in milliseconds.
         *   Should be large enough, otherwise the process of uploading large
         *   files may fail.
         * @param socketTimeoutMillis TCP socket timeout, in milliseconds.
         * @param authConfiguration authentication configuration.
         * @return the newly created client instance.
         */
        operator fun invoke(
            backendUrl: URL,
            ioContext: CoroutineContext = Dispatchers.IO,
            requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
            socketTimeoutMillis: Long = DEFAULT_SOCKET_TIMEOUT_MILLIS,
            authConfiguration: Auth.() -> Unit
        ): SaveCloudClientEx =
                DefaultSaveCloudClient(
                    backendUrl,
                    ioContext,
                    requestTimeoutMillis,
                    socketTimeoutMillis,
                    authConfiguration
                )
    }
}
