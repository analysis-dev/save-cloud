package com.saveourtool.save.sandbox.storage

import com.saveourtool.save.storage.AbstractFileBasedStorage
import com.saveourtool.save.utils.pathNamesTill
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Path
import kotlin.io.path.div

/**
 * Storage implementation for sandbox
 */
@Component
class SandboxStorage(
    @Value("orchestrator.file-storage-location") fileStorageLocation: String,
) : AbstractFileBasedStorage<SandboxStorageKey>(
    Path.of(fileStorageLocation) / "sandbox"
) {
    @Suppress("DestructuringDeclarationWithTooManyEntries")
    override fun buildKey(rootDir: Path, pathToContent: Path): SandboxStorageKey {
        val (filename, typeName, username) = pathToContent.pathNamesTill(rootDir)
        return SandboxStorageKey(
            username,
            SandboxStorageKeyType.valueOf(typeName),
            filename,
        )
    }

    override fun buildPathToContent(rootDir: Path, key: SandboxStorageKey): Path =
            rootDir / key.userName / key.type.name / key.fileName
}
