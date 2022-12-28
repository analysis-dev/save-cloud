package com.saveourtool.save.testsuite

import com.saveourtool.save.domain.PluginType
import com.saveourtool.save.entities.DtoWithId
import kotlinx.serialization.Serializable

/**
 * @property name [com.saveourtool.save.entities.TestSuite.name]
 * @property description [com.saveourtool.save.entities.TestSuite.description]
 * @property source [com.saveourtool.save.entities.TestSuitesSource]
 * @property version snapshot version of [com.saveourtool.save.entities.TestSuitesSource]
 * @property language [com.saveourtool.save.entities.TestSuite.language]
 * @property tags [com.saveourtool.save.entities.TestSuite.tags]
 * @property id
 * @property plugins
 * @property isPublic
 */
@Serializable
data class TestSuiteDto(
    val name: String,
    val description: String?,
    val source: TestSuitesSourceDto,
    val version: String,
    val language: String? = null,
    val tags: List<String>? = null,
    override val id: Long? = null,
    val plugins: List<PluginType> = emptyList(),
    val isPublic: Boolean = true,
) : DtoWithId()
