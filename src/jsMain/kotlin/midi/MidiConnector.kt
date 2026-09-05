/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource

/** Identity of a played key. Channel AND note — the same note on two channels is two voices. */
data class NoteKey(val channel: Int, val note: Int)

/** A key currently held down, and the voice id it started. */
data class HeldNote(val key: NoteKey, val velocity: Int, val liveId: Int)

/**
 * Owns everything MIDI: device discovery and hot-plug, byte parsing, liveId assignment, the
 * key → liveId map, and every path that produces a note-off (release, retrigger, panic CC,
 * unplug).
 *
 * It knows nothing about audio, [io.peekandpoke.klang.audio_bridge.VoiceData] or the player. It
 * emits [Event]s that a consumer maps onto a playback controller — the translation (what a note
 * SOUNDS like) is the caller's business, because that is where taste lives.
 *
 * @param nextLiveId Mints the id for a new voice. Passed as a callback rather than taking the
 *   controller, so this class stays audio-free; wire it to `ctrl::nextLiveId`.
 */
class MidiConnector(
    private val nextLiveId: () -> Int,
) {
    enum class Status { Unsupported, Requesting, Denied, Ready }

    data class State(
        val status: Status,
        val devices: List<String> = emptyList(),
        /** Keys currently down — the single source of truth for "what is sounding". */
        val held: Map<NoteKey, HeldNote> = emptyMap(),
        val error: String? = null,
    )

    /**
     * Exhaustively mappable onto a playback controller — that shape is the point: a new variant
     * fails the consumer's `when` until it decides what the event means.
     */
    sealed interface Event {
        /** A key went down. [liveId] is already minted; the consumer decides what it sounds like. */
        data class NoteOn(
            val liveId: Int,
            val key: NoteKey,
            val velocity: Int,
            val tsMs: Double,
        ) : Event

        /** A key came up — or a retrigger replaced a still-held note (see [announceNoteOn]). */
        data class NoteOff(
            val liveId: Int,
            val key: NoteKey,
            val tsMs: Double,
        ) : Event

        /**
         * Every id still held, released at once: the MIDI panic CCs (120 All Sound Off / 123 All
         * Notes Off), a device disappearing mid-chord, or teardown. Without this a held voice
         * would sing until its gate horizon — hours — because its note-off can never arrive.
         */
        data class AllNotesOff(val liveIds: List<Int>, val reason: Reason) : Event

        /** Everything else on the control-change channel; v1 maps these onto oscparams. */
        data class ControlChange(
            val channel: Int,
            val cc: Int,
            val value: Int,
            val tsMs: Double,
        ) : Event

        /**
         * Any other channel message — pitch bend, aftertouch, program change, …
         *
         * A deliberate escape hatch rather than a gap: a controller's knobs, wheels and pads are
         * only discoverable by watching what it actually emits (the same hardware sends different
         * maps in different modes, so published charts are unreliable). Promote a message to its
         * own variant once something acts on it. System-realtime bytes (clock, active sensing)
         * are dropped before they get here — they would drown everything else.
         */
        data class Other(
            val status: Int,
            val kind: Int,
            val channel: Int,
            val data1: Int,
            val data2: Int,
            val tsMs: Double,
        ) : Event
    }

    enum class Reason { PanicCc, DeviceDisconnected, TearDown }

    private val _state = StreamSource(State(status = Status.Requesting))
    val state: Stream<State> = _state.readonly

    private val _events = StreamSource<Event?>(null)
    val events: Stream<Event?> = _events.readonly

    private var access: MidiAccess? = null

    companion object {
        private const val CC_ALL_SOUND_OFF = 120
        private const val CC_ALL_NOTES_OFF = 123
    }

    /** Requests MIDI access and hooks every input. Safe to call once per mount. */
    fun start() {
        if (!isWebMidiSupported()) {
            console.warn("[MidiConnector] Web MIDI API not available on navigator")
            _state { it.copy(status = Status.Unsupported) }
            return
        }

        console.log("[MidiConnector] requesting MIDI access ...")

        requestMidiAccess()
            .then { granted ->
                console.log("[MidiConnector] MIDI access GRANTED")
                access = granted
                hookInputs(granted)
                granted.onstatechange = { evt ->
                    console.log("[MidiConnector] state change:", evt.port.name, evt.port.type, evt.port.state)
                    // An unplugged device can never send its note-offs.
                    if (evt.port.type == "input" && evt.port.state == "disconnected") {
                        releaseAll(Reason.DeviceDisconnected)
                    }
                    hookInputs(granted)
                }
            }
            .catch { err ->
                // Also catches a throw inside the handler above, not only permission denial.
                console.error("[MidiConnector] MIDI setup failed:", err)
                _state { it.copy(status = Status.Denied, error = err.message ?: err.toString()) }
            }
    }

    /** Releases everything still held and unhooks the devices. */
    fun tearDown() {
        releaseAll(Reason.TearDown)

        access?.let { granted ->
            granted.onstatechange = null
            granted.inputs.forEach { input, _ -> input.onmidimessage = null }
        }
        access = null
    }

    //  MIDI  ///////////////////////////////////////////////////////////////////////////////////

    /** (Re-)hooks all inputs. Runs on every state change, so hot-plugged devices just work. */
    private fun hookInputs(access: MidiAccess) {
        val names = mutableListOf<String>()

        access.inputs.forEach { input, _ ->
            val portName = input.name ?: input.id
            names.add(portName)
            // The port is passed through because a controller usually exposes SEVERAL: a main
            // port, a DAW-protocol port (MCU/HUI, which encodes buttons as NOTES and would
            // otherwise play voices), a DIN-thru, and on Linux an ALSA loopback. Without
            // attribution, messages from all of them are indistinguishable.
            input.onmidimessage = { evt -> onMidiMessage(portName, evt) }
        }

        console.log("[MidiConnector] ${names.size} input(s) hooked")
        _state { it.copy(status = Status.Ready, devices = names) }
    }

    private fun onMidiMessage(port: String, evt: MidiMessageEvent) {
        val status = evt.byteAt(0)
        val kind = status and 0xF0
        val channel = (status and 0x0F) + 1

        // Raw log of everything that reaches us, so "the device sends nothing for this button" is
        // a checkable claim rather than an assumption. System realtime (>= 0xF8: clock at 24ppq,
        // active sensing) is dropped — it would bury everything else.
        if (status < 0xF8) {
            console.log(
                "[MidiConnector] <$port> status=0x${status.toString(16)} kind=0x${kind.toString(16)} " +
                        "ch=$channel d1=${evt.byteAt(1)} d2=${evt.byteAt(2)} len=${evt.data.length}"
            )
        }

        // A throw here would die silently inside the MIDI callback.
        try {
            when (kind) {
                // Note-on with velocity 0 is a note-off by convention.
                0x90 -> {
                    val note = evt.byteAt(1)
                    val velocity = evt.byteAt(2)
                    val key = NoteKey(channel = channel, note = note)

                    if (velocity > 0) {
                        announceNoteOn(key, velocity, evt.timeStamp)
                    } else {
                        announceNoteOff(key, evt.timeStamp)
                    }
                }

                0x80 -> announceNoteOff(NoteKey(channel = channel, note = evt.byteAt(1)), evt.timeStamp)

                0xB0 -> {
                    val cc = evt.byteAt(1)
                    if (cc == CC_ALL_SOUND_OFF || cc == CC_ALL_NOTES_OFF) {
                        releaseAll(Reason.PanicCc)
                    } else {
                        _events(
                            Event.ControlChange(
                                channel = channel,
                                cc = cc,
                                value = evt.byteAt(2),
                                tsMs = evt.timeStamp,
                            )
                        )
                    }
                }

                // Everything else surfaces raw so a device's controls can be discovered. System
                // realtime (>= 0xF8: clock, active sensing) is dropped — pure spam at 24 ppq.
                else -> if (status < 0xF8) {
                    _events(
                        Event.Other(
                            status = status,
                            kind = kind,
                            channel = channel,
                            data1 = evt.byteAt(1),
                            data2 = evt.byteAt(2),
                            tsMs = evt.timeStamp,
                        )
                    )
                }
            }
        } catch (e: Throwable) {
            console.error("[MidiConnector] message handling failed:", e)
        }
    }

    private fun announceNoteOn(key: NoteKey, velocity: Int, tsMs: Double) {
        // Retrigger of a still-held key: release the old voice first, or it would stay gated
        // until the held-gate horizon.
        _state().held[key]?.let { previous ->
            _events(Event.NoteOff(liveId = previous.liveId, key = key, tsMs = tsMs))
        }

        val liveId = nextLiveId()
        _state { it.copy(held = it.held + (key to HeldNote(key = key, velocity = velocity, liveId = liveId))) }
        _events(Event.NoteOn(liveId = liveId, key = key, velocity = velocity, tsMs = tsMs))
    }

    private fun announceNoteOff(key: NoteKey, tsMs: Double) {
        val held = _state().held[key] ?: return

        _state { it.copy(held = it.held - key) }
        _events(Event.NoteOff(liveId = held.liveId, key = key, tsMs = tsMs))
    }

    private fun releaseAll(reason: Reason) {
        val liveIds = _state().held.values.map { it.liveId }

        if (liveIds.isEmpty()) {
            return
        }

        _state { it.copy(held = emptyMap()) }
        _events(Event.AllNotesOff(liveIds = liveIds, reason = reason))
    }
}
