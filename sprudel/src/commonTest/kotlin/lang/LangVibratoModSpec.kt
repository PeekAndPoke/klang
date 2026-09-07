/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangVibratoModSpec : StringSpec({

    "vibratoMod dsl interface" {
        val pat = "c4 e4"
        val amount = 0.5

        dslInterfaceTests(
            "pattern.vibrato(depth = depth)" to note(pat).vibrato(depth = amount),
            "script pattern.vibrato(depth = depth)" to SprudelPattern.compile("""note("$pat").vibrato(depth = $amount)"""),
            "string.vibrato(depth = depth)" to pat.vibrato(depth = amount),
            "script string.vibrato(depth = depth)" to SprudelPattern.compile(""""$pat".vibrato(depth = $amount)"""),
            "vibrato(depth = depth)" to note(pat).apply(vibrato(depth = amount)),
            "script vibrato(depth = depth)" to SprudelPattern.compile("""note("$pat").apply(vibrato(depth = $amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.vibratoMod shouldBe amount
        }
    }

    "vibrato(depth = ...) sets VoiceData.vibratoMod depth" {
        val p = note("a b").vibrato(depth = "0.1 0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.vibratoMod } shouldBe listOf(0.1, 0.5)
    }

    "vibrato(depth = ...) works as pattern extension" {
        val p = note("c").vibrato(depth = "0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.vibratoMod shouldBe 0.1
    }

    "vibrato(depth = ...) works as string extension" {
        val p = "c".vibrato(depth = "0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.vibratoMod shouldBe 0.1
    }

    "vibrato(depth = ...) works in compiled code" {
        val p = SprudelPattern.compile("""note("c").vibrato(depth = "0.1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.vibratoMod shouldBe 0.1
    }

    "vibrato(depth = ...) with continuous pattern sets vibratoMod correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").vibrato(depth = sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.vibratoMod shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.vibratoMod shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.vibratoMod shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.vibratoMod shouldBe (0.0 plusOrMinus EPSILON)
    }
})
