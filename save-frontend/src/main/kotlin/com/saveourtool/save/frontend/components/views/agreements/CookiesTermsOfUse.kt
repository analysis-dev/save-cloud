@file:Suppress("HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE")

package com.saveourtool.save.frontend.components.views.agreements

import com.saveourtool.save.frontend.components.basic.markdown
import com.saveourtool.save.frontend.externals.cookie.acceptCookies
import com.saveourtool.save.frontend.externals.cookie.cookie
import com.saveourtool.save.frontend.externals.cookie.declineCookies
import com.saveourtool.save.frontend.externals.i18next.useTranslation
import com.saveourtool.save.frontend.utils.Style
import com.saveourtool.save.frontend.utils.buttonBuilder
import com.saveourtool.save.frontend.utils.useBackground
import react.FC
import react.VFC
import react.dom.html.ReactHTML.div
import web.cssom.ClassName

val cookieTermsOfUse: VFC = FC {
    useBackground(Style.INDEX)
    val (t) = useTranslation("cookies")
    div {
        className = ClassName("col-6 bg-light mx-auto my-5 p-3 rounded-lg")

        div {
            markdown("What are cookies".t().trimIndent())

            markdown("How do we use cookies".t().trimIndent())

            markdown("What exactly do we store".t().trimIndent())
        }
        div {
            className = ClassName("mt-3 mx-auto")
            buttonBuilder("Accept".t(), classes = "mx-1") {
                cookie.acceptCookies()
            }

            buttonBuilder("Decline".t(), classes = "mx-1") {
                cookie.declineCookies()
            }
        }
    }
}
