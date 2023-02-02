package com.saveourtool.save.backend.storage

import com.saveourtool.save.backend.configs.ConfigProperties
import com.saveourtool.save.backend.repository.TestExecutionRepository
import com.saveourtool.save.backend.service.TestExecutionService
import com.saveourtool.save.backend.utils.toFluxByteBufferAsJson
import com.saveourtool.save.domain.TestResultDebugInfo
import com.saveourtool.save.entities.TestExecution
import com.saveourtool.save.storage.AbstractS3Storage
import com.saveourtool.save.storage.concatS3Key
import com.saveourtool.save.storage.deleteAsyncUnexpectedIds
import com.saveourtool.save.utils.*

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import software.amazon.awssdk.services.s3.S3AsyncClient

import javax.annotation.PostConstruct

/**
 * A storage for storing additional data associated with test results
 */
@Service
class DebugInfoStorage(
    configProperties: ConfigProperties,
    s3Client: S3AsyncClient,
    private val objectMapper: ObjectMapper,
    private val testExecutionService: TestExecutionService,
    private val testExecutionRepository: TestExecutionRepository,
) : AbstractS3Storage<Long>(
    s3Client,
    configProperties.s3Storage.bucketName,
    concatS3Key(configProperties.s3Storage.prefix, "debugInfo"),
) {
    /**
     * Init method to delete unexpected ids which are not associated to [com.saveourtool.save.entities.TestExecution]
     */
    @PostConstruct
    fun deleteUnexpectedIds() {
        deleteAsyncUnexpectedIds(testExecutionRepository, log).subscribe()
    }

    /**
     * Store provided [testResultDebugInfo] associated with [TestExecution.id]
     *
     * @param executionId
     * @param testResultDebugInfo
     * @return count of saved bytes
     */
    fun upload(
        executionId: Long,
        testResultDebugInfo: TestResultDebugInfo,
    ): Mono<Long> = blockingToMono { testExecutionService.getTestExecution(executionId, testResultDebugInfo.testResultLocation)?.requiredId() }
        .switchIfEmptyToNotFound {
            "Not found ${TestExecution::class.simpleName} by executionId $executionId and testResultLocation: ${testResultDebugInfo.testResultLocation}"
        }
        .flatMap { testExecutionId ->
            log.debug { "Writing debug info for $testExecutionId" }
            upload(testExecutionId, testResultDebugInfo.toFluxByteBufferAsJson(objectMapper))
        }

    override fun buildKey(s3KeySuffix: String): Long = s3KeySuffix.toLong()
    override fun buildS3KeySuffix(key: Long): String = key.toString()

    companion object {
        private val log: Logger = getLogger<DebugInfoStorage>()
    }
}
