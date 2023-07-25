/**
 * Configuration beans for security in different profiles
 */

package com.saveourtool.save.sandbox.config

import com.saveourtool.save.authservice.config.NoopWebSecurityConfig
import com.saveourtool.save.authservice.config.WebSecurityConfig
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity

@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@Profile("secure")
@Import(
    WebSecurityConfig::class,
    ConvertingAuthenticationManager::class,
    AuthenticationUserDetailsService::class,
    AuthenticationUserRepository::class,
)
@Suppress("MISSING_KDOC_TOP_LEVEL", "MISSING_KDOC_CLASS_ELEMENTS", "MISSING_KDOC_ON_FUNCTION")
class SandboxWebSecurityConfig

@EnableWebFluxSecurity
@Profile("!secure")
@Import(NoopWebSecurityConfig::class)
@Suppress("MISSING_KDOC_TOP_LEVEL", "MISSING_KDOC_CLASS_ELEMENTS", "MISSING_KDOC_ON_FUNCTION")
class SandboxNoopWebSecurityConfig
