/**
 * View for cpg
 */

@file:Suppress("FILE_NAME_MATCH_CLASS")

package com.saveourtool.save.frontend.components.views.demo

import com.saveourtool.save.demo.cpg.CpgNodeAdditionalInfo
import com.saveourtool.save.demo.cpg.CpgResult
import com.saveourtool.save.frontend.components.basic.cardComponent
import com.saveourtool.save.frontend.components.basic.cpg.graphEvents
import com.saveourtool.save.frontend.components.basic.cpg.graphLoader
import com.saveourtool.save.frontend.components.basic.demoComponent
import com.saveourtool.save.frontend.components.modal.displaySimpleModal
import com.saveourtool.save.frontend.externals.fontawesome.faTimesCircle
import com.saveourtool.save.frontend.externals.fontawesome.fontAwesomeIcon
import com.saveourtool.save.frontend.externals.sigma.*
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.frontend.utils.loadingHandler
import com.saveourtool.save.utils.Languages

import csstype.*
import js.core.jso
import react.*
import react.dom.html.ButtonType
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.pre
import react.dom.html.ReactHTML.small
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val CPG_PLACEHOLDER_TEXT = """#include <iostream>

int main() {
    int a;
    std::cin >> a;
    std::cout << (a + a) << std::endl;
    return 0;    
}
"""

private const val NOT_PROVIDED = "NOT_PROVIDED"

private val backgroundCard = cardComponent(hasBg = false, isPaddingBottomNull = true)

@Suppress(
    "EMPTY_BLOCK_STRUCTURE_ERROR",
)
val cpgView: VFC = VFC {
    kotlinext.js.require("@react-sigma/core/lib/react-sigma.min.css")
    val (cpgResult, setCpgResult) = useState(CpgResult.empty)
    val (isLogs, setIsLogs) = useState(false)

    val (errorMessage, setErrorMessage) = useState("")
    val errorWindowOpenness = useWindowOpenness()

    val (selectedNodeName, setSelectedNodeName) = useState<String?>(null)

    displaySimpleModal(
        errorWindowOpenness,
        "Error log",
        errorMessage,
    )

    div {
        className = ClassName("d-flex justify-content-center mb-2")
        div {
            className = ClassName("col-12")
            backgroundCard {
                demoComponent {
                    this.placeholderText = CPG_PLACEHOLDER_TEXT
                    this.preselectedLanguage = Languages.CPP
                    this.resultRequest = { demoRequest ->
                        val response = post(
                            "$cpgDemoApiUrl/upload-code",
                            headers = jsonHeaders,
                            body = Json.encodeToString(demoRequest),
                            loadingHandler = ::loadingHandler,
                        )

                        if (response.ok) {
                            val cpgResultNew: CpgResult = response.unsafeMap {
                                it.decodeFromJsonString()
                            }
                            setCpgResult(cpgResultNew)
                            setIsLogs(false)
                        } else {
                            setErrorMessage(response.unpackMessage())
                            errorWindowOpenness.openWindow()
                        }
                    }
                    this.resultBuilder = { builder ->
                        with(builder) {
                            div {
                                className = ClassName("card card-body")
                                style = jso {
                                    height = "83%".unsafeCast<Height>()
                                    display = Display.block
                                }
                                val graphology = kotlinext.js.require("graphology")
                                sigmaContainer {
                                    settings = getSigmaContainerSettings()
                                    this.graph = graphology.MultiDirectedGraph
                                    graphEvents {
                                        shouldHideUnfocusedNodes = true
                                        setSelectedNode = { newSelectedNodeName ->
                                            setSelectedNodeName(
                                                newSelectedNodeName.takeIf { it != selectedNodeName }
                                            )
                                        }
                                    }
                                    graphLoader {
                                        cpgGraph = cpgResult.cpgGraph
                                    }
                                }
                                div {
                                    id = "collapse"
                                    val show = selectedNodeName?.let { nodeName ->
                                        cpgResult.cpgGraph.nodes.find { node -> node.key == nodeName }?.let { node ->
                                            displayCpgNodeAdditionalInfo(
                                                node.attributes.label,
                                                cpgResult.applicationName,
                                                node.attributes.additionalInfo,
                                            ) {
                                                setSelectedNodeName(it)
                                            }
                                        }
                                        "show"
                                    } ?: ""
                                    className = ClassName("col-6 p-0 position-absolute collapse width overflow-auto $show")
                                    style = jso {
                                        top = "0px".unsafeCast<Top>()
                                        right = "0px".unsafeCast<Right>()
                                        maxHeight = "100%".unsafeCast<MaxHeight>()
                                    }
                                }
                            }
                            div {
                                val alertStyle = if (cpgResult.applicationName.isNotBlank()) "alert-primary" else ""
                                className = ClassName("alert $alertStyle text-sm mt-3 pb-2 pt-2 mb-0")
                                +cpgResult.applicationName
                            }
                        }
                    }
                    this.changeLogsVisibility = {
                        setIsLogs { !it }
                    }
                }
            }
        }
    }
    if (isLogs) {
        div {
            val alertStyle = if (cpgResult.logs.isNotEmpty()) {
                cpgResult.logs.forEach { log ->
                    +log
                    br { }
                }

                "alert-primary"
            } else {
                +"No logs provided"

                "alert-secondary"
            }
            className = ClassName("alert $alertStyle text-sm mt-3 pb-2 pt-2 mb-0")
        }
    }
}

@Suppress("TYPE_ALIAS")
private val additionalInfoMapping: Map<String, (String, CpgNodeAdditionalInfo?) -> String?> = mapOf(
    "Code" to { _, info -> info?.code },
    "File" to { applicationName, info -> info?.file?.formatPathToFile(applicationName, "demo") },
    "Comment" to { _, info -> info?.comment },
    "Argument index" to { _, info -> info?.argumentIndex?.toString() },
    "isImplicit" to { _, info -> info?.isImplicit?.toString() },
    "isInferred" to { _, info -> info?.isInferred?.toString() },
    "Location" to { _, info -> info?.location },
)

@Suppress("TOO_LONG_FUNCTION", "LongMethod")
private fun ChildrenBuilder.displayCpgNodeAdditionalInfo(
    nodeName: String?,
    applicationName: String,
    additionalInfo: CpgNodeAdditionalInfo?,
    setSelectedNodeName: (String?) -> Unit,
) {
    div {
        className = ClassName("card card-body p-0")
        table {
            thead {
                tr {
                    className = ClassName("bg-dark text-light")
                    th {
                        scope = "col"
                        +"Name"
                    }
                    th {
                        scope = "col"
                        +(nodeName ?: NOT_PROVIDED).formatPathToFile(applicationName)
                    }
                    button {
                        className = ClassName("btn p-0 position-absolute")
                        fontAwesomeIcon(faTimesCircle)
                        type = "button".unsafeCast<ButtonType>()
                        onClick = { setSelectedNodeName(null) }
                        style = jso {
                            top = "1%".unsafeCast<Top>()
                            right = "1%".unsafeCast<Right>()
                        }
                    }
                }
            }
            tbody {
                additionalInfoMapping.map { (label, valueGetter) ->
                    label to (valueGetter(applicationName, additionalInfo) ?: NOT_PROVIDED)
                }
                    .forEachIndexed { index, (label, value) ->
                        tr {
                            if (index % 2 == 1) {
                                className = ClassName("bg-light")
                            }
                            td {
                                small {
                                    +label
                                }
                            }
                            td {
                                pre {
                                    className = ClassName("m-0")
                                    style = jso {
                                        fontSize = FontSize.small
                                    }
                                    +value
                                }
                            }
                        }
                    }
            }
        }
    }
}

private fun String.formatPathToFile(
    applicationName: String,
    missingDelimiterValue: String? = null,
) = missingDelimiterValue?.let {
    substringAfterLast("$applicationName/", missingDelimiterValue)
} ?: substringAfterLast("$applicationName/")
