/**
 * Utilities for visualization of auth providers on welcome and index pages
 */

@file:Suppress("FILE_NAME_MATCH_CLASS")

package com.saveourtool.save.frontend.utils

import com.saveourtool.save.frontend.externals.fontawesome.*
import com.saveourtool.save.info.OauthProviderInfo
import js.core.jso
import react.ChildrenBuilder
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import web.cssom.*

/**
 * Configruation for setting up oauth provider buttons on the frontend
 *
 * @property size font size of oauth logos
 * @property provider oauth provider (Huawei, Gitee, Github, etc.)
 * @property animate animation for these logos
 * @property label text for logo
 */
data class OauthProvidersFeConfig(
    val size: FontSize,
    val provider: OauthProviderInfo,
    val animate: String = "",
    val label: String = ""
)

/**
 * @param oauthProvidersFeConfig configuration for the frontend
 * @return html block with logo of the provider
 */
fun ChildrenBuilder.processRegistrationId(
    oauthProvidersFeConfig: OauthProvidersFeConfig
) = oauthLoginForKnownAwesomeIcons(
    oauthProvidersFeConfig,
    mapKnownUploadedIcons(oauthProvidersFeConfig.provider.registrationId),
    mapKnownFontAwesomeIcons(oauthProvidersFeConfig.provider.registrationId)
)

/**
 * @param oauthProvidersFeConfig all configurations
 * @param staticSvg
 * @param awesomeIcon known icon implemented in awesomeIcon
 */
private fun ChildrenBuilder.oauthLoginForKnownAwesomeIcons(
    oauthProvidersFeConfig: OauthProvidersFeConfig,
    staticSvg: String = "",
    awesomeIcon: dynamic
) {
    div {
        className = ClassName("animated-provider col animate__animated ${oauthProvidersFeConfig.animate}")
        a {
            href = oauthProvidersFeConfig.provider.authorizationLink
            className = ClassName("text-center")
            div {
                className = ClassName("col text-center")
                style = jso {
                    fontSize = oauthProvidersFeConfig.size
                }
                // if there is no fontAwesome for this brand we use simple static SVG
                if (staticSvg.isEmpty()) {
                    fontAwesomeIcon(awesomeIcon)
                } else {
                    img {
                        src = staticSvg
                        style = jso {
                            width = oauthProvidersFeConfig.size.unsafeCast<Width>()
                            height = oauthProvidersFeConfig.size.unsafeCast<Height>()
                            cursor = "pointer".unsafeCast<Cursor>()
                        }
                    }
                }
            }
            div {
                className = ClassName("col text text-center")
                +oauthProvidersFeConfig.label
            }
        }
    }
}

/**
 * @param registrationId oauth provider name (same as in spring security config) from api-gateway
 */
fun mapKnownFontAwesomeIcons(registrationId: String) =
    when(registrationId) {
        "codehub" -> faCopyright
        else -> faSignInAlt
    }

/**
 * Mapping ONLY for those icons that are uploaded to SAVE.
 * Please note that companies like google strictly prohibits incorrect usage of sign-in buttons:
 * https://developers.google.com/identity/branding-guidelines
 * @param registrationId
 */
fun mapKnownUploadedIcons(registrationId: String) =
        when (registrationId) {
            "huawei" -> "img/huawei.svg"
            "gitee" -> "img/gitee.svg"
            "github" -> "img/github.svg"
            "google" -> "img/google.svg"
            else -> ""
        }
