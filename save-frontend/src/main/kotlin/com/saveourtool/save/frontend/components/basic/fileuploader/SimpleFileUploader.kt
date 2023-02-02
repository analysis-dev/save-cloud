/**
 * Component for uploading files (FileDtos)
 */

@file:Suppress("FILE_NAME_MATCH_CLASS")

package com.saveourtool.save.frontend.components.basic.fileuploader

import com.saveourtool.save.domain.*
import com.saveourtool.save.entities.FileDto
import com.saveourtool.save.frontend.externals.fontawesome.*
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.frontend.utils.noopLoadingHandler

import csstype.ClassName
import js.core.asList
import org.w3c.fetch.Headers
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.strong
import react.dom.html.ReactHTML.ul
import web.file.File
import web.html.InputType
import web.http.FormData

@Suppress(
    "TOO_LONG_FUNCTION",
    "TYPE_ALIAS",
    "LongMethod",
    "ComplexMethod",
)
val simpleFileUploader: FC<SimpleFileUploaderProps> = FC { props ->
    val (selectedFiles, setSelectedFiles) = useState<List<FileDto>>(emptyList())
    val (availableFiles, setAvailableFiles) = useState<List<FileDto>>(emptyList())

    useEffect(selectedFiles) {
        props.updateFileDtos {
            selectedFiles
        }
    }

    useRequest {
        val response = get(
            props.getUrlForDemoFilesFetch(),
            jsonHeaders,
            loadingHandler = ::noopLoadingHandler,
            responseHandler = ::noopResponseHandler,
        )
        if (response.ok) {
            setSelectedFiles(response.decodeFromJsonString<List<FileDto>>())
        }
    }

    useRequest {
        props.getUrlForAvailableFilesFetch?.invoke()?.let { url ->
            val response = get(
                url,
                jsonHeaders,
                loadingHandler = ::noopLoadingHandler,
                responseHandler = ::noopResponseHandler,
            )
            if (response.ok) {
                val presentIndices = selectedFiles.map { it.id }
                response.decodeFromJsonString<List<FileDto>>()
                    .let { fileDtos ->
                        fileDtos.filter { fileDto ->
                            fileDto.id !in presentIndices
                        }
                    }
                    .let { setAvailableFiles(it) }
            }
        }
    }

    val (filesForUploading, setFilesForUploading) = useState<List<File>>(emptyList())
    @Suppress("TOO_MANY_LINES_IN_LAMBDA")
    val uploadFiles = useDeferredRequest {
        filesForUploading.forEach { fileForUploading ->
            post(
                props.getUrlForFileUpload(),
                Headers(),
                FormData().apply {
                    append("file", fileForUploading)
                },
                loadingHandler = ::noopLoadingHandler,
            )
                .decodeFromJsonString<FileDto>()
                .let { fileDto ->
                    setSelectedFiles { files ->
                        files.plus(fileDto)
                    }
                    props.updateFileDtos { fileDtos ->
                        fileDtos.plus(fileDto)
                    }
                }
        }
    }

    div {
        ul {
            className = ClassName("list-group")

            // ===== SELECTOR =====
            li {
                className = ClassName("list-group-item d-flex justify-content-between align-items-center")
                selectorBuilder(
                    "Select a file from existing",
                    availableFiles.map { it.name }.plus("Select a file from existing"),
                    classes = "form-control custom-select"
                ) { event ->
                    val availableFile = availableFiles.first {
                        it.name == event.target.value
                    }
                    setSelectedFiles { it.plus(availableFile) }
                    setAvailableFiles { it.minus(availableFile) }
                }
            }

            // ===== UPLOAD FILES BUTTON =====
            li {
                className = ClassName("list-group-item d-flex justify-content-between align-items-center")
                label {
                    val disable = if (props.isDisabled) "disabled" else ""
                    className = ClassName("btn btn-outline-secondary m-0 $disable")
                    input {
                        type = InputType.file
                        disabled = props.isDisabled
                        multiple = true
                        hidden = true
                        onChange = { event ->
                            setFilesForUploading(event.target.files!!.asList())
                            uploadFiles()
                        }
                    }
                    fontAwesomeIcon(icon = faUpload)
                    asDynamic()["data-toggle"] = "tooltip"
                    asDynamic()["data-placement"] = "top"
                    title = "Regular files/Executable files/ZIP Archives"
                    strong { +props.buttonLabel }
                }
            }

            // ===== SELECTED FILES =====
            selectedFiles.map { file ->
                li {
                    className = ClassName("list-group-item")
                    buttonBuilder(faTrash, null) {
                        setSelectedFiles { it.minus(file) }
                        setAvailableFiles { it.plus(file) }
                    }
                    +file.name
                }
            }
        }
    }
    useTooltip()
}

typealias FileDtosSetter = ((List<FileDto>) -> List<FileDto>) -> Unit

/**
 * Props for simpleFileUploader
 */
external interface SimpleFileUploaderProps : Props {
    /**
     * Callback to get url to get available files
     */
    var getUrlForAvailableFilesFetch: (() -> String)?

    /**
     * Callback to get url to get files that are already present in demo
     */
    var getUrlForDemoFilesFetch: () -> String

    /**
     * Callback to delete file
     */
    var getUrlForFileDeletion: (FileDto) -> String

    /**
     * Callback to get url to upload file
     */
    var getUrlForFileUpload: () -> String

    /**
     * Callback to update list of selected file ids
     */
    var updateFileDtos: FileDtosSetter

    /**
     * Flag that defines if the upload button is disabled
     */
    var isDisabled: Boolean

    /**
     * Upload button label
     */
    var buttonLabel: String
}
