package com.saveourtool.save.orchestrator.docker

import com.saveourtool.save.orchestrator.config.ConfigProperties
import com.saveourtool.save.utils.debug
import com.saveourtool.save.utils.getLogger

import com.github.dockerjava.api.DockerClient
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServerExtension
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.mock
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

import java.net.HttpURLConnection

@ExtendWith(SpringExtension::class, KubernetesMockServerExtension::class)
@EnableConfigurationProperties(ConfigProperties::class)
@EnableKubernetesMockClient
@TestPropertySource("classpath:application.properties")
class KubernetesManagerTest {
    private val dockerClient: DockerClient = mock()
    @Autowired private lateinit var configProperties: ConfigProperties
    private lateinit var kubernetesManager: KubernetesManager

    @BeforeEach
    fun setUp() {
        kubernetesManager = KubernetesManager(
            dockerClient,
            kubernetesClient,
            configProperties,
            CompositeMeterRegistry(),
        )
    }

    @Test
    fun `should delete a Job when stop is requested`() {
        kubernetesMockServer.expect()
            .delete()
            .withPath("/apis/batch/v1/namespaces/test/jobs/save-execution-1")
            .andReturn(HttpURLConnection.HTTP_OK, JobBuilder().build())
            .once()

        val disposable = Mono.fromCallable {
            kubernetesMockServer.takeRequest()
        }
            .subscribeOn(Schedulers.single())
            .doOnNext { request ->
                log.debug { request.toString() }
                Assertions.assertNotNull(request)
            }
            .subscribe()

        kubernetesManager.stop(1)

        Assertions.assertTrue(disposable.isDisposed)
    }

    companion object {
        private val log: Logger = getLogger<KubernetesManagerTest>()
        @JvmStatic internal lateinit var kubernetesClient: KubernetesClient
        @JvmStatic internal lateinit var kubernetesMockServer: KubernetesMockServer
    }
}
