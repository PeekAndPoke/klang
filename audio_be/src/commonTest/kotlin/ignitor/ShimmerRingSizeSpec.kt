/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * **The shimmer's grain ring is sized to what its grains read** (KLANG-00429, audit finding L5-03, option (b), the
 * maintainer's "size it to what it needs" of 2026-11-11). A grain at the rate `r` reads back `max(r, 1)` grain lengths
 * (150 ms each); the ring holds the largest of these over the pitch list, plus two frames, at most the 96,000 frames it
 * always had. At the default `[0, 7, 12]` at 48 kHz that is 14,402 frames (115 KB) where it was 96,000 (768 KB) at
 * every note.
 *
 * Two kinds of rows: the size law ([shimmerRingFrames]) and the sound: a shimmer with the sized ring renders what it
 * renders with the full ring, up to the rounding of its read positions (the ring's length enters the modulo of the
 * grains' start), and a ring a little short of the reach does not (the control: the comparison has teeth).
 */
class ShimmerRingSizeSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val grain = 7200

    "the ring holds the reach of the highest grain, or one grain length for a list at or below the note (L5-03)" {
        fun need(semitones: Double): Int = ceil(2.0.pow(semitones / 12.0) * grain).toInt() + 2

        shimmerRingFrames(pitches = listOf(0.0, 7.0, 12.0), sampleRate = sampleRate, maxFrames = 96_000) shouldBe 14_402
        shimmerRingFrames(pitches = listOf(0.0), sampleRate = sampleRate, maxFrames = 96_000) shouldBe 7_202
        shimmerRingFrames(pitches = listOf(-12.0, -5.0), sampleRate = sampleRate, maxFrames = 96_000) shouldBe 7_202
        shimmerRingFrames(pitches = emptyList(), sampleRate = sampleRate, maxFrames = 96_000) shouldBe 7_202
        shimmerRingFrames(pitches = listOf(-7.0, 19.0, 5.0), sampleRate = sampleRate, maxFrames = 96_000) shouldBe need(19.0)
        // A non-finite rate reads as 1.0, the node's own rule.
        shimmerRingFrames(pitches = listOf(Double.NaN, 5.0), sampleRate = sampleRate, maxFrames = 96_000) shouldBe need(5.0)
        shimmerRingFrames(pitches = listOf(Double.POSITIVE_INFINITY), sampleRate = sampleRate, maxFrames = 96_000) shouldBe 7_202
        // Inside a 2x region: the region's rate, the region's bound.
        shimmerRingFrames(pitches = listOf(0.0, 7.0, 12.0), sampleRate = 2 * sampleRate, maxFrames = 192_000) shouldBe 28_802
        // A reach at or past the bound keeps the ring of before; a huge one never overflows an Int.
        shimmerRingFrames(pitches = listOf(48.0), sampleRate = sampleRate, maxFrames = 96_000) shouldBe 96_000
        shimmerRingFrames(pitches = listOf(1200.0), sampleRate = sampleRate, maxFrames = 96_000) shouldBe 96_000
        shimmerRingFrames(pitches = listOf(12000.0), sampleRate = sampleRate, maxFrames = 96_000) shouldBe 96_000
    }

    /** A saw-like test tone (no rng), a shimmer over it at [ringFrames], 2 s in blocks. */
    fun render(pitches: List<Double>, ringFrames: Int, feedback: Double): DoubleArray {
        val tone = object : Ignitor {
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                for (i in 0 until ctx.length) {
                    val n = ctx.voiceElapsedFrames + i

                    buffer[ctx.offset + i] = 0.5 * sin(0.031 * n) + 0.3 * sin(0.173 * n) + 0.2 * sin(0.611 * n)
                }
            }
        }
        val node = tone.shimmer(
            wet = ConstantIgnitor(0.8),
            feedback = ConstantIgnitor(feedback),
            tone = ConstantIgnitor(6000.0),
            pitches = pitches,
            ringFrames = ringFrames,
        )
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = 2 * sampleRate,
            gateEndFrame = 2 * sampleRate,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(1),
        )
        val blocks = 2 * sampleRate / blockFrames
        val out = DoubleArray(blocks * blockFrames)
        val buffer = AudioBuffer(blockFrames)

        for (b in 0 until blocks) {
            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            ctx.voiceElapsedFrames = b * blockFrames
            node.generate(buffer, 220.0, ctx)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buffer[i]
            }
        }

        return out
    }

    fun maxDiff(a: DoubleArray, b: DoubleArray): Double {
        var m = 0.0

        for (i in a.indices) {
            m = maxOf(m, abs(a[i] - b[i]))
        }

        return m
    }

    "a sized ring renders what the full ring renders, up to the rounding of the read positions (L5-03)" {
        for (pitches in listOf(listOf(0.0, 7.0, 12.0), listOf(-12.0, 0.0, 19.0), listOf(-24.0), listOf(24.0, -5.0))) {
            for (feedback in listOf(0.0, 0.9)) {
                val sized = shimmerRingFrames(pitches = pitches, sampleRate = sampleRate, maxFrames = SHIMMER_RING_FRAMES)
                val diff = maxDiff(
                    render(pitches = pitches, ringFrames = sized, feedback = feedback),
                    render(pitches = pitches, ringFrames = SHIMMER_RING_FRAMES, feedback = feedback),
                )

                withClue("pitches $pitches, feedback $feedback, ring $sized: the largest difference $diff") {
                    diff shouldBeLessThan 1e-7
                }
            }
        }
    }

    "the control: a ring 300 frames short of the reach reads overwritten frames and sounds different" {
        val pitches = listOf(0.0, 7.0, 12.0)
        val short = shimmerRingFrames(pitches = pitches, sampleRate = sampleRate, maxFrames = SHIMMER_RING_FRAMES) - 300
        val diff = maxDiff(
            render(pitches = pitches, ringFrames = short, feedback = 0.0),
            render(pitches = pitches, ringFrames = SHIMMER_RING_FRAMES, feedback = 0.0),
        )

        diff shouldBeGreaterThan 1e-3
    }
})
