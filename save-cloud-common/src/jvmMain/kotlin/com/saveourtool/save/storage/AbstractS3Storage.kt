package com.saveourtool.save.storage

import com.saveourtool.save.s3.S3Operations
import com.saveourtool.save.utils.debug
import com.saveourtool.save.utils.getLogger
import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import java.nio.ByteBuffer
import java.time.Instant

/**
 * S3 implementation of Storage
 *
 * @param s3Operations [S3Operations] to operate with S3
 * @param prefix a common prefix for all S3 keys in this storage
 * @param K type of key
 */
abstract class AbstractS3Storage<K>(
    private val s3Operations: S3Operations,
    prefix: String,
) : Storage<K> {
    private val log: Logger = getLogger(this::class)
    private val prefix = prefix.removeSuffix(PATH_DELIMITER) + PATH_DELIMITER

    override fun list(): Flux<K> = s3Operations.listObjectsV2(prefix)
        .flatMapIterable { response ->
            response.contents().map {
                buildKey(it.key().removePrefix(prefix))
            }
        }

    override fun download(key: K): Flux<ByteBuffer> = s3Operations.getObject(buildS3Key(key))
        .flatMapMany {
            it.toFlux()
        }

    override fun upload(key: K, content: Flux<ByteBuffer>): Mono<Long> =
            s3Operations.uploadObject(buildS3Key(key), content)
                .flatMap {
                    contentSize(key)
                }

    override fun upload(key: K, contentLength: Long, content: Flux<ByteBuffer>): Mono<Unit> =
            s3Operations.uploadObject(buildS3Key(key), contentLength, content)
                .map { response ->
                    log.debug { "Uploaded $key with versionId: ${response.versionId()}" }
                }

    override fun delete(key: K): Mono<Boolean> = s3Operations.deleteObject(buildS3Key(key))
        .thenReturn(true)
        .defaultIfEmpty(false)

    override fun lastModified(key: K): Mono<Instant> = s3Operations.headObject(buildS3Key(key))
        .map { response ->
            response.lastModified()
        }

    override fun contentSize(key: K): Mono<Long> = s3Operations.headObject(buildS3Key(key))
        .map { response ->
            response.contentLength()
        }

    override fun doesExist(key: K): Mono<Boolean> = s3Operations.headObject(buildS3Key(key))
        .map { true }
        .defaultIfEmpty(false)

    /**
     * @param s3KeySuffix cannot start with [PATH_DELIMITER]
     * @return [K] is built from [s3KeySuffix]
     */
    protected abstract fun buildKey(s3KeySuffix: String): K

    /**
     * @param key
     * @return suffix for s3 key, cannot start with [PATH_DELIMITER]
     */
    protected abstract fun buildS3KeySuffix(key: K): String

    private fun buildS3Key(key: K) = prefix + buildS3KeySuffix(key).validateSuffix()

    companion object {
        private fun String.validateSuffix(): String = also { suffix ->
            require(!suffix.startsWith(PATH_DELIMITER)) {
                "Suffix cannot start with $PATH_DELIMITER: $suffix"
            }
        }
    }
}
