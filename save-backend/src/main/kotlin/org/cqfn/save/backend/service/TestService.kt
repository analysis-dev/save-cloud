package org.cqfn.save.backend.service

import org.cqfn.save.backend.configs.ConfigProperties
import org.cqfn.save.backend.repository.AgentRepository
import org.cqfn.save.backend.repository.ExecutionRepository
import org.cqfn.save.backend.repository.TestExecutionRepository
import org.cqfn.save.backend.repository.TestRepository
import org.cqfn.save.domain.TestResultStatus
import org.cqfn.save.backend.repository.TestSuiteRepository
import org.cqfn.save.entities.Test
import org.cqfn.save.test.TestBatchDto
import org.cqfn.save.test.TestDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * Service that is used for manipulating data with tests
 */
@Service
class TestService(private val configProperties: ConfigProperties) {
    private val log = LoggerFactory.getLogger(TestService::class.java)

    @Autowired
    private lateinit var testRepository: TestRepository

    @Autowired
    private lateinit var agentRepository: AgentRepository

    @Autowired
    private lateinit var executionRepository: ExecutionRepository

    @Autowired
    private lateinit var testExecutionRepository: TestExecutionRepository

    @Autowired
    private lateinit var testSuiteRepository: TestSuiteRepository

    /**
     * @param tests
     */
    fun saveTests(tests: List<TestDto>) {
        tests.map { testDto ->
            testSuiteRepository.findById(testDto.testSuiteId).ifPresent {
                testRepository.findByHash(testDto.hash).ifPresentOrElse(
                    {},
                    { testRepository.save(Test(testDto.hash, testDto.filePath, LocalDateTime.now(), it)) })
            }
        }
    }

    /*
    /**
     * @return Test batches
     */
    fun getTestBatches(): Mono<List<TestBatchDto>> {
        val tests = testRepository.retrieveBatches(configProperties.limit, offset.get()).map {
            TestBatchDto(it.filePath, it.testSuite.id!!, it.id!!)
        }
        offset.addAndGet(configProperties.limit)
        return Mono.just(tests)
    }
     */

    /**
     * @param agentId
     * @return Test batches
     */
    @Transactional
    fun getTestBatches(agentId: String): Mono<List<TestDto>> {
        val agent = agentRepository.findByContainerId(agentId) ?: error("The specified agent does not exist")
        log.debug("Agent found: $agent")
        val execution = agent.execution
        log.debug("Retrieving tests")
        val tests = testExecutionRepository.findByStatusAndTestSuiteExecutionId(
            TestResultStatus.READY,
            execution.id!!,
            PageRequest.of(execution.page, execution.batchSize)
        ).map {
            TestDto(it.test.filePath, it.test.testSuite.id!!, it.test.id!!)
        }
        log.debug("Increasing offset of the execution - ${agent.execution}")
        ++execution.page
        executionRepository.save(execution)
        return Mono.just(tests)
    }
}
