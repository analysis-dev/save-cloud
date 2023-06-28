package com.saveourtool.save.frontend.components.views.welcome.pagers

import com.saveourtool.save.frontend.externals.animations.*

import js.core.jso
import react.ChildrenBuilder
import react.dom.html.ReactHTML.img
import web.cssom.Height
import web.cssom.rem

@Suppress("CUSTOM_GETTERS_SETTERS", "MAGIC_NUMBER")
object AwesomeBenchmarks : WelcomePager {
    override val animation: Animation
        get() = batch(fade(), sticky())

    override fun renderPage(childrenBuilder: ChildrenBuilder) {
        childrenBuilder.renderAnimatedPage()
    }

    private fun ChildrenBuilder.renderAnimatedPage() {
        img {
            @Suppress("MAGIC_NUMBER")
            style = jso {
                width = 80.rem
                height = "auto".unsafeCast<Height>()
            }
            src = "img/awesome_view.png"
        }
    }
}
