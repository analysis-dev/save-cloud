import com.saveourtool.save.buildutils.configureSpotless

plugins {
    id("com.saveourtool.save.buildutils.kotlin-jvm-configuration")
}

dependencies {
    implementation(project.dependencies.platform(libs.spring.boot.dependencies))
    implementation(libs.okhttp.mockwebserver)
    implementation(libs.okhttp)
    implementation(libs.slf4j.api)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
}

configureSpotless()
