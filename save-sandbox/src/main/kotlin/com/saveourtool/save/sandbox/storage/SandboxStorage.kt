package com.saveourtool.save.sandbox.storage

import com.saveourtool.save.storage.AbstractFileBasedStorage
import com.saveourtool.save.utils.pathNamesTill
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import java.nio.file.Path
import kotlin.io.path.div

/**
 * Storage implementation for sandbox
 */
@Component
class SandboxStorage(
    @Value("\${sandbox.file-storage-location}") fileStorageLocation: String,
) : AbstractFileBasedStorage<SandboxStorageKey>(Path.of(fileStorageLocation) / "sandbox", PATH_PARTS_COUNT) {
    @Suppress("DestructuringDeclarationWithTooManyEntries")
    override fun buildKey(rootDir: Path, pathToContent: Path): SandboxStorageKey {
        val (filename, typeName, userId) = pathToContent.pathNamesTill(rootDir)
        return SandboxStorageKey(
            userId.toLong(),
            SandboxStorageKeyType.valueOf(typeName),
            filename,
        )
    }

    override fun buildPathToContent(rootDir: Path, key: SandboxStorageKey): Path =
            rootDir / key.userId.toString() / key.type.name / key.fileName

    /**
     * @param userId
     * @param types
     * @return list of keys in storage with requested [SandboxStorageKey.type] and [SandboxStorageKey.userId]
     */
    fun list(
        userId: Long,
        vararg types: SandboxStorageKeyType
    ): Flux<SandboxStorageKey> = list().filter {
        it.userId == userId && it.type in types
    }

    companion object {
        private const val PATH_PARTS_COUNT = 3  // userId + key.type + fileName
    }
}
