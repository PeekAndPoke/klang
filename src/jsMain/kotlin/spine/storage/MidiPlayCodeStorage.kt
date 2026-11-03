/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine.storage

import io.peekandpoke.klang.common.logging.LogArea
import io.peekandpoke.klang.common.logging.Logger
import io.peekandpoke.klang.midi.MidiPlayCode
import io.peekandpoke.klang.storage.MidiInstrument
import io.peekandpoke.klang.storage.MidiInstrumentStorage
import io.peekandpoke.klang.storage.StorageKeys
import kotlinx.browser.localStorage

private val log = Logger(area = LogArea.Storage, source = "MidiPlayCodeStorage")

/**
 * Where the MIDI rig keeps its play code (KLANG-00402, D7), in the browser's localStorage: the one the user applied last
 * (one stored code, the maintainer, 2026-11-02; a step is for the session). A browser that kept the page's instruments
 * before the one input finds them in its first play code, once ([MidiPlayCode.fromOld]). The storage can throw (a
 * private window, blocked site data): then nothing is kept, and the app still works.
 */
object MidiPlayCodeStorage {

    /** The instrument the page started with before the one play code, saved as the draft on every visit. */
    private val OLD_DEFAULT = MidiInstrument(
        name = "Warm saw",
        ignitorCode = "Ign.saw()\n  .plus(Ign.sine().detune(12).mul(0.3))\n  .lowpass(1800)\n  .adsr(0.01, 0.3, 0.6, 0.4)",
        mapperCode = "reverb(wet = 0.2)",
    )

    /** The play code kept; else an old save as one, kept from then on; null with neither. */
    fun load(): String? = try {
        localStorage.getItem(StorageKeys.MIDI_PLAY_CODE) ?: fromOld()?.also { save(it) }
    } catch (e: Throwable) {
        log.warn("Could not read the play code", e)
        null
    }

    fun save(code: String) {
        try {
            localStorage.setItem(StorageKeys.MIDI_PLAY_CODE, code)
        } catch (e: Throwable) {
            log.warn("Could not keep the play code", e)
        }
    }

    /** The page's draft and saved instruments of before the one input as one play code; null where there are none. */
    private fun fromOld(): String? {
        val draft = MidiInstrumentStorage.loadDraft()
        val saved = MidiInstrumentStorage.loadAll()

        // Nothing of the user's: none, or only the page's own default it saved on every visit, which stays the app's
        if (saved.isEmpty() && (draft == null || draft == OLD_DEFAULT)) {
            return null
        }

        return MidiPlayCode.fromOld(draft = draft, saved = saved)
    }
}
