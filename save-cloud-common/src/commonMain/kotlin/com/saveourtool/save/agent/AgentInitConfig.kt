package com.saveourtool.save.agent

import kotlinx.serialization.Serializable

/**
 * @property saveCliUrl an url to download save-cli
 * @property testSuitesSourceSnapshotUrl an url to download snapshot of test suites source with tests
 * @property additionalFileNameToUrl a map of file name to url to download additional file
 * @property saveCliOverrides overrides for save-cli
 */
@Serializable
data class AgentInitConfig(
    val saveCliUrl: String,
    val testSuitesSourceSnapshotUrl: String,
    val additionalFileNameToUrl: Map<String, String>,
    val saveCliOverrides: SaveCliOverrides,
)
