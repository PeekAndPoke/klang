/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.ConstantIgnitor
import io.peekandpoke.klang.audio_be.ignitor.crush
import io.peekandpoke.klang.audio_be.ignitor.renderThroughNode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sin

/**
 * The crush law's properties on its host, the Ignitor `Crush` node (`CrushCore`, decision D1: FLOOR). Moved here
 * from the voice strip's `CrushRenderer` when the strip retired (phase 3 step 9); the law's oracle rows live in
 * `StripLawCoresSpec`. The strip's oversampled crush rows went with it: the engine has no oversampled crush
 * (`docs/tasks/oversampling-regions.md`).
 */
class CrushLawSpec : StringSpec({

    /** [input] through the crush node at [amount], one window. */
    fun crushed(input: DoubleArray, amount: Double): DoubleArray = renderThroughNode(input) { it.crush(ConstantIgnitor(amount)) }

    "the crush node with amount=0 passes signal through unchanged" {
        val original = doubleArrayOf(0.5, -0.3, 0.8, -0.9)
        val buffer = crushed(original, 0.0)
        for (i in buffer.indices) {
            buffer[i] shouldBe original[i]
        }
    }

    "the crush node bypasses for 0 < amount < 1 (sub-2-level grid)" {
        // Regression: previously `amount = 0.5` would activate with halfLevels ≈ 0.707
        // and produce outputs of ≈ ±1.414, a 3 dB gain bump. Now it must bypass.
        for (amount in listOf(0.01, 0.25, 0.5, 0.7, 0.99)) {
            val original = doubleArrayOf(0.1, -0.3, 0.6, -0.9, 0.99, -0.99)
            val buffer = crushed(original, amount)
            for (i in buffer.indices) {
                buffer[i] shouldBe original[i]
            }
        }
    }

    "the crush node never inflates magnitude for any amount in [1, 16]" {
        // Stronger property test: for any musically valid amount, no input sample
        // in [-1, 1] should produce an output with magnitude > 1.
        val inputs = doubleArrayOf(0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99, 1.0)
        val amounts = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.5, 8.0, 16.0)
        for (amount in amounts) {
            for (sign in intArrayOf(1, -1)) {
                val buffer = crushed(AudioBuffer(inputs.size) { i -> sign * inputs[i] }, amount)
                for (s in buffer) {
                    abs(s) shouldBeLessThan 1.0001
                }
            }
        }
    }

    "the crush node quantizes to discrete levels" {
        // amount=2 → 2^2 = 4 levels; halfLevels = 2. Grid step = 0.5.
        val buffer = crushed(doubleArrayOf(0.1, 0.4, 0.6, 0.9, -0.2, -0.7), 2.0)
        // Every output must be a multiple of 0.5.
        for (s in buffer) {
            val q = s * 2.0
            abs(q - round(q)) shouldBeLessThan 1e-6
        }
    }

    "the crush node: zero input → zero output" {
        // floor(0 * hl) / hl == 0, so the quantizer has a grid point at zero.
        val buffer = crushed(AudioBuffer(16) { 0.0 }, 2.0)
        for (s in buffer) s shouldBe 0.0
    }

    "the crush node has an asymmetric DC bias on a symmetric sine (crunch character)" {
        // Floor-based quantization biases every sample DOWN to the next grid step.
        // For a symmetric sine this produces an amplitude-modulated DC offset of
        // roughly −0.5 / halfLevels: this IS the audible character of the crusher.
        // (A symmetric round would produce mean ≈ 0, which we explicitly don't want.)
        val blockFrames = 4800
        val sampleRate = 48000.0
        val cyclesInBlock = 10
        val freq = sampleRate * cyclesInBlock / blockFrames  // = 100 Hz
        val input = AudioBuffer(blockFrames) { i ->
            (sin(2.0 * PI * freq * i / sampleRate) * 0.8)
        }
        val buffer = crushed(input, 1.0) // halfLevels = 1, expected bias ≈ −0.5

        val mean = buffer.map { it }.average()
        // Must be clearly non-zero: mean ≈ −0.5 with room for envelope/phase variation.
        (abs(mean) > 0.3) shouldBe true
    }

    "the crush node's continuous `levels`: fractional amount differs from integer amount" {
        // Drop of toInt() means amount=2.5 produces a different grid than amount=2.0.
        // The old implementation floored levels itself to 4 for both, giving identical output.
        val a = crushed(AudioBuffer(32) { 0.8 }, 2.0)
        val b = crushed(AudioBuffer(32) { 0.8 }, 2.5)

        // At amount=2.0 (halfLevels=2):    floor(0.8*2)/2 = floor(1.6)/2 = 1/2 = 0.5.
        // At amount=2.5 (halfLevels≈2.828): floor(0.8*2.828)/2.828 = floor(2.26)/2.828
        //                                   = 2/2.828 ≈ 0.707.
        // Outputs must differ, proving the amount axis is continuous, not step-quantized.
        (abs(a[0] - b[0]) > 0.05) shouldBe true
    }
})
