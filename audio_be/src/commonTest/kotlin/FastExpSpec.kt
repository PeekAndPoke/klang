/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.math.exp

/**
 * [fastExp] replaces `kotlin.math.exp` in the envelopes' exponential curve and the compressor's
 * gain. Its contract: for `|x| < 22` it matches `exp` to a relative error under
 * [FAST_EXP2_MAX_REL_ERROR] (the argument scaling adds parts in 1e-15); beyond that, and for NaN
 * and the infinities, it is a platform power of the scaled argument, off by about `|x|` ulp.
 */
class FastExpSpec : StringSpec({

    fun relErr(x: Double): Double = abs(fastExp(x) / exp(x) - 1.0)

    "matches exp to under the bound across the fast range" {
        var worst = 0.0
        var worstAt = 0.0
        val steps = 440_000

        for (k in 0..steps) {
            val x = -22.0 + 44.0 * k / steps
            val err = relErr(x)

            if (err > worst) {
                worst = err
                worstAt = x
            }
        }

        withClue("worst relative error at x = $worstAt") { worst shouldBeLessThan FAST_EXP2_MAX_REL_ERROR }
    }

    "the envelope curve's arguments and the compressor's are exact to the bound" {
        // exp(k · x) for the default curvature k = 3 across a stage, and exp(dB · ln10 / 20) for
        // reductions down to -60 dB: the two callers' whole argument ranges, plus e^0 = 1.
        abs(fastExp(0.0) - 1.0) shouldBeLessThan FAST_EXP2_MAX_REL_ERROR

        for (i in 0..300) {
            relErr(3.0 * i / 300.0) shouldBeLessThan FAST_EXP2_MAX_REL_ERROR
            relErr(-60.0 * i / 300.0 * 0.11512925464970229) shouldBeLessThan FAST_EXP2_MAX_REL_ERROR
        }
    }

    "beyond the fast range it is still exp to a few ulp, and non-finite input behaves like exp" {
        for (x in listOf(-30.0, 30.0, -100.0, 100.0, -700.0, 700.0)) {
            withClue("x = $x") { relErr(x) shouldBeLessThan 1e-12 }
        }

        fastExp(1000.0) shouldBe Double.POSITIVE_INFINITY
        fastExp(-1000.0) shouldBe 0.0
        fastExp(Double.POSITIVE_INFINITY) shouldBe Double.POSITIVE_INFINITY
        fastExp(Double.NEGATIVE_INFINITY) shouldBe 0.0
        fastExp(Double.NaN).isNaN() shouldBe true
    }

    "the exponential envelope shape starts at exactly zero, ends within an ulp of one, and keeps its law to -180 dB" {
        // g(0) = 0 EXACTLY (the polynomial's pinned start): the release's last frame is 0.0 and
        // the envelope specs pin the sustain and gate-end levels to 1e-12. g(1) is A · (1 / A) of
        // one value, 1.0 or one ulp under (k = 7 is such a k). In between, (e^(kx) - 1) / (e^k - 1)
        // to an absolute 1e-9 (a level is an absolute quantity; near zero the difference
        // e^(kx) - 1 is tiny, so its RELATIVE error is not the bound), for the default curvature
        // and for the curvatures a pipeline's VCA stage may set.
        for (k in listOf(0.5, 3.0, 7.0, 8.0)) {
            val norm = adsrExpNorm(k)

            withClue("k = $k: g(0)") { adsrExpShape(0.0, k, norm) shouldBe 0.0 }
            withClue("k = $k: g(1)") { abs(adsrExpShape(1.0, k, norm) - 1.0) shouldBeLessThan 3e-16 }

            for (i in 1 until 1000) {
                val x = i / 1000.0
                val expected = (exp(k * x) - 1.0) / (exp(k) - 1.0)

                withClue("k = $k, x = $x") { abs(adsrExpShape(x, k, norm) - expected) shouldBeLessThan 1e-9 }
            }
        }
    }
})
