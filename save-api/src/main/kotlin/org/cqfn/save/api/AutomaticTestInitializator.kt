package org.cqfn.save.api

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.cqfn.save.core.result.DebugInfo
import org.cqfn.save.core.result.Pass
import org.cqfn.save.domain.TestResultDebugInfo
import org.cqfn.save.domain.TestResultLocation
import java.util.*

// TODO move into properties file
private const val BACKEND_URL = "http://localhost:5000/internal"
private const val PREPROCESSOR_URL = "http://localhost:5200"


internal val json = Json {
    serializersModule = SerializersModule {
        polymorphic(TestResultDebugInfo::class)
        polymorphic(DebugInfo::class)
        polymorphic(Pass::class)
    }
}

class AutomaticTestInitializator {
    @OptIn(InternalAPI::class)
    suspend fun start() {
        val httpClient = HttpClient(Apache) {
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.ALL
            }
            install(JsonFeature) {
                serializer = KotlinxSerializer(json)
            }
            install(Auth) {
                basic {
                    sendWithoutRequest { request ->
                        request.url.host == "localhost"
                    }
                    //sendWithoutRequest = true
                    credentials {
                        // no need `:` after username
                        BasicAuthCredentials(username = "admin", password = "")
                        //BasicAuthCredentials(username = "YWRtaW46", password = "")
                    }
                    //realm = "Access to the '/' path"
                }
            }
        }

        println("-------------------Start post debug info---------------------")

        val testResultDebugInfo  = TestResultDebugInfo(
            TestResultLocation(
                "stub",
                "stub",
                "stub",
                "stub",
            ),
            DebugInfo(
                "stub",
                "stub",
                "stub",
                1
            ),
            Pass(
               "ok",
               "ok",
            )
        )
        httpClient.post<HttpResponse> {
            url("${BACKEND_URL}/internal/files/debug-info?agentId=test-agent-id")
            //header("Authorization", "Basic ${Base64.getEncoder().encodeToString("admin:".toByteArray())}")
            contentType(ContentType.Application.Json)
            body = testResultDebugInfo
        }

        println("-------------------Start execution---------------------")

        httpClient.submitForm<HttpResponse> (
            url = "${BACKEND_URL}/submitExecutionRequest",
            formParameters = Parameters.build {
                append("first_name", "John")
                append("last_name", "Doe")
            }
        )


    }
}
