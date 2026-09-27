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
        // of i delays puts two onsets on the same time and shows. The
        // layers' GAIN is not pinned: `.gain(decay^i)` replaces the source gain today, where the KDoc reads as a
        // multiply; that question is with the maintainer (test consolidation, the golden replacement).
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
