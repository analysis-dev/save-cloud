package com.saveourtool.save.backend.event

import com.saveourtool.save.backend.service.NotificationService
import com.saveourtool.save.domain.Role
import com.saveourtool.save.entities.Notification
import com.saveourtool.save.entities.User
import com.saveourtool.save.evententities.UserEvent
import com.saveourtool.save.info.UserStatus
import com.saveourtool.save.service.UserService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * A user listener for sending notifications.
 */
@Component
class UserListener(
    private val userDetailsService: UserService,
    private val notificationService: NotificationService,
) {
    /**
     * @param userEvent new userEvent
     */
    @EventListener
    fun createUser(userEvent: UserEvent) {
        val newMessage = when (userEvent.user.status) {
            UserStatus.NOT_APPROVED -> messageNewUser(userEvent.user)
            UserStatus.BANNED -> messageBanUser(userEvent.user)
            else -> null
        }

        newMessage?.let { message ->
            val recipients = userDetailsService.findByRole(Role.SUPER_ADMIN.asSpringSecurityRole())
            val notifications = recipients.map { user ->
                Notification(
                    message = message,
                    user = user,
                )
            }
            notificationService.saveAll(notifications)
        }
    }

    companion object {
        /**
         * @param user
         * @return message
         */
        fun messageNewUser(user: User) = """
            New user: ${user.name} is waiting for approve of his account.
        """.trimIndent()

        /**
         * @param user
         * @return message
         */
        fun messageBanUser(user: User) = """
            User: ${user.name} has been banned.
        """.trimIndent()
    }
}
