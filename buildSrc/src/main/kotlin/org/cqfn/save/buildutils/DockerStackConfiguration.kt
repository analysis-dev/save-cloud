package org.cqfn.save.buildutils

import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

fun Project.createStackDeployTask() {
    tasks.register<Exec>("startLocalDockerRegistry") {
        enabled = false
        description = "Start local docker registry for spring boot images. Disabled, see comment in deployDockerStack task."
        commandLine("docker", "service", "create", "--name", "registry", "--publish", "published=6000,target=5000", "registry:2")
    }

    tasks.register<Exec>("deployDockerStack") {
        dependsOn(subprojects.flatMap { it.tasks.withType<BootBuildImage>() })
        doFirst {
            copy {
                description = "Copy configuration files from repo to actual locations"
                from("save-deploy")
                into("${System.getProperty("user.home")}/configs")
            }
            description = "Set project version in docker-compose file"
            val newText = file("$rootDir/docker-compose.yaml.template").readLines()
                .joinToString(System.lineSeparator()) { it.replace("{{project.version}}", version.toString()) }
            file("$buildDir/docker-compose.yaml")
                .apply { createNewFile() }
                .writeText(newText)
        }
        description = "Deploy to docker swarm. If swarm contains more than one node, some registry for built images is requried."
        commandLine("docker", "stack", "deploy", "--compose-file", "$buildDir/docker-compose.yaml", "save")
//        doLast {
//            exec {
//                description = "Stop local docker registry"
//                commandLine("docker", "service", "rm", "registry")
//            }
//        }
    }
}
