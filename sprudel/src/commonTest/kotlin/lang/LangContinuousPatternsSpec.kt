/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeBetween
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.sampleAt

class LangContinuousPatternsSpec : StringSpec({

    "range dsl interface" {
        dslInterfaceTests(
            "pattern.range(from, to)" to
                    sine.range(0.0, 100.0),
            "script pattern.range(from, to)" to
                    SprudelPattern.compile("sine.range(0, 100)"),
            "range(from, to)" to
                    sine.apply(range(0.0, 100.0)),
            "script range(from, to)" to
                    SprudelPattern.compile("sine.apply(range(0, 100))"),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "rangex dsl interface" {
        dslInterfaceTests(
            "pattern.rangex(from, to)" to
                    sine.rangex(100.0, 1000.0),
            "script pattern.rangex(from, to)" to
                    SprudelPattern.compile("sine.rangex(100, 1000)"),
            "rangex(from, to)" to
                    sine.apply(rangex(100.0, 1000.0)),
            "script rangex(from, to)" to
                    SprudelPattern.compile("sine.apply(rangex(100, 1000))"),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "steady pattern" {
        withClue("steady in kotlin") {
            val pattern = steady(0.5)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        }

        withClue("steady with range in kotlin") {
            val pattern = steady(0.5).range(10.0, 20.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (15.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (15.0 plusOrMinus EPSILON)
        }

        withClue("steady compiled") {
            val pattern = SprudelPattern.compile("steady(0.75)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.75 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.75 plusOrMinus EPSILON)
        }

        withClue("steady compiled with range") {
            val pattern = SprudelPattern.compile("steady(0.5).range(10, 20)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (15.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (15.0 plusOrMinus EPSILON)
        }
    }

    "signal pattern" {
        withClue("signal in kotlin") {
            val pattern = signal { t -> t * 2.0 }
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
        }

        withClue("signal with range in kotlin") {
            val pattern = signal { t -> t }.range(0.0, 100.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (50.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (100.0 plusOrMinus EPSILON)
        }

        withClue("signal compiled") {
            val pattern = SprudelPattern.compile("signal(t => t * 3)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)
        }

        withClue("signal compiled with range") {
            val pattern = SprudelPattern.compile("signal(t => t).range(0, 10)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
        }
    }

    "time pattern" {
        withClue("time in kotlin") {
            val pattern = time
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(2.5, 2.5 + EPSILON)[0].data.value?.asDouble shouldBe (2.5 plusOrMinus EPSILON)
        }

        withClue("time with range in kotlin") {
            val pattern = time.range(0.0, 100.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (50.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (100.0 plusOrMinus EPSILON)
        }

        withClue("time compiled") {
            val pattern = SprudelPattern.compile("time")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(10.0, 10.0 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
        }

        withClue("time compiled with range") {
            val pattern = SprudelPattern.compile("time.range(0, 100)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (50.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (100.0 plusOrMinus EPSILON)
        }
    }

    "sine oscillator" {
        withClue("sine in kotlin") {
            val pattern = sine
            pattern.queryArc(0.0, 0.0 + EPSILON).shouldNotBeNull()
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("sine with range in kotlin") {
            val pattern = sine.range(-0.5, 0.5)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (-0.5 plusOrMinus EPSILON)
        }

        withClue("sine compiled") {
            val pattern = SprudelPattern.compile("sine")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("sine compiled with range") {
            val pattern = SprudelPattern.compile("sine.range(-0.5, 0.5)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (-0.5 plusOrMinus EPSILON)
        }
    }

    "cosine oscillator" {
        withClue("cosine in kotlin") {
            val pattern = cosine
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        }

        withClue("cosine with range in kotlin") {
            val pattern = cosine.range(-1.0, 1.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
        }

        withClue("cosine compiled") {
            val pattern = SprudelPattern.compile("cosine")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("cosine compiled with range") {
            val pattern = SprudelPattern.compile("cosine.range(-1, 1)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
        }
    }

    "range and rangex have no string receiver: a mini-notation string scales with mul and add" {
        for (method in listOf("range", "rangex")) {
            withClue(method) {
                val error = shouldThrow<KlangScriptTypeError> {
                    SprudelPattern.compile(""""0 0.5 1".$method(100, 1000)""")
                }
                error.message.shouldNotBeNull() shouldContain "has no method '$method'"
            }
        }

        // The migration the docs give: "0 0.5 1".mul(900).add(100) is 100, 550 and 1000, on both doors
        val expected = listOf(100.0, 550.0, 1000.0)
        "0 0.5 1".mul(900).add(100).queryArc(0.0, 1.0).map { it.data.value?.asDouble } shouldBe expected
        SprudelPattern.compile(""""0 0.5 1".mul(900).add(100)""")!!
            .queryArc(0.0, 1.0).map { it.data.value?.asDouble } shouldBe expected
    }

    "cosine is sine.early(0.25), exactly: bare, ranged, through the shorthand and on the script door" {
        val shifted = sine.early(0.25)
        val points = listOf(0.0, 0.1, 0.125, 0.2, 0.25, 1.0 / 3.0, 0.5, 0.7, 0.75, 0.9, 1.0, 1.6, 2.25, 3.875)

        fun SprudelPattern.valuesAt(t: Double): List<Double> =
            queryArc(t, t + EPSILON).map { it.data.value?.asDouble.shouldNotBeNull() }

        val cases = listOf(
            Triple("bare", cosine, shifted),
            Triple("range(200, 400)", cosine.range(200, 400), shifted.range(200, 400)),
            Triple("range(1, -1)", cosine.range(1, -1), shifted.range(1, -1)),
            Triple("shorthand cosine(200, 400)", cosine(200, 400), shifted.range(200, 400)),
            Triple("script bare", SprudelPattern.compile("cosine")!!, SprudelPattern.compile("sine.early(0.25)")!!),
            Triple(
                "script shorthand",
                SprudelPattern.compile("cosine(200, 400)")!!,
                SprudelPattern.compile("sine.early(0.25).range(200, 400)")!!,
            ),
        )

        for ((label, actual, expected) in cases) {
            withClue(label) {
                for (t in points) {
                    withClue("t = $t") {
                        val values = expected.valuesAt(t)
                        values.shouldNotBeEmpty()
                        actual.valuesAt(t) shouldBe values
                    }
                }
            }
        }

        // Not a vacuous match: cosine is not sine, it is a quarter cycle ahead of it
        cosine.valuesAt(0.0) shouldNotBe sine.valuesAt(0.0)
        cosine.valuesAt(0.0) shouldBe sine.valuesAt(0.25)
    }

    "saw oscillator" {
        withClue("saw in kotlin") {
            val pattern = saw
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        }

        withClue("saw with range in kotlin") {
            val pattern = saw.range(10.0, 20.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (15.0 plusOrMinus EPSILON)
        }

        withClue("saw compiled") {
            val pattern = SprudelPattern.compile("saw")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        }

        withClue("saw compiled with range") {
            val pattern = SprudelPattern.compile("saw.range(10, 20)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (15.0 plusOrMinus EPSILON)
        }
    }

    "a falling saw is saw.range(1, 0)" {
        withClue("saw.range(1, 0) in kotlin") {
            val pattern = saw.range(1.0, 0.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }

        withClue("saw.range(10, 0) in kotlin") {
            val pattern = saw.range(10.0, 0.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (5.0 plusOrMinus EPSILON)
        }

        withClue("saw.range(1, 0) compiled") {
            val pattern = SprudelPattern.compile("saw.range(1, 0)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        }

        withClue("saw(10, 0) compiled") {
            val pattern = SprudelPattern.compile("saw(10, 0)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (5.0 plusOrMinus EPSILON)
            pattern.queryArc(1.0, 1.0 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
        }
    }

    "tri oscillator" {
        withClue("tri in kotlin") {
            val pattern = tri
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }

        withClue("tri with range in kotlin") {
            val pattern = tri.range(-1.0, 1.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }

        withClue("tri compiled") {
            val pattern = SprudelPattern.compile("tri")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }

        withClue("tri compiled with range") {
            val pattern = SprudelPattern.compile("tri.range(-1, 1)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "an inverted triangle is tri.range(1, 0)" {
        withClue("tri.range(1, 0) in kotlin") {
            val pattern = tri.range(1.0, 0.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.25, 0.25 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.75, 0.75 + EPSILON)[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        }

        withClue("tri.range(100, 0) in kotlin") {
            val pattern = tri.range(100.0, 0.0)
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (100.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("tri.range(1, 0) compiled") {
            val pattern = SprudelPattern.compile("tri.range(1, 0)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }

        withClue("tri(100, 0) compiled") {
            val pattern = SprudelPattern.compile("tri(100, 0)")!!
            pattern.queryArc(0.0, 0.0 + EPSILON)[0].data.value?.asDouble shouldBe (100.0 plusOrMinus EPSILON)
            pattern.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }
    }

    "square oscillator" {
        withClue("square in kotlin") {
            val pattern = square
            pattern.queryArc(0.1, 0.1 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.6, 0.6 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }

        withClue("square with range in kotlin") {
            val pattern = square.range(0.0, 10.0)
            pattern.queryArc(0.1, 0.1 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.6, 0.6 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
        }

        withClue("square compiled") {
            val pattern = SprudelPattern.compile("square")!!
            pattern.queryArc(0.1, 0.1 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.6, 0.6 + EPSILON)[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }

        withClue("square compiled with range") {
            val pattern = SprudelPattern.compile("square.range(0, 10)")!!
            pattern.queryArc(0.1, 0.1 + EPSILON)[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            pattern.queryArc(0.6, 0.6 + EPSILON)[0].data.value?.asDouble shouldBe (10.0 plusOrMinus EPSILON)
        }
    }

    "Transformation order (slow and range)" {
        withClue("slow(2).range vs range.slow(2)") {
            val t = 0.5 // t=0.5 at slow(2) means phase 0.25

            val patA = sine.slow(2.0).range(-0.5, 0.5)
            val patB = sine.range(-0.5, 0.5).slow(2.0)

            val valA = patA.queryArc(t, t + EPSILON)[0].data.value?.asDouble
            val valB = patB.queryArc(t, t + EPSILON)[0].data.value?.asDouble

            // phase 0.25 -> sine is 1.0 -> mapped to range -0.5..0.5 is 0.5
            valA shouldBe (0.5 plusOrMinus EPSILON)
            valB shouldBe (valA!! plusOrMinus EPSILON)
        }

        withClue("compiled: slow(2).range vs range.slow(2)") {
            val t = 1.5 // t=1.5 at slow(2) means phase 0.75

            val patA = SprudelPattern.compile("sine.slow(2).range(0, 100)")!!
            val patB = SprudelPattern.compile("sine.range(0, 100).slow(2)")!!

            val valA = patA.queryArc(t, t + EPSILON)[0].data.value?.asDouble
            val valB = patB.queryArc(t, t + EPSILON)[0].data.value?.asDouble

            // phase 0.75 -> sine is 0.0 -> mapped to range 0..100 is 0.0
            valA shouldBe (0.0 plusOrMinus EPSILON)
            valB shouldBe (valA!! plusOrMinus EPSILON)
        }
    }

    "perlin oscillator: same seed produces same values" {
        val p1 = perlin.seed(42)
        val p2 = perlin.seed(42)

        val val1 = p1.queryArc(0.5, 0.6)[0].data.value?.asDouble
        val val2 = p2.queryArc(0.5, 0.6)[0].data.value?.asDouble

        val1 shouldBe val2
    }

    "perlin oscillator: different seeds produce different values" {
        val p1 = perlin.seed(1)
        val p2 = perlin.seed(3)

        val val1 = p1.queryArc(0.1, 0.2)[0].data.value?.asDouble
        val val2 = p2.queryArc(0.1, 0.2)[0].data.value?.asDouble

        val1 shouldNotBe val2
    }

    "perlin oscillator: output range in DSL is 0.0 to 1.0" {
        val p = perlin.seed(55)
        var min = 1.0
        var max = 0.0

        for (i in 0..1000) {
            val t = i * 0.1
            val events = p.queryArc(t, t + EPSILON)
            if (events.isNotEmpty()) {
                val v = events[0].data.value?.asDouble ?: 0.5
                // Should be strictly within 0..1 (unipolar)
                v.shouldBeBetween(0.0, 1.0, 0.0001)

                if (v < min) min = v
                if (v > max) max = v
            }
        }

        // Ensure it covers a good part of the range (it's not stuck at 0.5)
        min.shouldBeBetween(0.0, 0.35, 0.0)
        max.shouldBeBetween(0.65, 1.0, 0.0)
    }

    "berlin oscillator: same seed produces same values" {
        val p1 = berlin.seed(99)
        val p2 = berlin.seed(99)

        val val1 = p1.queryArc(0.7, 0.8)[0].data.value?.asDouble
        val val2 = p2.queryArc(0.7, 0.8)[0].data.value?.asDouble

        val1 shouldBe val2
    }

    "berlin oscillator: different seeds produce different values" {
        val p1 = berlin.seed(1)
        val p2 = berlin.seed(2)

        val val1 = p1.queryArc(0.1, 0.2)[0].data.value?.asDouble
        val val2 = p2.queryArc(0.1, 0.2)[0].data.value?.asDouble

        val1 shouldNotBe val2
    }

    "berlin oscillator: stability across multiple queries" {
        val p = berlin.seed(123)

        // Query same point twice in separate query cycles
        val val1 = p.queryArc(1.5, 1.6)[0].data.value?.asDouble
        val val2 = p.queryArc(1.5, 1.6)[0].data.value?.asDouble

        val1 shouldBe val2
    }

    "berlin oscillator: output range in DSL is 0.0 to 1.0" {
        val p = berlin.seed(66)
        var min = 1.0
        var max = 0.0

        for (i in 0..1000) {
            val t = i * 0.1
            val events = p.queryArc(t, t + EPSILON)
            if (events.isNotEmpty()) {
                val v = events[0].data.value?.asDouble ?: 0.5
                // Should be strictly within 0..1 (unipolar)
                v.shouldBeBetween(0.0, 1.0, 0.0001)

                if (v < min) min = v
                if (v > max) max = v
            }
        }

        // Ensure it covers a good part of the range
        min.shouldBeBetween(0.0, 0.35, 0.0)
        max.shouldBeBetween(0.65, 1.0, 0.0)
    }

    "rangex() with exponential scaling" {
        withClue("rangex in kotlin") {
            // rangex uses exponential scaling: log(min) to log(max)
            val pattern = sine.rangex(100.0, 1000.0)
            val events = pattern.queryArc(0.0, 0.0 + EPSILON)

            // At phase 0, sine is 0.5, which should map to geometric mean
            val expected = kotlin.math.sqrt(100.0 * 1000.0) // ~316.2
            events[0].data.value?.asDouble shouldBe (expected plusOrMinus 1.0)
        }

        withClue("rangex compiled") {
            val pattern = SprudelPattern.compile("sine.rangex(100, 1000)")!!
            val events = pattern.queryArc(0.0, 0.0 + EPSILON)

            val expected = kotlin.math.sqrt(100.0 * 1000.0)
            events[0].data.value?.asDouble shouldBe (expected plusOrMinus 1.0)
        }
    }

    "round() rounds values to nearest integer" {
        withClue("round in kotlin") {
            val p = seq("0.5 1.5 2.5").round()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 3
            events[0].data.value?.asInt shouldBe 1  // 0.5 rounds to 0 (banker's rounding may vary)
            events[1].data.value?.asInt shouldBe 2  // 1.5 rounds to 2
            events[2].data.value?.asInt shouldBe 3  // 2.5 rounds to 2
        }

        withClue("round with string extension") {
            val p = "0.4 1.6 2.3".round()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 3
            events[0].data.value?.asInt shouldBe 0
            events[1].data.value?.asInt shouldBe 2
            events[2].data.value?.asInt shouldBe 2
        }

        withClue("round compiled") {
            val p = SprudelPattern.compile("""seq("0.3 1.7 2.5").round()""")!!
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 3
            events[0].data.value?.asInt shouldBe 0
            events[1].data.value?.asInt shouldBe 2
        }
    }

    "floor() floors values to lower integer" {
        withClue("floor in kotlin") {
            val p = seq("0.9 1.1 2.9").floor()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 3
            events[0].data.value?.asInt shouldBe 0
            events[1].data.value?.asInt shouldBe 1
            events[2].data.value?.asInt shouldBe 2
        }

        withClue("floor with negative numbers") {
            val p = seq("-1.5 -0.5 0.5 1.5").floor()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 4
            events[0].data.value?.asInt shouldBe -2  // floor(-1.5) = -2
            events[1].data.value?.asInt shouldBe -1  // floor(-0.5) = -1
            events[2].data.value?.asInt shouldBe 0
            events[3].data.value?.asInt shouldBe 1
        }

        withClue("floor with string extension") {
            val p = "42.1 42.5 43".floor()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 3
            events[0].data.value?.asInt shouldBe 42
            events[1].data.value?.asInt shouldBe 42
            events[2].data.value?.asInt shouldBe 43
        }

        withClue("floor compiled") {
            val p = SprudelPattern.compile("""seq("42.7 43.2").floor()""")!!
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 2
            events[0].data.value?.asInt shouldBe 42
            events[1].data.value?.asInt shouldBe 43
        }
    }

    "ceil() ceils values to higher integer" {
        withClue("ceil in kotlin") {
            val p = seq("0.1 1.1 2.9").ceil()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 3
            events[0].data.value?.asInt shouldBe 1
            events[1].data.value?.asInt shouldBe 2
            events[2].data.value?.asInt shouldBe 3
        }

        withClue("ceil with negative numbers") {
            val p = seq("-1.5 -0.5 0.5 1.5").ceil()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 4
            events[0].data.value?.asInt shouldBe -1  // ceil(-1.5) = -1
            events[1].data.value?.asInt shouldBe 0   // ceil(-0.5) = 0
            events[2].data.value?.asInt shouldBe 1
            events[3].data.value?.asInt shouldBe 2
        }

        withClue("ceil with string extension") {
            val p = "42.1 42.5 43".ceil()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 3
            events[0].data.value?.asInt shouldBe 43
            events[1].data.value?.asInt shouldBe 43
            events[2].data.value?.asInt shouldBe 43
        }

        withClue("ceil compiled") {
            val p = SprudelPattern.compile("""seq("42.2 43.8").ceil()""")!!
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 2
            events[0].data.value?.asInt shouldBe 43
            events[1].data.value?.asInt shouldBe 44
        }
    }

    "note(\"a b\").pan(sine) - multiple notes with continuous pattern" {
        // First, let's test if multiple notes work with sine (no slow)
        val pattern = note("a b").pan(sine)
        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        withClue("Should have 2 events") {
            events.size shouldBe 2
        }

        withClue("Event 0 should have pan") {
            events[0].data.pan.shouldNotBeNull()
        }

        withClue("Event 1 should have pan") {
            events[1].data.pan.shouldNotBeNull()
        }
    }

    "note(\"a\").pan(sine) - apply continuous pattern to property" {
        // Test that continuous patterns can be used to set properties
        // sine oscillates between -1 and 1
        val pattern = note("a").pan(sine)

        val events = pattern.queryArc(0.0, 1.0)

        withClue("Should have 1 event") {
            events.size shouldBe 1
        }

        val event = events[0]
        withClue("Should have note 'a'") {
            event.data.note shouldBe "a"
        }

        withClue("Should have pan value from sine at event start") {
            // sine at 0.0: sin(0) = 0, normalized: (0 + 1.0) / 2.0 = 0.5
            event.data.pan shouldNotBe null
            event.data.pan!! shouldBe (0.5 plusOrMinus EPSILON)
        }
    }

    "sine.slow(8) generates events" {
        // First verify that sine.slow(8) itself works
        val pattern = sine.slow(8.0)
        val events = pattern.queryArc(0.0, 1.0)

        withClue("sine.slow(8) should generate events") {
            events.size shouldBe 1
            events[0].data.value.shouldNotBeNull()
            events[0].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)  // At t=0, sine = 0.5
        }
    }

    "sine.slow(8) can be sampled with sampleAt()" {
        // Verify that sampleAt works on sine.slow(8)
        val pattern = sine.slow(8.0)
        val ctx = SprudelPattern.QueryContext.empty

        val event0 = pattern.sampleAt(0.0, ctx)
        withClue("sampleAt(0.0) should return event") {
            event0.shouldNotBeNull()
            val value0 = event0.data.value.shouldNotBeNull()
            value0.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        }

        val event05 = pattern.sampleAt(0.5, ctx)
        withClue("sampleAt(0.5) should return event") {
            event05.shouldNotBeNull()
            event05.data.value.shouldNotBeNull()
        }
    }

    "note(\"a b\").pan(sine.slow(8)) - apply slowed continuous pattern to multiple notes" {
        // Test continuous pattern with tempo modification applied to multiple events
        val pattern = note("a b").pan(sine.slow(8))

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        withClue("Should have 2 events") {
            events.size shouldBe 2
        }

        // Event 0: note "a" at [0.0, 0.5)
        withClue("Event 0 - note a") {
            events[0].data.note shouldBe "a"
            events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            events[0].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

            // Pan should be sampled from sine.slow(8) at event start (0.0)
            // sine.slow(8) at 0.0: phase = 0.0, sine(0) = 0.5
            events[0].data.pan.shouldNotBeNull()
            events[0].data.pan shouldBe (0.5 plusOrMinus EPSILON)
        }

        // Event 1: note "b" at [0.5, 1.0)
        withClue("Event 1 - note b") {
            events[1].data.note shouldBe "b"
            events[1].part.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            events[1].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

            // Pan should be sampled from sine.slow(8) at event start (0.5)
            // sine.slow(8) at 0.5: phase = 0.5/8 = 0.0625
            // sine(0.0625) = (sin(0.0625 * 2π) + 1) / 2 = (sin(0.3927) + 1) / 2 ≈ (0.383 + 1) / 2 ≈ 0.691
            events[1].data.pan.shouldNotBeNull()
            events[1].data.pan shouldBe (0.691 plusOrMinus 0.01)  // Allow some tolerance
        }
    }

    "note(\"c d e\").gain(sine.range(0.5, 1.0)) - apply ranged continuous pattern to gain" {
        // Test continuous pattern with range transformation
        val pattern = note("c d e").gain(sine.range(0.5, 1.0))

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        withClue("Should have 3 events") {
            events.size shouldBe 3
        }

        // All events should have gain values between 0.5 and 1.0
        events.forEachIndexed { i, event ->
            withClue("Event $i") {
                val gainValue = event.data.gain.shouldNotBeNull()
                gainValue.shouldBeBetween(0.5, 1.0, tolerance = 0.01)
            }
        }
    }

    "sound(\"bd hh\").pan(saw) - apply saw wave to pan" {
        // Test saw wave (ramps from 0 to 1)
        val pattern = sound("bd hh").pan(saw)

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        withClue("Should have 2 events") {
            events.size shouldBe 2
        }

        events.forEachIndexed { i, event ->
            withClue("Event $i") {
                // Saw values should be between -1 and 1 (after normalization)
                val panValue = event.data.pan.shouldNotBeNull()
                panValue.shouldBeBetween(-1.0, 1.0, tolerance = 0.01)
            }
        }
    }

    "PatternMapperFn.range() chains as mapper" {
        // ContextModifierPattern chains inner-first: range(0,10) is inner, range(0,20) is outer.
        // At query time inner context (min=0, max=10) overrides outer (min=0, max=20).
        // saw at 0.5 = 0.5, context [0, 10] -> 5.0
        val p = saw.apply(range(0.0, 10.0).range(0.0, 20.0))

        p.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (5.0 plusOrMinus EPSILON)
    }

    "PatternMapperFn.rangex() chains as mapper" {
        // Verify PatternMapperFn.rangex() works
        // saw at 0.5 = 0.5; rangex(100, 1000): geometric mean of 100*1000 = ~316
        val p = saw.apply(rangex(100.0, 1000.0))
        val expected = kotlin.math.sqrt(100.0 * 1000.0)

        p.queryArc(0.5, 0.5 + EPSILON)[0].data.value?.asDouble shouldBe (expected plusOrMinus 1.0)
    }

    "note(\"a\").lpf(sine.range(200, 2000)) - apply continuous pattern to filter cutoff" {
        // Test applying continuous pattern to filter frequency
        val pattern = note("a").lpf(sine.range(200.0, 2000.0))

        val events = pattern.queryArc(0.0, 1.0)

        withClue("Should have 1 event") {
            events.size shouldBe 1
        }

        val event = events[0]
        withClue("Should have cutoff value") {
            // sine.range(200, 2000) at 0.0: sine(0) = 0 → range(0) = 1100 (midpoint)
            val cutoffValue = event.data.cutoff.shouldNotBeNull()
            cutoffValue.shouldBeBetween(200.0, 2000.0, tolerance = 0.01)
        }
    }

    "compiled: note(\"a b\").pan(sine.slow(4)) - continuous pattern from compiled code" {
        // Test that compiled patterns also work with continuous properties
        val pattern = SprudelPattern.compile("""note("a b").pan(sine.slow(4))""")

        pattern.shouldNotBeNull()

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        withClue("Should have 2 events") {
            events.size shouldBe 2
        }

        events.forEach { event ->
            withClue("Event at ${event.part.begin}") {
                val panValue = event.data.pan.shouldNotBeNull()
                panValue.shouldBeBetween(-1.0, 1.0, tolerance = 0.01)
            }
        }
    }
})
