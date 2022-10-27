package com.saveourtool.save.request

import com.saveourtool.save.domain.Sdk
import kotlinx.serialization.Serializable

/**
 * @property executionId ID of [com.saveourtool.save.entities.Execution]
 * @property sdk
 * @property saveAgentVersion version of save-agent [generated.SAVE_CLOUD_VERSION]
 * @property saveAgentUrl URL to download save-agent
 */
@Serializable
data class RunExecutionRequest(
    val executionId: Long,
    val sdk: Sdk,
    val saveAgentVersion: String,
    val saveAgentUrl: String,
)
