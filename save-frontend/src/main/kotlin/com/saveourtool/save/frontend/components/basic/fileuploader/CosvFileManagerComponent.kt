@file:Suppress("HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE")

package com.saveourtool.save.frontend.components.basic.fileuploader

import com.saveourtool.save.entities.OrganizationDto
import com.saveourtool.save.entities.cosv.RawCosvFileDto
import com.saveourtool.save.entities.cosv.RawCosvFileDto.Companion.isDuplicate
import com.saveourtool.save.entities.cosv.RawCosvFileDto.Companion.isHasErrors
import com.saveourtool.save.entities.cosv.RawCosvFileDto.Companion.isProcessing
import com.saveourtool.save.entities.cosv.RawCosvFileDto.Companion.isUploadedJsonFile
import com.saveourtool.save.entities.cosv.RawCosvFileDto.Companion.isUploadedZipArchive
import com.saveourtool.save.entities.cosv.RawCosvFileStatisticsDto
import com.saveourtool.save.entities.cosv.RawCosvFileStatus
import com.saveourtool.save.entities.cosv.RawCosvFileStreamingResponse
import com.saveourtool.save.frontend.components.basic.selectFormRequired
import com.saveourtool.save.frontend.components.inputform.InputTypes
import com.saveourtool.save.frontend.components.inputform.dragAndDropForm
import com.saveourtool.save.frontend.externals.fontawesome.faBoxOpen
import com.saveourtool.save.frontend.externals.fontawesome.faReload
import com.saveourtool.save.frontend.externals.fontawesome.fontAwesomeIcon
import com.saveourtool.save.frontend.externals.i18next.useTranslation
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.utils.FILE_PART_NAME
import com.saveourtool.save.utils.toKilobytes
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.json.Json

private const val DEFAULT_SIZE = 10

val cosvFileManagerComponent: FC<Props> = FC {
    useTooltip()
    val (t) = useTranslation("vulnerability-upload")

    @Suppress("GENERIC_VARIABLE_WRONG_DECLARATION")
    val organizationSelectForm = selectFormRequired<String>()

    val (statistic, setStatistic) = useState(RawCosvFileStatisticsDto.empty)
    val (lastPage, setLastPage) = useState(0)
    val (availableFiles, setAvailableFiles) = useState<List<RawCosvFileDto>>(emptyList())
    val (filesForUploading, setFilesForUploading) = useState<List<File>>(emptyList())

    val leftAvailableFilesCount = statistic.allAvailableFilesCount - lastPage * DEFAULT_SIZE

    val (userOrganizations, setUserOrganizations) = useState(emptyList<OrganizationDto>())
    val (selectedOrganization, setSelectedOrganization) = useState<String>()

    val (fileToDelete, setFileToDelete) = useState<RawCosvFileDto>()
    val (fileToUnzip, setFileToUnzip) = useState<RawCosvFileDto>()

    val (currentProgress, setCurrentProgress) = useState(-1)
    val (currentProgressMessage, setCurrentProgressMessage) = useState("")
    val resetCurrentProgress = {
        setCurrentProgress(-1)
        setCurrentProgressMessage("")
    }

    val (isStreamingOperationActive, setStreamingOperationActive) = useState(false)

    val deleteFile = useDeferredRequest {
        fileToDelete?.let { file ->
            val response = delete(
                "$apiUrl/raw-cosv/$selectedOrganization/delete/${file.requiredId()}",
                headers = Headers().withAcceptJson(),
                loadingHandler = ::noopLoadingHandler,
            )

            if (response.ok) {
                setAvailableFiles { it.minus(file) }
                setStatistic { it.copy(allAvailableFilesCount = statistic.allAvailableFilesCount.dec()) }
                when {
                    file.isUploadedZipArchive() -> setStatistic { it.copy(uploadedArchivesCount = statistic.uploadedArchivesCount.dec()) }
                    file.isUploadedJsonFile() -> setStatistic { it.copy(uploadedJsonFilesCount = statistic.uploadedJsonFilesCount.dec()) }
                    file.isProcessing() -> setStatistic { it.copy(processingFilesCount = statistic.processingFilesCount.dec()) }
                    file.isDuplicate() -> setStatistic { it.copy(duplicateFilesCount = statistic.duplicateFilesCount.dec()) }
                    file.isHasErrors() -> setStatistic { it.copy(errorFilesCount = statistic.errorFilesCount.dec()) }
                }
                setFileToDelete(null)
            } else {
                window.alert("Failed to delete file due to ${response.unpackMessageOrHttpStatus()}")
            }
        }
    }

    val deleteAllDuplicatedCosvFiles = useDeferredRequest {
        val response = delete(
            url = "$apiUrl/raw-cosv/$selectedOrganization/delete-all-duplicated-files",
            jsonHeaders,
            loadingHandler = ::loadingHandler,
            responseHandler = ::noopResponseHandler
        )
        if (response.ok) {
            window.alert("All duplicated files deleted")
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

    val fetchMoreFiles = useDeferredRequest {
        selectedOrganization?.let {
            val newPage = lastPage.inc()
            val response = get(
                url = "$apiUrl/raw-cosv/$selectedOrganization/list",
                params = jso<dynamic> {
                    page = newPage - 1
                    size = DEFAULT_SIZE
                },
                headers = Headers().withAcceptNdjson().withContentTypeJson(),
                loadingHandler = ::noopLoadingHandler,
                responseHandler = ::noopResponseHandler
            )
            when {
                response.ok -> {
                    setStreamingOperationActive(true)
                    response
                        .readLines()
                        .filter(String::isNotEmpty)
                        .onCompletion {
                            setStreamingOperationActive(false)
                            setLastPage(newPage)
                        }
                        .collect { message ->
                            val uploadedFile: RawCosvFileDto = Json.decodeFromString(message)
                            setAvailableFiles { it.plus(uploadedFile) }
                            if (uploadedFile.isDuplicate()) {
                                setDuplicateFiles { it.plus(uploadedFile) }
                            }
                        }
                }
                else -> window.alert("Failed to fetch next page: ${response.unpackMessageOrNull().orEmpty()}")
            }
        }
    }

    val getStatistic = useDeferredRequest {
        selectedOrganization?.let {
            val response = get(
                url = "$apiUrl/raw-cosv/$selectedOrganization/statistics",
                jsonHeaders,
                loadingHandler = ::noopLoadingHandler,
                responseHandler = ::noopResponseHandler
            )
            when {
                response.ok -> setStatistic(response.unsafeMap { it.decodeFromJsonString<RawCosvFileStatisticsDto>() })
                else -> window.alert("Failed to get statistic data: ${response.unpackMessageOrNull().orEmpty()}")
            }
        }
    }

    val reFetchFiles = useDeferredRequest {
        selectedOrganization?.let {
            getStatistic()
            setAvailableFiles(emptyList())
            setLastPage(0)
            fetchMoreFiles()
        }
    }

    var processedBytes by useState(0L)
    val uploadFiles = useDeferredRequest {
        setStreamingOperationActive(true)
        setCurrentProgress(0)
        setCurrentProgressMessage("Initializing...")
        val totalBytes = filesForUploading.sumOf { it.size.toLong() }
        val response = post(
            url = "$apiUrl/raw-cosv/$selectedOrganization/batch-upload",
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
                    reFetchFiles()
                }
                .collect { message ->
                    val uploadedFile: RawCosvFileDto = Json.decodeFromString(message)
                    processedBytes += uploadedFile.requiredContentLength()
                    if (processedBytes == totalBytes) {
                        setCurrentProgress(((processedBytes / totalBytes) * 100).toInt())
                        setCurrentProgressMessage("${processedBytes.toKilobytes()} / ${totalBytes.toKilobytes()} KB")
                    } else {
                        setCurrentProgress(100)
                        setCurrentProgressMessage("Successfully uploaded ${totalBytes.toKilobytes()} KB.")
                    }
                }
            else -> {
                setStreamingOperationActive(false)
                resetCurrentProgress()
                window.alert(response.unpackMessageOrNull().orEmpty())
            }
        }
    }

    val unzipFile = useDeferredRequest {
        fileToUnzip?.let { file ->
            setStreamingOperationActive(true)
            val response = post(
                "$apiUrl/raw-cosv/$selectedOrganization/unzip/${file.requiredId()}",
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
                            setFileToUnzip(null)
                            reFetchFiles()
                        }
                        .collect { message ->
                            val entryResponse: RawCosvFileStreamingResponse = Json.decodeFromString(message)
                            setCurrentProgress(entryResponse.progress)
                            setCurrentProgressMessage(entryResponse.progressMessage)
                        }
                }
                else -> {
                    setStreamingOperationActive(false)
                    resetCurrentProgress()
                    window.alert("Failed to unzip ${file.fileName}: ${response.unpackMessageOrNull().orEmpty()}")
                }
            }
        }
    }

    val submitAllUploadedCosvFiles = useDeferredRequest {
        val response = post(
            url = "$apiUrl/raw-cosv/$selectedOrganization/submit-all-uploaded-to-process",
            jsonHeaders,
            body = undefined,
            loadingHandler = ::noopLoadingHandler,
            responseHandler = ::noopResponseHandler
        )
        if (response.ok) {
            reFetchFiles()
            setCurrentProgress(100)
            setCurrentProgressMessage("All uploaded files submitted to be processed")
        }
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
                reFetchFiles()
            }
        }

        ul {
            className = ClassName("list-group")

            // ===== UPLOAD FILES FIELD =====
            li {
                className = ClassName("list-group-item p-0 d-flex bg-light")
                dragAndDropForm {
                    isDisabled = selectedOrganization.isNullOrEmpty() || isStreamingOperationActive
                    isMultipleFilesSupported = true
                    tooltipMessage = "Only JSON files or ZIP archives"
                    onChangeEventHandler = { files ->
                        setFilesForUploading(files!!.asList())
                        uploadFiles()
                    }
                }
            }

            // ===== SUBMIT BUTTONS =====
            li {
                className = ClassName("list-group-item p-1 d-flex bg-light justify-content-center")
                buttonBuilder("Delete all duplicates", classes = "mr-1", isDisabled = duplicateFiles.isEmpty() || isStreamingOperationActive) {
                    if (window.confirm("Duplicated files will be removed. Do you want to continue?")) {
                        deleteAllDuplicatedCosvFiles()
                    }
                }
                buttonBuilder("Submit all uploaded", classes = "mr-1", isDisabled = statistic.uploadedJsonFilesCount == 0 || isStreamingOperationActive) {
                    if (window.confirm("Processed files will be removed. Do you want to continue?")) {
                        submitAllUploadedCosvFiles()
                    }
                }
                buttonBuilder(faReload, isDisabled = isStreamingOperationActive) {
                    reFetchFiles()
                }
            }

            // ===== STATUS BAR =====
            with(statistic) {
                if (!isStreamingOperationActive && allAvailableFilesCount > 0) {
                    li {
                        className = ClassName("list-group-item p-1 d-flex bg-light justify-content-center")

                        when {
                            uploadedJsonFilesCount > 0 && uploadedArchivesCount > 0 -> +"Uploaded $uploadedJsonFilesCount new json files and $uploadedArchivesCount archives. "
                            uploadedJsonFilesCount > 0 -> +"Uploaded $uploadedJsonFilesCount new json files. "
                            uploadedArchivesCount > 0 -> +"Uploaded $uploadedArchivesCount new archives. "
                        }

                        if (processingFilesCount > 0) {
                            +"Still processing $processingFilesCount files. "
                        }

                        when {
                            duplicateFilesCount > 0 && errorFilesCount > 0 -> +"Failed with $duplicateFilesCount duplicates, $errorFilesCount files with another errors."
                            duplicateFilesCount > 0 -> +"Failed with $duplicateFilesCount duplicates."
                            errorFilesCount > 0 -> +"Failed $errorFilesCount files with errors."
                        }
                    }
                }
            }

            // ===== PROGRESS BAR =====
            defaultProgressBarComponent {
                this.currentProgress = currentProgress
                this.currentProgressMessage = currentProgressMessage
                reset = {
                    setCurrentProgress(-1)
                    setCurrentProgressMessage("")
                }
            }

            // ===== AVAILABLE FILES =====
            availableFiles.map { file ->
                li {
                    className = ClassName("list-group-item text-left")
                    if (file.isUploadedZipArchive()) {
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

                    downloadFileButton(file, RawCosvFileDto::fileName) {
                        "$apiUrl/raw-cosv/$selectedOrganization/download/${file.requiredId()}"
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
                            if (file.isDuplicate()) {
                                "text-warning"
                            } else {
                                "text-danger"
                            }
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
                            RawCosvFileStatus.PROCESSED -> " (processed, will be deleted shortly)"
                            RawCosvFileStatus.FAILED -> if (file.isDuplicate()) " (duplicate)" else " (error)"
                            else -> " "
                        }
                    }
                }
            }

            if (leftAvailableFilesCount > 0) {
                li {
                    className = ClassName("list-group-item p-1 d-flex bg-light justify-content-center")
                    buttonBuilder("Load more (left $leftAvailableFilesCount)", isDisabled = isStreamingOperationActive) {
                        fetchMoreFiles()
                    }
                }
            }
        }
    }
}
