package org.cqfn.save.backend.repository

import org.cqfn.save.backend.configs.ConfigProperties
import org.cqfn.save.domain.FileInfo
import org.cqfn.save.domain.ImageInfo

import com.mchange.io.FileUtils
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

import java.io.File
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.APPEND
import java.util.*
import java.util.stream.Collectors

import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.io.path.outputStream
import kotlin.io.path.pathString

/**
 * A repository which gives access to the files in a designated file system location
 */
@Repository
class TimestampBasedFileSystemRepository(configProperties: ConfigProperties) {
    private val logger = LoggerFactory.getLogger(TimestampBasedFileSystemRepository::class.java)
    private val rootDir = (Paths.get(configProperties.fileStorage.location) / "storage").apply {
        if (!exists()) {
            createDirectories()
        }
    }
    private val rootDirImage = (Paths.get(configProperties.fileStorage.location) / "imageAvatar").apply {
        if (!exists()) {
            createDirectories()
        }
    }

    private fun getStorageDir(fileInfo: FileInfo) = rootDir.resolve(fileInfo.uploadedMillis.toString())

    private fun createStorageDir(fileInfo: FileInfo) = getStorageDir(fileInfo).createDirectory()

    /**
     * @return list of files in [rootDir]
     */
    fun getFilesList() = rootDir.listDirectoryEntries()
        .filter { it.isDirectory() }
        .flatMap { it.listDirectoryEntries() }

    /**
     * @param owner owner name
     * @return image of avatar for owner in [rootDirImage]
     */
    fun getAvatar(owner: String): Path? = rootDirImage.listDirectoryEntries()
        .filter { it.isDirectory() }
        .filter { it.name == owner }
        .flatMap { it.listDirectoryEntries() }
        .ifEmpty { return null }
        .single()

    /**
     * @param fileInfo a FileInfo based on which a file should be located
     * @return requested file as a [FileSystemResource]
     */
    fun getFile(fileInfo: FileInfo): FileSystemResource = getPath(fileInfo).let(::FileSystemResource)

    /**
     * @param file a file to save
     * @return a FileInfo describing a saved file
     */
    fun saveFile(file: Path): FileInfo {
        val destination = rootDir
            .resolve(file.getLastModifiedTime().toMillis().toString())
            .createDirectories()
            .resolve(file.name)
        logger.info("Saving a new file into $destination")
        file.copyTo(destination, overwrite = false)
        return FileInfo(file.name, file.getLastModifiedTime().toMillis(), file.fileSize())
    }

    /**
     * @param parts file parts
     * @return Mono with number of bytes saved
     * @throws FileAlreadyExistsException if file with this name already exists
     */
    fun saveFile(parts: Mono<FilePart>): Mono<FileInfo> = parts.flatMap { part ->
        val uploadedMillis = System.currentTimeMillis()
        rootDir
            .resolve(uploadedMillis.toString())
            .createDirectories()
            .resolve(part.filename()).run {
                if (notExists()) {
                    logger.info("Saving a new file from parts into $this")
                    createFile()
                }
                part.content().map { db ->
                    outputStream(APPEND).use { os ->
                        db.asInputStream().use {
                            it.copyTo(os)
                        }
                    }
                }
                    .collect(Collectors.summingLong { it })
                    .map {
                        logger.info("Saved $it bytes into $this")
                        FileInfo(name, uploadedMillis, it)
                    }
            }
    }

    /**
     * @param owner owner name
     * @param part file part
     * @return Mono with number of bytes saved
     * @throws FileAlreadyExistsException if file with this name already exists
     */
    fun saveImage(part: Mono<FilePart>, owner: String): Mono<ImageInfo> = part.flatMap { part ->
        val uploadedDir = rootDirImage.resolve(owner)

        uploadedDir.apply {
            if (exists()) {
                listDirectoryEntries().forEach { it.deleteIfExists() }
            }
        }.deleteIfExists()

        uploadedDir
            .createDirectories()
            .resolve(part.filename()).run {
                if (notExists()) {
                    logger.info("Saving a new file from parts into $this")
                    createFile()
                }
                part.content().map { db ->
                    outputStream(APPEND).use { os ->
                        db.asInputStream().use {
                            it.copyTo(os)
                        }
                    }
                }
                    .collect(Collectors.summingLong { it })
                    .map {
                        logger.info("Saved $it bytes into $this")
                        val base64 = Base64.getEncoder().encodeToString(FileUtils.getBytes(File(pathString)))
                        ImageInfo(name, base64, it)
                    }
            }
    }

    /**
     * Delete a file described by [fileInfo]
     *
     * @param fileInfo a [FileInfo] describing a file to be deleted
     * @return true if file has been deleted successfully, false otherwise
     */
    fun delete(fileInfo: FileInfo) = try {
        Files.walk(getStorageDir(fileInfo)).forEach {
            it.deleteExisting()
        }
        true
    } catch (fe: FileSystemException) {
        logger.error("Failed to delete file $fileInfo", fe)
        false
    }

    /**
     * @param fileInfo
     * @return path to the file in storage
     */
    fun getPath(fileInfo: FileInfo) = getStorageDir(fileInfo).resolve(fileInfo.name)
}
