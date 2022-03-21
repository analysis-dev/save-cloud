package org.cqfn.save.backend.security

import org.cqfn.save.backend.repository.LnkUserProjectRepository
import org.cqfn.save.backend.service.LnkUserProjectService
import org.cqfn.save.backend.utils.AuthenticationDetails
import org.cqfn.save.domain.Role
import org.cqfn.save.entities.LnkUserProject
import org.cqfn.save.entities.Project
import org.cqfn.save.entities.User
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class, MockitoExtension::class)
@Import(ProjectPermissionEvaluator::class, LnkUserProjectService::class)
class ProjectPermissionEvaluatorTest {
    @Autowired private lateinit var projectPermissionEvaluator: ProjectPermissionEvaluator
    @MockBean private lateinit var lnkUserProjectRepository: LnkUserProjectRepository
    private lateinit var mockProject: Project

    @BeforeEach
    fun setUp() {
        mockProject = Project.stub(99)
    }

    @Test
    fun `default permissions for users with only global roles`() {
        userShouldHavePermissions(
            "super_admin", Role.SUPER_ADMIN, emptyList(), *Permission.values()
        )
        userShouldHavePermissions(
            "admin", Role.ADMIN, emptyList(), Permission.READ
        )
        userShouldHavePermissions(
            "owner", Role.OWNER, emptyList(), Permission.READ
        )
        userShouldHavePermissions(
            "viewer", Role.VIEWER, emptyList(), Permission.READ
        )
    }

    @Test
    fun `default permissions for users with only global roles for private projects`() {
        mockProject.public = false
        userShouldHavePermissions(
            "super_admin", Role.SUPER_ADMIN, emptyList(), *Permission.values()
        )
        userShouldHavePermissions(
            "admin", Role.ADMIN, emptyList()
        )
        userShouldHavePermissions(
            "owner", Role.OWNER, emptyList()
        )
        userShouldHavePermissions(
            "viewer", Role.VIEWER, emptyList()
        )
    }

    @Test
    fun `permissions for project owners`() {
        mockProject.userId = 99
        userShouldHavePermissions(
            "super_admin", Role.SUPER_ADMIN, listOf(Role.OWNER), *Permission.values(), userId = 99
        )
        userShouldHavePermissions(
            "admin", Role.ADMIN, listOf(Role.OWNER), *Permission.values(), userId = 99
        )
        userShouldHavePermissions(
            "owner", Role.OWNER, listOf(Role.OWNER), *Permission.values(), userId = 99
        )
        userShouldHavePermissions(
            "viewer", Role.VIEWER, listOf(Role.OWNER), *Permission.values(), userId = 99
        )
    }

    @Test
    fun `permissions for project admins`() {
        userShouldHavePermissions(
            "super_admin", Role.SUPER_ADMIN, listOf(Role.ADMIN), *Permission.values(), userId = 99
        )
        userShouldHavePermissions(
            "admin", Role.ADMIN, listOf(Role.ADMIN), Permission.READ, Permission.WRITE, userId = 99
        )
        userShouldHavePermissions(
            "owner", Role.OWNER, listOf(Role.ADMIN), Permission.READ, Permission.WRITE, userId = 99
        )
        userShouldHavePermissions(
            "viewer", Role.VIEWER, listOf(Role.ADMIN), Permission.READ, Permission.WRITE, userId = 99
        )
    }

    @Test
    fun `permissions for project viewers`() {
        userShouldHavePermissions(
            "super_admin", Role.SUPER_ADMIN, listOf(Role.VIEWER), *Permission.values(), userId = 99
        )
        userShouldHavePermissions(
            "admin", Role.ADMIN, listOf(Role.VIEWER), Permission.READ, userId = 99
        )
        userShouldHavePermissions(
            "owner", Role.OWNER, listOf(Role.VIEWER), Permission.READ, userId = 99
        )
        userShouldHavePermissions(
            "viewer", Role.VIEWER, listOf(Role.VIEWER), Permission.READ, userId = 99
        )
    }

    @Test
    fun `permissions for organization owners`() {
        mockProject.organization.ownerId = 99
        userShouldHavePermissions(
            "super_admin", Role.SUPER_ADMIN, emptyList(), *Permission.values(), userId = 99
        )
        userShouldHavePermissions(
            "admin", Role.ADMIN, emptyList(), *Permission.values(), userId = 99
        )
        userShouldHavePermissions(
            "owner", Role.OWNER, emptyList(), *Permission.values(), userId = 99
        )
        userShouldHavePermissions(
            "viewer", Role.VIEWER, emptyList(), *Permission.values(), userId = 99
        )
    }

    private fun userShouldHavePermissions(
        username: String,
        role: Role,
        projectRoles: List<Role>,
        vararg permissions: Permission,
        userId: Long = 1
    ) {
        val authentication = mockAuth(username, role.asSpringSecurityRole(), id = userId)
        whenever(lnkUserProjectRepository.findByUserIdAndProject(any(), any())).thenAnswer { invocation ->
            projectRoles.map { role ->
                LnkUserProject(
                    invocation.arguments[1] as Project,
                    mockUser((invocation.arguments[0] as Number).toLong()),
                    role,
                )
            }
        }
        permissions.forEach {
            Assertions.assertTrue(projectPermissionEvaluator.hasPermission(authentication, mockProject, it)) {
                "User by authentication=$authentication is expected to have permission $it on project $mockProject"
            }
        }
        Permission.values().filterNot { it in permissions }.forEach {
            Assertions.assertFalse(projectPermissionEvaluator.hasPermission(authentication, mockProject, it)) {
                "User by authentication=$authentication isn't expected to have permission $it on project $mockProject"
            }
        }
    }

    private fun mockAuth(principal: String, vararg roles: String, id: Long = 99) = UsernamePasswordAuthenticationToken(
        principal,
        "",
        roles.map { SimpleGrantedAuthority(it) }
    ).apply {
        details = AuthenticationDetails(id = id, identitySource = "")
    }

    private fun mockUser(id: Long) = User(null, null, null, "").apply { this.id = id }
}
