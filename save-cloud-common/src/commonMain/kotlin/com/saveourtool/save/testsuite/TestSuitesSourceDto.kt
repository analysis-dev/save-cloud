package com.saveourtool.save.testsuite

import com.saveourtool.save.entities.GitDto
import com.saveourtool.save.validation.Validatable
import com.saveourtool.save.validation.isValidName
import com.saveourtool.save.validation.isValidPath
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
) : Validatable {
    override fun validate(): Boolean = validateName() && validateOrganizationName() && validateTestRootPath()

    /**
     * @return true if name is valid, false otherwise
     */
    @Suppress("FUNCTION_BOOLEAN_PREFIX")
    fun validateName(): Boolean = name.isValidName()

    /**
     * @return true if [organizationName] is set, false otherwise
     */
    @Suppress("FUNCTION_BOOLEAN_PREFIX")
    private fun validateOrganizationName(): Boolean = name.isNotBlank()

    /**
     * @return true if [testRootPath] is valid, false otherwise
     */
    @Suppress("FUNCTION_BOOLEAN_PREFIX")
    fun validateTestRootPath(): Boolean = testRootPath.isBlank() || testRootPath.isValidPath()

    companion object {
        val empty = TestSuitesSourceDto(
            "",
            "",
            "",
            GitDto.empty,
            "",
            "",
        )
    }
}
