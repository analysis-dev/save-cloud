package com.saveourtool.save.storage

import com.saveourtool.save.s3.S3Operations
import com.saveourtool.save.spring.entity.BaseEntity
import com.saveourtool.save.spring.repository.BaseEntityRepository
import com.saveourtool.save.utils.*

import org.slf4j.Logger
import org.springframework.data.domain.Example
import org.springframework.http.HttpStatus
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.net.URL
import java.nio.ByteBuffer
import java.time.Instant
import javax.annotation.PostConstruct

/**
 * Implementation of S3 storage which stores keys in database
 *
 * @param s3Operations interface to operate with S3 storage
 * @param prefix a common prefix for all keys in S3 storage for this storage
 * @property repository repository for [E] which is entity for [K]
 */
abstract class AbstractStorageWithDatabase<K : Any, E : BaseEntity, R : BaseEntityRepository<E>>(
    private val s3Operations: S3Operations,
    private val prefix: String,
    protected val repository: R,
) : Storage<K> {
    private val log: Logger = getLogger(this.javaClass)
    private val commonPrefix: String = prefix.asS3CommonPrefix()
    private val storage: Storage<Long> = defaultS3Storage(s3Operations, commonPrefix)

    /**
     * Init method to back up unexpected ids which are detected in storage,but missed in database
     */
    @PostConstruct
    fun backupUnexpectedIds() {
        Mono.fromFuture {
            s3Operations.backupUnexpectedKeys(
                storageName = "${this::class.simpleName}",
                commonPrefix = commonPrefix,
            ) { s3Key ->
                val id = s3Key.removePrefix(commonPrefix).toLong()
                repository.findById(id).isEmpty
            }
        }.publishOn(s3Operations.scheduler)
            .subscribe()
    }

    /**
     * @return a key [K] created from receiver entity [E]
     */
    protected abstract fun E.toKey(): K

    /**
     * @return an entity [E] created from receiver key [K]
     */
    protected abstract fun K.toEntity(): E

    override fun list(): Flux<K> = blockingToFlux {
        repository.findAll().map { it.toKey() }
    }

    override fun doesExist(key: K): Mono<Boolean> = blockingToMono { findEntity(key) }
        .flatMap { entity ->
            storage.doesExist(entity.requiredId())
                .filter { it }
                .switchIfEmptyToResponseException(HttpStatus.CONFLICT) {
                    "The key $key is presented in database, but missed in storage"
                }
        }
        .defaultIfEmpty(false)

    override fun contentLength(key: K): Mono<Long> = getIdAsMono(key).flatMap { storage.contentLength(it) }

    override fun lastModified(key: K): Mono<Instant> = getIdAsMono(key).flatMap { storage.lastModified(it) }

    override fun delete(key: K): Mono<Boolean> = blockingToMono { findEntity(key) }
        .flatMap { entity ->
            storage.delete(entity.requiredId())
                .asyncEffectIf({ this }) {
                    doDelete(entity)
                }
        }
        .defaultIfEmpty(false)

    override fun upload(key: K, content: Flux<ByteBuffer>): Mono<K> = blockingToMono {
        repository.save(key.toEntity())
    }
        .flatMap { entity ->
            storage.upload(entity.requiredId(), content)
                .map { entity.toKey() }
                .onErrorResume { ex ->
                    doDelete(entity).then(Mono.error(ex))
                }
        }

    override fun upload(key: K, contentLength: Long, content: Flux<ByteBuffer>): Mono<K> = blockingToMono {
        repository.save(key.toEntity())
    }
        .flatMap { entity ->
            storage.upload(entity.requiredId(), contentLength, content)
                .map { entity.toKey() }
                .onErrorResume { ex ->
                    doDelete(entity).then(Mono.error(ex))
                }
        }

    override fun move(source: K, target: K): Mono<Boolean> = throw UnsupportedOperationException("${AbstractStorageWithDatabase::class.simpleName} storage doesn't support moving")

    override fun download(key: K): Flux<ByteBuffer> = getIdAsMono(key).flatMapMany { storage.download(it) }

    override fun generateUrlToDownload(key: K): URL = getId(key).let { storage.generateUrlToDownload(it) }

    private fun getIdAsMono(key: K): Mono<Long> = blockingToMono { findEntity(key)?.requiredId() }
        .switchIfEmptyToNotFound { "Key $key is not saved: ID is not set and failed to find by default example" }

    private fun getId(key: K): Long = findEntity(key)?.requiredId().orNotFound { "Key $key is not saved: ID is not set and failed to find by default example" }

    private fun doDelete(entity: E): Mono<Unit> = blockingToMono {
        beforeDelete(entity)
        repository.delete(entity)
    }

    /**
     * A default implementation uses Spring's [Example]
     *
     * @param key
     * @return [E] entity found by [K] key or null
     */
    protected abstract fun findEntity(key: K): E?

    /**
     * @receiver [E] entity which needs to be processed before deletion
     * @param entity
     */
    protected open fun beforeDelete(entity: E): Unit = Unit

    companion object {
        private fun defaultS3Storage(s3Operations: S3Operations, prefix: String): Storage<Long> = object : AbstractS3Storage<Long>(s3Operations, prefix) {
            override fun buildKey(s3KeySuffix: String): Long = s3KeySuffix.toLong()
            override fun buildS3KeySuffix(key: Long): String = key.toString()
        }
    }
}
