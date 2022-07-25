package com.saveourtool.save.orchestrator.utils

import com.saveourtool.save.utils.warn
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.name

internal val allExecute = setOf(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_EXECUTE)

context(LoggingContext)
internal fun Path.tryMarkAsExecutable() {
    try {
        Files.setPosixFilePermissions(this, Files.getPosixFilePermissions(this) + allExecute)
    } catch (ex: UnsupportedOperationException) {
        logger.warn(ex) { "Failed to mark file ${this.name} as executable" }
    }
}
