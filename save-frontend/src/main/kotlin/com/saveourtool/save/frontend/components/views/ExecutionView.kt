/**
 * View for displaying individual execution results
 */

package com.saveourtool.save.frontend.components.views

import com.saveourtool.save.agent.TestExecutionDto
import com.saveourtool.save.core.logging.describe
import com.saveourtool.save.core.result.CountWarnings
import com.saveourtool.save.domain.TestResultDebugInfo
import com.saveourtool.save.domain.TestResultStatus
import com.saveourtool.save.execution.ExecutionDto
import com.saveourtool.save.execution.ExecutionUpdateDto
import com.saveourtool.save.filters.TestExecutionFilters
import com.saveourtool.save.frontend.components.RequestStatusContext
import com.saveourtool.save.frontend.components.basic.*
import com.saveourtool.save.frontend.components.requestStatusContext
import com.saveourtool.save.frontend.components.tables.TableProps
import com.saveourtool.save.frontend.components.tables.columns
import com.saveourtool.save.frontend.components.tables.enableExpanding
import com.saveourtool.save.frontend.components.tables.invoke
import com.saveourtool.save.frontend.components.tables.isExpanded
import com.saveourtool.save.frontend.components.tables.pageIndex
import com.saveourtool.save.frontend.components.tables.pageSize
import com.saveourtool.save.frontend.components.tables.tableComponent
import com.saveourtool.save.frontend.components.tables.value
import com.saveourtool.save.frontend.components.tables.visibleColumnsCount
import com.saveourtool.save.frontend.http.getDebugInfoFor
import com.saveourtool.save.frontend.http.getExecutionInfoFor
import com.saveourtool.save.frontend.themes.Colors
import com.saveourtool.save.frontend.utils.*

import csstype.*
import js.core.jso
import org.w3c.fetch.Headers
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.tr

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * [Props] for execution results view
 */
external interface ExecutionProps : PropsWithChildren {
    /**
     * ID of execution
     */
    var executionId: String

    /**
     * All filters in one value [filters]
     */
    var filters: TestExecutionFilters
}

/**
 * A state of execution view
 */
external interface ExecutionState : State {
    /**
     * Execution dto
     */
    var executionDto: ExecutionDto?

    /**
     * All filters in one value [filters]
     */
    var filters: TestExecutionFilters
}

/**
 * [Props] of a data table with status and testSuite
 */
external interface StatusProps<D : Any> : TableProps<D> {
    /**
     * All filters in one value [filters]
     */
    var filters: TestExecutionFilters
}

/**
 * A Component for execution view
 */
@JsExport
@OptIn(ExperimentalJsExport::class)
@Suppress("MAGIC_NUMBER", "TYPE_ALIAS")
class ExecutionView : AbstractView<ExecutionProps, ExecutionState>(false) {
    @Suppress("TYPE_ALIAS")
    private val additionalInfo: MutableMap<String, AdditionalRowInfo> = mutableMapOf()
    private val testExecutionsTable: FC<StatusProps<TestExecutionDto>> = tableComponent(
        columns = {
            columns {
                column(id = "index", header = "#") {
                    Fragment.create {
                        td {
                            +"${it.row.index + 1 + it.pageIndex * it.pageSize}"
                        }
                    }
                }
                column(id = "startTime", header = "Start time", { startTimeSeconds }) { cellContext ->
                    Fragment.create {
                        td {
                            +"${
                                cellContext.value?.let { Instant.fromEpochSeconds(it, 0) }
                                ?: "Running"
                            }"
                        }
                    }
                }
                column(id = "endTime", header = "End time", { endTimeSeconds }) { cellContext ->
                    Fragment.create {
                        td {
                            +"${
                                cellContext.value?.let { Instant.fromEpochSeconds(it, 0) }
                                ?: "Running"
                            }"
                        }
                    }
                }
                column(id = "status", header = "Status", { status.name }) {
                    Fragment.create {
                        td {
                            +it.value
                        }
                    }
                }
                column(id = "missing", header = "Missing", { unmatched }) {
                    Fragment.create {
                        td {
                            +formatCounter(it.value)
                        }
                    }
                }
                column(id = "matched", header = "Matched", { matched }) {
                    Fragment.create {
                        td {
                            +formatCounter(it.value)
                        }
                    }
                }
                column(id = "path", header = "Test Name") { cellContext ->
                    Fragment.create {
                        td {
                            val testName = cellContext.value.filePath
                            val shortTestName =
                                    if (testName.length > 35) "${testName.take(15)} ... ${testName.takeLast(15)}" else testName
                            +shortTestName

                            // debug info is provided by agent after the execution
                            // possibly there can be cases when this info is not available
                            if (cellContext.value.hasDebugInfo == true) {
                                style = jso {
                                    textDecoration = "underline".unsafeCast<TextDecoration>()
                                    color = "blue".unsafeCast<Color>()
                                    cursor = "pointer".unsafeCast<Cursor>()
                                }

                                onClick = {
                                    this@ExecutionView.scope.launch {
                                        if (!cellContext.row.isExpanded) {
                                            getAdditionalInfoFor(cellContext.value, cellContext.row.id)
                                        }
                                        cellContext.row.toggleExpanded(null)
                                    }
                                }
                            }
                        }
                    }
                }
                column(id = "plugin", header = "Plugin type", { pluginName }) {
                    Fragment.create {
                        td {
                            +it.value
                        }
                    }
                }
                column(id = "suiteName", header = "Test suite", { testSuiteName }) {
                    Fragment.create {
                        td {
                            +"${it.value}"
                        }
                    }
                }
                column(id = "tags", header = "Tags") {
                    Fragment.create {
                        td {
                            +"${it.value.tags}"
                        }
                    }
                }
                column(id = "agentName", header = "Agent Name") {
                    Fragment.create {
                        td {
                            +"${it.value.agentContainerName}"
                        }
                    }
                }
                column(id = "agentId", header = "Agent ID") {
                    Fragment.create {
                        td {
                            +"${it.value.agentContainerId}".takeLast(12)
                        }
                    }
                }
            }
        },
        useServerPaging = true,
        usePageSelection = true,
        tableOptionsCustomizer = { tableOptions ->
            enableExpanding(tableOptions)
        },
        renderExpandedRow = { tableInstance, row ->
            val (errorDescription, trdi, trei) = additionalInfo[row.id] ?: AdditionalRowInfo()
            when {
                errorDescription != null -> tr {
                    td {
                        colSpan = tableInstance.visibleColumnsCount()
                        +"Error retrieving additional information: $errorDescription"
                    }
                }
                trei?.failReason != null || trdi != null -> {
                    trei?.failReason?.let { executionStatusComponent(it, tableInstance)() }
                    trdi?.let { testStatusComponent(it, tableInstance)() }
                }
                else -> tr {
                    td {
                        colSpan = tableInstance.visibleColumnsCount()
                        +"No info available yet for this test execution"
                    }
                }
            }
        },
        commonHeader = { tableInstance, navigate ->
            tr {
                th {
                    colSpan = tableInstance.visibleColumnsCount()
                    testExecutionFiltersRow {
                        filters = state.filters
                        onChangeFilters = { filterValue ->
                            setState {
                                filters = filters.copy(
                                    status = filterValue.status?.takeIf { it.name != "ANY" },
                                    fileName = filterValue.fileName?.ifEmpty { null },
                                    testSuite = filterValue.testSuite?.ifEmpty { null },
                                    tag = filterValue.tag?.ifEmpty { null },
                                )
                            }
                            tableInstance.resetPageIndex(true)
                            navigate(getUrlWithFiltersParams(filterValue))
                        }
                    }
                }
            }
        },
        getRowProps = { row ->
            val color = when (row.original.status) {
                TestResultStatus.FAILED -> Colors.RED
                TestResultStatus.IGNORED -> Colors.GOLD
                TestResultStatus.READY_FOR_TESTING, TestResultStatus.RUNNING -> Colors.GREY
                TestResultStatus.INTERNAL_ERROR, TestResultStatus.TEST_ERROR -> Colors.DARK_RED
                TestResultStatus.PASSED -> Colors.GREEN
            }
            jso {
                style = jso {
                    background = color.value.unsafeCast<Background>()
                }
            }
        },
        getAdditionalDependencies = {
            arrayOf(it.filters)
        }
    )

    init {
        state.executionDto = null
        state.filters = TestExecutionFilters.empty
    }

    private fun formatCounter(count: Long?): String = count?.let {
        if (CountWarnings.isNotApplicable(it.toInt())) {
            "N/A"
        } else {
            it.toString()
        }
    } ?: ""

    private suspend fun getAdditionalInfoFor(testExecution: TestExecutionDto, id: String) {
        val trDebugInfoResponse = getDebugInfoFor(testExecution)
        val trExecutionInfoResponse = getExecutionInfoFor(testExecution)
        // there may be errors during deserialization, which will otherwise be silently ignored
        try {
            additionalInfo[id] = AdditionalRowInfo()
            if (trDebugInfoResponse.ok) {
                additionalInfo[id] = additionalInfo[id]!!
                    .copy(testResultDebugInfo = trDebugInfoResponse.decodeFromJsonString<TestResultDebugInfo>())
            }
            if (trExecutionInfoResponse.ok) {
                additionalInfo[id] = additionalInfo[id]!!
                    .copy(executionInfo = trExecutionInfoResponse.decodeFromJsonString<ExecutionUpdateDto>())
            }
        } catch (ex: SerializationException) {
            additionalInfo[id] = additionalInfo[id]!!
                .copy(errorDescription = ex.describe())
        }
    }

    override fun componentDidMount() {
        super.componentDidMount()

        scope.launch {
            val headers = Headers().also { it.set("Accept", "application/json") }
            val executionDtoFromBackend: ExecutionDto =
                    get(
                        "$apiUrl/executionDto?executionId=${props.executionId}",
                        headers,
                        loadingHandler = ::classLoadingHandler,
                    )
                        .decodeFromJsonString()
            setState {
                executionDto = executionDtoFromBackend
                filters = props.filters
            }
        }
    }

    @Suppress(
        "EMPTY_BLOCK_STRUCTURE_ERROR",
        "TOO_LONG_FUNCTION",
        "AVOID_NULL_CHECKS",
        "MAGIC_NUMBER",
        "ComplexMethod",
        "LongMethod"
    )
    override fun ChildrenBuilder.render() {
        div {
            div {
                displayExecutionInfoHeader(state.executionDto, false, "row mb-2") { event ->
                    scope.launch {
                        val response = post(
                            "$apiUrl/run/re-trigger?executionId=${props.executionId}",
                            Headers(),
                            body = undefined,
                            loadingHandler = ::classLoadingHandler,
                        )
                        if (response.ok) {
                            window.alert("Rerun request successfully submitted")
                            window.location.reload()
                        }
                    }
                    event.preventDefault()
                }
            }
        }

        // fixme: table is rendered twice because of state change when `executionDto` is fetched
        testExecutionsTable {
            filters = state.filters
            getData = { page, size ->
                post(
                    url = "$apiUrl/test-executions?executionId=${props.executionId}&page=$page&size=$size&checkDebugInfo=true",
                    headers = jsonHeaders,
                    body = Json.encodeToString(filters),
                    loadingHandler = ::classLoadingHandler,
                ).unsafeMap {
                    Json.decodeFromString<Array<TestExecutionDto>>(
                        it.text().await()
                    )
                }.apply {
                    asDynamic().debugInfo = null
                }
            }
            getPageCount = { pageSize ->
                val filtersQueryString = buildString {
                    filters.status?.let {
                        append("&status=${filters.status}")
                    } ?: append("")

                    filters.testSuite?.let {
                        append("&testSuite=${filters.testSuite}")
                    } ?: append("")
                }

                val count: Int = get(
                    url = "$apiUrl/testExecution/count?executionId=${props.executionId}$filtersQueryString",
                    headers = Headers().also {
                        it.set("Accept", "application/json")
                    },
                    loadingHandler = ::classLoadingHandler,
                )
                    .json()
                    .await()
                    .unsafeCast<Int>()
                count / pageSize + 1
            }
        }
        displayTestNotFound(state.executionDto)
    }

    private fun getUrlWithFiltersParams(filterValue: TestExecutionFilters) =
            // fixme: relies on the usage of HashRouter, hence hash.drop leading `#`
            "${window.location.hash.drop(1)}${filterValue.toQueryParams()}"

    companion object : RStatics<ExecutionProps, ExecutionState, ExecutionView, Context<RequestStatusContext>>(ExecutionView::class) {
        init {
            contextType = requestStatusContext
        }
    }
}

/**
 * @property errorDescription if retrieved data can't be parsed, this field should contain description of the error
 * @property testResultDebugInfo
 * @property executionInfo
 */
private data class AdditionalRowInfo(
    val errorDescription: String? = null,
    val testResultDebugInfo: TestResultDebugInfo? = null,
    val executionInfo: ExecutionUpdateDto? = null,
)
