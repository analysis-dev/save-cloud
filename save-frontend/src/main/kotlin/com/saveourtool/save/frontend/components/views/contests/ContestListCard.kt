/**
 * a card with contests list from ContestListView
 */

@file:Suppress("FILE_NAME_MATCH_CLASS")

package com.saveourtool.save.frontend.components.views.contests

import com.saveourtool.save.entities.ContestDto
import com.saveourtool.save.frontend.components.basic.ContestNameProps
import com.saveourtool.save.frontend.components.basic.showContestEnrollerModal
import com.saveourtool.save.frontend.components.modal.displayModal
import com.saveourtool.save.frontend.components.modal.mediumTransparentModalStyle
import com.saveourtool.save.frontend.externals.fontawesome.faArrowRight
import com.saveourtool.save.frontend.externals.fontawesome.faCode
import com.saveourtool.save.frontend.externals.fontawesome.fontAwesomeIcon
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.validation.FrontendRoutes

import csstype.ClassName
import csstype.rem
import react.*
import react.dom.html.ButtonType
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.strong

import kotlinx.js.jso

val contestList = contestList()

/**
 * this enum is used in a tab for contests
 */
enum class ContestTypesTab {
    ACTIVE, FINISHED, PLANNED
}

private fun ChildrenBuilder.contests(
    selectedTab: ContestTypesTab,
    activeContests: Set<ContestDto>,
    finishedContests: Set<ContestDto>,
    onEnrollButtonPressed: (String) -> Unit,
) {
    when (selectedTab) {
        ContestTypesTab.ACTIVE -> contestListTable(activeContests, onEnrollButtonPressed)
        ContestTypesTab.FINISHED -> contestListTable(finishedContests, null)
        ContestTypesTab.PLANNED -> {
            // FixMe: Add planned contests
        }
    }
}

@Suppress("MAGIC_NUMBER")
private fun ChildrenBuilder.contestListTable(
    contests: Set<ContestDto>,
    onEnrollButtonPressed: ((String) -> Unit)?,
) {
    contests.forEach { contest ->
        div {
            className = ClassName("media text-muted pb-3")
            img {
                className = ClassName("rounded")
                src = "img/undraw_code_inspection_bdl7.svg"
                style = jso {
                    width = 4.2.rem
                }
            }

            div {
                className = ClassName("media-body pb-3 mb-0 small lh-125 border-bottom border-gray text-left")
                strong {
                    className = ClassName("d-block text-gray-dark")
                    +contest.name
                }
                +(contest.description ?: "")

                div {
                    className = ClassName("navbar-landing mt-3")
                    button {
                        type = ButtonType.button
                        className = ClassName("btn btn-outline-success ml-auto mr-2")
                        disabled = onEnrollButtonPressed == null
                        onClick = {
                            onEnrollButtonPressed?.let {
                                onEnrollButtonPressed(contest.name)
                            }
                        }
                        +"Enroll"
                    }
                    a {
                        className = ClassName("btn btn-outline-info mr-2")
                        href = "#/${FrontendRoutes.CONTESTS.path}/${contest.name}"
                        +"Rules and more "
                        fontAwesomeIcon(icon = faArrowRight)
                    }
                }
            }
        }
    }
}

/**
 * @return functional component that render the stylish table with contests
 */
@Suppress("TOO_LONG_FUNCTION", "LongMethod")
private fun contestList() = VFC {
    val (isContestEnrollerModalOpen, setIsContestEnrollerModalOpen) = useState(false)
    val (isConfirmationModalOpen, setIsConfirmationModalOpen) = useState(false)
    val (enrollmentResponseString, setEnrollmentResponseString) = useState("")
    val (selectedContestName, setSelectedContestName) = useState("")
    showContestEnrollerModal(
        isContestEnrollerModalOpen,
        ContestNameProps(selectedContestName),
        { setIsContestEnrollerModalOpen(false) }
    ) {
        setEnrollmentResponseString(it)
        setIsConfirmationModalOpen(true)
        setIsContestEnrollerModalOpen(false)
    }

    displayModal(
        isConfirmationModalOpen,
        "Contest Enroller",
        enrollmentResponseString,
        mediumTransparentModalStyle,
        { setIsConfirmationModalOpen(false) }
    ) {
        buttonBuilder("Close", "secondary") {
            setIsConfirmationModalOpen(false)
        }
    }

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

    val (selectedTab, setSelectedTab) = useState(ContestTypesTab.ACTIVE)
    div {
        className = ClassName("col-lg-6")
        div {
            className = ClassName("card flex-md-row mb-1 box-shadow")
            style = jso {
                minHeight = 40.rem
            }

            div {
                className = ClassName("col")

                title(" Available Contests", faCode)

                tab(selectedTab.name, ContestTypesTab.values().map { it.name }) {
                    setSelectedTab(ContestTypesTab.valueOf(it))
                }

                contests(selectedTab, activeContests, finishedContests) {
                    setSelectedContestName(it)
                    setIsContestEnrollerModalOpen(true)
                }
            }
        }
    }
}
