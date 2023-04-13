package com.saveourtool.save.entities

import kotlinx.serialization.Serializable

/**
 * @property name
 * @property description
 * @property critical
 * @property vulnerabilityName
 * @property organizationName
 * @property projectName
 * @property id
 */
@Serializable
data class ProjectProblemDto(
    val name: String,
    val description: String,
    val critical: ProjectProblemCritical,
    val vulnerabilityName: String?,
    val organizationName: String,
    val projectName: String,
    val id: Long? = null,
) {
    companion object {
        val empty = ProjectProblemDto(
            "",
            "",
            ProjectProblemCritical.LOW,
            null,
            "",
            "",
        )
    }
}
