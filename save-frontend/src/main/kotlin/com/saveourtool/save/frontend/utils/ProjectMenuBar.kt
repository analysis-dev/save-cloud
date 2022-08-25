package com.saveourtool.save.frontend.utils

import com.saveourtool.save.domain.Role
import com.saveourtool.save.entities.benchmarks.MenuBar

/**
 * A value for project menu.
 */
@Suppress("WRONG_DECLARATIONS_ORDER")
enum class ProjectMenuBar {
    INFO,
    RUN,
    STATISTICS,
    SETTINGS,
    ;

    companion object : MenuBar<ProjectMenuBar> {
        override val defaultTab: ProjectMenuBar = INFO
        val listOfStringEnumElements = ProjectMenuBar.values().map { it.name.lowercase() }
        override val regex = Regex("/project/[^/]+/[^/]+/[^/]+")
        override var paths: Pair<String, String> = "" to ""

        override fun valueOf(elem: String): ProjectMenuBar = ProjectMenuBar.valueOf(elem)
        override fun values(): Array<ProjectMenuBar> = ProjectMenuBar.values()
        override fun findEnumElements(elem: String): ProjectMenuBar? = values().find { it.name.lowercase() == elem }
        override fun setPath(shortPath: String, longPath: String) {
            paths = shortPath to longPath
        }

        override fun returnStringOneOfElements(elem: ProjectMenuBar): String = elem.name

        override fun isNotAvailableWithThisRole(role: Role, elem: ProjectMenuBar?, flag: Boolean?): Boolean = ((elem == SETTINGS) || (elem == RUN)) &&
                !role.isHigherOrEqualThan(Role.ADMIN)
    }
}
