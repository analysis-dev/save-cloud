/**
 * SDK which are supported for test execution in save-cloud
 */

package com.saveourtool.save.domain

import kotlinx.serialization.Serializable

private const val GHCR_SAVE_BASE_URL = "ghcr.io/saveourtool/save-base"

val sdks = listOf("Default", Jdk.NAME, Python.NAME)

/**
 * @property name name of the SDK
 * @property version
 */
@Serializable
open class Sdk(val name: String, open val version: String) {
    /**
     * Should be used when no particular SDK is required
     */
    object Default : Sdk("ubuntu", "latest")

    /**
     * Fixme: we sometimes rely on this method, so this prevents child classes from being `data class`es
     */
    final override fun toString() = "$name:$version"

    /**
     * @return name like `save-base:openjdk-11`
     */
    fun baseImageName() = "$GHCR_SAVE_BASE_URL:${toString().replace(":", "-")}"
}

/**
 * @property version version of JDK
 */
class Jdk(override val version: String) : Sdk("eclipse-temurin", version) {
    companion object {
        const val NAME = "Java"
        val versions = listOf("8", "11", "17")
    }
}

/**
 * @property version version of Python
 */
class Python(override val version: String) : Sdk("python", version) {
    companion object {
        const val NAME = "Python"
        val versions = listOf("2.7", "3.2", "3.3", "3.4", "3.5", "3.6", "3.7", "3.8", "3.9", "3.10")
    }
}

/**
 * Parse string to sdk
 *
 * @return sdk by string
 */
fun String.toSdk(): Sdk {
    val splitSdk = this.split(":")
    require(splitSdk.size == 2) { "Cant find correct sdk and version" }
    val (sdkType, sdkVersion) = splitSdk.run { this.first() to this.last() }
    return when (sdkType) {
        Jdk.NAME, Jdk("-1").name -> Jdk(sdkVersion)
        Python.NAME, Python("-1").name -> Python(sdkVersion)
        else -> Sdk.Default
    }
}

/**
 * @return all version by sdk name
 */
fun String.getSdkVersions(): List<String> =
        when (this) {
            Jdk.NAME -> Jdk.versions
            Python.NAME -> Python.versions
            else -> listOf(Sdk.Default.version)
        }
