/**
 * Contains util method to add a runtime dependency from a file
 */

package com.saveourtool.save.buildutils

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.register

private typealias AddFunction = (String, Any) -> Dependency?

/**
 * @param gradlePropertyName
 * @param buildSubDirectory
 * @param copyTaskName
 * @param logMsgPrefix
 * @param add [org.gradle.kotlin.dsl.support.delegates.DependencyHandlerDelegate.add]
 */
fun Project.addRuntimeDependency(
    gradlePropertyName: String,
    buildSubDirectory: String,
    copyTaskName: String,
    logMsgPrefix: String,
    add: AddFunction,
) {
    val saveAgentPath = providers.gradleProperty(gradlePropertyName)
    if (saveAgentPath.isPresent) {
        val target = layout.buildDirectory.dir(buildSubDirectory)
        logger.info(
            "{}: add {} as a runtime dependency",
            logMsgPrefix, saveAgentPath
        )

        @Suppress("GENERIC_VARIABLE_WRONG_DECLARATION")
        val copySaveAgent = tasks.register<Copy>(copyTaskName) {
            from(saveAgentPath)
            into(target)
            eachFile {
                duplicatesStrategy = DuplicatesStrategy.WARN
            }
        }
        add("runtimeOnly", files(target).apply { builtBy(copySaveAgent) })
    }
}
