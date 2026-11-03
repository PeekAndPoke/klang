/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine.storage

import io.peekandpoke.klang.common.logging.LogArea
import io.peekandpoke.klang.common.logging.Logger
import io.peekandpoke.klang.spine.Recents
import io.peekandpoke.klang.storage.StorageKeys
import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

private val log = Logger(area = LogArea.Storage, source = "RecentsStorage")

/**
 * Where [Recents] keeps what was opened, as JSON in the browser's localStorage: the first store in the Spine's own
 * storage (KLANG-00389, the maintainer, 2026-11-01: "new stores straight into spine/storage"); only the Spine reads and
 * writes it. The storage can throw (a private window, blocked site data): then nothing is kept, and the app still works.
 */
object RecentsStorage {

    fun load(): List<Recents.Entry> = try {
        localStorage.getItem(StorageKeys.RECENTS)
            ?.let { decode(Json.parseToJsonElement(it)) }
            ?: emptyList()
    } catch (e: Throwable) {
        log.warn("Ignoring an unreadable list", e)
        emptyList()
    }

    fun save(entries: List<Recents.Entry>) {
        try {
            localStorage.setItem(StorageKeys.RECENTS, encode(entries).toString())
        } catch (e: Throwable) {
            log.warn("Could not remember what was opened", e)
        }
    }

    fun encode(entries: List<Recents.Entry>): JsonArray = JsonArray(
        entries.map { entry ->
            JsonObject(
                listOfNotNull(
                    "kind" to JsonPrimitive(entry.kind.name),
                    "id" to JsonPrimitive(entry.id),
                    "title" to JsonPrimitive(entry.title),
                    "at" to JsonPrimitive(entry.at),
                    entry.rpm?.let { "rpm" to JsonPrimitive(it) },
                    entry.track?.let { "track" to JsonPrimitive(it) },
                ).toMap()
            )
        }
    )

    /** The entries [json] holds; an entry that cannot be read (an unknown kind, a missing id) is left out, not the list. */
    fun decode(json: JsonElement): List<Recents.Entry> = (json as? JsonArray).orEmpty().mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null

        fun text(name: String): String? = (obj[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

        // A time or a tempo that is no number (NaN, infinite) is as unreadable as a missing one
        fun number(name: String): Double? = (obj[name] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }

        val kind = Recents.Kind.entries.firstOrNull { it.name == text("kind") } ?: return@mapNotNull null

        Recents.Entry(
            kind = kind,
            id = text("id") ?: return@mapNotNull null,
            title = text("title") ?: return@mapNotNull null,
            at = number("at") ?: return@mapNotNull null,
            rpm = number("rpm"),
            track = text("track"),
        )
    }
}
