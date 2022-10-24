package com.saveourtool.save.authservice.service

import com.saveourtool.save.authservice.repository.AuthenticationUserRepository
import com.saveourtool.save.authservice.utils.getIdentitySourceAwareUserDetails
import org.springframework.context.annotation.Primary
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

/**
 * A service that provides `UserDetails`
 */
@Service
@Primary
class AuthenticationUserDetailsService(
    private val authenticationUserRepository: AuthenticationUserRepository,
) : ReactiveUserDetailsService {
    /**
     * @param username
     * @return IdentitySourceAwareUserDetails retrieved from UserDetails
     */
    override fun findByUsername(username: String): Mono<UserDetails> = {
        authenticationUserRepository.findByName(username)
    }.toMono().getIdentitySourceAwareUserDetails(username)
}
