/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.tones.progression

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ProgressionExamplesTest : StringSpec({
    "Progression.fromRomanNumerals" {
        Progression.fromRomanNumerals("C", listOf("IMaj7", "IIm7", "V7")) shouldBe
                listOf("CMaj7", "Dm7", "G7")
    }

    "Progression.toRomanNumerals" {
        Progression.toRomanNumerals("C", listOf("Cmaj7", "Dm7", "G7")) shouldBe
                listOf("Imaj7", "IIm7", "V7")
    }
})
