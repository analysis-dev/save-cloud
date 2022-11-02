plugins {
    // workaround https://github.com/gradle/gradle/issues/16345
    `kotlin-dsl`.version("2.3.3")
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // workaround https://github.com/gradle/gradle/issues/15383
    implementation(files(project.libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.diktat.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.reckon.gradle.plugin)
    implementation(libs.kotlin.plugin.allopen)
    implementation(libs.gradle.plugin.spotless)
    implementation(libs.publish.gradle.plugin)
    implementation(libs.download.plugin)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + "-opt-in=kotlin.RequiresOptIn"
    }
}
