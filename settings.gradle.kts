rootProject.name = "save-cloud"

dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            content {
                includeGroup("com.saveourtool.sarifutils")
            }
        }
        mavenCentral()
        setOf(
            "saveourtool/okio-extras",
            "0x6675636b796f75676974687562/kotlintree"
        ).forEach { githubCoordinate ->
            maven {
                name = githubCoordinate
                url = uri("https://maven.pkg.github.com/$githubCoordinate")
                credentials {
                    username = providers.gradleProperty("gprUser").orNull
                        ?: System.getenv("GITHUB_ACTOR")
                    password = providers.gradleProperty("gprKey").orNull
                        ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradle.enterprise") version "3.12.6"
}

includeBuild("gradle/plugins")
include("api-gateway")
include("save-backend")
include("save-orchestrator-common")
include("save-orchestrator")
include("save-frontend")
include("save-cloud-common")
include("save-agent")
include("save-preprocessor")
include("test-utils")
include("save-api")
include("save-api-cli")
include("save-sandbox")
include("authentication-service")
include("save-demo")
include("save-demo-cpg")
include("test-analysis-core")
include("save-demo-agent")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

gradleEnterprise {
    @Suppress("AVOID_NULL_CHECKS")
    if (System.getenv("CI") != null) {
        buildScan {
            publishAlways()
            termsOfServiceUrl = "https://gradle.com/terms-of-service"
            termsOfServiceAgree = "yes"
        }
    }
}
