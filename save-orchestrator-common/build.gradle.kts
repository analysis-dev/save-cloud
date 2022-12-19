import com.saveourtool.save.buildutils.*

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.saveourtool.save.buildutils.kotlin-jvm-configuration")
    id("com.saveourtool.save.buildutils.spring-boot-configuration")
    id("com.saveourtool.save.buildutils.code-quality-convention")
    id("org.gradle.test-retry") version "1.5.0"
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
    }
}

tasks.withType<Test> {
    retry {
        // There once were flaky tests in orchestrator, but it seems like they became stable.
        // Settings can be restored or removed, as required.
        failOnPassedAfterRetry.set(false)
        maxFailures.set(5)
        maxRetries.set(1)
    }
}

kotlin {
    sourceSets {
        val commonMain by creating {
            dependencies {
                api(projects.saveCloudCommon)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        val commonTest by creating {
            dependencies {
                implementation(projects.testUtils)
            }
        }
    }
}

dependencies {
    implementation(libs.dockerJava.core)
    implementation(libs.dockerJava.transport.httpclient5)
    implementation(libs.kotlinx.serialization.json.jvm)
    implementation(libs.commons.compress)
    implementation(libs.kotlinx.datetime)
    implementation(libs.zip4j)
    implementation(libs.fabric8.kubernetes.client)
    implementation(libs.spring.kafka)
    testImplementation(libs.fabric8.kubernetes.server.mock)
}
