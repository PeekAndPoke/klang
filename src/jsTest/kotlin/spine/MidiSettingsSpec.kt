/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.midi.KeyLayout

/**
 * The MIDI settings' letters (KLANG-00402, D9): what a drawing of the laptop keys prints on a key, shared by the MIDI
 * page and the MIDI tool. The app's one settings, so the spec puts the layout back.
 */
class MidiSettingsSpec : StringSpec({

    "a key's letter: the layout chosen first, else the one learned, else the English one; a key learns its first letter only" {
        val kept = MidiSettings.layout()

        try {
            MidiSettings.chooseLayout(null)

            withClue("nothing learned, no layout chosen: the English letter") {
                MidiSettings.letters()("KeyZ") shouldBe KeyLayout.English.labelOf("KeyZ")
            }

            MidiSettings.learn(code = "KeyZ", letter = "Ä")
            MidiSettings.learn(code = "KeyZ", letter = "Ö")

            withClue("learned: the first letter it pressed, not a later one") { MidiSettings.letters()("KeyZ") shouldBe "Ä" }

            MidiSettings.chooseLayout(KeyLayout.French)

            withClue("a layout chosen by hand wins over what was learned") {
                MidiSettings.letters()("KeyZ") shouldBe KeyLayout.French.labelOf("KeyZ")
            }
        } finally {
            MidiSettings.chooseLayout(kept)
        }
    }
})
