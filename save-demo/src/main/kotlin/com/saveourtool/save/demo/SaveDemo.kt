package com.saveourtool.save.demo

import com.saveourtool.save.demo.config.ConfigProperties
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.XADataSourceAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties

/**
 * An entrypoint for spring boot for save-demo
 */
@EnableConfigurationProperties(ConfigProperties::class)
@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class, XADataSourceAutoConfiguration::class])
open class SaveDemo

fun main(args: Array<String>) {
    SpringApplication.run(SaveDemo::class.java, *args)
}
