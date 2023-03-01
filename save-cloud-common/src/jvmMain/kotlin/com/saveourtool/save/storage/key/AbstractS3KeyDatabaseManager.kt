package com.saveourtool.save.storage.key

import com.saveourtool.save.spring.entity.BaseEntity
import com.saveourtool.save.spring.repository.BaseEntityRepository
import com.saveourtool.save.utils.orNotFound

import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.annotation.Transactional
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Implementation of [S3KeyManager] which stores keys in database
 *
 * @param prefix a common prefix for all keys in S3 storage for this storage
 * @property repository repository for [E]
 * @property ioScheduler
 * @property ioDispatcher
 */
abstract class AbstractS3KeyDatabaseManager<K : Any, E : BaseEntity, R : BaseEntityRepository<E>>(
    prefix: String,
    protected val repository: R,
    val ioScheduler: Scheduler = Schedulers.boundedElastic(),
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : S3KeyManager<K> {
    /**
     * [S3KeyManager] with [Long] as key (it's [ID][com.saveourtool.save.spring.entity.BaseEntity.requiredId])
     */
    private val entityIdKeyManager: S3KeyManager<Long> = object : AbstractS3KeyManager<Long>(prefix) {
        override fun buildKeyFromSuffix(s3KeySuffix: String): Long = s3KeySuffix.toLong()
        override fun buildS3KeySuffix(key: Long): String = key.toString()
    }
    override val commonPrefix: String = entityIdKeyManager.commonPrefix

    /**
     * @return a key [K] created from receiver entity [E]
     */
    protected abstract fun E.toKey(): K

    /**
     * @return an entity [E] created from receiver key [K]
     */
    protected abstract fun K.toEntity(): E

    /**
     * @param key
     * @return [E] entity found by [K] key or null
     */
    protected abstract fun findEntity(key: K): E?

    @Transactional
    override fun delete(key: K) {
        findEntity(key)?.let { entity ->
            beforeDelete(entity)
            repository.delete(entity)
        }
    }

    /**
     * @receiver [E] entity which needs to be processed before deletion
     * @param entity
     */
    protected open fun beforeDelete(entity: E): Unit = Unit

    @Transactional
    override fun createNewS3Key(key: K): String {
        val entity = repository.save(key.toEntity())
        return entityIdKeyManager.createNewS3Key(entity.requiredId())
    }

    override fun findExistedS3Key(key: K): String? = findEntity(key)?.let { entity -> entityIdKeyManager.findExistedS3Key(entity.requiredId()) }

    override fun findKey(s3Key: String): K? {
        val entityId = entityIdKeyManager.findKey(s3Key).orNotFound {
            "Cannot extract entity id from s3 key: $s3Key"
        }
        return repository.findByIdOrNull(entityId)
            .orNotFound {
                "Not found entity by id $entityId"
            }
            .toKey()
    }
}
