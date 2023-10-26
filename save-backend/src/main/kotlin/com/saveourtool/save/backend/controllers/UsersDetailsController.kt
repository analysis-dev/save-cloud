package com.saveourtool.save.backend.controllers

import com.saveourtool.save.authservice.utils.userId
import com.saveourtool.save.backend.repository.UserRepository
import com.saveourtool.save.backend.service.UserDetailsService
import com.saveourtool.save.configs.RequiresAuthorizationSourceHeader
import com.saveourtool.save.domain.UserSaveStatus
import com.saveourtool.save.info.UserInfo
import com.saveourtool.save.info.UserStatus
import com.saveourtool.save.utils.*
import com.saveourtool.save.v1
import com.saveourtool.save.validation.isValidLengthName
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

/**
 * Controller that handles operation with users
 */
@RestController
@RequestMapping(path = ["/api/$v1/users"])
class UsersDetailsController(
    private val userRepository: UserRepository,
    private val userDetailsService: UserDetailsService,
) {
    /**
     * @param userName username
     * @return [UserInfo] info about user's
     */
    @GetMapping("/{userName}")
    @PreAuthorize("permitAll()")
    fun findByName(
        @PathVariable userName: String,
    ): Mono<UserInfo> = blockingToMono { userRepository.findByName(userName) }
        .map { it.toUserInfo() }
        .orNotFound()

    @GetMapping("/by-prefix")
    @PreAuthorize("permitAll()")
    @Operation(
        method = "GET",
        summary = "Get users by prefix.",
        description = "Get list of users by prefix with ids not in.",
    )
    @Parameters(
        Parameter(name = "prefix", `in` = ParameterIn.QUERY, description = "username prefix", required = true),
        Parameter(
            name = "pageSize",
            `in` = ParameterIn.QUERY,
            description = "amount of users that should be returned, default: 5",
            required = false
        ),
    )
    @ApiResponse(responseCode = "200", description = "Successfully fetched users.")
    @RequiresAuthorizationSourceHeader
    fun findByPrefix(
        @RequestParam prefix: String,
        @RequestParam(required = false, defaultValue = "5") pageSize: Int,
        @RequestParam(required = false, defaultValue = "") namesToSkip: String,
    ): Flux<UserInfo> = namesToSkip.toMono()
        .map { stringNames -> stringNames.split(DATABASE_DELIMITER) }
        .map { stringNameList -> stringNameList.filter { it.isNotBlank() }.toSet() }
        .flatMapMany { nameList ->
            // `userRepository.findByNameStartingWithAndIdNotIn` with empty `nameList` results with empty list for some reason
            blockingToFlux {
                if (nameList.isNotEmpty()) {
                    userRepository.findByNameStartingWithAndNameNotIn(prefix, nameList, Pageable.ofSize(pageSize))
                } else {
                    userRepository.findByNameStartingWith(prefix, Pageable.ofSize(pageSize))
                }
            }
        }
        .map { it.toUserInfo() }

    /**
     * @return list of [UserInfo] info about user's
     */
    @GetMapping("/all")
    @PreAuthorize("permitAll()")
    fun findAll(): Flux<UserInfo> = blockingToFlux {
        userRepository.findAll().map { it.toUserInfo() }
    }

    /**
     * @param newUserInfo
     * @param authentication
     */
    @PostMapping("/save")
    @PreAuthorize("isAuthenticated()")
    @Suppress("MagicNumber")
    fun saveUser(@RequestBody newUserInfo: UserInfo, authentication: Authentication): Mono<StringResponse> = Mono.just(newUserInfo)
        .filter { newUserInfo.name.isValidLengthName() }
        .switchIfEmptyToResponseException(HttpStatus.CONFLICT) {
            UserSaveStatus.INVALID_NAME.message
        }
        .blockingMap {
            userRepository.findByIdOrNull(authentication.userId())
        }
        .switchIfEmptyToNotFound {
            "User with id ${authentication.userId()} not found in database"
        }
        .blockingMap { user ->
            val oldName = user.name.takeUnless { it == newUserInfo.name }
            val oldStatus = user.status
            val newStatus = when (oldStatus) {
                UserStatus.CREATED -> UserStatus.NOT_APPROVED
                UserStatus.ACTIVE -> UserStatus.ACTIVE
                else -> null
            }
            newStatus?.let {
                userDetailsService.saveUser(
                    user.apply {
                        name = newUserInfo.name
                        email = newUserInfo.email
                        company = newUserInfo.company
                        location = newUserInfo.location
                        gitHub = newUserInfo.gitHub
                        linkedin = newUserInfo.linkedin
                        twitter = newUserInfo.twitter
                        status = newStatus
                        website = newUserInfo.website
                        realName = newUserInfo.realName
                        freeText = newUserInfo.freeText
                    },
                    oldName,
                    oldStatus,
                )
            } ?: UserSaveStatus.FORBIDDEN
        }
        .filter { status -> status == UserSaveStatus.UPDATE }
        .switchIfEmptyToResponseException(HttpStatus.CONFLICT) {
            UserSaveStatus.CONFLICT.message
        }
        .map { status ->
            ResponseEntity.ok(status.message)
        }

    /**
     * @param userName
     * @param token
     * @param authentication an [Authentication] representing an authenticated request
     * @return response
     */
    @PostMapping("{userName}/save/token")
    @PreAuthorize("isAuthenticated()")
    fun saveUserToken(
        @PathVariable userName: String,
        @RequestBody token: String,
        authentication: Authentication,
    ): Mono<StringResponse> = blockingToMono { userRepository.findByName(userName) }
        .requireOrSwitchToForbidden({ id == authentication.userId() })
        .blockingMap { user ->
            userRepository.save(user.apply {
                password = "{bcrypt}${BCryptPasswordEncoder().encode(token)}"
            })
        }
        .map {
            ResponseEntity.ok("User token saved successfully")
        }

    /**
     * @param authentication
     * @return [UserInfo] of authenticated user
     */
    @GetMapping("/user-info")
    fun getSelfUserInfo(authentication: Authentication?): Mono<UserInfo> = blockingToMono {
        authentication?.userId()?.let { userDetailsService.findById(it) }?.toUserInfo()
    }

    /**
     * @param userName
     * @param authentication
     */
    @GetMapping("/delete")
    @PreAuthorize("isAuthenticated()")
    fun deleteUser(
        @RequestParam userName: String,
        authentication: Authentication,
    ): Mono<StringResponse> = blockingToMono {
        userDetailsService.deleteUser(userName, authentication)
    }
        .map { status ->
            when (status) {
                UserSaveStatus.DELETED ->
                    ResponseEntity.ok(status.message)
                else ->
                    ResponseEntity.status(HttpStatus.CONFLICT).body(status.message)
            }
        }

    /**
     * @param userName
     */
    @GetMapping("/ban")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    fun banUser(
        @RequestParam userName: String,
    ): Mono<StringResponse> = blockingToMono {
        userDetailsService.banUser(userName)
    }
        .filter { status ->
            status == UserSaveStatus.BANNED
        }
        .switchIfEmptyToResponseException(HttpStatus.CONFLICT) {
            UserSaveStatus.CONFLICT.message
        }
        .map { status ->
            ResponseEntity.ok(status.message)
        }

    /**
     * @return list of [UserInfo] info about users
     */
    @GetMapping("/new-users")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    fun findNewUsers(): Flux<UserInfo> = blockingToFlux { userRepository.findByStatus(UserStatus.NOT_APPROVED).map { it.toUserInfo() } }

    /**
     * @return count new users
     */
    @GetMapping("/new-users-count")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    fun findCountNewUsers(): Mono<Int> = blockingToMono { userRepository.countByStatus(UserStatus.NOT_APPROVED) }

    /**
     * @param userName
     */
    @GetMapping("/approve")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    fun approveUser(
        @RequestParam userName: String,
    ): Mono<StringResponse> = blockingToMono {
        userDetailsService.approveUser(userName)
    }
        .filter { status ->
            status == UserSaveStatus.APPROVED
        }
        .switchIfEmptyToResponseException(HttpStatus.CONFLICT) {
            UserSaveStatus.CONFLICT.message
        }
        .map { status ->
            ResponseEntity.ok(status.message)
        }
}
