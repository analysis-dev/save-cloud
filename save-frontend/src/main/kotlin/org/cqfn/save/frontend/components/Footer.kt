package org.cqfn.save.frontend.components

import generated.SAVE_VERSION
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.dom.br
import react.dom.div
import react.dom.footer
import react.dom.span

/**
 * A web page footer component
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@Suppress("EMPTY_BLOCK_STRUCTURE_ERROR")
class Footer : RComponent<RProps, RState>() {
    override fun RBuilder.render() {
        footer("sticky-footer bg-white") {
            div("container my-auto") {
                div("copyright text-center my-auto") {
                    span {
                        +"Copyright &copy; SAVE 2021"
                        br {}
                        +"Version $SAVE_VERSION"
                    }
                }
            }
        }
    }
}
