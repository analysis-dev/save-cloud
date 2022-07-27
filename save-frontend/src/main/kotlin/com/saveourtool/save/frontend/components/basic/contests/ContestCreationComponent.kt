@file:Suppress("FILE_NAME_MATCH_CLASS", "FILE_WILDCARD_IMPORTS", "LargeClass")

package com.saveourtool.save.frontend.components.basic.contests

import com.saveourtool.save.entities.ContestDto
import com.saveourtool.save.frontend.components.basic.*
import com.saveourtool.save.frontend.externals.modal.CssProperties
import com.saveourtool.save.frontend.externals.modal.Styles
import com.saveourtool.save.frontend.externals.modal.modal
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.frontend.utils.noopLoadingHandler
import com.saveourtool.save.utils.LocalDateTime

import csstype.ClassName
import org.w3c.fetch.Response
import react.*
import react.dom.html.ButtonType
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form

import kotlin.js.json
import kotlinx.browser.window
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Component that allows to create new contests
 */
val contestCreationComponent = contestCreationComponent()

private val contestCreationCard = cardComponent()

/**
 *  Contest creation component props
 */
external interface ContestCreationComponentProps : Props {
    /**
     * Name of current organization
     */
    var organizationName: String

    /**
     * Callback invoked on successful contest creation
     */
    var onSaveSuccess: (String) -> Unit

    /**
     * Callback invoked on error while contest creation
     */
    var onSaveError: (Response) -> Unit
}

/**
 * @param organizationName name of an organization to which a new contest will be linked
 * @param isOpen flag that indicates if the modal is open
 * @param onSuccess callback invoked on successful contest creation
 * @param onFailure callback invoked on error when creating contest
 * @param onClose callback invoked on close button press
 */
fun ChildrenBuilder.showContestCreationModal(
    organizationName: String,
    isOpen: Boolean,
    onSuccess: (String) -> Unit,
    onFailure: (Response) -> Unit,
    onClose: () -> Unit,
) {
    modal { props ->
        props.isOpen = isOpen
        props.style = Styles(
            content = json(
                "top" to "25%",
                "left" to "30%",
                "right" to "30%",
                "bottom" to "auto",
                "position" to "absolute",
                "overflow" to "hide"
            ).unsafeCast<CssProperties>()
        )
        contestCreationComponent {
            this.organizationName = organizationName
            onSaveSuccess = onSuccess
            onSaveError = onFailure
        }
        div {
            className = ClassName("d-flex justify-content-center")
            button {
                type = ButtonType.button
                className = ClassName("btn btn-secondary mt-4")
                +"Cancel"
                onClick = {
                    onClose()
                }
            }
        }
    }
}

private fun String.dateToLocalDateTime(time: LocalTime = LocalTime(0, 0, 0)) = LocalDateTime(LocalDate.parse(this), time)

@Suppress(
    "TOO_LONG_FUNCTION",
    "LongMethod",
    "MAGIC_NUMBER",
    "AVOID_NULL_CHECKS"
)
private fun contestCreationComponent() = FC<ContestCreationComponentProps> { props ->
    val stubDateTime = LocalDateTime(1, 1, 1, 1, 1)
    val (contestDto, setContestDto) = useState(
        ContestDto(
            "",
            stubDateTime,
            stubDateTime,
            "",
            props.organizationName,
        )
    )

    val onSaveButtonPressed = useRequest {
        val response = post(
            "$apiUrl/contests/create",
            jsonHeaders,
            Json.encodeToString(contestDto),
            ::noopLoadingHandler,
        )
        if (!response.ok) {
            props.onSaveError(response)
        } else {
            props.onSaveSuccess("${window.location.origin}#/contests/${contestDto.name}")
        }
    }

    div {
        className = ClassName("card")
        contestCreationCard {
            div {
                className = ClassName("")
                form {
                    className = ClassName("needs-validation")
                    // ==== Contest Name
                    div {
                        className = ClassName("mt-2")
                        inputTextFormRequired(
                            InputTypes.CONTEST_NAME,
                            true,
                            "col-12",
                            "Contest name"
                        ) {
                            setContestDto(contestDto.copy(name = it.target.value))
                        }
                    }
                    // ==== Organization Name selection
                    div {
                        className = ClassName("mt-2")
                        inputTextDisabled(
                            InputTypes.CONTEST_SUPER_ORGANIZATION_NAME,
                            "col-12",
                            "Super organization name",
                            contestDto.organizationName
                        )
                    }
                    // ==== Contest dates
                    div {
                        className = ClassName("mt-2 d-flex justify-content-between")
                        inputDateFormRequired(
                            InputTypes.CONTEST_START_TIME,
                            true,
                            "col-6",
                            "Starting time",
                        ) {
                            setContestDto(contestDto.copy(startTime = it.target.value.dateToLocalDateTime()))
                        }
                        inputDateFormRequired(
                            InputTypes.CONTEST_END_TIME,
                            true,
                            "col-6",
                            "Ending time",
                        ) {
                            setContestDto(contestDto.copy(endTime = it.target.value.dateToLocalDateTime(LocalTime(23, 59, 59))))
                        }
                    }
                    // ==== Contest description
                    div {
                        className = ClassName("mt-2")
                        inputTextFormOptional(
                            InputTypes.CONTEST_DESCRIPTION,
                            "",
                            "Contest description",
                        ) {
                            setContestDto(contestDto.copy(description = it.target.value))
                        }
                    }
                }
            }
            div {
                className = ClassName("mt-3 d-flex justify-content-center")
                button {
                    type = ButtonType.button
                    className = ClassName("btn btn-primary")
                    +"Create contest"
                    onClick = {
                        onSaveButtonPressed()
                    }
                }
            }
        }
    }
}
