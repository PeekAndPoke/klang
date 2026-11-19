/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangScrambleSpec : StringSpec({

    "scramble(4) picks 4 slices randomly" {
        val p = n("0 1 2 3").scramble(4).seed(789)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        val values = events.mapNotNull { it.data.soundIndex }

        // All values should be from the original set 0..3
        // Note: scramble allows repetition (replacement), unlike shuffle
        values.all { it in 0..3 } shouldBe true
    }

    "scramble works as string extension" {
        val p = "0 1 2 3".scramble(4).seed(999)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        val values = events.mapNotNull { it.data.value?.asInt }
        values.all { it in 0..3 } shouldBe true
    }

    // Until 2026-11-19 bite (which scramble plays through) sped a patterned n up: the n = 4 cycle of "<2 4>"
    // played two slices instead of four.
    "scramble with a patterned n plays the slice count of each cycle" {
        val p = n("0 1 2 3").scramble("<2 4>").seed(789)

        fun onsetBegins(cycle: Int) =
            p.queryArc(cycle.toDouble(), cycle + 1.0).filter { it.isOnset }.map { it.part.begin.toDouble() - cycle }.sorted()

        onsetBegins(0) shouldBe listOf(0.0, 0.25, 0.5, 0.75)
        onsetBegins(1) shouldBe listOf(0.0, 0.25, 0.5, 0.75)
    }
})
