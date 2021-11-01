@file:Suppress("FILE_NAME_MATCH_CLASS")

package org.cqfn.save.frontend.components.basic

import org.cqfn.save.execution.ExecutionDto
import org.cqfn.save.execution.ExecutionStatus
import react.Props
import react.dom.button
import react.dom.div
import react.dom.h1
import react.dom.img
import react.fc

/**
 * [Props] for execution statistics component
 */
external interface ExecutionStatisticsProps : Props {
    /**
     * And instance of [ExecutionDto], which should be passed from parent component
     */
    var executionDto: ExecutionDto?
}

/**
 * A component which displays statistics about an execution from its props
 *
 * @param classes HTML classes for the enclosing div
 * @return a functional react component
 */
@Suppress("MAGIC_NUMBER")
fun executionStatistics(classes: String = "") = fc<ExecutionStatisticsProps> { props ->
    val totalTests = props.executionDto?.run {
        runningTests + passedTests + failedTests + skippedTests
    } ?: 0
    val isInProgress = props.executionDto?.run { status == ExecutionStatus.RUNNING || status == ExecutionStatus.PENDING } ?: true
    val isSuccess = props.executionDto?.run { passedTests == totalTests } ?: false
    val style = if (isInProgress) {
        "btn-info"
    } else if (isSuccess) {
        "btn-success"
    } else {
        "btn-danger"
    }
    div(classes) {
        button(classes = "btn $style") {
            attrs.disabled = true
            val passRate = props.executionDto?.run {
                if (totalTests > 0) (passedTests.toFloat() / totalTests * 100).toInt() else 0
            } ?: "N/A"
            +"$totalTests tests, $passRate% passed, ${props.executionDto?.runningTests} running"
        }
    }
}

/**
 * A component which displays a GIF if tests not found
 *
 * @return a functional react component
 */
fun executionTestsNotFound() = fc<ExecutionStatisticsProps> { props ->
    val totalTests = props.executionDto?.run {
        runningTests + passedTests + failedTests + skippedTests
    } ?: 0

    if (totalTests == 0L) {
        div("d-flex justify-content-center") {
            img(src = "img/sad_cat.gif") {}
        }
        div("d-sm-flex align-items-center justify-content-center mb-4 mt-2") {
            h1("h3 mb-0 text-gray-800") {
                +"Tests not found!"
            }
        }
    }
}
