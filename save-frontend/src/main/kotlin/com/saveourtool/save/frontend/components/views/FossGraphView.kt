/**
 * View for FossGraph
 */

@file:Suppress("FILE_NAME_MATCH_CLASS")

package com.saveourtool.save.frontend.components.views

import com.saveourtool.save.frontend.externals.fontawesome.faSearch
import com.saveourtool.save.frontend.externals.fontawesome.fontAwesomeIcon
import com.saveourtool.save.frontend.externals.progressbar.Color
import com.saveourtool.save.frontend.externals.progressbar.progressBar

import csstype.AlignItems
import csstype.ClassName
import csstype.Display
import js.core.jso
import react.*
import react.dom.aria.ariaDescribedBy
import react.dom.aria.ariaLabel
import react.dom.html.ButtonType
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h6
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.span
import web.html.InputType

/**
 * [VFC] for foss graph view
 */
@Suppress("EMPTY_BLOCK_STRUCTURE_ERROR")
val fossGraphView: VFC = VFC {
    val fossGraphView = fossGraphView()
    fossGraphView {

    }
}

/**
 * [Props] for FossGraphView
 */
external interface FossGraphViewProps : Props {
    /**
     * Name of security vulnerabilities
     */
    var name: String?
}

@Suppress(
    "MAGIC_NUMBER",
    "TOO_LONG_FUNCTION",
)
private fun fossGraphView(): FC<FossGraphViewProps> = FC { props ->
    div {
        className = ClassName("card card-body mt-0")

        div {
            className = ClassName("row d-flex justify-content-end mb-4")
            span {
                className = ClassName("col-3 mask opacity-6")
                form {
                    className = ClassName("d-none d-inline-block form-inline w-100 navbar-search")
                    div {
                        className = ClassName("input-group")
                        input {
                            className = ClassName("form-control bg-light border-0 small")
                            type = InputType.text
                            placeholder = "Search for the benchmark..."
                            ariaLabel = "Search"
                            ariaDescribedBy = "basic-addon2"
                        }
                        div {
                            className = ClassName("input-group-append")
                            button {
                                className = ClassName("btn btn-primary")
                                type = ButtonType.button
                                fontAwesomeIcon(icon = faSearch, classes = "trash-alt")
                            }
                        }
                    }
                }
            }
        }

        h1 {
            className = ClassName("h3 mb-0 text-center text-gray-800")
            // +(props.name)
            +"CVE-2022-22978"
        }

        div {
            className = ClassName("row justify-content-center")
            // ===================== LEFT COLUMN =======================================================================
            div {
                className = ClassName("col-2 mr-3")
                div {
                    className = ClassName("text-xs text-center font-weight-bold text-primary text-uppercase mb-3")
                    +""
                }
                div {
                    className = ClassName("col-xl col-md-6 mb-4")
                    val progress = 87
                    val color = if (progress < 51) {
                        Color.GREEN.hexColor
                    } else {
                        Color.RED.hexColor
                    }
                    progressBar(progress, color = color)
                }
                div {
                    className = ClassName("card shadow mb-4")
                    div {
                        className = ClassName("card-header py-3")
                        div {
                            className = ClassName("row")
                            h6 {
                                className = ClassName("m-0 font-weight-bold text-primary")
                                style = jso {
                                    display = Display.flex
                                    alignItems = AlignItems.center
                                }
                                +"Description"
                            }
                        }
                    }
                    div {
                        className = ClassName("card-body")
                        ReactHTML.textarea {
                            className = ClassName("auto_height form-control-plaintext pt-0 pb-0")
                            value = "description info"  // description
                            disabled = true
                        }
                    }
                }
            }
            // ===================== RIGHT COLUMN =======================================================================
            div {
                className = ClassName("col-6")
                div {
                    className = ClassName("mt-5 text-xs text-center font-weight-bold text-primary text-uppercase mb-3")
                    +"Affected open source projects"
                }
                button {
                    type = ButtonType.button
                    className = ClassName("btn btn-primary")
                    +"+"
                }

                div {
                    className = ClassName("mt-5 text-xs text-center font-weight-bold text-primary text-uppercase mb-3")
                    +"Affected projects"
                }
                button {
                    type = ButtonType.button
                    className = ClassName("btn btn-primary")
                    +"+"
                }
            }
        }
    }
}
