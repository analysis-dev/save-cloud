@file:Suppress("HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE")

package com.saveourtool.save.frontend.components.inputform

import csstype.ClassName
import dom.html.HTMLInputElement
import react.ChildrenBuilder
import react.dom.events.ChangeEvent
import react.dom.html.InputType
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.span

/**
 * @param form
 * @param validInput
 * @param classes
 * @param text
 * @param errorMessage
 * @param onChangeFun
 * @return a [div] with required input form with datepicker
 */
@Suppress("TOO_MANY_PARAMETERS", "LongParameterList")
internal fun ChildrenBuilder.inputDateFormRequired(
    form: InputTypes,
    validInput: Boolean,
    classes: String,
    text: String,
    errorMessage: String = "Please input a valid ${form.str}",
    onChangeFun: (ChangeEvent<HTMLInputElement>) -> Unit
) = div {
    className = ClassName(classes)
    label {
        className = ClassName("form-label")
        htmlFor = form.name
        +text
        span {
            className = ClassName("text-danger text-left")
            +"*"
        }
    }
    div {
        className = ClassName("input-group needs-validation")
        input {
            type = InputType.date
            onChange = onChangeFun
            id = form.name
            required = true
            className = if ((value as String?).isNullOrEmpty()) {
                ClassName("form-control")
            } else if (validInput) {
                ClassName("form-control is-valid")
            } else {
                ClassName("form-control is-invalid")
            }
        }
        if (!validInput) {
            div {
                className = ClassName("invalid-feedback d-block")
                +(form.errorMessage ?: errorMessage)
            }
        }
    }
}
