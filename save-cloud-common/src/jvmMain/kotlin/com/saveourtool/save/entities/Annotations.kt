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
import javax.persistence.MappedSuperclass
import javax.persistence.OneToMany

actual typealias Entity = Entity
actual typealias Id = Id
actual typealias GeneratedValue = GeneratedValue
actual typealias JoinColumn = JoinColumn
actual typealias ManyToOne = ManyToOne
actual typealias OneToMany = OneToMany
actual typealias MappedSuperclass = MappedSuperclass
actual typealias Enumerated = Enumerated
actual typealias ForeignKey = ForeignKey
actual typealias NotNull = javax.validation.constraints.NotNull
