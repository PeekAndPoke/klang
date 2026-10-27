/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import io.peekandpoke.klang.ui.comp.KlangWorkbench
import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The workbench's layout in the browser's localStorage (KLANG-00349): the pinned tool windows and the bottom panel,
 * so a reload finds them where they were.
 *
 * The storage can throw or come back empty (a private window, blocked site data); the bench then starts bare and
 * still works, it only forgets on reload. A part it cannot read starts at its default.
 */
object WorkbenchLayoutStorage {

    /** The kept layout; null when nothing readable is stored. */
    fun load(): KlangWorkbench.Layout? {
        return try {
            val raw = localStorage.getItem(StorageKeys.WORKBENCH_LAYOUT) ?: return null

            decode(Json.parseToJsonElement(raw).jsonObject)
        } catch (e: Throwable) {
            console.warn("WorkbenchLayoutStorage: ignoring an unreadable layout", e)
            null
        }
    }

    fun save(layout: KlangWorkbench.Layout) {
        try {
            localStorage.setItem(StorageKeys.WORKBENCH_LAYOUT, encode(layout).toString())
        } catch (e: Throwable) {
            console.warn("WorkbenchLayoutStorage: could not remember the layout", e)
        }
    }

    internal fun encode(layout: KlangWorkbench.Layout): JsonObject = JsonObject(
        listOfNotNull(
            "pinned" to JsonArray(layout.pinned.sorted().map { JsonPrimitive(it) }),
            layout.open?.let { "open" to JsonPrimitive(it) },
            "bottomOpen" to JsonPrimitive(layout.bottomOpen),
            "bottomPinned" to JsonPrimitive(layout.bottomPinned),
            "bottomHeight" to JsonPrimitive(layout.bottomHeight),
        ).toMap()
    )

    internal fun decode(obj: JsonObject): KlangWorkbench.Layout {
        val defaults = KlangWorkbench.Layout()

        return KlangWorkbench.Layout(
            pinned = (obj["pinned"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.toSet()
                ?: defaults.pinned,
            open = (obj["open"] as? JsonPrimitive)?.contentOrNull,
            bottomOpen = (obj["bottomOpen"] as? JsonPrimitive)?.booleanOrNull ?: defaults.bottomOpen,
            bottomPinned = (obj["bottomPinned"] as? JsonPrimitive)?.booleanOrNull ?: defaults.bottomPinned,
            // A height that is no number (NaN, infinite) starts at the default, as one that cannot be read
            bottomHeight = (obj["bottomHeight"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }
                ?: defaults.bottomHeight,
        )
    }
}
