import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm")
    alias(libs.plugins.kotlin.plugin.serialization)
}

application {
    mainClass.set("org.cqfn.save.api.MainKt")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = Versions.jdk
        freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
    }
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(Versions.jdk))
    }
}

dependencies {
    implementation(projects.saveCloudCommon)
    implementation(libs.save.common.jvm)
    implementation(libs.kotlinx.cli)
    implementation(libs.log4j)
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.ktor.client.apache)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.serialization)
    implementation(libs.ktor.client.logging)
    implementation(libs.kotlinx.serialization.properties)
}
