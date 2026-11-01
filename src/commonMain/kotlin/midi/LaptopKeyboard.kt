/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

/**
 * The letters printed on the laptop's keys, by layout (KLANG-00402, D9). What plays never depends on them: the keys
 * are read by position ([LaptopKeys]); only the drawing's letters do. A layout lists its punctuation and the letters
 * that move; a letter key it does not list prints the letter its position names.
 */
enum class KeyLayout(val title: String, private val own: Map<String, String>) {
    English(
        title = "English (QWERTY)",
        own = mapOf(
            "Comma" to ",", "Period" to ".", "Slash" to "/", "Semicolon" to ";", "BracketLeft" to "[", "BracketRight" to "]",
        ),
    ),
    German(
        title = "German (QWERTZ)",
        own = mapOf(
            "KeyZ" to "Y", "Comma" to ",", "Period" to ".", "Slash" to "-", "Semicolon" to "Ö", "BracketLeft" to "Ü",
            "BracketRight" to "+",
        ),
    ),
    French(
        title = "French (AZERTY)",
        own = mapOf(
            "KeyZ" to "W", "KeyQ" to "A", "KeyW" to "Z", "KeyM" to ",", "Comma" to ";", "Period" to ":", "Slash" to "!",
            "Semicolon" to "M", "BracketLeft" to "^", "BracketRight" to "$",
        ),
    );

    /** The letter on the key at [code]: this layout's own, else the key's name ("KeyQ" is Q, "Digit1" is 1). */
    fun labelOf(code: String): String = own[code] ?: plainLabel(code)

    companion object {
        /** The letter a key's position names on every layout alike; the code itself for one it does not name. */
        private fun plainLabel(code: String): String = when {
            code.startsWith("Key") -> code.removePrefix("Key")
            code.startsWith("Digit") -> code.removePrefix("Digit")
            else -> English.own[code] ?: code
        }
    }
}

/**
 * The laptop's keys drawn as a panel, like the KeyLab's (KLANG-00402, section 6): the pads in two rows of four, the
 * function keys, and the keybed from C to E, each with the letter [label] gives for its position. The keybed is drawn
 * at C4; the caller shifts it by the laptop's octave (`MidiPanel`'s note offset).
 */
fun laptopKeyboardPanel(label: (code: String) -> String): MidiDevicePanel {
    val padCodes = LaptopKeys.PADS.entries.sortedBy { it.value }.map { it.key }

    return MidiDevicePanel(
        name = "Laptop keys",
        width = 720,
        height = 300,
        keyBed = KeyBed(
            fromNote = LaptopKeys.BASE_NOTE,
            toNote = LaptopKeys.BASE_NOTE + LaptopKeys.NOTES.values.max(),
            x = 20,
            y = 150,
            width = 680,
            height = 135,
        ),
        controls = buildList {
            padCodes.forEachIndexed { i, code ->
                add(PanelControl.Pad("pad-${i + 1}", 20 + (i % 4) * 62, 16 + (i / 4) * 62, 56, label(code)))
            }

            fun button(id: String, x: Int, y: Int, text: String) = add(PanelControl.Button(id, x, y, 125, 30, text))

            button(ControlId.InstrumentPrevious.id, 290, 18, "${label("BracketLeft")}  sound -")
            button(ControlId.InstrumentNext.id, 425, 18, "${label("BracketRight")}  sound +")
            button("play", 290, 58, "Space  play")
            button(ControlId.Sustain.id, 425, 58, "Tab  sustain")
            button(ControlId.Panic.id, 560, 58, "Esc  panic")
            button("octave", 290, 98, "\u2190 \u2192  octave")
            button("velocity", 425, 98, "\u2193 \u2191  velocity")
        },
        nameX = 562,
        nameY = 38,
    )
}
