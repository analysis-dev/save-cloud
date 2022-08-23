/**
 * View for tests execution history
 */

package com.saveourtool.save.frontend.components.views

import com.saveourtool.save.execution.ExecutionDto
import com.saveourtool.save.execution.ExecutionStatus
import com.saveourtool.save.frontend.components.RequestStatusContext
import com.saveourtool.save.frontend.components.basic.*
import com.saveourtool.save.frontend.components.requestStatusContext
import com.saveourtool.save.frontend.components.tables.tableComponent
import com.saveourtool.save.frontend.externals.chart.DataPieChart
import com.saveourtool.save.frontend.externals.chart.PieChartColors
import com.saveourtool.save.frontend.externals.chart.pieChart
import com.saveourtool.save.frontend.externals.fontawesome.faCheck
import com.saveourtool.save.frontend.externals.fontawesome.faExclamationTriangle
import com.saveourtool.save.frontend.externals.fontawesome.faSpinner
import com.saveourtool.save.frontend.externals.fontawesome.fontAwesomeIcon
import com.saveourtool.save.frontend.themes.Colors
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.info.UserInfo

import csstype.*
import react.*
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr
import react.table.columns
import react.table.useExpanded
import react.table.usePagination
import react.table.useSortBy

import kotlinx.datetime.Instant
import kotlinx.js.get
import kotlinx.js.jso

/**
 * [Props] for [ContestExecutionView]
 */
external interface ContestExecutionViewProps : PropsWithChildren {
    /**
     * Info about current user
     */
    var currentUserInfo: UserInfo?

    /**
     * Name of a contest
     */
    var contestName: String

    /**
     * Name of an organization
     */
    var organizationName: String

    /**
     * Name of a project
     */
    var projectName: String
}

/**
 * A table to display execution results of a project in a contest.
 */
@JsExport
@OptIn(ExperimentalJsExport::class)
class ContestExecutionView : AbstractView<ContestExecutionViewProps, State>(false) {
    @Suppress("MAGIC_NUMBER")
    private val executionsTable = tableComponent(
        columns = columns<ExecutionDto> {
            column("result", "", { status }) { cellProps ->
                val result = when (cellProps.row.original.status) {
                    ExecutionStatus.ERROR -> ResultColorAndIcon("text-danger", faExclamationTriangle)
                    ExecutionStatus.PENDING -> ResultColorAndIcon("text-success", faSpinner)
                    ExecutionStatus.RUNNING -> ResultColorAndIcon("text-success", faSpinner)
                    ExecutionStatus.FINISHED -> if (cellProps.row.original.failedTests != 0L) {
                        ResultColorAndIcon("text-danger", faExclamationTriangle)
                    } else {
                        ResultColorAndIcon("text-success", faCheck)
                    }
                }
                Fragment.create {
                    td {
                        fontAwesomeIcon(result.resIcon, classes = result.resColor)
                    }
                }
            }
            column("status", "Status", { this }) { cellProps ->
                Fragment.create {
                    td {
                        style = jso {
                            textDecoration = "underline".unsafeCast<TextDecoration>()
                            color = "blue".unsafeCast<Color>()
                            cursor = "pointer".unsafeCast<Cursor>()
                        }
                        onClick = {
                            cellProps.row.toggleRowExpanded()
                        }

                        +"${cellProps.value.status}"
                    }
                }
            }
            column("startDate", "Start time", { startTime }) { cellProps ->
                Fragment.create {
                    td {
                        a {
                            +(formattingDate(cellProps.value) ?: "Starting")
                        }
                    }
                }
            }
            column("endDate", "End time", { endTime }) { cellProps ->
                Fragment.create {
                    td {
                        a {
                            +(formattingDate(cellProps.value) ?: "Starting")
                        }
                    }
                }
            }
            column("running", "Running", { runningTests }) { cellProps ->
                Fragment.create {
                    td {
                        a {
                            +"${cellProps.value}"
                        }
                    }
                }
            }
            column("passed", "Passed", { passedTests }) { cellProps ->
                Fragment.create {
                    td {
                        a {
                            +"${cellProps.value}"
                        }
                    }
                }
            }
            column("failed", "Failed", { failedTests }) { cellProps ->
                Fragment.create {
                    td {
                        a {
                            +"${cellProps.value}"
                        }
                    }
                }
            }
            column("skipped", "Skipped", { skippedTests }) { cellProps ->
                Fragment.create {
                    td {
                        a {
                            +"${cellProps.value}"
                        }
                    }
                }
            }
        },
        getRowProps = { row ->
            val color = when (row.original.status) {
                ExecutionStatus.ERROR -> Colors.RED
                ExecutionStatus.PENDING -> Colors.GREY
                ExecutionStatus.RUNNING -> if (row.original.failedTests != 0L) Colors.DARK_RED else Colors.GREY
                ExecutionStatus.FINISHED -> if (row.original.failedTests != 0L) Colors.DARK_RED else Colors.GREEN
            }
            jso {
                style = jso {
                    background = color.value.unsafeCast<Background>()
                }
            }
        },
        renderExpandedRow = { tableInstance, row ->
            tr {
                td {
                    colSpan = tableInstance.columns.size
                    div {
                        className = ClassName("row")
                        displayExecutionInfoHeader(row.original, "row col-11")
                        div {
                            className = ClassName("col-1")
                            pieChart(
                                getPieChartData(row.original),
                            ) { pieProps ->
                                pieProps.animate = true
                                pieProps.segmentsShift = 2
                                pieProps.radius = 47
                            }
                        }
                    }
                }
            }
        },
        plugins = arrayOf(
            useSortBy,
            useExpanded,
            usePagination
        )
    )

    private fun getPieChartData(execution: ExecutionDto) = execution
        .run {
            arrayOf(
                DataPieChart("Running tests", runningTests.toInt(), PieChartColors.GREY.hex),
                DataPieChart("Failed tests", failedTests.toInt(), PieChartColors.RED.hex),
                DataPieChart("Passed tests", passedTests.toInt(), PieChartColors.GREEN.hex),
            )
        }

    @Suppress(
        "TOO_LONG_FUNCTION",
        "ForbiddenComment",
        "LongMethod",
    )
    override fun ChildrenBuilder.render() {
        executionsTable {
            tableHeader = "Executions details"
            getData = { _, _ ->
                get(
                    url = "$apiUrl/contests/${props.contestName}/executions/${props.organizationName}/${props.projectName}",
                    headers = jsonHeaders,
                    loadingHandler = ::loadingHandler
                )
                    .unsafeMap {
                        it.decodeFromJsonString<Array<ExecutionDto>>()
                    }
            }
            getPageCount = null
        }
    }

    private fun formattingDate(date: Long?) = date?.let {
        Instant.fromEpochSeconds(date, 0)
            .toString()
            .replace("[TZ]".toRegex(), " ")
    }

    /**
     * @property resColor
     * @property resIcon
     */
    private data class ResultColorAndIcon(val resColor: String, val resIcon: dynamic)

    companion object : RStatics<ContestExecutionViewProps, State, ContestExecutionView, Context<RequestStatusContext>>(ContestExecutionView::class) {
        init {
            contextType = requestStatusContext
        }
    }
}
