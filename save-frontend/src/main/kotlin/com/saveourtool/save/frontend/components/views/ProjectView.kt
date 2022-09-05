/**
 * A view with project details
 */

@file:Suppress("WildcardImport", "FILE_WILDCARD_IMPORTS", "LargeClass")

package com.saveourtool.save.frontend.components.views

import com.saveourtool.save.domain.*
import com.saveourtool.save.entities.*
import com.saveourtool.save.execution.ExecutionDto
import com.saveourtool.save.execution.TestingType
import com.saveourtool.save.frontend.components.RequestStatusContext
import com.saveourtool.save.frontend.components.basic.*
import com.saveourtool.save.frontend.components.basic.projects.projectInfoMenu
import com.saveourtool.save.frontend.components.basic.projects.projectSettingsMenu
import com.saveourtool.save.frontend.components.basic.projects.projectStatisticMenu
import com.saveourtool.save.frontend.components.modal.displayModal
import com.saveourtool.save.frontend.components.modal.mediumTransparentModalStyle
import com.saveourtool.save.frontend.components.requestStatusContext
import com.saveourtool.save.frontend.externals.fontawesome.faCalendarAlt
import com.saveourtool.save.frontend.externals.fontawesome.faEdit
import com.saveourtool.save.frontend.externals.fontawesome.faHistory
import com.saveourtool.save.frontend.externals.fontawesome.fontAwesomeIcon
import com.saveourtool.save.frontend.http.getProject
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.frontend.utils.noopResponseHandler
import com.saveourtool.save.info.UserInfo
import com.saveourtool.save.testsuite.TestSuiteDto
import com.saveourtool.save.utils.getHighestRole

import csstype.ClassName
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.fetch.Headers
import org.w3c.fetch.Response
import org.w3c.xhr.FormData
import react.*
import react.dom.html.ButtonType
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.nav
import react.dom.html.ReactHTML.p

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * `Props` retrieved from router
 */
@Suppress("MISSING_KDOC_CLASS_ELEMENTS")
external interface ProjectExecutionRouteProps : PropsWithChildren {
    var owner: String
    var name: String
    var currentUserInfo: UserInfo?
}

/**
 * [State] of project view component for CONTEST run
 */
external interface ContestRunState : State {
    /**
     * Currently selected contest
     */
    var selectedContest: ContestDto

    /**
     * All available contest
     */
    var availableContests: List<ContestDto>
}

/**
 * [State] of project view component
 */
external interface ProjectViewState : StateWithRole, ContestRunState {
    /**
     * Currently loaded for display Project
     */
    var project: Project

    /**
     * Files required for tests execution for this project
     */
    var files: MutableList<FileInfo>

    /**
     * Files that are available on server side
     */
    var availableFiles: MutableList<FileInfo>

    /**
     * Message of error
     */
    var errorMessage: String

    /**
     * Flag to handle error
     */
    var isErrorOpen: Boolean

    /**
     * Error label
     */
    var errorLabel: String

    /**
     * Message of warning
     */
    var confirmMessage: String

    /**
     * Flag to handle confirm Window
     */
    var isConfirmWindowOpen: Boolean?

    /**
     * Label of confirm Window
     */
    var confirmLabel: String

    /**
     * Selected sdk
     */
    var selectedSdk: String

    /**
     * Selected version
     */
    var selectedSdkVersion: String

    /**
     * Flag to handle upload type project
     */
    var testingType: TestingType

    /**
     * Submit button was pressed
     */
    var isSubmitButtonPressed: Boolean?

    /**
     * State for the creation of unified confirmation logic
     */
    var confirmationType: ConfirmationType

    /**
     * List of IDs of private [TestSuiteDto] for execution run
     */
    var selectedPrivateTestSuiteIds: List<Long>

    /**
     * List of IDs of public [TestSuiteDto] for execution run
     */
    var selectedPublicTestSuiteIds: List<Long>

    /**
     * Execution command for standard mode
     */
    var execCmd: String

    /**
     * Batch size for static analyzer tool in standard mode
     */
    var batchSizeForAnalyzer: String

    /**
     * General size of test suite in bytes
     */
    var suiteByteSize: Long

    /**
     * Bytes received by server
     */
    var bytesReceived: Long

    /**
     * Flag to handle uploading a file
     */
    var isUploading: Boolean?

    /**
     * Whether editing of project info is disabled
     */
    var isEditDisabled: Boolean?

    /**
     * project selected menu
     */
    var selectedMenu: ProjectMenuBar?

    /**
     * latest execution id for this project
     */
    var latestExecutionId: Long?

    /**
     * Label that will be shown on close button
     */
    var closeButtonLabel: String?
}

/**
 * A Component for project view
 * Each modal opening call causes re-render of the whole page, that's why we need to use state for all fields
 */
@JsExport
@OptIn(ExperimentalJsExport::class)
@Suppress("MAGIC_NUMBER")
class ProjectView : AbstractView<ProjectExecutionRouteProps, ProjectViewState>(false) {
    private val date = LocalDateTime(1970, Month.JANUARY, 1, 0, 0, 1)
    private val projectInfo = projectInfo(
        turnEditMode = ::turnEditMode,
        onProjectSave = { draftProject, setDraftProject ->
            if (draftProject != state.project) {
                scope.launch {
                    val response = updateProject(draftProject!!)
                    if (response.ok) {
                        setState {
                            project = draftProject
                        }
                    } else {
                        // rollback form content
                        setDraftProject(state.project)
                    }
                }
            }
        },
    )
    private val projectInfoCard = cardComponent(isBordered = true, hasBg = true)
    private val typeSelection = cardComponent()
    private lateinit var responseFromDeleteProject: Response

    init {
        state.project = Project(
            "N/A",
            "N/A",
            "N/A",
            ProjectStatus.CREATED,
            userId = -1,
            organization = Organization("stub", OrganizationStatus.CREATED, null, date)
        )
        state.selectedContest = ContestDto.empty
        state.availableContests = emptyList()
        state.selectedPrivateTestSuiteIds = emptyList()
        state.selectedPublicTestSuiteIds = emptyList()
        state.execCmd = ""
        state.batchSizeForAnalyzer = ""
        state.confirmationType = ConfirmationType.NO_CONFIRM
        state.testingType = TestingType.PRIVATE_TESTS
        state.selectedContest = ContestDto.empty
        state.availableContests = emptyList()
        state.isErrorOpen = false
        state.isSubmitButtonPressed = false
        state.errorMessage = ""
        state.errorLabel = ""
        state.files = mutableListOf()
        state.availableFiles = mutableListOf()
        state.selectedSdk = Sdk.Default.name
        state.selectedSdkVersion = Sdk.Default.version
        state.suiteByteSize = state.files.sumOf { it.sizeBytes }
        state.bytesReceived = state.availableFiles.sumOf { it.sizeBytes }
        state.isUploading = false
        state.isEditDisabled = true
        state.selectedMenu = ProjectMenuBar.INFO
        state.closeButtonLabel = null
        state.selfRole = Role.NONE
    }

    private fun showNotification(notificationLabel: String, notificationMessage: String) {
        setState {
            isErrorOpen = true
            errorLabel = notificationLabel
            errorMessage = notificationMessage
            closeButtonLabel = "Confirm"
        }
    }

    @Suppress("TOO_LONG_FUNCTION")
    override fun componentDidMount() {
        super.componentDidMount()

        scope.launch {
            val result = getProject(props.name, props.owner)
            val project = if (result.isFailure) {
                return@launch
            } else {
                result.getOrThrow()
            }
            setState {
                this.project = project
            }
            val headers = Headers().apply {
                set("Accept", "application/json")
                set("Content-Type", "application/json")
            }
            val currentUserRole: Role = get(
                "$apiUrl/projects/${project.organization.name}/${project.name}/users/roles",
                headers,
                loadingHandler = ::classLoadingHandler,
            ).decodeFromJsonString()
            setState {
                selfRole = getHighestRole(currentUserRole, props.currentUserInfo?.globalRole)
            }

            val availableFiles = getFilesList(project.organization.name, project.name)
            setState {
                this.availableFiles.clear()
                this.availableFiles.addAll(availableFiles)
            }

            val contests = getContests()
            setState {
                availableContests = contests
                contests.firstOrNull()?.let { selectedContest = it }
            }

            fetchLatestExecutionId()
        }
    }

    @Suppress("ComplexMethod", "TOO_LONG_FUNCTION")
    private fun submitExecutionRequest() {
        when (state.testingType) {
            TestingType.PRIVATE_TESTS -> submitExecutionRequestByTestSuiteIds(state.selectedPrivateTestSuiteIds, state.testingType)
            TestingType.PUBLIC_TESTS -> submitExecutionRequestByTestSuiteIds(state.selectedPublicTestSuiteIds, state.testingType)
            TestingType.CONTEST_MODE -> submitExecutionRequestByTestSuiteIds(state.selectedContest.testSuiteIds, state.testingType)
            else -> throw IllegalStateException("Not supported testing type: ${state.testingType}")
        }
    }

    private fun submitExecutionRequestByTestSuiteIds(selectedTestSuiteIds: List<Long>, testingType: TestingType) {
        val selectedSdk = "${state.selectedSdk}:${state.selectedSdkVersion}".toSdk()
        val executionRequest = RunExecutionRequest(
            projectCoordinates = ProjectCoordinates(
                organizationName = state.project.organization.name,
                projectName = state.project.name
            ),
            testSuiteIds = selectedTestSuiteIds,
            files = state.files.map { it.toStorageKey() },
            sdk = selectedSdk,
            execCmd = state.execCmd.takeUnless { it.isBlank() },
            batchSizeForAnalyzer = state.batchSizeForAnalyzer.takeUnless { it.isBlank() }
        )
        submitRequest("/run/trigger?testingType=$testingType", jsonHeaders, Json.encodeToString(executionRequest))
    }

    private fun submitRequest(url: String, headers: Headers, body: dynamic) {
        scope.launch {
            val response = post(
                apiUrl + url,
                headers,
                body,
                loadingHandler = ::classLoadingHandler,
            )
            if (response.ok) {
                window.location.href = "${window.location}/history"
            }
        }
    }

    @Suppress("TOO_LONG_FUNCTION", "LongMethod", "ComplexMethod")
    override fun ChildrenBuilder.render() {
        val modalCloseCallback = {
            setState {
                isErrorOpen = false
                closeButtonLabel = null
            }
        }
        displayModal(
            state.isErrorOpen,
            state.errorLabel,
            state.errorMessage,
            mediumTransparentModalStyle,
            modalCloseCallback,
        ) {
            buttonBuilder(state.closeButtonLabel ?: "Close", "secondary") {
                modalCloseCallback()
            }
        }

        // Page Heading
        div {
            className = ClassName("d-sm-flex align-items-center justify-content-center mb-4")
            h1 {
                className = ClassName("h3 mb-0 text-gray-800")
                +" Project ${state.project.name}"
            }
            privacySpan(state.project)
        }

        div {
            className = ClassName("row align-items-center justify-content-center")
            nav {
                className = ClassName("nav nav-tabs mb-4")
                ProjectMenuBar.values()
                    .filterNot {
                        (it == ProjectMenuBar.RUN || it == ProjectMenuBar.SETTINGS) && !state.selfRole.isHigherOrEqualThan(Role.ADMIN)
                    }
                    .forEachIndexed { i, projectMenu ->
                        li {
                            className = ClassName("nav-item")
                            val classVal =
                                    if ((i == 0 && state.selectedMenu == null) || state.selectedMenu == projectMenu) " active font-weight-bold" else ""
                            p {
                                className = ClassName("nav-link $classVal text-gray-800")
                                onClick = {
                                    if (state.selectedMenu != projectMenu) {
                                        setState {
                                            selectedMenu = projectMenu
                                        }
                                    }
                                }
                                +projectMenu.name
                            }
                        }
                    }
            }
        }

        when (state.selectedMenu!!) {
            ProjectMenuBar.RUN -> renderRun()
            ProjectMenuBar.STATISTICS -> renderStatistics()
            ProjectMenuBar.SETTINGS -> renderSettings()
            ProjectMenuBar.INFO -> renderInfo()
            else -> {
                // this is a generated else block
            }
        }
    }

    @Suppress("TOO_LONG_FUNCTION", "LongMethod")
    private fun ChildrenBuilder.renderRun() {
        div {
            className = ClassName("row justify-content-center ml-5")
            // ===================== LEFT COLUMN =======================================================================
            div {
                className = ClassName("col-2 mr-3")
                div {
                    className = ClassName("text-xs text-center font-weight-bold text-primary text-uppercase mb-3")
                    +"Testing types"
                }

                typeSelection {
                    div {
                        className = ClassName("text-left")
                        testingTypeButton(
                            TestingType.PRIVATE_TESTS,
                            "Evaluate your tool with your own tests",
                            "mr-2"
                        )
                        testingTypeButton(
                            TestingType.PUBLIC_TESTS,
                            "Evaluate your tool with public test suites",
                            "mt-3 mr-2"
                        )
                        if (state.project.public) {
                            testingTypeButton(
                                TestingType.CONTEST_MODE,
                                "Participate in SAVE contests with your tool",
                                "mt-3 mr-2"
                            )
                        }
                    }
                }
            }
            // ===================== MIDDLE COLUMN =====================================================================
            div {
                className = ClassName("col-4")
                div {
                    className = ClassName("text-xs text-center font-weight-bold text-primary text-uppercase mb-3")
                    +"Test configuration"
                }

                // ======== file selector =========
                fileUploader {
                    isSubmitButtonPressed = state.isSubmitButtonPressed
                    files = state.files
                    availableFiles = state.availableFiles
                    suiteByteSize = state.suiteByteSize
                    bytesReceived = state.bytesReceived
                    isUploading = state.isUploading
                    projectCoordinates = ProjectCoordinates(props.owner, props.name)
                    onFileSelect = { element ->
                        setState {
                            val availableFile = availableFiles.first { it.name == element.value }
                            files.add(availableFile)
                            bytesReceived += availableFile.sizeBytes
                            suiteByteSize += availableFile.sizeBytes
                            availableFiles.remove(availableFile)
                        }
                    }
                    onFileRemove = {
                        setState {
                            files.remove(it)
                            bytesReceived -= it.sizeBytes
                            suiteByteSize -= it.sizeBytes
                            availableFiles.add(it)
                        }
                    }
                    onFileInput = { postFileUpload(it) }
                    onFileDelete = { postFileDelete(it) }
                    onExecutableChange = { selectedFile, checked ->
                        setState {
                            files[files.indexOf(selectedFile)] = selectedFile.copy(isExecutable = checked)
                        }
                    }
                }

                // ======== sdk selection =========
                sdkSelection {
                    selectedSdk = state.selectedSdk
                    selectedSdkVersion = state.selectedSdkVersion
                    onSdkChange = {
                        setState {
                            selectedSdk = it.value
                            selectedSdkVersion = selectedSdk.getSdkVersions().first()
                        }
                    }
                    onVersionChange = { setState { selectedSdkVersion = it.value } }
                }

                // ======== test resources selection =========
                testResourcesSelection {
                    testingType = state.testingType
                    isSubmitButtonPressed = state.isSubmitButtonPressed
                    // properties for CONTEST_TESTS mode
                    projectName = props.name
                    organizationName = props.owner
                    onContestEnrollerResponse = {
                        setState {
                            isErrorOpen = true
                            errorMessage = it
                            errorLabel = "Contest enrollment"
                        }
                    }
                    selectedContest = state.selectedContest
                    setSelectedContest = { selectedContest ->
                        setState {
                            this.selectedContest = selectedContest
                        }
                    }
                    availableContests = state.availableContests
                    // properties for PRIVATE_TESTS mode
                    selectedPrivateTestSuiteIds = state.selectedPrivateTestSuiteIds
                    setSelectedPrivateTestSuiteIds = { selectedTestSuiteIds ->
                        setState {
                            this.selectedPrivateTestSuiteIds = selectedTestSuiteIds
                        }
                    }
                    // properties for PUBLIC_TESTS mode
                    selectedPublicTestSuiteIds = state.selectedPublicTestSuiteIds
                    setSelectedPublicTestSuiteIds = { selectedTestSuiteIds ->
                        setState {
                            this.selectedPublicTestSuiteIds = selectedTestSuiteIds
                        }
                    }
                    // properties for PRIVATE_TESTS and PUBLIC_TESTS modes
                    execCmd = state.execCmd
                    setExecCmd = { execCmd ->
                        setState {
                            this.execCmd = execCmd
                        }
                    }
                    batchSizeForAnalyzer = state.batchSizeForAnalyzer
                    setBatchSizeForAnalyzer = { batchSizeForAnalyzer ->
                        setState {
                            this.batchSizeForAnalyzer = batchSizeForAnalyzer
                        }
                    }
                }

                div {
                    className = ClassName("d-sm-flex align-items-center justify-content-center")
                    button {
                        type = ButtonType.button
                        className = ClassName("btn btn-primary")
                        onClick = { submitWithValidation() }
                        +"Test the tool now"
                    }
                }
            }
            // ===================== RIGHT COLUMN ======================================================================
            div {
                className = ClassName("col-3 ml-2")
                div {
                    className = ClassName("text-xs text-center font-weight-bold text-primary text-uppercase mb-3")
                    +"Information"
                    button {
                        className = ClassName("btn btn-link text-xs text-muted text-left p-1 ml-2")
                        +"Edit  "
                        fontAwesomeIcon(icon = faEdit)
                        onClick = {
                            turnEditMode(isOff = false)
                        }
                    }
                }

                projectInfoCard {
                    projectInfo {
                        project = state.project
                        isEditDisabled = state.isEditDisabled
                    }

                    div {
                        className = ClassName("ml-3 mt-2 align-items-left justify-content-between")
                        fontAwesomeIcon(icon = faHistory)

                        button {
                            className = ClassName("btn btn-link text-left")
                            +"Latest Execution"
                            disabled = state.latestExecutionId == null

                            onClick = {
                                window.location.href = "${window.location}/history/execution/${state.latestExecutionId}"
                            }
                        }
                    }
                    div {
                        className = ClassName("ml-3 align-items-left")
                        fontAwesomeIcon(icon = faCalendarAlt)
                        a {
                            href = "#/${state.project.organization.name}/${state.project.name}/history"
                            className = ClassName("btn btn-link text-left")
                            +"Execution History"
                        }
                    }
                }
            }
        }
    }

    private fun ChildrenBuilder.renderStatistics() {
        projectStatisticMenu {
            executionId = state.latestExecutionId
        }
    }

    private fun ChildrenBuilder.renderInfo() {
        projectInfoMenu {
            projectName = props.name
            organizationName = props.owner
            latestExecutionId = state.latestExecutionId
        }
    }

    private fun ChildrenBuilder.renderSettings() {
        projectSettingsMenu {
            project = state.project
            currentUserInfo = props.currentUserInfo ?: UserInfo("Unknown")
            selfRole = state.selfRole
            deleteProjectCallback = ::deleteProject
            updateProjectSettings = { project ->
                scope.launch {
                    val response = updateProject(project)
                    if (response.ok) {
                        setState {
                            this.project = project
                        }
                    }
                }
            }
            updateErrorMessage = {
                setState {
                    errorLabel = "Failed to save project info"
                    errorMessage = "Failed to save project info: ${it.status} ${it.statusText}"
                    isErrorOpen = true
                }
            }
            updateNotificationMessage = ::showNotification
        }
    }

    private fun fileDelete(file: FileInfo) {
        scope.launch {
            val response = delete(
                "$apiUrl/files/${props.owner}/${props.name}/${file.uploadedMillis}",
                jsonHeaders,
                Json.encodeToString(file),
                loadingHandler = ::noopLoadingHandler,
            )

            if (response.ok) {
                setState {
                    files.remove(file)
                    bytesReceived -= file.sizeBytes
                    suiteByteSize -= file.sizeBytes
                }
            }
        }
    }

    private fun postFileDelete(fileForDelete: FileInfo) {
        val confirm = window.confirm(
            "Are you sure you want to delete ${fileForDelete.name} file?"
        )

        if (confirm) {
            fileDelete(fileForDelete)
        }
    }

    private fun postFileUpload(element: HTMLInputElement) =
            scope.launch {
                setState {
                    isUploading = true
                    element.files!!.asList().forEach { file ->
                        suiteByteSize += file.size.toLong()
                    }
                }

                element.files!!.asList().forEach { file ->
                    val response: FileInfo = post(
                        "$apiUrl/files/${props.owner}/${props.name}/upload?returnShortFileInfo=false",
                        Headers(),
                        FormData().apply {
                            append("file", file)
                        },
                        loadingHandler = ::noopLoadingHandler,
                    )
                        .decodeFromJsonString()

                    setState {
                        // add only to selected files so that this entry isn't duplicated
                        files.add(response)
                        bytesReceived += response.sizeBytes
                    }
                }
                setState {
                    isUploading = false
                }
            }

    private fun turnEditMode(isOff: Boolean) {
        setState {
            isEditDisabled = isOff
        }
        (document.getElementById("Save new project info") as HTMLButtonElement).hidden = isOff
        (document.getElementById("Cancel") as HTMLButtonElement).hidden = isOff
    }

    private fun ChildrenBuilder.testingTypeButton(selectedTestingType: TestingType, text: String, divClass: String) {
        div {
            className = ClassName(divClass)
            button {
                type = ButtonType.button
                className =
                        if (state.testingType == selectedTestingType) {
                            ClassName("btn btn-primary")
                        } else {
                            ClassName("btn btn-outline-primary")
                        }
                onClick = {
                    setState {
                        testingType = selectedTestingType
                    }
                }
                +text
            }
        }
    }

    /**
     * In some cases scripts and binaries can be uploaded to a git repository, so users won't be providing or uploading
     * binaries. For this case we should open a window, so user will need to click a checkbox, so he will confirm that
     * he understand what he is doing.
     */
    private fun submitWithValidation() {
        setState {
            isSubmitButtonPressed = true
        }
        when {
            // no binaries were provided
            state.files.isEmpty() -> setState {
                confirmationType = ConfirmationType.NO_BINARY_CONFIRM
                isConfirmWindowOpen = true
                confirmLabel = "Single binary confirmation"
                confirmMessage = "You have not provided any files related to your tested tool." +
                        " If these files were uploaded to your repository - press OK, otherwise - please upload these files using 'Upload files' button."
            }
            // everything is in place, can proceed
            else -> submitExecutionRequest()
        }
    }

    private fun deleteProject() {
        val newProject = state.project.copy(status = ProjectStatus.DELETED)

        setState {
            project = newProject
            confirmationType = ConfirmationType.DELETE_CONFIRM
            isConfirmWindowOpen = true
            confirmLabel = ""
            confirmMessage = "Are you sure you want to delete this project?"
        }
    }

    @Suppress("COMMENTED_OUT_CODE")
    private suspend fun updateProject(draftProject: Project): Response {
        val headers = Headers().also {
            it.set("Accept", "application/json")
            it.set("Content-Type", "application/json")
        }
        return post(
            "$apiUrl/projects/update",
            headers,
            Json.encodeToString(draftProject.toDto()),
            loadingHandler = ::noopLoadingHandler,
        )
    }

    private fun deleteProjectBuilder() {
        val headers = Headers().also {
            it.set("Accept", "application/json")
            it.set("Content-Type", "application/json")
        }
        scope.launch {
            responseFromDeleteProject =
                    delete(
                        "$apiUrl/projects/${state.project.organization.name}/${state.project.name}/delete",
                        headers,
                        body = undefined,
                        loadingHandler = ::noopLoadingHandler,
                    )
        }.invokeOnCompletion {
            if (responseFromDeleteProject.ok) {
                window.location.href = "${window.location.origin}/"
            }
        }
    }

    private suspend fun fetchLatestExecutionId() {
        val headers = Headers().apply { set("Accept", "application/json") }
        val response = get(
            "$apiUrl/latestExecution?name=${state.project.name}&organizationName=${state.project.organization.name}",
            headers,
            loadingHandler = ::noopLoadingHandler,
            responseHandler = ::noopResponseHandler,
        )
        when {
            !response.ok -> setState {
                errorLabel = "Failed to fetch latest execution"
                errorMessage =
                        "Failed to fetch latest execution: [${response.status}] ${response.statusText}, please refresh the page and try again"
                latestExecutionId = null
            }
            response.status == 204.toShort() -> setState {
                latestExecutionId = null
            }
            else -> {
                val executionIdFromResponse: Long = response
                    .decodeFromJsonString<ExecutionDto>().id

                setState {
                    latestExecutionId = executionIdFromResponse
                }
            }
        }
    }

    private suspend fun getFilesList(
        organizationName: String,
        projectName: String,
    ) = get(
        "$apiUrl/files/$organizationName/$projectName/list",
        Headers(),
        loadingHandler = ::noopLoadingHandler,
    )
        .unsafeMap {
            it.decodeFromJsonString<List<FileInfo>>()
        }

    private suspend fun getContests() = get(
        "$apiUrl/contests/active",
        Headers(),
        loadingHandler = ::noopLoadingHandler,
    )
        .unsafeMap {
            it.decodeFromJsonString<List<ContestDto>>()
        }

    companion object :
        RStatics<ProjectExecutionRouteProps, ProjectViewState, ProjectView, Context<RequestStatusContext>>(ProjectView::class) {
        const val TEST_ROOT_DIR_HINT = """
            The path you are providing should be relative to the root directory of your repository.
            This directory should contain <a href = "https://github.com/saveourtool/save#how-to-configure"> save.properties </a>
            or <a href = "https://github.com/saveourtool/save-cli#-savetoml-configuration-file">save.toml</a> files.
            For example, if the URL to your repo with tests is: 
            <a href ="https://github.com/saveourtool/save-cli/">https://github.com/saveourtool/save</a>, then
            you need to specify the following directory with 'save.toml': 
            <a href ="https://github.com/saveourtool/save-cli/tree/main/examples/kotlin-diktat">examples/kotlin-diktat/</a>.
 
            Please note, that the tested tool and it's resources will be copied to this directory before the run.
            """

        init {
            contextType = requestStatusContext
        }
    }
}
