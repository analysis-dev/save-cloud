/**
 * Card with our e-mail - where you can propose a contest
 */

package com.saveourtool.save.frontend.components.views.contests

import csstype.*
import react.ChildrenBuilder
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.strong

import kotlinx.js.jso

/**
 * rendering of a card where we suggest to propose new custom contests
 */
@Suppress("MAGIC_NUMBER")
fun ChildrenBuilder.proposeContest() {
    div {
        className = ClassName("row mt-3")

        div {
            className = ClassName("col-lg-3 pr-0")
            img {
                src = "img/undraw_mailbox_re_dvds.svg"
                style = jso {
                    width = "100%".unsafeCast<Width>()
                }
            }
        }

        div {
            className = ClassName("col-lg-9")
            p {
                +"Want to make your own contest? Write us an e-mail:"
            }
        }
    }

    div {
        className = ClassName("row")
        div {
            className = ClassName("col-lg-3 pr-0")
        }

        div {
            className = ClassName("col-lg-9 justify-content-center")

            a {
                href = "mailto:saveourtool@gmail.com"
                strong {
                    className = ClassName("d-inline-block mb-2 text-success")
                    +"saveourtool@gmail.com"
                }
            }
        }
    }
}
