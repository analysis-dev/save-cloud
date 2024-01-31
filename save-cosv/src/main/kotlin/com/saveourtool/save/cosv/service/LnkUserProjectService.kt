package com.saveourtool.save.cosv.service

import com.saveourtool.save.authservice.utils.userId
import com.saveourtool.save.cosv.repositorysave.LnkUserProjectRepository
import com.saveourtool.save.domain.Role
import com.saveourtool.save.entities.LnkUserProject
import com.saveourtool.save.entities.Project
import com.saveourtool.save.entities.ProjectStatus
import com.saveourtool.save.entities.User
import com.saveourtool.save.repository.UserRepository
import com.saveourtool.save.utils.getHighestRole

import org.springframework.data.domain.PageRequest
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service

/**
 * Service of lnkUserProjects
 */
@Service
class LnkUserProjectService(
    private val lnkUserProjectRepository: LnkUserProjectRepository,
    private val userRepository: UserRepository,
    private val userDetailsService: UserService,
) {
    /**
     * @param project
     * @return all users with role in project
     */
    fun getAllUsersAndRolesByProject(project: Project) =
            lnkUserProjectRepository.findByProject(project).associate { it.user to (it.role) }

    /**
     * @param userId
     * @param project
     * @return role for user in [project] by user ID
     */
    fun findRoleByUserIdAndProject(userId: Long, project: Project) = lnkUserProjectRepository
        .findByUserIdAndProject(userId, project)
        ?.role
        ?: Role.NONE

    /**
     * Set role of [user] on a project [project] to [role]
     *
     * @throws IllegalStateException if [role] is [Role.NONE]
     */
    @Suppress("KDOC_WITHOUT_PARAM_TAG", "UnsafeCallOnNullableType")
    fun setRole(user: User, project: Project, role: Role) {
        if (role == Role.NONE) {
            throw IllegalStateException("Role NONE should not be present in database!")
        }
        val lnkUserProject = lnkUserProjectRepository.findByUserIdAndProject(user.id!!, project)
            ?.apply { this.role = role }
            ?: LnkUserProject(project, user, role)
        lnkUserProjectRepository.save(lnkUserProject)
    }

    /**
     * Set role of user with [userId] on a project with [projectId] to [role]
     *
     * @throws IllegalStateException if [role] is [Role.NONE]
     */
    @Suppress("KDOC_WITHOUT_PARAM_TAG", "UnsafeCallOnNullableType")
    fun setRoleByIds(userId: Long, projectId: Long, role: Role) {
        if (role == Role.NONE) {
            throw IllegalStateException("Role NONE should not be present in database!")
        }
        lnkUserProjectRepository.findByUserIdAndProjectId(userId, projectId)
            ?.apply { this.role = role }
            ?.let { lnkUserProjectRepository.save(it) }
            ?: lnkUserProjectRepository.save(projectId, userId, role.toString())
    }

    /**
     * @param user that should be deleted from [project]
     * @param project
     * @return none
     * @throws NoSuchElementException
     */
    @Suppress("UnsafeCallOnNullableType")
    fun removeRole(user: User, project: Project) = lnkUserProjectRepository.findByUserIdAndProject(user.id!!, project)
        ?.id
        ?.let {
            lnkUserProjectRepository.deleteById(it)
        }
        ?: throw NoSuchElementException(
            "Cannot delete user with name ${user.name} because he is not found in project ${project.organization.name}/${project.name}"
        )

    /**
     * Get certain [pageSize] of platform users with names that start with [prefix]
     *
     * @param prefix
     * @param projectUserIds
     * @param pageSize
     * @return list of all save-cloud users
     */
    fun getNonProjectUsersByNamePrefix(prefix: String, projectUserIds: Set<Long>, pageSize: Int): List<User> = if (pageSize > 0) {
        userRepository.findByNameStartingWithAndIdNotIn(prefix, projectUserIds, PageRequest.of(0, pageSize)).content
    } else {
        emptyList()
    }

    /**
     * @param name
     * @param projectUserIds
     * @return list of [User]s not from project with names that exactly match [name]
     */
    fun getNonProjectUsersByName(name: String, projectUserIds: Set<Long>): List<User> = userRepository.findByNameAndIdNotIn(name, projectUserIds)

    /**
     * @param project
     * @return list of [User]s that are connected to [project]
     */
    fun getAllUsersByProject(project: Project): List<User> = lnkUserProjectRepository.findByProject(project).map { it.user }

    /**
     * @param userId
     * @param statuses
     * @return list of [Project]s that are connected to user with [userId]
     */
    fun getProjectsByUserIdAndStatuses(userId: Long, statuses: Set<ProjectStatus> = setOf(ProjectStatus.CREATED)): List<Project> =
            lnkUserProjectRepository.findByUserIdAndProjectStatusIn(userId, statuses).mapNotNull { it.project }

    /**
     * @param authentication
     * @param project
     * @return the highest of two roles: the one in [project] and global one.
     */
    fun getGlobalRoleOrProjectRole(authentication: Authentication, project: Project): Role {
        val selfId = authentication.userId()
        val selfGlobalRole = userDetailsService.getGlobalRole(authentication)
        val selfOrganizationRole = findRoleByUserIdAndProject(selfId, project)
        return getHighestRole(selfOrganizationRole, selfGlobalRole)
    }
}
