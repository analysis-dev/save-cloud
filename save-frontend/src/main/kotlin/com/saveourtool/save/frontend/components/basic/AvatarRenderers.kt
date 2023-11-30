/**
 * File containing functions to render avatars
 */

package com.saveourtool.save.frontend.components.basic

import com.saveourtool.save.entities.OrganizationDto
import com.saveourtool.save.entities.OrganizationStatus
import com.saveourtool.save.frontend.utils.AVATAR_PROFILE_PLACEHOLDER
import com.saveourtool.save.info.UserInfo
import com.saveourtool.save.info.UserStatus
import com.saveourtool.save.v1
import com.saveourtool.save.validation.FrontendCosvRoutes
import js.core.jso
import react.CSSProperties
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.router.dom.Link
import web.cssom.ClassName
import web.cssom.rem

/**
 * Placeholder for organization avatar
 */
const val AVATAR_ORGANIZATION_PLACEHOLDER = "/img/company.png"

/**
 * The base URL for uploaded avatars.
 */
const val AVATAR_BASE_URL = "/api/$v1/avatar"

/**
 * links to avatars: `/img` for static resources, `/api` for uploaded.
 *
 * @receiver the local avatar URL (w/o the host name), either absolute or relative.
 * @return the absolute avatar URL (still local to the web server).
 */
fun String.avatarRenderer(): String =
        when {
            /*
             * Static resource, such as `/img/avatar_packs/avatar1.png`
             */
            startsWith("/img") -> this

            /*
             * Uploaded resource (absolute), the URL is already processed/canonicalized.
             */
            startsWith(AVATAR_BASE_URL) -> this

            /*
             * Uploaded resource (absolute), such as `/users/admin?1`.
             */
            startsWith('/') -> AVATAR_BASE_URL + this

            /*
             * Uploaded resource (relative).
             */
            else -> "$AVATAR_BASE_URL/$this"
        }

/**
 * Render organization avatar or placeholder
 *
 * @param organizationDto organization to render avatar
 * @param classes classes applied to [img] html tag
 * @param link link to redirect to if clicked
 * @param styleBuilder [CSSProperties] builder
 */
fun ChildrenBuilder.renderAvatar(
    organizationDto: OrganizationDto,
    classes: String = "",
    link: String? = null,
    styleBuilder: CSSProperties.() -> Unit = {},
) = renderAvatar(
    organizationDto.avatar?.avatarRenderer() ?: AVATAR_ORGANIZATION_PLACEHOLDER,
    classes,
    link ?: "/${organizationDto.name}",
    styleBuilder
)

/**
 * Render user avatar or placeholder
 *
 * @param userInfo user to render avatar
 * @param classes classes applied to [img] html tag
 * @param link link to redirect to if clicked
 * @param styleBuilder [CSSProperties] builder
 * @param isLinkActive
 */
fun ChildrenBuilder.renderAvatar(
    userInfo: UserInfo?,
    classes: String = "",
    link: String? = null,
    isLinkActive: Boolean = true,
    styleBuilder: CSSProperties.() -> Unit,
) {
    val newLink = (link ?: "/${FrontendCosvRoutes.PROFILE}/${userInfo?.name}").takeIf { userInfo?.status != UserStatus.DELETED && isLinkActive }
    return renderAvatar(
        userInfo?.avatar?.avatarRenderer() ?: AVATAR_PROFILE_PLACEHOLDER,
        classes,
        newLink,
        styleBuilder
    )
}

/**
 * @param userInfo
 * @param classes
 * @param link
 * @param styleBuilder
 * @param isHorizontal if the avatar shoud be on the same line with text
 * @param isCentered
 */
@Suppress("TOO_MANY_PARAMETERS", "LongParameterList")
fun ChildrenBuilder.renderUserAvatarWithName(
    userInfo: UserInfo,
    classes: String = "",
    link: String? = null,
    isCentered: Boolean = true,
    isHorizontal: Boolean = false,
    styleBuilder: CSSProperties.() -> Unit = {},
) {
    val renderImg: ChildrenBuilder.() -> Unit = {
        div {
            className = ClassName("col")
            if (isHorizontal) {
                div {
                    className = ClassName("row d-flex align-items-center")
                    renderAvatar(userInfo, classes, link, styleBuilder = styleBuilder)
                    style = jso {
                        fontSize = 1.rem
                    }
                    +" ${userInfo.name}"
                }
            } else {
                val justify = if (isCentered) "justify-content-center" else ""
                div {
                    className = ClassName("row $justify")
                    renderAvatar(userInfo, classes, link, styleBuilder = styleBuilder)
                }
                div {
                    className = ClassName("row $justify mt-2")
                    style = jso {
                        fontSize = 0.8.rem
                    }
                    +" ${userInfo.name}"
                }
            }
        }
    }
    return if (userInfo.status != UserStatus.DELETED) {
        Link {
            to = "/${FrontendCosvRoutes.PROFILE}/${userInfo.name}"
            renderImg()
        }
    } else {
        renderImg()
    }
}

/**
 * @param organizationDto
 * @param classes
 * @param link
 * @param styleBuilder
 */
fun ChildrenBuilder.renderOrganizationWithName(
    organizationDto: OrganizationDto,
    classes: String = "",
    link: String? = null,
    styleBuilder: CSSProperties.() -> Unit = {},
) {
    val renderImg: ChildrenBuilder.() -> Unit = {
        div {
            className = ClassName("col")
            div {
                className = ClassName("row justify-content-center")
                renderAvatar(organizationDto, classes, link, styleBuilder = styleBuilder)
            }
            div {
                className = ClassName("row justify-content-center mt-2")
                style = jso {
                    fontSize = 0.8.rem
                }
                +" ${organizationDto.name}"
            }
        }
    }
    return if (organizationDto.status != OrganizationStatus.DELETED) {
        Link {
            to = link ?: "/${organizationDto.name}"
            renderImg()
        }
    } else {
        renderImg()
    }
}

private fun ChildrenBuilder.renderAvatar(
    avatarLink: String,
    classes: String,
    link: String?,
    styleBuilder: CSSProperties.() -> Unit,
) {
    val renderImg: ChildrenBuilder.() -> Unit = {
        img {
            className = ClassName("avatar avatar-user border color-bg-default rounded-circle $classes")
            src = avatarLink
            style = jso { styleBuilder() }
        }
    }
    link?.let {
        Link {
            to = it
            renderImg()
        }
    } ?: renderImg()
}
