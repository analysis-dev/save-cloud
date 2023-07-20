package com.saveourtool.save.gateway.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.saveourtool.save.authservice.utils.IdentitySourceAwareUserDetails
import com.saveourtool.save.gateway.config.ConfigurationProperties
import com.saveourtool.save.utils.IdentitySourceAwareUserDetailsMixin
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.jackson2.CoreJackson2Module
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.toEntity
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

/**
 * A service to backend to lookup users in DB
 */
@Service
class BackendService(
    configurationProperties: ConfigurationProperties,
) {
    private val objectMapper = ObjectMapper()
        .findAndRegisterModules()
        .registerModule(CoreJackson2Module())
        .addMixIn(IdentitySourceAwareUserDetails::class.java, IdentitySourceAwareUserDetailsMixin::class.java)
    private val webClient = WebClient.create(configurationProperties.backend.url)
        .mutate()
        .codecs {
            it.defaultCodecs().jackson2JsonEncoder(
                Jackson2JsonEncoder(objectMapper)
            )
        }
        .build()

    /**
     * @param username
     * @return [UserDetails] found in DB by received name
     */
    fun findByName(username: String): Mono<UserDetails> = webClient.get()
        .uri("/internal/users/find-by-name/$username")
        .retrieve()
        .onStatus({ it.is4xxClientError }) {
            Mono.error(ResponseStatusException(it.statusCode()))
        }
        .toEntity<String>()
        .map {
            objectMapper.readValue(it.body, UserDetails::class.java)
        }

    /**
     * @param source
     * @param nameInSource
     * @return [UserDetails] found in DB by source and name in this source
     */
    fun findByOriginalLogin(source: String, nameInSource: String): Mono<UserDetails> = webClient.get()
        .uri("/internal/users/find-by-original-login/$source/$nameInSource")
        .retrieve()
        .onStatus({ it.is4xxClientError }) {
            Mono.error(ResponseStatusException(it.statusCode()))
        }
        .toEntity<String>()
        .map {
            objectMapper.readValue(it.body, UserDetails::class.java)
        }
}