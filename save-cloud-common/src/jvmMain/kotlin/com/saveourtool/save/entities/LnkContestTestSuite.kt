package com.saveourtool.save.entities

import com.saveourtool.save.spring.entity.BaseEntity
import javax.persistence.Entity
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne

/**
 * @property contest
 * @property testSuite
 */
@Entity
class LnkContestTestSuite(
    @ManyToOne
    @JoinColumn(name = "contest_id")
    var contest: Contest,

    @ManyToOne
    @JoinColumn(name = "test_suite_id")
    var testSuite: TestSuite,
) : BaseEntity()
