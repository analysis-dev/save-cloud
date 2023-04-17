package com.saveourtool.save.listeners

import com.saveourtool.save.spring.entity.BaseEntityWithDate
import java.time.LocalDateTime
import javax.persistence.PrePersist
import javax.persistence.PreUpdate

@Suppress("MISSING_KDOC_TOP_LEVEL")
class DateListener {
    @PrePersist
    private fun beforeSave(entity: BaseEntityWithDate) {
        val date = LocalDateTime.now()
        with(entity) {
            createDate = date
            updateDate = date
        }
    }
    @PreUpdate
    private fun beforeUpdate(entity: BaseEntityWithDate) {
        with(entity) {
            updateDate = LocalDateTime.now()
        }
    }
}
