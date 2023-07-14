package com.saveourtool.save.frontend.components.views

import com.saveourtool.save.frontend.utils.ComponentWithScope
import com.saveourtool.save.frontend.utils.SAVE_BLUE_GRADIENT

import react.*

import kotlinx.browser.document

/**
 * Abstract view class that should be used in all functional views
 */
abstract class AbstractView<P : Props, S : State>(private val hasBg: Boolean = true) : ComponentWithScope<P, S>() {
    // A small hack to avoid duplication of main content-wrapper from App.kt
    // We will have custom background only for sign-up and sign-in views
    override fun componentDidMount() {
        val style = if (hasBg) {
            Style(
                SAVE_BLUE_GRADIENT,
                "",
                "transparent",
                "px-0",
                ""
            )
        } else {
            Style(
                "bg-light",
                "bg-dark",
                "bg-dark",
                "",
                "mb-3"
            )
        }

        document.getElementById("content-wrapper")?.setAttribute(
            "style",
            "background: ${style.globalBackground}"
        )

        configureTopBar(style)
    }

    private fun configureTopBar(style: Style) {
        val topBar = document.getElementById("navigation-top-bar")
        topBar?.setAttribute(
            "class",
            "navbar navbar-expand ${style.topBarBgColor} navbar-dark topbar ${style.marginBottomForTopBar} " +
                    "static-top shadow mr-1 ml-1 rounded"
        )

        topBar?.setAttribute(
            "style",
            "background: ${style.topBarTransparency}"
        )

        val container = document.getElementById("common-save-container")
        container?.setAttribute(
            "class",
            "container-fluid ${style.borderForContainer}"
        )
    }

    /**
     * @property globalBackground
     * @property topBarBgColor
     * @property topBarTransparency
     * @property borderForContainer
     * @property marginBottomForTopBar
     */
    private data class Style(
        val globalBackground: String,
        val topBarBgColor: String,
        val topBarTransparency: String,
        val borderForContainer: String,
        val marginBottomForTopBar: String,
    )
}
