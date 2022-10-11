package com.saveourtool.save.frontend.components.views.usersettings

import com.saveourtool.save.domain.Role
import com.saveourtool.save.frontend.components.basic.cardComponent
import com.saveourtool.save.frontend.components.modal.displayModal
import com.saveourtool.save.frontend.externals.fontawesome.faPlus
import com.saveourtool.save.frontend.externals.fontawesome.faTrashAlt
import com.saveourtool.save.frontend.externals.fontawesome.fontAwesomeIcon
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.frontend.utils.noopLoadingHandler
import com.saveourtool.save.v1
import csstype.BorderRadius

import csstype.ClassName
import kotlinx.js.jso
import react.*
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.a

import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.ul

@Suppress("MISSING_KDOC_TOP_LEVEL", "TOO_LONG_FUNCTION", "LongMethod")
class UserSettingsOrganizationsMenuView : UserSettingsView() {
    private val organizationListCard = cardComponent(isBordered = false, hasBg = true)

    override fun renderMenu(): FC<UserSettingsProps> = FC { props ->
        val windowOpenness = useWindowOpenness()
        val (stateOrganizationName, setOrganizationName) = useState("")
        displayModal(
            isOpen = windowOpenness.isOpen(),
            title = "Warning: recovered of organization",
            message = "You are about to recove organization $stateOrganizationName. Are you sure?",
            onCloseButtonPressed = windowOpenness.closeWindowAction()
        ) {
            buttonBuilder("Yes, recovered $stateOrganizationName", "warning") {
                recoveryOrganization(stateOrganizationName)
                windowOpenness.closeWindow()
            }
            buttonBuilder("Cancel") {
                windowOpenness.closeWindow()
            }
        }


        organizationListCard {
            div {
                className = ClassName("d-sm-flex align-items-center justify-content-center mb-4 mt-4")
                h1 {
                    className = ClassName("h3 mb-0 mt-2 text-gray-800")
                    +"Organizations"
                }
            }

            ul {
                className = ClassName("list-group list-group-flush")
                state.selfOrganizationDtos.forEach { organizationDto ->
                    li {
                        className = ClassName("list-group-item")
                        div {
                            className = ClassName("row justify-content-between align-items-center")
                            div {
                                className = ClassName("align-items-center ml-3")
                                img {
                                    className = ClassName("avatar avatar-user width-full border color-bg-default rounded-circle")
                                    src = organizationDto.avatar?.let {
                                        "/api/$v1/avatar$it"
                                    } ?: "img/company.svg"
                                    height = 60.0
                                    width = 60.0
                                }
                                a {
                                    className = ClassName("ml-2")
                                    href = "#/${organizationDto.name}"
                                    +organizationDto.name
                                }
                            }
                            div {
                                className = ClassName("col-5 align-self-right d-flex align-items-center justify-content-end")
                                val role = state.userInfo?.name?.let { organizationDto.userRoles[it] } ?: Role.NONE
                                if (role.isHigherOrEqualThan(Role.OWNER)) {
                                    deleteOrganizationButton {
                                        organizationName = organizationDto.name
                                        onDeletionSuccess = {
                                            setState {
                                                selfOrganizationDtos =
                                                    selfOrganizationDtos.minusElement(organizationDto)
                                                selfDeletedOrganizationDtos =
                                                    selfDeletedOrganizationDtos.plusElement(organizationDto)
                                            }
                                        }
                                        buttonStyleBuilder = { childrenBuilder ->
                                            with(childrenBuilder) {
                                                fontAwesomeIcon(icon = faTrashAlt)
                                            }
                                        }
                                        classes = "btn mr-3"
                                        userRole = role
                                    }
                                }
                                div {
                                    className = ClassName("mr-3")
                                    +role.formattedName
                                }
                            }
                        }
                    }
                }

                state.selfDeletedOrganizationDtos.forEach { organizationDto ->
                    li {
                        className = ClassName("list-group-item")
                        div {
                            className = ClassName("row justify-content-between align-items-center")
                            div {
                                className = ClassName("align-items-center ml-3")
                                img {
                                    className = ClassName("avatar avatar-user width-full border color-bg-default rounded-circle mr-2")
                                    src = organizationDto.avatar?.let {
                                        "/api/$v1/avatar$it"
                                    } ?: "img/company.svg"
                                    height = 60.0
                                    width = 60.0
                                }
                                +organizationDto.name
                                ReactHTML.span {
                                    className = ClassName("border ml-2 pr-1 pl-1 text-xs text-muted ")
                                    style = jso { borderRadius = "2em".unsafeCast<BorderRadius>() }
                                    +"deleted"
                                }
                            }
                            div {
                                className = ClassName("col-5 align-self-right d-flex align-items-center justify-content-end")
                                val role = state.userInfo?.name?.let { organizationDto.userRoles[it] } ?: Role.NONE
                                if (role.isHigherOrEqualThan(Role.OWNER)) {
                                    div {
                                        ReactHTML.button {
                                            className = ClassName("btn mr-3")
                                            fontAwesomeIcon(icon = faPlus)
                                            id = "recovery-organization-${organizationDto.name}"
                                            onClick = {
                                                setState {
                                                    setOrganizationName(organizationDto.name)
                                                    windowOpenness.openWindow()
                                                    selfDeletedOrganizationDtos = selfDeletedOrganizationDtos.minusElement(organizationDto)
                                                    selfOrganizationDtos = selfOrganizationDtos.plusElement(organizationDto)
                                                }
                                            }
                                        }
                                    }
                                }
                                div {
                                    className = ClassName("mr-3")
                                    +role.formattedName
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun recoveryOrganization(organizationName: String) {
        useDeferredRequest {
            post (
                "$apiUrl/organizations/${organizationName}/recovery",
                headers = jsonHeaders,
                body = undefined,
                loadingHandler = ::noopLoadingHandler,
            )
        }
    }
}
