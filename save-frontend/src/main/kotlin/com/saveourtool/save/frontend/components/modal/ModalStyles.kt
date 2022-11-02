@file:Suppress("HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE")

package com.saveourtool.save.frontend.components.modal

import com.saveourtool.save.frontend.externals.modal.CssProperties
import com.saveourtool.save.frontend.externals.modal.Styles
import kotlin.js.json

val defaultModalStyle = Styles(
    // make modal window occupy center of the screen
    content = json(
        "top" to "25%",
        "left" to "35%",
        "right" to "35%",
        "bottom" to "auto",
        "overflow" to "hide"
    ).unsafeCast<CssProperties>()
)

val smallTransparentModalStyle = Styles(
    content = json(
        "top" to "10%",
        "left" to "35%",
        "right" to "35%",
        "bottom" to "2%",
        "overflow" to "hide",
        "backgroundColor" to "transparent",
        "border" to "1px solid rgba(255, 255, 255, 0.01)"
    ).unsafeCast<CssProperties>()
)

val mediumTransparentModalStyle = Styles(
    content = json(
        "top" to "5%",
        "left" to "20%",
        "right" to "20%",
        "bottom" to "2%",
        "overflow" to "hide",
        "backgroundColor" to "transparent",
        "border" to "1px solid rgba(255, 255, 255, 0.01)"
    ).unsafeCast<CssProperties>()
)

val largeTransparentModalStyle = Styles(
    content = json(
        "top" to "5%",
        "left" to "5%",
        "right" to "5%",
        "bottom" to "2%",
        "overflow" to "hide",
        "backgroundColor" to "transparent",
        "border" to "1px solid rgba(255, 255, 255, 0.01)"
    ).unsafeCast<CssProperties>()
)

val loaderModalStyle = Styles(
    content = json(
        "top" to "25%",
        "left" to "35%",
        "right" to "35%",
        "bottom" to "45%",
        "overflow" to "hide",
        "backgroundColor" to "transparent",
        // small hack to remove modal border and make loader prettier
        "border" to "1px solid rgba(255, 255, 255, 0.01)"
    ).unsafeCast<CssProperties>()
)
