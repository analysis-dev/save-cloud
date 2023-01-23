package com.saveourtool.save.backend.storage

import com.saveourtool.save.backend.configs.ConfigProperties
import com.saveourtool.save.backend.repository.FileRepository
import com.saveourtool.save.backend.service.ExecutionService
import com.saveourtool.save.backend.service.ProjectService
import com.saveourtool.save.entities.*
import com.saveourtool.save.storage.AbstractStorageWithDatabase
import com.saveourtool.save.utils.*

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.nio.file.Path

import kotlin.io.path.div
import kotlinx.datetime.toJavaLocalDateTime

/**
 * Storage for evaluated tools are loaded by users
 */
@Service
class FileStorage(
    configProperties: ConfigProperties,
    fileRepository: FileRepository,
    private val projectService: ProjectService,
    private val executionService: ExecutionService,
) : AbstractStorageWithDatabase<FileDto, File, FileRepository>(
    Path.of(configProperties.fileStorage.location) / "storage", fileRepository) {
    override fun createNewEntityFromDto(dto: FileDto): File = dto.toEntity {
        projectService.findByNameAndOrganizationNameAndCreatedStatus(dto.projectCoordinates.projectName, dto.projectCoordinates.organizationName)
            .orNotFound {
                "Not found project by coordinates: ${dto.projectCoordinates}"
            }
    }

    override fun findByDto(dto: FileDto): File? = repository.findByProject_Organization_NameAndProject_NameAndNameAndUploadedTime(
        organizationName = dto.projectCoordinates.organizationName,
        projectName = dto.projectCoordinates.projectName,
        name = dto.name,
        uploadedTime = dto.uploadedTime.toJavaLocalDateTime(),
    )

    override fun beforeDelete(entity: File) {
        executionService.unlinkFileFromAllExecution(entity)
    }

    override fun File.updateByContentSize(sizeBytes: Long): File = apply {
        this.sizeBytes = sizeBytes
    }

    /**
     * @param project
     * @return all [FileDto]s which provided [Project] does contain
     */
    fun listByProject(
        project: Project,
    ): Flux<FileDto> = blockingToFlux {
        repository.findAllByProject(project).map { it.toDto() }
    }

    /**
     * @param fileId
     * @return [FileDto] for [File] with provided [fileId]
     */
    fun getFileById(
        fileId: Long,
    ): Mono<FileDto> = blockingToMono {
        repository.findByIdOrNull(fileId)?.toDto()
    }
        .switchIfEmptyToNotFound { "Not found a file by id $fileId" }
}
