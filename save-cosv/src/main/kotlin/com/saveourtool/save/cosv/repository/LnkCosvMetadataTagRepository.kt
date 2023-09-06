package com.saveourtool.save.cosv.repository

import com.saveourtool.save.entities.cosv.LnkCosvMetadataTag
import com.saveourtool.save.spring.repository.BaseEntityRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

/**
 * [BaseEntityRepository] for [LnkCosvMetadataTag]
 */
@Repository
interface LnkCosvMetadataTagRepository : BaseEntityRepository<LnkCosvMetadataTag> {
    /**
     * @param tagName tag
     * @return list of [LnkCosvMetadataTag] links to COSV metadata
     */
    fun findByTagName(tagName: String): List<LnkCosvMetadataTag>

    /**
     * @param tagNames [Set] of tags
     * @return list of [LnkCosvMetadataTag] links to COSV metadata
     */
    fun findByTagNameIn(tagNames: Set<String>): List<LnkCosvMetadataTag>

    /**
     * @param cosvMetadataId id of COSV metadata
     * @return list of [LnkCosvMetadataTag] links to COSV metadata
     */
    fun findByCosvMetadataId(cosvMetadataId: Long): List<LnkCosvMetadataTag>

    /**
     * @param cosvId id of COSV
     * @return list of [LnkCosvMetadataTag] links to COSV metadata
     */
    fun findAllByCosvMetadataCosvId(cosvId: String): List<LnkCosvMetadataTag>

    /**
     * @param cosvMetadataIds [List] of [CosvMetadata.id]s
     * @return list of [LnkCosvMetadataTag] links to COSV metadata
     */
    fun findByCosvMetadataIdIn(cosvMetadataIds: List<Long>): List<LnkCosvMetadataTag>

    /**
     * @param cosvMetadataId id of COSV metadata
     * @param tagName tag
     * @return [LnkCosvMetadataTag]
     */
    fun findByCosvMetadataIdAndTagName(cosvMetadataId: Long, tagName: String): LnkCosvMetadataTag?

    /**
     * @param prefix [LnkCosvMetadataTag.tag] name prefix
     * @param page
     * @return [List] of [LnkCosvMetadataTag]s with name that starts with [prefix]
     */
    fun findAllByTagNameStartingWith(prefix: String, page: Pageable): List<LnkCosvMetadataTag>
}
