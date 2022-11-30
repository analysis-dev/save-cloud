package com.saveourtool.save.sandbox.entity

import com.saveourtool.save.agent.AgentState
import com.saveourtool.save.entities.AgentStatusDto
import com.saveourtool.save.spring.entity.BaseEntity

import java.time.LocalDateTime
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime

/**
 * @property startTime staring time of status
 * @property endTime time of update
 * @property state current state of the agent
 * @property agent agent who's state is described
 */
@Entity
@Table(name = "agent_status")
class SandboxAgentStatus(
    var startTime: LocalDateTime,
    var endTime: LocalDateTime,

    @Enumerated(EnumType.STRING)
    var state: AgentState,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "agent_id")
    var agent: SandboxAgent,
) : BaseEntity() {
    /**
     * @return this object converted to [AgentStatusDto]
     */
    fun toDto() = AgentStatusDto(state, agent.containerId, endTime.toKotlinLocalDateTime())
}

/**
 * @param agentResolver resolver for [SandboxAgent] by [AgentStatusDto.containerId]
 * @return [SandboxAgentStatus] built from [AgentStatusDto]
 */
fun AgentStatusDto.toEntity(agentResolver: (String) -> SandboxAgent) = SandboxAgentStatus(
    startTime = time.toJavaLocalDateTime(),
    endTime = time.toJavaLocalDateTime(),
    state = state,
    agent = agentResolver(containerId)
)
