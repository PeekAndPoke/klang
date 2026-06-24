/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * Portions derived from tonal.js — Copyright (c) 2015 danigb.
 * SPDX-License-Identifier: MIT
 * Full license: tones/LICENSE
 */

package io.peekandpoke.klang.tones.key

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class KeyChordTest : StringSpec({
    "C major chords" {
        val chords = Key.majorKeyChords("C")
        chords.find { it.name == "Em7" } shouldBe KeyChord(
            name = "Em7",
            roles = listOf("T", "ii/II")
        )
    }
})
