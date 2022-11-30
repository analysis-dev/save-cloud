package com.saveourtool.save.frontend.components.views.welcome.pagers

import com.saveourtool.save.frontend.externals.animations.*
import react.ChildrenBuilder
import react.dom.html.ReactHTML.img

@Suppress("CUSTOM_GETTERS_SETTERS")
object HighLevelSave : WelcomePager {
    override val animation: Animation
        get() = moveUpFromBottom

    override fun renderPage(childrenBuilder: ChildrenBuilder) {
        childrenBuilder.renderAnimatedPage()
    }

    private fun ChildrenBuilder.renderAnimatedPage() {
        img {
            src = "img/save_hl.png"
        }
    }
}
