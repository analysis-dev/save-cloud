package com.saveourtool.save.backend.controllers.internal

import com.saveourtool.save.backend.ByteBufferFluxResponse
import com.saveourtool.save.backend.service.*
import com.saveourtool.save.backend.storage.TestsSourceSnapshotStorage
import com.saveourtool.save.entities.TestSuitesSource
import com.saveourtool.save.test.TestsSourceSnapshotDto
import com.saveourtool.save.test.TestsSourceVersionDto
import com.saveourtool.save.testsuite.*
import com.saveourtool.save.utils.*

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.Part
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

/**
 * Controller for [TestSuitesSource]
 */
@RestController
@RequestMapping("/internal/test-suites-sources")
class TestSuitesSourceInternalController(
    private val testsSourceVersionService: TestsSourceVersionService,
    private val snapshotStorage: TestsSourceSnapshotStorage,
    private val executionService: ExecutionService,
) {
    /**
     * @param snapshotDto
     * @param contentAsMonoPart
     * @param contentLength
     * @return [Mono] with updated [snapshotDto]
     */
    @PostMapping("/upload-snapshot", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadSnapshot(
        @RequestPart("snapshot") snapshotDto: TestsSourceSnapshotDto,
        @RequestPart("content") contentAsMonoPart: Mono<Part>,
        @RequestHeader(CONTENT_LENGTH_CUSTOM) contentLength: Long,
    ): Mono<TestsSourceSnapshotDto> = contentAsMonoPart.flatMap { part ->
        val content = part.content().map { it.asByteBuffer() }
        snapshotStorage.uploadAndReturnUpdatedKey(snapshotDto, content)
    }

    /**
     * @param versionDto the version to save.
     * @return `true` if the [version][versionDto] was saved, `false` if the
     *   version with the same [name][TestsSourceVersionDto.name] and numeric
     *   [snapshot id][TestsSourceVersionDto.snapshotId] already exists.
     */
    @PostMapping("/save-version")
    fun saveVersion(
        @RequestBody versionDto: TestsSourceVersionDto,
    ): Mono<Boolean> = blockingToMono {
        testsSourceVersionService.save(versionDto)
    }

    /**
     * @param sourceId
     * @param commitId
     * @return [Mono] with result or empty
     */
    @GetMapping("/find-snapshot")
    fun findSnapshot(
        @RequestParam sourceId: Long,
        @RequestParam commitId: String,
    ): Mono<TestsSourceSnapshotDto> = blockingToMono {
        testsSourceVersionService.findSnapshot(sourceId, commitId)
    }

    /**
     * @param executionId
     * @return content of tests related to provided values
     */
    @PostMapping("/download-snapshot-by-execution-id", produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun downloadByExecutionId(
        @RequestParam executionId: Long
    ): Mono<ByteBufferFluxResponse> = blockingToMono {
        executionService.getRelatedTestsSourceSnapshot(executionId)
    }.map { snapshot ->
        ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(snapshotStorage.download(snapshot))
    }
}
