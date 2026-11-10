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
 * page and the MIDI tool. The layout chosen is kept in the browser, so the spec puts it back.
 */
class MidiSettingsSpec : StringSpec({

    "a key's letter: the layout chosen first, else the one learned, else the English one; a key learns its first letter only" {
        val settings = MidiSettings()
        val kept = settings.layout()

        try {
            settings.chooseLayout(null)

            withClue("nothing learned, no layout chosen: the English letter") {
                settings.letters()("KeyZ") shouldBe KeyLayout.English.labelOf("KeyZ")
            }

            settings.learn(code = "KeyZ", letter = "Ä")
            settings.learn(code = "KeyZ", letter = "Ö")

            withClue("learned: the first letter it pressed, not a later one") { settings.letters()("KeyZ") shouldBe "Ä" }

            settings.chooseLayout(KeyLayout.French)

            withClue("a layout chosen by hand wins over what was learned") {
                settings.letters()("KeyZ") shouldBe KeyLayout.French.labelOf("KeyZ")
            }
        } finally {
            settings.chooseLayout(kept)
        }
    }
})
