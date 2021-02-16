package org.cqfn.save.buildutils

import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

fun Project.configureSpringBoot() {
    apply<SpringBootPlugin>()

    dependencies {
        add("implementation", "org.springframework.boot:spring-boot-starter-webflux:${Versions.springBoot}")
        add("implementation", "org.springframework.boot:spring-boot-starter-actuator:${Versions.springBoot}")
        add("implementation", "io.micrometer:micrometer-registry-prometheus:${Versions.micrometer}")  // expose prometheus metrics in actuator
        add("implementation", "org.springframework.security:spring-security-core:${Versions.springSecurity}")
        add("testImplementation", "org.springframework.boot:spring-boot-starter-test:${Versions.springBoot}")
    }

    tasks.named<BootBuildImage>("bootBuildImage") {
        dependsOn(rootProject.tasks.getByName("startLocalDockerRegistry"))
        docker {
            publishRegistry {
                host = "http://localhost:6000"
            }
        }
        isPublish = true
    }
}
