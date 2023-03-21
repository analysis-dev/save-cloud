package com.saveourtool.save.demo.service

import com.saveourtool.save.demo.DemoAgentConfig
import com.saveourtool.save.demo.DemoStatus
import com.saveourtool.save.demo.RunCommandMap
import com.saveourtool.save.demo.entity.Demo
import com.saveourtool.save.demo.entity.RunCommand
import com.saveourtool.save.demo.repository.DemoRepository
import com.saveourtool.save.demo.repository.RunCommandRepository
import com.saveourtool.save.demo.runners.RunnerFactory
import com.saveourtool.save.demo.storage.DependencyStorage
import com.saveourtool.save.utils.StringResponse
import com.saveourtool.save.utils.blockingToMono
import com.saveourtool.save.utils.switchIfEmptyToNotFound

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.kotlin.core.publisher.toMono

import kotlinx.coroutines.reactor.mono

/**
 * [Service] for [Demo] entity
 */
@Service
class DemoService(
    private val demoRepository: DemoRepository,
    private val kubernetesService: KubernetesService?,
    private val dependencyStorage: DependencyStorage,
    private val runCommandRepository: RunCommandRepository,
) {
    /**
     * Get preferred [RunnerFactory.RunnerType] for demo runner.
     *
     * @return preferred [RunnerFactory.RunnerType] for demo runner.
     */
    fun getRunnerType(): Mono<RunnerFactory.RunnerType> = Mono.fromCallable {
        kubernetesService?.let { RunnerFactory.RunnerType.POD } ?: RunnerFactory.RunnerType.CLI
    }

    /**
     * Start kubernetes job if kubernetes profile is set, otherwise do nothing
     *
     * @param demo demo entity
     * @return [Mono] of [StringResponse] filled with readable message
     */
    fun start(demo: Demo): Mono<StringResponse> = kubernetesService?.let {
        kubernetesService.start(demo)
    } ?: Mono.fromCallable {
        StringResponse.ok("Demo successfully created")
    }

    /**
     * Stop kubernetes job if kubernetes profile is set, do nothing otherwise
     *
     * @param demo demo entity
     */
    fun stop(demo: Demo) = kubernetesService?.let { kubernetesService.stop(demo) } ?: Unit

    /**
     * Get [DemoStatus] of [demo]
     *
     * If kubernetes profile is enabled, request is performed in order to get current status
     * If kubernetes profile is not enabled, [DemoStatus.RUNNING] is returned
     *
     * @param demo demo entity
     * @return current [DemoStatus] of [demo]
     */
    fun getStatus(demo: Demo): Mono<DemoStatus> = kubernetesService?.let {
        mono { kubernetesService.getStatus(demo) }
    } ?: DemoStatus.RUNNING.toMono()

    /**
     * Get save-demo-agent configuration
     *
     * @param demo [Demo] entity
     * @param version required demo version
     * @return [DemoAgentConfig] corresponding to [Demo] with [version]
     * @throws IllegalStateException on inactive kubernetes profile
     */
    fun getAgentConfiguration(
        demo: Demo,
        version: String,
    ) = kubernetesService?.getConfiguration(demo, version) ?: throw IllegalStateException(
        "Could not get configuration for pod as kubernetes profile is inactive."
    )

    /**
     * @return list of [Demo]s that are stored in database
     */
    fun getAllDemos(): List<Demo> = demoRepository.findAll()

    /**
     * @param demo [Demo] entity
     * @param runCommands [RunCommandMap] where keys are demo mode names and values are run commands
     * @return [Demo] entity, updated or saved to database
     */
    @Transactional
    fun saveOrUpdateExisting(demo: Demo, runCommands: RunCommandMap): Demo = demoRepository.findByOrganizationNameAndProjectName(
        demo.organizationName,
        demo.projectName
    )
        ?.let { demoFromDb ->
            demo.apply {
                this.id = demoFromDb.requiredId()
                val existingCommands = demoFromDb.runCommands.filter { (it.modeName to it.command) in runCommands.toList() }
                val newCommands = runCommands.filterKeys { modeName -> modeName !in existingCommands.map { it.modeName } }.toRunCommandEntityList(this)
                    .also {
                        runCommandRepository.saveAllAndFlush(it)
                    }

                this.runCommands = existingCommands + newCommands
            }
        }
        ?.let { demoRepository.save(it) }
        ?: demoRepository.save(demo).apply {
            this.runCommands = runCommands.toRunCommandEntityList(this).let { runCommandRepository.saveAll(it) }
        }

    private fun RunCommandMap.toRunCommandEntityList(demo: Demo) = map { (modeName, runCommand) ->
        RunCommand(demo, modeName, runCommand)
    }

    /**
     * @param organizationName saveourtool organization name
     * @param projectName saveourtool project name
     * @return [Demo] connected with project [organizationName]/[projectName] or null if not present
     */
    @Transactional
    fun findBySaveourtoolProject(
        organizationName: String,
        projectName: String,
    ): Demo? = demoRepository.findByOrganizationNameAndProjectName(organizationName, projectName)

    /**
     * @param organizationName saveourtool organization name
     * @param projectName saveourtool project name
     * @param lazyMessage
     * @return [Demo] or Mono filled with error (not found)
     */
    fun findBySaveourtoolProjectOrNotFound(
        organizationName: String,
        projectName: String,
        lazyMessage: () -> String,
    ): Mono<Demo> = blockingToMono {
        findBySaveourtoolProject(organizationName, projectName)
    }.switchIfEmptyToNotFound(lazyMessage)

    /**
     * @param demo [Demo] entity
     * @param version version of demo
     * @return [Mono] of [Unit]
     */
    fun delete(demo: Demo, version: String): Mono<StringResponse> = stop(demo)
        .let { dependencyStorage.list(demo, version) }
        .concatMap { dependencyStorage.delete(it) }
        .collectList()
        .publishOn(Schedulers.boundedElastic())
        .map { demoRepository.delete(demo) }
        .map { StringResponse.ok("Successfully deleted demo of ${demo.projectCoordinates()}.") }
}
