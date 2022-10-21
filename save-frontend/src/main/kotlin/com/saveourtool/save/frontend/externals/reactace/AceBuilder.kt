@file:Suppress("HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE")

package com.saveourtool.save.frontend.externals.reactace

import com.saveourtool.save.utils.DEBOUNCE_PERIOD_FOR_EDITORS

import csstype.ClassName
import io.github.petertrr.diffutils.diff
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div

import kotlinx.js.jso

/**
 * @param text displayed text
 * @param selectedMode highlight mode
 * @param selectedTheme displayed theme
 * @param aceMarkers array of [AceMarker]s that defines which lines should be marked as unsaved
 * @param disabled should this editor be readonly
 * @param onChangeFun callback invoked on input
 */
@Suppress("TOO_MANY_PARAMETERS", "LongParameterList")
fun ChildrenBuilder.aceBuilder(
    text: String,
    selectedMode: AceModes,
    selectedTheme: AceThemes = AceThemes.CHROME,
    aceMarkers: Array<AceMarker> = emptyArray(),
    disabled: Boolean = false,
    onChangeFun: (String) -> Unit,
) {
    selectedTheme.require()
    selectedMode.require()
    div {
        className = ClassName("d-flex justify-content-center flex-fill")
        reactAce {
            className = "flex-fill"
            mode = selectedMode.modeName
            theme = selectedTheme.themeName
            width = "auto"
            debounceChangePeriod = DEBOUNCE_PERIOD_FOR_EDITORS
            value = text
            showPrintMargin = false
            readOnly = disabled
            onChange = { value, _ ->
                onChangeFun(value)
            }
            markers = aceMarkers
        }
    }
}

/**
 * Get array of [AceMarker]s for modified lines of a [String].
 *
 * @param oldString old version of string
 * @param newString new version of string
 * @return Array of [AceMarker]s corresponding to modified lines.
 */
fun getAceMarkers(oldString: String, newString: String) = diff(
    oldString.split("\n"),
    newString.split("\n"),
)
    .deltas
    .map {
        it.target.position to it.target.last()
    }
    .map { (from, to) ->
        aceMarkerBuilder(from, to)
    }
    .toTypedArray()

/**
 * Get [AceMarker]
 *
 * @param beginLineIndex index of the first line to be marked
 * @param endLineIndex index of the last line to be marked
 * @param markerType type of marker
 * @param classes
 * @return [AceMarker]
 */
fun aceMarkerBuilder(
    beginLineIndex: Int,
    endLineIndex: Int = beginLineIndex,
    markerType: String = "fullLine",
    classes: String = "unsaved-marker",
): AceMarker = jso {
    startRow = beginLineIndex
    endRow = endLineIndex
    startCol = 0
    endCol = 1
    className = classes
    type = markerType
    inFront = false
}
