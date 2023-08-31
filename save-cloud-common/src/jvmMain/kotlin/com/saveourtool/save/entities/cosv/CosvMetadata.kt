package com.saveourtool.save.entities.cosv

import com.saveourtool.save.entities.Organization
import com.saveourtool.save.entities.User
import com.saveourtool.save.spring.entity.BaseEntityWithDto

import java.time.LocalDateTime
import javax.persistence.Entity
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne

import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime

/**
 * @property cosvId [com.saveourtool.osv4k.OsvSchema.id]
 * @property summary [com.saveourtool.osv4k.OsvSchema.summary]
 * @property details [com.saveourtool.osv4k.OsvSchema.details]
 * @property severity [com.saveourtool.osv4k.Severity.score]
 * @property severityNum [com.saveourtool.osv4k.Severity.scoreNum]
 * @property modified [com.saveourtool.osv4k.OsvSchema.modified]
 * @property published [com.saveourtool.osv4k.OsvSchema.published]
 * @property user [User] who uploaded COSV to save
 * @property organization [Organization] to which COSV was uploaded
 **/
@Entity
@Suppress("LongParameterList")
class CosvMetadata(
    var cosvId: String,
    var summary: String,
    var details: String,
    var severity: String?,
    var severityNum: Int,
    var modified: LocalDateTime,
    var published: LocalDateTime,
    @ManyToOne
    @JoinColumn(name = "user_id")
    var user: User,
    @ManyToOne
    @JoinColumn(name = "organization_id")
    var organization: Organization,
) : BaseEntityWithDto<CosvMetadataDto>() {
    override fun toDto(): CosvMetadataDto = CosvMetadataDto(
        cosvId = cosvId,
        summary = summary,
        details = details,
        severity = severity,
        severityNum = severityNum,
        modified = modified.toKotlinLocalDateTime(),
        published = published.toKotlinLocalDateTime(),
        userId = user.requiredId(),
        organizationId = organization.requiredId(),
    )

    companion object {
        /**
         * @receiver [CosvMetadataDto] dto to create [CosvMetadata]
         * @param userResolver
         * @param organizationResolver
         * @return [CosvMetadata] created from receiver
         */
        fun CosvMetadataDto.toEntity(
            userResolver: (Long) -> User,
            organizationResolver: (Long) -> Organization,
        ): CosvMetadata = CosvMetadata(
            cosvId = cosvId,
            summary = summary,
            details = details,
            severity = severity,
            severityNum = severityNum,
            modified = modified.toJavaLocalDateTime(),
            published = published.toJavaLocalDateTime(),
            user = userResolver(userId),
            organization = organizationResolver(organizationId),
        )
    }
}
