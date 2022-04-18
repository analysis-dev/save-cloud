/**
 * Utility methods for spring-security
 */

package org.cqfn.save.backend.utils

import org.cqfn.save.entities.User
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails

/**
 * Convert [Authentication] to [User] based on convention in backend.
 * We assume here that all authentications are created by [ConvertingAuthenticationManager],
 * so `principal` is a String, containing identity source.
 *
 * @return [User]
 */
fun Authentication.toUser(): User {
    val (identitySource, name) = (principal as String).split(':')
    return User(
        name = name,
        password = null,
        email = null,
        role = (this as UsernamePasswordAuthenticationToken).authorities.joinToString(separator = ","),
        source = identitySource,
    )
}

/**
 * Extract username from this [Authentication] based on convention in backend.
 * We assume here that most of the authentications are created by [ConvertingAuthenticationManager],
 * so `principal` is a String, containing identity source.
 *
 * @return username
 */
fun Authentication.username(): String = when (principal) {
    // this should be the most common branch, as requests are authenticated by `ConvertingAuthenticationManager`
    is String -> (principal as String).split(':').last()
    is UserDetails -> (principal as UserDetails).username
    else -> error("Authentication instance $this has unsupported type of principal: $principal of type ${principal::class}")
}
