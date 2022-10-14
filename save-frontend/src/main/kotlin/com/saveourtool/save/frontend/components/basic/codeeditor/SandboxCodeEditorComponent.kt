@file:Suppress("FILE_NAME_MATCH_CLASS", "HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE")

package com.saveourtool.save.frontend.components.basic.codeeditor

import com.saveourtool.save.frontend.components.basic.codeeditor.FileType.CODE
import com.saveourtool.save.frontend.components.basic.codeeditor.FileType.Companion.getTypedOption
import com.saveourtool.save.frontend.components.basic.codeeditor.FileType.SAVE_TOML
import com.saveourtool.save.frontend.components.basic.codeeditor.FileType.SETUP_SH
import com.saveourtool.save.frontend.components.views.sandboxApiUrl
import com.saveourtool.save.frontend.externals.reactace.AceModes
import com.saveourtool.save.frontend.externals.reactace.AceThemes
import com.saveourtool.save.frontend.utils.*

import csstype.ClassName
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h6
import react.useState

import kotlinx.browser.window
import kotlinx.coroutines.await

private const val DEFAULT_EDITOR_MESSAGE = "Select one of the files above to start editing it!"

private const val TEXT_PLACEHOLDER = "Please load data from server using button above."

/**
 * CodeEditor component for sandbox that encapsulates toolbar and oen editor for three different files.
 */
val sandboxCodeEditorComponent = sandboxCodeEditorComponent()

/**
 * SandboxCodeEditor functional component [Props]
 */
external interface SandboxCodeEditorComponentProps : Props {
    /**
     * Title of an editor
     */
    var editorTitle: String?

    /**
     * Action to run execution
     */
    var doRunExecution: () -> Unit

    /**
     * Action to reload debug info
     */
    var doResultReload: () -> Unit
}

private suspend fun WithRequestStatusContext.postTextRequest(
    urlPart: String,
    content: String,
    fileName: String,
) = post(
    url = "$sandboxApiUrl/upload-$urlPart-as-text?fileName=$fileName",
    headers = jsonHeaders,
    body = content,
    loadingHandler = ::noopLoadingHandler,
).ok

private suspend fun WithRequestStatusContext.getTextRequest(
    urlPart: String,
    fileName: String,
) = get(
    url = "$sandboxApiUrl/download-$urlPart-as-text?fileName=$fileName",
    headers = jsonHeaders,
    loadingHandler = ::noopLoadingHandler,
)
    .let { response ->
        if (response.ok) {
            response.text().await()
        } else {
            null
        }
    }

@Suppress("TOO_LONG_FUNCTION", "LongMethod", "ComplexMethod")
private fun sandboxCodeEditorComponent() = FC<SandboxCodeEditorComponentProps> { props ->
    val (selectedMode, setSelectedMode) = useState(AceModes.KOTLIN)
    val (selectedTheme, setSelectedTheme) = useState(AceThemes.CHROME)
    val (selectedFileType, setSelectedFileType) = useState<FileType?>(null)

    val (draftCodeText, setDraftCodeText) = useState(TEXT_PLACEHOLDER)
    val (draftConfigText, setDraftConfigText) = useState(TEXT_PLACEHOLDER)
    val (draftSetupShText, setDraftSetupShText) = useState(TEXT_PLACEHOLDER)

    val (savedCodeText, setSavedCodeText) = useState(TEXT_PLACEHOLDER)
    val (savedConfigText, setSavedConfigText) = useState(TEXT_PLACEHOLDER)
    val (savedSetupShText, setSavedSetupShText) = useState(TEXT_PLACEHOLDER)

    val fetchTexts = useDeferredRequest {
        FileType.values().forEach { fileType ->
            val (urlPart, fileName) = getTypedOption(fileType, "test" to "test", "test" to "save.toml", "file" to "setup.sh")
            val text = getTextRequest(urlPart, fileName) ?: TEXT_PLACEHOLDER
            getTypedOption(fileType, setSavedCodeText, setSavedConfigText, setSavedSetupShText)(text)
            getTypedOption(fileType, setDraftCodeText, setDraftConfigText, setDraftSetupShText)(text)
        }
    }

    val fetchText = useDeferredRequest {
        selectedFileType?.let { fileType ->
            val (urlPart, fileName) = getTypedOption(fileType, "test" to "test", "test" to "save.toml", "file" to "setup.sh")
            val text = getTextRequest(urlPart, fileName) ?: TEXT_PLACEHOLDER
            getTypedOption(fileType, setSavedCodeText, setSavedConfigText, setSavedSetupShText)(text)
            getTypedOption(fileType, setDraftCodeText, setDraftConfigText, setDraftSetupShText)(text)
        }
    }

    val uploadText = useDeferredRequest {
        selectedFileType?.let { fileType ->
            val (urlPart, content, fileName) = getTypedOption(
                fileType,
                Triple("test", draftCodeText, "test"),
                Triple("test", draftConfigText, "save.toml"),
                Triple("file", draftSetupShText, "setup.sh"),
            )
            if (postTextRequest(urlPart, content, fileName)) {
                getTypedOption(fileType, setSavedCodeText, setSavedConfigText, setSavedSetupShText)(content)
            }
        }
    }

    val uploadTexts = useDeferredRequest {
        FileType.values().forEach { fileType ->
            val (urlPart, content, fileName) = getTypedOption(
                fileType,
                Triple("test", draftCodeText, "test"),
                Triple("test", draftConfigText, "save.toml"),
                Triple("file", draftSetupShText, "setup.sh"),
            )
            if (postTextRequest(urlPart, content, fileName)) {
                getTypedOption(fileType, setSavedCodeText, setSavedConfigText, setSavedSetupShText)(content)
            }
        }
    }

    useOnce(fetchTexts)

    div {
        props.editorTitle?.let { editorTitle ->
            h6 {
                className = ClassName("text-center text-primary")
                +editorTitle
            }
        }
        val hasUncommittedChanges = mapOf(
            CODE to (savedCodeText != draftCodeText),
            SAVE_TOML to (savedConfigText != draftConfigText),
            SETUP_SH to (savedSetupShText != draftSetupShText),
        )
        displayCodeEditorToolbar(
            selectedMode,
            selectedTheme,
            selectedFileType,
            hasUncommittedChanges,
            { newModeName -> setSelectedMode(AceModes.values().find { it.modeName == newModeName }!!) },
            { newThemeName -> setSelectedTheme(AceThemes.values().find { it.themeName == newThemeName }!!) },
            onUploadChanges = uploadText,
            onReloadChanges = fetchText,
            onResultReload = props.doResultReload,
            onRunExecution = {
                val hasAnyUncommittedChanges = hasUncommittedChanges.any { (_, hasChanges) ->
                    hasChanges
                }
                if (hasAnyUncommittedChanges) {
                    if (window.confirm("Some changes are not saved. Save and run execution?")) {
                        uploadTexts()
                        props.doRunExecution()
                        window.alert("Successfully saved and started execution.")
                    } else {
                        window.alert("Run canceled.")
                    }
                } else {
                    uploadTexts()
                    props.doRunExecution()
                    window.alert("Successfully started execution.")
                }
            },
        ) { fileType ->
            if (fileType == selectedFileType) {
                setSelectedFileType(null)
            } else {
                setSelectedFileType(fileType)
            }
        }

        codeEditorComponent {
            editorTitle = props.editorTitle
            isDisabled = selectedFileType == null
            this.selectedTheme = selectedTheme
            this.selectedMode = selectedMode
            onDraftTextUpdate = { text -> getTypedOption(selectedFileType, setDraftCodeText, setDraftConfigText, setDraftSetupShText)?.invoke(text) }
            savedText = getTypedOption(selectedFileType, savedCodeText, savedConfigText, savedSetupShText) ?: DEFAULT_EDITOR_MESSAGE
            draftText = getTypedOption(selectedFileType, draftCodeText, draftConfigText, draftSetupShText) ?: DEFAULT_EDITOR_MESSAGE
        }
    }
}
