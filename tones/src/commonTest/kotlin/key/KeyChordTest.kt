/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
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
