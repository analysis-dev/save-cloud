/**
 * This file contains actual annotation from Spring data jpa
 */

package com.saveourtool.save.entities

import javax.persistence.Entity
import javax.persistence.Enumerated
import javax.persistence.ForeignKey
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne

actual typealias Entity = Entity
actual typealias Id = Id
actual typealias GeneratedValue = GeneratedValue
actual typealias JoinColumn = JoinColumn
actual typealias ManyToOne = ManyToOne
actual typealias Enumerated = Enumerated
actual typealias ForeignKey = ForeignKey
