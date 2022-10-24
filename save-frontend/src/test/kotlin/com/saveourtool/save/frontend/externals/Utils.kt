/**
 * Generic wrappers around `testing-library`'s queries
 */

@file:Suppress(
    "MISSING_KDOC_ON_FUNCTION",
    "MISSING_KDOC_TOP_LEVEL",
    "KDOC_NO_EMPTY_TAGS",
    "KDOC_WITHOUT_PARAM_TAG",
    "KDOC_WITHOUT_RETURN_TAG",
)

package com.saveourtool.save.frontend.externals

import dom.html.HTMLElement

inline fun <reified T : HTMLElement> BoundFunctions.findByTextAndCast(text: String, options: dynamic = undefined, waitForOptions: dynamic = null) =
        findByText(text, options, waitForOptions).then { it as T }

inline fun <reified T : HTMLElement> BoundFunctions.getByTextAndCast(text: String, options: dynamic = undefined) =
        getByText(text, options).let { it as T }

inline fun <reified T : HTMLElement> BoundFunctions.getByRoleAndCast(text: String, options: dynamic = undefined) =
        getByRole(text, options).let { it as T }

inline fun <reified T : HTMLElement> BoundFunctions.queryByTextAndCast(text: String, options: dynamic = undefined) =
        queryByText(text, options).let { it as T? }
