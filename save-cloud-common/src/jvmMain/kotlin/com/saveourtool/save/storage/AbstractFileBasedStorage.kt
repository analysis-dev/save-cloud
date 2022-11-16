package com.saveourtool.save.storage

import com.saveourtool.save.utils.countPartsTill
import com.saveourtool.save.utils.toDataBufferFlux
import com.saveourtool.save.utils.collectToFile
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.*

/**
 * File based implementation of Storage
 *
 * @param rootDir root directory for storage
 * @param pathPartsCount count of parts in path for key, if it's null -- this validation is not applicable
 * @param K type of key
 */
abstract class AbstractFileBasedStorage<K>(
    private val rootDir: Path,
    private val pathPartsCount: Int? = null,
) : Storage<K> {
    init {
        rootDir.createDirectoriesIfRequired()
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override fun list(): Flux<K> = Files.walk(rootDir)
        .toFlux()
        .filter { it.isRegularFile() }
        .filter { pathToContent ->
            pathPartsCount?.let { pathToContent.countPartsTill(rootDir) == it } ?: true
        }
        .filter { isKey(rootDir, it) }
        .map { buildKey(rootDir, it) }

    override fun doesExist(key: K): Mono<Boolean> = Mono.fromCallable { buildPathToContent(key).exists() }

    override fun contentSize(key: K): Mono<Long> = Mono.fromCallable { buildPathToContent(key).fileSize() }

    override fun delete(key: K): Mono<Boolean> {
        val contentPath = buildPathToContent(key)
        return Mono.fromCallable {
            contentPath.deleteIfExists()
        }.doOnNext {
            if (it) {
                contentPath.parent.deleteDirectoriesTill(rootDir)
            }
        }
    }

    override fun upload(key: K, content: Flux<ByteBuffer>): Mono<Long> {
        val contentPath = buildPathToContent(key)
        return Mono.fromCallable {
            contentPath.parent.createDirectoriesIfRequired()
            contentPath.createFile()
        }.flatMap { _ ->
            content.collectToFile(contentPath)
        }.map { it.toLong() }
    }

    override fun download(key: K): Flux<ByteBuffer> {
        @Suppress("BlockingMethodInNonBlockingContext")
        return buildPathToContent(key).toDataBufferFlux()
            .map { it.asByteBuffer() }
    }

    /**
     * @param rootDir
     * @param pathToContent
     * @return true if provided path is key for content of this storage otherwise - false
     */
    protected open fun isKey(rootDir: Path, pathToContent: Path): Boolean = true

    /**
     * @param rootDir
     * @param pathToContent
     * @return [K] object is built by [Path]
     */
    protected abstract fun buildKey(rootDir: Path, pathToContent: Path): K

    /**
     * @param rootDir
     * @param key
     * @return [Path] is built by [K] object
     */
    protected abstract fun buildPathToContent(rootDir: Path, key: K): Path

    private fun buildPathToContent(key: K): Path = buildPathToContent(rootDir, key)

    private fun Path.createDirectoriesIfRequired() {
        if (!exists()) {
            createDirectories()
        }
    }

    private fun Path.deleteDirectoriesTill(stopDirectory: Path) {
        if (this != stopDirectory && this.listDirectoryEntries().isEmpty()) {
            this.deleteExisting()
            this.parent.deleteDirectoriesTill(stopDirectory)
        }
    }
}
