package com.saveourtool.save.storage

import java.nio.ByteBuffer
import java.time.Instant

import kotlinx.coroutines.flow.Flow

/**
 * Base interface for Storage using [coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
 *
 * @param K type of key
 */
interface StorageCoroutines<K> {
    /**
     * @return list of keys in storage
     */
    suspend fun list(): Flow<K>

    /**
     * @param key a key to be checked
     * @return true if the key exists, otherwise false
     */
    suspend fun doesExist(key: K): Boolean

    /**
     * @param key a key to be checked
     * @return content size in bytes
     */
    suspend fun contentLength(key: K): Long?

    /**
     * @param key a key to be checked
     * @return when a key was modified last time
     */
    suspend fun lastModified(key: K): Instant?

    /**
     * @param key a key to be deleted
     * @return true if the object deleted, otherwise false
     */
    suspend fun delete(key: K): Boolean

    /**
     * @param key a key for provided content
     * @param content
     * @return count of written bytes
     */
    suspend fun upload(key: K, content: Flow<ByteBuffer>): K

    /**
     * @param key a key for provided content
     * @param contentLength a content length of content
     * @param content as [Flow] of [ByteBuffer]
     */
    suspend fun upload(key: K, contentLength: Long, content: Flow<ByteBuffer>): K

    /**
     * @param key a key to download content
     * @return downloaded content
     */
    suspend fun download(key: K): Flow<ByteBuffer>

    /**
     * @param source a key of source
     * @param target a key of target
     * @return true if the [source] deleted, otherwise false
     */
    suspend fun move(source: K, target: K): Boolean
}
