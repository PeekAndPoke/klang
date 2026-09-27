/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.beGreaterThanOrEqualTo
import io.kotest.matchers.doubles.beLessThanOrEqualTo
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random

/**
 * Property-style bounds tests for every shape in [ShapingFuncs], as tables over the shapes.
 *
 * For each function: output is finite and bounded by 1.0; a symmetric shape is odd, f(-x) = -f(x); an asymmetric
 * one leans the documented way. Inputs are a deterministic mix of edge values and PRNG samples with a fixed seed
 * for reproducibility.
 *
 * NaN/Inf inputs are deliberately NOT exercised here: those are caller responsibility per the file-level KDoc.
 */
class ShapingFuncsBoundsSpec : StringSpec({

    val edgeInputs = listOf(
        -100.0, -10.0, -3.5, -3.0, -1.5, -1.0, -0.95, -0.5, -1e-6, 0.0,
        1e-6, 0.5, 0.95, 1.0, 1.5, 3.0, 3.5, 10.0, 100.0,
    )
    val rng = Random(seed = 0x4B4C4156L) // fixed for reproducibility
    val randomInputs = List(64) { rng.nextDouble(-50.0, 50.0) }
    val allInputs = edgeInputs + randomInputs

    /** Every shape, with the tolerance of its odd symmetry, or null for a shape that is asymmetric by design. */
    val shapes = listOf(
        Shape("fastTanh", 1e-12) { ShapingFuncs.fastTanh(it) },
        Shape("hardClip", 1e-9) { ShapingFuncs.hardClip(it) },
        Shape("softClip", 1e-9) { ShapingFuncs.softClip(it) },
        Shape("cubicClip", 1e-9) { ShapingFuncs.cubicClip(it) },
        Shape("sineFold", 1e-12) { ShapingFuncs.sineFold(it) },
        Shape("nativeTanh", 1e-12) { ShapingFuncs.nativeTanh(it) },
        Shape("diodeClip", null) { ShapingFuncs.diodeClip(it) },
        Shape("chebyshevT3", 1e-9) { ShapingFuncs.chebyshevT3(it) },
        Shape("rectify", null) { ShapingFuncs.rectify(it) },
        Shape("expClip", 1e-12) { ShapingFuncs.expClip(it) },
        Shape("softSat", 1e-12) { ShapingFuncs.softSat(it) },
        Shape("tube", null) { ShapingFuncs.tube(it) },
        Shape("linearFold", 1e-9) { ShapingFuncs.linearFold(it) },
        Shape("zeroSquare", 1e-12) { ShapingFuncs.zeroSquare(it) },
        Shape("sineShaper", 1e-12) { ShapingFuncs.sineShaper(it) },
        Shape("asym", null) { ShapingFuncs.asym(it) },
        Shape("stompBox", null) { ShapingFuncs.stompBox(it) },
        Shape("softCap", 1e-12) { ShapingFuncs.softCap(it) },
    )

    "every shape is finite and bounded by 1.0 for finite input, and every distortion shape by its rail" {
        for (shape in shapes) {
            for (x in allInputs) {
                val y = shape.fn(x)

                withClue("${shape.name} x=$x -> y=$y") {
                    y.isFinite() shouldBe true
                    abs(y) should beLessThanOrEqualTo(1.0 + 1e-9)
                }
            }
        }

        // ...and so is every shape a distortion NAME selects, as the distort node applies it: the enum maps to
        // a bounded function (gentle is softClip doubled, so its rail is 2).
        for (shape in DistortionShape.entries) {
            val bound = if (shape == DistortionShape.GENTLE) 2.0 else 1.0

            for (x in allInputs) {
                val y = applyDistortionShape(shape, x)

                withClue("DistortionShape.$shape x=$x -> y=$y") {
                    y.isFinite() shouldBe true
                    abs(y) should beLessThanOrEqualTo(bound + 1e-9)
                }
            }
        }
    }

    "every symmetric shape is odd-symmetric" {
        for (shape in shapes) {
            val tol = shape.oddTol ?: continue

            for (x in allInputs) {
                if (x == 0.0) {
                    continue
                }

                val yp = shape.fn(x)
                val yn = shape.fn(-x)

                withClue("${shape.name} symmetry @ x=$x: f(x)=$yp, f(-x)=$yn") {
                    yn shouldBe (-yp plusOrMinus tol)
                }
            }
        }
    }

    "every asymmetric shape leans the documented way" {
        // (shape, probe inputs, true when the NEGATIVE side reaches deeper)
        val leans = listOf(
            // positive branch fastTanh(x), negative fastTanh(0.75 x): the negative side is attenuated
            Triple("diodeClip", listOf(0.5, 1.0, 1.5, 2.0), false),
            // bias 0.5, normalized so the negative rail hits -1 and the positive saturates near +0.37
            Triple("tube", listOf(0.5, 1.0, 1.5, 2.0, 5.0), true),
            // sqrt knee on the negative side: |asym(-0.25)| = 0.5 against |asym(0.25)| near 0.367
            Triple("asym", listOf(0.1, 0.25, 0.5), true),
            // the negative anti-parallel pair has gain 3.0, the positive 1.5
            Triple("stompBox", listOf(0.3, 0.5, 1.0, 2.0), true),
        )

        // The two tables together cover every asymmetric shape: rectify has its own one-sided rule below.
        leans.map { it.first }.toSet() + "rectify" shouldBe shapes.filter { it.oddTol == null }.map { it.name }.toSet()

        for ((name, xs, negativeDeeper) in leans) {
            val fn = shapes.single { it.name == name }.fn

            for (x in xs) {
                val pos = abs(fn(x))
                val neg = abs(fn(-x))

                withClue("$name asymmetry @ x=$x: |f(x)|=$pos, |f(-x)|=$neg") {
                    (if (negativeDeeper) neg > pos else pos > neg) shouldBe true
                }
            }
        }

        for (x in allInputs) {
            val y = ShapingFuncs.rectify(x)

            withClue("rectify output is always non-negative @ x=$x: y=$y") { y should beGreaterThanOrEqualTo(0.0) }
        }
    }

    "fixed points, identity regions and landmark values" {
        // linearFold is the identity in [-1, 1], softCap below its 0.95 threshold.
        for (x in listOf(-1.0, -0.95, -0.5, -0.1, 0.0, 0.1, 0.5, 0.95, 1.0)) {
            withClue("linearFold @ x=$x") { ShapingFuncs.linearFold(x) shouldBe (x plusOrMinus 1e-12) }

            // softCap's identity region is the smaller one.
            if (abs(x) <= 0.95) {
                withClue("softCap @ x=$x") { ShapingFuncs.softCap(x) shouldBe (x plusOrMinus 1e-12) }
            }
        }

        withClue("sineShaper peaks at +1 for x = +1") { ShapingFuncs.sineShaper(1.0) shouldBe (1.0 plusOrMinus 1e-12) }
        withClue("sineShaper peaks at -1 for x = -1") { ShapingFuncs.sineShaper(-1.0) shouldBe (-1.0 plusOrMinus 1e-12) }

        // Zero in, zero out, so the DC blocker downstream can do its job.
        withClue("tube(0)") { ShapingFuncs.tube(0.0) shouldBe (0.0 plusOrMinus 1e-12) }
        withClue("asym(0)") { ShapingFuncs.asym(0.0) shouldBe (0.0 plusOrMinus 1e-12) }

        // stompBox is continuous at zero, from both sides.
        withClue("stompBox(-1e-9)") { ShapingFuncs.stompBox(-1e-9) shouldBe (0.0 plusOrMinus 1e-7) }
        withClue("stompBox(+1e-9)") { ShapingFuncs.stompBox(1e-9) shouldBe (0.0 plusOrMinus 1e-7) }

        // Landmarks of the distortion shapers: the bounds rows above cannot tell a shaper that reaches its rail
        // from one that stops short of it, or an identity region from a gentle curve.
        withClue("fastTanh(0)") { ShapingFuncs.fastTanh(0.0) shouldBe 0.0 }
        withClue("fastTanh saturates at +1") { ShapingFuncs.fastTanh(100.0) shouldBe (1.0 plusOrMinus 0.01) }
        withClue("fastTanh saturates at -1") { ShapingFuncs.fastTanh(-100.0) shouldBe (-1.0 plusOrMinus 0.01) }
        withClue("hardClip is the identity in range") { ShapingFuncs.hardClip(0.5) shouldBe 0.5 }
        withClue("hardClip(2) is the rail") { ShapingFuncs.hardClip(2.0) shouldBe 1.0 }
        withClue("hardClip(-2) is the rail") { ShapingFuncs.hardClip(-2.0) shouldBe -1.0 }
        withClue("cubicClip(0)") { ShapingFuncs.cubicClip(0.0) shouldBe 0.0 }
        withClue("cubicClip keeps the sign") { (ShapingFuncs.cubicClip(0.5) > 0.0) shouldBe true }
        withClue("diodeClip passes the positive side") { (ShapingFuncs.diodeClip(1.0) > 0.0) shouldBe true }
        withClue("sineFold(0)") { ShapingFuncs.sineFold(0.0) shouldBe (0.0 plusOrMinus 0.001) }
        withClue("sineFold(pi/2) is the peak") { ShapingFuncs.sineFold(PI / 2.0) shouldBe (1.0 plusOrMinus 0.001) }
        withClue("sineFold(pi) folds back to 0") { ShapingFuncs.sineFold(PI) shouldBe (0.0 plusOrMinus 0.001) }
    }

    "softCap is value- and slope-continuous at its threshold" {
        // Value: both sides within about 2 eps of 0.95.
        val eps = 1e-9

        withClue("value below") { ShapingFuncs.softCap(0.95 - eps) shouldBe (0.95 plusOrMinus 2e-9) }
        withClue("value above") { ShapingFuncs.softCap(0.95 + eps) shouldBe (0.95 plusOrMinus 2e-9) }

        // Slope, numerically: the identity's slope is 1, and so is tanh'(0).
        val h = 1e-6
        val slopeBelow = (ShapingFuncs.softCap(0.95) - ShapingFuncs.softCap(0.95 - h)) / h
        val slopeAbove = (ShapingFuncs.softCap(0.95 + h) - ShapingFuncs.softCap(0.95)) / h

        withClue("slope below") { slopeBelow shouldBe (1.0 plusOrMinus 1e-3) }
        withClue("slope above") { slopeAbove shouldBe (1.0 plusOrMinus 1e-3) }
    }
})

/** One shaping function, with the tolerance of its odd symmetry, or null for a shape asymmetric by design. */
private class Shape(val name: String, val oddTol: Double?, val fn: (Double) -> Double)
