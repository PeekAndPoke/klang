/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One instrument authored in the Midi Playground: both inputs as KlangScript source.
 *
 * @property ignitorCode Evaluates to an Ignitor; blank = the built-in sound picked on the page.
 * @property mapperCode Evaluates to a pattern mapper (`x => x.reverb(wet = 0.2)`); blank = none.
 */
data class MidiInstrument(
    val name: String,
    val ignitorCode: String,
    val mapperCode: String,
)

/**
 * The playground's instruments in the browser's localStorage: the saved list, plus the draft the
 * editors held when last applied, so a reload picks up where we left off.
 */
object MidiInstrumentStorage {

    private const val KEY_INSTRUMENTS = "klang-midi-instruments"
    private const val KEY_DRAFT = "klang-midi-instrument-draft"

    fun loadAll(): List<MidiInstrument> {
        val raw = localStorage.getItem(KEY_INSTRUMENTS) ?: return emptyList()

        return try {
            Json.parseToJsonElement(raw).jsonArray.mapNotNull { decode(it.jsonObject) }
        } catch (e: Throwable) {
            console.warn("MidiInstrumentStorage: ignoring unreadable instruments", e)
            emptyList()
        }
    }

    fun saveAll(instruments: List<MidiInstrument>) {
        localStorage.setItem(KEY_INSTRUMENTS, JsonArray(instruments.map { encode(it) }).toString())
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

    fun saveDraft(instrument: MidiInstrument) {
        localStorage.setItem(KEY_DRAFT, encode(instrument).toString())
    }

    private fun encode(instrument: MidiInstrument): JsonObject = JsonObject(
        mapOf(
            "name" to JsonPrimitive(instrument.name),
            "ignitor" to JsonPrimitive(instrument.ignitorCode),
            "mapper" to JsonPrimitive(instrument.mapperCode),
        )
    )

    private fun decode(obj: JsonObject): MidiInstrument? {
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return null

        return MidiInstrument(
            name = name,
            ignitorCode = obj["ignitor"]?.jsonPrimitive?.contentOrNull ?: "",
            mapperCode = obj["mapper"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }
}
