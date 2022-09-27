/**
 * Utility methods to work with files using Okio
 */

package com.saveourtool.save.agent.utils

import com.saveourtool.save.agent.fs
import com.saveourtool.save.core.files.findAllFilesMatching
import okio.FileNotFoundException
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.posix.S_IRGRP
import platform.posix.S_IROTH
import platform.posix.S_IRUSR
import platform.posix.S_IWUSR
import platform.posix.S_IXUSR

/**
 * Extract path as ZIP archive to provided directory
 *
 * @param targetPath
 */
internal fun Path.extractZipTo(targetPath: Path) {
    require(fs.metadata(targetPath).isDirectory)
    logDebugCustom("Unzip ${fs.canonicalize(this)} into ${fs.canonicalize(targetPath)}")
    platform.posix.system("unzip $this -d $targetPath")
}

/**
 * Write content of [this] into a file [file]
 *
 * @receiver [ByteArray] to be written into a file
 * @param file target [Path]
 * @param mustCreate will be passed to Okio's [FileSystem.write]
 */
internal fun ByteArray.writeToFile(file: Path, mustCreate: Boolean = true) {
    fs.write(
        file = file,
        mustCreate = mustCreate,
    ) {
        write(this@writeToFile).flush()
    }
}

/**
 * Mark [this] file as executable. Sets permissions to rwxr--r--
 */
internal fun Path.markAsExecutable() {
    platform.posix.chmod(
        this.toString(),
        (S_IRUSR or S_IWUSR or S_IXUSR or S_IRGRP or S_IROTH).toUInt()
    )
}

/**
 * Read file as a list of strings
 *
 * @param filePath a file to read
 * @return list of string from file
 */
internal fun readFile(filePath: String): List<String> = try {
    val path = filePath.toPath()
    FileSystem.SYSTEM.read(path) {
        generateSequence { readUtf8Line() }.toList()
    }
} catch (e: FileNotFoundException) {
    logErrorCustom("Not able to find file in the following path: $filePath")
    emptyList()
}

/**
 * Read properties file as a map
 *
 * @param filePath a file to read
 * @return map of properties with values
 */
internal fun readProperties(filePath: String): Map<String, String> = readFile(filePath)
    .associate { line ->
        line.split("=").map { it.trim() }.let {
            require(it.size == 2)
            it.first() to it.last()
        }
    }

/**
 * @param pathToFile
 */
internal fun unzipIfRequired(
    pathToFile: Path,
) {
    // FixMe: for now support only .zip files
    if (pathToFile.name.endsWith(".zip")) {
        pathToFile.extractZipTo(pathToFile.parent!!)
        // fixme: need to store information about isExecutable in Execution (FileKey)
        pathToFile.parent!!.findAllFilesMatching {
            if (fs.metadata(it).isRegularFile) {
                it.markAsExecutable()
            }
            true
        }
        fs.delete(pathToFile, mustExist = true)
    }
}
