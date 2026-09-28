/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.sprudel.SprudelPattern

class LangVowelSpec : StringSpec({

    // A bare call reinterprets the pattern's own values as the HEAD, which is the WET since step
    // 3d(iii). It writes the wet alone: the vowel is the stage's name knob, and a wet never invents it.
    "reinterpret voice data as wet | seq(\"0.2 0.5 0.8\").vowel()" {
        val p = seq("0.2 0.5 0.8").vowel()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events.map { it.data.vowelMix } shouldBe listOf(0.2, 0.5, 0.8)
        events.map { it.data.vowel } shouldBe listOf(null, null, null)
    }

    "vowel() handles case insensitively" {
        val p = note("c3").vowel(vowel = "A")

        val events = p.queryArc(0.0, 1.0)

        events[0].data.vowel shouldBe "a"
    }

    // The orbit's vowel stage reads its SLOTS (`vowel.vowel`, `vowel.wet`, `vowel.floor` in `katalystParams`); the
    // `FilterDef.Formant` the voice once carried in `filters` left the wire in phase 3 step 9. These rows read the
    // slots the door writes and resolve the vowel index through the table the orbit uses.
    fun slots(pattern: SprudelPattern): Map<String, Double> =
        pattern.queryArc(0.0, 1.0)[0].data.toVoiceData().katalystParams ?: emptyMap()

    fun bands(slots: Map<String, Double>) = VowelBands.bandsAt(slots["vowel.vowel"] ?: Double.NaN)

    "vowel() works with all vowels (a, e, i, o, u)" {
        listOf("a", "e", "i", "o", "u").forEach { v ->
            val p = note("c3").vowel(vowel = v)

            p.queryArc(0.0, 1.0)[0].data.vowel shouldBe v
            // Every one resolves to a formant bank.
            bands(slots(p)).shouldNotBeNull().shouldNotBeEmpty()
        }
    }

    "vowel() with unknown vowel is ignored: its index is none, and no formant bank resolves" {
        val slots = slots(note("c3").vowel(vowel = "x"))

        slots["vowel.vowel"] shouldBe 0.0
        bands(slots) shouldBe null
    }

    "vowel(vowel = \"none\") is the off switch: it clears a previously set vowel" {
        // "none" is the explicit reset: no formant bank, even after an earlier vowel(vowel = "a").
        val slots = slots(note("c3").vowel(vowel = "a").vowel(vowel = "none"))

        slots["vowel.vowel"] shouldBe 0.0
        bands(slots) shouldBe null
    }
})
