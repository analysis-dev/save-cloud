package org.cqfn.save.entities

import org.cqfn.save.testsuite.TestSuiteType

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
 * @property propertiesRelativePath location of save.properties file for this test suite, relative to project's root directory
 */
@Suppress("USE_DATA_CLASS")
@Entity
class TestSuite(
    @Enumerated(EnumType.STRING)
    var type: TestSuiteType? = null,

    var name: String = "FB",

    @ManyToOne
    @JoinColumn(name = "project_id")
    var project: Project? = null,

    var dateAdded: LocalDateTime? = null,

    var propertiesRelativePath: String,
) : BaseEntity()
