/**
 * Names that are used as endpoints in the frontend.
 * If you create a new view with new URL - add it here.
 */

package com.saveourtool.save.validation

import com.saveourtool.save.utils.URL_PATH_DELIMITER
import kotlin.js.JsExport

const val SETTINGS = "settings"

/**
 * @property path substring of url that defines given route
 */
@JsExport
enum class FrontendRoutes(val path: String) {
    ABOUT_US("about"),
    AWESOME_BENCHMARKS("awesome-benchmarks"),
    BAN("ban"),
    CONTESTS("contests"),
    CONTESTS_GLOBAL_RATING("contests/global-rating"),
    CONTESTS_TEMPLATE("contest-template"),
    COOKIE("cookie"),
    CREATE_CONTESTS_TEMPLATE("create-contest-template"),
    CREATE_ORGANIZATION("create-organization"),
    CREATE_PROJECT("create-project"),
    CREATE_VULNERABILITY("vuln/create-vulnerability"),
    DEMO("demo"),
    ERROR_404("404"),
    INDEX(""),
    MANAGE_ORGANIZATIONS("organizations"),
    NOT_FOUND("not-found"),
    PROFILE("profile"),
    PROJECTS("projects"),
    REGISTRATION("registration"),
    SANDBOX("sandbox"),
    SAVE("save"),
    SETTINGS_DELETE("$SETTINGS/delete"),
    SETTINGS_EMAIL("$SETTINGS/email"),
    SETTINGS_ORGANIZATIONS("$SETTINGS/organizations"),
    SETTINGS_PROFILE("$SETTINGS/profile"),
    SETTINGS_TOKEN("$SETTINGS/token"),
    TERMS_OF_USE("terms-of-use"),
    VULN("vuln"),
    VULNERABILITIES("$VULN/list"),
    VULNERABILITY_SINGLE("$VULN/collection"),
    VULN_TOP_RATING("$VULN/top-rating"),
    ;

    override fun toString(): String = path

    companion object {
        /**
         * List of views on which topbar should not be rendered
         */
        val noTopBarViewList = arrayOf(
            REGISTRATION,
            INDEX,
            ERROR_404,
            TERMS_OF_USE,
        )

        /**
         * Get forbidden words from [FrontendRoutes].
         *
         * @return list of forbidden words
         */
        fun getForbiddenWords() = FrontendRoutes.values()
            .map { it.path.split(URL_PATH_DELIMITER) }
            .flatten()
            .toTypedArray()
    }
}
