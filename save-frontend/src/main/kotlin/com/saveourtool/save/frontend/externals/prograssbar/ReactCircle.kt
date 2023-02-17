@file:Suppress("HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE")
@file:JsModule("react-circle")
@file:JsNonModule

package com.saveourtool.save.frontend.externals.prograssbar

import react.Component
import react.PropsWithChildren
import react.ReactElement
import react.State

/**
 * External declaration of [ReactCircleProps] react component
 */
@JsName("default")
external class ReactCircle : Component<ReactCircleProps, State> {
    override fun render(): ReactElement<ReactCircleProps>?
}

/**
 * Props of [ReactCircleProps]
 */
external interface ReactCircleProps : PropsWithChildren {
    /**
     * Defines the size of the circle.
     */
    var size: String

    /**
     * Defines the thickness of the circle's stroke.
     */
    var lineWidth: String

    /**
     * Update to change the progress and percentage.
     */
    var progress: String

    /**
     * Color of "progress" portion of circle.
     */
    var progressColor: String

    /**
     * Color of "empty" portion of circle.
     */
    var bgColor: String

    /**
     * Color of percentage text color.
     */
    var textColor: String

    /**
     * Adjust spacing of "%" symbol and number.
     */
    var percentSpacing: Long

    /**
     * Show/hide percentage.
     */
    var showPercentage: Boolean

    /**
     * Show/hide only the "%" symbol.
     */
    var showPercentageSymbol: Boolean
}
