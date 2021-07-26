package org.cqfn.save.frontend.components.views

import kotlinext.js.jsObject
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.dom.div
import react.dom.p
import react.router.dom.LinkComponent

/**
 * A [RComponent] representing fallback page with 404 error
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class FallbackView : RComponent<RProps, RState>() {
    @Suppress("ForbiddenComment")
    override fun RBuilder.render() {
        div("text-center") {
            div("error mx-auto") {
                attrs["data-text"] = "404"
                +"404"
            }
            p("lead text-gray-800 mb-5") {
                +"Page not found"
            }
            child(type = LinkComponent, props = jsObject()) {
                attrs.to = "/"
                +"← Back to the main page"
            }
        }
    }
}
