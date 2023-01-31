package com.saveourtool.save.demo.service

import com.saveourtool.save.demo.DemoResult
import com.saveourtool.save.demo.DemoRunRequest
import com.saveourtool.save.demo.config.ConfigProperties
import com.saveourtool.save.demo.runners.cli.DiktatCliRunner
import com.saveourtool.save.demo.utils.KOTLIN_TEST_NAME

import org.springframework.stereotype.Service

import java.nio.file.Path

import kotlin.io.path.div

/**
 * Demo service implementation for diktat-demo
 */
@Service
class DiktatDemoService(
    private val diktatCliRunner: DiktatCliRunner,
    configProperties: ConfigProperties,
) : AbstractDemoService (diktatCliRunner) {
    private val tmpDir = Path.of(configProperties.fileStorage.location) / "tmp"

    /**
     * @param runRequest instance of [DemoRunRequest]
     */
    override fun launch(runRequest: DemoRunRequest): DemoResult = diktatCliRunner.runInTempDir(
        runRequest,
        tmpDir,
        testFileName = KOTLIN_TEST_NAME,
        additionalDirectoryTree = listOf("src"),
    )
}
