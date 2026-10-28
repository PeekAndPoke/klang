/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import io.peekandpoke.klang.ui.feel.KlangLookPreset
import io.peekandpoke.klang.ui.feel.KlangLookShelf
import io.peekandpoke.klang.ui.feel.KlangLookText
import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The looks in the browser's localStorage: the look the user chose, by its id (KLANG-00339), and the user's own looks
 * with the equipped slots (KLANG-00382), as [KlangLookText]'s JSON.
 *
 * The storage can throw or come back empty (a private window, blocked site data); the app then starts with the
 * built-in looks and the default look and still works, it only forgets on reload. An id no look has any more starts
 * the default too.
 */
object LookStorage {

    /** The own looks and the slots into [KlangLookShelf], then each change of them back here; once, at start. */
    fun restoreShelf() {
        val json = try {
            localStorage.getItem(StorageKeys.LOOKS)?.let { Json.parseToJsonElement(it).jsonObject }
        } catch (e: Throwable) {
            console.warn("LookStorage: could not read the looks", e)
            null
        }

        if (json != null) {
            val own = (json["own"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(KlangLookText::fromJson) }
            val slots = (json["slots"] as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull }

            KlangLookShelf.restore(own = own, slots = slots)
        }

        KlangLookShelf.subscribeToStream { saveShelf(it) }
    }

    private fun saveShelf(state: KlangLookShelf.State) {
        val json = buildJsonObject {
            put("own", JsonArray(state.own.map { KlangLookText.toJson(it, withId = true) }))
            put("slots", JsonArray(state.loadout.slots.map { if (it == null) JsonNull else JsonPrimitive(it) }))
        }

        try {
            localStorage.setItem(StorageKeys.LOOKS, json.toString())
        } catch (e: Throwable) {
            console.warn("LookStorage: could not remember the looks", e)
        }
    }

    /** The remembered look, built-in or own; null when nothing (readable, known) is stored. Call after [restoreShelf]. */
    fun load(): KlangLookPreset? {
        val id = try {
            localStorage.getItem(StorageKeys.LOOK)
        } catch (e: Throwable) {
            console.warn("LookStorage: could not read the look", e)
            null
        }

        return id?.let { KlangLookShelf().byId(it) }
    }

    fun save(preset: KlangLookPreset) {
        try {
            localStorage.setItem(StorageKeys.LOOK, preset.id)
        } catch (e: Throwable) {
            console.warn("LookStorage: could not remember the look", e)
        }
    }
}
