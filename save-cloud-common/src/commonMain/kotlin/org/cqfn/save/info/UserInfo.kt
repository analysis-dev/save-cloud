package org.cqfn.save.info

import org.cqfn.save.domain.Role

import kotlinx.serialization.Serializable

/**
 * Represents all data related to the User
 *
 * @property name name/login of the user
 * @property source platform user came from
 * @property projects [String] of project name to [Role] of the user
 * @property source where the user identity is coming from, e.g. "github"
 */
@Serializable
data class UserInfo(
    val name: String,
    val source: String? = null,
    val projects: Map<String, Role> = emptyMap()
)
