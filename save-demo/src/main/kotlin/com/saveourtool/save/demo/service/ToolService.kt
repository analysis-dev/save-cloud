package com.saveourtool.save.demo.service

import com.saveourtool.save.demo.entity.GithubRepo
import com.saveourtool.save.demo.entity.Snapshot
import com.saveourtool.save.demo.entity.Tool
import com.saveourtool.save.demo.repository.ToolRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [Service] for [ToolService] entity
 */
@Service
class ToolService(
    private val toolRepository: ToolRepository,
    private val githubRepoService: GithubRepoService,
    private val snapshotService: SnapshotService,
) {
    private fun save(githubRepo: GithubRepo, snapshot: Snapshot) = toolRepository.save(Tool(githubRepo, snapshot))

    /**
     * @param githubRepo
     * @param snapshot
     * @return [Tool] entity saved to database
     */
    @Transactional
    fun saveIfNotPresent(githubRepo: GithubRepo, snapshot: Snapshot): Tool {
        val githubRepoFromDb = githubRepoService.saveIfNotPresent(githubRepo)
        val snapshotFromDb = snapshotService.saveIfNotPresent(snapshot)
        return toolRepository.findByGithubRepoAndSnapshot(githubRepoFromDb, snapshotFromDb) ?: save(githubRepoFromDb, snapshotFromDb)
    }

    /**
     * @param githubRepo
     * @param version
     * @return [Tool] fetched from [githubRepo] that matches requested [version]
     */
    fun findByGithubRepoAndVersion(githubRepo: GithubRepo, version: String) = toolRepository.findByGithubRepoAndSnapshotVersion(githubRepo, version)
}
