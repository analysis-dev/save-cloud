@file:Suppress("HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE")

package com.saveourtool.save.frontend.components.basic.fileuploader

import com.saveourtool.save.entities.OrganizationDto
import com.saveourtool.save.entities.cosv.RawCosvFileDto
import com.saveourtool.save.entities.cosv.RawCosvFileStatus
import com.saveourtool.save.entities.cosv.UnzipRawCosvFileResponse
import com.saveourtool.save.frontend.components.basic.selectFormRequired
import com.saveourtool.save.frontend.components.inputform.InputTypes
import com.saveourtool.save.frontend.components.inputform.dragAndDropForm
import com.saveourtool.save.frontend.externals.fontawesome.faBoxOpen
import com.saveourtool.save.frontend.externals.fontawesome.faReload
import com.saveourtool.save.frontend.externals.fontawesome.fontAwesomeIcon
import com.saveourtool.save.frontend.externals.i18next.useTranslation
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.utils.ARCHIVE_EXTENSION
import com.saveourtool.save.utils.FILE_PART_NAME
import com.saveourtool.save.validation.isValidName

import js.core.asList
import js.core.jso
import org.w3c.fetch.Headers
import react.FC
import react.Props
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.ul
import react.useState
import web.cssom.ClassName
import web.cssom.Cursor
import web.file.File
import web.html.ButtonType
import web.html.InputType
import web.http.FormData

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.json.Json

val cosvFileManagerComponent: FC<Props> = FC { _ ->
    useTooltip()
    val (t) = useTranslation("vulnerability-upload")

    @Suppress("GENERIC_VARIABLE_WRONG_DECLARATION")
    val organizationSelectForm = selectFormRequired<String>()

    val (availableFiles, setAvailableFiles) = useState<List<RawCosvFileDto>>(emptyList())
    val (selectedFiles, setSelectedFiles) = useState<List<RawCosvFileDto>>(emptyList())
    val (filesForUploading, setFilesForUploading) = useState<List<File>>(emptyList())

    val (userOrganizations, setUserOrganizations) = useState(emptyList<OrganizationDto>())
    val (selectedOrganization, setSelectedOrganization) = useState<String>()

    val (fileToDelete, setFileToDelete) = useState<RawCosvFileDto>()
    val (fileToUnzip, setFileToUnzip) = useState<RawCosvFileDto>()

    val (processedBytes, setProcessedBytes) = useState(0L)
    val (totalBytes, setTotalBytes) = useState(0L)

    val (isStreamingOperationActive, setStreamingOperationActive) = useState(false)

    val deleteFile = useDeferredRequest {
        fileToDelete?.let { file ->
            val response = delete(
                "$apiUrl/cosv/$selectedOrganization/delete/${file.requiredId()}",
                jsonHeaders,
                loadingHandler = ::loadingHandler,
            )

            if (response.ok) {
                setAvailableFiles { it.minus(file) }
                setFileToDelete(null)
            } else {
                window.alert("Failed to delete file due to ${response.unpackMessageOrHttpStatus()}")
            }
        }
    }

    val deleteProcessedFiles = useDeferredRequest {
        val response = delete(
            "$apiUrl/cosv/$selectedOrganization/delete-processed",
            jsonHeaders,
            loadingHandler = ::loadingHandler,
        )

        if (response.ok) {
            val deletedFiles: Set<RawCosvFileDto> = response.decodeFromJsonString()
            setAvailableFiles { it.minus(deletedFiles) }
        } else {
            window.alert("Failed to delete processed files due to ${response.unpackMessageOrHttpStatus()}")
        }
    }

    useRequest {
        val organizations = get(
            url = "$apiUrl/organizations/with-allow-bulk-upload",
            headers = jsonHeaders,
            loadingHandler = ::loadingHandler,
            responseHandler = ::noopResponseHandler,
        )
            .unsafeMap {
                it.decodeFromJsonString<List<OrganizationDto>>()
            }

        setUserOrganizations(organizations)
    }

    val fetchFiles = useDeferredRequest {
        selectedOrganization?.let {
            val result: List<RawCosvFileDto> = get(
                url = "$apiUrl/cosv/$selectedOrganization/list",
                jsonHeaders,
                loadingHandler = ::loadingHandler,
                responseHandler = ::noopResponseHandler
            ).decodeFromJsonString()
            setAvailableFiles(result)
        }
    }

    val uploadFiles = useDeferredRequest {
        setStreamingOperationActive(true)
        val response = post(
            url = "$apiUrl/cosv/$selectedOrganization/batch-upload",
            headers = Headers().withAcceptNdjson(),
            body = FormData().apply { filesForUploading.forEach { append(FILE_PART_NAME, it) } },
            loadingHandler = ::noopLoadingHandler,
            responseHandler = ::noopResponseHandler,
        )
        when {
            response.ok -> response
                .readLines()
                .filter(String::isNotEmpty)
                .onCompletion {
                    setStreamingOperationActive(false)
                }
                .collect { message ->
                    val uploadedFile: RawCosvFileDto = Json.decodeFromString(message)
                    setProcessedBytes { it.plus(uploadedFile.requiredContentLength()) }
                    setAvailableFiles { it.plus(uploadedFile) }
                }
            else -> window.alert(response.unpackMessageOrNull().orEmpty())
        }
    }

    val unzipFile = useDeferredRequest {
        fileToUnzip?.let { file ->
            setStreamingOperationActive(true)
            val response = post(
                "$apiUrl/cosv/$selectedOrganization/unzip/${file.requiredId()}",
                headers = Headers().withContentTypeJson().withAcceptNdjson(),
                body = undefined,
                loadingHandler = ::noopLoadingHandler,
                responseHandler = ::noopResponseHandler,
            )

            when {
                response.ok -> {
                    setAvailableFiles {
                        it.minus(file)
                    }
                    response
                        .readLines()
                        .filter(String::isNotEmpty)
                        .onCompletion {
                            setStreamingOperationActive(false)
                        }
                        .collect { message ->
                            val entryResponse: UnzipRawCosvFileResponse = Json.decodeFromString(message)
                            entryResponse.result?.let { result ->
                                setAvailableFiles {
                                    it.plus(result)
                                }
                            }
                            if (entryResponse.updateCounters) {
                                setTotalBytes(entryResponse.fullSize)
                                setProcessedBytes(entryResponse.processedSize)
                            } else {
                                setProcessedBytes { it + entryResponse.processedSize }
                            }
                        }
                }
                else -> window.alert(response.unpackMessageOrNull().orEmpty())
            }
            if (response.ok) {
                setFileToUnzip(null)
            }
        }
    }

    val submitCosvFiles = useDeferredRequest {
        val response = post(
            url = "$apiUrl/cosv/$selectedOrganization/submit-to-process",
            jsonHeaders,
            body = selectedFiles.map { it.requiredId() },
            loadingHandler = ::loadingHandler,
            responseHandler = ::noopResponseHandler
        )
        if (response.ok) {
            window.alert(response.text().await())
        }
        setSelectedFiles(emptyList())
    }

    val submitAllUploadedCosvFiles = useDeferredRequest {
        val response = post(
            url = "$apiUrl/cosv/$selectedOrganization/submit-all-uploaded-to-process",
            jsonHeaders,
            body = undefined,
            loadingHandler = ::loadingHandler,
            responseHandler = ::noopResponseHandler
        )
        if (response.ok) {
            window.alert(response.text().await())
        }
        setSelectedFiles(emptyList())
    }

    div {
        if (selectedOrganization.isNullOrEmpty()) {
            div {
                className = ClassName("mx-auto")
                b {
                    +"${"Organization that has permission".t()}!"
                }
            }
        }

        organizationSelectForm {
            selectClasses = "custom-select"
            formType = InputTypes.ORGANIZATION_NAME
            validInput = !selectedOrganization.isNullOrEmpty() && selectedOrganization.isValidName()
            classes = "mb-3"
            formName = "Organization"
            getData = { userOrganizations.map { it.name } }
            dataToString = { it }
            selectedValue = selectedOrganization.orEmpty()
            disabled = false
            onChangeFun = { value ->
                setSelectedOrganization(value)
                fetchFiles()
            }
        }

        ul {
            className = ClassName("list-group")

            // SUBMIT to process
            li {
                className = ClassName("list-group-item p-0 d-flex bg-light justify-content-center")
                buttonBuilder("Delete all processed", isDisabled = availableFiles.none { it.status == RawCosvFileStatus.PROCESSED }) {
                    deleteProcessedFiles()
                }
                buttonBuilder("Submit", isDisabled = selectedFiles.isEmpty() || isStreamingOperationActive) {
                    submitCosvFiles()
                }
                buttonBuilder("Submit all uploaded by you", isDisabled = availableFiles.isEmpty()) {
                    submitAllUploadedCosvFiles()
                }
                buttonBuilder(faReload) {
                    fetchFiles()
                }
            }

            // ===== UPLOAD FILES BUTTON =====
            li {
                className = ClassName("list-group-item p-0 d-flex bg-light")
                dragAndDropForm {
                    isDisabled = selectedOrganization.isNullOrEmpty() || isStreamingOperationActive
                    isMultipleFilesSupported = true
                    tooltipMessage = "Only JSON files or ZIP archives"
                    onChangeEventHandler = { files ->
                        files!!.asList()
                            .also { setTotalBytes(it.sumOf(File::size).toLong()) }
                            .let { setFilesForUploading(it) }
                        uploadFiles()
                    }
                }
            }
            progressBarComponent {
                current = processedBytes
                total = totalBytes
                flushCounters = {
                    setTotalBytes(0)
                    setProcessedBytes(0)
                }
            }

            // ===== SELECTED FILES =====
            availableFiles.map { file ->
                li {
                    className = ClassName("list-group-item text-left")
                    input {
                        className = ClassName("mx-auto")
                        type = InputType.checkbox
                        id = "checkbox"
                        checked = file in selectedFiles
                        disabled = file.isNotSelectable()
                        onChange = { event ->
                            if (event.target.checked) {
                                setSelectedFiles { it.plus(file) }
                            } else {
                                setSelectedFiles { it.minus(file) }
                            }
                        }
                    }
                    downloadFileButton(file, RawCosvFileDto::fileName) {
                        "$apiUrl/cosv/$selectedOrganization/download/${file.requiredId()}"
                    }
                    if (file.fileName.endsWith(ARCHIVE_EXTENSION, ignoreCase = true)) {
                        button {
                            type = ButtonType.button
                            className = ClassName("btn")
                            fontAwesomeIcon(icon = faBoxOpen)
                            disabled = isStreamingOperationActive
                            onClick = {
                                val confirm = window.confirm(
                                    "Are you sure you want to unzip and then remove ${file.fileName} file?"
                                )
                                if (confirm) {
                                    setFileToUnzip(file)
                                    unzipFile()
                                }
                            }
                        }
                    }
                    deleteFileButton(file, RawCosvFileDto::fileName) {
                        setFileToDelete(it)
                        deleteFile()
                    }

                    +"${file.fileName} "
                    span {
                        style = jso {
                            cursor = "pointer".unsafeCast<Cursor>()
                        }
                        val textColor = if (file.status == RawCosvFileStatus.FAILED) {
                            "text-danger"
                        } else {
                            "text-gray-400"
                        }
                        className = ClassName("$textColor text-justify")
                        file.statusMessage?.let { statusMessage ->
                            onClick = {
                                window.alert(statusMessage)
                            }
                        }
                        +when (file.status) {
                            RawCosvFileStatus.IN_PROGRESS -> " (in progress)"
                            RawCosvFileStatus.PROCESSED -> " (processed, will be deleted after ${file.updateDate?.date})"
                            RawCosvFileStatus.FAILED -> " (with errors)"
                            else -> " "
                        }
                    }
                }
            }
        }
    }
}

private fun RawCosvFileDto.isNotSelectable() = status in setOf(RawCosvFileStatus.PROCESSED, RawCosvFileStatus.IN_PROGRESS)
