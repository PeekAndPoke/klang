/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.math.sin

/**
 * **The DC blocker's one-pole law, directly** (test consolidation gap, 2026-09-28; before, only the distort paths and
 * the `dcBlock` combinator reached it, through loose DC-removal rows). The documented law:
 * `y[n] = x[n] - x[n-1] + a y[n-1]`, the raw pole `a` (0.995 by default), the state carried from one call to the
 * next, and the stored output flushed to 0 below 1e-15 (the house `flushState`). Both forms, in place (the distort
 * paths, the master) and input to output (the `dcBlock` combinator).
 * The coefficient guard: a finite pole is clamped to [0, 0.99999], a non-finite one falls back to 0.995.
 */
class DcBlockerSpec : StringSpec({

    fun oracle(input: DoubleArray, a: Double): DoubleArray {
        val out = DoubleArray(input.size)
        var xPrev = 0.0
        var yPrev = 0.0

        for (i in input.indices) {
            val y = input[i] - xPrev + a * yPrev

            xPrev = input[i]
            yPrev = if (abs(y) >= 1e-15) y else 0.0
            out[i] = y
        }

        return out
    }

    /** A step onto a DC offset of 0.4 at frame 50, with a 90 Hz tone on top: DC to remove and a signal to keep. */
    fun signal(n: Int): DoubleArray = DoubleArray(n) { i -> (if (i >= 50) 0.4 else 0.0) + 0.3 * sin(i * 0.0118) }

    val windows = listOf(128, 37, 91, 256, 1, 511)

    fun firstMismatch(a: DoubleArray, b: DoubleArray): Int? = a.indices.firstOrNull { a[it].toRawBits() != b[it].toRawBits() }

    "the one-pole law, bit for bit, across calls, in both forms, at the default and an explicit pole" {
        val input = signal(windows.sum())

        for ((label, make, a) in listOf(
            Triple("default", { LowPassHighPassFilters.DcBlocker() }, 0.995),
            Triple("explicit 0.9", { LowPassHighPassFilters.DcBlocker(0.9) }, 0.9),
            Triple("explicit 0.999 (the master's)", { LowPassHighPassFilters.DcBlocker(0.999) }, 0.999),
        )) {
            val expected = oracle(input, a)

            val inPlace = input.copyOf()
            val inPlaceBlocker = make()
            val outOf = DoubleArray(input.size)
            val outOfBlocker = make()
            var at = 0

            for (n in windows) {
                inPlaceBlocker.process(inPlace, at, n)
                outOfBlocker.process(input, outOf, at, n)
                at += n
            }

            withClue("$label, in place: first mismatching frame") { firstMismatch(inPlace, expected) shouldBe null }
            withClue("$label, input to output: first mismatching frame") { firstMismatch(outOf, expected) shouldBe null }
        }
    }

    "the flush: after the signal stops, the decaying output is written as exact zeros once it falls below 1e-15" {
        // At a = 0.9 the tail falls by 0.9 per frame: from about 0.7 to 1e-15 in about 260 frames, so 600 frames of
        // silence end in the flushed region. Without the flush the stored output keeps decaying as a denormal-bound
        // geometric tail and is never exactly 0.
        val input = DoubleArray(700) { i -> if (i < 100) 0.7 * sin(i * 0.3) + 0.2 else 0.0 }
        val out = input.copyOf()

        LowPassHighPassFilters.DcBlocker(0.9).process(out, 0, out.size)

        withClue("the tail is audible right after the signal") { (abs(out[101]) > 1e-3) shouldBe true }
        withClue("the last 100 frames are exact zeros") { (600 until 700).all { out[it] == 0.0 } shouldBe true }
        withClue("bit for bit the oracle, flush included") { firstMismatch(out, oracle(input, 0.9)) shouldBe null }
    }

    "a finite coefficient out of range is clamped to [0, 0.99999]" {
        val input = signal(1024)

        for ((given, clamped) in listOf(1.5 to 0.99999, 1.0 to 0.99999, -0.3 to 0.0)) {
            val out = input.copyOf()

            LowPassHighPassFilters.DcBlocker(given).process(out, 0, out.size)

            withClue("coefficient $given runs as $clamped: first mismatching frame") { firstMismatch(out, oracle(input, clamped)) shouldBe null }
        }
    }

    "a non-finite coefficient falls back to the default pole" {
        val input = signal(1024)
        val expected = oracle(input, 0.995)

        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val out = input.copyOf()

            LowPassHighPassFilters.DcBlocker(bad).process(out, 0, out.size)

            withClue("coefficient $bad: first mismatching frame") { firstMismatch(out, expected) shouldBe null }
        }
    }
})
