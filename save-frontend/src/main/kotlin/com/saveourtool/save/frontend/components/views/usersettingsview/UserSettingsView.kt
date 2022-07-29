/**
 * A view with settings user
 */

package com.saveourtool.save.frontend.components.views.usersettingsview

import com.saveourtool.save.domain.ImageInfo
import com.saveourtool.save.frontend.components.basic.InputTypes
import com.saveourtool.save.frontend.components.views.AbstractView
import com.saveourtool.save.frontend.externals.fontawesome.*
import com.saveourtool.save.frontend.http.getUser
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.info.OrganizationInfo
import com.saveourtool.save.info.UserInfo
import com.saveourtool.save.utils.AvatarType
import com.saveourtool.save.v1
import com.saveourtool.save.validation.FrontendRoutes

import csstype.*
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.fetch.Headers
import org.w3c.xhr.FormData
import react.*
import react.dom.aria.ariaLabel
import react.dom.events.ChangeEvent
import react.dom.html.InputType
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.nav

import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * `Props` retrieved from router
 */
@Suppress("MISSING_KDOC_CLASS_ELEMENTS")
external interface UserSettingsProps : PropsWithChildren {
    /**
     * Currently logged in user or null
     */
    var userName: String?
}

/**
 * [State] of project view component
 */
@Suppress("MISSING_KDOC_TOP_LEVEL", "TYPE_ALIAS")
external interface UserSettingsViewState : State {
    /**
     * Flag to handle uploading a file
     */
    var isUploading: Boolean

    /**
     * Image to owner avatar
     */
    var image: ImageInfo?

    /**
     * Currently logged in user or null
     */
    var userInfo: UserInfo?

    /**
     * Token for user
     */
    var token: String?

    /**
     * Organizations connected to user
     */
    var selfOrganizationInfos: List<OrganizationInfo>
}

@Suppress("MISSING_KDOC_TOP_LEVEL")
abstract class UserSettingsView : AbstractView<UserSettingsProps, UserSettingsViewState>(false) {
    private val fieldsMap: MutableMap<InputTypes, String> = mutableMapOf()
    private val renderMenu = renderMenu()

    init {
        state.isUploading = false
        state.selfOrganizationInfos = emptyList()
    }

    /**
     * @param fieldName
     * @param target
     */
    fun changeFields(
        fieldName: InputTypes,
        target: ChangeEvent<HTMLInputElement>,
    ) {
        val tg = target.target
        val value = tg.value
        fieldsMap[fieldName] = value
    }

    override fun componentDidMount() {
        super.componentDidMount()
        scope.launch {
            val avatar = getAvatar()
            val user = props.userName
                ?.let { getUser(it) }
            val organizationInfos = getOrganizationInfos()
            setState {
                image = avatar
                userInfo = user
                userInfo?.let { updateFieldsMap(it) }
                selfOrganizationInfos = organizationInfos
            }
        }
    }

    private fun updateFieldsMap(userInfo: UserInfo) {
        userInfo.email?.let { fieldsMap[InputTypes.USER_EMAIL] = it }
        userInfo.company?.let { fieldsMap[InputTypes.COMPANY] = it }
        userInfo.location?.let { fieldsMap[InputTypes.LOCATION] = it }
        userInfo.linkedin?.let { fieldsMap[InputTypes.LINKEDIN] = it }
        userInfo.gitHub?.let { fieldsMap[InputTypes.GIT_HUB] = it }
        userInfo.twitter?.let { fieldsMap[InputTypes.TWITTER] = it }
    }

    /**
     * @return element
     */
    abstract fun renderMenu(): FC<UserSettingsProps>

    @Suppress("TOO_LONG_FUNCTION", "LongMethod", "MAGIC_NUMBER")
    override fun ChildrenBuilder.render() {
        div {
            className = ClassName("row justify-content-center")
            // ===================== LEFT COLUMN =======================================================================
            div {
                className = ClassName("col-2 mr-3")
                div {
                    className = ClassName("card card-body mt-0 pt-0 pr-0 pl-0 border-secondary")
                    div {
                        className = ClassName("col mr-2 pr-0 pl-0")
                        style = kotlinx.js.jso {
                            background = "#e1e9ed".unsafeCast<Background>()
                        }
                        div {
                            className = ClassName("mb-0 font-weight-bold text-gray-800")
                            form {
                                div {
                                    className = ClassName("row g-3 ml-3 mr-3 pb-2 pt-2 border-bottom")
                                    div {
                                        className = ClassName("col-md-4 pl-0 pr-0")
                                        label {
                                            input {
                                                type = InputType.file
                                                hidden = true
                                                onChange = {
                                                    postImageUpload(it.target)
                                                }
                                            }
                                            ariaLabel = "Change avatar owner"
                                            img {
                                                className = ClassName("avatar avatar-user width-full border color-bg-default rounded-circle")
                                                src = state.image?.path?.let {
                                                    "/api/$v1/avatar$it"
                                                }
                                                    ?: run {
                                                        "img/user.svg"
                                                    }
                                                height = 60.0
                                                width = 60.0
                                            }
                                        }
                                    }
                                    div {
                                        className = ClassName("col-md-6 pl-0")
                                        style = kotlinx.js.jso {
                                            display = Display.flex
                                            alignItems = AlignItems.center
                                        }
                                        h1 {
                                            className = ClassName("h5 mb-0 text-gray-800")
                                            +"${props.userName}"
                                        }
                                    }
                                }
                            }
                        }
                    }

                    div {
                        className = ClassName("col mr-2 pr-0 pl-0")
                        nav {
                            div {
                                className = ClassName("pl-3 ui vertical menu profile-setting")
                                form {
                                    div {
                                        className = ClassName("item mt-2")
                                        div {
                                            className = ClassName("header")
                                            +"Basic Setting"
                                        }
                                        div {
                                            className = ClassName("menu")
                                            div {
                                                className = ClassName("mt-2")
                                                a {
                                                    className = ClassName("item")
                                                    href = "#/${props.userName}/${FrontendRoutes.SETTINGS_PROFILE.path}"
                                                    fontAwesomeIcon(icon = faUser) {
                                                        it.className = "fas fa-sm fa-fw mr-2 text-gray-600"
                                                    }
                                                    +"Profile"
                                                }
                                            }
                                            div {
                                                className = ClassName("mt-2")
                                                a {
                                                    className = ClassName("item")
                                                    href = "#/${props.userName}/${FrontendRoutes.SETTINGS_EMAIL.path}"
                                                    fontAwesomeIcon(icon = faEnvelope) {
                                                        it.className = "fas fa-sm fa-fw mr-2 text-gray-600"
                                                    }
                                                    +"Email management"
                                                }
                                            }
                                            div {
                                                className = ClassName("mt-2")
                                                a {
                                                    className = ClassName("item")
                                                    href = "#/${props.userName}/${FrontendRoutes.SETTINGS_ORGANIZATIONS.path}"
                                                    fontAwesomeIcon(icon = faCity) {
                                                        it.className = "fas fa-sm fa-fw mr-2 text-gray-600"
                                                    }
                                                    +"Organizations"
                                                }
                                            }
                                        }
                                    }
                                }
                                form {
                                    div {
                                        className = ClassName("item mt-2")
                                        div {
                                            className = ClassName("header")
                                            +"Security Setting"
                                        }
                                        div {
                                            className = ClassName("menu")
                                            div {
                                                className = ClassName("mt-2")
                                                a {
                                                    className = ClassName("item")
                                                    href = "#/${props.userName}/${FrontendRoutes.SETTINGS_TOKEN.path}"
                                                    fontAwesomeIcon(icon = faKey) {
                                                        it.className = "fas fa-sm fa-fw mr-2 text-gray-600"
                                                    }
                                                    +"Personal access tokens"
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ===================== RIGHT COLUMN =======================================================================
            div {
                className = ClassName("col-6")
                renderMenu {
                    userName = props.userName
                }
            }
        }
    }

    @Suppress("MISSING_KDOC_CLASS_ELEMENTS", "MISSING_KDOC_ON_FUNCTION")
    fun updateUser() {
        val newUserInfo = UserInfo(
            name = state.userInfo!!.name,
            source = state.userInfo!!.source,
            projects = state.userInfo!!.projects,
            email = fieldsMap[InputTypes.USER_EMAIL]?.trim(),
            company = fieldsMap[InputTypes.COMPANY]?.trim(),
            location = fieldsMap[InputTypes.LOCATION]?.trim(),
            linkedin = fieldsMap[InputTypes.LINKEDIN]?.trim(),
            gitHub = fieldsMap[InputTypes.GIT_HUB]?.trim(),
            twitter = fieldsMap[InputTypes.TWITTER]?.trim(),
            avatar = state.userInfo!!.avatar,
        )

        val headers = Headers().also {
            it.set("Accept", "application/json")
            it.set("Content-Type", "application/json")
        }
        scope.launch {
            post(
                "$apiUrl/users/save",
                headers,
                Json.encodeToString(newUserInfo),
                loadingHandler = ::classLoadingHandler,
            )
        }
    }

    private fun postImageUpload(element: HTMLInputElement) =
            scope.launch {
                setState {
                    isUploading = true
                }
                element.files!!.asList().single().let { file ->
                    val response: ImageInfo? = post(
                        "$apiUrl/image/upload?owner=${props.userName}&type=${AvatarType.USER}",
                        Headers(),
                        FormData().apply {
                            append("file", file)
                        },
                        loadingHandler = ::classLoadingHandler,
                    )
                        .decodeFromJsonString()
                    setState {
                        image = response
                    }
                }
                setState {
                    isUploading = false
                }
            }

    private suspend fun getAvatar() = get(
        "$apiUrl/users/${props.userName}/avatar",
        Headers(),
        loadingHandler = ::noopLoadingHandler,
    )
        .unsafeMap {
            it.decodeFromJsonString<ImageInfo>()
        }

    @Suppress("TYPE_ALIAS")
    private suspend fun getOrganizationInfos() = get(
        "$apiUrl/organizations/by-user/not-deleted",
        Headers(),
        loadingHandler = ::classLoadingHandler,
    )
        .unsafeMap { it.decodeFromJsonString<List<OrganizationInfo>>() }
}
