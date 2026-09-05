/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

/**
 * A drawable description of a MIDI controller's front panel.
 *
 * Pure data on purpose: adding a device means adding a value, not code. The renderer
 * (`MidiPanel`) knows how to draw each [PanelControl] shape and nothing about any specific
 * hardware; the device profile knows the layout and nothing about drawing.
 *
 * Coordinates are in an arbitrary panel space (the renderer scales via an SVG viewBox), taken
 * from a straight-on photo of the unit.
 */
data class MidiDevicePanel(
    val name: String,
    val width: Int,
    val height: Int,
    /** Panel body colour. */
    val body: String = "#2b2b2f",
    val keyBed: KeyBed? = null,
    val controls: List<PanelControl> = emptyList(),
    val bindings: MidiBindings = MidiBindings(),
)

/**
 * What the hardware sends, mapped to the panel's control ids.
 *
 * Data rather than code, and separate from the drawing on purpose: the same panel can be re-bound
 * when a device is reconfigured (a KeyLab's encoders send whatever its MIDI Control Center preset
 * holds), and a control that has no binding yet still draws — it simply never lights up.
 */
data class MidiBindings(
    /** CC number -> control id. */
    val cc: Map<Int, String> = emptyMap(),
    /** Control that the 14-bit pitch-bend message drives, if any. */
    val pitchBend: String? = null,
    /** (channel, note) -> control id, for pads that send notes rather than CCs. */
    val notes: Map<Pair<Int, Int>, String> = emptyMap(),
)

/**
 * The keyboard itself. [fromNote]/[toNote] are MIDI note numbers (60 = C4), so highlighting a
 * held note needs no translation.
 */
data class KeyBed(
    val fromNote: Int,
    val toNote: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/**
 * One drawable control.
 *
 * [id] is the stable handle a device adapter binds MIDI to ("knob-1", "fader-3", "pad-5") and the
 * renderer highlights by. It is deliberately NOT a CC number: what a knob sends is configurable on
 * most controllers (on a KeyLab it lives in the MIDI Control Center preset), so the panel names
 * the control and the adapter learns the number.
 */
sealed interface PanelControl {
    val id: String
    val x: Int
    val y: Int

    /** Rotary encoder / knob. */
    data class Knob(
        override val id: String,
        override val x: Int,
        override val y: Int,
        val radius: Int,
        val label: String? = null,
        /** Silver clickable main encoder vs the small dark ones. */
        val accent: Boolean = false,
        /**
         * Endless encoder: it reports a DIRECTION around a centre of 64, not a position — above 64
         * is clockwise, below is counter-clockwise, and 64 itself is no motion.
         *
         * The distinction is not cosmetic. An absolute knob's byte says where it is; a relative
         * one's says only which way it just turned. So the renderer draws no pointer for these and
         * shows the raw number instead: there is no angle that would be true.
         */
        val relative: Boolean = false,
        /**
         * Id of the companion "clicked" binding, for encoders that are also pushbuttons.
         *
         * A separate id because the two send separate CCs and mean different things — folding the
         * click into [id] would have a press overwrite the turn value in the same slot.
         */
        val pressId: String? = null,
    ) : PanelControl

    /** Vertical fader; [x],[y] is the top of the travel. */
    data class Fader(
        override val id: String,
        override val x: Int,
        override val y: Int,
        val travel: Int,
        val label: String? = null,
    ) : PanelControl

    /** Velocity/pressure pad. */
    data class Pad(
        override val id: String,
        override val x: Int,
        override val y: Int,
        val size: Int,
        val label: String? = null,
    ) : PanelControl

    /** Any labelled switch. [tint] colours transport buttons the way the hardware does. */
    data class Button(
        override val id: String,
        override val x: Int,
        override val y: Int,
        val width: Int,
        val height: Int,
        val label: String? = null,
        val tint: String? = null,
    ) : PanelControl

    /** Pitch-bend / modulation wheel. */
    data class Wheel(
        override val id: String,
        override val x: Int,
        override val y: Int,
        val width: Int,
        val height: Int,
        val label: String? = null,
    ) : PanelControl

    /** The LCD module — inert, drawn for recognisability. */
    data class Display(
        override val id: String,
        override val x: Int,
        override val y: Int,
        val width: Int,
        val height: Int,
        val text: String? = null,
    ) : PanelControl
}

/**
 * Arturia KeyLab Essential 49 mk3.
 *
 * Layout traced from a straight-on panel photo; control names from the official manual
 * (§3.2 Front Panel). What the manual does NOT provide, and what therefore is not encoded here:
 * the CC numbers of the 9 encoders and 9 faders. In ARTURIA mode they drive Analog Lab macros and
 * in USER mode they are whatever the MIDI Control Center preset says, so they must be learned from
 * the device — see the playground's MIDI monitor.
 *
 * What the manual DOES pin down (used by the adapter, not by this drawing): pads send notes on
 * channel 10 — bank A 40,41,42,43,36,37,38,39 and bank B 48,49,50,51,44,45,46,47 (the manual
 * misprints 49 as "48") — the mod wheel is CC 1, and the pads send polyphonic aftertouch.
 */
/** Pad notes per bank, in pad order 1-8. Both measured on the hardware. */
val PAD_NOTES_BANK_A: List<Int> = listOf(40, 41, 42, 43, 36, 37, 38, 39)
val PAD_NOTES_BANK_B: List<Int> = listOf(48, 49, 50, 51, 44, 45, 46, 47)

/**
 * Which pad bank a note came from, or null if it is not a pad note.
 *
 * The Bank button only announces that it was pressed (CC 118), never which bank became active, so
 * asking the notes is the only reliable answer — and unlike tracking button presses it cannot
 * drift out of sync with the hardware.
 */
fun padBankOf(note: Int): String? = when (note) {
    in PAD_NOTES_BANK_A -> "A"
    in PAD_NOTES_BANK_B -> "B"
    else -> null
}

val KeyLabEssential49Mk3: MidiDevicePanel = MidiDevicePanel(
    name = "KeyLab Essential 49 mk3",
    width = 1600,
    height = 500,
    keyBed = KeyBed(fromNote = 36, toNote = 84, x = 165, y = 200, width = 1415, height = 290),
    controls = buildList {
        // ── Left button block ────────────────────────────────────────────────
        add(PanelControl.Button("midi-ch", 35, 30, 46, 20, "MIDI Ch"))
        add(PanelControl.Button("bank", 88, 30, 46, 20, "Bank"))
        add(PanelControl.Button("transp-down", 35, 112, 46, 20, "Transp -"))
        add(PanelControl.Button("transp-up", 88, 112, 46, 20, "Transp +"))
        add(PanelControl.Button("oct-down", 35, 144, 46, 20, "Oct -"))
        add(PanelControl.Button("oct-up", 88, 144, 46, 20, "Oct +"))

        // ── Pads: 4 columns x 2 rows, 1-4 on top ─────────────────────────────
        for (row in 0..1) {
            for (col in 0..3) {
                val n = row * 4 + col + 1
                add(PanelControl.Pad("pad-$n", 207 + col * 68, 32 + row * 72, 62, "$n"))
            }
        }

        // ── Save / Quant / Undo / Redo ───────────────────────────────────────
        listOf("Save", "Quant", "Undo", "Redo").forEachIndexed { i, name ->
            add(PanelControl.Button("cmd-${name.lowercase()}", 508 + i * 48, 30, 42, 20, name))
        }

        // ── Transport ────────────────────────────────────────────────────────
        add(PanelControl.Button("refresh", 508, 100, 40, 24, "↻"))
        add(PanelControl.Button("rewind", 556, 100, 40, 24, "<<"))
        add(PanelControl.Button("forward", 604, 100, 40, 24, ">>"))
        add(PanelControl.Button("metronome", 652, 100, 40, 24, "metro"))
        add(PanelControl.Button("stop", 508, 136, 40, 24, "stop"))
        add(PanelControl.Button("play", 556, 136, 40, 24, "play", tint = "#3fb950"))
        add(PanelControl.Button("record", 604, 136, 40, 24, "rec", tint = "#e0508a"))
        add(PanelControl.Button("tap", 652, 136, 40, 24, "TAP"))

        // ── Display module ───────────────────────────────────────────────────
        add(PanelControl.Display("display", 728, 36, 132, 70, "Arturia"))
        for (i in 0..3) {
            add(PanelControl.Button("ctx-${i + 1}", 736 + i * 32, 114, 26, 14, null))
        }
        add(
            PanelControl.Knob(
                "main-encoder", 795, 158, 26, "",
                accent = true, relative = true, pressId = "main-encoder-press",
            )
        )

        // ── Right button block ───────────────────────────────────────────────
        // Only Part reaches the wire (CC 119). Prog, Hold, Chord, Scale and Arp send NOTHING on
        // any of the device's five ports — VERIFIED 2026-08-29 against the raw message log, not
        // assumed. They change internal keyboard state, like Transpose and Octave, so they are
        // drawn but can never light up.
        add(PanelControl.Button("prog", 918, 30, 44, 20, "Prog"))
        add(PanelControl.Button("part", 966, 30, 44, 20, "Part"))
        add(PanelControl.Button("hold", 918, 112, 44, 20, "Hold"))
        add(PanelControl.Button("chord", 966, 112, 44, 20, "Chord"))
        add(PanelControl.Button("scale", 918, 144, 44, 20, "Scale"))
        add(PanelControl.Button("arp", 966, 144, 44, 20, "Arp"))

        // ── 9 encoders over 9 faders ─────────────────────────────────────────
        for (i in 0..8) {
            val cx = 1085 + i * 53
            add(PanelControl.Knob("knob-${i + 1}", cx, 45, 17))
            add(PanelControl.Fader("fader-${i + 1}", cx, 95, 85, "${i + 1}"))
        }

        // ── Wheels, left of the keybed ───────────────────────────────────────
        add(PanelControl.Wheel("pitch-bend", 42, 290, 26, 110, "pitch"))
        add(PanelControl.Wheel("mod-wheel", 82, 290, 26, 110, "mod"))
    },
    bindings = MidiBindings(
        cc = buildMap {
            // Knobs 96-104 and faders 105-113, MEASURED on a unit running FACTORY settings
            // (2026-08-29). Not published anywhere: the manual gives no defaults for the knobs and
            // faders because they follow whatever the MIDI Control Center preset holds. Factory
            // state makes these a good default for any KeyLab Essential mk3 — but a device someone
            // has reconfigured will send something else, which is exactly why the binding is data
            // and the playground has a monitor to re-learn it.
            for (i in 0..8) {
                put(96 + i, "knob-${i + 1}")
                put(105 + i, "fader-${i + 1}")
            }
            // DAW Command Center and transport, MEASURED (2026-08-29). Three contiguous blocks of
            // four, which is why they are written out in device order rather than as a map.
            put(40, "cmd-save")
            put(41, "cmd-quant")
            put(42, "cmd-undo")
            put(43, "cmd-redo")

            put(24, "refresh")
            put(25, "rewind")
            put(26, "forward")
            put(27, "metronome")

            put(20, "stop")
            put(21, "play")
            put(22, "record")
            put(23, "tap")

            // The four soft buttons under the display, left to right.
            put(44, "ctx-1")
            put(45, "ctx-2")
            put(46, "ctx-3")
            put(47, "ctx-4")

            // The big clickable encoder — RELATIVE, MEASURED (2026-08-29): it never sends a
            // position, only a DIRECTION around a centre of 64 (> 64 clockwise, < 64 counter-
            // clockwise), and in practice only 62..66. Its click is a second CC entirely, so it
            // gets its own id (see [PanelControl.Knob.pressId]).
            put(116, "main-encoder")
            put(117, "main-encoder-press")

            // The only member of the right-hand block that transmits; see the note there.
            put(119, "part")

            // The one control the manual does pin down.
            put(1, "mod-wheel")
            // Measured. The Bank button reports that it was PRESSED, not which bank is now live —
            // so the active bank is inferred from the pad notes instead (see [padBankOf]).
            put(118, "bank")
        },
        pitchBend = "pitch-bend",
        notes = buildMap {
            // ⚠️ Channel 11, NOT the 10 the manual states — MEASURED (2026-08-29). MIDI channels
            // are 0-15 on the wire and 1-16 in every UI; `MidiConnector` reports the 1-based form,
            // so the manual's "channel 10" is the RAW wire value and arrives here as 11. Do not
            // "correct" this back to 10 against the manual: the hardware is the authority, and the
            // note numbers below matched the manual exactly, which is what proves the offset is
            // numbering and not a different mapping.
            val padChannel = 11

            // Both banks confirmed on the unit (2026-08-29). Bank B also settled the manual's
            // misprint: it prints pad 2 as "48 (C#2)" but the hardware sends 49. (Its octave names
            // also run one lower than ours — Arturia numbers 60 as C3, we use C4.)
            PAD_NOTES_BANK_A.forEachIndexed { i, note -> put(padChannel to note, "pad-${i + 1}") }
            PAD_NOTES_BANK_B.forEachIndexed { i, note -> put(padChannel to note, "pad-${i + 1}") }
        },
    ),
)
