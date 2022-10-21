@file:Suppress("FILE_NAME_MATCH_CLASS")

package com.saveourtool.save.frontend.components.basic.testsuitessources

import com.saveourtool.save.domain.EntitySaveStatus
import com.saveourtool.save.entities.DtoWithId
import com.saveourtool.save.entities.GitDto
import com.saveourtool.save.frontend.components.basic.organizations.gitWindow
import com.saveourtool.save.frontend.components.basic.selectFormRequired
import com.saveourtool.save.frontend.components.inputform.InputTypes
import com.saveourtool.save.frontend.components.inputform.inputTextDisabled
import com.saveourtool.save.frontend.components.inputform.inputTextFormOptional
import com.saveourtool.save.frontend.components.inputform.inputTextFormRequired
import com.saveourtool.save.frontend.components.modal.modal
import com.saveourtool.save.frontend.externals.fontawesome.faTimesCircle
import com.saveourtool.save.frontend.externals.fontawesome.fontAwesomeIcon
import com.saveourtool.save.frontend.externals.modal.CssProperties
import com.saveourtool.save.frontend.externals.modal.Styles
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.testsuite.TestSuitesSourceDto
import com.saveourtool.save.v1

import csstype.ClassName
import react.*
import react.dom.aria.AriaRole
import react.dom.aria.ariaLabel
import react.dom.html.ButtonType
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h5

import kotlin.js.json

val testSuiteSourceCreationComponent = testSuiteSourceUpsertComponent()

@Suppress("GENERIC_VARIABLE_WRONG_DECLARATION")
private val gitSelectionForm = selectFormRequired<GitDto>()

/**
 * [Props] for [testSuiteSourceUpsertComponent]
 */
external interface TestSuiteSourceUpsertProps : Props {
    /**
     * Name of a current organization
     */
    var organizationName: String

    /**
     * existed [com.saveourtool.save.testsuite.TestSuitesSourceDto] with ID to update or null to create a new one
     */
    var testSuitesSourceWithId: DtoWithId<TestSuitesSourceDto>?

    /**
     * Callback invoked on successful save
     */
    var onSuccess: (TestSuitesSourceDto) -> Unit
}

/**
 * @param windowOpenness
 * @param testSuitesSourceWithId
 * @param organizationName
 * @param onSuccess
 */
@Suppress("TOO_LONG_FUNCTION")
fun ChildrenBuilder.showTestSuiteSourceUpsertModal(
    windowOpenness: WindowOpenness,
    testSuitesSourceWithId: DtoWithId<TestSuitesSourceDto>?,
    organizationName: String,
    onSuccess: (TestSuitesSourceDto) -> Unit,
) {
    modal { props ->
        props.isOpen = windowOpenness.isOpen()
        props.style = Styles(
            content = json(
                "top" to "10%",
                "left" to "30%",
                "right" to "30%",
                "bottom" to "auto",
                "position" to "absolute",
                "overflow" to "hide",
            ).unsafeCast<CssProperties>()
        )
        div {
            className = ClassName("d-flex justify-content-between")
            h5 {
                className = ClassName("modal-title mb-3")
                +"Create test suite source"
            }
            button {
                type = ButtonType.button
                className = ClassName("close")
                asDynamic()["data-dismiss"] = "modal"
                ariaLabel = "Close"
                fontAwesomeIcon(icon = faTimesCircle)
                onClick = windowOpenness.closeWindowAction().withUnusedArg()
            }
        }
        div {
            testSuiteSourceCreationComponent {
                this.organizationName = organizationName
                this.testSuitesSourceWithId = testSuitesSourceWithId
                this.onSuccess = {
                    windowOpenness.closeWindow()
                    onSuccess(it)
                }
            }
        }
    }
}

private fun EntitySaveStatus.message() = when (this) {
    EntitySaveStatus.CONFLICT -> "Test suite source with such test root path and git id is already present"
    EntitySaveStatus.EXIST -> "Test suite source already exists"
    EntitySaveStatus.NEW -> "Test suite source saved successfully"
    EntitySaveStatus.UPDATED -> "Test suite source updated successfully"
    else -> throw NotImplementedError("Not supported save status $this")
}

@Suppress(
    "TOO_LONG_FUNCTION",
    "LongMethod",
    "ComplexMethod",
)
private fun testSuiteSourceUpsertComponent() = FC<TestSuiteSourceUpsertProps> { props ->
    val (testSuiteSource, setTestSuiteSource) = useState(
        props.testSuitesSourceWithId?.content ?: TestSuitesSourceDto.empty.copy(organizationName = props.organizationName)
    )
    val saveStatusState: StateInstance<EntitySaveStatus?> = useState()
    val (saveStatus, setSaveStatus) = saveStatusState
    val requestToUpsertEntity = prepareRequest(
        id = props.testSuitesSourceWithId?.id,
        testSuiteSource = testSuiteSource,
        entitySaveStatusState = saveStatusState,
    ) {
        props.onSuccess(testSuiteSource)
    }

    val gitWindowOpenness = useWindowOpenness()
    val gitCredentialToUpsertState = useState(GitDto.empty)
    gitWindow {
        windowOpenness = gitWindowOpenness
        organizationName = props.organizationName
        gitToUpsertState = gitCredentialToUpsertState
    }

    div {
        inputTextFormRequired {
            form = InputTypes.SOURCE_NAME
            textValue = testSuiteSource.name
            validInput = testSuiteSource.validateName() && saveStatus != EntitySaveStatus.EXIST
            classes = "mb-2"
            name = "Source name"
            conflictMessage = saveStatus?.message()
            onChangeFun = {
                setTestSuiteSource(testSuiteSource.copy(name = it.target.value))
                if (saveStatus == EntitySaveStatus.EXIST) {
                    setSaveStatus(null)
                }
            }
        }
        inputTextDisabled(
            InputTypes.ORGANIZATION_NAME,
            "mb-2",
            "Organization name",
            testSuiteSource.organizationName
        )
        inputTextFormOptional {
            form = InputTypes.SOURCE_TEST_ROOT_PATH
            textValue = testSuiteSource.testRootPath
            classes = "mb-2"
            name = "Test root path"
            validInput = testSuiteSource.validateTestRootPath() && saveStatus != EntitySaveStatus.CONFLICT
            onChangeFun = {
                setTestSuiteSource(testSuiteSource.copy(testRootPath = it.target.value))
                if (saveStatus == EntitySaveStatus.CONFLICT) {
                    setSaveStatus(null)
                }
            }
        }
        gitSelectionForm {
            formType = InputTypes.SOURCE_GIT
            selectClasses = "custom-select"
            validInput = saveStatus != EntitySaveStatus.CONFLICT
            classes = "mb-2"
            formName = "Git Credentials"
            getData = {
                get(
                    "$apiUrl/organizations/${props.organizationName}/list-git",
                    headers = jsonHeaders,
                    loadingHandler = ::loadingHandler,
                )
                    .unsafeMap {
                        it.decodeFromJsonString()
                    }
            }
            getDataRequestDependencies = arrayOf(gitWindowOpenness.isOpen())
            dataToString = { it.url }
            notFoundErrorMessage = "You have no available git credentials in organization ${props.organizationName}."
            addNewItemChildrenBuilder = { childrenBuilder ->
                with(childrenBuilder) {
                    a {
                        className = ClassName("text-primary")
                        role = "button".unsafeCast<AriaRole>()
                        onClick = {
                            gitWindowOpenness.openWindow()
                        }
                        +"Add new git credentials"
                    }
                }
            }
            selectedValue = testSuiteSource.gitDto.url
            disabled = props.testSuitesSourceWithId != null
            onChangeFun = { git ->
                git?.let {
                    setTestSuiteSource(testSuiteSource.copy(gitDto = it))
                    if (saveStatus == EntitySaveStatus.CONFLICT) {
                        setSaveStatus(null)
                    }
                }
            }
        }
        inputTextFormOptional {
            form = InputTypes.DESCRIPTION
            textValue = testSuiteSource.description
            classes = "mb-2"
            name = "Description"
            validInput = true
            onChangeFun = {
                setTestSuiteSource(testSuiteSource.copy(description = it.target.value))
            }
        }
        div {
            className = ClassName("d-flex justify-content-center")
            button {
                type = ButtonType.button
                className = ClassName("btn btn-primary mt-2 mb-2")
                disabled = !testSuiteSource.validate() || saveStatus != null
                onClick = requestToUpsertEntity.withUnusedArg()
                +"Submit"
            }
        }
        saveStatus?.let {
            div {
                className = ClassName("invalid-feedback d-block text-center")
                +it.message()
            }
        }
    }
}

private fun prepareRequest(
    id: Long?,
    testSuiteSource: TestSuitesSourceDto,
    entitySaveStatusState: StateInstance<EntitySaveStatus?>,
    onSuccess: () -> Unit,
) = useDeferredRequest {
    val (_, setEntitySaveStatus) = entitySaveStatusState
    val response = post(
        url = "/api/$v1/test-suites-sources/${id?.let { "update?id=$it" } ?: "create"}",
        headers = jsonHeaders,
        body = testSuiteSource.toJsonBody(),
        loadingHandler = ::loadingHandler,
        responseHandler = ::responseHandlerWithValidation,
    )
    if (response.ok) {
        onSuccess()
    } else if (response.isConflict()) {
        setEntitySaveStatus(response.decodeFromJsonString<EntitySaveStatus>())
    }
}
