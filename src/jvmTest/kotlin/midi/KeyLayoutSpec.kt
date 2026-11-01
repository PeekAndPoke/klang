/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** The letters on the laptop's keys by layout (KLANG-00402, D9), and the drawing printing them. */
class KeyLayoutSpec : StringSpec({

    "the same key position prints its own letter in each layout" {
        KeyLayout.English.labelOf("KeyZ") shouldBe "Z"
        KeyLayout.German.labelOf("KeyZ") shouldBe "Y"
        KeyLayout.French.labelOf("KeyZ") shouldBe "W"

        KeyLayout.English.labelOf("Semicolon") shouldBe ";"
        KeyLayout.German.labelOf("Semicolon") shouldBe "Ö"
        KeyLayout.French.labelOf("Semicolon") shouldBe "M"

        KeyLayout.German.labelOf("BracketLeft") shouldBe "Ü"
        KeyLayout.French.labelOf("KeyQ") shouldBe "A"
    }

    "a key a layout does not list prints the letter its position names" {
        KeyLayout.German.labelOf("KeyQ") shouldBe "Q"
        KeyLayout.French.labelOf("Digit3") shouldBe "3"
        KeyLayout.German.labelOf("Comma") shouldBe ","
    }

    "every key the laptop plays has a letter in every layout, never its code" {
        val codes = LaptopKeys.NOTES.keys + LaptopKeys.PADS.keys + listOf("BracketLeft", "BracketRight")

        for (layout in KeyLayout.entries) {
            for (code in codes) {
                (layout.labelOf(code).length == 1) shouldBe true
            }
        }
    }

    "the drawing prints the letters it is given: the pads, and the instrument keys' buttons" {
        val panel = laptopKeyboardPanel(label = KeyLayout.German::labelOf)
        val pads = panel.controls.filterIsInstance<PanelControl.Pad>().associate { it.id to it.label }

        pads["pad-1"] shouldBe "1"
        pads["pad-5"] shouldBe "Q"
        panel.controls.filterIsInstance<PanelControl.Button>().first { it.id == ControlId.InstrumentPrevious.id }.label shouldBe
            "Ü  sound -"
    }
})
