/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.BooleanValue
import io.peekandpoke.klang.script.runtime.KlangScriptInternalError
import io.peekandpoke.klang.script.runtime.NumberValue

/**
 * The stdlib's `Random` (KLANG-00450): the functions of Kotlin's `Random`, each form of a function reached by
 * leaving arguments out. Every draw is repeated, so a range that is off by one or a form that reads the wrong
 * argument shows.
 */
class StdLibRandomSpec : StringSpec({

    val draws = 400

    fun engine() = klangScript().also { it.execute("""import * from "stdlib"""") }

    fun nums(code: String): List<Double> {
        val engine = engine()

        return List(draws) { engine.execute(code).shouldBeInstanceOf<NumberValue>().value }
    }

    "nextInt(from, until) gives every whole number of the range, and none outside" {
        nums("Random.nextInt(3, 6)").distinct() shouldContainExactlyInAnyOrder listOf(3.0, 4.0, 5.0)
    }

    "nextInt(until) starts at 0 and stays below its argument" {
        nums("Random.nextInt(3)").distinct() shouldContainExactlyInAnyOrder listOf(0.0, 1.0, 2.0)
    }

    "a named until alone is the form with one argument" {
        nums("Random.nextInt(until = 3)").distinct() shouldContainExactlyInAnyOrder listOf(0.0, 1.0, 2.0)

        val values = nums("Random.nextDouble(until = 10)")

        values.forEach { it shouldBeGreaterThanOrEqual 0.0; it shouldBeLessThan 10.0 }
        values.count { it < 5.0 } shouldBeGreaterThan 100
        values.count { it >= 5.0 } shouldBeGreaterThan 100
    }

    "named from and until are the range" {
        nums("Random.nextInt(until = 6, from = 3)").distinct() shouldContainExactlyInAnyOrder listOf(3.0, 4.0, 5.0)
    }

    "nextInt() gives whole numbers of 32 bits, negative ones too, and not the same one each time" {
        val values = nums("Random.nextInt()")

        values.forEach { withClue(it) { (it == it.toInt().toDouble()).shouldBeTrue() } }
        values.count { it < 0 } shouldBeGreaterThan 100
        values.count { it > 0 } shouldBeGreaterThan 100
        values.distinct().size shouldBeGreaterThan draws - 5
    }

    "nextDouble() stays in 0 to below 1 and reaches both halves" {
        val values = nums("Random.nextDouble()")

        values.forEach { it shouldBeGreaterThanOrEqual 0.0; it shouldBeLessThan 1.0 }
        values.count { it < 0.5 } shouldBeGreaterThan 100
        values.count { it >= 0.5 } shouldBeGreaterThan 100
    }

    "nextDouble(until) starts at 0 and stays below its argument" {
        val values = nums("Random.nextDouble(10)")

        values.forEach { it shouldBeGreaterThanOrEqual 0.0; it shouldBeLessThan 10.0 }
        values.count { it < 5.0 } shouldBeGreaterThan 100
        values.count { it >= 5.0 } shouldBeGreaterThan 100
    }

    "nextDouble(from, until) stays in its range and reaches both halves" {
        val values = nums("Random.nextDouble(20, 30)")

        values.forEach { it shouldBeGreaterThanOrEqual 20.0; it shouldBeLessThan 30.0 }
        values.count { it < 25.0 } shouldBeGreaterThan 100
        values.count { it >= 25.0 } shouldBeGreaterThan 100
    }

    "nextBoolean() gives both answers" {
        val engine = engine()
        val values = List(draws) { engine.execute("Random.nextBoolean()").shouldBeInstanceOf<BooleanValue>().value }

        values.count { it } shouldBeGreaterThan 100
        values.count { !it } shouldBeGreaterThan 100
    }

    "a range that holds no number is an error, as in Kotlin" {
        val engine = engine()

        listOf("Random.nextInt(5, 5)", "Random.nextInt(0)", "Random.nextDouble(2, 1)").forEach { code ->
            withClue(code) {
                shouldThrow<KlangScriptInternalError> { engine.execute(code) }.cause.shouldBeInstanceOf<IllegalArgumentException>()
            }
        }
    }
})
