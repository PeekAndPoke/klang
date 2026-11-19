/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.trumpet

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.math.floor

class HeldReachSpec : StringSpec({

    // a look all there until 9.9 s, out of reach from then on at once: the moments a tenth of a second apart
    fun reach(t: Double): Double = if (t <= 9.9) 1.0 else 0.0

    fun grid(t: Double): List<Pair<Double, Double>> =
        (0..20).map { k -> ((floor(t * 10 + 1e-9).toInt() - k) / 10.0).let { at -> at to reach(at) } }

    fun held(t: Double): Double = heldReach(t = t, now = reach(t), before = grid(t), fall = 1.6)

    "a look that goes out of reach at once is let go over the fall, eased as a look falls" {
        withClue("all there until then") {
            held(9.9) shouldBe 1.0
        }

        withClue("half way through the fall, half let go") {
            held(9.9 + 0.8) shouldBe (0.5 plusOrMinus 1e-9)
        }

        withClue("gone after the fall") {
            held(9.9 + 1.6) shouldBe (0.0 plusOrMinus 1e-9)
            held(12.0) shouldBe 0.0
        }
    }

    "it never lets go faster than the fall, nor jumps" {
        var t = 9.0

        while (t < 12.0) {
            withClue("at $t s") {
                // the eased fall over 1.6 s is steepest in its middle: 1.5 / 1.6 a second
                abs(held(t + 0.01) - held(t)) shouldBeLessThan 0.01 * 1.5 / 1.6 + 1e-9
            }

            t += 0.01
        }
    }

    "a look that comes into reach is taken at once, and moments after now or long past count for nothing" {
        heldReach(t = 5.0, now = 0.7, before = listOf(4.9 to 0.2), fall = 1.6) shouldBe 0.7
        heldReach(t = 5.0, now = 0.0, before = listOf(5.5 to 1.0, 3.0 to 1.0), fall = 1.6) shouldBe 0.0
    }
})
