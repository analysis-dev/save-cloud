@file:Suppress("HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE")

package com.saveourtool.save.frontend.components.basic

import com.saveourtool.save.frontend.components.modal.MAX_Z_INDEX
import com.saveourtool.save.frontend.externals.cookie.acceptCookies
import com.saveourtool.save.frontend.externals.cookie.cookie
import com.saveourtool.save.frontend.externals.cookie.declineCookies
import com.saveourtool.save.frontend.externals.cookie.isAccepted
import com.saveourtool.save.frontend.externals.i18next.useTranslation
import com.saveourtool.save.frontend.utils.buttonBuilder
import com.saveourtool.save.validation.FrontendRoutes
import js.core.jso
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.router.useNavigate
import react.useState
import web.cssom.ClassName
import web.cssom.ZIndex

val cookieBanner = FC {
    val (isOpen, setIsOpen) = useState(!cookie.isAccepted())
    val navigate = useNavigate()
    val (t) = useTranslation("cookies")

    if (isOpen) {
        div {
            className = ClassName("fixed-bottom bg-light px-4 d-flex justify-content-between align-items-center")
            style = jso {
                zIndex = (MAX_Z_INDEX - 1).unsafeCast<ZIndex>()
            }
            div {
                className = ClassName("pt-2")
                markdown("We value your privacy".t().trimIndent())
            }
            div {
                buttonBuilder("Decline".t(), "secondary", classes = "mx-2") {
                    cookie.declineCookies()
                    setIsOpen(false)
                }
                buttonBuilder("Read more".t(), "info", classes = "mx-2") {
                    navigate("/${FrontendRoutes.COOKIE}")
                }
                buttonBuilder("Accept".t(), classes = "mx-2") {
                    cookie.acceptCookies()
                    setIsOpen(false)
                }
            }
        }
    }
}
