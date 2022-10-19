package com.saveourtool.save.backend.controller

import com.saveourtool.save.backend.configs.WebSecurityConfig
import com.saveourtool.save.backend.controllers.LnkUserOrganizationController
import com.saveourtool.save.backend.repository.OriginalLoginRepository
import com.saveourtool.save.backend.repository.UserRepository
import com.saveourtool.save.backend.security.OrganizationPermissionEvaluator
import com.saveourtool.save.backend.service.*
import com.saveourtool.save.utils.AuthenticationDetails
import com.saveourtool.save.backend.utils.ConvertingAuthenticationManager
import com.saveourtool.save.backend.utils.mutateMockedUser
import com.saveourtool.save.domain.Role
import com.saveourtool.save.entities.*
import com.saveourtool.save.permission.Permission
import com.saveourtool.save.permission.SetRoleRequest
import com.saveourtool.save.v1
import org.junit.jupiter.api.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

@WebFluxTest(controllers = [LnkUserOrganizationController::class])
@Import(
    WebSecurityConfig::class,
    OrganizationService::class,
    ConvertingAuthenticationManager::class,
    UserDetailsService::class,
)
@AutoConfigureWebTestClient
class LnkUserOrganizationControllerTest {
    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var lnkUserOrganizationService: LnkUserOrganizationService

    @MockBean
    private lateinit var organizationPermissionEvaluator: OrganizationPermissionEvaluator

    @MockBean
    private lateinit var userRepository: UserRepository

    @MockBean
    private lateinit var organizationService: OrganizationService

    @MockBean
    private lateinit var originalLoginRepository: OriginalLoginRepository

    @Test
    @WithMockUser
    fun `should allow reading roles if user is viewer or higher`() {
        mutateMockedUser {
            details = AuthenticationDetails(id = 99)
        }
        given(
            user = { User(name = it.arguments[0] as String, null, null, "") },
            organization = Organization.stub(id = 99),
            organizationRole = Role.VIEWER,
        )
        given(lnkUserOrganizationService.getRole(any(), any())).willReturn(Role.ADMIN)

        webTestClient.get()
            .uri("/api/$v1/organizations/Huawei/users/roles?userName=admin")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody<Role>()
            .isEqualTo(Role.ADMIN)
        verify(lnkUserOrganizationService, times(1)).getRole(any(), any())
    }

    @Test
    @WithMockUser
    fun `should forbid reading of roles if user doesn't have permission`() {
        mutateMockedUser {
            details = AuthenticationDetails(id = 99)
        }
        given(
            user = { User(name = it.arguments[0] as String, null, null, "") },
            organization = Organization.stub(id = 99),
            organizationRole = Role.NONE,
        )

        webTestClient.get()
            .uri("/api/$v1/organizations/Huawei/users/roles?userName=admin")
            .exchange()
            .expectStatus()
            .isForbidden
        verify(lnkUserOrganizationService, times(0)).getRole(any(), any())
    }

    @Test
    @WithMockUser
    fun `should allow changing roles for organization owners`() {
        mutateMockedUser {
            details = AuthenticationDetails(id = 99)
        }
        given(userRepository.findByName(any())).willReturn(
            User("user", null, null, "").apply { id = 99 }
        )
        given(
            user = { User(name = it.arguments[0] as String, null, null, "") },
            organization = Organization.stub(id = 99),
            organizationRole = Role.OWNER,
        )
        given(organizationPermissionEvaluator.canChangeRoles(any(), any(), any(), any())).willReturn(true)
        webTestClient.post()
            .uri("/api/$v1/organizations/Huawei/users/roles")
            .bodyValue(SetRoleRequest("admin", Role.ADMIN))
            .exchange()
            .expectStatus()
            .isOk
        verify(lnkUserOrganizationService, times(1)).setRole(any(), any(), any())
    }

    @Test
    @WithMockUser
    fun `should forbid changing roles unless user is an organization owner`() {
        mutateMockedUser {
            details = AuthenticationDetails(id = 99)
        }
        given(
            user = { User(name = it.arguments[0] as String, null, null, "") },
            organization = Organization.stub(id = 99),
            organizationRole = Role.ADMIN,
        )

        webTestClient.post()
            .uri("/api/$v1/organizations/Huawei/users/roles")
            .bodyValue(SetRoleRequest("admin", Role.ADMIN))
            .exchange()
            .expectStatus()
            .isForbidden
        verify(lnkUserOrganizationService, times(0)).setRole(any(), any(), any())
    }

    @Test
    @WithMockUser
    fun `should get 403 when deleting users from organization without permission`() {
        mutateMockedUser {
            details = AuthenticationDetails(id = 99)
        }
        given(
            user = { User(name = it.arguments[0] as String, null, null, "") },
            organization = Organization.stub(id = 99),
            organizationRole = Role.VIEWER,
        )
        webTestClient.delete()
            .uri("/api/$v1/organizations/Huawei/users/roles/user")
            .exchange()
            .expectStatus()
            .isForbidden
        verify(lnkUserOrganizationService, times(0)).removeRole(any(), any())
    }

    @Test
    @WithMockUser
    fun `should permit deleting users from organization if user is admin or higher`() {
        mutateMockedUser {
            details = AuthenticationDetails(id = 99)
        }
        given(
            user = { User(name = it.arguments[0] as String, null, null, "") },
            organization = Organization.stub(99),
            organizationRole = Role.ADMIN,
        )

        given(organizationPermissionEvaluator.canChangeRoles(any(), any(), any(), any())).willReturn(true)
        webTestClient.delete()
            .uri("/api/$v1/organizations/Huawei/users/roles/user")
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    @WithMockUser
    fun `should forbid removing people from organization if user has less permissions than admin`() {
        mutateMockedUser {
            details = AuthenticationDetails(id = 99)
        }
        given(
            user = { User(name = it.arguments[0] as String, null, null, "") },
            organization = Organization.stub(id = 99),
            organizationRole = Role.VIEWER,
        )
        given(organizationPermissionEvaluator.canChangeRoles(any(), any(), any(), any())).willReturn(false)
        webTestClient.delete()
            .uri("/api/$v1/organizations/Huawei/users/roles/user")
            .exchange()
            .expectStatus()
            .isForbidden
        verify(lnkUserOrganizationService, times(0)).removeRole(any(), any())
    }

    @Suppress("LAMBDA_IS_NOT_LAST_PARAMETER")
    private fun given(
        user: (InvocationOnMock) -> User,
        organization: Organization,
        organizationRole: Role,
    ) {
        given(organizationService.findByName(any())).willReturn(organization)
        given(organizationPermissionEvaluator.hasPermission(any(), any(), any())).willAnswer {
            when (it.arguments[2] as Permission?) {
                null -> false
                Permission.READ -> organizationRole.priority >= Role.VIEWER.priority
                Permission.WRITE -> organizationRole.priority >= Role.ADMIN.priority
                Permission.DELETE -> organizationRole.priority >= Role.OWNER.priority
            }
        }
        given(lnkUserOrganizationService.getUserByName(any())).willAnswer { invocationOnMock ->
            user(invocationOnMock)
        }
    }
}
