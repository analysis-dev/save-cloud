package com.saveourtool.save.preprocessor.service

import com.saveourtool.save.core.config.TestConfig
import com.saveourtool.save.entities.*
import com.saveourtool.save.preprocessor.config.ConfigProperties
import com.saveourtool.save.testsuite.TestSuiteDto
import com.saveourtool.save.testsuite.TestSuitesSourceDto
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.io.File
import java.nio.file.Files.createTempDirectory
import java.nio.file.Path

@ExtendWith(SpringExtension::class)
@EnableConfigurationProperties(ConfigProperties::class)
@TestPropertySource("classpath:application.properties")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(
    TestDiscoveringService::class,
    TestsPreprocessorToBackendBridge::class,
)
class TestDiscoveringServiceTest {
    private val logger = LoggerFactory.getLogger(TestDiscoveringServiceTest::class.java)
    private val propertiesRelativePath = "examples/kotlin-diktat/save.properties"
    @Autowired private lateinit var testDiscoveringService: TestDiscoveringService
    private lateinit var tmpDir: Path
    private lateinit var rootTestConfig: TestConfig
    private lateinit var testSuitesSourceDto: TestSuitesSourceDto

    @MockBean
    private lateinit var configProperties: ConfigProperties

    @MockBean
    private lateinit var webClientCustomizer: WebClientCustomizer

    @BeforeAll
    fun setUp() {
        tmpDir = createTempDirectory(this::class.simpleName)
        Git.cloneRepository()
            .setURI("https://github.com/saveourtool/save-cli")
            .setDirectory(tmpDir.toFile())
            .call()
            .use {
                it.checkout().setName("993aa6228cba0a9f9075fb3aca8a0a8b9196a12a").call()
            }
        rootTestConfig = testDiscoveringService.getRootTestConfig(tmpDir.resolve("examples/kotlin-diktat").toString())
        val organization = Organization.stub(42)
        testSuitesSourceDto = TestSuitesSourceDto(
            organization.name,
            "Test",
            null,
            GitDto("https://github.com/saveourtool/save-cli"),
            "main",
            "examples/kotlin-diktat",
            "aaaaaa",
        )
    }

    @AfterAll
    fun tearDown() {
        tmpDir.toFile().deleteRecursively()
    }

    @Test
    fun `should discover test suites`() {
        val testSuites = testDiscoveringService.getAllTestSuites(
            rootTestConfig,
            testSuitesSourceDto,
            "not-provided"
        )

        logger.debug("Discovered test suites: $testSuites")
        Assertions.assertTrue(testSuites.isNotEmpty())
        Assertions.assertEquals("Autofix: Smoke Tests", testSuites.first().name)
    }

    @Test
    fun `should throw exception with invalid path for test suites discovering`() {
        assertThrows<IllegalArgumentException> {
            testDiscoveringService.getAllTestSuites(
                testDiscoveringService.getRootTestConfig(tmpDir.resolve("buildSrc").toString()),
                testSuitesSourceDto,
                "not-provided"
            )
        }
    }

    @Test
    fun `should discover tests`() {
        val testDtos = testDiscoveringService.getAllTests(
            rootTestConfig,
            listOf(
                createTestSuiteStub("Autofix: Smoke Tests"),
                createTestSuiteStub("DocsCheck"),
                createTestSuiteStub("Only Warnings: General"),
                createTestSuiteStub("Autofix and Warn"),
                createTestSuiteStub("Directory: Chapter 1"),
                createTestSuiteStub("Directory: Chapter2"),
                createTestSuiteStub("Directory: Chapter3"),
            )
        )
            .map {
                it.second
            }
            .toList()

        logger.debug("Discovered the following tests: $testDtos")
        Assertions.assertEquals(16, testDtos.size)
        Assertions.assertEquals(testDtos.size, testDtos.map { it.hash + it.filePath + it.testSuiteId }.distinct().size) {
            "Some tests have the same hash/filePath/testSuiteId combination in $testDtos"
        }
        Assertions.assertTrue(testDtos.none { File(it.filePath).isAbsolute }) {
            "Test should be stored with paths relative to their root config, but some are stored with absolute paths: $testDtos"
        }
    }

    private fun createTestSuiteStub(name: String) = mock<TestSuiteDto>().also {
        whenever(it.name).thenReturn(name)
    }
}
