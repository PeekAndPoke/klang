/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine.storage

import io.peekandpoke.klang.common.logging.LogArea
import io.peekandpoke.klang.common.logging.Logger
import io.peekandpoke.klang.midi.MidiLoadout
import io.peekandpoke.klang.storage.StorageKeys
import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

private val log = Logger(area = LogArea.Storage, source = "MidiLoadoutStorage")

/**
 * Where the MIDI rig keeps its loadout (KLANG-00402, step 5c), in the browser's localStorage: the instruments' codes in
 * their order, as a JSON array. What cannot be read is an empty loadout. The storage can throw (a private window,
 * blocked site data): then nothing is kept, and the app still works.
 */
object MidiLoadoutStorage {

    fun load(): MidiLoadout = try {
        val codes = localStorage.getItem(StorageKeys.MIDI_LOADOUT)
            ?.let { raw -> Json.parseToJsonElement(raw).jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }
            .orEmpty()

        MidiLoadout.ofCodes(codes)
    } catch (e: Throwable) {
        log.warn("Could not read the loadout", e)
        MidiLoadout()
    }

    fun save(loadout: MidiLoadout) {
        try {
            localStorage.setItem(StorageKeys.MIDI_LOADOUT, JsonArray(loadout.codes.map { JsonPrimitive(it) }).toString())
        } catch (e: Throwable) {
            log.warn("Could not keep the loadout", e)
        }
    }
}
