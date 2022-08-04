package com.saveourtool.save.testsuite

import com.saveourtool.save.entities.GitDto
import kotlinx.serialization.Serializable

typealias TestSuitesSourceDtoList = List<TestSuitesSourceDto>

/**
 * @property organizationName
 * @property name
 * @property description
 * @property gitDto
 * @property branch
 * @property testRootPath
 */
@Serializable
data class TestSuitesSourceDto(
    val organizationName: String,
    val name: String,
    val description: String?,
    val gitDto: GitDto,
    val branch: String,
    val testRootPath: String,
)
