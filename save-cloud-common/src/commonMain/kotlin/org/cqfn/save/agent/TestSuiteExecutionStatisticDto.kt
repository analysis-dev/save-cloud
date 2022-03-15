package org.cqfn.save.agent

import org.cqfn.save.domain.TestResultStatus

import kotlinx.serialization.Serializable

/**
 * @property testSuiteName
 * @property countTest
 * @property countWithStatusTest
 * @property status
 */
@Serializable
data class TestSuiteExecutionStatisticDto(
    val testSuiteName: String,
    val countTest: Int,
    val countWithStatusTest: Int,
    val status: TestResultStatus,
)
