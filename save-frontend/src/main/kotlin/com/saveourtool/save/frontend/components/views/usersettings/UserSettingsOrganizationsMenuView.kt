package com.saveourtool.save.frontend.components.views.usersettings

import com.saveourtool.save.domain.Role
import com.saveourtool.save.entities.OrganizationStatus
import com.saveourtool.save.frontend.components.basic.cardComponent
import com.saveourtool.save.frontend.components.basic.organizations.responseChangeOrganizationStatus
import com.saveourtool.save.frontend.components.views.actionButtonClasses
import com.saveourtool.save.frontend.components.views.actionIconClasses
import com.saveourtool.save.frontend.externals.fontawesome.faTrashAlt
import com.saveourtool.save.frontend.externals.fontawesome.fontAwesomeIcon
import com.saveourtool.save.frontend.utils.actionButton
import com.saveourtool.save.frontend.utils.buttonBuilder
import com.saveourtool.save.v1

import csstype.ClassName
import react.*
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.ul

import kotlinx.browser.window

@Suppress("MISSING_KDOC_TOP_LEVEL", "TOO_LONG_FUNCTION", "LongMethod")
class UserSettingsOrganizationsMenuView : UserSettingsView() {
    private val organizationListCard = cardComponent(isBordered = false, hasBg = true)

    override fun renderMenu(): FC<UserSettingsProps> = FC { props ->
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
                state.selfOrganizationWithUserList.forEach { organizationWithUsers ->
                    val organizationDto = organizationWithUsers.organization
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
                                val role = state.userInfo?.name?.let { organizationWithUsers.userRoles[it] } ?: Role.NONE
                                if (role.isHigherOrEqualThan(Role.OWNER)) {
                                    actionButton {
                                        title = "WARNING: About to delete this organization..."
                                        errorTitle = "You cannot delete the organization ${organizationDto.name}"
                                        message = "Are you sure you want to ban the organization ${organizationDto.name}?"
                                        buttonStyleBuilder = { childrenBuilder ->
                                            with(childrenBuilder) {
                                                fontAwesomeIcon(icon = faTrashAlt, classes = actionIconClasses.joinToString(" "))
                                            }
                                        }
                                        classes = actionButtonClasses.joinToString(" ")
                                        modalButtons = { action, window, childrenBuilder ->
                                            with(childrenBuilder) {
                                                buttonBuilder(label = "Yes, delete ${organizationDto.name}", style = "danger", classes = "mr-2") {
                                                    action()
                                                    window.closeWindow()
                                                }
                                                buttonBuilder("Cancel") {
                                                    window.closeWindow()
                                                }
                                            }
                                        }
                                        onActionSuccess = { _ ->
                                            setState { selfOrganizationWithUserList = selfOrganizationWithUserList.minusElement(organizationWithUsers) }
                                        }
                                        conditionClick = false
                                        sendRequest = { _ ->
                                            responseChangeOrganizationStatus(organizationDto.name, OrganizationStatus.DELETED)
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
}
