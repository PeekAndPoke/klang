/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.math.pow

/**
 * [fastExp2] replaces `2.0.pow(x)` in the pitch paths' per-sample loops. Its contract: for any
 * `x` in `(-32, 32)` it matches `pow` to a relative error under [FAST_EXP2_MAX_REL_ERROR]; outside,
 * and for NaN and the infinities, it is `pow` itself.
 *
 * The bound is asserted against a dense sweep, not derived from the polynomial: the polynomial's
 * own coefficients and the octave table are the things under test.
 */
class FastExp2Spec : StringSpec({

    fun relErr(x: Double): Double = abs(fastExp2(x) / 2.0.pow(x) - 1.0)

    "matches pow to under FAST_EXP2_MAX_REL_ERROR across the whole fast range" {
        var worst = 0.0
        var worstAt = 0.0
        val steps = 640_000

        for (k in 1 until steps) {
            val x = -32.0 + 64.0 * k / steps
            val err = relErr(x)

            if (err > worst) {
                worst = err
                worstAt = x
            }
        }

        withClue("worst relative error at x = $worstAt") { worst shouldBeLessThan FAST_EXP2_MAX_REL_ERROR }
    }

    "every integer is its power of two exactly, and the fifth is exact to the bound" {
        // The polynomial's ends are pinned (p(0) = 1, p(1) = 2 in floating point), which the
        // envelope curve's endpoints rely on through fastExp(0) = 1.
        for (n in -32..31) {
            withClue("2^$n") { fastExp2(n.toDouble()) shouldBe 2.0.pow(n) }
        }

        // an equal-tempered fifth, the ratio a pitch path asks for most
        abs(fastExp2(7.0 / 12.0) - 1.4983070768766815) shouldBeLessThan FAST_EXP2_MAX_REL_ERROR
    }

    "no step at an integer boundary: the value just below n continues into the exact 2^n" {
        // A sweeping pitch crosses octaves; the value just below n comes from the polynomial's
        // upper end scaled by 2^(n-1), the value at n from its pinned lower end scaled by 2^n.
        for (n in -31..31) {
            val below = fastExp2(n - 1e-12)
            val at = fastExp2(n.toDouble())

            withClue("boundary at $n") { abs(below / at - 1.0) shouldBeLessThan 3 * FAST_EXP2_MAX_REL_ERROR }
        }
    }

    "outside the fast range and for non-finite input it is pow itself" {
        fastExp2(40.0) shouldBe 2.0.pow(40.0)
        fastExp2(-40.0) shouldBe 2.0.pow(-40.0)
        fastExp2(32.0) shouldBe 2.0.pow(32.0)
        fastExp2(-32.0) shouldBe 2.0.pow(-32.0)
        fastExp2(Double.NaN).isNaN() shouldBe true
        fastExp2(Double.POSITIVE_INFINITY) shouldBe Double.POSITIVE_INFINITY
        fastExp2(Double.NEGATIVE_INFINITY) shouldBe 0.0
    }
})
