/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

/**
 * The laptop's keys as a source (KLANG-00402, section 3, D1): a translation from a key's position
 * (`KeyboardEvent.code`) to [ControlEvent]s, with no browser in it. By position, not by the printed letter, so the
 * shape is the same on QWERTZ, QWERTY and AZERTY (D9): the German Y X C V is the English Z X C V.
 *
 * ```
 *   Number row   [1][2][3][4]  pads 1-4
 *   Top row      [Q][W][E][R]  pads 5-8                  [Ü][+]  instrument -/+
 *   Home row        [S][D]   [G][H][J]   [L][Ö]          black keys: C# D#   F# G# A#   C# D#
 *   Bottom row   [Y][X][C][V][B][N][M][,][.][-]          white keys: C D E F G A B C D E
 *   Arrows       [<-][->] octave -/+    [v][^] velocity -/+
 *   Others       [Space] play/stop   [Tab] sustain while held   [Esc] panic
 * ```
 *
 * One octave and a third, C to E; the keys between (A, F, K, Ä, and the one left of Y on a German board) are ours but
 * play nothing, so a slipped finger is silent and opens nothing (Firefox's quick find on `'`). The
 * octave and the velocity are the laptop's own, as the KeyLab's Oct buttons stay in the KeyLab. A key's auto-repeat
 * plays nothing again, and a key held with Ctrl, Alt or Meta is the browser's; so is Shift+Tab, the keyboard's way
 * out of the keys (Tab is the sustain, Esc the panic).
 */
class LaptopKeys {

    /** What a key did: the [events], and whether the key was this source's, so the browser's default goes. */
    data class Result(val events: List<ControlEvent>, val handled: Boolean) {
        companion object {
            val NotOurs = Result(events = emptyList(), handled = false)
        }
    }

    companion object {
        val source = SourceId.Laptop

        /** The keybed, by position: semitones above the octave's C. */
        val NOTES: Map<String, Int> = mapOf(
            "KeyZ" to 0, "KeyS" to 1, "KeyX" to 2, "KeyD" to 3, "KeyC" to 4, "KeyV" to 5, "KeyG" to 6, "KeyB" to 7,
            "KeyH" to 8, "KeyN" to 9, "KeyJ" to 10, "KeyM" to 11, "Comma" to 12, "KeyL" to 13, "Period" to 14,
            "Semicolon" to 15, "Slash" to 16,
        )

        /** The pads, by position: 1 to 4 on the number row, 5 to 8 under them, as the KeyLab's two rows of four. */
        val PADS: Map<String, Int> = mapOf(
            "Digit1" to 1, "Digit2" to 2, "Digit3" to 3, "Digit4" to 4,
            "KeyQ" to 5, "KeyW" to 6, "KeyE" to 7, "KeyR" to 8,
        )

        /** The function keys: a control each, which the routing points somewhere. */
        val FUNCTIONS: Map<String, ControlId> = mapOf(
            "BracketLeft" to ControlId.InstrumentPrevious,
            "BracketRight" to ControlId.InstrumentNext,
            "Space" to ControlId("play"),
            "Tab" to ControlId.Sustain,
            "Escape" to ControlId.Panic,
        )

        /** The keys in the keybed's gaps and beside it: ours, so a finger slipping onto one does nothing at all. */
        private val GAPS: Set<String> = setOf("KeyA", "KeyF", "KeyK", "Quote", "IntlBackslash")

        /** The keys that change the octave and the velocity. */
        private val ARROWS: Set<String> = setOf("ArrowLeft", "ArrowRight", "ArrowDown", "ArrowUp")

        /** Meta's up: macOS sends no up for a key let go while Meta is held, so one let go then would hang. */
        private val META: Set<String> = setOf("MetaLeft", "MetaRight")

        /** The C the keybed starts on at octave 0: C4. */
        const val BASE_NOTE = 60

        /** How far the octave goes each way: C1 to C7. */
        const val OCTAVE_RANGE = 3

        /** The velocities the arrows step through, as MIDI numbers; [DEFAULT_VELOCITY] to start. */
        val VELOCITIES: List<Int> = listOf(32, 48, 64, 80, 96, 112, 127)

        const val DEFAULT_VELOCITY = 96

        /** The note [code] plays at [octave]; null for a key that is not on the keybed. */
        fun noteAt(code: String, octave: Int): Int? = NOTES[code]?.let { BASE_NOTE + octave * 12 + it }

        /** The keybed's letters at [octave] by the note each key plays, [label] giving a position's letter: the drawing's. */
        fun keyLabels(octave: Int, label: (code: String) -> String): Map<Int, String> =
            NOTES.keys.associate { code -> noteAt(code = code, octave = octave)!! to label(code) }
    }

    /** The octave, -[OCTAVE_RANGE] to +[OCTAVE_RANGE] around [BASE_NOTE]. */
    var octave: Int = 0
        private set

    /** The velocity the keys play at, a MIDI number from [VELOCITIES]. */
    var velocity: Int = DEFAULT_VELOCITY
        private set

    /** The keys down now and the note each started: its up stops that note, whatever the octave became meanwhile. */
    private val down = mutableMapOf<String, Int>()

    /** The pads and functions down now. */
    private val pressed = mutableMapOf<String, ControlId>()

    /** The note [code] plays at the octave now; null for a key that is not on the keybed. */
    fun noteOf(code: String): Int? = noteAt(code = code, octave = octave)

    /**
     * A key went down. [repeat] is the browser's auto-repeat; [modified] says Ctrl, Alt or Meta is held; [shift] says
     * Shift is, which leaves only Shift+Tab to the browser (the focus moves back, out of the keys).
     */
    fun keyDown(code: String, repeat: Boolean, modified: Boolean, tsMs: Double, shift: Boolean = false): Result {
        if (modified || !isOurs(code) || (shift && code == "Tab")) {
            return Result.NotOurs
        }

        // A held key's repeat: still ours (Space would scroll, Tab move the focus), but it plays nothing again
        if (repeat || code in down || code in pressed) {
            return Result(events = emptyList(), handled = true)
        }

        noteOf(code)?.let { note ->
            down[code] = note

            return played(ControlEvent.NoteDown(source = source, channel = 1, note = note, velocity = velocity / 127.0, tsMs = tsMs))
        }

        PADS[code]?.let { pad ->
            val control = ControlId.pad(pad)

            pressed[code] = control

            return played(ControlEvent.Press(source = source, control = control, velocity = velocity / 127.0, tsMs = tsMs))
        }

        FUNCTIONS[code]?.let { control ->
            pressed[code] = control

            return played(ControlEvent.Press(source = source, control = control, velocity = 1.0, tsMs = tsMs))
        }

        when (code) {
            "ArrowLeft" -> octave = (octave - 1).coerceAtLeast(-OCTAVE_RANGE)
            "ArrowRight" -> octave = (octave + 1).coerceAtMost(OCTAVE_RANGE)
            "ArrowDown" -> velocity = VELOCITIES.lastOrNull { it < velocity } ?: velocity
            "ArrowUp" -> velocity = VELOCITIES.firstOrNull { it > velocity } ?: velocity
        }

        return Result(events = emptyList(), handled = true)
    }

    /**
     * A key came up: the note it started stops, the pad or function it pressed is released. Meta's up lets everything
     * go, as a focus lost does, but stays the browser's: on macOS a key let go while Meta was held sends no up.
     */
    fun keyUp(code: String, tsMs: Double): Result {
        if (code in META) {
            return Result(events = focusLost(tsMs = tsMs), handled = false)
        }

        down.remove(code)?.let { note ->
            return played(ControlEvent.NoteUp(source = source, channel = 1, note = note, tsMs = tsMs))
        }

        pressed.remove(code)?.let { control ->
            return played(ControlEvent.Release(source = source, control = control, tsMs = tsMs))
        }

        return if (isOurs(code)) Result(events = emptyList(), handled = true) else Result.NotOurs
    }

    /**
     * The keys lost the focus (the tool, the window, the tab): a key held then never sends its up, so everything this
     * source holds lets go at once; nothing when nothing is held.
     */
    fun focusLost(tsMs: Double): List<ControlEvent> {
        if (down.isEmpty() && pressed.isEmpty()) {
            return emptyList()
        }

        down.clear()
        pressed.clear()

        return listOf(ControlEvent.AllUp(source = source, reason = ControlEvent.Reason.FocusLost, tsMs = tsMs))
    }

    private fun isOurs(code: String): Boolean =
        code in NOTES || code in PADS || code in FUNCTIONS || code in ARROWS || code in GAPS

    private fun played(event: ControlEvent) = Result(events = listOf(event), handled = true)
}
