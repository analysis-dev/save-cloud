package com.saveourtool.save.test.analysis.algorithms

import com.saveourtool.save.test.analysis.api.TestRuns
import com.saveourtool.save.test.analysis.metrics.RegularTestMetrics
import com.saveourtool.save.test.analysis.metrics.TestMetrics
import com.saveourtool.save.test.analysis.results.IrregularTest

/**
 * A heuristic algorithm which accepts test history ([TestRuns]), pre-calculated
 * metrics ([TestMetrics]) and returns either a result ([IrregularTest]), or
 * `null` if this algorithm hasn't detected anything special about the test.
 */
fun interface Algorithm : Function2<TestRuns, RegularTestMetrics, IrregularTest?>
