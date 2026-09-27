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
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangVowelSpec : StringSpec({

    "vowel dsl interface" {
        val pat = "c3"
        val vowelVal = "a"

        // Positional: wet FIRST, then the vowel (step 3d(iii), 2026-09-24).
        dslInterfaceTests(
            "pattern.vowel(w, v)" to note(pat).vowel(0.7, vowelVal),
            "script pattern.vowel(w, v)" to SprudelPattern.compile("""note("$pat").vowel(0.7, "$vowelVal")"""),
            "string.vowel(w, v)" to pat.vowel(0.7, vowelVal),
            "script string.vowel(w, v)" to SprudelPattern.compile(""""$pat".vowel(0.7, "$vowelVal")"""),
            "vowel(w, v)" to note(pat).apply(vowel(0.7, vowelVal)),
            "script vowel(w, v)" to SprudelPattern.compile("""note("$pat").apply(vowel(0.7, "$vowelVal"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.vowel shouldBe "a"
            events[0].data.vowelMix shouldBe 0.7
        }
    }

    // A bare call reinterprets the pattern's own values as the HEAD, which is the WET since step
    // 3d(iii). It writes the wet alone: the vowel is the stage's name knob, and a wet never invents it.
    "reinterpret voice data as wet | seq(\"0.2 0.5 0.8\").vowel()" {
        val p = seq("0.2 0.5 0.8").vowel()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events.map { it.data.vowelMix } shouldBe listOf(0.2, 0.5, 0.8)
        events.map { it.data.vowel } shouldBe listOf(null, null, null)
    }

    "reinterpret voice data as wet | \"0.2 0.5 0.8\".vowel()" {
        val p = "0.2 0.5 0.8".vowel()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events.map { it.data.vowelMix } shouldBe listOf(0.2, 0.5, 0.8)
    }

    "reinterpret voice data as wet | seq(\"0.2 0.5 0.8\").apply(vowel())" {
        val p = seq("0.2 0.5 0.8").apply(vowel())
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events.map { it.data.vowelMix } shouldBe listOf(0.2, 0.5, 0.8)
    }

    "vowel() sets the vowel property" {
        val p = note("c3").vowel(vowel = "a")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.vowel shouldBe "a"
    }

    "vowel() works with string pattern sequences" {
        val p = note("c3 e3").vowel(vowel = "a o")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.vowel shouldBe "a"
        events[1].data.vowel shouldBe "o"
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

    "vowel() writes the orbit's vowel slots: the vowel's index (a 5-band formant bank) and the default wet" {
        val slots = slots(note("c3").vowel(vowel = "a"))

        bands(slots)?.size shouldBe 5
        // Default dry/wet amount (blended over the dry source, not wet-only).
        slots["vowel.wet"] shouldBe 0.5
    }

    "vowel(wet = ...) overrides the formant dry/wet amount" {
        slots(note("c3").vowel(vowel = "a", wet = 0.3))["vowel.wet"] shouldBe 0.3
    }

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

    "vowel() as string extension" {
        val p = "c3 e3".vowel(vowel = "a")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.vowel shouldBe "a"
        events[1].data.vowel shouldBe "a"
    }
})
