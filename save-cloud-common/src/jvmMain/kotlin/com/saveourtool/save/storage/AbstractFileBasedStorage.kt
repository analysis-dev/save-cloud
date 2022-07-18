package com.saveourtool.save.storage

import com.saveourtool.save.domain.ProjectCoordinates
import com.saveourtool.save.utils.toDataBufferFlux
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.stream.Collectors
import kotlin.io.path.*

/**
 * File based implementation of Storage
 *
 * @param rootDir root directory for storage
 * @param K type of key
 */
abstract class AbstractFileBasedStorage<K>(
    private val rootDir: Path
) : Storage<K> {
    init {
        rootDir.createDirectoriesIfRequired()
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override fun list(): Flux<K> = Files.walk(rootDir)
        .toFlux()
        .filter { it.isRegularFile() }
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
            content.map { byteBuffer ->
                contentPath.outputStream(StandardOpenOption.APPEND).use { os ->
                    Channels.newChannel(os).use { it.write(byteBuffer) }
                }
            }.collect(Collectors.summingInt { it })
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

    /**
     * @param key
     */
    internal fun buildPathToContent(key: K): Path = buildPathToContent(rootDir, key)

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

    companion object {
        const val DEFAULT_PROJECT_LOCATION = "default"
    }

    /**
     * @param rootDir root directory
     * @param K type of inner key
     */
    abstract class WithProjectCoordinates<K>(
        rootDir: Path
    ) : AbstractFileBasedStorage<Storage.WithProjectCoordinates.Key<K>>(rootDir), Storage.WithProjectCoordinates<K> {
        private val defaultProjectPath: Path by lazy {
            buildPathToDefaultProject(rootDir)
        }

        override fun buildPathToContent(rootDir: Path, key: Storage.WithProjectCoordinates.Key<K>): Path {
            val projectPath = buildPathToProject(rootDir, key.projectCoordinates)
            return buildPathToContentFromProjectPath(projectPath, key.key)
        }

        /**
         * @param projectPath
         * @param innerKey
         * @return [Path] to content
         */
        protected abstract fun buildPathToContentFromProjectPath(projectPath: Path, innerKey: K): Path

        private fun buildPathToProject(rootDir: Path, projectCoordinates: ProjectCoordinates?): Path = projectCoordinates
            ?.let {
                rootDir.resolve(it.organizationName).resolve(it.projectName)
            } ?: defaultProjectPath

        override fun buildKey(rootDir: Path, pathToContent: Path): Storage.WithProjectCoordinates.Key<K> {
            val (innerKey, projectPath) = buildInnerKeyAndReturnProjectPath(pathToContent)
            val projectCoordinates = when (projectPath) {
                defaultProjectPath -> null
                rootDir -> throw IllegalArgumentException("Failed to detect projectCoordinates for $pathToContent")
                else -> ProjectCoordinates(
                    organizationName = projectPath.parent.name,
                    projectName = projectPath.name,
                )
            }
            return Storage.WithProjectCoordinates.Key(projectCoordinates, innerKey)
        }

        /**
         * @param pathToContent
         * @return pair of inner key [K] and [Path] to project
         */
        protected abstract fun buildInnerKeyAndReturnProjectPath(pathToContent: Path): Pair<K, Path>

        /**
         * @param rootDir
         * @return [Path] to default project, when [ProjectCoordinates] is null
         */
        protected open fun buildPathToDefaultProject(rootDir: Path): Path = rootDir.resolve(DEFAULT_PROJECT_LOCATION)

        /**
         * @param projectCoordinates
         * @param key
         * @return [Path] to content on file system
         */
        @Deprecated("avoid usage of this method: applicable only for file based storage")
        fun getPath(projectCoordinates: ProjectCoordinates?, key: K): Path = buildPathToContent(Storage.WithProjectCoordinates.Key(projectCoordinates, key))
    }
}
