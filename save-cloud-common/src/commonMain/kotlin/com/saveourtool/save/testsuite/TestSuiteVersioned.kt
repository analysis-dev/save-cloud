package com.saveourtool.save.testsuite

import com.saveourtool.save.domain.pluginName
import com.saveourtool.save.utils.PRETTY_DELIMITER
import kotlinx.serialization.Serializable

/**
 * @property id id from saved [com.saveourtool.save.entities.TestSuite], not nullable
 * @property name name from [com.saveourtool.save.entities.TestSuite]
 * @property sourceName name from [com.saveourtool.save.entities.TestSuitesSource] to which [com.saveourtool.save.entities.TestSuite] belongs
 * @property organizationName name from [com.saveourtool.save.entities.Organization] to which [com.saveourtool.save.entities.TestSuite] belongs
 * @property isLatestFetchedVersion true if [version] of [com.saveourtool.save.entities.TestSuite] is latest fetched in [com.saveourtool.save.entities.TestSuitesSource]
 * @property description description from [com.saveourtool.save.entities.TestSuite] or empty value
 * @property version snapshot version of [com.saveourtool.save.entities.TestSuitesSource]
 * @property language language [com.saveourtool.save.entities.TestSuite] or empty value
 * @property tags tags from [com.saveourtool.save.entities.TestSuite] combined to a single string
 * @property plugins plugins from [com.saveourtool.save.entities.TestSuite] combined to a single string
 */
@Serializable
data class TestSuiteVersioned(
    val id: Long,
    val name: String,
    val sourceName: String,
    val organizationName: String,
    var isLatestFetchedVersion: Boolean,
    val description: String,
    val version: String,
    val language: String,
    val tags: String,
    val plugins: String,
) {
    companion object {
        /**
         * @return [TestSuiteVersioned] created from [TestSuiteDto]
         */
        fun TestSuiteDto.toVersioned(): TestSuiteVersioned = TestSuiteVersioned(
            id = this.requiredId(),
            name = this.name,
            sourceName = this.source.name,
            organizationName = this.source.organizationName,
            isLatestFetchedVersion = this.version == this.source.latestFetchedVersion,
            description = this.description.orEmpty(),
            version = this.version,
            language = this.language.orEmpty(),
            tags = this.tags?.joinToString(PRETTY_DELIMITER).orEmpty(),
            plugins = this.plugins.joinToString(PRETTY_DELIMITER) { it.pluginName() },
        )
    }
}
