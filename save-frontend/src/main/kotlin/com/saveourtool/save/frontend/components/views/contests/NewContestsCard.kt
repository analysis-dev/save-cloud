/**
 * card with newly added contests
 */

package com.saveourtool.save.frontend.components.views.contests

import com.saveourtool.save.entities.ContestDto
import com.saveourtool.save.frontend.utils.*

import csstype.ClassName
import csstype.rem
import react.VFC
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.strong
import react.useState

import kotlinx.js.jso

val newContestsCard = newContestsCard()

/**
 * rendering of newly added contests
 */
@Suppress("MAGIC_NUMBER")
private fun newContestsCard() = VFC {
    val (newContests, setNewContests) = useState<List<ContestDto>>(emptyList())
    useRequest {
        val contests: List<ContestDto> = get(
            url = "$apiUrl/contests/newest?pageSize=3",
            headers = jsonHeaders,
            loadingHandler = ::loadingHandler
        )
            .decodeFromJsonString()
        setNewContests(contests.sortedByDescending { it.creationTime })
    }

    div {
        className = ClassName("col-lg-6")
        div {
            className = ClassName("card flex-md-row mb-1 box-shadow")
            style = jso {
                height = 14.rem
            }

            div {
                className = ClassName("card-body d-flex flex-column align-items-start")
                strong {
                    className = ClassName("d-inline-block mb-2 text-success")
                    +"""New contests"""
                }
                h3 {
                    className = ClassName("mb-0")
                    a {
                        className = ClassName("text-dark")
                        href = "#/contests"
                        +"Hurry up! 🔥"
                    }
                }
                p {
                    className = ClassName("card-text mb-auto")
                    +"Checkout and participate in newest contests!"
                }
                newContests.forEach { contest ->
                    a {
                        href = "#/contests/${contest.name}"
                        +contest.name
                    }
                }
            }

            img {
                className = ClassName("card-img-right flex-auto d-none d-md-block")
                src = "img/undraw_exciting_news_re_y1iw.svg"
                style = jso {
                    width = 12.rem
                }
            }
        }
    }
}
