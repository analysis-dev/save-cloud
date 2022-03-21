
/**
 * A view with project creation details
 */

@file:Suppress("WildcardImport", "FILE_WILDCARD_IMPORTS")

package org.cqfn.save.frontend.components.views

import org.cqfn.save.entities.*
import org.cqfn.save.frontend.components.basic.*
import org.cqfn.save.frontend.components.basic.InputTypes
import org.cqfn.save.frontend.components.basic.inputTextFormOptional
import org.cqfn.save.frontend.components.basic.inputTextFormRequired
import org.cqfn.save.frontend.components.basic.selectFormRequired
import org.cqfn.save.frontend.components.errorStatusContext
import org.cqfn.save.frontend.utils.*
import org.cqfn.save.frontend.utils.noopResponseHandler

import org.w3c.dom.*
import org.w3c.dom.events.Event
import org.w3c.fetch.Headers
import org.w3c.fetch.Response
import react.Context
import react.Props
import react.RBuilder
import react.RStatics
import react.State
import react.StateSetter
import react.dom.*
import react.setState

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.html.ButtonType
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * [RState] of project creation view component
 */
external interface ProjectSaveViewState : State {
    /**
     * Flag to handle error
     */
    var isErrorWithProjectSave: Boolean?

    /**
     * Error message
     */
    var errorMessage: String

    /**
     * Validation of input fields
     */
    var isValidOrganization: Boolean?

    /**
     * Validation of input fields
     */
    var isValidProjectName: Boolean?

    /**
     * Validation of input fields
     */
    var isValidGitUrl: Boolean?

    /**
     * Validation of input fields
     */
    var isValidGitUser: Boolean?

    /**
     * Validation of input fields
     */
    var isValidGitToken: Boolean?

    /**
     * Validation of input fields
     */
    var gitConnectionCheckingStatus: GitConnectionStatusEnum?
}

/**
 * Special enum that stores the value with the result of testing git credentials
 */
enum class GitConnectionStatusEnum {
    CHECKED_NOT_OK,
    CHECKED_OK,
    INTERNAL_SERVER_ERROR,
    NOT_CHECKED,
    VALIDATING,
    ;
}

/**
 * A functional RComponent for project creation view
 *
 * @return a functional component
 */
@JsExport
@OptIn(ExperimentalJsExport::class)
class CreationView : AbstractView<Props, ProjectSaveViewState>(true) {
    private val fieldsMap: MutableMap<InputTypes, String> = mutableMapOf()
    private val selectFormRequired = selectFormRequired(
        onChangeFun = ::changeFields,
    )

    init {
        state.isErrorWithProjectSave = false
        state.errorMessage = ""
        state.gitConnectionCheckingStatus = GitConnectionStatusEnum.NOT_CHECKED

        state.isValidOrganization = true
        state.isValidProjectName = true
        state.isValidGitUrl = true
        state.isValidGitUser = true
        state.isValidGitToken = true
    }

    private fun changeFields(
        fieldName: InputTypes,
        target: Event,
        isProject: Boolean = true,
    ) {
        val tg = target.target
        val value = when (tg) {
            is HTMLInputElement -> tg.value
            is HTMLSelectElement -> tg.value
            else -> ""
        }
        fieldsMap[fieldName] = value
    }

    @Suppress("UnsafeCallOnNullableType", "TOO_LONG_FUNCTION")
    private fun validateGitConnection() {
        val headers = Headers().also {
            it.set("Accept", "application/json")
            it.set("Content-Type", "application/json")
        }
        val urlArguments =
                "?user=${fieldsMap[InputTypes.GIT_USER]}&token=${fieldsMap[InputTypes.GIT_TOKEN]}&url=${fieldsMap[InputTypes.GIT_URL]}"

        scope.launch {
            setState {
                gitConnectionCheckingStatus = GitConnectionStatusEnum.VALIDATING
            }
            val responseFromCreationProject =
                    get("$apiUrl/check-git-connectivity-adaptor$urlArguments", headers,
                        responseHandler = ::noopResponseHandler)

            if (responseFromCreationProject.ok) {
                if (responseFromCreationProject.text().await().toBoolean()) {
                    setState {
                        gitConnectionCheckingStatus = GitConnectionStatusEnum.CHECKED_OK
                    }
                } else {
                    setState {
                        gitConnectionCheckingStatus = GitConnectionStatusEnum.CHECKED_NOT_OK
                    }
                }
            } else {
                setState {
                    gitConnectionCheckingStatus = GitConnectionStatusEnum.INTERNAL_SERVER_ERROR
                }
            }
        }
    }

    @Suppress("UnsafeCallOnNullableType", "TOO_LONG_FUNCTION", "MAGIC_NUMBER")
    private fun saveProject() {
        if (!isValidInput()) {
            return
        }
        val organizationName = fieldsMap[InputTypes.ORGANIZATION_NAME]!!.trim()
        val date = LocalDateTime(1970, Month.JANUARY, 1, 0, 0, 1)
        val newProjectRequest = NewProjectDto(
            Project(
                fieldsMap[InputTypes.PROJECT_NAME]!!.trim(),
                fieldsMap[InputTypes.PROJECT_URL]?.trim(),
                fieldsMap[InputTypes.DESCRIPTION]?.trim(),
                ProjectStatus.CREATED,
                userId = -1,
                organization = Organization("stub", null, date)
            ),
            fieldsMap[InputTypes.ORGANIZATION_NAME]!!.trim(),
            GitDto(
                fieldsMap[InputTypes.GIT_URL]?.trim() ?: "",
                fieldsMap[InputTypes.GIT_USER]?.trim(),
                fieldsMap[InputTypes.GIT_TOKEN]?.trim(),
                fieldsMap[InputTypes.GIT_BRANCH]?.trim()
            ),
        )
        val headers = Headers().also {
            it.set("Accept", "application/json")
            it.set("Content-Type", "application/json")
        }
        scope.launch {
            val responseFromCreationProject =
                    post("$apiUrl/projects/save", headers, Json.encodeToString(newProjectRequest))

            if (responseFromCreationProject.ok == true) {
                window.location.href =
                        "${window.location.origin}#/" +
                                "${organizationName.replace(" ", "%20")}/" +
                                newProjectRequest.project.name.replace(" ", "%20")
            } else {
                responseFromCreationProject.text().then {
                    setState {
                        isErrorWithProjectSave = true
                        errorMessage = it
                    }
                }
            }
        }
    }

    /**
     * A little bit ugly method with code duplication due to different states.
     * FixMe: May be it will be possible to optimize it in the future, now we don't have time.
     */
    @Suppress("TOO_LONG_FUNCTION", "SAY_NO_TO_VAR")
    private fun isValidInput(): Boolean {
        var valid = true
        if (fieldsMap[InputTypes.ORGANIZATION_NAME].isNullOrBlank()) {
            setState { isValidOrganization = false }
            valid = false
        } else {
            setState { isValidOrganization = true }
        }

        if (fieldsMap[InputTypes.PROJECT_NAME].isNullOrBlank()) {
            setState { isValidProjectName = false }
            valid = false
        } else {
            setState { isValidProjectName = true }
        }

        val gitUser = fieldsMap[InputTypes.GIT_USER]
        if (gitUser.isNullOrBlank() || Regex(".*\\s.*").matches(gitUser.trim())) {
            setState { isValidGitUser = false }
        } else {
            setState { isValidGitUser = true }
        }

        val gitToken = fieldsMap[InputTypes.GIT_TOKEN]
        if (gitToken.isNullOrBlank() || Regex(".*\\s.*").matches(gitToken.trim())) {
            setState { isValidGitToken = false }
        } else {
            setState { isValidGitToken = true }
        }

        val gitUrl = fieldsMap[InputTypes.GIT_URL]
        if (gitUrl.isNullOrBlank() || !gitUrl.trim().startsWith("http")) {
            setState { isValidGitUrl = false }
        } else {
            setState { isValidGitUrl = true }
        }
        return valid
    }

    @Suppress(
        "TOO_LONG_FUNCTION",
        "EMPTY_BLOCK_STRUCTURE_ERROR",
        "LongMethod",
    )
    override fun RBuilder.render() {
        runErrorModal(
            state.isErrorWithProjectSave,
            "Error appeared during project creation",
            state.errorMessage
        ) {
            setState { isErrorWithProjectSave = false }
        }

        main("main-content mt-0 ps") {
            div("page-header align-items-start min-vh-100") {
                span("mask bg-gradient-dark opacity-6") {}
                div("row justify-content-center") {
                    div("col-sm-4") {
                        div("container card o-hidden border-0 shadow-lg my-2 card-body p-0") {
                            div("p-5 text-center") {
                                h1("h4 text-gray-900 mb-4") {
                                    +"Create new test project"
                                }
                                div {
                                    button(type = ButtonType.button, classes = "btn btn-primary mb-2") {
                                        a(classes = "text-light", href = "#/createOrganization/") {
                                            +"Add new organization"
                                        }
                                    }
                                }
                                form(classes = "needs-validation") {
                                    div("row g-3") {
                                        child(selectFormRequired) {
                                            attrs.form = InputTypes.ORGANIZATION_NAME
                                            attrs.validInput = state.isValidOrganization!!
                                            attrs.classes = "col-md-6 pl-0 pl-2 pr-2"
                                            attrs.text = "Organization"
                                        }
                                        inputTextFormRequired(InputTypes.PROJECT_NAME, state.isValidProjectName!!, "col-md-6 pl-2 pr-2", "Tested tool name") {
                                            changeFields(InputTypes.PROJECT_NAME, it)
                                        }
                                        inputTextFormOptional(InputTypes.PROJECT_URL, "col-md-6 pr-0 mt-3", "Tested Tool Website") {
                                            changeFields(InputTypes.PROJECT_URL, it)
                                        }
                                        inputTextFormOptional(
                                            InputTypes.GIT_URL,
                                            "col-md-6 mt-3 pl-0",
                                            "Test Suite Git URL"
                                        ) {
                                            changeFields(InputTypes.GIT_URL, it, false)
                                        }

                                        div("col-md-12 mt-3 mb-3 pl-0 pr-0") {
                                            label("form-label") {
                                                attrs.set("for", InputTypes.DESCRIPTION.name)
                                                +"Description"
                                            }
                                            div("input-group has-validation") {
                                                textarea("form-control") {
                                                    attrs {
                                                        onChangeFunction = {
                                                            val tg = it.target as HTMLTextAreaElement
                                                            fieldsMap[InputTypes.DESCRIPTION] = tg.value
                                                        }
                                                    }
                                                    attrs["aria-describedby"] = "${InputTypes.DESCRIPTION.name}Span"
                                                    attrs["row"] = "2"
                                                    attrs["id"] = InputTypes.DESCRIPTION.name
                                                    attrs["required"] = false
                                                    attrs["class"] = "form-control"
                                                }
                                            }
                                        }

                                        div("col-md-12 mt-3 border-top") {
                                            p("mx-auto mt-2") {
                                                +"Provide Credentials if your repo with Test Suites is private:"
                                            }
                                        }

                                        inputTextFormOptional(InputTypes.GIT_USER, "col-md-6 mt-1", "Git Username") {
                                            changeFields(InputTypes.GIT_USER, it, false)
                                        }
                                        inputTextFormOptional(InputTypes.GIT_TOKEN, "col-md-6 mt-1 pr-0", "Git Token") {
                                            changeFields(InputTypes.GIT_TOKEN, it, false)
                                        }

                                        div("form-check form-switch mt-2") {
                                            input(classes = "form-check-input") {
                                                attrs["type"] = "checkbox"
                                                attrs["id"] = "isPublicSwitch"
                                                attrs["checked"] = "true"
                                            }
                                            label("form-check-label") {
                                                attrs["htmlFor"] = "isPublicSwitch"
                                                +"Public project"
                                            }
                                        }
                                    }

                                    button(type = ButtonType.submit, classes = "btn btn-info mt-4 mr-3") {
                                        +"Create test project"
                                        attrs.onClickFunction = { saveProject() }
                                    }
                                    button(type = ButtonType.button, classes = "btn btn-success mt-4 ml-3") {
                                        +"Validate connection"
                                        attrs.onClickFunction = { validateGitConnection() }
                                    }
                                    div("row justify-content-center") {
                                        when (state.gitConnectionCheckingStatus) {
                                            GitConnectionStatusEnum.CHECKED_NOT_OK ->
                                                createDiv(
                                                    "invalid-feedback d-block",
                                                    "Validation failed: please check your git URL and credentials"
                                                )
                                            GitConnectionStatusEnum.CHECKED_OK ->
                                                createDiv("valid-feedback d-block", "Successful validation of git configuration")
                                            GitConnectionStatusEnum.NOT_CHECKED ->
                                                createDiv("invalid-feedback d-block", "")
                                            GitConnectionStatusEnum.INTERNAL_SERVER_ERROR ->
                                                createDiv("invalid-feedback d-block", "Internal server error during git validation")
                                            GitConnectionStatusEnum.VALIDATING ->
                                                div("spinner-border spinner-border-sm mt-3") {
                                                    attrs["role"] = "status"
                                                }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun RBuilder.createDiv(blockName: String, text: String) =
            div("$blockName mt-2") {
                +text
            }

    companion object : RStatics<Props, ProjectSaveViewState, CreationView, Context<StateSetter<Response?>>>(CreationView::class) {
        init {
            contextType = errorStatusContext
        }
    }
}
