/**
 * A view with organization details
 */

package org.cqfn.save.frontend.components.views

import org.cqfn.save.domain.ImageInfo
import org.cqfn.save.entities.Organization
import org.cqfn.save.entities.Project
import org.cqfn.save.frontend.components.basic.privacySpan
import org.cqfn.save.frontend.components.errorStatusContext
import org.cqfn.save.frontend.components.tables.tableComponent
import org.cqfn.save.frontend.http.getOrganization
import org.cqfn.save.frontend.utils.*

import csstype.Left
import csstype.Position
import csstype.TextAlign
import csstype.Top
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.fetch.Headers
import org.w3c.fetch.Response
import org.w3c.xhr.FormData
import react.*
import react.dom.*
import react.table.columns

import kotlinx.coroutines.launch
import kotlinx.html.InputType
import kotlinx.html.hidden
import kotlinx.html.js.onChangeFunction
import kotlinx.js.jso

/**
 * `Props` retrieved from router
 */
@Suppress("MISSING_KDOC_CLASS_ELEMENTS")
external interface OrganizationProps : PropsWithChildren {
    var organizationName: String
}

/**
 * [State] of project view component
 */
external interface OrganizationViewState : State {
    /**
     * Flag to handle uploading a file
     */
    var isUploading: Boolean

    /**
     * Image to owner avatar
     */
    var image: ImageInfo?

    /**
     * Organization
     */
    var organization: Organization?
}

/**
 * A Component for owner view
 */
class OrganizationView : AbstractView<OrganizationProps, OrganizationViewState>(false) {
    init {
        state.isUploading = false
        state.organization = Organization("", null, null, null)
    }

    override fun componentDidMount() {
        super.componentDidMount()
        scope.launch {
            val avatar = getAvatar()
            val organizationNew = getOrganization(props.organizationName)
            setState {
                image = avatar
                organization = organizationNew
            }
        }
    }

    @Suppress("TOO_LONG_FUNCTION", "LongMethod", "MAGIC_NUMBER")
    override fun RBuilder.render() {
        div("d-sm-flex align-items-center justify-content-center mb-4") {
            h1("h3 mb-0 text-gray-800") {
                +"${state.organization?.name}"
            }
        }

        div("row justify-content-center") {
            // ===================== LEFT COLUMN =======================================================================
            div("col-2 mr-3") {
                div("text-xs text-center font-weight-bold text-primary text-uppercase mb-3") {
                    +"Organization"
                }

                div {
                    attrs["style"] = kotlinx.js.jso<CSSProperties> {
                        position = "relative".unsafeCast<Position>()
                        textAlign = "center".unsafeCast<TextAlign>()
                    }
                    label {
                        input(type = InputType.file) {
                            attrs.hidden = true
                            attrs {
                                onChangeFunction = { event ->
                                    val target = event.target as HTMLInputElement
                                    postImageUpload(target)
                                }
                            }
                        }
                        attrs["aria-label"] = "Change avatar owner"
                        img(classes = "avatar avatar-user width-full border color-bg-default rounded-circle") {
                            attrs.src = state.image?.path?.let {
                                "/api/avatar$it"
                            }
                                ?: run {
                                    "img/company.svg"
                                }
                            attrs.height = "260"
                            attrs.width = "260"
                        }
                    }
                }

                div("position-relative") {
                    attrs["style"] = jso<CSSProperties> {
                        position = "relative".unsafeCast<Position>()
                        textAlign = "center".unsafeCast<TextAlign>()
                    }
                    img(classes = "width-full color-bg-default") {
                        attrs.src = "img/green_square.png"
                        attrs.height = "200"
                        attrs.width = "200"
                    }
                    div("position-absolute") {
                        attrs["style"] = jso<CSSProperties> {
                            top = "40%".unsafeCast<Top>()
                            left = "40%".unsafeCast<Left>()
                        }
                        // fixme: It must be replaced with the current value after creating the calculated rating.
                        h1(" mb-0 text-gray-800") {
                            +"4.5"
                        }
                    }
                }
            }

            // ===================== RIGHT COLUMN =======================================================================
            div("col-6") {
                div("text-xs text-center font-weight-bold text-primary text-uppercase mb-3") {
                    +"Projects"
                }

                child(tableComponent(
                    columns = columns<Project> {
                        column(id = "name", header = "Evaluated Tool", { name }) {
                            buildElement {
                                td {
                                    a(href = "#/${it.row.original.organization.name}/${it.value}") { +it.value }
                                    privacySpan(it.row.original)
                                }
                            }
                        }
                        column(id = "description", header = "Description") {
                            buildElement {
                                td {
                                    +(it.value.description ?: "Description not provided")
                                }
                            }
                        }
                        column(id = "rating", header = "Contest Rating") {
                            buildElement {
                                td {
                                    +"0"
                                }
                            }
                        }
                    },
                    initialPageSize = 10,
                    useServerPaging = false,
                    usePageSelection = false,
                ) { _, _ ->
                    get(
                        url = "$apiUrl/projects/get/projects-by-organization?organizationName=${props.organizationName}",
                        headers = Headers().also {
                            it.set("Accept", "application/json")
                        },
                    )
                        .unsafeMap {
                            it.decodeFromJsonString<Array<Project>>()
                        }
                }) { }
            }
        }
    }

    private fun postImageUpload(element: HTMLInputElement) =
            scope.launch {
                setState {
                    isUploading = true
                }
                element.files!!.asList().single().let { file ->
                    val response: ImageInfo? = post(
                        "$apiUrl/image/upload?owner=${props.organizationName}",
                        Headers(),
                        FormData().apply {
                            append("file", file)
                        }
                    )
                        .decodeFromJsonString()
                    setState {
                        image = response
                    }
                }
                setState {
                    isUploading = false
                }
            }

    private suspend fun getAvatar() = get("$apiUrl/organization/${props.organizationName}/avatar", Headers(),
        responseHandler = ::noopResponseHandler)
        .unsafeMap {
            it.decodeFromJsonString<ImageInfo>()
        }

    companion object : RStatics<OrganizationProps, OrganizationViewState, OrganizationView, Context<StateSetter<Response?>>>(OrganizationView::class) {
        init {
            contextType = errorStatusContext
        }
    }
}
