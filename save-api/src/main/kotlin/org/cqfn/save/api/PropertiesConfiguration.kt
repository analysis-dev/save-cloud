/**
 * Logic for configuration for http client and evaluated tool
 */

package org.cqfn.save.api

import org.cqfn.save.domain.Jdk
import org.cqfn.save.domain.Python
import org.cqfn.save.domain.Sdk

import org.slf4j.LoggerFactory

import java.io.File
import java.io.IOException

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.decodeFromStringMap

private val log = LoggerFactory.getLogger(PropertiesConfiguration::class.java)

/**
 * Available types of configurations
 */
enum class PropertiesConfigurationType {
    EVALUATED_TOOL,
    WEB_CLIENT,
    ;
}

/**
 * Base class for configuration
 */
@Serializable
sealed class PropertiesConfiguration

/**
 * @property backendUrl
 */
@Serializable
data class WebClientProperties(
    val backendUrl: String,
) : PropertiesConfiguration()

/**
 * @property organizationName
 * @property projectName
 * @property sdk
 * @property gitUrl
 * @property gitUserName
 * @property gitPassword
 * @property branch
 * @property commitHash
 * @property testRootPath
 * @property additionalFiles
 * @property testSuites
 * @property execCmd
 * @property batchSize
 */
@Serializable
data class EvaluatedToolProperties(
    val organizationName: String,
    val projectName: String,
    val sdk: String? = null,
    val gitUrl: String,
    val gitUserName: String? = null,
    val gitPassword: String? = null,
    val branch: String? = null,
    val commitHash: String? = null,
    val testRootPath: String,
    val additionalFiles: String? = null,
    val testSuites: String,
    val execCmd: String? = null,
    val batchSize: String? = null,
) : PropertiesConfiguration()

/**
 * @return sdk instance converted from string representation
 * @throws IllegalArgumentException in case of invalid configuration
 */
internal fun String?.toSdk(): Sdk {
    this ?: run {
        log.info("Setting SDK to default value: Java 11")
        return Jdk("11")
    }
    val sdk = this.split(" ").map { it.trim() }
    require(sdk.size == 2) {
        "SDK should have the environment and version separated by whitespace, e.g.: `Java 11`, but found ${this}."
    }
    return if (sdk.first().lowercase() == "java" && sdk.last() in Jdk.versions) {
        Jdk(sdk.last())
    } else if (sdk.first().lowercase() == "python" && sdk.last() in Python.versions) {
        Python(sdk.last())
    } else {
        throw IllegalArgumentException(
            """
            Provided SDK $sdk have incorrect value!
            Available list of SDK:
            Java: ${Jdk.versions.map { "Java $it" }}
            Python: ${Python.versions.map { "Python $it" }}
            """.trimMargin()
        )
    }
}

/**
 * Read config file [configFileName] and return [PropertiesConfiguration] instance
 *
 * @param configFileName
 * @param type
 * @return corresponding configuration
 */
@OptIn(ExperimentalSerializationApi::class)
@Suppress("TOO_LONG_FUNCTION")
fun readPropertiesFile(configFileName: String, type: PropertiesConfigurationType): PropertiesConfiguration? {
    try {
        val classLoader = AutomaticTestInitializator::class.java.classLoader
        val input = classLoader.getResource(configFileName)?.file
        input ?: run {
            log.error("Unable to find configuration file: $configFileName")
            return null
        }
        when (type) {
            PropertiesConfigurationType.WEB_CLIENT -> return Properties.decodeFromStringMap<WebClientProperties>(
                readProperties(input)
            )

            PropertiesConfigurationType.EVALUATED_TOOL -> return Properties.decodeFromStringMap<EvaluatedToolProperties>(
                readProperties(input)
            )
            else -> {
                log.error("Type $type for properties configuration doesn't supported!")
                return null
            }
        }
    } catch (ex: IOException) {
        ex.printStackTrace()
        return null
    }
}

/**
 * Read properties file as a map
 *
 * @param filePath a file to read
 * @return map of properties with values
 */
private fun readProperties(filePath: String): Map<String, String> = File(filePath).readLines()
    .filter { it.contains("=") }
    .associate { line ->
        line.split("=").map { it.trim() }.let {
            require(it.size == 2)
            it.first() to it.last()
        }
    }
