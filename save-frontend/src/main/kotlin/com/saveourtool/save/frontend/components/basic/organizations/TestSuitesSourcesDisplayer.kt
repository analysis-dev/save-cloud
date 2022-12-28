@file:Suppress(
    "FILE_NAME_MATCH_CLASS",
    "FILE_WILDCARD_IMPORTS",
    "WildcardImport",
    "HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE",
)

package com.saveourtool.save.frontend.components.basic.organizations

import com.saveourtool.save.frontend.externals.fontawesome.*
import com.saveourtool.save.frontend.utils.buttonBuilder
import com.saveourtool.save.testsuite.*
import com.saveourtool.save.utils.millisToInstant
import com.saveourtool.save.utils.prettyPrint

import csstype.ClassName
import csstype.Cursor
import js.core.jso
import react.ChildrenBuilder
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.ul

/**
 * Display single TestSuiteSource as list option
 *
 * @param isSelected flag that defines if this test suite source is selected or not
 * @param testSuitesSourceDto
 * @param selectHandler callback invoked on TestSuitesSource selection
 * @param editHandler callback invoked on edit TestSuitesSource button pressed
 * @param fetchHandler callback invoked on fetch button pressed
 * @param refreshHandler callback invoked on refresh button pressed
 */
@Suppress(
    "TOO_LONG_FUNCTION",
    "TOO_MANY_PARAMETERS",
    "LongParameterList",
    "LongMethod",
)
fun ChildrenBuilder.showTestSuitesSourceAsListElement(
    testSuitesSourceDto: TestSuitesSourceDto,
    isSelected: Boolean,
    selectHandler: (TestSuitesSourceDto) -> Unit,
    editHandler: (TestSuitesSourceDto) -> Unit,
    fetchHandler: (TestSuitesSourceDto) -> Unit,
    refreshHandler: () -> Unit,
) {
    val active = if (isSelected) "list-group-item-secondary" else ""
    li {
        className = ClassName("list-group-item $active")
        div {
            className = ClassName("d-flex w-100 justify-content-between")
            button {
                className = ClassName("btn btn-lg btn-link p-0 mb-1")
                onClick = {
                    selectHandler(testSuitesSourceDto)
                }
                label {
                    style = jso {
                        cursor = "pointer".unsafeCast<Cursor>()
                    }
                    fontAwesomeIcon(
                        if (isSelected) {
                            faArrowLeft
                        } else {
                            faArrowRight
                        }
                    )
                    +("  ${testSuitesSourceDto.name}")
                }
            }

            buttonBuilder(faEdit, null, title = "Edit source") {
                editHandler(testSuitesSourceDto)
            }
        }
        div {
            p {
                +(testSuitesSourceDto.description ?: "Description is not provided.")
            }
        }
        div {
            className = ClassName("clearfix")
            div {
                className = ClassName("float-left")
                buttonBuilder("Fetch new version", "info", isOutline = true, classes = "btn-sm mr-2") {
                    fetchHandler(testSuitesSourceDto)
                }
            }
            if (isSelected) {
                div {
                    className = ClassName("float-left")
                    buttonBuilder(faSyncAlt, "info", isOutline = false, classes = "btn-sm mr-2") {
                        refreshHandler()
                    }
                }
            }
            span {
                className = ClassName("float-right align-bottom")
                asDynamic()["data-toggle"] = "tooltip"
                asDynamic()["data-placement"] = "bottom"
                title = "Organization-creator"
                +(testSuitesSourceDto.organizationName)
            }
        }
    }
}

/**
 * Display list of TestSuiteSources as a list
 *
 * @param testSuitesSources [TestSuitesSourceDtoList]
 * @param selectHandler callback invoked on TestSuitesSource selection
 * @param editHandler callback invoked on edit TestSuitesSource button pressed
 * @param fetchHandler callback invoked on fetch button pressed
 * @param refreshHandler
 */
fun ChildrenBuilder.showTestSuitesSources(
    testSuitesSources: TestSuitesSourceDtoList,
    selectHandler: (TestSuitesSourceDto) -> Unit,
    fetchHandler: (TestSuitesSourceDto) -> Unit,
    editHandler: (TestSuitesSourceDto) -> Unit,
    refreshHandler: () -> Unit,
) {
    div {
        className = ClassName("list-group col-8")
        testSuitesSources.forEach {
            showTestSuitesSourceAsListElement(it, false, selectHandler, editHandler, fetchHandler, refreshHandler)
        }
    }
}

/**
 * Display list of [TestSuitesSourceSnapshotKey] of [selectedTestSuiteSource]
 *
 * @param selectedTestSuiteSource
 * @param testSuitesSourcesSnapshotKeys
 * @param selectHandler callback invoked on TestSuitesSource selection
 * @param editHandler callback invoked on edit TestSuitesSource button pressed
 * @param fetchHandler callback invoked on fetch button pressed
 * @param deleteHandler callback invoked on [TestSuitesSourceSnapshotKey] deletion
 * @param refreshHandler
 */
@Suppress("LongParameterList", "TOO_MANY_PARAMETERS")
fun ChildrenBuilder.showTestSuitesSourceSnapshotKeys(
    selectedTestSuiteSource: TestSuitesSourceDto,
    testSuitesSourcesSnapshotKeys: TestSuitesSourceSnapshotKeyList,
    selectHandler: (TestSuitesSourceDto) -> Unit,
    editHandler: (TestSuitesSourceDto) -> Unit,
    fetchHandler: (TestSuitesSourceDto) -> Unit,
    deleteHandler: (TestSuitesSourceSnapshotKey) -> Unit,
    refreshHandler: () -> Unit,
) {
    ul {
        className = ClassName("list-group col-8")
        showTestSuitesSourceAsListElement(selectedTestSuiteSource, true, selectHandler, editHandler, fetchHandler, refreshHandler)
        if (testSuitesSourcesSnapshotKeys.isEmpty()) {
            li {
                className = ClassName("list-group-item list-group-item-light")
                +"This source is not fetched yet..."
            }
        } else {
            li {
                className = ClassName("list-group-item list-group-item-light")
                div {
                    className = ClassName("clearfix")
                    div {
                        className = ClassName("float-left")
                        +"Version"
                    }
                    div {
                        className = ClassName("float-right")
                        +"Git commit time"
                    }
                }
            }
            testSuitesSourcesSnapshotKeys.forEach { testSuitesSourceSnapshotKey ->
                li {
                    className = ClassName("list-group-item")
                    div {
                        className = ClassName("clearfix")
                        div {
                            className = ClassName("float-left")
                            +testSuitesSourceSnapshotKey.version
                        }
                        buttonBuilder(faTimesCircle, style = null, classes = "float-right btn-sm pt-0 pb-0") {
                            deleteHandler(testSuitesSourceSnapshotKey)
                        }
                        div {
                            className = ClassName("float-right")
                            +testSuitesSourceSnapshotKey.creationTimeInMills.millisToInstant().prettyPrint()
                        }
                    }
                }
            }
        }
    }
}
