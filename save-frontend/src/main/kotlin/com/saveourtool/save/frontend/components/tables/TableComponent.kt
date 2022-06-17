/**
 * Utilities for react-tables
 */

@file:Suppress("FILE_NAME_MATCH_CLASS", "MatchingDeclarationName")

package com.saveourtool.save.frontend.components.tables

import com.saveourtool.save.frontend.components.modal.errorModal
import com.saveourtool.save.frontend.components.requestStatusContext
import com.saveourtool.save.frontend.http.HttpStatusException
import com.saveourtool.save.frontend.utils.WithRequestStatusContext
import com.saveourtool.save.frontend.utils.spread

import org.w3c.fetch.Response
import react.Props
import react.RBuilder
import react.dom.RDOMBuilder
import react.dom.div
import react.dom.em
import react.dom.h6
import react.dom.span
import react.dom.table
import react.dom.tbody
import react.dom.th
import react.dom.thead
import react.dom.tr
import react.fc
import react.table.Column
import react.table.PluginHook
import react.table.Row
import react.table.TableInstance
import react.table.TableOptions
import react.table.TableRowProps
import react.table.usePagination
import react.table.useSortBy
import react.table.useTable
import react.useContext
import react.useEffect
import react.useMemo
import react.useState

import kotlin.js.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.html.THEAD
import kotlinx.js.jso

/**
 * [Props] of a data table
 */
external interface TableProps<D : Any> : Props {
    /**
     * Table header
     */
    var tableHeader: String

    /**
     * Lambda to get table data
     */
    @Suppress("TYPE_ALIAS")
    var getData: suspend WithRequestStatusContext.(pageIndex: Int, pageSize: Int) -> Array<out D>

    /**
     * Lambda to update number of pages
     */
    var getPageCount: (suspend (pageSize: Int) -> Int)?
}

/**
 * A `RComponent` for a data table
 *
 * @param columns columns as an array of [Column]
 * @param initialPageSize initial size of table page
 * @param getRowProps a function returning `TableRowProps` for customization of table row, defaults to empty
 * @param useServerPaging whether data is split into pages server-side or in browser
 * @param usePageSelection whether to display entries settings
 * @param plugins
 * @param additionalOptions
 * @param renderExpandedRow how to render an expanded row if `useExpanded` plugin is used
 * @param commonHeader (optional) a common header for the table, which will be placed above individual column headers
 * @return a functional react component
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress(
    "TOO_LONG_FUNCTION",
    "TOO_MANY_PARAMETERS",
    "TYPE_ALIAS",
    "ComplexMethod",
    "ForbiddenComment",
    "LongMethod",
    "LongParameterList",
    "TooGenericExceptionCaught"
)
fun <D : Any> tableComponent(
    columns: Array<out Column<D, *>>,
    initialPageSize: Int = 10,
    useServerPaging: Boolean = false,
    usePageSelection: Boolean = false,
    plugins: Array<PluginHook<D>> = arrayOf(useSortBy, usePagination),
    additionalOptions: TableOptions<D>.() -> Unit = {},
    getRowProps: ((Row<D>) -> TableRowProps) = { jso() },
    renderExpandedRow: (RBuilder.(table: TableInstance<D>, row: Row<D>) -> Unit)? = undefined,
    commonHeader: RDOMBuilder<THEAD>.(table: TableInstance<D>) -> Unit = {},
) = fc<TableProps<D>> { props ->
    require(useServerPaging xor (props.getPageCount == null)) {
        "Either use client-side paging or provide a function to get page count"
    }

    val (data, setData) = useState<Array<out D>>(emptyArray())
    val (pageCount, setPageCount) = useState(1)
    val (pageIndex, setPageIndex) = useState(0)
    val (isModalOpen, setIsModalOpen) = useState(false)
    val (dataAccessException, setDataAccessException) = useState<Exception?>(null)
    val scope = CoroutineScope(Dispatchers.Default)

    val tableInstance: TableInstance<D> = useTable(options = jso {
        this.columns = useMemo { columns }
        this.data = data
        this.manualPagination = useServerPaging
        if (useServerPaging) {
            this.pageCount = pageCount
        }
        this.initialState = jso {
            this.pageSize = initialPageSize
            this.pageIndex = pageIndex
        }
        additionalOptions()
    }, plugins = plugins)

    useEffect(tableInstance.state.pageSize) {
        if (useServerPaging) {
            scope.launch {
                val newPageCount = props.getPageCount!!.invoke(tableInstance.state.pageSize)
                if (newPageCount != pageCount) {
                    setPageCount(newPageCount)
                }
            }
        }
    }

    // list of entities, updates of which will cause update of the data retrieving effect
    val dependencies: Array<dynamic> = if (useServerPaging) {
        arrayOf(tableInstance.state.pageIndex, tableInstance.state.pageSize, pageCount)
    } else {
        // when all data is already available, we don't need to repeat `getData` calls
        emptyArray()
    }
    val statusContext = useContext(requestStatusContext)
    val context = object : WithRequestStatusContext {
        override val coroutineScope = CoroutineScope(Dispatchers.Default)
        override fun setResponse(response: Response) = statusContext.setResponse(response)
        override fun setLoadingCounter(transform: (oldValue: Int) -> Int) = statusContext.setLoadingCounter(transform)
    }
    useEffect(*dependencies) {
        scope.launch {
            try {
                setData(context.(props.getData)(tableInstance.state.pageIndex, tableInstance.state.pageSize))
            } catch (e: CancellationException) {
                // this means, that view is re-rendering while network request was still in progress
                // no need to display an error message in this case
            } catch (e: HttpStatusException) {
                // this is a normal situation which should be handled by responseHandler in `getData` itself.
                // no need to display an error message in this case
            } catch (e: Exception) {
                // other exceptions are not handled by `responseHandler` and should be displayed separately
                setIsModalOpen(true)
                setDataAccessException(e)
            }
        }
        cleanup {
            if (scope.isActive) {
                scope.cancel()
            }
        }
    }

    div("card shadow mb-4") {
        div("card-header py-3") {
            h6("m-0 font-weight-bold text-primary text-center") {
                +props.tableHeader
            }
        }
        div("card-body") {
            div("table-responsive") {
                table("table table-bordered") {
                    spread(tableInstance.getTableProps())
                    attrs["width"] = "100%"
                    attrs["cellSpacing"] = "0"
                    thead {
                        commonHeader(tableInstance)
                        tableInstance.headerGroups.map { headerGroup ->
                            tr {
                                spread(headerGroup.getHeaderGroupProps())
                                headerGroup.headers.map { column ->
                                    val columnProps = column.getHeaderProps(column.getSortByToggleProps())
                                    val className = if (column.canSort) columnProps.className.unsafeCast<String?>() else ""
                                    th(classes = className) {
                                        +column.render("Header")
                                        // fixme: find a way to set `canSort`; now it's always true
                                        if (column.canSort) {
                                            spread(columnProps)
                                            span {
                                                +when {
                                                    column.isSorted -> " 🔽"
                                                    column.isSortedDesc -> " 🔼"
                                                    else -> ""
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    tbody {
                        spread(tableInstance.getTableBodyProps())
                        tableInstance.page.map { row ->
                            tableInstance.prepareRow(row)
                            tr {
                                spread(row.getRowProps(getRowProps(row)))
                                row.cells.map { cell ->
                                    // fixme: userProps are not present in actual html, but .render("Cell") produces td, so can't wrap
                                    child(cell.render("Cell", userProps = json().apply {
                                        spread(cell.getCellProps()) { key, value ->
                                            this[key] = value
                                        }
                                    }))
                                }
                            }
                            if (row.isExpanded) {
                                requireNotNull(renderExpandedRow) {
                                    "`useExpanded` is used, but no method for expanded row is provided"
                                }
                                renderExpandedRow.invoke(this@tbody, tableInstance, row)
                            }
                        }
                    }
                }
                // if (tableInstance.pageCount > 1) {
                // block with paging controls

                if (data.isEmpty()) {
                    div("align-items-center justify-content-center mb-4") {
                        h6("m-0 font-weight-bold text-primary text-center") {
                            +"No results found"
                        }
                    }
                }

                div("wrapper container m-0 p-0") {
                    pagingControl(tableInstance, setPageIndex, pageIndex, pageCount)

                    div("row ml-1") {
                        +"Page "
                        em {
                            +"${tableInstance.state.pageIndex + 1} of ${tableInstance.pageCount}"
                        }
                    }
                }

                // }
            }
        }
    }
    errorModal(
        "Error",
        "Error when fetching data: ${dataAccessException?.message}",
        {
            attrs.isOpen = isModalOpen
        }) {
        setIsModalOpen(false)
    }
}
