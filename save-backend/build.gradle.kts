import com.saveourtool.save.buildutils.configureJacoco
import com.saveourtool.save.buildutils.configureSpotless
import com.saveourtool.save.buildutils.configureSpringBoot

import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    // this plugin will generate generateOpenApiDocs task
    // running this task, it will write the OpenAPI spec into a backend-api-docs.json file in save-backend dir.
    id("org.springdoc.openapi-gradle-plugin") version "1.4.0"
}

openApi {
    apiDocsUrl.set("http://localhost:5800/internal/v3/api-docs/latest")
    outputDir.set(file(projectDir))
    outputFileName.set("backend-api-docs.json")
    waitTimeInSeconds.set(120)

    customBootRun {
        jvmArgs.add("-Dbackend.fileStorage.location=\${HOME}/cnb/files")
    }
}

configureSpringBoot(true)

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = Versions.jdk
        freeCompilerArgs = freeCompilerArgs + "-opt-in=kotlin.RequiresOptIn"
    }
}

tasks.getByName("processTestResources").dependsOn("copyLiquibase")

tasks.register<Copy>("copyLiquibase") {
    from("$rootDir/db")
    into("$buildDir/resources/test/db")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<Exec>("cleanupDbAndStorage") {
    dependsOn(":liquibaseDropAll")
    val profile = properties.get("save.profile") as String?

    val storagePath = when (profile) {
        "win" -> "${System.getProperty("user.home")}/.save-cloud/cnb/files"
        "mac" -> "/Users/Shared/.save-cloud/cnb/files"
        else -> "/home/cnb/files"
    }

    val args = if (profile != "win") {
        arrayOf("rm", "-rf")
    } else {
        arrayOf("rmdir", "/s", "/q")
    }
    commandLine(*args, storagePath)
}

dependencies {
    implementation(projects.saveCloudCommon)
    runtimeOnly(projects.saveFrontend) {
        targetConfiguration = "distribution"  // static resources packed as a jar, will be accessed from classpath
    }
    implementation(libs.save.common.jvm)
    implementation(libs.spring.boot.starter.quartz)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.security.core)
    implementation(libs.hibernate.micrometer)
    implementation(libs.spring.cloud.starter.kubernetes.client.config)
    implementation("io.projectreactor.addons:reactor-extra")
    testImplementation(libs.spring.security.test)
    testImplementation(projects.testUtils)
}

configureJacoco()
tasks.withType<Test> {
    extensions.configure(JacocoTaskExtension::class) {
        // this file is only used in dev profile for debugging, no need to calculate test coverage
        excludes = listOf("**/CorsFilter.kt")
    }
}
configureSpotless()
