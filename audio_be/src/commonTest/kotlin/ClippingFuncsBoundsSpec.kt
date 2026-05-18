package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.beGreaterThanOrEqualTo
import io.kotest.matchers.doubles.beLessThanOrEqualTo
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.random.Random

/**
 * Property-style bounds tests for every shape in [ClippingFuncs].
 *
 * For each function: assert output is finite, bounded by a per-shape constant,
 * and (for symmetric shapes) that f(-x) ≈ -f(x). Inputs are a deterministic
 * mix of edge values and PRNG samples with a fixed seed for reproducibility.
 *
 * NaN/Inf inputs are deliberately NOT exercised here — those are caller
 * responsibility per the file-level KDoc.
 */
class ClippingFuncsBoundsSpec : StringSpec({

    val edgeInputs = listOf(
        -100.0, -10.0, -3.5, -3.0, -1.5, -1.0, -0.95, -0.5, -1e-6, 0.0,
        1e-6, 0.5, 0.95, 1.0, 1.5, 3.0, 3.5, 10.0, 100.0,
    )
    val rng = Random(seed = 0x4B4C4156L) // fixed for reproducibility
    val randomInputs = List(64) { rng.nextDouble(-50.0, 50.0) }
    val allInputs = edgeInputs + randomInputs

    fun assertFiniteAndBounded(fnName: String, inputs: List<Double>, bound: Double, fn: (Double) -> Double) {
        for (x in inputs) {
            val y = fn(x)
            withClue(fnName, x, y) { y.isFinite() shouldBe true }
            withClue(fnName, x, y) { abs(y) should beLessThanOrEqualTo(bound + 1e-9) }
        }
    }

    fun assertSymmetric(fnName: String, inputs: List<Double>, tol: Double = 1e-9, fn: (Double) -> Double) {
        for (x in inputs) {
            if (x == 0.0) continue
            val yp = fn(x)
            val yn = fn(-x)
            withClue("$fnName symmetry @ x=$x: f(x)=$yp, f(-x)=$yn") {
                yn shouldBe (-yp plusOrMinus tol)
            }
        }
    }

    "fastTanh is finite and bounded by 1.0" {
        assertFiniteAndBounded("fastTanh", allInputs, 1.0) { ClippingFuncs.fastTanh(it) }
    }
    "fastTanh is odd-symmetric" {
        assertSymmetric("fastTanh", allInputs, tol = 1e-12) { ClippingFuncs.fastTanh(it) }
    }

    "hardClip is finite and bounded by 1.0" {
        assertFiniteAndBounded("hardClip", allInputs, 1.0) { ClippingFuncs.hardClip(it) }
    }
    "hardClip is odd-symmetric" {
        assertSymmetric("hardClip", allInputs) { ClippingFuncs.hardClip(it) }
    }

    "softClip is finite and bounded by 1.0 for finite input" {
        assertFiniteAndBounded("softClip", allInputs, 1.0) { ClippingFuncs.softClip(it) }
    }
    "softClip is odd-symmetric" {
        assertSymmetric("softClip", allInputs) { ClippingFuncs.softClip(it) }
    }

    "cubicClip is finite and bounded by 1.0" {
        assertFiniteAndBounded("cubicClip", allInputs, 1.0) { ClippingFuncs.cubicClip(it) }
    }
    "cubicClip is odd-symmetric" {
        assertSymmetric("cubicClip", allInputs) { ClippingFuncs.cubicClip(it) }
    }

    "sineFold is finite and bounded by 1.0" {
        assertFiniteAndBounded("sineFold", allInputs, 1.0) { ClippingFuncs.sineFold(it) }
    }
    "sineFold is odd-symmetric" {
        assertSymmetric("sineFold", allInputs, tol = 1e-12) { ClippingFuncs.sineFold(it) }
    }

    "nativeTanh is finite and bounded by 1.0" {
        assertFiniteAndBounded("nativeTanh", allInputs, 1.0) { ClippingFuncs.nativeTanh(it) }
    }
    "nativeTanh is odd-symmetric" {
        assertSymmetric("nativeTanh", allInputs, tol = 1e-12) { ClippingFuncs.nativeTanh(it) }
    }

    "diodeClip is finite and bounded by 1.0" {
        // diodeClip is asymmetric — only finiteness + bound, no symmetry check.
        assertFiniteAndBounded("diodeClip", allInputs, 1.0) { ClippingFuncs.diodeClip(it) }
    }
    "diodeClip is asymmetric in the right direction (negative side attenuated)" {
        // For |x| ∈ (0, 3], the positive branch is fastTanh(x) and the negative
        // branch is fastTanh(x · 0.75) — so |diodeClip(-x)| should be < |diodeClip(x)|.
        for (x in listOf(0.5, 1.0, 1.5, 2.0)) {
            val pos = abs(ClippingFuncs.diodeClip(x))
            val neg = abs(ClippingFuncs.diodeClip(-x))
            withClue("diodeClip asymmetry @ x=$x: |f(x)|=$pos, |f(-x)|=$neg") {
                (pos > neg) shouldBe true
            }
        }
    }

    "chebyshevT3 is finite and bounded by 1.0" {
        assertFiniteAndBounded("chebyshevT3", allInputs, 1.0) { ClippingFuncs.chebyshevT3(it) }
    }
    "chebyshevT3 is odd-symmetric" {
        assertSymmetric("chebyshevT3", allInputs) { ClippingFuncs.chebyshevT3(it) }
    }

    "rectify is finite and bounded by 1.0" {
        assertFiniteAndBounded("rectify", allInputs, 1.0) { ClippingFuncs.rectify(it) }
    }
    "rectify output is always non-negative" {
        for (x in allInputs) {
            val y = ClippingFuncs.rectify(x)
            withClue("rectify @ x=$x: y=$y") { y should beGreaterThanOrEqualTo(0.0) }
        }
    }

    "expClip is finite and bounded by 1.0" {
        assertFiniteAndBounded("expClip", allInputs, 1.0) { ClippingFuncs.expClip(it) }
    }
    "expClip is odd-symmetric" {
        assertSymmetric("expClip", allInputs, tol = 1e-12) { ClippingFuncs.expClip(it) }
    }

    // ── softCap ────────────────────────────────────────────────────────────

    "softCap is identity for |x| <= 0.95" {
        for (x in listOf(-0.95, -0.5, -0.1, 0.0, 0.1, 0.5, 0.95)) {
            ClippingFuncs.softCap(x) shouldBe (x plusOrMinus 1e-12)
        }
    }

    "softCap is finite and bounded by 1.0 for any finite input" {
        assertFiniteAndBounded("softCap", allInputs, 1.0) { ClippingFuncs.softCap(it) }
    }

    "softCap is odd-symmetric" {
        assertSymmetric("softCap", allInputs, tol = 1e-12) { ClippingFuncs.softCap(it) }
    }

    "softCap value-continuity at the threshold" {
        val eps = 1e-9
        val below = ClippingFuncs.softCap(0.95 - eps)
        val above = ClippingFuncs.softCap(0.95 + eps)
        // Both should be very close to 0.95 — within ~2·eps.
        below shouldBe (0.95 plusOrMinus 2e-9)
        above shouldBe (0.95 plusOrMinus 2e-9)
    }

    "softCap slope-continuity at the threshold (numerical derivative)" {
        val eps = 1e-6
        val slopeBelow = (ClippingFuncs.softCap(0.95) - ClippingFuncs.softCap(0.95 - eps)) / eps
        val slopeAbove = (ClippingFuncs.softCap(0.95 + eps) - ClippingFuncs.softCap(0.95)) / eps
        // Identity slope = 1; tanh'(0) = 1. Both should be near 1.
        slopeBelow shouldBe (1.0 plusOrMinus 1e-3)
        slopeAbove shouldBe (1.0 plusOrMinus 1e-3)
    }
})

// Tiny helper so failure messages include the offending input.
private inline fun withClue(fnName: String, x: Double, y: Double, block: () -> Unit) {
    try {
        block()
    } catch (t: Throwable) {
        throw AssertionError("[$fnName] x=$x → y=$y :: ${t.message}", t)
    }
}

private inline fun withClue(clue: String, block: () -> Unit) {
    try {
        block()
    } catch (t: Throwable) {
        throw AssertionError("[$clue] ${t.message}", t)
    }
}
