/**
 * Configuration for diktat static analysis
 */

package org.cqfn.save.buildutils

import org.cqfn.diktat.plugin.gradle.DiktatExtension
import org.cqfn.diktat.plugin.gradle.DiktatGradlePlugin
import org.cqfn.diktat.plugin.gradle.DiktatJavaExecTaskBase
import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

/**
 * Applies diktat gradle plugin and configures diktat for [this] project
 */
fun Project.configureDiktat() {
    apply<DiktatGradlePlugin>()
    configure<DiktatExtension> {
        diktatConfigFile = rootProject.file("diktat-analysis.yml")
        githubActions = findProperty("diktat.githubActions")?.toString()?.toBoolean() ?: false
        inputs {
            include("src/**/*.kt", "**/*.kts")
        }
    }
    fixDiktatTasks()
}

/**
 * Creates unified tasks to run diktat on all projects
 */
fun Project.createDiktatTask() {
    if (this == rootProject) {
        // apply diktat to buildSrc
        apply<DiktatGradlePlugin>()
        configure<DiktatExtension> {
            diktatConfigFile = rootProject.file("diktat-analysis.yml")
            githubActions = findProperty("diktat.githubActions")?.toString()?.toBoolean() ?: false
            inputs {
                include("$rootDir/buildSrc/src/**/*.kt", "$rootDir/*.kts", "$rootDir/buildSrc/**/*.kts")
            }
        }
        fixDiktatTasks()
    }
    tasks.register("diktatCheckAll") {
        allprojects {
            this@register.dependsOn(tasks.getByName("diktatCheck"))
        }
    }
    tasks.register("diktatFixAll") {
        allprojects {
            this@register.dependsOn(tasks.getByName("diktatFix"))
        }
    }
}

private fun Project.fixDiktatTasks() {
    tasks.withType<DiktatJavaExecTaskBase>().configureEach {
        javaLauncher.set(project.extensions.getByType<JavaToolchainService>().launcherFor {
            languageVersion.set(JavaLanguageVersion.of(11))
        })
        // https://github.com/analysis-dev/diktat/issues/1213
        systemProperty("user.home", rootProject.projectDir.toString())
    }
}