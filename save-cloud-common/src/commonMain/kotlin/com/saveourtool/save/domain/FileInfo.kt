@file:Suppress("HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE")

package com.saveourtool.save.domain

import kotlinx.serialization.Serializable

/**
 * @property name name of a file
 * @property uploadedMillis timestamp of file uploading
 * @property sizeBytes size in bytes
 * @property isExecutable
 */
@Serializable
data class FileInfo(
    val name: String,
    val uploadedMillis: Long,
    val sizeBytes: Long,
    val isExecutable: Boolean = false,
) {
    /**
     * @return ShortFileInfo
     */
    fun toShortFileInfo() = ShortFileInfo(
        this.name,
        this.uploadedMillis,
        this.isExecutable,
    )

    /**
     * @return [FileKey]
     */
    fun toStorageKey() = FileKey(
        this.name,
        this.uploadedMillis,
    )
}

/**
 * @property name name of a file
 * @property uploadedMillis timestamp of file uploading
 * @property isExecutable whether the file is executable
 */
@Serializable
data class ShortFileInfo(
    val name: String,
    val uploadedMillis: Long,
    val isExecutable: Boolean = false,
) {
    /**
     * @return [FileKey]
     */
    fun toStorageKey() = FileKey(
        this.name,
        this.uploadedMillis,
    )
}

/**
 * @property path path to image
 */
@Serializable
data class ImageInfo(
    val path: String?,
)
