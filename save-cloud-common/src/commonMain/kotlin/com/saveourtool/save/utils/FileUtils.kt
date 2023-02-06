/**
 * Utility methods to work with files using Okio
 */

package com.saveourtool.save.utils

import com.saveourtool.save.core.logging.logInfo
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

expect val fs: FileSystem

/**
 * Mark [this] file as executable. Sets permissions to rwxr--r--
 */
expect fun Path.markAsExecutable()

/**
 * Write content of [this] into a file [file]
 *
 * @receiver [ByteArray] to be written into a file
 * @param file target [Path]
 * @param mustCreate will be passed to Okio's [FileSystem.write]
 */
expect fun ByteArray.writeToFile(file: Path, mustCreate: Boolean = true)

/**
 * Parse config file
 * Notice that [C] should be serializable
 *
 * @param configPath path to toml config file
 * @return [C] filled with configuration information
 */
expect inline fun <reified C : Any> parseConfig(configPath: Path): C

/**
 * Parse config file
 * Notice that [C] should be serializable
 *
 * @param configName name of a toml config file, agent.toml by default
 * @return [C] filled with configuration information
 */
inline fun <reified C : Any> parseConfig(configName: String = "agent.toml"): C = parseConfig<C>(configName.toPath())
    .also { logInfo("Found ${configName.toPath()}.") }
