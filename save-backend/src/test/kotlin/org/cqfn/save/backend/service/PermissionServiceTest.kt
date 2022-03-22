package org.cqfn.save.backend.service

import org.cqfn.save.backend.repository.UserRepository
import org.cqfn.save.domain.Role
import org.cqfn.save.entities.Project
import org.cqfn.save.entities.User
import org.cqfn.save.permission.SetRoleRequest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.given
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import java.util.Optional

@ExtendWith(SpringExtension::class)
@Import(PermissionService::class)
class PermissionServiceTest {
    @Autowired private lateinit var permissionService: PermissionService
    @MockBean private lateinit var userRepository: UserRepository
    @MockBean private lateinit var projectService: ProjectService
    @MockBean private lateinit var lnkUserProjectService: LnkUserProjectService

    @Test
    fun `should return a role`() {
        given(userRepository.findByName(any())).willAnswer { invocationOnMock ->
            User(invocationOnMock.arguments[0] as String, null, null, "basic")
                .apply { id = 99 }
                .let { Optional.of(it) }
        }
        given(projectService.findByNameAndOrganizationName(any(), any())).willAnswer {
            Project.stub(id = 99)
        }
        given(lnkUserProjectService.findRoleByUserIdAndProject(eq(99), any())).willReturn(Role.ADMIN)

        val role = permissionService.getRole(userName = "admin", projectName = "Example", organizationName = "Example Org")
            .blockOptional()

        Assertions.assertEquals(Role.ADMIN, role.get())
    }

    @Test
    fun `should return empty for non-existent projects or users`() {
        given(userRepository.findByName(any())).willReturn(Optional.empty<User>())
        given(projectService.findByNameAndOrganizationName(any(), any())).willReturn(null)

        val role = permissionService.getRole(userName = "admin", projectName = "Example", organizationName = "Example Org")
            .blockOptional()

        Assertions.assertTrue(role.isEmpty)
        verify(lnkUserProjectService, times(0)).findRoleByUserIdAndProject(any(), any())
    }

    @Test
    fun `should add a role`() {
        given(userRepository.findByName(any())).willAnswer { invocationOnMock ->
            User(invocationOnMock.arguments[0] as String, null, null, "basic")
                .apply { id = 99 }
                .let { Optional.of(it) }
        }
        given(projectService.findByNameAndOrganizationName(any(), any())).willAnswer {
            Project.stub(id = 99)
        }

        val result = permissionService.setRole("Example Org", "Example", SetRoleRequest("user", Role.ADMIN))
            .blockOptional()

        Assertions.assertTrue(result.isPresent)
        verify(lnkUserProjectService, times(1)).setRole(any(), any(), any())
    }

    private fun PermissionService.getRole(userName: String, projectName: String, organizationName: String): Mono<Role> =
             findUserAndProject(userName, organizationName, projectName).map { (user, project) ->
                getRole(user, project)
            }
}
