@file:Suppress("FILE_NAME_MATCH_CLASS", "FILE_WILDCARD_IMPORTS", "LargeClass")

package com.saveourtool.save.frontend.components.basic.contests

import com.saveourtool.save.entities.ContestResult
import com.saveourtool.save.execution.ExecutionStatus
import com.saveourtool.save.frontend.components.tables.tableComponent
import com.saveourtool.save.frontend.utils.*
import csstype.*
import react.*
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div

import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.td
import react.table.columns

/**
 * SUBMISSIONS tab in ContestView
 */
val contestSubmissionsMenu = contestSubmissionsMenu()

@Suppress("MAGIC_NUMBER")
private val myProjectsTable = tableComponent(
    columns = columns<ContestResult> {
        column(id = "project_name", header = "Project Name", { this }) { cellProps ->
            Fragment.create {
                td {
                    a {
                        cellProps.value.let {
                            href = "#/contests/${it.contestName}/${it.organizationName}/${it.projectName}"
                            +"${it.organizationName}/${it.projectName}"
                        }
                    }
                }
            }
        }
        column(id = "sdk", header = "SDK", { this }) { cellProps ->
            Fragment.create {
                td {
                    +cellProps.value.sdk
                }
            }
        }
        column(id = "submission_time", header = "Last submission time", { this }) { cellProps ->
            Fragment.create {
                td {
                    +(cellProps.value.submissionTime?.toString()?.replace("T", " ") ?: "No data")
                }
            }
        }
        column(id = "status", header = "Last submission status", { this }) { cellProps ->
            Fragment.create {
                td {
                    cellProps.value.let { displayStatus(it.submissionStatus, it.hasFailedTest, it.score) }
                }
            }
        }
    },
    initialPageSize = 10,
    useServerPaging = false,
    usePageSelection = false,
)

/**
 * ContestSubmissionsMenu component [Props]
 */
external interface ContestSubmissionsMenuProps : Props {
    /**
     * Name of a current contest
     */
    var contestName: String
}

private fun ChildrenBuilder.displayStatus(status: ExecutionStatus, hasFailedTests: Boolean, score: Double?) {
    span {
        className = when (status) {
            ExecutionStatus.PENDING -> ClassName("")
            ExecutionStatus.RUNNING -> ClassName("")
            ExecutionStatus.ERROR -> ClassName("text-danger")
            ExecutionStatus.FINISHED -> if (hasFailedTests) {
                ClassName("text-danger")
            } else {
                ClassName("text-success")
            }
        }
        +"${status.name} "
    }
    displayScore(status, score)
}

private fun ChildrenBuilder.displayScore(status: ExecutionStatus, score: Double?) {
    if (status == ExecutionStatus.FINISHED) {
        span {
            +"${score?.let { ("$it/100") }}"
        }
    }
}

private fun contestSubmissionsMenu(
) = FC<ContestSubmissionsMenuProps> { props ->
    div {
        className = ClassName("d-flex justify-content-center")
        div {
            className = ClassName("col-8")
            myProjectsTable {
                tableHeader = "My Submissions"
                getData = { _, _ ->
                    get(
                        url = "$apiUrl/contests/${props.contestName}/my-results",
                        headers = jsonHeaders,
                        ::loadingHandler,
                    )
                        .decodeFromJsonString<Array<ContestResult>>()
                }
                getPageCount = null
            }
        }
    }
}
