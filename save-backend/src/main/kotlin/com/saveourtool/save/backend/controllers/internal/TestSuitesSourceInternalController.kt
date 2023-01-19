package com.saveourtool.save.backend.controllers.internal

import com.saveourtool.save.backend.ByteBufferFluxResponse
import com.saveourtool.save.backend.service.*
import com.saveourtool.save.backend.storage.TestsSourceSnapshotStorage
import com.saveourtool.save.entities.TestSuitesSource
import com.saveourtool.save.test.TestsSourceSnapshotDto
import com.saveourtool.save.test.TestsSourceVersionInfo
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
    private val testSuitesService: TestSuitesService,
    private val testSuitesSourceService: TestSuitesSourceService,
    private val executionService: ExecutionService,
    private val lnkExecutionTestSuiteService: LnkExecutionTestSuiteService,
) {
    /**
     * @param snapshotDto
     * @param contentAsMonoPart
     * @return [Mono] without value
     */
    @PostMapping("/upload-snapshot", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadSnapshot(
        @RequestPart("snapshot") snapshotDto: TestsSourceSnapshotDto,
        @RequestPart("content") contentAsMonoPart: Mono<Part>,
    ): Mono<Unit> = contentAsMonoPart.flatMap { part ->
        val content = part.content().map { it.asByteBuffer() }
        snapshotStorage.upload(snapshotDto, content).thenReturn(Unit)
    }

    /**
     * @param versionInfo
     * @return [Mono] without value
     */
    @PostMapping("/save-version")
    fun saveVersion(
        @RequestBody versionInfo: TestsSourceVersionInfo,
    ): Mono<Unit> = snapshotStorage.copy(
        source = versionInfo.toOriginSnapshot(),
        target = versionInfo.toSnapshot(),
    ).flatMap {
        blockingToMono {
            testSuitesService.copyToNewVersion(
                organizationName = versionInfo.organizationName,
                sourceName = versionInfo.sourceName,
                originalVersion = versionInfo.commitId,
                newVersion = versionInfo.version,
            )
        }
    }

    /**
     * @param snapshotDto
     * @return [Mono] with result
     */
    @PostMapping("/contains-snapshot")
    fun containsSnapshot(
        @RequestBody snapshotDto: TestsSourceSnapshotDto,
    ): Mono<Boolean> = snapshotStorage.doesExist(snapshotDto)

    /**
     * @param executionId
     * @return content of tests related to provided values
     */
    @PostMapping("/download-snapshot-by-execution-id", produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun downloadByExecutionId(
        @RequestParam executionId: Long
    ): Mono<ByteBufferFluxResponse> = blockingToMono {
        val execution = executionService.findExecution(executionId)
            .orNotFound { "Execution (id=$executionId) not found" }
        val testSuite = lnkExecutionTestSuiteService.getAllTestSuitesByExecution(execution).firstOrNull().orNotFound {
            "Execution (id=$executionId) doesn't have any testSuites"
        }
        testSuite
            .toDto()
            .let { it.source to it.version }
    }.flatMap { (source, version) ->
        source.downloadSnapshot(version)
    }

    private fun TestSuitesSourceDto.downloadSnapshot(
        version: String
    ): Mono<ByteBufferFluxResponse> = blockingToMono {
        testsSourceVersionService.findSnapshot(organizationName, name, version)
    }
        .switchIfEmptyToNotFound {
            "Not found a snapshot of $name in $organizationName with version=$version"
        }
        .map { snapshot ->
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(snapshotStorage.download(snapshot))
        }

    private fun TestsSourceVersionInfo.toSnapshot() = TestsSourceSnapshotDto(
        sourceId = testSuitesSourceService.getByName(organizationName, sourceName).requiredId(),
        commitId = version,
        commitTime = creationTime,
    )

    private fun TestsSourceVersionInfo.toOriginSnapshot() = TestsSourceSnapshotDto(
        sourceId = testSuitesSourceService.getByName(organizationName, sourceName).requiredId(),
        commitId = commitId,
        commitTime = commitTime,
    )
}
