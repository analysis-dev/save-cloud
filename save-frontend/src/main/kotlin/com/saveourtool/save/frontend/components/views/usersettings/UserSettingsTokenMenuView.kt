package com.saveourtool.save.frontend.components.views.usersettings

import com.saveourtool.save.frontend.components.basic.cardComponent
import com.saveourtool.save.frontend.utils.apiUrl
import com.saveourtool.save.frontend.utils.jsonHeaders
import com.saveourtool.save.frontend.utils.noopLoadingHandler
import com.saveourtool.save.frontend.utils.post

import kotlinext.js.assign
import react.VFC
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.input
import web.cssom.ClassName
import web.html.ButtonType

import kotlinx.coroutines.launch

@Suppress("MISSING_KDOC_TOP_LEVEL")
class UserSettingsTokenMenuView : UserSettingsView() {
    private val tokenCard = cardComponent(isBordered = false, hasBg = true)
    @Suppress("TOO_LONG_FUNCTION")
    override fun renderMenu(): VFC = VFC {
        tokenCard {
            div {
                className = ClassName("d-sm-flex align-items-center justify-content-center mb-4")
                h1 {
                    className = ClassName("h3 mb-0 mt-2 text-gray-800")
                    +"Personal access tokens"
                }
            }

            div {
                className = ClassName("row justify-content-center")
                button {
                    type = ButtonType.button
                    className = ClassName("btn btn-outline-primary mb-2 mr-2")
                    +"Generate new token"
                    onClick = {
                        generateToken()
                    }
                }
            }

            state.token?.let {
                div {
                    className = ClassName("col-12 mt-3")
                    input {
                        value = state.token ?: ""
                        required = true
                        className = ClassName("form-control")
                    }
                    div {
                        className = ClassName("invalid-feedback d-block")
                        +"This is your unique token. It will be shown to you only once. Please remember it."
                    }
                }
            }
        }
    }

    @Suppress("MAGIC_NUMBER")
    private fun generateToken() {
        var token = "ghp_"
        val charPool = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        while (token.length < 40) {
            token += charPool.random()
        }
        setState(
            assign(state) {
                this.token = token
            }
        ) {
            scope.launch {
                post(
                    "$apiUrl/users/${state.userInfo!!.name}/save/token",
                    jsonHeaders,
                    state.token,
                    loadingHandler = ::noopLoadingHandler,
                )
            }
        }
    }
}
