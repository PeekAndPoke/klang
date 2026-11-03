/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine.storage

import io.peekandpoke.klang.common.logging.LogArea
import io.peekandpoke.klang.common.logging.Logger
import io.peekandpoke.klang.midi.KeyLayout
import io.peekandpoke.klang.storage.StorageKeys
import kotlinx.browser.localStorage

private val log = Logger(area = LogArea.Storage, source = "MidiSettingsStorage")

/**
 * Where [io.peekandpoke.klang.spine.MidiSettings] keeps the laptop keys' layout, a layout's name in the browser's
 * localStorage; nothing kept is "Automatic". The storage can throw (a private window, blocked site data): then nothing
 * is kept, and the app still works.
 */
object MidiSettingsStorage {

    /** The layout chosen by hand; null for "Automatic", and for a name no layout has (one from an older version). */
    fun loadLayout(): KeyLayout? = try {
        localStorage.getItem(StorageKeys.MIDI_KEY_LAYOUT)?.let { name -> KeyLayout.entries.firstOrNull { it.name == name } }
    } catch (e: Throwable) {
        log.warn("Could not read the layout", e)
        null
    }

    fun saveLayout(layout: KeyLayout?) {
        try {
            if (layout == null) {
                localStorage.removeItem(StorageKeys.MIDI_KEY_LAYOUT)
            } else {
                localStorage.setItem(StorageKeys.MIDI_KEY_LAYOUT, layout.name)
            }
        } catch (e: Throwable) {
            log.warn("Could not keep the layout", e)
        }
    }
}
