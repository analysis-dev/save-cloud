package com.saveourtool.save.filters

import com.saveourtool.save.entities.ProjectStatus
import kotlinx.serialization.Serializable

/**
 * @property name substring that match the beginning of a name
 * @property statuses current [statuses] of an organization
 * @property organizationName
 */
@Serializable
data class ProjectFilters(
    val name: String,
    val organizationName: String = "",
    val statuses: Set<ProjectStatus> = setOf(ProjectStatus.CREATED),
) {
    companion object {
        val created = ProjectFilters(name = "")

        /**
         * The filter which returns projects with any status, not just
         * [ProjectStatus.CREATED].
         */
        val any = ProjectFilters(
            name = "",
            statuses = enumValues<ProjectStatus>().toSet(),
        )
    }
}
