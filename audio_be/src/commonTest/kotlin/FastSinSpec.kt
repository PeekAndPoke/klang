/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitors
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * [fastSin] replaces `kotlin.math.sin` in the oscillators' per-sample loops. Its contract: for a
 * phase in the engine's wrapped range `[0, 2π)` it matches `sin` to better than [FAST_SIN_MAX_ERROR]
 * (-200 dB), and the fold holds it for one more quarter period on either side of that range.
 *
 * The bound is asserted against a dense sweep, not derived from the polynomial: the polynomial's
 * own coefficients are the thing under test.
 */
class FastSinSpec : StringSpec({

    "matches sin to under FAST_SIN_MAX_ERROR across the whole wrapped period" {
        var worst = 0.0
        var worstAt = 0.0
        val steps = 400_000

        for (k in 0 until steps) {
            val x = TWO_PI * k / steps
            val err = abs(fastSin(x) - sin(x))

            if (err > worst) {
                worst = err
                worstAt = x
            }
        }

        withClue("worst error at phase $worstAt") { worst shouldBeLessThan FAST_SIN_MAX_ERROR }
    }

    "the landmarks are exact to the bound" {
        abs(fastSin(0.0)) shouldBeLessThan FAST_SIN_MAX_ERROR
        abs(fastSin(PI / 2) - 1.0) shouldBeLessThan FAST_SIN_MAX_ERROR
        abs(fastSin(PI)) shouldBeLessThan FAST_SIN_MAX_ERROR
        abs(fastSin(3 * PI / 2) + 1.0) shouldBeLessThan FAST_SIN_MAX_ERROR
        abs(fastSin(TWO_PI - 1e-9)) shouldBeLessThan 1e-8
    }

    "a quarter period past either end of the wrapped range still holds the bound" {
        // A phase that has not been wrapped yet (one increment past 2π, or a negative modulation
        // step) must not fall off a cliff: the fold covers [-π/2, 5π/2] exactly. Further out the
        // polynomial diverges, which is why every oscillator wraps before it evaluates.
        var worst = 0.0
        val steps = 100_000

        for (k in 0..steps) {
            val x = -PI / 2 + 3 * PI * k / steps
            worst = maxOf(worst, abs(fastSin(x) - sin(x)))
        }

        worst shouldBeLessThan FAST_SIN_MAX_ERROR
    }

    "NaN in, NaN out, like sin" {
        fastSin(Double.NaN).isNaN() shouldBe true
    }

    "the sine oscillator renders sin(phase) to the bound, so the swap is inaudible by construction" {
        // A plain sine at an awkward frequency for 4096 frames against a phase accumulator that
        // calls the library sin: the largest deviation must stay under the bound. Guards that the
        // ignitor really runs on the polynomial with the SAME phase, not a different accumulator.
        val sampleRate = 48000
        val blockFrames = 128
        val freq = 439.7
        val ctx = IgniteContext(
            sampleRate = sampleRate, voiceDurationFrames = 48000, gateEndFrame = 48000, releaseFrames = 0,
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val sine = Ignitors.sine()
        val buf = AudioBuffer(blockFrames)
        var phase = 0.0
        val inc = TWO_PI * freq / sampleRate
        var worst = 0.0

        for (b in 0 until 32) {
            ctx.updateOffsetAndLength(0, blockFrames)
            sine.generate(buf, freq, ctx)

            for (i in 0 until blockFrames) {
                worst = maxOf(worst, abs(buf[i] - sin(phase)))
                phase = (phase + inc).wrapPhase(TWO_PI)
            }

            ctx.voiceElapsedFrames += blockFrames
        }

        worst shouldBeLessThan FAST_SIN_MAX_ERROR
    }
})
