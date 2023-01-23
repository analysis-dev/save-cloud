package com.saveourtool.save.preprocessor.controllers

import com.saveourtool.save.entities.GitDto
import com.saveourtool.save.entities.TestSuite
import com.saveourtool.save.preprocessor.service.*
import com.saveourtool.save.preprocessor.utils.GitCommitInfo
import com.saveourtool.save.request.TestsSourceFetchRequest
import com.saveourtool.save.test.TestsSourceSnapshotDto
import com.saveourtool.save.test.TestsSourceVersionDto
import com.saveourtool.save.testsuite.TestSuitesSourceDto
import com.saveourtool.save.testsuite.TestSuitesSourceFetchMode
import com.saveourtool.save.utils.getCurrentLocalDateTime
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.*
import reactor.core.publisher.Mono
import java.nio.file.Paths

typealias TestSuiteList = List<TestSuite>

internal class TestSuitesPreprocessorControllerTest {
    private val gitPreprocessorService: GitPreprocessorService = mock()
    private val testDiscoveringService: TestDiscoveringService = mock()
    private val testsPreprocessorToBackendBridge: TestsPreprocessorToBackendBridge = mock()
    private val testSuitesPreprocessorController = TestSuitesPreprocessorController(
        gitPreprocessorService,
        testDiscoveringService,
        testsPreprocessorToBackendBridge,
    )
    private val gitDto = GitDto("https://github.com/saveourtool/save-cli")
    private val sourceId = 2L
    private val testSuitesSourceDto = TestSuitesSourceDto(
        "Organization",
        "TestSuitesSource",
        null,
        gitDto,
        "examples/discovery-test",
        "aaaaaa",
        sourceId,
    )
    private val repositoryDirectory = Paths.get("./some-folder")
    private val testLocations = repositoryDirectory.resolve(testSuitesSourceDto.testRootPath)
    private val tag = "v0.1"
    private val branch = "main"
    private val branchVersion = "main (123456)"
    private val commit = "1234567"
    private val fullCommit = "1234567890"
    private val commitTime = getCurrentLocalDateTime()
    private val userId = 123L
    private val snapshotId = 3L
    private val testsSourceSnapshotDtoCandidate = TestsSourceSnapshotDto(
        sourceId = sourceId,
        commitId = fullCommit,
        commitTime = commitTime,
    )
    private val testsSourceSnapshotDto = testsSourceSnapshotDtoCandidate.copy(
        id = snapshotId,
    )

    @BeforeEach
    fun setup() {
        val answerCall = { answer: InvocationOnMock ->
            @Suppress("UNCHECKED_CAST")
            val processor = answer.arguments[2] as GitRepositoryProcessor<TestSuiteList>
            processor(repositoryDirectory, GitCommitInfo(fullCommit, commitTime))
        }
        doAnswer(answerCall).whenever(gitPreprocessorService)
            .cloneTagAndProcessDirectory<TestSuiteList>(eq(gitDto), eq(tag), any())
        doAnswer(answerCall).whenever(gitPreprocessorService)
            .cloneBranchAndProcessDirectory<TestSuiteList>(eq(gitDto), eq(branch), any())
        doAnswer(answerCall).whenever(gitPreprocessorService)
            .cloneCommitAndProcessDirectory<TestSuiteList>(eq(gitDto), eq(commit), any())
        doAnswer { answer ->
            @Suppress("UNCHECKED_CAST")
            val processor = answer.arguments[1] as ArchiveProcessor<TestSuiteList>
            processor(testLocations)
        }.whenever(gitPreprocessorService).archiveToTar<TestSuiteList>(eq(testLocations), any())

        whenever(testsPreprocessorToBackendBridge.saveTestsSuiteSourceSnapshot(eq(testsSourceSnapshotDtoCandidate), any()))
            .thenReturn(Mono.just(testsSourceSnapshotDto))
        whenever(testsPreprocessorToBackendBridge.saveTestsSourceVersion(any()))
            .thenReturn(Mono.just(Unit))

        whenever(testDiscoveringService.detectAndSaveAllTestSuitesAndTests(eq(repositoryDirectory), eq(testSuitesSourceDto), any()))
            .thenReturn(Mono.just(emptyList()))
    }

    @Test
    fun fetchFromTagSuccessful() {
        whenever(testsPreprocessorToBackendBridge.findTestsSourceSnapshot(any(), any()))
            .thenReturn(Mono.empty())
        testSuitesPreprocessorController.fetch(TestsSourceFetchRequest(testSuitesSourceDto, TestSuitesSourceFetchMode.BY_TAG, tag, userId))
            .block()

        verify(gitPreprocessorService).cloneTagAndProcessDirectory<TestSuiteList>(eq(gitDto), eq(tag), any())
        verify(testsPreprocessorToBackendBridge).saveTestsSourceVersion(testsSourceVersionDto(tag, TestSuitesSourceFetchMode.BY_TAG))
        verifyNewCommit()
    }

    @Test
    fun fetchFromTagAlreadyContains() {
        whenever(testsPreprocessorToBackendBridge.findTestsSourceSnapshot(any(), any()))
            .thenReturn(Mono.just(testsSourceSnapshotDto))
        testSuitesPreprocessorController.fetch(TestsSourceFetchRequest(testSuitesSourceDto, TestSuitesSourceFetchMode.BY_TAG, tag, userId))
            .block()

        verify(gitPreprocessorService).cloneTagAndProcessDirectory<TestSuiteList>(eq(gitDto), eq(tag), any())
        verify(testsPreprocessorToBackendBridge).saveTestsSourceVersion(testsSourceVersionDto(tag, TestSuitesSourceFetchMode.BY_TAG))
        verifyExistedCommit()
    }

    @Test
    fun fetchFromBranchSuccessful() {
        whenever(testsPreprocessorToBackendBridge.findTestsSourceSnapshot(any(), any()))
            .thenReturn(Mono.empty())
        testSuitesPreprocessorController.fetch(TestsSourceFetchRequest(testSuitesSourceDto, TestSuitesSourceFetchMode.BY_BRANCH, branch, userId))
            .block()

        verify(gitPreprocessorService).cloneBranchAndProcessDirectory<TestSuiteList>(eq(gitDto), eq(branch), any())
        verify(testsPreprocessorToBackendBridge).saveTestsSourceVersion(testsSourceVersionDto(branchVersion, TestSuitesSourceFetchMode.BY_BRANCH))
        verifyNewCommit()
    }

    @Test
    fun fetchCommitSuccessful() {
        whenever(testsPreprocessorToBackendBridge.findTestsSourceSnapshot(any(), any()))
            .thenReturn(Mono.empty())
        testSuitesPreprocessorController.fetch(TestsSourceFetchRequest(testSuitesSourceDto, TestSuitesSourceFetchMode.BY_COMMIT, commit, userId))
            .block()

        verify(gitPreprocessorService).cloneCommitAndProcessDirectory<TestSuiteList>(eq(gitDto), eq(commit), any())
        verify(testsPreprocessorToBackendBridge).saveTestsSourceVersion(testsSourceVersionDto(commit, TestSuitesSourceFetchMode.BY_COMMIT))
        verifyNewCommit()
    }

    @Test
    fun fetchCommitAlreadyContains() {
        whenever(testsPreprocessorToBackendBridge.findTestsSourceSnapshot(any(), any()))
            .thenReturn(Mono.just(testsSourceSnapshotDto))
        testSuitesPreprocessorController.fetch(TestsSourceFetchRequest(testSuitesSourceDto, TestSuitesSourceFetchMode.BY_COMMIT, commit, userId))
            .block()

        verify(gitPreprocessorService).cloneCommitAndProcessDirectory<TestSuiteList>(eq(gitDto), eq(commit), any())
        verify(testsPreprocessorToBackendBridge).saveTestsSourceVersion(testsSourceVersionDto(commit, TestSuitesSourceFetchMode.BY_COMMIT))
        verifyExistedCommit()
    }

    private fun verifyNewCommit() {
        verify(testsPreprocessorToBackendBridge).findTestsSourceSnapshot(eq(sourceId), eq(fullCommit))
        verify(gitPreprocessorService).archiveToTar<TestSuiteList>(eq(testLocations), any())
        verify(testsPreprocessorToBackendBridge).saveTestsSuiteSourceSnapshot(eq(testsSourceSnapshotDtoCandidate), any())
        verify(testDiscoveringService).detectAndSaveAllTestSuitesAndTests(eq(repositoryDirectory), eq(testSuitesSourceDto), eq(fullCommit))
        verifyNoMoreInteractions(testsPreprocessorToBackendBridge, gitPreprocessorService, testDiscoveringService)
    }

    private fun verifyExistedCommit() {
        verify(testsPreprocessorToBackendBridge).findTestsSourceSnapshot(eq(sourceId), eq(fullCommit))
        verifyNoMoreInteractions(testsPreprocessorToBackendBridge)
        verifyNoMoreInteractions(gitPreprocessorService)
        verifyNoInteractions(testDiscoveringService)
    }

    private fun testsSourceVersionDto(
        version: String,
        mode: TestSuitesSourceFetchMode,
    ): TestsSourceVersionDto = argThat { value ->
        value.snapshotId == snapshotId &&
                value.name == version &&
                value.type == mode &&
                value.createdByUserId == userId
    }
}
