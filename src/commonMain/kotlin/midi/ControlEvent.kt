/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

/** Which input said it: "keylab-49-mk3", "midi:<port name>", "laptop", "screen". */
data class SourceId(val id: String) {
    companion object {
        val Laptop = SourceId("laptop")
    }
}

/**
 * What a control IS, not what it sends: the ids [MidiDevicePanel] draws ("pad-1", "knob-3", "fader-2", "play", "stop",
 * "record", "tap", "mod-wheel", "pitch-bend", "main-encoder"), and "sustain" and "panic". The same id on two devices is
 * the same role, so the KeyLab's Play and the laptop's Space share one route (KLANG-00402, section 2).
 */
data class ControlId(val id: String) {
    companion object {
        val Sustain = ControlId("sustain")
        val Panic = ControlId("panic")
        val PitchBend = ControlId("pitch-bend")
        val ModWheel = ControlId("mod-wheel")
        val MainEncoder = ControlId("main-encoder")

        /** The laptop's two instrument keys (Ü and +). */
        val InstrumentPrevious = ControlId("instrument-previous")
        val InstrumentNext = ControlId("instrument-next")

        fun pad(n: Int) = ControlId("pad-$n")
    }
}

/**
 * The intermediate format between the devices and what they do (KLANG-00402, section 2): every source (a MIDI device
 * through its profile, the laptop keys, the drawn controls) translates its own input into these, and knows nothing of
 * what they will do; the routing decides that. Plain data, so a source and the routing test on the JVM.
 */
sealed interface ControlEvent {
    val source: SourceId
    val tsMs: Double

    /** A key of a keybed went down. [velocity] is 0..1; [channel] keeps a multi-channel device's notes apart. */
    data class NoteDown(
        override val source: SourceId,
        val channel: Int,
        val note: Int,
        val velocity: Double,
        override val tsMs: Double,
    ) : ControlEvent

    /** A key of a keybed came up. */
    data class NoteUp(
        override val source: SourceId,
        val channel: Int,
        val note: Int,
        override val tsMs: Double,
    ) : ControlEvent

    /** A momentary control went down: a pad, a button, a function key. [velocity] is 0..1 (1 for a button). */
    data class Press(
        override val source: SourceId,
        val control: ControlId,
        val velocity: Double,
        override val tsMs: Double,
    ) : ControlEvent

    /** A momentary control came up. */
    data class Release(
        override val source: SourceId,
        val control: ControlId,
        override val tsMs: Double,
    ) : ControlEvent

    /** A control with a position moved: a knob, a fader, the mod wheel 0..1; the pitch bend -1..1. */
    data class Move(
        override val source: SourceId,
        val control: ControlId,
        val value: Double,
        override val tsMs: Double,
    ) : ControlEvent

    /** An endless control turned by [steps], clockwise positive: the KeyLab's main encoder. */
    data class Nudge(
        override val source: SourceId,
        val control: ControlId,
        val steps: Int,
        override val tsMs: Double,
    ) : ControlEvent

    /** Everything this source holds is up at once: its keys' up can no longer arrive, or a panic asked for it. */
    data class AllUp(
        override val source: SourceId,
        val reason: Reason,
        override val tsMs: Double,
    ) : ControlEvent

    enum class Reason {
        /** The MIDI panic messages (CC 120, CC 123): every source's notes go, not only the sender's. */
        Panic,

        /** The device was unplugged mid-chord. */
        Disconnected,

        /** The laptop keys lost the focus (the tool, the window, the tab): a key held then never sends its up. */
        FocusLost,
    }
}
