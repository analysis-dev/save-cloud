package com.saveourtool.save.entities

import com.saveourtool.save.testsuite.TestSuitesSourceDto
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne

/**
 * @param organization which this test suites source belongs to
 * @param name unique name of [TestSuitesSource]
 * @param description free text
 * @param git git credentials for this test suites source
 * @param branch branch which is used for this test suites source
 * @param testRootPath relative path to tests in source
 * @property organization
 * @property name
 * @property description
 * @property git
 * @property branch
 * @property testRootPath
 * @property latestFetchedVersion
 */
@Entity
@Suppress("LongParameterList")
class TestSuitesSource(
    @ManyToOne
    @JoinColumn(name = "organization_id")
    var organization: Organization,

    var name: String,
    var description: String?,

    @ManyToOne
    @JoinColumn(name = "git_id")
    var git: Git,
    var branch: String,
    var testRootPath: String,
    var latestFetchedVersion: String?,
) : BaseEntity() {
    /**
     * @return entity as dto [TestSuitesSourceDto]
     */
    fun toDto(): TestSuitesSourceDto = TestSuitesSourceDto(
        organizationName = organization.name,
        name = name,
        description = description,
        gitDto = git.toDto(),
        branch = branch,
        testRootPath = testRootPath,
        latestFetchedVersion = latestFetchedVersion
    )

    companion object {
        val empty = TestSuitesSource(
            Organization.stub(-1),
            "",
            null,
            Git.empty,
            "",
            "",
            null,
        )

        /**
         * @param organization [Organization] from database
         * @param git [Git] from database
         * @param latestFetchedVersion
         * @return [TestSuitesSource] from [TestSuitesSourceDto]
         */
        fun TestSuitesSourceDto.toTestSuiteSource(
            organization: Organization,
            git: Git,
            latestFetchedVersion: String? = null,
        ): TestSuitesSource {
            require(organizationName == organization.name) {
                "Provided another organization: $organization"
            }
            require(gitDto == git.toDto()) {
                "Provided another git: $git"
            }
            return TestSuitesSource(
                organization,
                name,
                description,
                git,
                branch,
                testRootPath,
                latestFetchedVersion,
            )
        }
    }
}
