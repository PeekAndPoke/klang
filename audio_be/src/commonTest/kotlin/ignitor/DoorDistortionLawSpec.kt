/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.DistortionShape
import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.applyDistortionShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * **The waveshaper curves and the doors' distortion chain, against oracles written here** (test consolidation gaps,
 * 2026-09-28). Before this spec only the baseline's whole-voice rows pinned the curves in exact bits, and the doors'
 * chain had loose bound rows only.
 *
 * - The curve table: every [DistortionShape] at a handful of inputs, the expected value from the formula its KDoc in
 *   `ShapingFuncs` documents, written out here. The Padé `tanh` the soft shapes share is written out too
 *   (`x (27 + x²) / (27 + 9 x²)`, clamped to ±1 outside ±3), because it IS the documented curve (`fastTanh`), not an
 *   approximation of one.
 * - The doors' chain (`Ignitor.drive` + `Ignitor.shape`, which `Ignitor.distort` is, decision D2 option A; the
 *   guitars of DerSchmetterling, Sandsturm and ATruthWorthLyingFor): drive `10^(1.2 amount)`, the shaper, the DC
 *   blocker `y = x - x[n-1] + 0.995 y[n-1]`, then `softCap` (identity to 0.95, `0.95 + 0.05 tanh((|x| - 0.95) / 0.05)`
 *   above). The oversampled rows run the shaper inside the production [Oversampler] (its law is `OversamplerSpec`'s);
 *   drive, shaper, blocker and cap are this spec's.
 */
class DoorDistortionLawSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128

    /** The documented Padé tanh (`ShapingFuncs.fastTanh`), clamped to the rail outside ±3. */
    fun pade(x: Double): Double = when {
        x < -3.0 -> -1.0
        x > 3.0 -> 1.0
        else -> x * (27.0 + x * x) / (27.0 + 9.0 * x * x)
    }

    fun clamp1(x: Double): Double = if (x < -1.0) -1.0 else if (x > 1.0) 1.0 else x

    /** The triangle fold of period 4 through the origin, amplitude ±1, written piecewise. */
    fun triangleFold(x: Double): Double {
        var p = x

        while (p > 3.0) {
            p -= 4.0
        }

        while (p < -1.0) {
            p += 4.0
        }

        return if (p <= 1.0) p else 2.0 - p
    }

    /** Each shape's documented curve, and the tolerance its oracle carries (0 = the same arithmetic, bit for bit). */
    fun curve(shape: DistortionShape, x: Double): Pair<Double, Double> = when (shape) {
        DistortionShape.SOFT -> pade(x) to 0.0
        DistortionShape.HARD -> clamp1(x) to 0.0
        DistortionShape.GENTLE -> 2.0 * x / (1.0 + abs(x)) to 0.0
        DistortionShape.CUBIC -> clamp1(x).let { 1.5 * it - 0.5 * it * it * it } to 0.0
        DistortionShape.DIODE -> (if (x >= 0.0) pade(x) else pade(0.75 * x)) to 0.0
        DistortionShape.FOLD -> sin(x) to 0.0
        DistortionShape.CHEBYSHEV -> clamp1(x).let { 4.0 * it * it * it - 3.0 * it } to 0.0
        DistortionShape.RECTIFY -> minOf(abs(x), 1.0) to 0.0
        // e^x through the engine's fastExp (relative error bound 1e-10, DspUtil) against the library exp here: measured
        // 3.3e-11 (EXP) and 1.8e-11 (STOMP_BOX), so the bound itself is the tolerance.
        DistortionShape.EXP -> (if (x >= 0.0) 1.0 - exp(-x) else -(1.0 - exp(x))) to 1e-10
        DistortionShape.SOFT_SAT -> x / sqrt(1.0 + x * x) to 0.0
        // (tanh(x + 0.5) - tanh(0.5)) / (1 + tanh(0.5)) on the Padé tanh; the code multiplies by rounded constants
        // (measured 2.2e-16; the 1e-15 of these three rows is the ulp scale, sineShaper measured 0 on the JVM).
        DistortionShape.TUBE -> (pade(x + 0.5) - pade(0.5)) / (1.0 + pade(0.5)) to 1e-15
        // The fold reduced by whole periods here, by `x - 4 floor(x / 4)` in the code: an ulp apart at most.
        DistortionShape.LINEAR_FOLD -> triangleFold(x) to 1e-15
        DistortionShape.ZERO_SQUARE -> pade(8.0 * x) to 0.0
        DistortionShape.SINE_SHAPER -> sin(PI * x / 2.0) to 1e-15
        DistortionShape.ASYM -> (if (x >= 0.0) minOf(x, 1.0).let { 1.5 * it - 0.5 * it * it * it } else -sqrt(minOf(-x, 1.0))) to 0.0
        DistortionShape.STOMP_BOX -> (if (x >= 0.0) 1.0 - exp(-1.5 * x) else -(1.0 - exp(3.0 * x))) to 1e-10
    }

    val probes = listOf(-4.0, -2.5, -1.2, -0.7, -0.3, 0.0, 0.2, 0.6, 1.0, 1.7, 3.5)

    "every distortion shape's curve, value by value, against its documented formula" {
        for (shape in DistortionShape.entries) {
            for (x in probes) {
                val (expected, tol) = curve(shape, x)
                val actual = applyDistortionShape(shape, x)

                withClue("$shape($x): expected $expected, got $actual") {
                    if (tol == 0.0) {
                        actual.toRawBits() shouldBe expected.toRawBits()
                    } else {
                        actual shouldBe (expected plusOrMinus tol)
                    }
                }
            }
        }
    }

    // ── the doors' chain ────────────────────────────────────────────────────────────────────────────

    fun softCap(x: Double): Double {
        val a = abs(x)

        if (a <= 0.95) {
            return x
        }

        val s = 0.95 + 0.05 * pade((a - 0.95) / 0.05)

        return if (x < 0.0) -s else s
    }

    /** The DC blocker's one-pole law, its state carried across blocks, the stored output flushed below 1e-15. */
    class Blocker {
        var xPrev = 0.0
        var yPrev = 0.0

        fun step(x: Double): Double {
            val y = x - xPrev + 0.995 * yPrev

            xPrev = x
            yPrev = if (abs(y) >= 1e-15) y else 0.0

            return y
        }
    }

    /** A 220 Hz sine at [amplitude] with a small offset, so the shaper sees an asymmetric signal and the blocker works. */
    fun source(n: Int, amplitude: Double): DoubleArray = DoubleArray(n) { i -> 0.05 + amplitude * sin(2.0 * PI * 220.0 * i / sampleRate) }

    /** The chain's output, and the largest magnitude that reached the cap (to show the cap acts in a fixture). */
    fun oracleChain(input: DoubleArray, amount: Double, shape: DistortionShape, oversampleStages: Int): Pair<DoubleArray, Double> {
        val gain = 10.0.pow(amount * 1.2)
        val blocker = Blocker()
        val os = if (oversampleStages > 0) Oversampler(oversampleStages) else null
        val scratch = ScratchBuffers(blockFrames)
        val out = DoubleArray(input.size)
        val work = DoubleArray(blockFrames)
        var capInPeak = 0.0
        var at = 0

        while (at < input.size) {
            val n = minOf(blockFrames, input.size - at)

            for (i in 0 until n) {
                work[i] = input[at + i] * gain
            }

            if (os != null) {
                os.process(work, 0, n, scratch) { w, count ->
                    for (i in 0 until count) {
                        w[i] = curve(shape, w[i]).first
                    }
                }
            } else {
                for (i in 0 until n) {
                    work[i] = curve(shape, work[i]).first
                }
            }

            for (i in 0 until n) {
                val blocked = blocker.step(work[i])

                capInPeak = maxOf(capInPeak, abs(blocked))
                out[at + i] = softCap(blocked)
            }

            at += n
        }

        return out to capInPeak
    }

    fun maxAbsDiff(a: DoubleArray, b: DoubleArray): Double = a.indices.maxOf { abs(a[it] - b[it]) }

    "the doors' chain: drive, shaper, DC blocker, soft cap, in that order, on the plain and the oversampled path" {
        val total = 2048
        // The soft and tube guitars, a one-sided and an asymmetric shape, at a drive that rails the shaper (so the
        // blocker overshoots and the cap acts) and at lighter ones; the DSL name and the curve it selects.
        val cases = listOf(
            DistortCase(1.0, "soft", DistortionShape.SOFT, 0, capActs = true),
            DistortCase(0.35, "tube", DistortionShape.TUBE, 0, capActs = false),
            DistortCase(0.5, "rectify", DistortionShape.RECTIFY, 0, capActs = false),
            DistortCase(0.7, "asym", DistortionShape.ASYM, 0, capActs = true),
            DistortCase(1.0, "soft", DistortionShape.SOFT, 2, capActs = true),
            DistortCase(0.3, "tube", DistortionShape.TUBE, 1, capActs = false),
        )
        val windows = List(total / blockFrames) { blockFrames }

        // `Ignitor.distort(amount, shape, os)` IS `drive(amount).shape(shape, os)` (one line in IgnitorEffects.kt); both
        // spellings are rendered so a future divergence of the two doors shows here.
        for (c in cases) {
            val label = "${c.name} x${c.os} at ${c.amount}"
            val input = source(total, 0.8)
            val (expected, capInPeak) = oracleChain(input, c.amount, c.shape, c.os)
            val viaDistort = renderNodeWindows(ArrayIgnitor(input).distort(ParamIgnitor("amount", c.amount), c.name, c.os), windows, sampleRate)
            val viaChain = renderNodeWindows(ArrayIgnitor(input).drive(ParamIgnitor("amount", c.amount)).shape(c.name, c.os), windows, sampleRate)

            if (c.capActs) {
                withClue("$label: the blocker overshoots past the knee, so the cap acts in this fixture") {
                    capInPeak shouldBeGreaterThan 1.0
                }
            }

            // 1e-13: measured at most 8.3e-16 (JVM, 2026-09-28), the tube curve's last-bit difference carried through
            // the blocker. A wrong chain (a step dropped or moved, another drive law) is orders of magnitude further.
            withClue("$label: distort(...)") { maxAbsDiff(viaDistort, expected) shouldBeLessThan 1e-13 }
            withClue("$label: drive(...).shape(...)") { maxAbsDiff(viaChain, expected) shouldBeLessThan 1e-13 }
        }
    }
})

private class DistortCase(val amount: Double, val name: String, val shape: DistortionShape, val os: Int, val capActs: Boolean)
