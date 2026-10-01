/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.dslInterfaceTests

/**
 * `beats(n)`: the length of n beats in seconds, read against the tempo of each query.
 * At the default cps 0.5 (120 bpm) one beat is 0.5 s.
 */
class LangBeatsSpec : StringSpec({

    fun ctxAtCps(cps: Double) = QueryContext {
        set(QueryContext.cpsKey, cps)
    }

    "beats dsl interface" {
        dslInterfaceTests(
            "beats" to beats(0.5),
            "script beats" to SprudelPattern.compile("beats(0.5)"),
            "script beats named, base by default" to SprudelPattern.compile("beats(n = 0.5)"),
        ) { _, events ->
            events shouldHaveSize 1
            events[0].data.value?.asDouble shouldBe 0.25
        }
    }

    "beats follows a tempo change between two queries of the same pattern" {
        val p = beats(1)

        p.queryArcContextual(0.0, 1.0, ctxAtCps(0.5))[0].data.value?.asDouble shouldBe 0.5
        p.queryArcContextual(1.0, 2.0, ctxAtCps(1.0))[0].data.value?.asDouble shouldBe 0.25
        p.queryArcContextual(2.0, 3.0, ctxAtCps(0.25))[0].data.value?.asDouble shouldBe 1.0
    }

    "beats(1) at the playing tempo is one beat of bpm" {
        val ctx = ctxAtCps(0.6)
        val seconds = beats(1).queryArcContextual(0.0, 1.0, ctx)[0].data.value?.asDouble!!
        val bpmNow = bpm.queryArcContextual(0.0, 1.0, ctx)[0].data.value?.asDouble!!

        seconds * bpmNow shouldBe (60.0 plusOrMinus 1e-9)
    }

    "beats with a pattern keeps its rhythm and follows the tempo" {
        dslInterfaceTests(
            "beats" to beats("<0.5 0.75>"),
            "script beats" to SprudelPattern.compile("beats(\"<0.5 0.75>\")"),
        ) { cycle, events ->
            events shouldHaveSize 1
            events[0].data.value?.asDouble shouldBe if (cycle.toInt() % 2 == 0) 0.25 else 0.375
        }

        val p = beats("0.5 1")
        val events = p.queryArcContextual(0.0, 1.0, ctxAtCps(1.0))

        events shouldHaveSize 2
        events.map { it.data.value?.asDouble } shouldBe listOf(0.125, 0.25)
    }

    "beats with a base counts that many beats to the cycle" {
        dslInterfaceTests(
            "beats" to beats(1, base = 3),
            "script beats" to SprudelPattern.compile("beats(1, 3)"),
            "script beats named" to SprudelPattern.compile("beats(n = 1, base = 3)"),
        ) { _, events ->
            events shouldHaveSize 1
            events[0].data.value?.asDouble!! shouldBe (2.0 / 3.0 plusOrMinus 1e-12)
        }

        beats("<1 2>", base = 8).queryArcContextual(1.0, 2.0, ctxAtCps(1.0))[0].data.value?.asDouble shouldBe 0.25
    }

    "beats with a base of 0 or less counts in fours" {
        beats(1, base = 0).queryArc(0.0, 1.0)[0].data.value?.asDouble shouldBe 0.5
        beats(1, base = -3).queryArc(0.0, 1.0)[0].data.value?.asDouble shouldBe 0.5
    }

    "beats with a pattern keeps its rests" {
        val p = beats("<0.5 ~>")

        p.queryArc(0.0, 1.0) shouldHaveSize 1
        p.queryArc(1.0, 2.0) shouldHaveSize 0
    }

    "beats as a delay time writes seconds at the playing tempo" {
        val subjects = listOf(
            "kotlin" to note("c3").delay(0.3, beats(0.5)),
            "script" to SprudelPattern.compile("note(\"c3\").delay(0.3, beats(0.5))")!!,
        )

        assertSoftly {
            subjects.forEach { (name, p) ->
                repeat(12) { cycle ->
                    withClue("$name, cycle $cycle") {
                        val from = cycle.toDouble()
                        val atDefault = p.queryArc(from, from + 1).filter { it.isOnset }
                        val atDouble = p.queryArcContextual(from, from + 1, ctxAtCps(1.0)).filter { it.isOnset }

                        atDefault[0].data.katalystParams?.get("delay.time") shouldBe 0.25
                        atDouble[0].data.katalystParams?.get("delay.time") shouldBe 0.125
                    }
                }
            }
        }
    }
})
