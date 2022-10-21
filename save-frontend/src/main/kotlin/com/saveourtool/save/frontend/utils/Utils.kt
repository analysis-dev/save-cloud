/**
 * Various utils for frontend
 */

package com.saveourtool.save.frontend.utils

import com.saveourtool.save.domain.Role

import csstype.ClassName
import dom.html.HTMLInputElement
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.xhr.FormData
import react.ChildrenBuilder
import react.StateSetter
import react.dom.events.ChangeEvent
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.samp
import react.dom.html.ReactHTML.small
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.tr

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * An error message.
 */
internal typealias ErrorMessage = String

/**
 * A generic error handler.
 */
internal typealias ErrorHandler = (ErrorMessage) -> Unit

/**
 * The body of a [useDeferredRequest] invocation.
 *
 * @param T the return type of this action.
 */
internal typealias DeferredRequestAction<T> = suspend (WithRequestStatusContext, ErrorHandler) -> T

/**
 * Append an object [obj] to `this` [FormData] as a JSON, using kx.serialization for serialization
 *
 * @param name key to be appended to the form data
 * @param obj an object to be appended
 * @return Unit
 */
inline fun <reified T> FormData.appendJson(name: String, obj: T) =
        append(
            name,
            Blob(
                arrayOf(Json.encodeToString(obj)),
                BlobPropertyBag("application/json")
            )
        )

/**
 * @return [Role] if string matches any role, else throws [IllegalStateException]
 * @throws IllegalStateException if string is not matched with any role
 */
fun String.toRole() = Role.values().find {
    this == it.formattedName || this == it.toString()
} ?: throw IllegalStateException("Unknown role is passed: $this")

/**
 * @return lambda which does the same as receiver but takes unused arg
 */
fun <T> (() -> Unit).withUnusedArg(): (T) -> Unit = { this() }

/**
 * @return lambda which does the same but take value from [HTMLInputElement]
 */
fun StateSetter<String?>.fromInput(): (ChangeEvent<HTMLInputElement>) -> Unit =
        { event -> this(event.target.value) }

/**
 * @return lambda which does the same but take value from [HTMLInputElement]
 */
fun StateSetter<String>.fromInput(): (ChangeEvent<HTMLInputElement>) -> Unit =
        { event -> this(event.target.value) }

/**
 * Adds this text to ChildrenBuilder line by line, separating with `<br>`
 *
 * @param text text to display
 */
@Suppress("EMPTY_BLOCK_STRUCTURE_ERROR")
internal fun ChildrenBuilder.multilineText(text: String) {
    text.lines().forEach {
        small {
            samp {
                +it
            }
        }
        br { }
    }
}

/**
 * @param text
 */
internal fun ChildrenBuilder.multilineTextWithIndices(text: String) {
    table {
        className = ClassName("table table-borderless table-hover table-sm")
        tbody {
            text.lines().filterNot { it.isEmpty() }.forEachIndexed { i, line ->
                tr {
                    td {
                        +"${i + 1}"
                    }
                    td {
                        +line
                    }
                }
            }
        }
    }
}

/**
 * @param maxLength
 * @return true if string is invalid
 */
internal fun String?.isInvalid(maxLength: Int) = this.isNullOrBlank() || this.contains(" ") || this.length > maxLength

/**
 * @param digits number of digits to round to
 */
internal fun Double.toFixed(digits: Int) = asDynamic().toFixed(digits)
