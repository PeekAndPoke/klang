/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The text form of the user's keys (KLANG-00325): a JSON object from action id to the list of keys,
 * `{"klang.stop": ["F8"]}`. In common code so its spec runs on the JVM; `KeymapStorage` keeps the text.
 */
object KeymapJson {

    /**
     * The user's keys in [text]. An entry that is no list of strings is skipped, an unreadable text is no keys at all.
     */
    fun read(text: String): Map<String, List<String>> {
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return emptyMap()
        }

        return root.mapNotNull { (actionId, keys) ->
            val array = keys as? JsonArray ?: return@mapNotNull null
            val texts = array.map { (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.contentOrNull }

            when {
                texts.any { it == null } -> null
                else -> actionId to texts.filterNotNull()
            }
        }.toMap()
    }

    /** [userKeys] as text. */
    fun write(userKeys: Map<String, List<String>>): String =
        JsonObject(userKeys.mapValues { (_, keys) -> JsonArray(keys.map { JsonPrimitive(it) }) }).toString()
}
