import com.saveourtool.save.buildutils.configureJacoco
import com.saveourtool.save.buildutils.configureSpotless

plugins {
    id("com.saveourtool.save.buildutils.kotlin-jvm-configuration")
    id("com.saveourtool.save.buildutils.spring-boot-configuration")
}

dependencies {
    api(projects.saveCloudCommon)
    implementation(libs.spring.cloud.starter.gateway)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.client)
    implementation(libs.spring.cloud.starter.kubernetes.client.config)
    implementation(libs.spring.security.core)
}

configureJacoco()
configureSpotless()
