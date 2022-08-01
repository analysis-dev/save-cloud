package com.saveourtool.save.entities

import com.saveourtool.save.testsuite.TestSuiteDto
import com.saveourtool.save.testsuite.TestSuiteType
import com.saveourtool.save.utils.DATABASE_DELIMITER

import java.time.LocalDateTime
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne

/**
 * @property type type of the test suite, one of [TestSuiteType]
 * @property name name of the test suite
 * @property project project, which this test suite belongs to
 * @property dateAdded date and time, when this test suite was added to the project
 * @property testRootPath location of save.properties file for this test suite, relative to project's root directory
 * @property testSuiteRepoUrl url of the repo with test suites
 * @property description description of the test suite
 * @property language
 * @property tags
 */
@Suppress("LongParameterList")
@Entity
class TestSuite(
    @Enumerated(EnumType.STRING)
    var type: TestSuiteType? = null,

    var name: String = "Undefined",

    var description: String? = "Undefined",

    @ManyToOne
    @JoinColumn(name = "project_id")
    var project: Project? = null,

    var dateAdded: LocalDateTime? = null,

    var testRootPath: String,

    var testSuiteRepoUrl: String? = null,

    var language: String? = null,

    var tags: String? = null,
) : BaseEntity() {
    /**
     * @return [tags] as a list of strings
     */
    fun tagsAsList() = tags?.split(DATABASE_DELIMITER)?.filter { it.isNotBlank() }.orEmpty()

    /**
     * @return Dto of testSuite
     */
    fun toDto() =
            TestSuiteDto(
                this.type,
                this.name,
                this.description,
                this.project,
                this.testRootPath,
                this.testSuiteRepoUrl,
                this.language,
                this.tagsAsList(),
            )

    companion object {
        /**
         * Concatenates [tags] using same format as [TestSuite.tagsAsList]
         *
         * @param tags list of tags
         * @return representation of [tags] as a single string understood by [TestSuite.tagsAsList]
         */
        fun tagsFromList(tags: List<String>) = tags.joinToString(separator = DATABASE_DELIMITER)
    }
}
