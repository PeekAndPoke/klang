/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The playground's instruments as the page kept them in the browser's localStorage before the one play code (KLANG-00402,
 * D7): the saved list and the draft. Only read now, once, for the play code's first load (`MidiPlayCodeStorage`).
 */
object MidiInstrumentStorage {

    private const val KEY_INSTRUMENTS = StorageKeys.MIDI_INSTRUMENTS
    private const val KEY_DRAFT = StorageKeys.MIDI_INSTRUMENT_DRAFT

    fun loadAll(): List<MidiInstrument> {
        val raw = localStorage.getItem(KEY_INSTRUMENTS) ?: return emptyList()

        return try {
            Json.parseToJsonElement(raw).jsonArray.mapNotNull { decode(it.jsonObject) }
        } catch (e: Throwable) {
            console.warn("MidiInstrumentStorage: ignoring unreadable instruments", e)
            emptyList()
        }
    }

    fun loadDraft(): MidiInstrument? {
        val raw = localStorage.getItem(KEY_DRAFT) ?: return null

        return try {
            decode(Json.parseToJsonElement(raw).jsonObject)
        } catch (e: Throwable) {
            console.warn("MidiInstrumentStorage: ignoring unreadable draft", e)
            null
        }
    }

    private fun decode(obj: JsonObject): MidiInstrument? {
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return null

        return MidiInstrument(
            name = name,
            ignitorCode = obj["ignitor"]?.jsonPrimitive?.contentOrNull ?: "",
            mapperCode = obj["mapper"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }
}
