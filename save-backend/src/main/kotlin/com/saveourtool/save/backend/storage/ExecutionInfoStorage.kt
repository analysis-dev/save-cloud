package com.saveourtool.save.backend.storage

import com.saveourtool.save.backend.configs.ConfigProperties
import com.saveourtool.save.backend.repository.ExecutionRepository
import com.saveourtool.save.backend.utils.readAsJson
import com.saveourtool.save.backend.utils.toFluxByteBufferAsJson
import com.saveourtool.save.execution.ExecutionUpdateDto
import com.saveourtool.save.s3.S3Operations
import com.saveourtool.save.storage.AbstractS3Storage
import com.saveourtool.save.storage.concatS3Key
import com.saveourtool.save.storage.deleteAsyncUnexpectedIds
import com.saveourtool.save.utils.debug
import com.saveourtool.save.utils.getLogger

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

import javax.annotation.PostConstruct

/**
 * A storage for storing additional data (ExecutionInfo) associated with test results
 */
@Service
class ExecutionInfoStorage(
    configProperties: ConfigProperties,
    s3Operations: S3Operations,
    private val objectMapper: ObjectMapper,
    private val executionRepository: ExecutionRepository,
) : AbstractS3Storage<Long>(
    s3Operations,
    concatS3Key(configProperties.s3Storage.prefix, "executionInfo"),
) {
    /**
     * Init method to delete unexpected ids which are not associated to [com.saveourtool.save.entities.Execution]
     */
    @PostConstruct
    fun deleteUnexpectedIds() {
        deleteAsyncUnexpectedIds(executionRepository, log).subscribe()
    }

    /**
     * Update ExecutionInfo if it's required ([ExecutionUpdateDto.failReason] not null)
     *
     * @param executionInfo
     * @return empty Mono
     */
    fun upsertIfRequired(executionInfo: ExecutionUpdateDto): Mono<Unit> = executionInfo.failReason?.let {
        upsert(executionInfo)
    } ?: Mono.just(Unit)

    private fun upsert(executionInfo: ExecutionUpdateDto): Mono<Unit> = doesExist(executionInfo.id)
        .flatMap { exists ->
            if (exists) {
                download(executionInfo.id)
                    .readAsJson<ExecutionUpdateDto>(objectMapper)
                    .map {
                        it.copy(failReason = "${it.failReason}, ${executionInfo.failReason}")
                    }
                    .flatMap { executionInfoToSafe ->
                        delete(executionInfo.id).map { executionInfoToSafe }
                    }
            } else {
                Mono.just(executionInfo)
            }
        }
        .flatMap { executionInfoToSafe ->
            log.debug { "Writing debug info for ${executionInfoToSafe.id} to storage" }
            upload(executionInfoToSafe.id, executionInfoToSafe.toFluxByteBufferAsJson(objectMapper))
        }.map { bytesCount ->
            log.debug { "Wrote $bytesCount bytes of debug info for ${executionInfo.id} to storage" }
        }

    override fun buildKey(s3KeySuffix: String): Long = s3KeySuffix.toLong()
    override fun buildS3KeySuffix(key: Long): String = key.toString()

    companion object {
        private val log: Logger = getLogger<ExecutionInfoStorage>()
        private const val FILE_NAME = "execution-info.json"
    }
}
