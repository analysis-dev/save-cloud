package com.saveourtool.save.test.analysis.results

import kotlinx.serialization.Serializable

/**
 * @property detailMessage the detail message string of this result.
 */
@Serializable
data class FlakyTest(override val detailMessage: String = "") : IrregularTest()
