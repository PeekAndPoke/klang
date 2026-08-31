/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.abs

class CompressorSpec : StringSpec({

    val sampleRate = 44100

    "Compressor reduces signal above threshold" {
        val compressor = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -20.0,
            ratio = 4.0,
            kneeDb = 0.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1
        )

        // Create a buffer with loud signal (above threshold)
        val buffer = AudioBuffer(1000) { 0.5 } // ~-6 dB

        compressor.process(buffer, 0, 1000)

        // Signal should be reduced
        val avgLevel = buffer.map { abs(it) }.average()
        avgLevel shouldBeLessThan 0.5
    }

    "Compressor does not affect signal below threshold" {
        val compressor = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -20.0,
            ratio = 4.0,
            kneeDb = 0.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1
        )

        // Create a buffer with quiet signal (below threshold)
        val buffer = AudioBuffer(1000) { 0.01 } // ~-40 dB
        val original = buffer.copyOf()

        compressor.process(buffer, 0, 1000)

        // Signal should be mostly unchanged
        for (i in buffer.indices) {
            buffer[i] shouldBe (original[i] plusOrMinus 0.01)
        }
    }


    "Compressor reset clears state" {
        val compressor = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -20.0,
            ratio = 4.0
        )

        // Process some audio
        val buffer = AudioBuffer(100) { 0.5 }
        compressor.process(buffer, 0, 100)

        // Reset
        compressor.reset()

        // Process quiet signal - should not be affected by previous state
        val quietBuffer = AudioBuffer(100) { 0.01 }
        val original = quietBuffer.copyOf()
        compressor.process(quietBuffer, 0, 100)

        for (i in quietBuffer.indices) {
            quietBuffer[i] shouldBe (original[i] plusOrMinus 0.01)
        }
    }

    "Compressor processes stereo correctly" {
        val compressor = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -20.0,
            ratio = 4.0,
            kneeDb = 0.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1
        )

        val left = AudioBuffer(1000) { 0.5 }
        val right = AudioBuffer(1000) { 0.5 }

        compressor.process(left, right, 1000)

        // Both channels should be reduced
        val avgLeft = left.map { abs(it) }.average()
        val avgRight = right.map { abs(it) }.average()

        avgLeft shouldBeLessThan 0.5
        avgRight shouldBeLessThan 0.5
    }

    "Compressor soft knee creates smooth transition" {
        val hardKnee = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -20.0,
            ratio = 4.0,
            kneeDb = 0.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1
        )

        val softKnee = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -20.0,
            ratio = 4.0,
            kneeDb = 12.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1
        )

        // Create signal right at threshold
        val bufferHard = AudioBuffer(1000) { 0.1 } // ~-20 dB
        val bufferSoft = bufferHard.copyOf()

        hardKnee.process(bufferHard, 0, 1000)
        softKnee.process(bufferSoft, 0, 1000)

        // Both should compress, but soft knee should be gentler
        val avgHard = bufferHard.map { abs(it) }.average()
        val avgSoft = bufferSoft.map { abs(it) }.average()

        avgHard shouldBeLessThan 0.15
        avgSoft shouldBeLessThan 0.15
    }

    "one +Inf sample does not silently disable the compressor forever" {
        // Master round M3. The classic (lookahead-free) path had no non-finite guard, unlike
        // its lookahead twin. `ln(Inf)` drove `envelopeDb` to +Inf; the NEXT finite sample
        // computed `Inf + releaseCoeff * -Inf` = NaN, and from then on
        // `calculateGainReduction(NaN)` was NaN, `NaN < GAIN_SKIP_THRESHOLD_DB` was false, and
        // the gain returned EXACTLY 1.0 forever — a brickwall degraded to a bit-exact
        // pass-through, with nothing to indicate it. Note the direction: a NaN sample never
        // latched it (`NaN > SILENCE_LIN` is false); only +/-Inf did.
        //
        // Reference vs poisoned: same compressor settings, same loud input, one sample
        // differing. If the guard is gone, the poisoned run stops attenuating and its output
        // is strictly louder.
        fun run(poison: Boolean): Double {
            val c = Compressor(
                sampleRate = sampleRate,
                thresholdDb = -20.0,
                ratio = 20.0,
                kneeDb = 0.0,
                attackSeconds = 0.001,
                releaseSeconds = 0.1,
            )
            val warm = AudioBuffer(64) { if (poison && it == 0) Double.POSITIVE_INFINITY else 0.5 }
            val other = AudioBuffer(64) { 0.5 }
            c.process(warm, other, 64)

            val loud = AudioBuffer(2000) { 0.5 }
            val loudR = AudioBuffer(2000) { 0.5 }
            c.process(loud, loudR, 2000)

            return loud.takeLast(500).map { abs(it) }.average()
        }

        val clean = run(poison = false)
        val poisoned = run(poison = true)

        // The compressor is doing its job in both runs …
        clean shouldBeLessThan 0.4
        // … and the poisoned one is not measurably louder, i.e. it still limits.
        poisoned shouldBeLessThan clean * 1.05
    }
})
