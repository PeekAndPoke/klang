/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The text form the user's keys had until KLANG-00422: a JSON object from action id to the list of keys,
 * `{"klang.stop": ["F8"]}`. Only read, once, into the stored section (the Spine's `UserKeys`). Scaffolding: it goes
 * with the old key. In common code so its spec runs on the JVM.
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
}
