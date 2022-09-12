/**
 * Contests "market" - showcase for users, where they can navigate and check contests
 */

@file:Suppress("FILE_WILDCARD_IMPORTS", "WildcardImport", "MAGIC_NUMBER")

package com.saveourtool.save.frontend.components.views.contests

import com.saveourtool.save.frontend.components.RequestStatusContext
import com.saveourtool.save.frontend.components.requestStatusContext
import com.saveourtool.save.frontend.components.views.AbstractView
import com.saveourtool.save.info.UserInfo
import com.saveourtool.save.utils.LocalDateTime
import com.saveourtool.save.utils.getCurrentLocalDateTime

import csstype.ClassName
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.main

/**
 * TODO:
 * 2. Link to create contests
 * 3. Grand champions
 * 4. Countdown till the end of contests
 */

/**
 * [Props] retrieved from router
 */
@Suppress("MISSING_KDOC_CLASS_ELEMENTS")
external interface ContestListViewProps : Props {
    var currentUserInfo: UserInfo?
}

/**
 * [State] of [ContestListView] component
 */
external interface ContestListViewState : State {
    /**
     * current time
     */
    var currentDateTime: LocalDateTime
}

/**
 * A view with collection of contests
 */
@JsExport
@OptIn(ExperimentalJsExport::class)
class ContestListView : AbstractView<ContestListViewProps, ContestListViewState>() {
    init {
        state.currentDateTime = getCurrentLocalDateTime()
    }

    @Suppress("TOO_LONG_FUNCTION", "LongMethod")
    override fun ChildrenBuilder.render() {
        main {
            className = ClassName("main-content mt-0 ps")
            div {
                className = ClassName("page-header align-items-start min-vh-100")
                div {
                    className = ClassName("row justify-content-center")
                    div {
                        className = ClassName("col-lg-9")

                        div {
                            className = ClassName("row mb-2")
                            featuredContest()
                            newContestsCard()
                        }

                        div {
                            className = ClassName("row mb-2")
                            yourContests()
                            statistics()
                            proposeContest()
                        }

                        div {
                            className = ClassName("row mb-2")
                            userRating()
                            contestList()
                            myProjectsRating {
                                currentUserInfo = props.currentUserInfo
                            }
                        }
                    }
                }
            }
        }
    }

    companion object :
        RStatics<ContestListViewProps, ContestListViewState, ContestListView, Context<RequestStatusContext>>(
        ContestListView::class
    ) {
        init {
            contextType = requestStatusContext
        }
    }
}
