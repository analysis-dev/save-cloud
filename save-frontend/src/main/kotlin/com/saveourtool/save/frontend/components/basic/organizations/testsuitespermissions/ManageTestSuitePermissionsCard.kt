/**
 * This file contains function to create ManageGitCredentialsCard
 */

@file:Suppress("FILE_NAME_MATCH_CLASS")

package com.saveourtool.save.frontend.components.basic.organizations.testsuitespermissions

import com.saveourtool.save.frontend.components.basic.organizations.testsuitespermissions.PermissionManagerMode.MESSAGE
import com.saveourtool.save.frontend.components.basic.organizations.testsuitespermissions.PermissionManagerMode.PUBLISH
import com.saveourtool.save.frontend.components.basic.organizations.testsuitespermissions.PermissionManagerMode.SUITE_SELECTOR_FOR_PUBLISH
import com.saveourtool.save.frontend.components.basic.organizations.testsuitespermissions.PermissionManagerMode.SUITE_SELECTOR_FOR_RIGHTS
import com.saveourtool.save.frontend.components.basic.organizations.testsuitespermissions.PermissionManagerMode.TRANSFER
import com.saveourtool.save.frontend.components.basic.testsuiteselector.TestSuiteSelectorPurpose
import com.saveourtool.save.frontend.components.basic.testsuiteselector.testSuiteSelector
import com.saveourtool.save.frontend.components.inputform.inputWithDebounceForString
import com.saveourtool.save.frontend.components.modal.largeTransparentModalStyle
import com.saveourtool.save.frontend.components.modal.modal
import com.saveourtool.save.frontend.components.modal.modalBuilder
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.permission.Rights
import com.saveourtool.save.permission.SetRightsRequest
import com.saveourtool.save.testsuite.TestSuiteVersioned

import csstype.ClassName
import react.*
import react.dom.html.InputType
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.option

import kotlinx.coroutines.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Fully independent component that allows to manage test suites permissions.
 */
val manageTestSuitePermissionsComponent = manageTestSuitePermissionsComponent()

/**
 * Props for ManageGitCredentialsCard
 */
external interface ManageTestSuitePermissionsComponentProps : Props {
    /**
     * name of organization, assumption that it's checked by previous views and valid here
     */
    var organizationName: String

    /**
     * Flag that defines if component is shown or not
     */
    var isModalOpen: Boolean

    /**
     * Callback to hide component
     */
    var closeModal: () -> Unit

    /**
     * Mode that is used to tell if PUBLISH or TRANSFER manager should be opened.
     */
    var mode: PermissionManagerMode?
}

@Suppress(
    "TOO_MANY_PARAMETERS",
    "LongParameterList",
    "TOO_LONG_FUNCTION",
    "LongMethod",
)
private fun ChildrenBuilder.displayPermissionManager(
    selectedTestSuites: List<TestSuiteVersioned>,
    organizationName: String,
    requestedRights: Rights,
    openTestSuiteSelector: () -> Unit,
    setOrganizationName: (String) -> Unit,
    setRequestedRights: (Rights) -> Unit,
) {
    div {
        div {
            className = ClassName("text-xs text-center font-weight-bold text-primary text-uppercase mb-3")
            +TRANSFER.purpose.orEmpty()
        }
        div {
            className = ClassName("row mb-2")
            label {
                className = ClassName("col-1 float-left align-self-center m-0")
                +"1. "
            }
            div {
                className = ClassName("pl-0 col-11")
                input {
                    className = ClassName("form-control")
                    placeholder = "Choose test suites to share..."
                    value = selectedTestSuites.map { it.name }.sorted().joinToString(", ")
                    onClick = {
                        openTestSuiteSelector()
                    }
                }
            }
        }
        div {
            className = ClassName("row mb-2")
            label {
                className = ClassName("col-1 float-left align-self-center m-0")
                +"2. "
            }
            div {
                className = ClassName("pl-0 col-11")
                inputWithDebounceForString {
                    selectedOption = organizationName
                    setSelectedOption = { setOrganizationName(it) }
                    getOptionFromString = { it }
                    getString = { it }
                    getUrlForOptions = { prefix -> "$apiUrl/organizations/get/by-prefix?prefix=$prefix" }
                    placeholder = "Start typing organization name..."
                    decodeListFromJsonString = { it.decodeFromJsonString() }
                    getHTMLDataListElementFromOption = { childrenBuilder, organizationName ->
                        with(childrenBuilder) {
                            option { value = organizationName }
                        }
                    }
                }
            }
        }
        div {
            className = ClassName("row")
            label {
                className = ClassName("col-1 float-left align-self-center m-0")
                +"3. "
            }
            div {
                className = ClassName("pl-0 col-11")
                selectorBuilder(requestedRights.toString(), Rights.values().map { it.toString() }) {
                    setRequestedRights(Rights.valueOf(it.target.value))
                }
            }
        }
    }
}

@Suppress(
    "TOO_MANY_PARAMETERS",
    "LongParameterList",
    "TOO_LONG_FUNCTION",
    "LongMethod"
)
private fun ChildrenBuilder.displayMassPermissionManager(
    selectedTestSuites: List<TestSuiteVersioned>,
    isToBePublic: Boolean,
    openTestSuiteSelector: () -> Unit,
    setIsToBePublic: (Boolean) -> Unit,
) {
    div {
        div {
            className = ClassName("text-xs text-center font-weight-bold text-primary text-uppercase mb-3")
            +PUBLISH.purpose.orEmpty()
        }
        div {
            className = ClassName("row mb-2")
            label {
                className = ClassName("col-1 float-left align-self-center m-0")
                +"1. "
            }
            div {
                className = ClassName("pl-0 col-11")
                input {
                    className = ClassName("form-control")
                    placeholder = "Choose test suites to share..."
                    value = selectedTestSuites.map { it.name }.sorted().joinToString(", ")
                    onClick = {
                        openTestSuiteSelector()
                    }
                }
            }
        }
        div {
            className = ClassName("row mb-2")
            label {
                className = ClassName("col-1 float-left align-self-center m-0")
                +"2. "
            }
            div {
                className = ClassName("form-check form-check-inline")
                input {
                    className = ClassName("form-check-input")
                    type = "radio".unsafeCast<InputType>()
                    name = "visibility"
                    id = "visibility-public"
                    value = "public"
                    checked = isToBePublic
                    onChange = {
                        setIsToBePublic(it.target.checked)
                    }
                }
                label {
                    className = ClassName("form-check-label")
                    htmlFor = "visibility-public"
                    +"Public"
                }
            }
            div {
                className = ClassName("form-check form-check-inline")
                input {
                    className = ClassName("form-check-input")
                    type = "radio".unsafeCast<InputType>()
                    name = "visibility"
                    id = "visibility-private"
                    value = "private"
                    checked = !isToBePublic
                    onChange = {
                        setIsToBePublic(!it.target.checked)
                    }
                }
                label {
                    className = ClassName("form-check-label")
                    htmlFor = "visibility-private"
                    +"Private"
                }
            }
        }
    }
}

@Suppress("TOO_LONG_FUNCTION", "LongMethod", "ComplexMethod")
private fun manageTestSuitePermissionsComponent() = FC<ManageTestSuitePermissionsComponentProps> { props ->
    val (selectedTestSuites, setSelectedTestSuites) = useState<List<TestSuiteVersioned>>(emptyList())
    val (organizationName, setOrganizationName) = useState("")
    val (requiredRights, setRequiredRights) = useState(Rights.NONE)

    val (isToBePublic, setIsToBePublic) = useState(true)

    val (currentMode, setCurrentMode) = useState(props.mode ?: TRANSFER)
    useEffect(props.mode) {
        if (currentMode in listOf(TRANSFER, PUBLISH) && props.mode != currentMode) {
            props.mode?.let { setCurrentMode(it) }
        }
    }
    val (backendResponseMessage, setBackendResponseMessage) = useState("")
    val sendTransferRequest = useDeferredRequest {
        val response = post(
            url = "$apiUrl/test-suites/${props.organizationName}/batch-set-rights",
            headers = jsonHeaders,
            body = Json.encodeToString(SetRightsRequest(organizationName, requiredRights, selectedTestSuites.map { it.id })),
            loadingHandler = ::noopLoadingHandler,
            responseHandler = ::noopResponseHandler,
        )
        val message = if (response.ok) {
            response.text().await()
        } else {
            response.unpackMessage()
        }
        setBackendResponseMessage(message)
    }

    val sendPublishRequest = useDeferredRequest {
        val response = post(
            url = "$apiUrl/test-suites/${props.organizationName}/batch-change-visibility?isPublic=$isToBePublic",
            headers = jsonHeaders,
            body = Json.encodeToString(selectedTestSuites.map { it.id }),
            loadingHandler = ::noopLoadingHandler,
            responseHandler = ::noopResponseHandler,
        )
        val message = if (response.ok) {
            response.text().await()
        } else {
            response.unpackMessage()
        }
        setBackendResponseMessage(message)
    }

    val clearFields = {
        setSelectedTestSuites(emptyList())
        setCurrentMode(TRANSFER)
        setBackendResponseMessage("")
        setRequiredRights(Rights.NONE)
        setOrganizationName("")
        setIsToBePublic(true)
    }

    modal { modalProps ->
        modalProps.isOpen = props.isModalOpen
        modalProps.style = largeTransparentModalStyle
        modalBuilder(
            title = "Test Suite Permission Manager${currentMode.title?.let { " - $it" }.orEmpty()}",
            classes = "modal-lg modal-dialog-scrollable",
            onCloseButtonPressed = {
                props.closeModal()
                clearFields()
            },
            bodyBuilder = {
                when (currentMode) {
                    TRANSFER -> displayPermissionManager(
                        selectedTestSuites,
                        organizationName,
                        requiredRights,
                        { setCurrentMode(SUITE_SELECTOR_FOR_RIGHTS) },
                        { setOrganizationName(it) }
                    ) { setRequiredRights(it) }
                    SUITE_SELECTOR_FOR_PUBLISH, SUITE_SELECTOR_FOR_RIGHTS -> testSuiteSelector {
                        this.onTestSuiteUpdate = {
                            setSelectedTestSuites(it)
                        }
                        this.preselectedTestSuites = selectedTestSuites
                        this.selectorPurpose = TestSuiteSelectorPurpose.PRIVATE
                        this.currentOrganizationName = props.organizationName
                    }
                    PUBLISH -> displayMassPermissionManager(
                        selectedTestSuites,
                        isToBePublic,
                        { setCurrentMode(SUITE_SELECTOR_FOR_PUBLISH) },
                    ) { setIsToBePublic(it) }
                    MESSAGE -> +backendResponseMessage
                }
            }
        ) {
            when (currentMode) {
                TRANSFER -> buttonBuilder("Apply", isDisabled = selectedTestSuites.isEmpty()) {
                    sendTransferRequest()
                    setCurrentMode(MESSAGE)
                }
                SUITE_SELECTOR_FOR_RIGHTS -> buttonBuilder("Apply", isDisabled = selectedTestSuites.isEmpty()) {
                    setCurrentMode(TRANSFER)
                }
                SUITE_SELECTOR_FOR_PUBLISH -> buttonBuilder("Apply", isDisabled = selectedTestSuites.isEmpty()) {
                    setCurrentMode(PUBLISH)
                }
                PUBLISH -> buttonBuilder("Apply", isDisabled = selectedTestSuites.isEmpty()) {
                    sendPublishRequest()
                    setCurrentMode(MESSAGE)
                }
                else -> {}
            }
            buttonBuilder("Cancel", "secondary") {
                when (currentMode) {
                    TRANSFER, PUBLISH, MESSAGE -> {
                        clearFields()
                        props.closeModal()
                    }
                    SUITE_SELECTOR_FOR_RIGHTS -> {
                        setSelectedTestSuites(emptyList())
                        setCurrentMode(TRANSFER)
                    }
                    SUITE_SELECTOR_FOR_PUBLISH -> {
                        setSelectedTestSuites(emptyList())
                        setCurrentMode(PUBLISH)
                    }
                }
            }
        }
    }
}
