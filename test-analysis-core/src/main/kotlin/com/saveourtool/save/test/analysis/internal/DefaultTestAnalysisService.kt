package com.saveourtool.save.test.analysis.internal

import com.saveourtool.save.test.analysis.algorithms.Algorithm
import com.saveourtool.save.test.analysis.api.TestAnalysisService
import com.saveourtool.save.test.analysis.api.TestId
import com.saveourtool.save.test.analysis.api.TestStatisticsStorage
import com.saveourtool.save.test.analysis.api.metrics.NoDataAvailable
import com.saveourtool.save.test.analysis.api.metrics.RegularTestMetrics
import com.saveourtool.save.test.analysis.api.results.AnalysisResult
import com.saveourtool.save.test.analysis.api.results.RegularTest

/**
 * The default implementation of [TestAnalysisService].
 *
 * @property statisticsStorage the storage of statistical data about test runs.
 * @property algorithms the algorithms used by this service.
 */
internal class DefaultTestAnalysisService(
    override val statisticsStorage: TestStatisticsStorage,
    private vararg val algorithms: Algorithm
) : TestAnalysisService {
    override fun analyze(id: TestId): List<AnalysisResult> {
        val testRuns = statisticsStorage.getExecutionStatistics(id)

        return when (val metrics = statisticsStorage.getTestMetrics(id)) {
            is NoDataAvailable -> listOf(RegularTest)
            is RegularTestMetrics -> algorithms
                .asSequence()
                .map { algorithm ->
                    algorithm(testRuns, metrics)
                }
                .filterNotNull()
                .toList()
                .ifEmpty {
                    listOf(RegularTest)
                }
        }
    }
}
