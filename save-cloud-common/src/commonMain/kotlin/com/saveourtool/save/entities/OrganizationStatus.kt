package com.saveourtool.save.entities

import kotlinx.serialization.Serializable

/**
 * Enum of organization status
 */
@Serializable
enum class OrganizationStatus {
    /**
     * Organization banned
     */
    BANNED,

    /**
     * Organization created
     */
    CREATED,

    /**
     * Organization deleted
     */
    DELETED,
    ;
}
