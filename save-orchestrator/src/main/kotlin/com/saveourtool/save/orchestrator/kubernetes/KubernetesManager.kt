package com.saveourtool.save.orchestrator.kubernetes

import com.saveourtool.save.orchestrator.config.ConfigProperties
import com.saveourtool.save.orchestrator.findImage
import com.saveourtool.save.orchestrator.runner.AgentRunner
import com.saveourtool.save.orchestrator.runner.AgentRunnerException
import com.saveourtool.save.orchestrator.service.DockerService
import com.saveourtool.save.orchestrator.service.PersistentVolumeId
import com.saveourtool.save.utils.warn

import com.github.dockerjava.api.DockerClient
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.api.model.batch.v1.Job
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec
import io.fabric8.kubernetes.client.KubernetesClient
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * A component that manages save-agents running in Kubernetes.
 */
@Component
@Profile("kubernetes")
class KubernetesManager(
    private val dockerClient: DockerClient,
    private val kc: KubernetesClient,
    private val configProperties: ConfigProperties,
    private val meterRegistry: MeterRegistry,
) : AgentRunner {
    @Suppress(
        "TOO_LONG_FUNCTION",
        "LongMethod",
        "MagicNumber",
        "NestedBlockDepth",
    )
    override fun create(executionId: Long,
                        configuration: DockerService.RunConfiguration<PersistentVolumeId>,
                        replicas: Int,
                        workingDir: String,
    ): List<String> {
        val (baseImageId, agentRunCmd, pvId) = configuration
        require(pvId is KubernetesPvId) { "${KubernetesPersistentVolumeService::class.simpleName} can only operate with ${KubernetesPvId::class.simpleName}" }
        // fixme: pass image name instead of ID from the outside
        val baseImage = dockerClient.findImage(baseImageId, meterRegistry)
            ?: error("Image with requested baseImageId=$baseImageId is not present in the system")
        val baseImageName = baseImage.repoTags.first()

        // Creating Kubernetes objects that will be responsible for lifecycle of save-agents.
        // We use Job, because Deployment will always try to restart failing pods.
        val job = Job().apply {
            metadata = ObjectMeta().apply {
                name = jobNameForExecution(executionId)
            }
            spec = JobSpec().apply {
                parallelism = replicas
                // do not attempt to restart failed pods, because if we manually stop pods by deleting them,
                // job controller would think that they need to be restarted
                backoffLimit = 0
                template = PodTemplateSpec().apply {
                    spec = PodSpec().apply {
                        if (configProperties.kubernetes?.useGvisor == true) {
                            nodeSelector = mapOf(
                                "gvisor" to "enabled"
                            )
                        }
                        // FixMe: Orchestrator doesn't push images to a remote registry, so agents have to be run on the same host.
                        nodeName = System.getenv("NODE_NAME")
                        containers = listOf(
                            Container().apply {
                                name = "save-agent-pod"
                                metadata = ObjectMeta().apply {
                                    labels = mapOf(
                                        "executionId" to executionId.toString(),
                                        "baseImageId" to baseImageId,
                                        // "baseImageName" to baseImageName
                                        // "io.kompose.service" to
                                    )
                                }
                                image = baseImageName
                                imagePullPolicy = "IfNotPresent"  // so that local images could be used
                                // If agent fails, we should handle it manually (update statuses, attempt restart etc)
                                restartPolicy = "Never"
                                if (!configProperties.docker.runtime.isNullOrEmpty()) {
                                    logger.warn {
                                        "Discarding property configProperties.docker.runtime=${configProperties.docker.runtime}, " +
                                                "because custom runtimes are not supported yet"
                                    }
                                }
                                env = listOf(
                                    EnvVar().apply {
                                        name = "POD_NAME"
                                        valueFrom = EnvVarSource().apply {
                                            fieldRef = ObjectFieldSelector().apply {
                                                fieldPath = "metadata.name"
                                            }
                                        }
                                    }
                                )
                                command = agentRunCmd.split(" ")
                                this.workingDir = workingDir
                            }
                        )
                    }
                }
            }
        }
        kc.batch()
            .v1()
            .jobs()
            .create(job)
        logger.info("Created Job for execution id=$executionId")
        // fixme: wait for pods to be created
        return generateSequence<List<String>> {
            Thread.sleep(1_000)
            kc.pods().withLabel("baseImageId", baseImageId)
                .list()
                .items
                .map { it.metadata.name }
        }
            .take(10)
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
    }

    override fun start(executionId: Long) {
        logger.debug("${this::class.simpleName}#start is called, but it's no-op because Kubernetes workloads are managed by Kubernetes itself")
    }

    override fun stop(executionId: Long) {
        val jobName = jobNameForExecution(executionId)
        val deletedResources = kcJobsWithName(jobName)
            .delete()
        val isDeleted = deletedResources.size == 1
        if (!isDeleted) {
            throw AgentRunnerException("Failed to delete job with name $jobName: response is $deletedResources")
        }
        logger.debug("Deleted Job for execution id=$executionId")
    }

    override fun stopByAgentId(agentId: String): Boolean {
        val pod: Pod? = kc.pods().withName(agentId).get()
        pod ?: run {
            logger.debug("Agent id=$agentId is already stopped or not yet created")
            return true
        }
        val deletedResources = kc.pods().withName(agentId).delete()
        val isDeleted = deletedResources.size == 1
        if (!isDeleted) {
            throw AgentRunnerException("Failed to delete pod with name $agentId: response is $deletedResources")
        } else {
            logger.debug("Deleted pod with name=$agentId")
            return true
        }
    }

    override fun cleanup(executionId: Long) {
        logger.debug("Removing a Job for execution id=$executionId")
        val job = kcJobsWithName(jobNameForExecution(executionId))
        job.get()?.let {
            job.delete()
        }
    }

    override fun prune() {
        logger.debug("${this::class.simpleName}#prune is called, but it's no-op, " +
                "because we don't directly interact with the docker containers or images on the nodes of Kubernetes themselves")
    }

    override fun isAgentStopped(agentId: String): Boolean {
        val pod = kc.pods().withName(agentId).get()
        return pod == null || run {
            // Retrieve reason based on https://github.com/kubernetes/kubernetes/issues/22839
            val reason = pod.status.phase ?: pod.status.reason
            val isRunning = pod.status.containerStatuses.any {
                it.ready && it.state.running != null
            }
            logger.debug("Pod name=$agentId is still present; reason=$reason, isRunning=$isRunning, conditions=${pod.status.conditions}")
            if (reason == "Completed" && isRunning) {
                "ContainerReady" in pod.status.conditions.map { it.type }
            } else {
                !isRunning
            }
        }
    }

    private fun jobNameForExecution(executionId: Long) = "save-execution-$executionId"

    private fun kcJobsWithName(name: String) = kc.batch()
        .v1()
        .jobs()
        .withName(name)

    companion object {
        private val logger = LoggerFactory.getLogger(KubernetesManager::class.java)
    }
}
