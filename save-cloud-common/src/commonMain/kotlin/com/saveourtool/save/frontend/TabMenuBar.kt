package com.saveourtool.save.frontend

import com.saveourtool.save.domain.Role

/**
 * Interface for tab bar in many pages
 */
interface TabMenuBar<T : Enum<T>> {
    /**
     * Default value in every Enum classes
     */
    val defaultTab: T

    /**
     * Regular expression to determine tab based on URL
     */
    val regexForUrlClassification: Regex

    /**
     * name of the head section in url address for non-default tab
     */
    val nameOfTheHeadUrlSection: String

    /**
     * @return Array of elements this Enum
     */
    fun values(): Array<T>

    /**
     * @param elem
     * @return The Enum element by the string it corresponds to, or an exception if it is not found
     */
    fun valueOf(elem: String): T

    /**
     * @param elem
     * @return Equivalent to valueOf(), but returns null instead of an exception
     */
    fun valueOfOrNull(elem: String): T? = values().firstOrNull { valueOf(elem.uppercase()) == it }

    /**
     * @param role
     * @param elem
     * @param isOrganizationCanCreateContest
     * @return Returns true if the check for this tab and role is not passed, else return false
     */
    fun isAvailableWithThisRole(role: Role, elem: T?, isOrganizationCanCreateContest: Boolean?): Boolean = true
}
