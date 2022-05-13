package org.cqfn.save.orchestrator.docker

import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import org.cqfn.save.orchestrator.config.ConfigProperties
import org.cqfn.save.orchestrator.config.KubernetesSettings
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import javax.annotation.PreDestroy

/**
 * For save-agent in K8S the lifecycle is as follows:
 * create ConfigMap -> create Deployment (also creates ReplicaSet that creates Pods and starts containers)
 * stop: remove Deployment (? and also replica set?) -> remove ConfigMap
 */
@Component
@Profile("kubernetes")
class KubernetesManager(
    configProperties: ConfigProperties,
): AgentRunner {
    @PreDestroy
    fun close() {
        kc.close()
    }

    private val kubernetesSettings = requireNotNull(configProperties.kubernetes) {
        "Class [${this::class.simpleName}] requires kubernetes-related properties to be set"
    }

    private val kc = DefaultKubernetesClient().inNamespace(kubernetesSettings.namespace)

    override fun create(baseImageId: String,
                        replicas: Int,
                        workingDir: String,
                        runCmd: String,
                        containerName: String,
    ): List<String> {
        val deployment = Deployment().apply {
            metadata = ObjectMeta().apply {
                name = containerName
            }
            spec = DeploymentSpec().apply {
                this.replicas = replicas
                template = PodTemplateSpec().apply {
                    spec = PodSpec().apply {
                        containers = listOf(
                            Container().apply {
                                metadata = ObjectMeta().apply {
                                    labels = mapOf(
                                        "baseImageId" to baseImageId
                                    )
                                }
                                image = baseImageId
                                imagePullPolicy = "IfNotPresent"  // so that local images could be used
                                name = containerName
                                // todo: value
                                restartPolicy = "never"
                                env = listOf(
                                    EnvVar().apply {
                                        name = "POD_NAME"
                                        valueFrom = EnvVarSource().apply {
                                            fieldRef = ObjectFieldSelector().apply {
                                                fieldPath = this@pod.metadata!!.name
                                            }
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
        val appliedDeployment = kc.apps().deployments().create(deployment)
        return kc.pods().withLabel("baseImageId", baseImageId).list().items.map { it.metadata.name }
    }

    override fun start(id: String) {
        logger.debug("${this::class.simpleName}#start is called, but it's no-op because Kubernetes workloads are managed by Kubernetes itself")
    }

    override fun stop(id: String) {
        kc.apps().deployments().withName().delete()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(KubernetesManager::class.java)
    }
}