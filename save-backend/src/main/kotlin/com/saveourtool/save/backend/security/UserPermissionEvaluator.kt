package com.saveourtool.save.backend.security

import com.saveourtool.save.authservice.utils.username
import com.saveourtool.save.backend.service.LnkUserOrganizationService
import com.saveourtool.save.info.UserPermissions
import com.saveourtool.save.info.UserPermissionsInOrganization
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

/**
 * Class that is capable of assessing user's permissions regarding.
 */
@Component
class UserPermissionEvaluator(
    private var lnkUserOrganizationService: LnkUserOrganizationService,
) {
    /**
     * @param authentication
     * @return UserPermissions
     */
    fun getUserPermissions(
        authentication: Authentication,
    ): UserPermissions {
        val lnkOrganizations = lnkUserOrganizationService.getOrganizationsByUserNameAndCreatedStatus(authentication.username())

        return UserPermissions(
            lnkOrganizations.associate { it.organization.name to UserPermissionsInOrganization(it.organization.canCreateContests, it.organization.canBulkUpload) },
        )
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
        val lnkOrganization = lnkUserOrganizationService.getOrganizationsByUserNameAndCreatedStatusAndOrganizationName(authentication.username(), organizationName)

        val isPermittedCreateContest = lnkOrganization?.organization?.canCreateContests ?: false
        val isPermittedToBulkUpload = lnkOrganization?.organization?.canBulkUpload ?: false

        return UserPermissions(
            mapOf(organizationName to UserPermissionsInOrganization(isPermittedCreateContest, isPermittedToBulkUpload)),
        )
    }
}
