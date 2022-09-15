package com.saveourtool.save.backend.controllers

import com.saveourtool.save.agent.TestExecutionDto
import com.saveourtool.save.backend.ByteBufferFluxResponse
import com.saveourtool.save.backend.configs.ApiSwaggerSupport
import com.saveourtool.save.backend.repository.AgentRepository
import com.saveourtool.save.backend.service.ExecutionService
import com.saveourtool.save.backend.service.OrganizationService
import com.saveourtool.save.backend.service.ProjectService
import com.saveourtool.save.backend.service.UserDetailsService
import com.saveourtool.save.backend.storage.*
import com.saveourtool.save.backend.utils.mapToInputStream
import com.saveourtool.save.domain.*
import com.saveourtool.save.from
import com.saveourtool.save.permission.Permission
import com.saveourtool.save.utils.*
import com.saveourtool.save.v1
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.tags.Tags
import org.apache.commons.io.IOUtils

import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.security.core.Authentication
import org.springframework.util.FileSystemUtils
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import reactor.kotlin.core.publisher.toMono

import java.io.FileNotFoundException
import java.nio.ByteBuffer
import kotlin.io.path.deleteExisting
import kotlin.io.path.div
import kotlin.io.path.outputStream

/**
 * A Spring controller for file downloading
 */
@RestController
@ApiSwaggerSupport
@Tags(
    Tag(name = "files"),
)
@Suppress("LongParameterList")
class DownloadFilesController(
    private val fileStorage: FileStorage,
    private val avatarStorage: AvatarStorage,
    private val debugInfoStorage: DebugInfoStorage,
    private val executionInfoStorage: ExecutionInfoStorage,
    private val agentRepository: AgentRepository,
    private val organizationService: OrganizationService,
    private val userDetailsService: UserDetailsService,
    private val projectService: ProjectService,
    private val executionService: ExecutionService,
) {
    private val logger = LoggerFactory.getLogger(DownloadFilesController::class.java)

    /**
     * @param organizationName
     * @param projectName
     * @param authentication
     * @return a list of files in [fileStorage]
     */
    @GetMapping(path = ["/api/$v1/files/{organizationName}/{projectName}/list"])
    @Suppress("UnsafeCallOnNullableType")
    fun list(
        @PathVariable organizationName: String,
        @PathVariable projectName: String,
        authentication: Authentication,
    ): Flux<FileInfo> = projectService.findWithPermissionByNameAndOrganization(
        authentication, projectName, organizationName, Permission.READ
    )
        .toFlux()
        .flatMap {
            fileStorage.getFileInfoList(ProjectCoordinates(organizationName, projectName))
        }

    /**
     * @param organizationName
     * @param projectName
     * @param creationTimestamp
     * @param authentication
     * @return [Mono] with response
     */
    @DeleteMapping(path = ["/api/$v1/files/{organizationName}/{projectName}/{creationTimestamp}"])
    @Suppress("UnsafeCallOnNullableType")
    fun delete(
        @PathVariable organizationName: String,
        @PathVariable projectName: String,
        @PathVariable creationTimestamp: String,
        authentication: Authentication,
    ) = projectService.findWithPermissionByNameAndOrganization(
        authentication, projectName, organizationName, Permission.DELETE
    ).flatMap {
        fileStorage.deleteByUploadedMillis(ProjectCoordinates(organizationName, projectName), creationTimestamp.toLong())
    }.map { deleted ->
        if (deleted) {
            ResponseEntity.ok("File deleted successfully")
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("File not found by creationTimestamp $creationTimestamp in $organizationName/$projectName")
        }
    }

    /**
     * @param fileInfo a FileInfo based on which a file should be located
     * @param organizationName
     * @param projectName
     * @return [Mono] with file contents
     */
    @PostMapping(path = ["/api/$v1/files/{organizationName}/{projectName}/download"], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun download(
        @RequestBody fileInfo: FileInfo,
        @PathVariable organizationName: String,
        @PathVariable projectName: String,
    ): Mono<ByteBufferFluxResponse> = downloadByFileKey(fileInfo.toStorageKey(ProjectCoordinates(organizationName, projectName)))

    /**
     * @param fileKey a key [FileKey] of requested file
     * @return [Mono] with file contents
     */
    @PostMapping(path = ["/internal/files/download"], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun downloadByFileKey(
        @RequestBody fileKey: FileKey,
    ): Mono<ByteBufferFluxResponse> = Mono.fromCallable {
        logger.info("Sending file ${fileKey.name} to a client")
        val content = fileStorage.download(fileKey)
        ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(content)
    }
        .doOnError(FileNotFoundException::class.java) {
            logger.warn("File with key $fileKey is not found", it)
        }
        .onErrorReturn(
            FileNotFoundException::class.java,
            ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .build()
        )

    @GetMapping(path = ["/internal/files/download-by-execution-id"], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun downloadAllByExecutionId(
        @RequestParam executionId: Long,
    ): Mono<ByteBufferFluxResponse> = blockingToMono {
        executionService.findExecution(executionId)
    }
        .switchIfEmptyToNotFound {
            "Not found execution by id $executionId"
        }
        .map { it.getFileKeys() }
        .flatMap { fileKeys ->
            val tempDir = createTempDir().toPath()
            val tempFile = createTempFile().toPath()

            fileKeys.toFlux()
                .flatMap { fileKey ->
                    fileStorage.download(fileKey)
                        .mapToInputStream()
                        .map { inputStream ->
                            (tempDir / fileKey.name).outputStream().use {
                                IOUtils.copy(inputStream, it)
                            }
                        }
                }
                .then()
                .map {
                    tempDir.compressAsZipTo(tempFile)
                    FileSystemUtils.deleteRecursively(tempDir)
                }
                .map {
                    ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(tempFile.toByteBufferFlux())
                }
                .doAfterTerminate {
                    tempFile.deleteExisting()
                }
        }

    @Operation(
        method = "POST",
        summary = "Download save-agent with current save-cloud version.",
        description = "Download save-agent with current save-cloud version.",
    )
    @ApiResponse(responseCode = "200", description = "Returns content of the file.")
    @ApiResponse(responseCode = "404", description = "File is not found.")
    @PostMapping(path = ["/internal/files/download-save-agent"], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun downloadSaveAgent(): Mono<out Resource> =
            Mono.just(ClassPathResource("save-agent.kexe"))
                .filter { it.exists() }
                .switchIfEmptyToNotFound()

    @Operation(
        method = "POST",
        summary = "Download save-cli by version.",
        description = "Download save-cli by version.",
    )
    @Parameter(
        name = "version",
        `in` = ParameterIn.QUERY,
        description = "version of save-cli",
        required = true
    )
    @ApiResponse(responseCode = "200", description = "Returns content of the file.")
    @PostMapping(path = ["/internal/files/download-save-cli"], produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun downloadSaveCliByVersion(
        @RequestParam version: String,
    ): Mono<out Resource> =
            Mono.just(ClassPathResource("save-$version-linuxX64.kexe"))
                .filter { it.exists() }
                .switchIfEmptyToNotFound {
                    "Can't find save-$version-linuxX64.kexe with the requested version $version"
                }

    /**
     * @param file a file to be uploaded
     * @param organizationName
     * @param projectName
     * @param authentication
     * @return [Mono] with response
     */
    @PostMapping(path = ["/api/$v1/files/{organizationName}/{projectName}/upload"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Suppress("UnsafeCallOnNullableType")
    fun upload(
        @RequestPart("file") file: Mono<FilePart>,
        @PathVariable organizationName: String,
        @PathVariable projectName: String,
        authentication: Authentication,
    ) = projectService.findWithPermissionByNameAndOrganization(
        authentication, projectName, organizationName, Permission.WRITE
    )
        .flatMap {
            fileStorage.uploadFilePart(file, ProjectCoordinates(organizationName, projectName))
        }
        .map { fileInfo ->
            ResponseEntity.status(
                if (fileInfo.sizeBytes > 0) HttpStatus.OK else HttpStatus.INTERNAL_SERVER_ERROR
            )
                .body(fileInfo)
        }
        .onErrorReturn(
            FileAlreadyExistsException::class.java,
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        )

    /**
     * @param partMono image to be uploaded
     * @param owner owner name
     * @param type type of avatar
     * @return [Mono] with response
     */
    @Suppress("UnsafeCallOnNullableType")
    @PostMapping(path = ["/api/$v1/image/upload"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadImage(
        @RequestPart("file") partMono: Mono<FilePart>,
        @RequestParam owner: String,
        @RequestParam type: AvatarType
    ) = partMono.flatMap { part ->
        val avatarKey = AvatarKey(
            type,
            owner,
            part.filename()
        )
        val content = part.content().map { it.asByteBuffer() }
        avatarStorage.upsert(avatarKey, content).map {
            logger.info("Saved $it bytes of $avatarKey")
            ImageInfo(avatarKey.getRelativePath())
        }
    }.map { imageInfo ->
        imageInfo.path?.let {
            when (type) {
                AvatarType.ORGANIZATION -> organizationService.saveAvatar(owner, it)
                AvatarType.USER -> userDetailsService.saveAvatar(owner, it)
            }
        }
        ResponseEntity.status(
            imageInfo.path?.let {
                HttpStatus.OK
            } ?: HttpStatus.INTERNAL_SERVER_ERROR
        )
            .body(imageInfo)
    }
        .onErrorReturn(
            FileAlreadyExistsException::class.java,
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        )

    /**
     * @param testExecutionDto
     * @return [Mono] with content of DebugInfo
     * @throws ResponseStatusException if request is invalid or result cannot be returned
     */
    @Suppress("ThrowsCount", "UnsafeCallOnNullableType")
    @PostMapping(path = ["/api/$v1/files/get-debug-info"])
    fun getDebugInfo(
        @RequestBody testExecutionDto: TestExecutionDto,
    ): Flux<ByteBuffer> {
        val executionId = getExecutionId(testExecutionDto)
        val testResultLocation = TestResultLocation.from(testExecutionDto)

        return debugInfoStorage.download(DebugInfoStorageKey(executionId, testResultLocation))
            .switchIfEmpty(
                Mono.fromCallable {
                    logger.warn("Additional file for $executionId and $testResultLocation not found")
                }
                    .toFlux()
                    .flatMap {
                        Flux.error(ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"))
                    }
            )
    }

    private fun getExecutionId(testExecutionDto: TestExecutionDto): Long {
        testExecutionDto.executionId?.let { return it }

        val agentContainerId = testExecutionDto.agentContainerId
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body should contain agentContainerId")
        val execution = agentRepository.findByContainerId(agentContainerId)?.execution
        return execution?.id
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Execution for agent $agentContainerId not found"
            )
    }

    /**
     * @param testExecutionDto
     * @return [Mono] with response
     * @throws ResponseStatusException if request is invalid or result cannot be returned
     */
    @Suppress("ThrowsCount", "UnsafeCallOnNullableType")
    @PostMapping(path = ["/api/$v1/files/get-execution-info"])
    fun getExecutionInfo(
        @RequestBody testExecutionDto: TestExecutionDto,
    ): Flux<ByteBuffer> {
        logger.debug("Processing getExecutionInfo : $testExecutionDto")
        val executionId = getExecutionId(testExecutionDto)
        return executionInfoStorage.download(executionId)
            .switchIfEmpty(
                Mono.fromCallable {
                    logger.debug("ExecutionInfo for $executionId not found")
                }
                    .toFlux()
                    .flatMap {
                        Flux.error(ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"))
                    }
            )
    }

    /**
     * @param agentContainerId agent that has executed the test
     * @param testResultDebugInfo additional info that should be stored
     * @return [Mono] with count of uploaded bytes
     */
    @PostMapping(value = ["/internal/files/debug-info"])
    @Suppress("UnsafeCallOnNullableType")
    fun uploadDebugInfo(@RequestParam("agentId") agentContainerId: String,
                        @RequestBody testResultDebugInfo: TestResultDebugInfo,
    ): Mono<Long> {
        val executionId = agentRepository.findByContainerId(agentContainerId)
            .orNotFound()
            .execution
            .requiredId()
        return debugInfoStorage.save(executionId, testResultDebugInfo)
    }
}
