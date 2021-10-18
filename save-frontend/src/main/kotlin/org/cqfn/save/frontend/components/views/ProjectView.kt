/**
 * A view with project details
 */

package org.cqfn.save.frontend.components.views

import org.cqfn.save.domain.FileInfo
import org.cqfn.save.domain.Sdk
import org.cqfn.save.domain.getSdkVersions
import org.cqfn.save.domain.toSdk
import org.cqfn.save.entities.ExecutionRequest
import org.cqfn.save.entities.ExecutionRequestForStandardSuites
import org.cqfn.save.entities.GitDto
import org.cqfn.save.entities.Project
import org.cqfn.save.execution.ExecutionDto
import org.cqfn.save.frontend.components.basic.cardComponent
import org.cqfn.save.frontend.components.basic.checkBoxGrid
import org.cqfn.save.frontend.components.basic.fileUploader
import org.cqfn.save.frontend.components.basic.sdkSelection
import org.cqfn.save.frontend.externals.fontawesome.faCalendarAlt
import org.cqfn.save.frontend.externals.fontawesome.faHistory
import org.cqfn.save.frontend.externals.fontawesome.fontAwesomeIcon
import org.cqfn.save.frontend.externals.modal.modal
import org.cqfn.save.frontend.utils.appendJson
import org.cqfn.save.frontend.utils.decodeFromJsonString
import org.cqfn.save.frontend.utils.get
import org.cqfn.save.frontend.utils.getProject
import org.cqfn.save.frontend.utils.post
import org.cqfn.save.frontend.utils.runErrorModal
import org.cqfn.save.frontend.utils.unsafeMap
import org.cqfn.save.testsuite.TestSuiteDto

import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.fetch.Headers
import org.w3c.xhr.FormData
import react.PropsWithChildren
import react.RBuilder
import react.RComponent
import react.State
import react.dom.ReactHTML.b
import react.dom.a
import react.dom.attrs
import react.dom.button
import react.dom.defaultValue
import react.dom.div
import react.dom.h1
import react.dom.h6
import react.dom.input
import react.dom.span
import react.setState

import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.classes
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.html.role
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * `Props` retrieved from router
 */
@Suppress("MISSING_KDOC_CLASS_ELEMENTS")
external interface ProjectExecutionRouteProps : PropsWithChildren {
    var owner: String
    var name: String
}

/**
 * [State] of project view component
 */
external interface ProjectViewState : State {
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
    var isErrorOpen: Boolean?

    /**
     * Error label
     */
    var errorLabel: String

    /**
     * Flag to handle loading
     */
    var isLoading: Boolean?

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
    var isFirstTypeUpload: Boolean?
}

/**
 * A Component for project view
 * Each modal opening call causes re-render of the whole page, that's why we need to use state for all fields
 */
@JsExport
@OptIn(ExperimentalJsExport::class)
class ProjectView : RComponent<ProjectExecutionRouteProps, ProjectViewState>() {
    private var standardTestSuites: List<TestSuiteDto> = emptyList()
    private var pathToProperty: String? = null
    private var gitUrlFromInputField: String? = null
    private val selectedTypes: MutableList<String> = mutableListOf()
    private var gitDto: GitDto? = null
    private var project = Project("stub", "stub", "stub", "stub")

    init {
        state.isErrorOpen = false
        state.errorMessage = ""
        state.errorLabel = ""

        state.isFirstTypeUpload = true

        state.isLoading = true

        state.files = mutableListOf()
        state.availableFiles = mutableListOf()
        state.selectedSdk = Sdk.Default.name
        state.selectedSdkVersion = Sdk.Default.version
    }

    override fun componentDidMount() {
        GlobalScope.launch {
            project = getProject(props.name, props.owner)
            val jsonProject = Json.encodeToString(project)
            val headers = Headers().apply {
                set("Accept", "application/json")
                set("Content-Type", "application/json")
            }
            gitDto = post("${window.location.origin}/getGit", headers, jsonProject)
                .decodeFromJsonString<GitDto>()
            standardTestSuites = get("${window.location.origin}/allStandardTestSuites", headers)
                .decodeFromJsonString()

            val availableFiles = getFilesList()
            setState {
                this.availableFiles.clear()
                this.availableFiles.addAll(availableFiles)
                isLoading = false
            }
        }
    }

    @Suppress("ComplexMethod", "TOO_LONG_FUNCTION")
    private fun submitExecutionRequest() {
        if (state.isFirstTypeUpload == true) {
            gitUrlFromInputField?.let {
                val newGitDto = GitDto(url = it)
                submitExecutionRequestGit(newGitDto)
            } ?: gitDto?.let {
                submitExecutionRequestGit(it)
            } ?: setState {
                isErrorOpen = true
                errorLabel = "No git url"
                errorMessage = "Please provide a git url"
            }
        } else {
            if (selectedTypes.isEmpty()) {
                setState {
                    isErrorOpen = true
                    errorLabel = "Both type of project"
                    errorMessage = "Please choose one of type test suites"
                }
                return
            }
            if (state.files.isEmpty()) {
                setState {
                    isErrorOpen = true
                    errorLabel = "No files have been selected"
                    errorMessage = "Please provide files necessary for execution"
                }
                return
            }
            submitExecutionRequestBinFile()
        }
    }

    @Suppress("UnsafeCallOnNullableType")
    private fun submitExecutionRequestBinFile() {
        val headers = Headers()
        val formData = FormData()
        val selectedSdk = "${state.selectedSdk}:${state.selectedSdkVersion}".toSdk()
        val request = ExecutionRequestForStandardSuites(project, selectedTypes, selectedSdk)
        formData.appendJson("execution", request)
        state.files.forEach {
            formData.appendJson("file", it)
        }
        submitRequest("/submitExecutionRequestBin", headers, formData)
    }

    private fun submitExecutionRequestGit(correctGitDto: GitDto) {
        val selectedSdk = "${state.selectedSdk}:${state.selectedSdkVersion}".toSdk()
        val formData = FormData()
        val executionRequest = pathToProperty?.let {
            ExecutionRequest(
                project,
                correctGitDto,
                it,
                selectedSdk,
                null
            )
        } ?: ExecutionRequest(
            project,
            correctGitDto,
            sdk = selectedSdk,
            executionId = null
        )
        formData.appendJson("executionRequest", executionRequest)
        state.files.forEach {
            formData.appendJson("file", it)
        }
        submitRequest("/submitExecutionRequest", Headers(), formData)
    }

    private fun submitRequest(url: String, headers: Headers, body: dynamic) {
        setState {
            isLoading = true
        }
        GlobalScope.launch {
            val response = post(window.location.origin + url, headers, body)
            if (!response.ok) {
                response.text().then { text ->
                    setState {
                        isErrorOpen = true
                        errorLabel = "Error from backend"
                        errorMessage = "${response.statusText}: $text"
                    }
                }
            } else {
                window.location.href = "${window.location}/history"
            }
        }
            .invokeOnCompletion {
                setState {
                    isLoading = false
                }
            }
    }

    @Suppress("TOO_LONG_FUNCTION", "LongMethod", "ComplexMethod")
    override fun RBuilder.render() {
        // modal windows are initially hidden
        runErrorModal(state.isErrorOpen, state.errorLabel, state.errorMessage) {
            setState { isErrorOpen = false }
        }
        runLoadingModal()
        // Page Heading
        div("d-sm-flex align-items-center justify-content-center mb-4") {
            h1("h3 mb-0 text-gray-800") {
                +"Project ${project.name}"
            }
        }

        div("row justify-content-center") {
            // ===================== LEFT COLUMN =======================================================================
            div("col-2 mr-3") {
                div("text-xs text-center font-weight-bold text-primary text-uppercase mb-3") {
                    +"Types of testing"
                }

                child(cardComponent {
                    div("text-left") {
                        div("mr-2") {
                            button(type = ButtonType.button) {
                                attrs.classes =
                                        if (state.isFirstTypeUpload == true) {
                                            setOf("btn", "btn-primary")
                                        } else {
                                            setOf(
                                                "btn",
                                                "btn-outline-primary"
                                            )
                                        }
                                attrs.onClickFunction = {
                                    setState {
                                        isFirstTypeUpload = true
                                    }
                                }
                                +"Run your tool with your tests from git"
                            }
                        }
                        div("mt-3 mr-2") {
                            button(type = ButtonType.button, classes = "btn btn-link collapsed") {
                                attrs.classes =
                                        if (state.isFirstTypeUpload == true) {
                                            setOf("btn", "btn-outline-primary")
                                        } else {
                                            setOf(
                                                "btn",
                                                "btn-primary"
                                            )
                                        }
                                attrs.onClickFunction = {
                                    setState {
                                        isFirstTypeUpload = false
                                    }
                                }
                                +"Run your tool with standard suites"
                            }
                        }
                    }
                })
            }
            // ===================== MIDDLE COLUMN =====================================================================
            div("col-4") {
                div("text-xs text-center font-weight-bold text-primary text-uppercase mb-3") {
                    +"Test configuration"
                }

                child(fileUploader({ element ->
                    setState {
                        val availableFile = availableFiles.first { it.name == element.value }
                        files.add(availableFile)
                        availableFiles.remove(availableFile)
                    }
                }, {
                    setState {
                        files.remove(it)
                        this.availableFiles.add(it)
                    }
                }, { htmlInputElement ->
                    GlobalScope.launch {
                        setState {
                            isLoading = true
                        }
                        htmlInputElement.files!!.asList().forEach { file ->
                            val response: FileInfo = post(
                                "${window.location.origin}/files/upload",
                                Headers(),
                                FormData().apply {
                                    append("file", file)
                                }
                            )
                                .decodeFromJsonString()
                            setState {
                                // add only to selected files so that this entry isn't duplicated
                                files.add(response)
                            }
                        }
                        setState {
                            isLoading = false
                        }
                    }
                }, { selectedFile, checked ->
                    setState {
                        files[files.indexOf(selectedFile)] = selectedFile.copy(isExecutable = checked)
                    }
                })) {
                    attrs.files = state.files
                    attrs.availableFiles = state.availableFiles
                }

                h6(classes = "d-inline mr-3") {
                    +"2. Select the SDK that is needed (optional):"
                }

                child(sdkSelection({
                    setState {
                        selectedSdk = it.value
                        selectedSdkVersion = selectedSdk.getSdkVersions().first()
                    }
                }, {
                    setState { selectedSdkVersion = it.value }
                })) {
                    attrs.selectedSdk = state.selectedSdk
                    attrs.selectedSdkVersion = state.selectedSdkVersion
                }

                h6(classes = "d-inline") {
                    +"3. Setup test-resources that will be used to test provided tool:"
                }

                child(cardComponent {
                    div {
                        attrs.classes = if (state.isFirstTypeUpload == true) {
                            setOf(
                                "card",
                                "shadow",
                                "mb-4",
                                "w-100",
                            )
                        } else {
                            setOf("d-none")
                        }

                        div("card-body ") {
                            div("input-group-sm mb-3") {
                                div("row") {
                                    h6(classes = "d-inline ml-2") {
                                        +"Url of the git repository with your tests in save format:"
                                    }
                                }
                                div("input-group-prepend") {
                                    input(type = InputType.text) {
                                        attrs.set("class", "form-control")
                                        attrs {
                                            gitUrlFromInputField?.let {
                                                defaultValue = it
                                            } ?: gitDto?.url?.let {
                                                defaultValue = it
                                            }
                                            placeholder = "https://github.com/my-project"
                                            onChangeFunction = {
                                                val target = it.target as HTMLInputElement
                                                gitUrlFromInputField = target.value
                                            }
                                        }
                                    }
                                }
                            }

                            div("input-group-sm") {
                                div("row") {
                                    h6(classes = "d-inline ml-2") {
                                        +"Relative path to the root directory with tests in the repo:"
                                    }
                                }
                                div("input-group-prepend") {
                                    input(type = InputType.text, name = "itemText") {
                                        key = "itemText"
                                        attrs.set("class", "form-control")
                                        attrs {
                                            pathToProperty?.let {
                                                value = it
                                            }
                                            placeholder = "tests/my-save-tests/save.properties"
                                            onChangeFunction = {
                                                val target = it.target as HTMLInputElement
                                                pathToProperty = target.value
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    div {
                        attrs.classes = if (state.isFirstTypeUpload == false) {
                            setOf(
                                "card",
                                "shadow",
                                "mb-4",
                                "w-100",
                            )
                        } else {
                            setOf("d-none")
                        }
                        div("card-body") {
                            child(
                                checkBoxGrid(
                                    standardTestSuites
                                )
                            ) {
                                attrs.selectedOptions = selectedTypes
                                attrs.rowSize = TEST_SUITE_ROW
                            }
                        }
                    }

                    div("d-sm-flex align-items-center justify-content-center") {
                        button(type = ButtonType.button, classes = "btn btn-primary") {
                            attrs.onClickFunction = { submitExecutionRequest() }
                            +"Run tests now!"
                        }
                    }
                })
            }
            // ===================== RIGHT COLUMN ======================================================================
            div("col-2 ml-2") {
                div("text-xs text-center font-weight-bold text-primary text-uppercase mb-3") {
                    +"Information"
                }

                child(cardComponent {
                    div("ml-3") {
                        h6("d-inline") {
                            b { +"Name: " }
                            +project.name
                        }
                    }
                    div("ml-3") {
                        h6("d-inline") {
                            b { +"Description: " }
                            +(project.description ?: "")
                        }
                    }
                    div("ml-3 align-items-left justify-content-between") {
                        fontAwesomeIcon(icon = faHistory)

                        button(classes = "btn btn-link text-left") {
                            +"Latest execution"
                            attrs.onClickFunction = {
                                GlobalScope.launch {
                                    switchToLatestExecution()
                                }
                            }
                        }
                    }
                    div("ml-3 align-items-left") {
                        fontAwesomeIcon(icon = faCalendarAlt)
                        a(
                            href = "#/${project.owner}/${project.name}/history",
                            classes = "btn btn-link text-left"
                        ) {
                            +"Execution history"
                        }
                    }
                })
            }
        }
    }

    private fun RBuilder.runLoadingModal() = modal {
        attrs {
            isOpen = state.isLoading
            contentLabel = "Loading"
        }
        div("d-flex justify-content-center") {
            div("spinner-border") {
                attrs.role = "status"
                span("sr-only") {
                    +"Loading..."
                }
            }
        }
    }

    private suspend fun switchToLatestExecution() {
        val headers = Headers().apply { set("Accept", "application/json") }
        val response = get(
            "${window.location.origin}/latestExecution?name=${project.name}&owner=${project.owner}",
            headers
        )
        if (!response.ok) {
            setState {
                errorLabel = "Failed to fetch latest execution"
                errorMessage =
                        "Failed to fetch latest execution: ${response.status} ${response.statusText}"
                isErrorOpen = true
            }
        } else {
            val latestExecutionId = response
                .decodeFromJsonString<ExecutionDto>()
                .id
            window.location.href = "${window.location}/history/execution/$latestExecutionId"
        }
    }

    private suspend fun getFilesList() = get("${window.location.origin}/files/list", Headers())
        .unsafeMap {
            it.decodeFromJsonString<List<FileInfo>>()
        }

    companion object {
        const val TEST_SUITE_ROW = 4
    }
}
