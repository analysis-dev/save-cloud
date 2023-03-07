/**
 * Utils for kubernetes client
 */

package com.saveourtool.save.demo.utils

import com.saveourtool.save.demo.DemoAgentConfig
import com.saveourtool.save.demo.config.ConfigProperties
import com.saveourtool.save.demo.config.KubernetesConfig
import com.saveourtool.save.demo.entity.Demo
import com.saveourtool.save.demo.storage.DemoInternalFileStorage
import com.saveourtool.save.domain.toSdk
import com.saveourtool.save.utils.debug
import com.saveourtool.save.utils.downloadAndRunAgentCommand
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.api.model.batch.v1.Job
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.dsl.ScalableResource
import org.slf4j.LoggerFactory

private const val DEMO_ORG_NAME = "organizationName"
private const val DEMO_PROJ_NAME = "projectName"
private const val DEMO_VERSION = "version"
private const val REPLICAS_PER_DEMO = 1
private const val TTL_AFTER_COMPLETED = 3600

private val logger = LoggerFactory.getLogger("KubernetesUtils")

/**
 * @param demo demo entity
 * @param agentDownloadUrl url to download save-demo-agent.kexe, will be used to get pod start command
 * @param kubernetesSettings kubernetes configuration
 * @param agentConfig configuration that is required to be present on save-demo-agent on startup
 * @throws KubernetesRunnerException on failed job creation
 */
@Suppress("NestedBlockDepth")
fun KubernetesClient.startJob(
    demo: Demo,
    agentDownloadUrl: String,
    kubernetesSettings: KubernetesConfig,
    agentConfig: ConfigProperties.AgentConfig,
) {
    val job = Job().apply {
        metadata = ObjectMeta().apply {
            name = jobNameForDemo(demo)
        }
        spec = JobSpec().apply {
            parallelism = REPLICAS_PER_DEMO
            ttlSecondsAfterFinished = TTL_AFTER_COMPLETED
            backoffLimit = 0
            template = PodTemplateSpec().apply {
                spec = PodSpec().apply {
                    subdomain = kubernetesSettings.agentSubdomainName
                    if (kubernetesSettings.useGvisor) {
                        nodeSelector = mapOf(
                            "gvisor" to "enabled"
                        )
                        runtimeClassName = "gvisor"
                    }
                    metadata = ObjectMeta().apply {
                        labels = mapOf(
                            DEMO_ORG_NAME to demo.organizationName,
                            DEMO_PROJ_NAME to demo.projectName,
                            DEMO_VERSION to "manual",
                            // "baseImageName" to baseImageName
                            "io.kompose.service" to "save-demo-agent",
                        )
                    }
                    restartPolicy = "Never"
                    containers = listOf(
                        demoAgentContainerSpec(
                            demo.sdk.toSdk().baseImageName(),
                            agentDownloadUrl,
                            demo,
                            kubernetesSettings,
                            agentConfig,
                        )
                    )
                }
            }
        }
    }
    logger.debug { "Attempt to create Job from the following spec: $job" }
    try {
        resource(job).create()
        with(demo) {
            logger.info("Created Job for demo $organizationName/$projectName")
        }
    } catch (kex: KubernetesClientException) {
        with(demo) {
            throw KubernetesRunnerException("Unable to create a job for demo $organizationName/$projectName", kex)
        }
    }
}

/**
 * @param demo demo entity
 * @return [ScalableResource] of [Job]
 */
fun KubernetesClient.getJobByName(demo: Demo): ScalableResource<Job> = batch()
    .v1()
    .jobs()
    .withName(jobNameForDemo(demo))

/**
 * @param demo demo entity
 * @return true if the resource is ready or exists (if no readiness check exists), false otherwise.
 */
fun KubernetesClient.isJobReady(demo: Demo) = getJobByName(demo).isReady

/**
 * @param demo demo entity
 * @return list of ips of pods that are run by job associated with [demo]
 */
fun KubernetesClient.getJobPodsIps(demo: Demo) = getJobPods(demo)
    .map { it.status.podIP }

/**
 * @param demo demo entity
 * @return list of pods that are run by job associated with [demo]
 */
fun KubernetesClient.getJobPods(demo: Demo): List<Pod> = pods()
    .withLabel(DEMO_ORG_NAME, demo.organizationName)
    .withLabel(DEMO_PROJ_NAME, demo.projectName)
    .list()
    .items

private fun ContainerPort.default(port: Int) = apply {
    protocol = "TCP"
    containerPort = port
    hostPort = port
    name = "agent-server"
}

/**
 * @param demo demo entity
 * @return name of job that is/should be assigned to [demo]
 */
fun jobNameForDemo(demo: Demo) = with(demo) { "demo-${organizationName.lowercase()}-${projectName.lowercase()}-1" }

@Suppress("SameParameterValue")
private fun getConfigureMeUrl(baseUrl: String, demo: Demo, version: String) = with(demo) {
    "$baseUrl/demo/internal/manager/$organizationName/$projectName/configure-me?version=$version"
}

@Suppress("TOO_LONG_FUNCTION")
private fun demoAgentContainerSpec(
    imageName: String,
    agentDownloadUrl: String,
    demo: Demo,
    kubernetesSettings: KubernetesConfig,
    agentConfig: ConfigProperties.AgentConfig,
) = Container().apply {
    name = "save-demo-agent-pod"
    image = imageName
    imagePullPolicy = "IfNotPresent"

    // todo: in later phases should be removed
    val currentlyHardcodedVersion = "manual"

    listOf(
        "KTOR_LOG_LEVEL" to "TRACE",
        DemoAgentConfig.DEMO_CONFIGURE_ME_URL_ENV to getConfigureMeUrl(agentConfig.demoUrl, demo, currentlyHardcodedVersion),
        DemoAgentConfig.DEMO_ORGANIZATION_ENV to demo.organizationName,
        DemoAgentConfig.DEMO_PROJECT_ENV to demo.projectName,
        DemoAgentConfig.DEMO_VERSION_ENV to currentlyHardcodedVersion,
    )
        .map { (key, envValue) ->
            EnvVar().apply {
                name = key
                value = envValue
            }
        }
        .let { env = it }

    val startupCommand = downloadAndRunAgentCommand(
        agentDownloadUrl, DemoInternalFileStorage.saveDemoAgent,
    )

    command = listOf("sh", "-c", startupCommand)

    ports = listOf(ContainerPort().default(kubernetesSettings.agentPort))

    resources = with(kubernetesSettings) {
        ResourceRequirements().apply {
            requests = mapOf(
                "cpu" to Quantity(agentCpuRequests),
                "memory" to Quantity(agentMemoryRequests),
            )
            limits = mapOf(
                "cpu" to Quantity(agentCpuLimits),
                "memory" to Quantity(agentMemoryLimits),
            )
        }
    }
}
