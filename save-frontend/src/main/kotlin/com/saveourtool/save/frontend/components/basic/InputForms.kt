@file:Suppress("FILE_NAME_MATCH_CLASS")

package com.saveourtool.save.frontend.components.basic

import com.saveourtool.save.validation.DATE_RANGE_ERROR_MESSAGE
import com.saveourtool.save.validation.EMAIL_ERROR_MESSAGE
import com.saveourtool.save.validation.NAME_ERROR_MESSAGE
import com.saveourtool.save.validation.URL_ERROR_MESSAGE
import csstype.ClassName
import org.w3c.dom.HTMLInputElement
import react.ChildrenBuilder
import react.dom.*
import react.dom.aria.ariaDescribedBy
import react.dom.events.ChangeEvent
import react.dom.html.InputType
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.span

private const val URL_PLACEHOLDER = "https://example.com"
private const val EMAIL_PLACEHOLDER = "test@example.com"

/**
 * @property str
 * @property placeholder
 * @property errorMessage
 */
@Suppress("WRONG_DECLARATIONS_ORDER")
enum class InputTypes(
    val str: String,
    val errorMessage: String? = null,
    val placeholder: String? = null,
) {
    // ==== new project view
    DESCRIPTION("project description"),
    GIT_BRANCH("git branch"),
    GIT_TOKEN("git token"),
    GIT_URL("git url", URL_ERROR_MESSAGE, URL_PLACEHOLDER),
    GIT_USER("git username"),
    PROJECT_EMAIL("project email", EMAIL_ERROR_MESSAGE, EMAIL_PLACEHOLDER),

    // ==== signIn view
    LOGIN("login"),
    PASSWORD("password"),
    PROJECT_NAME("project name", NAME_ERROR_MESSAGE),
    PROJECT_URL("project Url", URL_ERROR_MESSAGE, URL_PLACEHOLDER),

    // ==== create organization view
    ORGANIZATION_NAME("organization name", NAME_ERROR_MESSAGE),

    // ==== user setting view
    USER_EMAIL("user email", EMAIL_ERROR_MESSAGE, EMAIL_PLACEHOLDER),
    COMPANY("company"),
    LOCATION("location"),
    GIT_HUB("git hub"),
    LINKEDIN("linkedin"),
    TWITTER("twitter"),

    // ==== contest creation component
    CONTEST_NAME("contest name", NAME_ERROR_MESSAGE),
    CONTEST_START_TIME("contest starting time", DATE_RANGE_ERROR_MESSAGE),
    CONTEST_END_TIME("contest ending time", DATE_RANGE_ERROR_MESSAGE),
    CONTEST_DESCRIPTION("contest description"),
    CONTEST_SUPER_ORGANIZATION_NAME("contest's super organization's name", NAME_ERROR_MESSAGE),
    ;
}

/**
 * @param form
 * @param validInput
 * @param classes
 * @param name
 * @param errorText
 * @param onChangeFun
 * @param textValue
 * @return div with an input form
 */
@Suppress(
    "TOO_LONG_FUNCTION",
    "TOO_MANY_PARAMETERS",
    "LongParameterList",
)
internal fun ChildrenBuilder.inputTextFormRequired(
    form: InputTypes,
    textValue: String?,
    validInput: Boolean,
    classes: String,
    name: String,
    errorText: String = "Please input a valid ${form.str}",
    onChangeFun: (ChangeEvent<HTMLInputElement>) -> Unit
) =
        div {
            className = ClassName("$classes mt-1")
            label {
                className = ClassName("form-label")
                htmlFor = form.name
                +name
            }

            div {
                className = ClassName("input-group has-validation")
                span {
                    className = ClassName("input-group-text")
                    id = "${form.name}Span"
                    +"*"
                }

                val inputType = if (form == InputTypes.PASSWORD) InputType.password else InputType.text
                input {
                    type = inputType
                    onChange = onChangeFun
                    id = form.name
                    required = true
                    value = textValue
                    placeholder = form.placeholder
                    className = if (textValue.isNullOrEmpty()) {
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
                        +(form.errorMessage ?: errorText)
                    }
                }
            }
        }

/**
 * @param form
 * @param classes
 * @param name
 * @param validInput
 * @param onChangeFun
 * @param errorText
 * @param textValue
 * @return div with an input form
 */
@Suppress("TOO_MANY_PARAMETERS", "LongParameterList")
internal fun ChildrenBuilder.inputTextFormOptional(
    form: InputTypes,
    textValue: String?,
    classes: String,
    name: String?,
    validInput: Boolean = true,
    errorText: String = "Please input a valid ${form.str}",
    onChangeFun: (ChangeEvent<HTMLInputElement>) -> Unit
) = div {
    className = ClassName("$classes pl-2 pr-2")
    name?.let { name ->
        label {
            className = ClassName("form-label")
            htmlFor = form.name
            +name
        }
    }
    input {
        type = InputType.text
        onChange = onChangeFun
        ariaDescribedBy = "${form.name}Span"
        id = form.name
        required = false
        value = textValue
        placeholder = form.placeholder
        className = if (textValue.isNullOrEmpty()) {
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
            +(form.errorMessage ?: errorText)
        }
    }
}

/**
 * @param form
 * @param classes
 * @param name
 * @param inputText
 * @return div with a disabled input form
 */
internal fun ChildrenBuilder.inputTextDisabled(
    form: InputTypes,
    classes: String,
    name: String,
    inputText: String,
) = div {
    className = ClassName("$classes pl-2 pr-2")
    label {
        className = ClassName("form-label")
        htmlFor = form.name
        +name
    }
    input {
        type = InputType.text
        ariaDescribedBy = "${form.name}Span"
        id = form.name
        required = false
        className = ClassName("form-control")
        disabled = true
        value = inputText
    }
}

/**
 * @param form
 * @param classes
 * @param text
 * @param onChangeFun
 * @return a [div] with optional input form with datepicker
 */
internal fun ChildrenBuilder.inputDateFormOptional(
    form: InputTypes,
    classes: String,
    text: String,
    onChangeFun: (ChangeEvent<HTMLInputElement>) -> Unit
) = div {
    className = ClassName("$classes pl-2 pr-2")
    label {
        className = ClassName("form-label")
        htmlFor = form.name
        +text
    }
    input {
        type = InputType.date
        onChange = onChangeFun
        ariaDescribedBy = "${form.name}Span"
        id = form.name
        required = false
        className = ClassName("form-control")
    }
}

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
    className = ClassName("$classes mt-1")
    label {
        className = ClassName("form-label")
        htmlFor = form.name
        +text
    }
    div {
        className = ClassName("input-group has-validation")
        span {
            className = ClassName("input-group-text")
            id = "${form.name}Span"
            +"*"
        }
        input {
            type = InputType.date
            onChange = onChangeFun
            id = form.name
            required = true
            className = if (value.isNullOrEmpty()) {
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
