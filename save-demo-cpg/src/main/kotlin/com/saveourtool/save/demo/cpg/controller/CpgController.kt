package com.saveourtool.save.demo.cpg.controller

import com.saveourtool.save.configs.ApiSwaggerSupport
import com.saveourtool.save.demo.cpg.*
import com.saveourtool.save.demo.cpg.config.ConfigProperties
import com.saveourtool.save.demo.cpg.repository.CpgRepository
import com.saveourtool.save.demo.cpg.service.CpgService
import com.saveourtool.save.demo.cpg.utils.*
import com.saveourtool.save.utils.blockingToMono
import com.saveourtool.save.utils.getLogger

import arrow.core.getOrHandle
import de.fraunhofer.aisec.cpg.*
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.tags.Tags
import org.apache.commons.io.FileUtils
import org.slf4j.Logger
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

import java.nio.file.Files.createTempDirectory
import java.nio.file.Path
import java.util.*

import kotlin.io.path.*
import kotlinx.serialization.ExperimentalSerializationApi

const val FILE_NAME_SEPARATOR = "==="

/**
 * A simple controller
 * @property configProperties
 * @property cpgService
 * @property cpgRepository
 */
@ApiSwaggerSupport
@Tags(
    Tag(name = "cpg-demo"),
)
@RestController
@RequestMapping("/cpg/api")
@ExperimentalSerializationApi
class CpgController(
    val configProperties: ConfigProperties,
    val cpgService: CpgService,
    val cpgRepository: CpgRepository,
) {
    /**
     * @param request
     * @return result of uploading, it contains ID to request the result further
     */
    @PostMapping("/upload-code")
    fun uploadCode(
        @RequestBody request: CpgRunRequest,
    ): Mono<CpgResult> = blockingToMono {
        val tmpFolder = createTempDirectory(request.params.language.modeName)
        try {
            createFiles(request, tmpFolder)

            val (result, logs) = cpgService.translate(tmpFolder)
            result
                .map {
                    cpgRepository.save(it)
                }
                .map {
                    CpgResult(
                        cpgRepository.getGraph(it),
                        tmpFolder.fileName.name,
                        logs,
                    )
                }
                .getOrHandle {
                    CpgResult(
                        CpgGraph.placeholder,
                        "NONE",
                        logs + "Exception: ${it.message} ${it.stackTraceToString()}",
                    )
                }
        } finally {
            FileUtils.deleteDirectory(tmpFolder.toFile())
        }
    }

    private fun createFiles(request: CpgRunRequest, tmpFolder: Path) {
        val files: MutableList<SourceCodeFile> = mutableListOf()
        request.codeLines.filterNot { it.isBlank() }.forEachIndexed { index, line ->
            if (line.startsWith(FILE_NAME_SEPARATOR) && line.endsWith(FILE_NAME_SEPARATOR)) {
                files.add(SourceCodeFile(line.getFileName(), mutableListOf()))
            } else {
                if (index == 0) {
                    files.add(SourceCodeFile("demo${request.params.language.extension}", mutableListOf()))
                }
                files.last().lines.add(line)
            }
        }
        files.forEach {
            it.createSourceFile(tmpFolder)
        }
    }

    private fun String.getFileName() =
            this.trim()
                .drop(FILE_NAME_SEPARATOR.length)
                .dropLast(FILE_NAME_SEPARATOR.length)
                .trim()

    /**
     * @property name
     * @property lines
     */
    private data class SourceCodeFile(
        val name: String,
        val lines: MutableList<String>
    ) {
        /**
         * @param tmpFolder
         */
        fun createSourceFile(tmpFolder: Path) {
            val file = (tmpFolder / name)
            file.writeLines(lines)
            log.info("Created a file with sources: ${file.fileName}")
        }
    }

    companion object {
        private val log: Logger = getLogger<CpgController>()
    }
}
