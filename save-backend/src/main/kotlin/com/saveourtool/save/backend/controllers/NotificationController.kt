package com.saveourtool.save.backend.controllers

import com.saveourtool.save.authservice.utils.username
import com.saveourtool.save.backend.service.NotificationService
import com.saveourtool.save.configs.ApiSwaggerSupport
import com.saveourtool.save.entities.NotificationDto
import com.saveourtool.save.utils.StringResponse
import com.saveourtool.save.utils.blockingToFlux
import com.saveourtool.save.utils.blockingToMono
import com.saveourtool.save.v1
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.tags.Tags
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Controller for working with notifications.
 */
@ApiSwaggerSupport
@Tags(
    Tag(name = "notifications"),
)
@RestController
@RequestMapping(path = ["/api/$v1/notifications"])
class NotificationController(
    private val notificationService: NotificationService,
) {
    @GetMapping("/get-all-by-user")
    @Operation(
        method = "GET",
        summary = "Get all notifications by user name.",
        description = "Get user notifications.",
    )
    @ApiResponse(responseCode = "200", description = "Successfully fetched all notifications by user name")
    fun getAllNotifications(
        authentication: Authentication?,
    ): Flux<NotificationDto> = blockingToFlux {
        authentication?.let {
            notificationService.getAllByUserName(authentication.username()).map { it.toDto() }
        }.orEmpty()
    }

    @DeleteMapping("/delete-by-id")
    @Operation(
        method = "DELETE",
        summary = "Delete notification by id.",
        description = "Delete notification by id.",
    )
    @PreAuthorize("permitAll()")
    @ApiResponse(responseCode = "200", description = "Successfully deleted notification by id")
    fun deleteById(
        @RequestParam id: Long,
        authentication: Authentication?,
    ): Mono<StringResponse> = blockingToMono {
        val notification = notificationService.getById(id)
        notification?.let {
            if (authentication?.username() == notification.user.name) {
                notificationService.deleteById(id)
                ResponseEntity.ok("Successfully deleted requested notification.")
            } else {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "it is not your notification")
            }
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "notification is not found")
    }
}
