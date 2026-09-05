/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import kotlinx.browser.window
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

/**
 * Minimal Web MIDI API surface for the Midi Playground.
 *
 * The browser types are external interfaces (structural), so no @JsName mapping is needed.
 * See https://developer.mozilla.org/en-US/docs/Web/API/Web_MIDI_API
 */

external interface MidiInputMap {
    val size: Int
    fun forEach(callback: (input: MidiInput, key: String) -> Unit)
}

external interface MidiPort {
    val id: String
    val name: String?
    val manufacturer: String?
    /** "input" | "output" */
    val type: String
    /** "connected" | "disconnected" */
    val state: String
}

external interface MidiInput : MidiPort {
    var onmidimessage: ((MidiMessageEvent) -> Unit)?
}

external interface MidiMessageEvent {
    val data: Uint8Array
    /** DOMHighResTimeStamp of the event, ms on the page's performance clock */
    val timeStamp: Double
}

external interface MidiConnectionEvent {
    val port: MidiPort
}

external interface MidiAccess {
    val inputs: MidiInputMap
    var onstatechange: ((MidiConnectionEvent) -> Unit)?
}

/** Reads one byte of the message as Int — no Byte types on Kotlin/JS. */
fun MidiMessageEvent.byteAt(index: Int): Int =
    data.asDynamic()[index].unsafeCast<Int>()

/** True when the browser exposes the Web MIDI API (Chrome/Edge yes, Safari no). */
fun isWebMidiSupported(): Boolean =
    window.navigator.asDynamic().requestMIDIAccess != null

/** Requests MIDI access (no sysex). Rejects when unsupported or the user denies permission. */
fun requestMidiAccess(): Promise<MidiAccess> =
    window.navigator.asDynamic().requestMIDIAccess().unsafeCast<Promise<MidiAccess>>()
