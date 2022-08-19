package com.saveourtool.save.preprocessor.controllers

import com.saveourtool.save.entities.TestSuite
import com.saveourtool.save.preprocessor.service.GitPreprocessorService
import com.saveourtool.save.preprocessor.service.TestDiscoveringService
import com.saveourtool.save.preprocessor.service.TestsPreprocessorToBackendBridge
import com.saveourtool.save.preprocessor.utils.detectLatestSha1
import com.saveourtool.save.testsuite.TestSuitesSourceDto
import com.saveourtool.save.utils.*

import org.slf4j.Logger
import org.springframework.core.io.FileSystemResource
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import kotlin.io.path.div

/**
 * Preprocessor's controller for [com.saveourtool.save.entities.TestSuitesSource]
 */
@RestController
@RequestMapping("/test-suites-sources")
class TestSuitesPreprocessorController(
    private val gitPreprocessorService: GitPreprocessorService,
    private val testDiscoveringService: TestDiscoveringService,
    private val testsPreprocessorToBackendBridge: TestsPreprocessorToBackendBridge,
) {
    /**
     * Fetch new tests suites from provided source with specific version or latest if version is not provided
     *
     * @param testSuitesSourceDto source from which test suites need to be loaded
     * @param version version which needs to be loaded, null is latest
     * @return empty response
     */
    @PostMapping("/fetch")
    fun fetch(
        @RequestBody testSuitesSourceDto: TestSuitesSourceDto,
        @RequestParam(required = false) version: String?,
    ): Mono<Unit> = doFetch(
        testSuitesSourceDto,
        version?.let { Mono.just(it) } ?: detectLatestVersion(testSuitesSourceDto)
    ).lazyDefaultIfEmpty {
        with(testSuitesSourceDto) {
            version?.also {
                log.debug { "Test suites source $name in $organizationName already contains version $version" }
            } ?: log.debug { "There is no new version for $name in $organizationName" }
        }
    }

    private fun doFetch(
        testSuitesSourceDto: TestSuitesSourceDto,
        versionAsMono: Mono<String>,
    ): Mono<Unit> = versionAsMono
        .filterWhen { testsPreprocessorToBackendBridge.doesTestSuitesSourceContainVersion(testSuitesSourceDto, it).map(Boolean::not) }
        .flatMap { version ->
            fetchTestSuitesFromGit(testSuitesSourceDto, version)
                .map {
                    with(testSuitesSourceDto) {
                        log.info { "Loaded ${it.size} test suites from test suites source $name in $organizationName with version $version" }
                    }
                }
        }

    /**
     * Detect latest version of TestSuitesSource
     *
     * @param testSuitesSourceDto source of test suites
     * @return latest available version on source
     */
    private fun detectLatestVersion(
        testSuitesSourceDto: TestSuitesSourceDto,
    ): Mono<String> = Mono.fromCallable { testSuitesSourceDto.gitDto.detectLatestSha1(testSuitesSourceDto.branch) }

    private fun fetchTestSuitesFromGit(
        testSuitesSourceDto: TestSuitesSourceDto,
        sha1: String,
    ): Mono<List<TestSuite>> = gitPreprocessorService.cloneAndProcessDirectory(
        testSuitesSourceDto.gitDto,
        testSuitesSourceDto.branch,
        sha1
    ) { repositoryDirectory, creationTime ->
        val testRootPath = repositoryDirectory / testSuitesSourceDto.testRootPath
        gitPreprocessorService.archiveToTar(testRootPath) { archive ->
            testsPreprocessorToBackendBridge.saveTestsSuiteSourceSnapshot(
                testSuitesSourceDto,
                sha1,
                creationTime,
                FileSystemResource(archive)
            ).flatMap {
                testDiscoveringService.detectAndSaveAllTestSuitesAndTests(
                    repositoryPath = repositoryDirectory,
                    testSuitesSourceDto = testSuitesSourceDto,
                    version = sha1
                )
            }
        }
    }

    companion object {
        private val log: Logger = getLogger<TestSuitesPreprocessorController>()
    }
}
