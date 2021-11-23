package org.cqfn.save.backend.service

import org.cqfn.save.backend.repository.AgentRepository
import org.springframework.stereotype.Service

/**
 * Service for agent
 */
@Service
class AgentService(private val agentRepository: AgentRepository) {
    /**
     * @param projectId
     * @return Unit
     */
    internal fun deleteAgentWithProjectId(projectId: Long) =
            agentRepository.findByExecutionProjectId(projectId).forEach {
                agentRepository.delete(it)
            }

    /**
     * @param executionIds list of ids
     * @return Unit
     */
    internal fun deleteAgentByExecutionIds(executionIds: List<Long>) =
            agentRepository.deleteByExecutionIdIn(executionIds)
}
