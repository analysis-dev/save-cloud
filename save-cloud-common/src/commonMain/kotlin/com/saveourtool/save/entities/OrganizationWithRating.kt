package com.saveourtool.save.entities

import kotlinx.serialization.Serializable

/**
 * @property organization
 * @property globalRating
 */
@Serializable
data class OrganizationWithRating(
    val organization: OrganizationDto,
    val globalRating: Double,
)
