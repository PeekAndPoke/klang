/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangEchoSpec : StringSpec({

    "echo dsl interface" {
        val pat = "bd sd"
        dslInterfaceTests(
            "pattern.echo(3, 0.125, 0.5)" to s(pat).echo(3, 0.125, 0.5),
            "script pattern.echo(3, 0.125, 0.5)" to SprudelPattern.compile("""s("$pat").echo(3, 0.125, 0.5)"""),
            "string.echo(3, 0.125, 0.5)" to pat.echo(3, 0.125, 0.5),
            "script string.echo(3, 0.125, 0.5)" to SprudelPattern.compile(""""$pat".echo(3, 0.125, 0.5)"""),
            "echo(3, 0.125, 0.5)" to s(pat).apply(echo(3, 0.125, 0.5)),
            "script echo(3, 0.125, 0.5)" to SprudelPattern.compile("""s("$pat").apply(echo(3, 0.125, 0.5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "stut dsl interface" {
        val pat = "bd sd"
        dslInterfaceTests(
            "pattern.stut(3, 0.125, 0.5)" to s(pat).stut(3, 0.125, 0.5),
            "script pattern.stut(3, 0.125, 0.5)" to SprudelPattern.compile("""s("$pat").stut(3, 0.125, 0.5)"""),
            "string.stut(3, 0.125, 0.5)" to pat.stut(3, 0.125, 0.5),
            "script string.stut(3, 0.125, 0.5)" to SprudelPattern.compile(""""$pat".stut(3, 0.125, 0.5)"""),
            "stut(3, 0.125, 0.5)" to s(pat).apply(stut(3, 0.125, 0.5)),
            "script stut(3, 0.125, 0.5)" to SprudelPattern.compile("""s("$pat").apply(stut(3, 0.125, 0.5))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "echo() produces the original plus decayed delayed copies" {
        // s("bd sd").echo(3, 0.25, 0.5): 3 layers, each shifted 0.25 cycles
        val p = s("bd sd").echo(3, 0.25, 0.5)
        val events = p.queryArc(0.0, 1.0)

        // original (2) + echo1 (2) + echo2 wraps from/into adjacent cycles = 7 in [0,1)
        events.size shouldBe 7
    }

    "each echo layer starts delay x layer later, and the original layer keeps its own data" {
        // 3 x 0.2 < 1, so all four onsets fall inside [1, 2) and none coincide: a layer offset of one delay instead
        // of i delays puts two onsets on the same time and shows. The layers' gain has its own rows below.
        val events = note("c3").gain(0.8).echo(4, 0.2, 0.5).queryArc(1.0, 2.0)
            .filter { it.isOnset }
            .sortedBy { it.whole.begin.toDouble() }

        events.map { it.whole.begin.toDouble() - 1.0 }.zip(listOf(0.0, 0.2, 0.4, 0.6)).forEach { (actual, expected) ->
            actual shouldBe (expected plusOrMinus EPSILON)
        }
        events.size shouldBe 4
        events.forEach { it.whole.end.toDouble() - it.whole.begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON) }
        events.forEach { it.data.note shouldBe "c3" }

        withClue("the original layer: no delay, its own gain") {
            events[0].data.gain shouldBe 0.8
        }
    }

    "each echo layer multiplies its source gain by decay^i, and an unset gain counts as 1.0" {
        // Decided 2026-09-28: the decay multiplies the source gain. The source gain 0.9 is not a power of the decay
        // 0.6, so the product (0.54, 0.324) differs from both a replaced gain (0.6, 0.36) and a shifted power
        // (decay^(i+1): 0.36, 0.216); a source gain of 0.6 would make the first two coincide.
        fun layerGains(pattern: SprudelPattern): List<Double?> = pattern.queryArc(1.0, 2.0)
            .filter { it.isOnset && it.data.note == "c3" }
            .sortedBy { it.whole.begin.toDouble() }
            .map { it.data.gain }

        val withGain = layerGains(note("c3 e3").gain(0.9).echo(3, 0.125, 0.6))
        val withoutGain = layerGains(note("c3 e3").echo(3, 0.125, 0.6))

        withClue("source gain 0.9") {
            withGain.size shouldBe 3
            withGain.zip(listOf(0.9, 0.54, 0.324)).forEach { (actual, expected) ->
                actual shouldBe (expected plusOrMinus EPSILON)
            }
        }

        withClue("no source gain: the original stays unset, the echoes are decay^i") {
            withoutGain.size shouldBe 3
            withoutGain[0] shouldBe null
            withoutGain.drop(1).zip(listOf(0.6, 0.36)).forEach { (actual, expected) ->
                actual shouldBe (expected plusOrMinus EPSILON)
            }
        }
    }

    "a patterned source gain: each echo layer multiplies its own event's gain" {
        // c3 carries 0.9, e3 carries 0.5; one echo half a cycle late puts c3's echo on e3's slot and the reverse,
        // so a layer that read the gain by time instead of from its own event would swap them.
        val events = note("c3 e3").gain("0.9 0.5").echo(2, 0.5, 0.6).queryArc(1.0, 2.0)
            .filter { it.isOnset }

        fun gainOf(note: String, begin: Double): Double? =
            events.single { it.data.note == note && it.whole.begin.toDouble() == begin }.data.gain

        gainOf("c3", 1.0) shouldBe (0.9 plusOrMinus EPSILON)
        gainOf("e3", 1.5) shouldBe (0.5 plusOrMinus EPSILON)
        withClue("c3's echo") { gainOf("c3", 1.5) shouldBe (0.54 plusOrMinus EPSILON) }
        withClue("e3's echo, wrapped in from the previous cycle") { gainOf("e3", 1.0) shouldBe (0.3 plusOrMinus EPSILON) }
    }

    "a non-finite source gain counts as unset, so its echo layer is decay^i" {
        val echo = note("c3").gain(Double.NaN).echo(2, 0.5, 0.6).queryArc(1.0, 2.0)
            .filter { it.isOnset }
            .single { it.whole.begin.toDouble() == 1.5 }

        echo.data.gain shouldBe (0.6 plusOrMinus EPSILON)
    }

    "a non-finite decay leaves the echo layer at its source gain" {
        val echo = note("c3").gain(0.5).echo(2, 0.5, Double.NaN).queryArc(1.0, 2.0)
            .filter { it.isOnset }
            .single { it.whole.begin.toDouble() == 1.5 }

        echo.data.gain shouldBe (0.5 plusOrMinus EPSILON)
    }

    "stut multiplies the source gain as echo does" {
        val gains = note("c3").gain(0.9).stut(3, 0.25, 0.6).queryArc(1.0, 2.0)
            .filter { it.isOnset }
            .sortedBy { it.whole.begin.toDouble() }
            .map { it.data.gain }

        gains.size shouldBe 3
        gains.zip(listOf(0.9, 0.54, 0.324)).forEach { (actual, expected) ->
            actual shouldBe (expected plusOrMinus EPSILON)
        }
    }

    "echo(1, ...) returns just the original pattern unchanged" {
        val p = n("0 1 2 3").echo(1, 0.25, 0.5)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
    }

    "stut() produces the same result as echo()" {
        val p1 = s("bd sd").echo(3, 0.125, 0.7)
        val p2 = s("bd sd").stut(3, 0.125, 0.7)

        val events1 = p1.queryArc(0.0, 1.0)
        val events2 = p2.queryArc(0.0, 1.0)

        events1.size shouldBe events2.size
    }
})
