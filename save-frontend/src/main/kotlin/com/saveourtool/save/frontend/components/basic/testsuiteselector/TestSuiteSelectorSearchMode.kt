/**
 * Component for selecting test suites in search mode
 */

@file:Suppress("FILE_NAME_MATCH_CLASS")

package com.saveourtool.save.frontend.components.basic.testsuiteselector

import com.saveourtool.save.filters.TestSuiteFilters
import com.saveourtool.save.frontend.components.basic.showAvailableTestSuites
import com.saveourtool.save.frontend.components.basic.testsuiteselector.TestSuiteSelectorPurpose.CONTEST
import com.saveourtool.save.frontend.externals.lodash.debounce
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.frontend.utils.noopResponseHandler
import com.saveourtool.save.testsuite.TestSuiteDto
import com.saveourtool.save.utils.DEFAULT_DEBOUNCE_PERIOD

import csstype.ClassName
import org.w3c.dom.HTMLInputElement
import react.*
import react.dom.events.ChangeEvent
import react.dom.html.InputType
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val testSuiteSelectorSearchMode = testSuiteSelectorSearchMode()

/**
 * [Props] for [testSuiteSelectorSearchMode] component
 */
external interface TestSuiteSelectorSearchModeProps : Props {
    /**
     * List of test suites that should be preselected
     */
    var preselectedTestSuites: List<TestSuiteDto>

    /**
     * Callback invoked when test suite is being removed
     */
    var onTestSuitesUpdate: (List<TestSuiteDto>) -> Unit

    /**
     * Mode that defines what kind of test suites will be shown
     */
    var selectorPurpose: TestSuiteSelectorPurpose

    var currentOrganizationName: String
}

private fun ChildrenBuilder.buildInput(
    currentValue: String,
    inputName: String,
    classes: String,
    setValue: (ChangeEvent<HTMLInputElement>) -> Unit
) {
    input {
        className = ClassName("form-control $classes")
        value = currentValue
        placeholder = inputName
        onChange = setValue
    }
}

private fun ChildrenBuilder.showAvailableTestSuitesForSearchMode(
    testSuites: List<TestSuiteDto>,
    selectedTestSuites: List<TestSuiteDto>,
    isOnlyLatestVersion: Boolean,
    onTestSuiteClick: (TestSuiteDto) -> Unit,
) {
    val testSuitesToBeShown = testSuites.filter {
        !isOnlyLatestVersion || it.version == it.source.latestFetchedVersion
    }

    showAvailableTestSuites(
        testSuitesToBeShown,
        selectedTestSuites,
        TestSuiteSelectorMode.SEARCH,
        onTestSuiteClick
    )
}

@Suppress("TOO_LONG_FUNCTION", "LongMethod", "ComplexMethod")
private fun testSuiteSelectorSearchMode() = FC<TestSuiteSelectorSearchModeProps> { props ->
    val (selectedTestSuites, setSelectedTestSuites) = useState<List<TestSuiteDto>>(emptyList())
    val (filteredTestSuites, setFilteredTestSuites) = useState<List<TestSuiteDto>>(emptyList())
    useRequest {
        val contestFlag = if (props.selectorPurpose == CONTEST) {
            "?isContest=true"
        } else {
            ""
        }
        val testSuitesFromBackend: List<TestSuiteDto> = post(
            url = "$apiUrl/test-suites/get-by-ids/${props.currentOrganizationName}$contestFlag",
            headers = jsonHeaders,
            body = Json.encodeToString(props.preselectedTestSuites),
            loadingHandler = ::loadingHandler,
            responseHandler = ::noopResponseHandler,
        )
            .decodeFromJsonString()
        setSelectedTestSuites(testSuitesFromBackend)
    }

    val (filters, setFilters) = useState(TestSuiteFilters.empty)
    val getFilteredTestSuites = debounce(
        useDeferredRequest {
            if (filters.isNotEmpty()) {
                val testSuitesFromBackend: List<TestSuiteDto> = get(
                    url = "$apiUrl/test-suites/filtered/${props.currentOrganizationName}${
                        filters.copy(language = encodeURIComponent(filters.language))
                        .toQueryParams("isContest" to "${props.selectorPurpose == CONTEST}")
                    }",
                    headers = jsonHeaders,
                    loadingHandler = ::noopLoadingHandler,
                    responseHandler = ::noopResponseHandler,
                )
                    .decodeFromJsonString()
                setFilteredTestSuites(testSuitesFromBackend)
            }
        },
        DEFAULT_DEBOUNCE_PERIOD,
    )

    useEffect(filters) {
        if (filters.isEmpty()) {
            setFilteredTestSuites(emptyList())
        } else {
            getFilteredTestSuites()
        }
    }

    div {
        className = ClassName("d-flex justify-content-around mb-2")
        buildInput(filters.name, "Name", "mr-1") { event ->
            setFilters { it.copy(name = event.target.value) }
        }
        buildInput(filters.language, "Language", "ml-1 mr-1") { event ->
            setFilters { it.copy(language = event.target.value) }
        }
        buildInput(filters.tags, "Tags", "ml-1") { event ->
            setFilters { it.copy(tags = event.target.value) }
        }
    }

    val (isOnlyLatestVersion, setIsOnlyLatestVersion) = useState(false)
    div {
        className = ClassName("d-flex justify-content-around mb-3")
        div {
            className = ClassName("form-group form-check")
            input {
                type = InputType.checkbox
                className = ClassName("form-check-input")
                id = "isOnlyLatestVersion"
                checked = isOnlyLatestVersion
                onChange = {
                    setIsOnlyLatestVersion(it.target.checked)
                }
            }
            label {
                className = ClassName("form-check-label")
                htmlFor = "isOnlyLatestVersion"
                +"Show only latest fetched version"
            }
        }
    }
    useTooltip()
    showAvailableTestSuitesForSearchMode(
        filteredTestSuites,
        selectedTestSuites,
        isOnlyLatestVersion,
    ) { testSuite ->
        setSelectedTestSuites { selectedTestSuites ->
            selectedTestSuites.toMutableList()
                .apply {
                    if (testSuite in selectedTestSuites) {
                        remove(testSuite)
                    } else {
                        add(testSuite)
                    }
                }
                .toList()
                .also { listOfTestSuiteDtos ->
                    props.onTestSuitesUpdate(listOfTestSuiteDtos)
                }
        }
    }
}
