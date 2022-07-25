/**
 * Main entrypoint for SAVE Agent
 */

package com.saveourtool.save.agent

import com.saveourtool.save.agent.utils.logDebugCustom
import com.saveourtool.save.agent.utils.logInfoCustom
import com.saveourtool.save.agent.utils.readProperties
import com.saveourtool.save.core.config.LogType
import com.saveourtool.save.core.logging.logType

import generated.SAVE_CLOUD_VERSION
import generated.SAVE_CORE_VERSION
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.json.JsonPlugin
import io.ktor.client.plugins.kotlinx.serializer.KotlinxSerializer
import platform.posix.*

import kotlinx.cinterop.staticCFunction
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.decodeFromStringMap

internal val json = Json {
    serializersModule = SerializersModule {
        contextual(HeartbeatResponse::class, PolymorphicSerializer(HeartbeatResponse::class))
        polymorphic(HeartbeatResponse::class) {
            subclass(NewJobResponse::class)
            subclass(ContinueResponse::class)
            subclass(WaitResponse::class)
            subclass(TerminateResponse::class)
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun main() {
    val config: AgentConfiguration = Properties.decodeFromStringMap(
        readProperties("agent.properties")
    )
    logType.set(if (config.debug) LogType.ALL else LogType.WARN)
    logDebugCustom("Instantiating save-agent version $SAVE_CLOUD_VERSION with config $config")

    platform.posix.chmod(
        "save-$SAVE_CORE_VERSION-linuxX64.kexe",
        (S_IRUSR or S_IWUSR or S_IXUSR or S_IRGRP or S_IROTH).toUInt()
    )

    signal(SIGTERM, staticCFunction<Int, Unit> {
        logInfoCustom("Agent is shutting down because SIGTERM has been received")
        exit(1)
    })

    val httpClient = HttpClient {
        install(JsonPlugin) {
            serializer = KotlinxSerializer(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = config.requestTimeoutMillis
        }
    }

    runBlocking {
        // Launch in a new scope, because we cancel the scope on graceful termination,
        // and `BlockingCoroutine` shouldn't be cancelled.
        launch {
            val saveAgent = SaveAgent(config, httpClient, coroutineScope = this)

            val mainJob = saveAgent.start()
            mainJob.join()
        }
    }
    logInfoCustom("Agent is shutting down")
}
