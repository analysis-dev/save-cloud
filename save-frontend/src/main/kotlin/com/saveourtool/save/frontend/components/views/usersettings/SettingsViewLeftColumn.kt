/**
 * In settings view we have two columns: this one is the left one
 */

package com.saveourtool.save.frontend.components.views.usersettings

import com.saveourtool.save.frontend.components.basic.avatarRenderer
import com.saveourtool.save.frontend.externals.fontawesome.*
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.validation.FrontendRoutes
import js.core.jso
import react.ChildrenBuilder
import react.FC
import react.VFC
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.h4
import react.dom.html.ReactHTML.h6
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.nav
import react.router.dom.Link
import react.useState
import web.cssom.Background
import web.cssom.ClassName
import web.cssom.TextDecoration
import web.cssom.rem

internal const val AVATAR_TITLE = "Upload avatar"

val leftSettingsColumn: FC<SettingsProps> = FC { props ->
    val (avatarImgLink, setAvatarImgLink) = useState<String?>(null)

    div {
        className = ClassName("card card-body pt-0 px-0 shadow")
        style = cardHeight
        div {
            className = ClassName("col mr-2 px-0")
            style = jso {
                background = "#e1e9ed".unsafeCast<Background>()
            }
            div {
                className = ClassName("mb-0 font-weight-bold text-gray-800")
                form {
                    div {
                        className = ClassName("row g-3 ml-3 mr-3 pb-2 pt-2 border-bottom")
                        div {
                            className = ClassName("col")
                            div {
                                className = ClassName("row justify-content-center")
                                img {
                                    className = ClassName("avatar avatar-user width-full border color-bg-default rounded-circle")
                                    src = avatarImgLink
                                        ?: props.userInfo?.avatar?.avatarRenderer()
                                        ?: AVATAR_PROFILE_PLACEHOLDER
                                    style = jso {
                                        height = 12.rem
                                        width = 12.rem
                                    }
                                }
                            }
                            div {
                                className = ClassName("col text-center mt-2")
                                div {
                                    className = ClassName("row justify-content-center")
                                    h4 {
                                        className = ClassName("mb-0 text-gray-800")
                                        +(props.userInfo?.name ?: "")
                                    }
                                }
                                div {
                                    className = ClassName("row justify-content-center")
                                    h6 {
                                        className = ClassName("mb-0 text-gray-800")
                                        +(props.userInfo?.realName.orEmpty())
                                    }
                                }
                                div {
                                    className = ClassName("row justify-content-center")
                                    h6 {
                                        Link {
                                            to = "/${FrontendRoutes.PROFILE}/${props.userInfo?.name}"
                                            style = jso {
                                                textDecoration = TextDecoration.underline
                                            }
                                            +"profile"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        @Suppress("EMPTY_BLOCK_STRUCTURE_ERROR")
        settingsTabs {}
    }
}

val settingsTabs = VFC {
    div {
        className = ClassName("col mr-2 px-0")
        nav {
            div {
                className = ClassName("px-3 mt-3 ui vertical menu profile-setting")
                form {
                    settingMenuHeader("Basic Settings")
                    div {
                        className = ClassName("menu")
                        settingsMenuTab(FrontendRoutes.SETTINGS_PROFILE, "Profile settings", faUser)
                        settingsMenuTab(FrontendRoutes.SETTINGS_EMAIL, "Login and email", faEnvelope)
                        settingsMenuTab(FrontendRoutes.SETTINGS_ORGANIZATIONS, "Organizations", faCity)
                    }
                }
                form {
                    div {
                        className = ClassName("separator mt-3 mb-3")
                    }
                    settingMenuHeader("Security Settings")
                    div {
                        className = ClassName("menu")
                        settingsMenuTab(FrontendRoutes.SETTINGS_TOKEN, "Personal access tokens", faKey)
                        settingsMenuTab(FrontendRoutes.SETTINGS_TOKEN, "OAuth accounts", faGithub)
                    }
                }
                form {
                    div {
                        className = ClassName("separator mt-3 mb-3")
                    }
                    settingMenuHeader("Other")
                    div {
                        className = ClassName("menu")
                        settingsMenuTab(FrontendRoutes.SETTINGS_TOKEN, "Personal Statistics", faPlus)
                        settingsMenuTab(
                            FrontendRoutes.SETTINGS_DELETE,
                            "Delete Profile",
                            faWindowClose,
                            "btn-outline-danger"
                        )
                    }
                }
            }
        }
    }
}

private fun ChildrenBuilder.settingMenuHeader(header: String) {
    div {
        className = ClassName("header")
        +header
    }
}

private fun ChildrenBuilder.settingsMenuTab(
    link: FrontendRoutes,
    text: String,
    icon: FontAwesomeIconModule,
    style: String = "btn-outline-dark"
) {
    div {
        className = ClassName("mt-2")
        Link {
            className = ClassName("btn $style btn-block text-left shadow")
            to = "/$link"
            fontAwesomeIcon(icon = icon) {
                it.className = "fas fa-sm fa-fw mr-2"
            }
            +text
        }
    }
}
