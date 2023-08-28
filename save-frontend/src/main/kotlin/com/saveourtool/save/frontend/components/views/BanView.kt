/**
 * View for Ban
 */

@file:Suppress("FILE_NAME_MATCH_CLASS")

package com.saveourtool.save.frontend.components.views

import com.saveourtool.save.frontend.utils.Style
import com.saveourtool.save.frontend.utils.useBackground
import com.saveourtool.save.info.UserInfo
import com.saveourtool.save.info.UserStatus
import js.core.jso
import react.FC
import react.PropsWithChildren
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.p
import react.router.useNavigate
import web.cssom.ClassName
import web.cssom.rem

private const val BAN = """
    BAN
"""

private const val SUPPORT = """
    You have been banned from our platform. If you believe this was a mistake, please contact our support team at saveourtool@gmail.com.
"""

val banView: FC<BanProps> = FC { props ->
    useBackground(Style.SAVE_LIGHT)

    val navigate = useNavigate()
    if (props.userInfo?.status != UserStatus.BANNED) {
        navigate("/", jso { replace = true })
    }

    div {
        className = ClassName("col text-center")
        style = jso {
            height = 40.rem
        }

        div {
            className = ClassName("col error mx-auto mt-5")
            asDynamic()["data-text"] = BAN
            +BAN
        }

        p {
            className = ClassName("lead text-gray-800 mb-3")
            +SUPPORT
        }
    }
}

/**
 * `Props` retrieved from router
 */
external interface BanProps : PropsWithChildren {
    /**
     * Currently logged-in user or null
     */
    var userInfo: UserInfo?
}
