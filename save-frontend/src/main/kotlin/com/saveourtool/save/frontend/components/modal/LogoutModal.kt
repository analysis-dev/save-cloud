/**
 * A component for logout modal window
 */

package com.saveourtool.save.frontend.components.modal

import com.saveourtool.save.frontend.externals.modal.ModalProps
import com.saveourtool.save.frontend.externals.modal.modal
import com.saveourtool.save.frontend.utils.loadingHandler
import com.saveourtool.save.frontend.utils.post
import com.saveourtool.save.frontend.utils.spread
import com.saveourtool.save.frontend.utils.useDeferredRequest

import csstype.ClassName
import org.w3c.fetch.Headers
import react.FC
import react.dom.aria.ariaLabel
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h5
import react.dom.html.ReactHTML.span

import kotlinx.browser.window

/**
 * @param closeCallback a callback to call to close the modal
 * @return a Component
 */
@Suppress("TOO_LONG_FUNCTION", "LongMethod")
fun logoutModal(
    closeCallback: () -> Unit
) = FC<ModalProps> { props ->
    val doLogoutRequest = useDeferredRequest {
        val replyToLogout = post(
            "${window.location.origin}/logout",
            Headers(),
            "ping",
            loadingHandler = ::loadingHandler,
        )
        if (replyToLogout.ok) {
            // logout went good, need either to reload page or to setUserInfo(null) and use redirection like `window.location.href = window.location.origin`
            window.location.href = "${window.location.origin}/#"
            window.location.reload()
        } else {
            // close this modal to allow user to see modal with error description
            closeCallback()
        }
    }

    modal {
        spread(props) { key, value ->
            this.asDynamic()[key] = value
        }
        div {
            className = ClassName("modal-dialog")
            asDynamic()["role"] = "document"
            div {
                className = ClassName("modal-content")
                div {
                    className = ClassName("modal-header")
                    h5 {
                        className = ClassName("modal-title")
                        +"Ready to Leave?"
                    }
                    button {
                        className = ClassName("close")
                        type = react.dom.html.ButtonType.button
                        asDynamic()["data-dismiss"] = "modal"
                        ariaLabel = "Close"
                        span {
                            asDynamic()["aria-hidden"] = "true"
                            onClick = { closeCallback() }
                            +js("String.fromCharCode(215)").unsafeCast<String>()
                        }
                    }
                }
            }
            div {
                className = ClassName("modal-body")
                +"Select \"Logout\" below if you are ready to end your current session."
            }
            div {
                className = ClassName("modal-footer")
                button {
                    className = ClassName("btn btn-secondary")
                    type = react.dom.html.ButtonType.button
                    asDynamic()["data-dismiss"] = "modal"
                    onClick = { closeCallback() }
                    +"Cancel"
                }
                button {
                    className = ClassName("btn btn-primary")
                    type = react.dom.html.ButtonType.button
                    onClick = {
                        doLogoutRequest()
                    }
                    +"Logout"
                }
            }
        }
    }
}
