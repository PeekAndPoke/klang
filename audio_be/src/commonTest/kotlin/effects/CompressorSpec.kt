/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.abs
import kotlin.math.sin

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

        compressor.process(buffer = buffer, offset = 0, length = 1000)

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

        compressor.process(buffer = buffer, offset = 0, length = 1000)

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
        compressor.process(buffer = buffer, offset = 0, length = 100)

        // Reset
        compressor.reset()

        // Process quiet signal - should not be affected by previous state
        val quietBuffer = AudioBuffer(100) { 0.01 }
        val original = quietBuffer.copyOf()
        compressor.process(buffer = quietBuffer, offset = 0, length = 100)

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

        compressor.process(left = left, right = right, blockSize = 1000)

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

        hardKnee.process(buffer = bufferHard, offset = 0, length = 1000)
        softKnee.process(buffer = bufferSoft, offset = 0, length = 1000)

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
            c.process(left = warm, right = other, blockSize = 64)

            val loud = AudioBuffer(2000) { 0.5 }
            val loudR = AudioBuffer(2000) { 0.5 }
            c.process(left = loud, right = loudR, blockSize = 2000)

            return loud.takeLast(500).map { abs(it) }.average()
        }

        val clean = run(poison = false)
        val poisoned = run(poison = true)

        // The compressor is doing its job in both runs …
        clean shouldBeLessThan 0.4
        // … and the poisoned one is not measurably louder, i.e. it still limits.
        poisoned shouldBeLessThan clean * 1.05
    }

    "applyKnobArgs leaves the instance where the five setters leave it, bit for bit" {
        // Engine follow-up 40: the orbit compressor writes its five knobs through `knobArgs` and one
        // `applyKnobArgs`, the time constants computed once, where it called the five setters, each of which
        // computed them again. Both instances hear the same blocks and take the same knobs, one through the
        // setters (threshold, ratio, knee, attack, release, the order the orbit stage used), the other through the
        // holder; every stored knob and every output sample must match bit for bit. The steps reach every guard:
        // non-finite knobs (each kept at its stored value), a ratio below 1 and a knee below 0 (coerced), a knee of
        // -0.0 (kept, `coerceAtLeast` does not touch it), an attack and a release below the 0.1 ms floor, a NaN
        // attack beside other changes (no smoothing re-split), and attacks that move the lookahead's box length.
        val nan = Double.NaN
        val inf = Double.POSITIVE_INFINITY
        val steps = listOf(
            doubleArrayOf(-18.0, 4.0, 6.0, 0.003, 0.1),
            doubleArrayOf(-26.5, 2.5, 0.5, 0.0021, 0.051),
            doubleArrayOf(-12.5, 5.5, 5.5, 0.0093, 0.187),
            doubleArrayOf(nan, 0.5, -3.0, 0.004, nan),
            doubleArrayOf(inf, 3.0, -0.0, nan, 0.07),
            doubleArrayOf(-15.0, -inf, 2.0, 0.00005, 0.00002),
            doubleArrayOf(-20.0, 1.0, 0.0, 0.0, 0.2),
            doubleArrayOf(nan, nan, nan, nan, nan),
            doubleArrayOf(-22.0, 8.0, 4.0, 0.06, 0.3),
            doubleArrayOf(-24.0, 6.0, 3.0, 0.0011, -inf),
        )
        val block = 64

        for (lookahead in listOf(0.0, 0.005)) {
            val bySetters = Compressor(sampleRate = sampleRate, lookaheadSeconds = lookahead)
            val byHolder = Compressor(sampleRate = sampleRate, lookaheadSeconds = lookahead)

            steps.forEachIndexed { n, knobs ->
                bySetters.thresholdDb = knobs[0]
                bySetters.ratio = knobs[1]
                bySetters.kneeDb = knobs[2]
                bySetters.attackSeconds = knobs[3]
                bySetters.releaseSeconds = knobs[4]

                knobs.copyInto(byHolder.knobArgs)
                byHolder.applyKnobArgs()

                val clue = "lookahead $lookahead, step $n"

                withClue("$clue, the stored knobs") {
                    byHolder.thresholdDb.toRawBits() shouldBe bySetters.thresholdDb.toRawBits()
                    byHolder.ratio.toRawBits() shouldBe bySetters.ratio.toRawBits()
                    byHolder.kneeDb.toRawBits() shouldBe bySetters.kneeDb.toRawBits()
                    byHolder.attackSeconds.toRawBits() shouldBe bySetters.attackSeconds.toRawBits()
                    byHolder.releaseSeconds.toRawBits() shouldBe bySetters.releaseSeconds.toRawBits()
                }

                // A loud block that rises and falls, so the attack, both releases and the knee all act.
                val start = n * block
                val l1 = AudioBuffer(block) {
                    0.9 * sin(0.031 * (start + it)) * (0.2 + 0.8 * abs(sin(0.0021 * (start + it))))
                }
                val r1 = AudioBuffer(block) { 0.7 * sin(0.047 * (start + it) + 1.0) }
                val l2 = l1.copyOf()
                val r2 = r1.copyOf()

                bySetters.process(left = l1, right = r1, blockSize = block)
                byHolder.process(left = l2, right = r2, blockSize = block)

                for (k in 0 until block) {
                    withClue("$clue, sample $k") {
                        l2[k].toRawBits() shouldBe l1[k].toRawBits()
                        r2[k].toRawBits() shouldBe r1[k].toRawBits()
                    }
                }
            }
        }
    }
})
