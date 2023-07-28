/**
 * Main entrypoint for SAVE frontend
 */

package com.saveourtool.save.frontend

import com.saveourtool.save.*
import com.saveourtool.save.domain.Role
import com.saveourtool.save.frontend.components.*
import com.saveourtool.save.frontend.components.basic.scrollToTopButton
import com.saveourtool.save.frontend.components.topbar.topBarComponent
import com.saveourtool.save.frontend.externals.modal.ReactModal
import com.saveourtool.save.frontend.routing.basicRouting
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.info.UserInfo
import com.saveourtool.save.info.UserStatus
import com.saveourtool.save.validation.FrontendRoutes

import react.*
import react.dom.client.createRoot
import react.dom.html.ReactHTML.div
import react.router.*
import react.router.dom.HashRouter
import web.cssom.ClassName
import web.dom.document
import web.html.HTMLElement

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json

/**
 * Main component for the whole App
 */
@JsExport
@OptIn(ExperimentalJsExport::class)
@Suppress("VARIABLE_NAME_INCORRECT_FORMAT", "NULLABLE_PROPERTY_TYPE")
val App: FC<PropsWithChildren> = FC {
    val (userInfo, setUserInfo) = useState<UserInfo?>(null)

    useRequest {
        val userName: String? = get(
            "${window.location.origin}/sec/user",
            jsonHeaders,
            loadingHandler = ::noopLoadingHandler,
            responseHandler = ::noopResponseHandler
        ).run {
            val responseText = text().await()
            if (!ok || responseText == "null") null else responseText
        }

        val globalRole: Role? = get(
            "$apiUrl/users/global-role",
            jsonHeaders,
            loadingHandler = ::noopLoadingHandler,
            responseHandler = ::noopResponseHandler
        ).run {
            val responseText = text().await()
            if (!ok || responseText == "null") null else Json.decodeFromString(responseText)
        }

        val user: UserInfo? = userName?.let {
            get("$apiUrl/users/$userName", jsonHeaders, loadingHandler = ::loadingHandler)
                .decodeFromJsonString<UserInfo>()
        }

        val userInfoNew: UserInfo? = user?.copy(globalRole = globalRole)
            ?: userName?.let { UserInfo(name = userName, globalRole = globalRole) }

        userInfoNew?.let { setUserInfo(userInfoNew) }
    }

    HashRouter {
        requestModalHandler {
            this.userInfo = userInfo

            if (userInfo?.status == UserStatus.CREATED) {
                Navigate {
                    to = "/${FrontendRoutes.REGISTRATION}"
                    replace = false
                }
            }

            div {
                className = ClassName("d-flex flex-column")
                id = "content-wrapper"
                ErrorBoundary::class.react {
                    topBarComponent { this.userInfo = userInfo }
                    div {
                        className = ClassName("container-fluid")
                        id = "common-save-container"
                        basicRouting { this.userInfo = userInfo }
                    }
                    @Suppress("EMPTY_BLOCK_STRUCTURE_ERROR")
                    footer { }
                }
            }
        }
        scrollToTopButton()
    }
}

/**
 * The function creates routers with the given [basePath] and ending of string with all the elements given Enum<T>
 *
 * @param basePath
 * @param routeElement
 */
inline fun <reified T : Enum<T>> ChildrenBuilder.createRoutersWithPathAndEachListItem(
    basePath: String,
    routeElement: FC<Props>
) {
    enumValues<T>().map { it.name.lowercase() }.forEach { item ->
        PathRoute {
            path = "$basePath/$item"
            element = routeElement.create()
        }
    }
    PathRoute {
        path = basePath
        element = routeElement.create()
    }
}

fun main() {
    /* Workaround for issue: https://youtrack.jetbrains.com/issue/KT-31888 */
    @Suppress("UnsafeCastFromDynamic")
    if (window.asDynamic().__karma__) {
        return
    }

    kotlinext.js.require("../scss/save-frontend.scss")  // this is needed for webpack to include resource
    kotlinext.js.require("bootstrap")  // this is needed for webpack to include bootstrap
    ReactModal.setAppElement(document.getElementById("wrapper") as HTMLElement)  // required for accessibility in react-modal

    val mainDiv = document.getElementById("wrapper") as HTMLElement
    createRoot(mainDiv).render(App.create())
}
