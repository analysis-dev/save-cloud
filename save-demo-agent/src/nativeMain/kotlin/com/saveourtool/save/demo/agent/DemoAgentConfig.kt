/**
 * Configuration data classes
 */

package com.saveourtool.save.demo.agent

import kotlinx.serialization.Serializable

/**
 * Data class that contains everything for save-demo-agent configuration
 *
 * @property demoConfiguration all the information about current demo e.g. maintainer and version
 * @property runConfiguration all the required information to run demo
 * @property serverConfiguration information required for save-demo-agent server configuration
 * @property demoUrl url of save-demo
 */
@Serializable
data class DemoAgentConfig(
    val demoUrl: String,
    val demoConfiguration: DemoConfiguration,
    val runConfiguration: RunConfiguration,
    val serverConfiguration: ServerConfiguration = ServerConfiguration(),
)

/**
 * Data class that contains the information that is used for demo file fetch
 *
 * @property organizationName name of organization that runs the demo
 * @property projectName name of project that runs the demo
 * @property version current version of demo
 */
@Serializable
data class DemoConfiguration(
    val organizationName: String,
    val projectName: String,
    val version: String = "manual",
)

/**
 * Data class that contains all the required information to run demo
 *
 * @property inputFileName name of input file name
 * @property configFileName name of config file or null if not supported
 * @property runCommand command that should be executed in order to launch demo run
 * @property outputFileName name of a file that contains the output information e.g. report
 * @property logFileName name of file that contains logs
 */
@Serializable
data class RunConfiguration(
    val inputFileName: String,
    val configFileName: String?,
    val runCommand: String,
    val outputFileName: String?,
    val logFileName: String = "logs.txt",
)

/**
 * Data class that contains all the required information to start save-demo-agent server
 *
 * @property port port number that server should run on, 23456 by default
 */
@Serializable
data class ServerConfiguration(
    val port: Int = 23456,
)
