package org.cqfn.save.backend.configs

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.web.reactive.function.server.router

/**
 * Configuration class that enables serving static resources
 */
@Configuration
class WebConfiguration(
    private val configProperties: ConfigProperties
) {
    /**
     * @return a router bean
     */
    @Bean
    fun staticResourceRouter() = router {
        resources("/**", ClassPathResource("static/"))
    }

    /**
     * @return a router for image bean
     */
    @Bean
    fun staticImageResourceRouter() = router {
        resources("/api/avatar/**", FileSystemResource("${configProperties.fileStorage.location}/images/avatars/"))
    }

    /**
     * @param html requested resource
     * @return router bean
     */
    @Bean
    fun indexRouter(@Value("classpath:/static/index.html") html: Resource) = router {
        GET("/") {
            ok().header("Content-Type", "text/html; charset=utf8").bodyValue(html)
        }
    }
}
