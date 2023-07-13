@file:Suppress("HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE")

package com.saveourtool.save.frontend.components.mobile

import com.saveourtool.save.frontend.components.views.welcome.chevron
import com.saveourtool.save.frontend.components.views.welcome.pagers.allWelcomePagers
import com.saveourtool.save.frontend.components.views.welcome.pagers.renderReadMorePage
import com.saveourtool.save.frontend.components.views.welcome.welcomeMarketingTitle
import com.saveourtool.save.frontend.externals.animations.animator
import com.saveourtool.save.frontend.externals.animations.scrollContainer
import com.saveourtool.save.frontend.externals.animations.scrollPage
import com.saveourtool.save.frontend.utils.particles

import js.core.jso
import react.ChildrenBuilder
import react.VFC
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h4
import react.dom.html.ReactHTML.img
import web.cssom.*

/**
 * As a temp stub it was decided to make several views to make SAVE looking nice on mobile devices
 */
val saveWelcomeMobileView: VFC = VFC {
    div {
        style = jso {
            background =
                    "-webkit-linear-gradient(270deg, rgb(209, 229, 235),  rgb(217, 215, 235))".unsafeCast<Background>()
        }
        particles()
        sorryYourScreenIsTooSmall()
    }
}

private fun ChildrenBuilder.sorryYourScreenIsTooSmall() {
    notSupportedMobileYet()
    title()
    chevron("rgb(6, 7, 89)")

    @Suppress("EMPTY_BLOCK_STRUCTURE_ERROR")
    scrollContainer {
        scrollPage {}
        allWelcomePagers.forEach { pager ->
            scrollPage { }
            pager.forEach {
                scrollPage {
                    div {
                        animator {
                            animation = it.animation
                            it.renderPage(this)
                        }
                    }
                }
            }
        }
    }

    renderReadMorePage()
}

private fun ChildrenBuilder.notSupportedMobileYet() {
    div {
        className = ClassName("row d-flex justify-content-center mx-auto text-danger mt-2")
        h4 {
            className = ClassName("text-center")
            +"We do not "
            b {
                +"yet "
            }
            +"support devices with small screen size. But you can get familiar with SAVE using the information below."
        }
    }
    div {
        className = ClassName("row d-flex justify-content-center mx-auto")
        img {
            src = "img/sad_cat.gif"
            @Suppress("MAGIC_NUMBER")
            style = jso {
                width = 12.rem
            }
        }
    }
}

private fun ChildrenBuilder.title() {
    div {
        className = ClassName("row justify-content-center mx-auto")
        // Marketing information
        welcomeMarketingTitle("text-primary", true)
    }
}
