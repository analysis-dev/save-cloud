@file:Suppress(
    "FILE_NAME_MATCH_CLASS",
    "FILE_WILDCARD_IMPORTS",
    "HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE",
    "TOP_LEVEL_ORDER"
)

package com.saveourtool.save.frontend.components.basic.organizations

import com.saveourtool.save.domain.Role
import com.saveourtool.save.frontend.components.basic.testsuitessources.showTestSuiteSourceCreationModal
import com.saveourtool.save.frontend.components.inputform.InputTypes
import com.saveourtool.save.frontend.components.inputform.inputTextFormRequired
import com.saveourtool.save.frontend.components.tables.TableProps
import com.saveourtool.save.frontend.components.tables.tableComponent
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.frontend.utils.loadingHandler
import com.saveourtool.save.testsuite.TestSuitesSourceDto
import com.saveourtool.save.testsuite.TestSuitesSourceDtoList
import com.saveourtool.save.testsuite.TestSuitesSourceSnapshotKey
import com.saveourtool.save.testsuite.TestSuitesSourceSnapshotKeyList

import csstype.ClassName
import react.*
import react.dom.html.ButtonType
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.td
import react.table.columns

import kotlinx.coroutines.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

typealias TestSuitesSourceWithBranch = Pair<TestSuitesSourceDto, String>

/**
 * TESTS tab in OrganizationView
 */
val organizationTestsMenu = organizationTestsMenu()

/**
 * OrganizationTestsMenu component props
 */
external interface OrganizationTestsMenuProps : Props {
    /**
     * Current organization name
     */
    var organizationName: String

    /**
     * [Role] of user that is observing this component
     */
    var selfRole: Role
}

@Suppress("TOO_LONG_FUNCTION", "LongMethod")
private fun organizationTestsMenu() = FC<OrganizationTestsMenuProps> { props ->
    val testSuitesSourceCreationWindowOpenness = useWindowOpenness()
    val (isSourceCreated, setIsSourceCreated) = useState(false)
    val (testSuitesSources, setTestSuitesSources) = useState(emptyList<TestSuitesSourceWithBranch>())
    useRequest(dependencies = arrayOf(props.organizationName, isSourceCreated)) {
        val response = get(
            url = "$apiUrl/test-suites-sources/${props.organizationName}/list",
            headers = jsonHeaders,
            loadingHandler = ::loadingHandler,
        )
        if (!response.ok) {
            setTestSuitesSources(emptyList())
            return@useRequest
        }
        response.decodeFromJsonString<TestSuitesSourceDtoList>()
            .map { testSuitesSource ->
                val defaultBranchResponse = post(
                    url = "$apiUrl/git/default-branch-name",
                    headers = jsonHeaders,
                    loadingHandler = ::loadingHandler,
                    body = Json.encodeToString(testSuitesSource.gitDto)
                )
                if (!defaultBranchResponse.ok) {
                    setTestSuitesSources(emptyList())
                    return@useRequest
                }
                testSuitesSource to defaultBranchResponse.text().await()
            }
            .let {
                setTestSuitesSources(it)
            }
    }
    val (testSuiteSourceToFetch, setTestSuiteSourceToFetch) = useState<TestSuitesSourceDto>()
    val triggerFetchTestSuiteSource = useDeferredRequest {
        testSuiteSourceToFetch?.let { testSuiteSource ->
            post(
                url = "$apiUrl/test-suites-sources/${testSuiteSource.organizationName}/${encodeURIComponent(testSuiteSource.name)}/fetch",
                headers = jsonHeaders,
                loadingHandler = ::loadingHandler,
                body = undefined
            )
        }
    }

    val (selectedTestSuitesSource, setSelectedTestSuitesSource) = useState<TestSuitesSourceDto>()
    val (testSuitesSourceSnapshotKeys, setTestSuitesSourceSnapshotKeys) = useState(emptyList<TestSuitesSourceSnapshotKey>())
    val fetchTestSuitesSourcesSnapshotKeys = useDeferredRequest {
        selectedTestSuitesSource?.let { testSuitesSource ->
            val response = get(
                url = "$apiUrl/test-suites-sources/${testSuitesSource.organizationName}/${encodeURIComponent(testSuitesSource.name)}/list-snapshot",
                headers = jsonHeaders,
                loadingHandler = ::loadingHandler,
            )
            if (response.ok) {
                response.unsafeMap {
                    it.decodeFromJsonString<TestSuitesSourceSnapshotKeyList>()
                }.let {
                    setTestSuitesSourceSnapshotKeys(it)
                }
            } else {
                setTestSuitesSourceSnapshotKeys(emptyList())
            }
        }
    }

    val (testSuitesSourceSnapshotKeyToDelete, setTestSuitesSourceSnapshotKeyToDelete) = useState<TestSuitesSourceSnapshotKey>()
    val deleteTestSuitesSourcesSnapshotKey = useDeferredRequest {
        testSuitesSourceSnapshotKeyToDelete?.let { key ->
            delete(
                url = "$apiUrl/test-suites-sources/${key.organizationName}/${encodeURIComponent(key.testSuitesSourceName)}/delete-test-suites-and-snapshot?version=${key.version}",
                headers = jsonHeaders,
                loadingHandler = ::loadingHandler,
                // TODO: body is forbidden in delete in some implementations, probably we should not support it
                body = undefined
            )
            setTestSuitesSourceSnapshotKeyToDelete(null)
        }
    }
    val selectHandler: (TestSuitesSourceDto) -> Unit = {
        if (selectedTestSuitesSource == it) {
            setSelectedTestSuitesSource(null)
        } else {
            setSelectedTestSuitesSource(it)
            fetchTestSuitesSourcesSnapshotKeys()
        }
    }
    val (versionToFetch, setVersionToFetch) = useState<String>()
    val fetchHandler: (TestSuitesSourceDto) -> Unit = {
        setTestSuiteSourceToFetch(it)
        triggerFetchTestSuiteSource()
    }
    val editHandler: (TestSuitesSourceDto) -> Unit = {
        // do nothing for now
    }
    val testSuitesSourcesTable = prepareTestSuitesSourcesTable(selectHandler, versionToFetch, setVersionToFetch::invoke, fetchHandler, editHandler)
    val deleteHandler: (TestSuitesSourceSnapshotKey) -> Unit = {
        setTestSuitesSourceSnapshotKeyToDelete(it)
        deleteTestSuitesSourcesSnapshotKey()
        setTestSuitesSourceSnapshotKeys(testSuitesSourceSnapshotKeys.filterNot(it::equals))
    }
    val testSuitesSourceSnapshotKeysTable = prepareTestSuitesSourceSnapshotKeysTable(deleteHandler)

    showTestSuiteSourceCreationModal(
        testSuitesSourceCreationWindowOpenness.isOpen(),
        props.organizationName,
        { source ->
            testSuitesSourceCreationWindowOpenness.closeWindow()
            setTestSuiteSourceToFetch(source)
            triggerFetchTestSuiteSource()
            setIsSourceCreated { !it }
        },
    ) {
        testSuitesSourceCreationWindowOpenness.closeWindow()
    }
    div {
        className = ClassName("d-flex justify-content-center mb-3")
        button {
            type = ButtonType.button
            className = ClassName("btn btn-sm btn-primary")
            disabled = !props.selfRole.hasWritePermission()
            onClick = testSuitesSourceCreationWindowOpenness.openWindowAction().withUnusedArg()
            +"+ Create test suites source"
        }
    }
    div {
        className = ClassName("mb-2")
        testSuitesSourcesTable {
            getData = { _, _ ->
                testSuitesSources.toTypedArray()
            }
            content = testSuitesSources
        }
    }

    selectedTestSuitesSource?.let {
        div {
            className = ClassName("mb-2")
            testSuitesSourceSnapshotKeysTable {
                getData = { _, _ ->
                    testSuitesSourceSnapshotKeys.toTypedArray()
                }
                content = testSuitesSourceSnapshotKeys
            }
        }
    }
}

/**
 * Extensions for [TableProps] which adds content field (where content of table is taken from external variable)
 */
external interface TablePropsWithContent<D : Any> : TableProps<D> {
    /**
     * Signal to update table
     */
    var content: List<D>
}

@Suppress(
    "MAGIC_NUMBER",
    "TYPE_ALIAS",
    "TOO_LONG_FUNCTION",
    "LongMethod",
    "LAMBDA_IS_NOT_LAST_PARAMETER",
)
private fun prepareTestSuitesSourcesTable(
    selectHandler: (TestSuitesSourceDto) -> Unit,
    version: String?,
    versionHandler: (String) -> Unit,
    fetchHandler: (TestSuitesSourceDto) -> Unit,
    editHandler: (TestSuitesSourceDto) -> Unit,
): FC<TablePropsWithContent<TestSuitesSourceWithBranch>> = tableComponent(
    columns = columns {
        column(id = "organizationName", header = "Organization", { this.first }) { cellProps ->
            Fragment.create {
                td {
                    onClick = {
                        selectHandler(cellProps.value)
                    }
                    +cellProps.value.organizationName
                }
            }
        }
        column(id = "name", header = "Name", { this.first }) { cellProps ->
            Fragment.create {
                td {
                    onClick = {
                        selectHandler(cellProps.value)
                    }
                    +cellProps.value.name
                }
            }
        }
        column(id = "description", header = "Description", { this.first }) { cellProps ->
            Fragment.create {
                td {
                    onClick = {
                        selectHandler(cellProps.value)
                    }
                    +(cellProps.value.description ?: "Description is not provided")
                }
            }
        }
        column(id = "location", header = "Git location", { this }) { cellProps ->
            Fragment.create {
                td {
                    onClick = {
                        selectHandler(cellProps.value.first)
                    }
                    a {
                        href = "${cellProps.value.first.gitDto.url}/tree/${cellProps.value.second}/${cellProps.value.first.testRootPath}"
                        +"source"
                    }
                }
            }
        }
        column(id = "fetch", header = "Fetch new version", { this.first }) { cellProps ->
            Fragment.create {
                td {
                    inputTextFormRequired {
                        form = InputTypes.SOURCE_VERSION
                        textValue = version
                        classes = "mb-2"
                        name = "Version"
                        validInput = true
                        onChangeFun = {
                            versionHandler(it.target.value)
                        }
                    }
                    button {
                        type = ButtonType.button
                        className = ClassName("btn btn-sm btn-primary")
                        onClick = {
                            fetchHandler(cellProps.value)
                        }
                        +"fetch"
                    }
                }
            }
        }
        column(id = "edit", header = "Edit", { this.first }) { cellProps ->
            Fragment.create {
                td {
                    button {
                        type = ButtonType.button
                        className = ClassName("btn btn-sm btn-primary")
                        onClick = {
                            editHandler(cellProps.value)
                        }
                        +"edit"
                    }
                }
            }
        }
    },
    initialPageSize = 10,
    useServerPaging = false,
    usePageSelection = false,
    getAdditionalDependencies = {
        arrayOf(it.content)
    },
)

@Suppress("MAGIC_NUMBER", "TYPE_ALIAS")
private fun prepareTestSuitesSourceSnapshotKeysTable(
    deleteHandler: (TestSuitesSourceSnapshotKey) -> Unit
): FC<TablePropsWithContent<TestSuitesSourceSnapshotKey>> = tableComponent(
    columns = columns {
        column(id = "version", header = "Version", { version }) { cellProps ->
            Fragment.create {
                td {
                    +cellProps.value
                }
            }
        }
        column(id = "creationTime", header = "Creation Time", { convertAndGetCreationTime() }) { cellProps ->
            Fragment.create {
                td {
                    +cellProps.value.toString()
                }
            }
        }
        column(id = "delete", header = "Delete version", { this }) { cellProps ->
            Fragment.create {
                td {
                    button {
                        type = ButtonType.button
                        className = ClassName("btn btn-sm btn-primary")
                        onClick = {
                            deleteHandler(cellProps.value)
                        }
                        +"delete"
                    }
                }
            }
        }
    },
    initialPageSize = 10,
    useServerPaging = false,
    usePageSelection = false,
    getAdditionalDependencies = {
        arrayOf(it.content)
    },
)
