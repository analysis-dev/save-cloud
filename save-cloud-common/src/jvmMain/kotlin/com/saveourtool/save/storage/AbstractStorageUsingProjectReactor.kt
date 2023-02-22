package com.saveourtool.save.storage

import com.saveourtool.save.s3.S3Operations
import com.saveourtool.save.storage.key.S3KeyManager
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URL
import java.nio.ByteBuffer
import java.time.Instant
import javax.annotation.PostConstruct

/**
 * S3 implementation of Storage
 *
 * @param s3Operations [S3Operations] to operate with S3
 * @param K type of key
 */
abstract class AbstractStorageUsingProjectReactor<K : Any>(
    s3Operations: S3Operations,
) : StorageUsingProjectReactor<K> {
    private val initializer: StorageInitializer = StorageInitializer(this::class)

    /**
     * [S3KeyManager] manager for S3 keys
     */
    protected abstract val s3KeyManager: S3KeyManager<K>
    private val storageProjectReactor by lazy { DefaultStorageProjectReactor(s3Operations, s3KeyManager) }
    private val storagePreSignedUrl by lazy { DefaultStoragePreSignedUrl(s3Operations, s3KeyManager) }

    /**
     * Init method to call [initializer]
     */
    @PostConstruct
    fun init() {
        initializer.initReactively {
            doInit(storageProjectReactor)
        }
    }

    /**
     * @param underlying
     * @return result of init method as [Mono] without body, it's [Mono.empty] by default
     */
    protected open fun doInit(underlying: DefaultStorageProjectReactor<K>): Mono<Unit> = Mono.empty()

    override fun list(): Flux<K> = initializer.validateAndRun { storageProjectReactor.list() }

    override fun download(key: K): Flux<ByteBuffer> = initializer.validateAndRun { storageProjectReactor.download(key) }

    override fun upload(key: K, content: Flux<ByteBuffer>): Mono<K> = initializer.validateAndRun { storageProjectReactor.upload(key, content) }

    override fun upload(key: K, contentLength: Long, content: Flux<ByteBuffer>): Mono<K> = initializer.validateAndRun { storageProjectReactor.upload(key, contentLength, content) }

    override fun delete(key: K): Mono<Boolean> = initializer.validateAndRun { storageProjectReactor.delete(key) }

    override fun lastModified(key: K): Mono<Instant> = initializer.validateAndRun { storageProjectReactor.lastModified(key) }

    override fun contentLength(key: K): Mono<Long> = initializer.validateAndRun { storageProjectReactor.contentLength(key) }

    override fun doesExist(key: K): Mono<Boolean> = initializer.validateAndRun { storageProjectReactor.doesExist(key) }

    override fun move(source: K, target: K): Mono<Boolean> = initializer.validateAndRun { storageProjectReactor.move(source, target) }

    override fun generateUrlToDownload(key: K): URL? = initializer.validateAndRun { storagePreSignedUrl.generateUrlToDownload(key) }

    override fun generateUrlToUpload(key: K, contentLength: Long): UrlWithHeaders? = initializer.validateAndRun { storagePreSignedUrl.generateUrlToUpload(key, contentLength) }
}
