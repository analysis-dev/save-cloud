package com.saveourtool.save.storage

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.ByteBuffer

/**
 * Base interface for Storage
 *
 * @param K type of key
 */
interface Storage<K> {
    /**
     * @return list of keys in storage
     */
    fun list(): Flux<K>

    /**
     * @param key a key to be checked
     * @return true if the key exists, otherwise false
     */
    fun doesExist(key: K): Mono<Boolean>

    /**
     * @param key a ket to be checked
     * @return content size in bytes
     */
    fun contentSize(key: K): Mono<Long>

    /**
     * @param key a key to be deleted
     * @return true if the object deleted, otherwise false
     */
    fun delete(key: K): Mono<Boolean>

    /**
     * @param key a key for provided content
     * @param content
     * @return count of written bytes
     */
    fun upload(key: K, content: Flux<ByteBuffer>): Mono<Long>

    /**
     * @param key a key for provided content
     * @param content
     * @return count of written bytes
     */
    fun overwrite(key: K, content: Flux<ByteBuffer>): Mono<Long> = delete(key)
        .flatMap { upload(key, content) }

    /**
     * @param key a key to download content
     * @return downloaded content
     */
    fun download(key: K): Flux<ByteBuffer>

    /**
     * @param source a key of source
     * @param target a key of target
     * @return count of copied bytes
     */
    fun copy(source: K, target: K): Mono<Long> =
            upload(target, download(source))

    /**
     * @param source a key of source
     * @param target a key of target
     * @return true if the [source] deleted, otherwise false
     */
    fun move(source: K, target: K): Mono<Boolean> =
            copy(source, target).then(delete(source))
}
