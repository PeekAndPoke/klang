/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

/**
 * The held notes of every source in one place (KLANG-00402, section 5), out of `MidiConnector`: which voice each key
 * started, a retrigger releasing the old voice first, one source's [ControlEvent.AllUp] releasing that source's notes
 * only, and sustain. The same note on the laptop and on the KeyLab is two voices, as two channels of one device are.
 *
 * It knows no audio: it answers each event with what to start and what to stop ([Out]), and the caller maps that onto
 * the playback. Sustain is per source, as a pedal sustains the keyboard it is plugged into.
 *
 * @param nextLiveId mints the id of a new voice; wire it to the playback's own.
 */
class NoteVoicing(private val nextLiveId: () -> Int) {

    /** A key: the same note on two sources, or on two channels of one, is two keys. */
    data class Key(val source: SourceId, val channel: Int, val note: Int)

    /** A voice sounding: its [key], the [liveId] it started, and whether its key is [down] (else sustain holds it). */
    data class Held(val key: Key, val velocity: Double, val liveId: Int, val down: Boolean)

    /** What the playback does. */
    sealed interface Out {
        data class Start(val key: Key, val velocity: Double, val liveId: Int) : Out

        data class Stop(val liveId: Int) : Out
    }

    private val heldNotes = mutableMapOf<Key, Held>()

    private val sustained = mutableSetOf<SourceId>()

    /** The voices sounding now, by key. */
    val held: Map<Key, Held> get() = heldNotes.toMap()

    /** A key went down: a voice starts; a voice still sounding on the same key (held or sustained) stops first. */
    fun down(event: ControlEvent.NoteDown): List<Out> {
        val key = Key(source = event.source, channel = event.channel, note = event.note)
        val previous = heldNotes[key]
        val liveId = nextLiveId()

        heldNotes[key] = Held(key = key, velocity = event.velocity, liveId = liveId, down = true)

        return listOfNotNull(
            previous?.let { Out.Stop(liveId = it.liveId) },
            Out.Start(key = key, velocity = event.velocity, liveId = liveId),
        )
    }

    /** A key came up: its voice stops, or sounds on while the source's sustain is down. A key not held does nothing. */
    fun up(event: ControlEvent.NoteUp): List<Out> {
        val key = Key(source = event.source, channel = event.channel, note = event.note)
        val held = heldNotes[key] ?: return emptyList()

        if (event.source in sustained) {
            heldNotes[key] = held.copy(down = false)

            return emptyList()
        }

        heldNotes.remove(key)

        return listOf(Out.Stop(liveId = held.liveId))
    }

    /** [source]'s sustain went down or up; up stops the voices it held whose keys are up. */
    fun sustain(source: SourceId, on: Boolean): List<Out> {
        if (on) {
            sustained.add(source)

            return emptyList()
        }

        sustained.remove(source)

        return release { it.key.source == source && !it.down }
    }

    /** Everything [event]'s source holds stops at once, its sustain too; the other sources play on. */
    fun allUp(event: ControlEvent.AllUp): List<Out> {
        sustained.remove(event.source)

        return release { it.key.source == event.source }
    }

    /** Every voice of every source stops, every sustain goes up: the panic. */
    fun panic(): List<Out> {
        sustained.clear()

        return release { true }
    }

    private fun release(which: (Held) -> Boolean): List<Out> {
        val gone = heldNotes.values.filter(which)

        gone.forEach { heldNotes.remove(it.key) }

        return gone.map { Out.Stop(liveId = it.liveId) }
    }
}
