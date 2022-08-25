@file:Suppress("FILE_WILDCARD_IMPORTS", "WildcardImport", "HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE")

package com.saveourtool.save.frontend.components.views

import com.saveourtool.save.domain.Role
import com.saveourtool.save.entities.ContestDto
import com.saveourtool.save.entities.benchmarks.MenuBar
import com.saveourtool.save.frontend.components.RequestStatusContext
import com.saveourtool.save.frontend.components.basic.contests.contestInfoMenu
import com.saveourtool.save.frontend.components.basic.contests.contestSubmissionsMenu
import com.saveourtool.save.frontend.components.basic.contests.contestSummaryMenu
import com.saveourtool.save.frontend.components.requestStatusContext
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.frontend.utils.classLoadingHandler
import com.saveourtool.save.info.UserInfo
import com.saveourtool.save.validation.FrontendRoutes

import csstype.ClassName
import org.w3c.fetch.Headers
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.nav
import react.dom.html.ReactHTML.p

/**
 * Enum that defines the bar that is chosen
 */
enum class ContestMenuBar {
    INFO,
    SUBMISSIONS,
    SUMMARY,
    ;

    companion object : MenuBar<ContestMenuBar> {
        override val defaultTab: ContestMenuBar = INFO
        val listOfStringEnumElements = ContestMenuBar.values().map { it.name.lowercase() }
        override val regex = Regex("/project/[^/]+/[^/]+/[^/]+")
        override var paths: Pair<String, String> = "" to ""
        override fun valueOf(elem: String): ContestMenuBar = ContestMenuBar.valueOf(elem)
        override fun values(): Array<ContestMenuBar> = ContestMenuBar.values()
        override fun findEnumElements(elem: String): ContestMenuBar? = values().find { it.name.lowercase() == elem }
        override fun setPath(shortPath: String, longPath: String) {
            paths = shortPath to longPath
        }

        override fun returnStringOneOfElements(elem: ContestMenuBar): String = elem.name

        override fun isNotAvailableWithThisRole(role: Role, elem: ContestMenuBar?, flag: Boolean?): Boolean = false
    }
}

/**
 * `Props` retrieved from router
 */
@Suppress("MISSING_KDOC_CLASS_ELEMENTS")
external interface ContestViewProps : Props {
    var currentUserInfo: UserInfo?
    var currentContestName: String?
}

/**
 * [State] for [ContestView]
 */
external interface ContestViewState : State, HasSelectedMenu<ContestMenuBar>

/**
 * A view with collection of projects
 */
@JsExport
@OptIn(ExperimentalJsExport::class)
class ContestView : AbstractView<ContestViewProps, ContestViewState>(false) {
    init {
        state.selectedMenu = null
    }

    override fun componentDidMount() {
        super.componentDidMount()

        ContestMenuBar.setPath("#/${FrontendRoutes.CONTESTS.path}/${props.currentContestName}", "#/contests/${FrontendRoutes.CONTESTS.path}/${props.currentContestName}")
        urlAnalysis(ContestMenuBar, Role.NONE, false)
    }

    override fun ChildrenBuilder.render() {
        div {
            className = ClassName("d-flex justify-content-around")
            h1 {
                +"${props.currentContestName}"
            }
        }
        renderContestMenuBar()

        when (state.selectedMenu) {
            ContestMenuBar.INFO -> renderInfo()
            ContestMenuBar.SUBMISSIONS -> renderSubmissions()
            ContestMenuBar.SUMMARY -> renderSummary()
            else -> throw NotImplementedError()
        }
    }

    private fun ChildrenBuilder.renderSubmissions() {
        contestSubmissionsMenu {
            contestName = props.currentContestName ?: "UNDEFINED"
        }
    }

    private fun ChildrenBuilder.renderSummary() {
        contestSummaryMenu {
            contestName = props.currentContestName ?: "UNDEFINED"
        }
    }

    private fun ChildrenBuilder.renderInfo() {
        contestInfoMenu {
            contestName = props.currentContestName ?: "UNDEFINED"
        }
    }

    private fun ChildrenBuilder.renderContestMenuBar() {
        div {
            className = ClassName("row align-items-center justify-content-center")
            nav {
                className = ClassName("nav nav-tabs mb-4")
                ContestMenuBar.values()
                    .forEachIndexed { i, contestMenu ->
                        li {
                            className = ClassName("nav-item")
                            val classVal =
                                    if ((i == 0 && state.selectedMenu == null) || state.selectedMenu == contestMenu) " active font-weight-bold" else ""
                            p {
                                className = ClassName("nav-link $classVal text-gray-800")
                                onClick = {
                                    if (state.selectedMenu != contestMenu) {
                                        // changeUrl(contestMenu)
                                        changeUrl(contestMenu, ContestMenuBar)
                                        setState { selectedMenu = contestMenu }
                                    }
                                    +contestMenu.name
                                }
                            }
                        }
                    }
            }
        }
    }

    private suspend fun ComponentWithScope<*, *>.getContest(contestName: String) = get(
        "$apiUrl/contests/$contestName",
        Headers().apply {
            set("Accept", "application/json")
        },
        loadingHandler = ::classLoadingHandler,
    )
        .unsafeMap {
            it.decodeFromJsonString<ContestDto>()
        }

    companion object : RStatics<ContestViewProps, ContestViewState, ContestView, Context<RequestStatusContext>>(ContestView::class) {
        init {
            contextType = requestStatusContext
        }
    }
}
