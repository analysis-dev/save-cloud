package com.saveourtool.save.backend.service

import com.saveourtool.save.backend.configs.ConfigProperties
import com.saveourtool.save.backend.repository.*
import com.saveourtool.save.backend.utils.ErrorMessage
import com.saveourtool.save.backend.utils.getOrThrowBadRequest
import com.saveourtool.save.domain.*
import com.saveourtool.save.entities.*
import com.saveourtool.save.execution.ExecutionStatus
import com.saveourtool.save.execution.TestingType
import com.saveourtool.save.utils.*

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

import java.time.LocalDateTime
import java.util.Optional

/**
 * Service that is used to manipulate executions
 */
@Suppress("LongParameterList")
@Service
class ExecutionService(
    private val executionRepository: ExecutionRepository,
    private val projectService: ProjectService,
    private val userRepository: UserRepository,
    private val testRepository: TestRepository,
    private val testExecutionRepository: TestExecutionRepository,
    @Lazy private val testSuitesService: TestSuitesService,
    private val configProperties: ConfigProperties,
    private val lnkContestProjectService: LnkContestProjectService,
    private val lnkContestExecutionService: LnkContestExecutionService,
    private val lnkExecutionTestSuiteService: LnkExecutionTestSuiteService,
    private val fileRepository: FileRepository,
    private val lnkExecutionFileRepository: LnkExecutionFileRepository,
    @Lazy private val agentService: AgentService,
    private val agentStatusService: AgentStatusService,
    private val testAnalysisService: TestAnalysisService,
) {
    private val log = LoggerFactory.getLogger(ExecutionService::class.java)

    /**
     * Find execution by id
     *
     * @param id id of execution
     * @return execution if it has been found
     */
    fun findExecution(id: Long): Execution? = executionRepository.findByIdOrNull(id)

    /**
     * Get execution by id
     *
     * @param id id of [Execution]
     * @return [Execution] or exception
     */
    fun getExecution(id: Long): Execution = findExecution(id).orNotFound {
        "Not found execution with id $id"
    }

    /**
     * @param execution execution that is connected to testSuite
     * @param testSuites list of manageable [TestSuite]
     * @param files list of manageable [File]
     */
    @Transactional
    fun save(execution: Execution, testSuites: Collection<TestSuite>, files: Collection<File> = emptyList()): Execution {
        val newExecution = executionRepository.save(execution)
        testSuites.map { LnkExecutionTestSuite(newExecution, it) }.let { lnkExecutionTestSuiteService.saveAll(it) }
        files.map { LnkExecutionFile(newExecution, it) }.let { lnkExecutionFileRepository.saveAll(it) }
        return newExecution
    }

    /**
     * @param srcExecution [Execution]
     * @param newStatus [Execution.status]
     * @throws ResponseStatusException
     */
    @Transactional
    fun updateExecutionStatus(srcExecution: Execution, newStatus: ExecutionStatus) {
        val executionId = srcExecution.requiredId()
        log.debug("Updating status to $newStatus on execution id = $executionId")
        if (newStatus == ExecutionStatus.OBSOLETE) {
            log.info {
                "Marking execution with id $executionId as obsolete. Additionally cleanup dependencies to this execution"
            }
            doDeleteDependencies(listOf(executionId))
        }
        val execution = executionRepository.findWithLockingById(executionId).orNotFound()
        val updatedExecution = execution.apply {
            status = newStatus
        }
        if (updatedExecution.status == ExecutionStatus.FINISHED || updatedExecution.status == ExecutionStatus.ERROR) {
            // execution is completed, we can update end time
            updatedExecution.endTime = LocalDateTime.now()

            if (execution.type == TestingType.CONTEST_MODE && updatedExecution.status == ExecutionStatus.FINISHED) {
                // maybe this execution is the new best execution under a certain contest
                lnkContestProjectService.updateBestExecution(execution)
            }

            // if the tests are stuck in the READY_FOR_TESTING or RUNNING status
            testExecutionRepository.findByStatusListAndExecutionId(listOf(TestResultStatus.READY_FOR_TESTING, TestResultStatus.RUNNING), execution.requiredId()).map { testExec ->
                log.debug {
                    "Test execution id=${testExec.id} has status ${testExec.status} while execution id=${updatedExecution.id} has status ${updatedExecution.status}. " +
                            "Will mark it ${TestResultStatus.INTERNAL_ERROR}"
                }
                testExec.status = TestResultStatus.INTERNAL_ERROR
                testExecutionRepository.save(testExec)
            }
        }
        executionRepository.save(updatedExecution)

        /*
         * Test analysis: update the in-memory statistics.
         */
        if (updatedExecution.status == ExecutionStatus.FINISHED) {
            testAnalysisService.updateStatistics(updatedExecution)
        }
    }

    /**
     * @param name name of project
     * @param organization organization of project
     * @return list of execution
     */
    fun getExecutionByNameAndOrganization(name: String, organization: Organization) =
            executionRepository.getAllByProjectNameAndProjectOrganization(name, organization)

    /**
     * @param name name of project
     * @param organization organization of project
     * @param start start date
     * @param end end date
     * @return list of executions
     */
    fun getExecutionByNameAndOrganizationAndStartTimeBetween(
        name: String,
        organization: Organization,
        start: LocalDateTime,
        end: LocalDateTime
    ) =
            executionRepository.findByProjectNameAndProjectOrganizationAndStartTimeBetween(name, organization, start, end)

    /**
     * Get latest (by start time an) execution by project name and organization
     *
     * @param name name of project
     * @param organizationName name of organization of project
     * @return execution or null if it was not found
     */
    fun getLatestExecutionByProjectNameAndProjectOrganizationName(name: String, organizationName: String): Optional<Execution> =
            executionRepository.findTopByProjectNameAndProjectOrganizationNameOrderByStartTimeDesc(name, organizationName)

    /**
     * Delete executions, except participating in contests, by project name and organization
     *
     * @param name name of project
     * @param organization organization of project
     * @return Unit
     */
    @Suppress("IDENTIFIER_LENGTH")
    @Transactional
    fun deleteExecutionExceptParticipatingInContestsByProjectNameAndProjectOrganization(name: String, organization: Organization) {
        executionRepository.getAllByProjectNameAndProjectOrganization(name, organization)
            .filter {
                lnkContestExecutionService.findContestByExecution(it) == null
            }
            .map { it.requiredId() }
            .let { deleteByIds(it) }
    }

    /**
     * Get all executions, which contains provided test suite id
     *
     * @param testSuiteId
     * @return list of [Execution]'s
     */
    fun getExecutionsByTestSuiteId(testSuiteId: Long): List<Execution> =
            lnkExecutionTestSuiteService.getAllExecutionsByTestSuiteId(testSuiteId)

    /**
     * @param projectCoordinates
     * @param testSuiteIds
     * @param fileIds
     * @param username
     * @param sdk
     * @param execCmd
     * @param batchSizeForAnalyzer
     * @param testingType
     * @param contestName
     * @return new [Execution] with provided values
     */
    @Suppress("LongParameterList", "TOO_MANY_PARAMETERS")
    @Transactional
    fun createNew(
        projectCoordinates: ProjectCoordinates,
        testSuiteIds: List<Long>,
        fileIds: List<Long>,
        username: String,
        sdk: Sdk,
        execCmd: String?,
        batchSizeForAnalyzer: String?,
        testingType: TestingType,
        contestName: String?,
    ): Execution {
        val project = with(projectCoordinates) {
            projectService.findByNameAndOrganizationNameAndCreatedStatus(projectName, organizationName).orNotFound {
                "Not found project $projectName in $organizationName"
            }
        }
        return doCreateNew(
            project = project,
            testSuites = testSuiteIds.map { testSuitesService.getById(it) },
            allTests = testSuiteIds.flatMap { testRepository.findAllByTestSuiteId(it) }
                .count()
                .toLong(),
            files = fileIds.map { fileId ->
                fileRepository.findByIdOrNull(fileId)
                    .orNotFound { "File (id=$fileId) not found" }
            },
            username = username,
            sdk = sdk.toString(),
            execCmd = execCmd,
            batchSizeForAnalyzer = batchSizeForAnalyzer,
            testingType = testingType,
            contestName,
        )
    }

    /**
     * @param execution
     * @param username
     * @return new [Execution] with values taken from [execution]
     */
    @Transactional
    fun createNewCopy(
        execution: Execution,
        username: String,
    ): Execution {
        val testSuites = lnkExecutionTestSuiteService.getAllTestSuitesByExecution(execution)
        val files = lnkExecutionFileRepository.findAllByExecution(execution).map { it.file }
        return doCreateNew(
            project = execution.project,
            testSuites = testSuites,
            allTests = execution.allTests,
            files = files,
            username = username,
            sdk = execution.sdk,
            execCmd = execution.execCmd,
            batchSizeForAnalyzer = execution.batchSizeForAnalyzer,
            testingType = execution.type,
            contestName = lnkContestExecutionService.takeIf { execution.type == TestingType.CONTEST_MODE }
                ?.findContestByExecution(execution)?.name,
        )
    }

    @Suppress("LongParameterList", "TOO_MANY_PARAMETERS", "UnsafeCallOnNullableType")
    private fun doCreateNew(
        project: Project,
        testSuites: List<TestSuite>,
        allTests: Long,
        files: List<File>,
        username: String,
        sdk: String,
        execCmd: String?,
        batchSizeForAnalyzer: String?,
        testingType: TestingType,
        contestName: String?,
    ): Execution {
        val user = userRepository.findByName(username).orNotFound {
            "Not found user $username"
        }
        val execution = Execution(
            project = project,
            startTime = LocalDateTime.now(),
            endTime = null,
            status = ExecutionStatus.PENDING,
            batchSize = configProperties.initialBatchSize,
            type = testingType,
            version = testSuites.singleVersion().getOrThrowBadRequest(),
            allTests = allTests,
            runningTests = 0,
            passedTests = 0,
            failedTests = 0,
            skippedTests = 0,
            unmatchedChecks = 0,
            matchedChecks = 0,
            expectedChecks = 0,
            unexpectedChecks = 0,
            sdk = sdk,
            user = user,
            execCmd = execCmd,
            batchSizeForAnalyzer = batchSizeForAnalyzer,
            testSuiteSourceName = testSuites.singleSourceName().getOrThrowBadRequest(),
            score = null,
        )

        val savedExecution = executionRepository.save(execution)
        testSuites.map { LnkExecutionTestSuite(savedExecution, it) }.let { lnkExecutionTestSuiteService.saveAll(it) }
        files.map { LnkExecutionFile(savedExecution, it) }.let { lnkExecutionFileRepository.saveAll(it) }
        if (testingType == TestingType.CONTEST_MODE) {
            lnkContestExecutionService.createLink(
                savedExecution, requireNotNull(contestName) {
                    "Requested execution type is ${TestingType.CONTEST_MODE} but no contest name has been specified"
                }
            )
        }
        log.info("Created a new execution id=${savedExecution.id} for project id=${project.id}")
        return savedExecution
    }

    /**
     * Unlink provided [File] from all [Execution]s
     *
     * @param file
     */
    @Transactional
    fun unlinkFileFromAllExecution(file: File) {
        lnkExecutionFileRepository.findAllByFile(file)
            .also {
                lnkExecutionFileRepository.deleteAll(it)
            }
            .map { it.execution }
            .forEach {
                updateExecutionStatus(it, ExecutionStatus.OBSOLETE)
            }
    }

    /**
     * @param execution
     * @return all [FileDto]s are assigned to provided [Execution]
     */
    fun getAssignedFiles(execution: Execution): List<FileDto> = lnkExecutionFileRepository.findAllByExecution(execution)
        .map { it.file.toDto() }

    /**
     * Delete [Execution] and links to TestSuite, TestExecution and related Agent with AgentStatus
     *
     * @param executionIds
     */
    @Transactional
    fun deleteByIds(executionIds: List<Long>) {
        log.info {
            "Deleting executions with id in $executionIds. Additionally cleanup dependencies to these executions"
        }
        // dependencies will be cleanup by cascade constrains
        executionRepository.deleteAllById(executionIds)
    }

    private fun doDeleteDependencies(executionIds: List<Long>) {
        log.info {
            "Delete dependencies to executions ($executionIds): links to test suites and files, agents with their statuses and test executions"
        }
        lnkExecutionTestSuiteService.deleteByExecutionIds(executionIds)
        lnkExecutionFileRepository.deleteAllByExecutionIdIn(executionIds)
        testExecutionRepository.deleteByExecutionIdIn(executionIds)
        agentStatusService.deleteAgentStatusWithExecutionIds(executionIds)
        agentService.deleteAgentByExecutionIds(executionIds)
    }

    companion object {
        private fun Collection<TestSuite>.singleSourceName(): Either<ErrorMessage, String> = map { it.source }
            .distinctBy { it.requiredId() }
            .let { sources ->
                when (sources.size) {
                    1 -> sources[0]
                        .name
                        .right()

                    else -> ErrorMessage("Only a single test suites source is allowed for a run, but got: ${sources.map(TestSuitesSource::toDto)}")
                        .left()
                }
            }

        private fun Collection<TestSuite>.singleVersion(): Either<ErrorMessage, String> = map { it.version }
            .distinct()
            .let { versions ->
                when (versions.size) {
                    1 -> versions[0]
                        .right()

                    else -> ErrorMessage("Only a single version is supported, but got: $versions")
                        .left()
                }
            }
    }
}
