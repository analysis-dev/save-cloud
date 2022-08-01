package com.saveourtool.save.entities

import com.saveourtool.save.domain.FileKey
import com.saveourtool.save.domain.Sdk
import com.saveourtool.save.domain.format
import com.saveourtool.save.execution.ExecutionDto
import com.saveourtool.save.execution.ExecutionStatus
import com.saveourtool.save.execution.ExecutionType
import com.saveourtool.save.utils.DATABASE_DELIMITER
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne

/**
 * @property project
 * @property startTime
 * @property endTime If the state is RUNNING we are not considering it, so it can never be null
 * @property status
 * @property testSuiteIds a list of test suite IDs, that should be executed under this Execution.
 * @property resourcesRootPath path to test resources, relative to shared volume mount point
 * @property batchSize Maximum number of returning tests per execution
 * @property type
 * @property version
 * @property allTests
 * @property runningTests
 * @property passedTests
 * @property failedTests
 * @property skippedTests
 * @property unmatchedChecks
 * @property matchedChecks
 * @property expectedChecks
 * @property unexpectedChecks
 * @property sdk
 * @property additionalFiles
 * @property user user that has started this execution
 * @property execCmd
 * @property batchSizeForAnalyzer
 */
@Suppress("LongParameterList")
@Entity
class Execution(

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "project_id")
    var project: Project,

    var startTime: LocalDateTime,

    var endTime: LocalDateTime?,

    @Enumerated(EnumType.STRING)
    var status: ExecutionStatus,

    var testSuiteIds: String?,

    var resourcesRootPath: String?,

    var batchSize: Int?,

    @Enumerated(EnumType.STRING)
    var type: ExecutionType,

    var version: String?,

    var allTests: Long,

    var runningTests: Long,

    var passedTests: Long,

    var failedTests: Long,

    var skippedTests: Long,

    var unmatchedChecks: Long,

    var matchedChecks: Long,

    var expectedChecks: Long,

    var unexpectedChecks: Long,

    var sdk: String,

    var additionalFiles: String,

    @ManyToOne
    @JoinColumn(name = "user_id")
    var user: User?,

    var execCmd: String?,

    var batchSizeForAnalyzer: String?,

) : BaseEntity() {
    /**
     * @return Execution dto
     */
    @Suppress("UnsafeCallOnNullableType")
    fun toDto() = ExecutionDto(
        id!!,
        status,
        type,
        version,
        startTime.toEpochSecond(ZoneOffset.UTC),
        endTime?.toEpochSecond(ZoneOffset.UTC),
        allTests,
        runningTests,
        passedTests,
        failedTests,
        skippedTests,
        unmatchedChecks,
        matchedChecks,
        expectedChecks,
        unexpectedChecks,
    )

    /**
     * Parse and get testSuiteIds as List<Long>
     *
     * @return list of TestSuite IDs
     */
    fun parseAndGetTestSuiteIds(): List<Long>? = this.testSuiteIds
        ?.split(DATABASE_DELIMITER)
        ?.map { it.trim().toLong() }

    /**
     * Format and set provided list of TestSuite IDs
     *
     * @param testSuiteIds list of TestSuite IDs
     */
    fun formatAndSetTestSuiteIds(testSuiteIds: List<Long>) {
        this.testSuiteIds = testSuiteIds
            .distinct()
            .sorted()
            .joinToString(DATABASE_DELIMITER)
    }

    /**
     * Parse and get additionalFiles as List<String>
     *
     * @return list of keys [FileKey] of additional files
     */
    fun parseAndGetAdditionalFiles(): List<FileKey> = FileKey.parseList(additionalFiles)

    /**
     * Appends additional file to existed formatted String
     *
     * @param fileKey a new key [FileKey] to additional file
     */
    fun appendAdditionalFile(fileKey: FileKey) {
        if (additionalFiles.isNotEmpty()) {
            additionalFiles += FileKey.OBJECT_DELIMITER
        }
        additionalFiles += fileKey.format()
    }

    /**
     * Format and set provided list of [FileKey]
     *
     * @param fileKeys list of [FileKey]
     */
    fun formatAndSetAdditionalFiles(fileKeys: List<FileKey>) {
        additionalFiles = fileKeys.format()
    }

    companion object {
        /**
         * Create a stub for testing. Since all fields are mutable, only required ones can be set after calling this method.
         *
         * @param project project instance
         * @return a execution
         */
        fun stub(project: Project) = Execution(
            project = project,
            startTime = LocalDateTime.now(),
            endTime = null,
            status = ExecutionStatus.RUNNING,
            testSuiteIds = null,
            resourcesRootPath = null,
            batchSize = 20,
            type = ExecutionType.GIT,
            version = null,
            allTests = 0,
            runningTests = 0,
            passedTests = 0,
            failedTests = 0,
            skippedTests = 0,
            unmatchedChecks = 0,
            matchedChecks = 0,
            expectedChecks = 0,
            unexpectedChecks = 0,
            sdk = Sdk.Default.toString(),
            additionalFiles = "",
            user = null,
            execCmd = null,
            batchSizeForAnalyzer = null,
        )
    }
}
