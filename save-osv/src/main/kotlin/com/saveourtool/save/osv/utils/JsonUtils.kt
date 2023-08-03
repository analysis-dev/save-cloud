/**
 * File contains util methods for Json
 */

package com.saveourtool.save.osv.utils

import java.io.InputStream

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*

/**
 * @param content
 * @return decoded single [T] or array of [T] from json [content]
 */
inline fun <reified T : Any> Json.decodeSingleOrArrayFromString(content: String): List<T> {
    val jsonElement = parseToJsonElement(content)
    return if (jsonElement is JsonArray) {
        decodeFromJsonElement(jsonElement)
    } else {
        listOf(decodeFromJsonElement(jsonElement))
    }
}

/**
 * @param inputStream
 * @return decoded single [T] or array of [T] from json [inputStream]
 */
@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T : Any> Json.decodeSingleOrArrayFromStream(inputStream: InputStream): List<T> {
    val jsonElement: JsonElement = decodeFromStream(inputStream)
    return if (jsonElement is JsonArray) {
        decodeFromJsonElement(jsonElement)
    } else {
        listOf(decodeFromJsonElement(jsonElement))
    }
}