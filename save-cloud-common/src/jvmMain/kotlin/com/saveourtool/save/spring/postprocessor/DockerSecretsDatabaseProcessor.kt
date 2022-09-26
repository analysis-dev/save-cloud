package com.saveourtool.save.spring.postprocessor

import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.boot.logging.DeferredLogFactory
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.PropertiesPropertySource
import org.springframework.core.io.FileSystemResource
import org.springframework.util.StreamUtils
import java.nio.charset.Charset
import java.util.Properties

/**
 * Post processor that collects credentials for database from docker secrets
 */
class DockerSecretsDatabaseProcessor(
    logFactory: DeferredLogFactory
) : EnvironmentPostProcessor {
    private val log = logFactory.getLog(DockerSecretsDatabaseProcessor::class.java)

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        // Since `EnvironmentPostProcessor` is created before application context is fully initialized, usual means of registration control
        // like `@Profile` cannot be used. `environment` here should contain at least profiles set from env variable or system properties.
        if ("docker-secrets" !in environment.activeProfiles) {
            log.debug("Skipping activation of ${this::class.simpleName} because of active profiles")
            return
        }
        val secretsPrefix = System.getenv("SECRETS_PREFIX") ?: ""
        val secretsBasePath = System.getenv("SECRETS_PATH") ?: "/run/secrets"
        log.debug("Started DockerSecretsDatabaseProcessor [EnvironmentPostProcessor] configured to look up secrets in $secretsBasePath")
        val passwordResource = FileSystemResource("$secretsBasePath/${secretsPrefix}db_password")
        val usernameResource = FileSystemResource("$secretsBasePath/${secretsPrefix}db_username")
        val jdbcUrlResource = FileSystemResource("$secretsBasePath/${secretsPrefix}db_url")

        if (passwordResource.exists()) {
            log.debug("Acquired password. Beginning to setting properties")
            val dbPassword = passwordResource.inputStream.use { StreamUtils.copyToString(it, Charset.defaultCharset()) }
            val dbUsername = usernameResource.inputStream.use { StreamUtils.copyToString(it, Charset.defaultCharset()) }
            val dbUrl = jdbcUrlResource.inputStream.use { StreamUtils.copyToString(it, Charset.defaultCharset()) }
            val props = Properties()
            props["spring.datasource.password"] = dbPassword
            props["spring.datasource.username"] = dbUsername
            props["spring.datasource.url"] = dbUrl
            environment.propertySources.addLast(PropertiesPropertySource("dbProps", props))
            log.debug("Properties have been set")
        }
    }
}
