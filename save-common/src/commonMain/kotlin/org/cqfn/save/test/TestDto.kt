package org.cqfn.save.test

import kotlinx.serialization.Serializable
import org.cqfn.save.testsuite.TestSuiteDto

/**
 * @property filePath
 * @property testSuiteDto
 * @property hash
 */
@Serializable
data class TestDto(
    var filePath: String,
    var hash: String,
    var testSuiteId: Long,
)