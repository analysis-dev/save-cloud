/**
 * Preparation for a participation card
 */

package com.saveourtool.save.frontend.components.views.contests

import csstype.ClassName
import csstype.rem
import react.VFC
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div

import kotlinx.js.jso

val yourContests = yourContests()

private fun yourContests() = VFC {
    div {
        className = ClassName("col-lg-5")
        div {
            className = ClassName("card flex-md-row mb-1 box-shadow")
            style = jso {
                minHeight = 10.rem
            }
            ReactHTML.strong {
                className = ClassName("d-inline-block mb-2 text-info")
                +"Your projects are participating in following contests:"
            }
        }
    }
}
