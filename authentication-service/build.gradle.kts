import com.saveourtool.save.buildutils.*

plugins {
    id("com.saveourtool.save.buildutils.kotlin-jvm-configuration")
    id("com.saveourtool.save.buildutils.spring-boot-configuration")
    kotlin("plugin.allopen")
    alias(libs.plugins.kotlin.plugin.jpa)
}

kotlin {
    allOpen {
        annotation("javax.persistence.Entity")
        annotation("org.springframework.stereotype.Service")
    }

    sourceSets {
        sourceSets.all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
    }
}

dependencies {
    implementation(projects.saveCloudCommon)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.security.core)
    implementation("org.springframework:spring-jdbc")
    testImplementation(libs.spring.security.test)
    testImplementation(libs.junit.jupiter.api)
}

configureJacoco()
configureSpotless()
