/**
 * Configuration beans for security in different profiles
 */

package com.saveourtool.save.sandbox.config

import com.saveourtool.save.domain.Role
import com.saveourtool.save.sandbox.security.ConvertingAuthenticationManager
import com.saveourtool.save.sandbox.security.CustomAuthenticationBasicConverter

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
import org.springframework.security.access.hierarchicalroles.RoleHierarchyUtils
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.AuthenticationWebFilter
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint

import javax.annotation.PostConstruct

@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@Profile("secure")
@Suppress("MISSING_KDOC_TOP_LEVEL", "MISSING_KDOC_CLASS_ELEMENTS", "MISSING_KDOC_ON_FUNCTION")
class WebSecurityConfig(
    private val authenticationManager: ConvertingAuthenticationManager,
    @Autowired private var defaultMethodSecurityExpressionHandler: DefaultMethodSecurityExpressionHandler
) {
    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity
    ): SecurityWebFilterChain = http.run {
        // All `/sandbox/internal/**` requests should be sent only from internal network,
        // they are not proxied from gateway.
        authorizeExchange()
            .pathMatchers("/", "/sandbox/internal/**", "/heartbeat", *publicEndpoints.toTypedArray())
            .permitAll()
    }
        .and()
        .run {
            authorizeExchange()
                .pathMatchers("/sandbox/api/**")
                .authenticated()
        }
        .and()
        .run {
            // FixMe: Properly support CSRF protection https://github.com/saveourtool/save-cloud/issues/34
            csrf().disable()
        }
        .addFilterBefore(
            AuthenticationWebFilter(authenticationManager).apply {
                setServerAuthenticationConverter(CustomAuthenticationBasicConverter())
            },
            SecurityWebFiltersOrder.HTTP_BASIC,
        )
        .exceptionHandling {
            it.authenticationEntryPoint(
                HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)
            )
        }
        .logout()
        .disable()
        .formLogin()
        .disable()
        .build()

    fun roleHierarchy(): RoleHierarchy = mapOf(
        Role.SUPER_ADMIN to listOf(Role.ADMIN, Role.OWNER, Role.VIEWER),
        Role.ADMIN to listOf(Role.OWNER, Role.VIEWER),
        Role.OWNER to listOf(Role.VIEWER),
    )
        .mapKeys { it.key.asSpringSecurityRole() }
        .mapValues { (_, roles) -> roles.map { it.asSpringSecurityRole() } }
        .let(RoleHierarchyUtils::roleHierarchyFromMap)
        .let {
            RoleHierarchyImpl().apply { setHierarchy(it) }
        }

    @PostConstruct
    fun postConstruct() {
        defaultMethodSecurityExpressionHandler.setRoleHierarchy(roleHierarchy())
    }

    companion object {
        internal val publicEndpoints = listOf(
            "/error",
        )
    }
}

@EnableWebFluxSecurity
@Profile("!secure")
@Suppress("MISSING_KDOC_TOP_LEVEL", "MISSING_KDOC_CLASS_ELEMENTS", "MISSING_KDOC_ON_FUNCTION")
class NoopWebSecurityConfig {
    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity
    ): SecurityWebFilterChain = http.authorizeExchange()
        .anyExchange()
        .permitAll()
        .and()
        .csrf()
        .disable()
        .build()
}

/**
 * @return a bean with default [PasswordEncoder], that can be used throughout the application
 */
@Bean
fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
