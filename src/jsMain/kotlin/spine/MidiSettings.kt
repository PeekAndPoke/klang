/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.common.logging.LogArea
import io.peekandpoke.klang.common.logging.Logger
import io.peekandpoke.klang.midi.KeyLayout
import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.ops.combinedWith
import kotlinx.browser.window
import kotlin.js.Promise

private val log = Logger(area = LogArea.Midi, source = "MidiSettings")

/**
 * The MIDI settings, a part of the Spine (KLANG-00402): the laptop keys' [layout] and the [letters] it gives, which only
 * say which letters a drawing prints (D9; what plays goes by the keys' positions), and the device keybed's alignment
 * ([keyBedOffset]). Shared by every drawing of the rig, the MIDI page's and the MIDI tool's.
 */
class MidiSettings(
    /** Where the layout chosen is kept, this machine's; the default lives and dies with these settings (a spec's). */
    private val kept: MidiKept = MidiKept(stores = SectionStores.inMemory()),
) {

    /**
     * The layout chosen by hand; null for "Automatic" (the browser's map where it has one, else learned key by key),
     * and for a name no layout has (one from another version).
     */
    val layout: Stream<KeyLayout?> = kept.desk.part { desk -> KeyLayout.entries.firstOrNull { it.name == desk.keyLayout } }

    fun chooseLayout(layout: KeyLayout?) {
        kept.desk.update { it.copy(keyLayout = layout?.name) }
    }

    /** The browser's own letters by key position, where it tells them (Chrome, Edge); null elsewhere. */
    private val browserLetters = StreamSource<Map<String, String>?>(null)

    /** The letters learned from the keys pressed, by position: what "Automatic" shows where the browser tells nothing. */
    private val learnedLetters = StreamSource<Map<String, String>>(emptyMap())

    private var browserAsked = false

    /** The letter on the key at a position: the layout chosen, else the browser's, else learned, else the English one. */
    val letters: Stream<(code: String) -> String> = layout
        .combinedWith(browserLetters) { chosen, browser -> chosen to browser }
        .combinedWith(learnedLetters) { (chosen, browser), learned ->
            { code: String -> chosen?.labelOf(code) ?: browser?.get(code) ?: learned[code] ?: KeyLayout.English.labelOf(code) }
        }

    /** "Automatic" learns a key's [letter] as it is first pressed, where the browser does not tell the layout. */
    fun learn(code: String, letter: String) {
        if (code !in learnedLetters()) {
            learnedLetters(learnedLetters() + (code to letter))
        }
    }

    /** Asks the browser for its layout's letters, once, where it tells them (Chrome, Edge: the Keyboard Map API). */
    fun readBrowserLetters() {
        if (browserAsked) {
            return
        }

        browserAsked = true

        val keyboard = window.navigator.asDynamic().keyboard ?: return

        try {
            val read: Promise<dynamic> = keyboard.getLayoutMap().unsafeCast<Promise<dynamic>>()

            read.then<Unit> { map ->
                val letters = mutableMapOf<String, String>()
                val note: (String, String) -> Unit = { letter, code -> letters[code] = letter.uppercase() }

                map.forEach(note)
                browserLetters(letters)
            }.catch { e ->
                // Refused (a frame without the keyboard-map permission): the letters are learned instead
                log.warn("The browser's keyboard map is not readable", e)
            }
        } catch (e: Throwable) {
            log.warn("The browser's keyboard map is not readable", e)
        }
    }

    /**
     * The semitones the device's drawn keybed is shifted by, matching its Transpose and Octave settings; for this visit.
     *
     * MANUAL, and it cannot be otherwise: those buttons change what the keys transmit and send no MIDI at all, and a note
     * transposed down a semitone is byte-for-byte identical to the untransposed note below it. Nothing in the stream
     * distinguishes them.
     *
     * An earlier version guessed this from notes landing outside the drawn range. That was not inference: an
     * out-of-range note only says the drawing cannot show it, not why (a second device on another range does the same
     * thing), and once this became settable it silently overrode the user's own choice. Removed deliberately; do not
     * reintroduce it.
     */
    val keyBedOffset = StreamSource(0)
}
