/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * A look as text (KLANG-00382): to copy and paste it, and to keep the user's own in the browser. A name, an author and
 * the roles' values by their CSS names; never CSS text, never a URL. On the way in every value is checked against its
 * role ([KlangRole.accepts]) and dropped when it does not fit, an unknown name is dropped, and the name is cut to
 * [KlangLookPreset.NAME_MAX]: pasted text is data, never trusted.
 */
object KlangLookText {

    private const val MARK = "klang-look"

    /** [look] as JSON; [withId] for the browser's store, without for sharing (the id is the receiver's to give). */
    fun toJson(look: KlangLookPreset, withId: Boolean): JsonObject = buildJsonObject {
        put(MARK, 1)

        if (withId) {
            put("id", look.id)
        }

        put("name", look.name)
        put("author", look.author)
        put("values", buildJsonObject {
            for ((role, value) in look.values) {
                put(role.cssVar, value)
            }
        })
    }

    /** [look] as text to share. */
    fun encode(look: KlangLookPreset): String = toJson(look, withId = false).toString()

    /** A look of the user's own from [json], under [id] (or the id it carries); null when it is not a look. */
    fun fromJson(json: JsonObject, id: String? = null): KlangLookPreset? {
        if (json[MARK] == null) {
            return null
        }

        val values = (json["values"] as? JsonObject).orEmpty().mapNotNull { (name, value) ->
            val role = KlangRole.ofCssVar(name) ?: return@mapNotNull null
            val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: return@mapNotNull null

            if (role.accepts(text)) role to text else null
        }.toMap()

        return KlangLookPreset(
            id = id ?: json.string("id") ?: return null,
            name = cleanName(json.string("name").orEmpty()),
            values = values,
            author = json.string("author").orEmpty().take(AUTHOR_MAX),
            own = true,
        )
    }

    /** A pasted look under [id]; null when [text] is not one. */
    fun decode(text: String, id: String): KlangLookPreset? {
        val json = try {
            Json.parseToJsonElement(text.trim()).jsonObject
        } catch (e: Throwable) {
            return null
        }

        return fromJson(json, id = id)
    }

    /** A name as a look may have it: no control characters, trimmed, at most [KlangLookPreset.NAME_MAX]; else "Mine". */
    fun cleanName(name: String): String =
        name.filter { it >= ' ' }.trim().take(KlangLookPreset.NAME_MAX).trim().ifEmpty { "Mine" }

    private const val AUTHOR_MAX = 40

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}
