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
import kotlin.math.log10
import kotlin.math.sin

/**
 * The rows on one signal run it through the stereo door the engine calls, with a copy as the right channel: the
 * linked detector's `max(|L|, |R|)` over two equal channels is the signal's own level, so the left channel is the
 * mono render.
 */
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

        compressor.process(left = buffer, right = buffer.copyOf(), blockSize = 1000)

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

        compressor.process(left = buffer, right = buffer.copyOf(), blockSize = 1000)

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
        compressor.process(left = buffer, right = buffer.copyOf(), blockSize = 100)

        // Reset
        compressor.reset()

        // Process quiet signal - should not be affected by previous state
        val quietBuffer = AudioBuffer(100) { 0.01 }
        val original = quietBuffer.copyOf()
        compressor.process(left = quietBuffer, right = quietBuffer.copyOf(), blockSize = 100)

        // Bit for bit (KLANG-00428): a fresh compressor passes a level under the knee untouched, so must a reset one;
        // a follower state that survived the reset (the held reduction) would still be releasing here.
        for (i in quietBuffer.indices) {
            quietBuffer[i] shouldBe original[i]
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

        hardKnee.process(left = bufferHard, right = bufferHard.copyOf(), blockSize = 1000)
        softKnee.process(left = bufferSoft, right = bufferSoft.copyOf(), blockSize = 1000)

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
        // Engine follow-up 40: the cylinder compressor writes its five knobs through `knobArgs` and one
        // `applyKnobArgs`, the time constants computed once, where it called the five setters, each of which
        // computed them again. Both instances hear the same blocks and take the same knobs, one through the
        // setters (threshold, ratio, knee, attack, release, the order the cylinder stage used), the other through the
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
    "a very quiet input reads as silence, never quieter: after a tail at -120 dBFS the next hit is compressed as after silence" {
        // KLANG-00428, the audit's S-02: the follower converted any level above 1e-10 to dB, down to -200 dB, while
        // silence read -100 dB. Now a level at or under -100 dB reads as -100 dB. With the follower on the gain
        // reduction (L7b-04) a level under the knee asks for no reduction either way, so the floor shows only under a
        // threshold below -100 dB: there silence asks for reduction, and so must a -120 dBFS tail.
        val block = 128

        fun gains(quiet: Double): DoubleArray {
            val c = Compressor(sampleRate = sampleRate, thresholdDb = -110.0, kneeDb = 0.0)
            val loud = sampleRate / 2 / block
            val tail = 3 * sampleRate / 10 / block
            val hit = sampleRate / 20 / block
            val g = ArrayList<Double>()

            for (b in 0 until loud + tail + hit) {
                val level = when {
                    b < loud -> 0.9
                    b < loud + tail -> quiet
                    else -> 0.9
                }
                val l = AudioBuffer(block) { level }
                val r = AudioBuffer(block) { level }

                c.process(left = l, right = r, blockSize = block)

                if (b >= loud + tail) {
                    for (i in 0 until block) {
                        g.add(l[i] / level)
                    }
                }
            }

            return g.toDoubleArray()
        }

        val afterSilence = gains(quiet = 0.0)
        val afterQuiet = gains(quiet = 1e-6)

        for (i in afterSilence.indices) {
            withClue("frame $i of the hit") {
                afterQuiet[i] shouldBe afterSilence[i]
            }
        }
    }

    // ── L7b-04: the follower smooths the gain reduction (KLANG-00428) ────────────────────────────────────────────

    /** The gain (out over in) of every frame of a DC [levels] run, one level per stretch of [frames]. */
    fun dcGains(c: Compressor, stretches: List<Pair<Double, Int>>): DoubleArray {
        val total = stretches.sumOf { it.second }
        val x = DoubleArray(total)
        var at = 0

        for ((level, frames) in stretches) {
            for (k in 0 until frames) {
                x[at + k] = level
            }

            at += frames
        }

        val g = DoubleArray(total) { 1.0 }
        val block = 128
        var from = 0

        while (from < total) {
            val n = minOf(block, total - from)
            val l = AudioBuffer(n) { x[from + it] }
            val r = AudioBuffer(n) { x[from + it] }

            c.process(left = l, right = r, blockSize = n)

            for (k in 0 until n) {
                if (x[from + k] != 0.0) {
                    g[from + k] = l[k] / x[from + k]
                }
            }

            from += n
        }

        return g
    }

    "an onset after silence is reduced from its first sample: no dead time, at the defaults and for the limiter preset" {
        // The audit's L7b-04: smoothing the LEVEL let a 0 dBFS step after silence through untouched for 211 frames at
        // the defaults (188 for the limiter preset) at 48 kHz, until the level's envelope climbed into the knee.
        for ((label, c) in listOf(
            "defaults" to Compressor(sampleRate = 48000),
            "limiter preset" to Compressor(sampleRate = 48000, thresholdDb = -1.0, ratio = 20.0, kneeDb = 2.0, attackSeconds = 0.001),
        )) {
            val silence = 9600
            val g = dcGains(c, listOf(0.0 to silence, 1.0 to 4800))

            withClue("$label: the onset's first frame is reduced") {
                g[silence] shouldBeLessThan 1.0
            }
            withClue("$label: the first 2 ms all reduced, and more each frame") {
                for (k in 1 until 96) {
                    g[silence + k] shouldBeLessThan g[silence + k - 1]
                }
            }
            withClue("$label: six attack time constants in, within 0.1 dB of the steady reduction") {
                val attackFrames = if (label == "defaults") 144 else 48
                val steady = g[silence + 4799]

                abs(20.0 * log10(g[silence + 6 * attackFrames] / steady)) shouldBeLessThan 0.1
            }
        }
    }

    "the release depends on the reduction in force, not on what follows under the knee: into silence and into -30 dBFS alike" {
        // The audit's L7b-04: the level follower released in 24.5 ms into silence and in 128.9 ms into -30 dBFS (the
        // envelope fell toward what followed). On the reduction both ask for none, so both release alike, bit for bit.
        // Silence has no gain to read, so each silent run ends on one -30 dBFS frame, and the gain there is compared
        // with the -30 dBFS run's at the same frame.
        val loud = 24000
        val quiet = 0.0316227766
        val intoQuiet = dcGains(Compressor(sampleRate = 48000), listOf(1.0 to loud, quiet to 24000))

        withClue("the release is under way 10 ms after the loud part") {
            intoQuiet[loud + 480] shouldBeLessThan 0.95
        }

        for (probe in listOf(1, 480, 4800, 23999)) {
            val intoSilence = dcGains(Compressor(sampleRate = 48000), listOf(1.0 to loud, 0.0 to probe, quiet to 1))

            withClue("$probe frames after the loud part") {
                intoSilence[loud + probe] shouldBe intoQuiet[loud + probe]
            }
        }
    }

    "on a steady level the reduction is the curve's, as it was" {
        // The static curve at the defaults (-20 dB, 4:1, knee 6): -10 dBFS is 10 dB over, a 7.5 dB reduction.
        val g = dcGains(Compressor(sampleRate = 48000), listOf(0.316227766016838 to 48000))
        val db = 20.0 * log10(g[47999])

        db shouldBe ((-7.5) plusOrMinus 1e-9)
    }
})
