package com.saveourtool.save.storage

import com.saveourtool.save.s3.S3Operations
import com.saveourtool.save.storage.key.AbstractS3KeyDatabaseManager
import com.saveourtool.save.storage.key.S3KeyManager
import com.saveourtool.save.utils.*

import org.slf4j.Logger
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import reactor.kotlin.core.publisher.toMono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import software.amazon.awssdk.core.async.AsyncRequestBody

import java.net.URL
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletableFuture

import kotlin.time.Duration.Companion.minutes

/**
 * S3 implementation of Storage
 *
 * @param s3Operations [S3Operations] to operate with S3
 * @param K type of key
 */
abstract class AbstractS3Storage<K : Any>(
    private val s3Operations: S3Operations,
) : Storage<K> {
    private val log: Logger = getLogger(this::class)

    /**
     * [S3KeyManager] manager for S3 keys
     */
    protected abstract val s3KeyManager: S3KeyManager<K>

    override fun list(): Flux<K> = s3Operations.listObjectsV2(s3KeyManager.commonPrefix)
        .toMonoAndPublishOn()
        .expand { lastResponse ->
            if (lastResponse.isTruncated) {
                s3Operations.listObjectsV2(s3KeyManager.commonPrefix, lastResponse.nextContinuationToken())
                    .toMonoAndPublishOn()
            } else {
                Mono.empty()
            }
        }
        .flatMapIterable { response ->
            response.contents().mapNotNull { s3Object ->
                val s3Key = s3Object.key()
                val key = s3KeyManager.findKey(s3Key)
                if (!key.isNotNull()) {
                    log.warn {
                        "Found s3 key $s3Key which is not valid by ${s3KeyManager::class.simpleName}"
                    }
                }
                key
            }
        }

    override fun download(key: K): Flux<ByteBuffer> = findExistedS3Key(key)
        .flatMap { s3Key ->
            s3Operations.getObject(s3Key)
                .toMonoAndPublishOn()
        }
        .flatMapMany {
            it.toFlux()
        }

    override fun generateUrlToDownload(key: K): URL = s3KeyManager.findExistedS3Key(key)
        ?.let { s3Key ->
            s3Operations.requestToDownloadObject(s3Key, downloadDuration)
                .also { request ->
                    require(request.isBrowserExecutable) {
                        "Pre-singer url to download object should be browser executable (header-less)"
                    }
                }
                .url()
        }
        .orNotFound {
            "Not found s3 key for $key"
        }

    override fun upload(key: K, content: Flux<ByteBuffer>): Mono<K> =
            createNewS3Key(key)
                .flatMap { s3Key ->
                    s3Operations.createMultipartUpload(s3Key)
                        .toMonoAndPublishOn()
                        .flatMap { response ->
                            content.index()
                                .flatMap { (index, buffer) ->
                                    s3Operations.uploadPart(response, index + 1, AsyncRequestBody.fromByteBuffer(buffer))
                                        .toMonoAndPublishOn()
                                }
                                .collectList()
                                .flatMap { uploadPartResults ->
                                    s3Operations.completeMultipartUpload(response, uploadPartResults)
                                        .toMonoAndPublishOn()
                                }
                        }
                        .map {
                            requireNotNull(s3KeyManager.findKey(s3Key)) {
                                "Not found inserted updated key for $key"
                            }
                        }
                }

    override fun upload(key: K, contentLength: Long, content: Flux<ByteBuffer>): Mono<K> =
            createNewS3Key(key)
                .flatMap { s3Key ->
                    s3Operations.putObject(s3Key, contentLength, AsyncRequestBody.fromPublisher(content))
                        .toMonoAndPublishOn()
                        .doOnError {
                            s3KeyManager.delete(key)
                        }
                        .map {
                            requireNotNull(s3KeyManager.findKey(s3Key)) {
                                "Not found inserted updated key for $key"
                            }
                        }
                }

    override fun move(source: K, target: K): Mono<Boolean> =
            findExistedS3Key(source)
                .zipWith(createNewS3Key(target))
                .flatMap { (sourceS3Key, targetS3Key) ->
                    s3Operations.copyObject(sourceS3Key, targetS3Key)
                        .toMonoAndPublishOn()
                }
                .flatMap {
                    delete(source)
                }

    override fun delete(key: K): Mono<Boolean> = findExistedS3Key(key).flatMap { s3Key ->
        s3Operations.deleteObject(s3Key)
            .toMonoAndPublishOn()
    }
        .map { deleteKey(key) }
        .thenReturn(true)
        .defaultIfEmpty(false)

    override fun lastModified(key: K): Mono<Instant> = findExistedS3Key(key).flatMap { s3Key ->
        s3Operations.headObject(s3Key)
            .toMonoAndPublishOn()
    }
        .map { response ->
            response.lastModified()
        }

    override fun contentLength(key: K): Mono<Long> = findExistedS3Key(key).flatMap { s3Key ->
        s3Operations.headObject(s3Key)
            .toMonoAndPublishOn()
    }
        .map { response ->
            response.contentLength()
        }

    override fun doesExist(key: K): Mono<Boolean> = findExistedS3Key(key).flatMap { s3Key ->
        s3Operations.headObject(s3Key)
            .toMonoAndPublishOn()
    }
        .map { true }
        .defaultIfEmpty(false)

    private fun deleteKey(key: K): Mono<Unit> = if (s3KeyManager is AbstractS3KeyDatabaseManager<*, *, *>) {
        blockingToMono {
            s3KeyManager.delete(key)
        }
    } else {
        Mono.fromCallable {
            s3KeyManager.delete(key)
        }
    }

    private fun findExistedS3Key(key: K): Mono<String> = if (s3KeyManager is AbstractS3KeyDatabaseManager<*, *, *>) {
        blockingToMono {
            s3KeyManager.findExistedS3Key(key)
        }
    } else {
        s3KeyManager.findExistedS3Key(key).toMono()
    }

    private fun createNewS3Key(key: K): Mono<String> = if (s3KeyManager is AbstractS3KeyDatabaseManager<*, *, *>) {
        blockingToMono {
            s3KeyManager.createNewS3Key(key)
        }
    } else {
        s3KeyManager.createNewS3Key(key).toMono()
    }

    private fun <T : Any> CompletableFuture<out T?>.toMonoAndPublishOn(): Mono<T> = toMono().publishOn(s3Operations.scheduler)

    companion object {
        private val downloadDuration = 15.minutes
    }
}
