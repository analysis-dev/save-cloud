package com.saveourtool.save.backend.configs

import com.saveourtool.save.service.LokiConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

/**
 * Class for properties
 *
 * @property preprocessorUrl url of preprocessor
 * @property orchestratorUrl url of save-orchestrator
 * @property demoUrl url of save-demo
 * @property initialBatchSize initial size of tests batch (for further scaling)
 * @property fileStorage configuration of file storage
 * @property scheduling configuration for scheduled tasks
 * @property agentSettings properties for save-agents
 * @property testAnalysisSettings properties of the flaky test detector.
 * @property loki config of loki service for logging
 */
@ConstructorBinding
@ConfigurationProperties(prefix = "backend")
data class ConfigProperties(
    val preprocessorUrl: String,
    val orchestratorUrl: String,
    val demoUrl: String,
    val initialBatchSize: Int,
    val fileStorage: FileStorageConfig,
    val scheduling: Scheduling = Scheduling(),
    val agentSettings: AgentSettings = AgentSettings(),
    val testAnalysisSettings: TestAnalysisSettings = TestAnalysisSettings(),
    val loki: LokiConfig? = null,
) {
    /**
     * @property location location of file storage
     */
    data class FileStorageConfig(
        val location: String,
    )

    /**
     * @property standardSuitesUpdateCron cron expression to schedule update of standard test suites (by default, every hour)
     * @property baseImagesBuildCron cron expression to schedule builds of base docker images for test executions on all supported SDKs
     */
    data class Scheduling(
        val standardSuitesUpdateCron: String = "0 0 */1 * * ?",
        val baseImagesBuildCron: String = "0 0 */1 * * ?",
    )

    /**
     * @property backendUrl the URL of save-backend that will be reported to
     *   save-agents.
     */
    data class AgentSettings(
        val backendUrl: String = DEFAULT_BACKEND_URL,
    )

    /**
     * @property slidingWindowSize the size of the sliding window (the maximum
     *   sample size preserved in memory for any given test).
     */
    data class TestAnalysisSettings(
        val slidingWindowSize: Int = DEFAULT_SLIDING_WINDOW_SIZE
    )

    private companion object {
        private const val DEFAULT_BACKEND_URL = "http://backend:5800"
        private const val DEFAULT_SLIDING_WINDOW_SIZE = Int.MAX_VALUE
    }
}
