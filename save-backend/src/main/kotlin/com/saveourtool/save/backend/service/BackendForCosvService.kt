package com.saveourtool.save.backend.service

import com.saveourtool.save.backend.security.UserPermissionEvaluator
import com.saveourtool.save.entities.Organization
import com.saveourtool.save.entities.User
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service

/**
 * Service for [IBackendService] to get required info for COSV from backend
 */
@Service
class BackendForCosvService(
    private val organizationService: OrganizationService,
    private val userDetailsService: UserDetailsService,
    private val userPermissionEvaluator: UserPermissionEvaluator,
) : IBackendService {
    override fun getOrganizationByName(name: String): Organization = organizationService.getByName(name)

    override fun getUserByName(name: String): User = userDetailsService.getByName(name)

    override fun getUserPermissionsByOrganizationName(authentication: Authentication, organizationName: String) = userPermissionEvaluator.getUserPermissionsByOrganizationName(
        authentication, organizationName
    )
}