/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * Portions derived from tonal.js — Copyright (c) 2015 danigb.
 * SPDX-License-Identifier: MIT
 * Full license: tones/LICENSE
 */

package io.peekandpoke.klang.tones.roman

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.interval.Interval

class RomanNumeralExamplesTest : StringSpec({
    "RomanNumeral.get" {
        val r = RomanNumeral.get("bVIIMaj7")
        r.empty shouldBe false
        r.name shouldBe "bVIIMaj7"
        r.roman shouldBe "VII"
        r.acc shouldBe "b"
        r.chordType shouldBe "Maj7"
        r.alt shouldBe -1
        r.step shouldBe 6
        r.major shouldBe true
        r.oct shouldBe 0
    }

    "RomanNumeral from interval" {
        RomanNumeral.get(Interval.get("3m")).name shouldBe "bIII"
    }
})
