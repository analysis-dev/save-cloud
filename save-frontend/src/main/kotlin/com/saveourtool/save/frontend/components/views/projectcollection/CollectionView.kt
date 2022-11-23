@file:Suppress("FILE_WILDCARD_IMPORTS", "WildcardImport", "HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE")

package com.saveourtool.save.frontend.components.views.projectcollection

import com.saveourtool.save.entities.ProjectDto
import com.saveourtool.save.filters.ProjectFilters
import com.saveourtool.save.frontend.components.RequestStatusContext
import com.saveourtool.save.frontend.components.requestStatusContext
import com.saveourtool.save.frontend.components.tables.TableProps
import com.saveourtool.save.frontend.components.tables.columns
import com.saveourtool.save.frontend.components.tables.tableComponent
import com.saveourtool.save.frontend.components.tables.value
import com.saveourtool.save.frontend.components.views.AbstractView
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.frontend.utils.classLoadingHandler
import com.saveourtool.save.info.UserInfo

import csstype.ClassName
import react.*
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.td

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * `Props` retrieved from router
 */
@Suppress("MISSING_KDOC_CLASS_ELEMENTS")
external interface CreationViewProps : Props {
    var currentUserInfo: UserInfo?
}

/**
 * A view with collection of projects
 */
@JsExport
@OptIn(ExperimentalJsExport::class)
class CollectionView : AbstractView<CreationViewProps, State>() {
    @Suppress("MAGIC_NUMBER", "TYPE_ALIAS")
    private val projectsTable: FC<TableProps<ProjectDto>> = tableComponent(
        columns = {
            columns {
                column(id = "organization", header = "Organization", { organizationName }) { cellContext ->
                    Fragment.create {
                        td {
                            a {
                                href = "#/${cellContext.row.original.organizationName}"
                                +cellContext.value
                            }
                        }
                    }
                }
                column(id = "name", header = "Evaluated Tool", { name }) { cellContext ->
                    Fragment.create {
                        td {
                            a {
                                href = "#/${cellContext.row.original.organizationName}/${cellContext.value}"
                                +cellContext.value
                            }
                            privacySpan(cellContext.row.original)
                        }
                    }
                }
                column(id = "passed", header = "Description") {
                    Fragment.create {
                        td {
                            +(it.value.description ?: "Description not provided")
                        }
                    }
                }
                column(id = "rating", header = "Contest Rating") {
                    Fragment.create {
                        td {
                            +"0"
                        }
                    }
                }
            }
        },
        initialPageSize = 10,
        useServerPaging = false,
        usePageSelection = false,
    )

    @Suppress(
        "EMPTY_BLOCK_STRUCTURE_ERROR",
        "TOO_LONG_FUNCTION",
        "MAGIC_NUMBER",
        "LongMethod",
    )
    override fun ChildrenBuilder.render() {
        div {
            className = ClassName("row justify-content-center")
            div {
                className = ClassName("col-lg-10 mt-4 min-vh-100")
                div {
                    className = ClassName("row mb-2")
                    topLeftCard()
                    topRightCard()
                }

                projectsTable {
                    getData = { _, _ ->
                        val response = post(
                            url = "$apiUrl/projects/by-filters",
                            headers = jsonHeaders,
                            body = Json.encodeToString(ProjectFilters.created),
                            loadingHandler = ::classLoadingHandler,
                            responseHandler = ::noopResponseHandler
                        )
                        if (response.ok) {
                            response.unsafeMap {
                                it.decodeFromJsonString<Array<ProjectDto>>()
                            }
                        } else {
                            emptyArray()
                        }
                    }
                }
            }
        }
    }

    companion object : RStatics<CreationViewProps, State, CollectionView, Context<RequestStatusContext>>(CollectionView::class) {
        init {
            contextType = requestStatusContext
        }
    }
}
