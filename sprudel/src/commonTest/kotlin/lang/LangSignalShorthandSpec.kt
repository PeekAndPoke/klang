/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeBetween
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.script.runtime.KlangScriptArgumentError
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * The callable shorthand of the sprudel signals (`docs/tasks-archive/2026-10/20261005-sprudel-signals-range-cleanup.md`, step 4):
 * `perlin(200, 400)` is exactly `perlin.range(200, 400)` on both doors, the bare name stays a pattern, and a call
 * with fewer than two values is a script error that names the fix.
 */
class LangSignalShorthandSpec : StringSpec({

    /** One signal: its script name, the object, and its Kotlin call form with two, one and no values. */
    class Signal(
        val name: String,
        val obj: SprudelSignal,
        val two: (Number, Number) -> SprudelPattern,
        val one: (Number) -> SprudelPattern,
        val none: () -> SprudelPattern,
    )

    val signals = listOf(
        Signal("sine", sine, { a, b -> sine(a, b) }, { sine(it) }, { sine() }),
        Signal("cosine", cosine, { a, b -> cosine(a, b) }, { cosine(it) }, { cosine() }),
        Signal("saw", saw, { a, b -> saw(a, b) }, { saw(it) }, { saw() }),
        Signal("tri", tri, { a, b -> tri(a, b) }, { tri(it) }, { tri() }),
        Signal("square", square, { a, b -> square(a, b) }, { square(it) }, { square() }),
        Signal("perlin", perlin, { a, b -> perlin(a, b) }, { perlin(it) }, { perlin() }),
        Signal("berlin", berlin, { a, b -> berlin(a, b) }, { berlin(it) }, { berlin() }),
        Signal("rand", rand, { a, b -> rand(a, b) }, { rand(it) }, { rand() }),
    )

    /** Sampled the same way on every side: seeded (the noises and rand need it), 8 steps a cycle, 4 cycles. */
    fun SprudelPattern.sampled(): List<Double> =
        seed(7).segment(8).queryArc(0.0, 4.0).map { it.data.value?.asDouble.shouldNotBeNull() }

    fun script(code: String): SprudelPattern = SprudelPattern.compile(code).shouldNotBeNull()

    "the shorthand is exactly range, per signal, on the script door and the Kotlin door" {
        for (s in signals) {
            withClue(s.name) {
                val expected = s.obj.range(200, 400).sampled()

                // Not a vacuous match: 32 samples between the two values, and the signal moves.
                expected shouldHaveSize 32
                expected.forEach { it.shouldBeBetween(200.0, 400.0, 0.0) }
                expected.toSet().size shouldBeGreaterThan 1

                withClue("Kotlin door") { s.two(200, 400).sampled() shouldBe expected }
                withClue("script door") { script("${s.name}(200, 400)").sampled() shouldBe expected }
                withClue("script door, named") { script("${s.name}(from = 200, to = 400)").sampled() shouldBe expected }
                withClue("script range") { script("${s.name}.range(200, 400)").sampled() shouldBe expected }
                withClue("script range, named") {
                    script("${s.name}.range(from = 200, to = 400)").sampled() shouldBe expected
                    script("${s.name}.range(to = 400, from = 200)").sampled() shouldBe expected
                }
            }
        }
    }

    "the range values are named from and to on every range door of the script" {
        val expected = perlin.range(200, 400).sampled()
        val reversed = perlin.range(400, 200).sampled()
        val exponential = perlin.rangex(200, 400).sampled()

        // Not a vacuous match: the two directions and the two curves differ
        reversed shouldNotBe expected
        exponential shouldNotBe expected

        script("perlin.range(from = 200, to = 400)").sampled() shouldBe expected
        script("perlin.range(from = 400, to = 200)").sampled() shouldBe reversed
        script("perlin.apply(range(from = 200, to = 400))").sampled() shouldBe expected
        script("perlin.rangex(from = 200, to = 400)").sampled() shouldBe exponential
        script("perlin.apply(rangex(to = 400, from = 200))").sampled() shouldBe exponential
    }

    "the bare signal is still a pattern: a door takes it, and its pattern methods work" {
        for (s in signals) {
            withClue(s.name) {
                val raw = s.obj.sampled()
                raw.forEach { it.shouldBeBetween(0.0, 1.0, 0.0) }
                raw.toSet().size shouldBeGreaterThan 1

                // In a door: the pan of each note is the signal read at its onset, on both doors.
                val kotlinPan = note("c d e f").pan(s.obj).seed(7).queryArc(0.0, 2.0).map { it.data.pan }
                val scriptPan = script("""note("c d e f").pan(${s.name})""").seed(7).queryArc(0.0, 2.0).map { it.data.pan }
                kotlinPan shouldHaveSize 8
                kotlinPan.forEach { it.shouldNotBeNull().shouldBeBetween(0.0, 1.0, 0.0) }
                scriptPan shouldBe kotlinPan

                // A pattern method on the bare name, on both doors.
                script("${s.name}.slow(2)").sampled() shouldBe s.obj.slow(2).sampled()
            }
        }
    }

    "one value or none is a script error naming the fix, on the script door" {
        for (s in signals) {
            val message = "a signal's range takes two values: ${s.name}(from, to)"

            for (code in listOf("${s.name}(200)", "${s.name}()", "${s.name}(to = 400)", "${s.name}(from = 200)")) {
                withClue(code) {
                    val error = shouldThrow<KlangScriptArgumentError> { SprudelPattern.compile(code) }
                    error.message shouldBe message
                    error.location.shouldNotBeNull().startLine shouldBe 1
                }
            }
        }
    }

    "one value or none is the same error on the Kotlin door" {
        for (s in signals) {
            val message = "a signal's range takes two values: ${s.name}(from, to)"

            withClue("${s.name}(200)") { shouldThrow<KlangScriptArgumentError> { s.one(200) }.message shouldBe message }
            withClue("${s.name}()") { shouldThrow<KlangScriptArgumentError> { s.none() }.message shouldBe message }
        }
    }
})
