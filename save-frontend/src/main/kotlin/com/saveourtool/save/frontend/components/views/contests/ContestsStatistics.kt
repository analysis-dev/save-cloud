/**
 * Statistics of SAVE contests: active and finished contests
 */

package com.saveourtool.save.frontend.components.views.contests

import com.saveourtool.save.entities.ContestDto
import com.saveourtool.save.frontend.utils.*

import csstype.ClassName
import csstype.rem
import react.ChildrenBuilder
import react.VFC
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.strong
import react.useState

import kotlinx.js.jso

val statistics = statistics()

/**
 * @param activeContests
 * @param finishedContests
 */
fun ChildrenBuilder.stats(activeContests: Set<ContestDto>, finishedContests: Set<ContestDto>) {
    div {
        className = ClassName("row border-bottom mb-3 mx-3")

        div {
            className = ClassName("col-lg-6 mt-2 mb-2")
            div {
                className = ClassName("row justify-content-center")
                strong {
                    className = ClassName("d-inline-block mb-2 card-text")
                    +"Active contests:"
                }
            }
            div {
                className = ClassName("row justify-content-center")
                h1 {
                    className = ClassName("text-dark")
                    +activeContests.size.toString()
                }
            }
        }
        div {
            className = ClassName("col-lg-6 mt-2")
            div {
                className = ClassName("row justify-content-center")
                strong {
                    className = ClassName("d-inline-block mb-2 card-text ")
                    +"Finished contests:"
                }
            }
            div {
                className = ClassName("row justify-content-center")
                h1 {
                    className = ClassName("text-dark")
                    +finishedContests.size.toString()
                }
            }
        }
    }
}

@Suppress("TOO_LONG_FUNCTION", "LongMethod")
private fun statistics() = VFC {
    val (activeContests, setActiveContests) = useState<Set<ContestDto>>(emptySet())
    useRequest {
        val contests: List<ContestDto> = get(
            url = "$apiUrl/contests/active",
            headers = jsonHeaders,
            loadingHandler = ::loadingHandler,
        )
            .decodeFromJsonString()
        setActiveContests(contests.toSet())
    }

    val (finishedContests, setFinishedContests) = useState<Set<ContestDto>>(emptySet())
    useRequest {
        val contests: List<ContestDto> = get(
            url = "$apiUrl/contests/finished",
            headers = jsonHeaders,
            loadingHandler = ::loadingHandler,
        )
            .decodeFromJsonString()
        setFinishedContests(contests.toSet())
    }

    div {
        className = ClassName("col-lg-4")
        div {
            className = ClassName("card flex-md-row mb-1 box-shadow")
            style = jso {
                minHeight = 15.rem
            }
            div {
                className = ClassName("col-lg-12")
                stats(activeContests, finishedContests)
                proposeContest()
            }
        }
    }
}
