package com.saveourtool.save.cosv.service

import com.saveourtool.save.authservice.utils.userId
import com.saveourtool.save.cosv.repositorysave.OrganizationRepository
import com.saveourtool.save.cosv.utils.hasRole
import com.saveourtool.save.domain.OrganizationSaveStatus
import com.saveourtool.save.domain.Role
import com.saveourtool.save.entities.Organization
import com.saveourtool.save.entities.OrganizationStatus
import com.saveourtool.save.entities.ProjectStatus
import com.saveourtool.save.entities.ProjectStatus.CREATED
import com.saveourtool.save.filters.OrganizationFilter
import com.saveourtool.save.info.UserPermissions
import com.saveourtool.save.info.UserPermissionsInOrganization
import com.saveourtool.save.permission.Permission
import com.saveourtool.save.utils.orNotFound
import com.saveourtool.save.validation.isValidLengthName
import org.jetbrains.annotations.Blocking
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Service for organization
 */
@Service
class OrganizationService(
    private val organizationRepository: OrganizationRepository,
    private val projectService: ProjectService,
) {
    /**
     * Store [organization] in the database
     *
     * @param organization an [Organization] to store
     * @return organization's id, should never return null
     */
    @Suppress("UnsafeCallOnNullableType", "TooGenericExceptionCaught")
    @Transactional
    fun saveOrganization(organization: Organization): Pair<Long, OrganizationSaveStatus> {
        val (organizationId, organizationSaveStatus) = if (!organization.name.isValidLengthName()) {
            Pair(0L, OrganizationSaveStatus.INVALID_NAME)
        } else if (organizationRepository.validateName(organization.name) != 0L) {
            organizationRepository.saveHighLevelName(organization.name)
            Pair(organizationRepository.save(organization).id, OrganizationSaveStatus.NEW)
        } else {
            Pair(0L, OrganizationSaveStatus.CONFLICT)
        }
        requireNotNull(organizationId) { "Should have gotten an ID for organization from the database" }
        return Pair(organizationId, organizationSaveStatus)
    }

    /**
     * @param name
     * @return organization with [name]
     */
    fun getOrganizationByName(name: String): Organization = organizationRepository.getOrganizationByName(name)

    /**
     * @param id
     * @return organization with [id]
     */
    fun getOrganizationById(id: Long): Organization = organizationRepository.findByIdOrNull(id).orNotFound { "Organization with id: $id not found" }

    /**
     * @param authentication
     * @param organizationName name of organization
     * @param permission
     * @return true if [authentication] has [permission] in [organizationName], otherwise -- false
     */
    @Blocking
    fun hasPermissionInOrganization(
        authentication: Authentication?,
        organizationName: String,
        permission: Permission,
    ): Boolean {
        authentication ?: return permission == Permission.READ
        val userId = authentication.userId()
        if (authentication.hasRole(Role.SUPER_ADMIN)) {
            return true
        }

        val organization = organizationRepository.getOrganizationByName(organizationName)
        val organizationRole = organization.id?.let { organizationRepository.findRoleByUserIdAndOrganization(userId, it) }
        organizationRole ?: return permission == Permission.READ
        return when (permission) {
            Permission.READ -> hasReadAccess(userId, organizationRole)
            Permission.WRITE -> hasWriteAccess(userId, organizationRole)
            Permission.DELETE -> hasDeleteAccess(userId, organizationRole)
            Permission.BAN -> hasBanAccess(userId, organizationRole)
        }
    }

    /**
     * @param authentication
     * @param organizationName
     * @return UserPermissions
     */
    fun getUserPermissionsByOrganizationName(
        authentication: Authentication,
        organizationName: String,
    ): UserPermissions {
        val lnkOrganization = organizationRepository.findByUserNameAndOrganizationStatusAndOrganizationName(authentication.userId(), OrganizationStatus.CREATED.name,
            organizationName)

        val isPermittedCreateContest = lnkOrganization?.organization?.canCreateContests ?: false
        val isPermittedToBulkUpload = lnkOrganization?.organization?.canBulkUpload ?: false

        return UserPermissions(
            mapOf(organizationName to UserPermissionsInOrganization(isPermittedCreateContest, isPermittedToBulkUpload)),
        )
    }

    /**
     * @param organizationFilter
     * @param pageable [Pageable]
     * @return list of organizations with that match [organizationFilter]
     */
    fun getFiltered(organizationFilter: OrganizationFilter, pageable: Pageable): List<Organization> = organizationRepository.findByNameStartingWithAndStatusIn(
        organizationFilter.prefix,
        organizationFilter.statuses,
        pageable,
    )

    /**
     * @param organization
     * @return organization
     */
    fun updateOrganization(organization: Organization): Organization = organizationRepository.save(organization)

    /**
     * @param name
     * @param statuses
     * @return organization by name and statuses
     */
    fun findByNameAndStatuses(name: String, statuses: Set<OrganizationStatus>) =
            organizationRepository.findByNameAndStatusIn(name, statuses)

    /**
     * @param name
     * @return organization by name with [CREATED] status
     */
    fun findByNameAndCreatedStatus(name: String) = findByNameAndStatuses(name, EnumSet.of(OrganizationStatus.CREATED))

    /**
     * Mark organization [organization] as deleted
     *
     * @param organization an [Organization] to delete
     * @return deleted organization
     */
    fun deleteOrganization(organization: Organization): Organization = changeOrganizationStatus(organization, OrganizationStatus.DELETED)

    /**
     * Mark organization with [organization] as created.
     * If an organization was previously banned, then all its projects become deleted.
     *
     * @param organization an [Organization] to create
     * @return recovered organization
     */
    @Transactional
    fun recoverOrganization(organization: Organization): Organization {
        if (organization.status == OrganizationStatus.BANNED) {
            projectService.getAllByOrganizationName(organization.name).forEach {
                it.status = ProjectStatus.DELETED
                projectService.updateProject(it)
            }
        }
        return changeOrganizationStatus(organization, OrganizationStatus.CREATED)
    }

    /**
     * Mark organization with [organization] and all its projects as banned.
     *
     * @param organization an [Organization] to ban
     * @return banned organization
     */
    @Transactional
    fun banOrganization(organization: Organization): Organization {
        projectService.getAllByOrganizationName(organization.name).forEach {
            it.status = ProjectStatus.BANNED
            projectService.updateProject(it)
        }
        return changeOrganizationStatus(organization, OrganizationStatus.BANNED)
    }

    /**
     * @param organizationName the unique name of the organization.
     * @return `true` if this organization has at least one non-deleted project,
     *   `false` otherwise.
     */
    fun hasProjects(organizationName: String): Boolean =
            projectService.getAllByOrganizationName(organizationName).any { project ->
                project.status == CREATED
            }

    /**
     * Mark organization with [organization] as [newProjectStatus].
     * Before performing the function, check for user permissions by the [organization].
     *
     * @param newProjectStatus is new status for [organization]
     * @param organization is organization in which the status will be changed
     * @return organization
     */
    @Suppress("UnsafeCallOnNullableType")
    private fun changeOrganizationStatus(organization: Organization, newProjectStatus: OrganizationStatus): Organization = organization
        .apply {
            status = newProjectStatus
        }
        .let {
            organizationRepository.save(it)
        }

    @Suppress("FunctionOnlyReturningConstant", "UNUSED_PARAMETER")
    private fun hasReadAccess(userId: Long?, organizationRole: Role): Boolean = true

    private fun hasWriteAccess(userId: Long?, organizationRole: Role): Boolean = hasDeleteAccess(userId, organizationRole) ||
            userId?.let { organizationRole == Role.ADMIN } ?: false

    private fun hasDeleteAccess(userId: Long?, organizationRole: Role): Boolean =
            hasBanAccess(userId, organizationRole) || userId?.let { organizationRole == Role.OWNER } ?: false

    /**
     * Only [Role.SUPER_ADMIN] can ban the project. And a user with such a global role has permissions for all actions.
     * Since we have all the rights issued depending on the following, you need to set [false] here
     */
    @Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
    private fun hasBanAccess(userId: Long?, organizationRole: Role): Boolean = false
}
