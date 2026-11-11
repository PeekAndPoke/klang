/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.random.Random

/** This file's one seeded stream: every run draws the same, and successive builds still draw
 *  differently (as they did from the process-wide stream these calls used before). */
private val testRandom = Random(0x5EED)

class SampleIgnitorTest : StringSpec({

    val sampleRate = 44100

    fun createCtx(blockFrames: Int = 128) = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = sampleRate,
        gateEndFrame = sampleRate,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = testRandom,
    ).apply {
        updateOffsetAndLength(offset = 0, length = blockFrames)
        voiceElapsedFrames = 0
    }

    "one-shot playback produces interpolated samples" {
        // Simple ramp: 0.0, 0.25, 0.5, 0.75, 1.0
        val pcm = doubleArrayOf(0.0, 0.25, 0.5, 0.75, 1.0)

        val gen = SampleIgnitor(
            pcm = pcm,
            rate = 1.0,
            playhead = 0.0,
            loopStart = -1.0,
            loopEnd = -1.0,
            isLooping = false,
            stopFrame = Double.MAX_VALUE,
            sampleRate = 48000,
            rng = testRandom,
        )

        val buffer = AudioBuffer(5)
        val ctx = createCtx(5)
        ctx.updateLength(5)
        gen.generate(buffer, 440.0, ctx)

        // At rate=1.0, playhead lands exactly on integer indices
        buffer[0] shouldBe (0.0 plusOrMinus 0.001)
        buffer[1] shouldBe (0.25 plusOrMinus 0.001)
        buffer[2] shouldBe (0.5 plusOrMinus 0.001)
        buffer[3] shouldBe (0.75 plusOrMinus 0.001)
        // The last frame is played (audit finding L3-14: it used to read 0 here)
        buffer[4] shouldBe 1.0
    }

    "half-speed playback interpolates between samples" {
        val pcm = doubleArrayOf(0.0, 1.0, 0.0)

        val gen = SampleIgnitor(
            pcm = pcm,
            rate = 0.5,
            playhead = 0.0,
            loopStart = -1.0,
            loopEnd = -1.0,
            isLooping = false,
            stopFrame = Double.MAX_VALUE,
            sampleRate = 48000,
            rng = testRandom,
        )

        val buffer = AudioBuffer(4)
        val ctx = createCtx(4)
        ctx.updateLength(4)
        gen.generate(buffer, 440.0, ctx)

        // playhead: 0.0, 0.5, 1.0, 1.5; the cubic read halfway weighs the four frames around it -1/16, 9/16, 9/16, -1/16
        // (silence before frame 0 and after the last)
        buffer[0] shouldBe (0.0 plusOrMinus 0.001)      // pcm[0]
        buffer[1] shouldBe (0.5625 plusOrMinus 1e-12)   // between 0.0 and 1.0: 9/16
        buffer[2] shouldBe (1.0 plusOrMinus 0.001)      // pcm[1]
        buffer[3] shouldBe (0.5625 plusOrMinus 1e-12)   // between 1.0 and 0.0: 9/16
    }

    "looping wraps playhead correctly" {
        // 4 samples, loop from index 1 to 3
        val pcm = doubleArrayOf(0.0, 0.5, 1.0, 0.5)

        val gen = SampleIgnitor(
            pcm = pcm,
            rate = 1.0,
            playhead = 0.0,
            loopStart = 1.0,
            loopEnd = 3.0,
            isLooping = true,
            stopFrame = Double.MAX_VALUE,
            sampleRate = 48000,
            rng = testRandom,
        )

        val buffer = AudioBuffer(6)
        val ctx = createCtx(6)
        ctx.updateLength(6)
        gen.generate(buffer, 440.0, ctx)

        // playhead: 0, 1, 2, 3->wraps to 1, 2, 3->wraps to 1
        buffer[0] shouldBe (0.0 plusOrMinus 0.001)   // pcm[0]
        buffer[1] shouldBe (0.5 plusOrMinus 0.001)   // pcm[1]
        buffer[2] shouldBe (1.0 plusOrMinus 0.001)   // pcm[2]
        buffer[3] shouldBe (0.5 plusOrMinus 0.001)   // wraps to 1 -> pcm[1]
        buffer[4] shouldBe (1.0 plusOrMinus 0.001)   // pcm[2]
        buffer[5] shouldBe (0.5 plusOrMinus 0.001)   // wraps to 1 -> pcm[1]
    }

    "stopFrame truncates output to silence" {
        val pcm = doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0)

        val gen = SampleIgnitor(
            pcm = pcm,
            rate = 1.0,
            playhead = 0.0,
            loopStart = -1.0,
            loopEnd = -1.0,
            isLooping = false,
            stopFrame = 2.0,
            sampleRate = 48000,
            rng = testRandom,
        )

        val buffer = AudioBuffer(4)
        val ctx = createCtx(4)
        ctx.updateLength(4)
        gen.generate(buffer, 440.0, ctx)

        // First two samples play, rest are silenced by stopFrame
        buffer[0] shouldBe 1.0
        buffer[1] shouldBe 1.0
        buffer[2] shouldBe 0.0
        buffer[3] shouldBe 0.0
    }

    "phaseMod scales playback rate per sample" {
        val pcm = doubleArrayOf(0.0, 0.5, 1.0, 0.5, 0.0)

        val gen = SampleIgnitor(
            pcm = pcm,
            rate = 1.0,
            playhead = 0.0,
            loopStart = -1.0,
            loopEnd = -1.0,
            isLooping = false,
            stopFrame = Double.MAX_VALUE,
            sampleRate = 48000,
            rng = testRandom,
        )

        // Double speed via phaseMod
        val phaseMod = DoubleArray(4) { 2.0 }

        val buffer = AudioBuffer(4)
        val ctx = createCtx(4)
        ctx.updateLength(4)
        ctx.phaseMod = phaseMod
        gen.generate(buffer, 440.0, ctx)

        // playhead advances by rate*phaseMod = 1.0*2.0 = 2.0 per sample
        // playhead: 0.0, 2.0, 4.0(>=pcmMax), ...
        buffer[0] shouldBe (0.0 plusOrMinus 0.001)   // pcm[0]
        buffer[1] shouldBe (1.0 plusOrMinus 0.001)   // pcm[2]
        buffer[2] shouldBe 0.0                                  // past end
        buffer[3] shouldBe 0.0                                  // past end
    }

    "empty pcm returns silence" {
        val gen = SampleIgnitor(
            pcm = doubleArrayOf(),
            rate = 1.0,
            playhead = 0.0,
            loopStart = -1.0,
            loopEnd = -1.0,
            isLooping = false,
            stopFrame = Double.MAX_VALUE,
            sampleRate = 48000,
            rng = testRandom,
        )

        val buffer = AudioBuffer(4) { 999.0 } // prefill to detect changes
        val ctx = createCtx(4)
        ctx.updateLength(4)
        gen.generate(buffer, 440.0, ctx)

        // pcm.size - 1 = -1, so base >= pcmMax for all => silence
        buffer.all { it == 0.0 } shouldBe true
    }

    "negative playhead outputs silence" {
        val pcm = doubleArrayOf(0.5, 1.0, 0.5)

        val gen = SampleIgnitor(
            pcm = pcm,
            rate = 1.0,
            playhead = -2.0,
            loopStart = -1.0,
            loopEnd = -1.0,
            isLooping = false,
            stopFrame = Double.MAX_VALUE,
            sampleRate = 48000,
            rng = testRandom,
        )

        val buffer = AudioBuffer(4) { 999.0 }
        val ctx = createCtx(4)
        ctx.updateLength(4)
        gen.generate(buffer, 440.0, ctx)

        // playhead: -2.0, -1.0, 0.0, 1.0
        buffer[0] shouldBe 0.0    // negative -> silence
        buffer[1] shouldBe 0.0    // negative -> silence
        buffer[2] shouldBe (0.5 plusOrMinus 0.001)  // pcm[0]
        buffer[3] shouldBe (1.0 plusOrMinus 0.001)  // pcm[1]
    }

    "fractional negative playhead outputs silence, not extrapolation" {
        val pcm = doubleArrayOf(0.5, 1.0, 0.5)

        val gen = SampleIgnitor(
            pcm = pcm,
            rate = 0.5,
            playhead = -0.5,
            loopStart = -1.0,
            loopEnd = -1.0,
            isLooping = false,
            stopFrame = Double.MAX_VALUE,
            sampleRate = 48000,
            rng = testRandom,
        )

        val buffer = AudioBuffer(3) { 999.0 }
        val ctx = createCtx(3)
        ctx.updateLength(3)
        gen.generate(buffer, 440.0, ctx)

        // playhead: -0.5, 0.0, 0.5
        buffer[0] shouldBe 0.0    // fractional negative -> silence (not extrapolation)
        buffer[1] shouldBe (0.5 plusOrMinus 0.001)   // pcm[0]
        buffer[2] shouldBe (0.8125 plusOrMinus 1e-12)  // the cubic read between pcm[0] and pcm[1]: (0 * -1 + 0.5 * 9 + 1.0 * 9 - 0.5) / 16
    }

    "loop wrap handles large rate overshoots" {
        // Loop from 0 to 4, but rate=10 jumps way past loopEnd
        val pcm = doubleArrayOf(0.0, 0.25, 0.5, 0.75, 1.0, 0.0, 0.0, 0.0)

        val gen = SampleIgnitor(
            pcm = pcm,
            rate = 10.0,
            playhead = 0.0,
            loopStart = 0.0,
            loopEnd = 4.0,
            isLooping = true,
            stopFrame = Double.MAX_VALUE,
            sampleRate = 48000,
            rng = testRandom,
        )

        val buffer = AudioBuffer(3)
        val ctx = createCtx(3)
        ctx.updateLength(3)
        gen.generate(buffer, 440.0, ctx)

        // playhead: 0.0 -> advance 10 -> 10.0 -> wrap to 10%4=2.0 -> advance 10 -> 12.0 -> wrap to 12%4=0.0
        buffer[0] shouldBe (0.0 plusOrMinus 0.001)   // ph=0 -> pcm[0]
        buffer[1] shouldBe (0.5 plusOrMinus 0.001)   // ph=10 wraps to 2 -> pcm[2]
        buffer[2] shouldBe (0.0 plusOrMinus 0.001)   // ph=12 wraps to 0 -> pcm[0]
    }

    "freqHz is ignored" {
        val pcm = doubleArrayOf(0.0, 1.0, 0.0)

        val gen1 = SampleIgnitor(pcm = pcm, rate = 1.0, playhead = 0.0, loopStart = -1.0, loopEnd = -1.0, isLooping = false, stopFrame = Double.MAX_VALUE, sampleRate = 48000, rng = testRandom)
        val gen2 = SampleIgnitor(pcm = pcm, rate = 1.0, playhead = 0.0, loopStart = -1.0, loopEnd = -1.0, isLooping = false, stopFrame = Double.MAX_VALUE, sampleRate = 48000, rng = testRandom)

        val buf1 = AudioBuffer(3)
        val buf2 = AudioBuffer(3)
        val ctx1 = createCtx(3).apply { updateLength(3) }
        val ctx2 = createCtx(3).apply { updateLength(3) }

        gen1.generate(buf1, 440.0, ctx1)
        gen2.generate(buf2, 880.0, ctx2)

        // Output should be identical regardless of freqHz
        for (i in buf1.indices) {
            buf1[i] shouldBe buf2[i]
        }
    }
    // ── The cubic read (audit finding L3-05) and the sample's edges (L3-14), KLANG-00430 ─────────────────────────

    /** The cubic Lagrange weight of the frame at [k] (-1 to 2) for a playhead [t] past frame 0: the product form. */
    fun lagrangeWeight(k: Int, t: Double): Double {
        var w = 1.0

        for (j in -1..2) {
            if (j != k) {
                w *= (t - j) / (k - j)
            }
        }

        return w
    }

    fun oneShot(pcm: DoubleArray, rate: Double, stopFrame: Double = pcm.size.toDouble()) = SampleIgnitor(
        pcm = pcm, rate = rate, playhead = 0.0, loopStart = -1.0, loopEnd = -1.0, isLooping = false,
        stopFrame = stopFrame, sampleRate = 48000, rng = testRandom,
    )

    fun play(gen: SampleIgnitor, frames: Int): AudioBuffer {
        val buffer = AudioBuffer(frames)
        val ctx = createCtx(frames)

        ctx.updateLength(frames)
        gen.generate(buffer, 440.0, ctx)

        return buffer
    }

    "the read between frames is the four-point cubic Lagrange interpolation of the frames around it (L3-05)" {
        // Seeded random frames, read at a rate that lands on many fractions; the oracle is the Lagrange product formula
        // over the frames base - 1 .. base + 2, with silence outside the PCM.
        val rnd = Random(77)
        val pcm = DoubleArray(64) { rnd.nextDouble() * 2.0 - 1.0 }
        val rate = 0.613
        val frames = 100
        val out = play(oneShot(pcm, rate), frames)

        fun frame(k: Int): Double = if (k in pcm.indices) pcm[k] else 0.0

        for (i in 0 until frames) {
            val ph = i * rate
            val base = ph.toInt()
            val t = ph - base
            val want = if (ph >= pcm.size) 0.0 else (-1..2).sumOf { k -> lagrangeWeight(k, t) * frame(base + k) }

            withClue("frame $i, playhead $ph") {
                out[i] shouldBe (want plusOrMinus 1e-12)
            }
        }
    }

    "a one-shot plays its last frame, then falls to silence within one frame (L3-14)" {
        val ramp = DoubleArray(8) { 0.1 * (it + 1) }

        // Rate 1: every frame, the last one included, then silence from the stop frame on.
        val whole = play(oneShot(ramp, rate = 1.0), 12)

        for (i in 0 until 8) {
            withClue("frame $i") { whole[i] shouldBe (ramp[i] plusOrMinus 1e-12) }
        }

        for (i in 8 until 12) {
            withClue("frame $i, past the stop frame") { whole[i] shouldBe 0.0 }
        }

        // Rate 0.5: halfway past the last frame the read is the cubic towards silence (0.7, 0.8, then 0, 0).
        val half = play(oneShot(ramp, rate = 0.5), 18)

        half[14] shouldBe (0.8 plusOrMinus 1e-12)
        half[15] shouldBe ((-0.7 + 9.0 * 0.8) / 16.0 plusOrMinus 1e-12)
        half[16] shouldBe 0.0
    }

    "a loop over the whole sample has no zero at its seam and reads across it both ways (L3-14)" {
        // A constant PCM looped over all of it: every frame is the constant, at a rate that lands between frames at the
        // seam (the read used to write 0 for every frame past the last one, at every pass). The first frames read the
        // silence before frame 0 (the playhead has not wrapped yet), so the check starts at frame 2.
        val flat = DoubleArray(8) { 0.5 }
        val looped = SampleIgnitor(
            pcm = flat, rate = 0.75, playhead = 0.0, loopStart = 0.0, loopEnd = 8.0, isLooping = true,
            stopFrame = Double.MAX_VALUE, sampleRate = 48000, rng = testRandom,
        )
        val out = play(looped, 64)

        for (i in 2 until 64) {
            withClue("frame $i, playhead ${(i * 0.75) % 8.0}") { out[i] shouldBe (0.5 plusOrMinus 1e-12) }
        }

        // A ramp looped over all of it, at rate 0.5: past the last frame the read goes on from the loop's start, and after
        // the wrap it reaches back to the loop's end.
        val ramp = DoubleArray(8) { it.toDouble() }
        val rampLoop = SampleIgnitor(
            pcm = ramp, rate = 0.5, playhead = 0.0, loopStart = 0.0, loopEnd = 8.0, isLooping = true,
            stopFrame = Double.MAX_VALUE, sampleRate = 48000, rng = testRandom,
        )
        val r = play(rampLoop, 20)

        // playhead 7.5: frames 6, 7, then 0, 1
        r[15] shouldBe ((-6.0 + 9.0 * 7.0 + 9.0 * 0.0 - 1.0) / 16.0 plusOrMinus 1e-12)
        // playhead 0.5 after the wrap: frames 7 (the loop's end), 0, 1, 2
        r[17] shouldBe ((-7.0 + 9.0 * 0.0 + 9.0 * 1.0 - 2.0) / 16.0 plusOrMinus 1e-12)
    }
    "a playhead carried far below a wrapped loop's start reads the loop's own frames, in one step per frame (L3-14)" {
        // A soundfont-like loop of 100 frames at frame 60000, on a ramp PCM whose frame f holds f. The playhead wraps
        // once (60099.5, then 60100.5 -> 60000.5), then a negative phaseMod carries it to 100.25 in one sample. Its four
        // frames 99 .. 102 are read as the loop gives them: 60099, 60000, 60001, 60002 (each moved forward by 600 whole
        // loops). The read used to step one loop length at a time, 600 steps per frame on the audio thread.
        val pcm = DoubleArray(60200) { it.toDouble() }
        val gen = SampleIgnitor(
            pcm = pcm, rate = 1.0, playhead = 60099.5, loopStart = 60000.0, loopEnd = 60100.0, isLooping = true,
            stopFrame = Double.MAX_VALUE, sampleRate = 48000, rng = testRandom,
        )
        val buffer = AudioBuffer(3)
        val ctx = createCtx(3)

        ctx.updateLength(3)
        ctx.phaseMod = doubleArrayOf(1.0, 100.25 - 60000.5, 1.0)
        gen.generate(buffer, 440.0, ctx)

        // Right after the wrap, at 60000.5, the read takes the same four frames (59999 is the loop's last, 60099).
        val frames = doubleArrayOf(60099.0, 60000.0, 60001.0, 60002.0)

        fun cubic(t: Double): Double = (-1..2).sumOf { k -> lagrangeWeight(k, t) * frames[k + 1] }

        buffer[1] shouldBe (cubic(0.5) plusOrMinus 1e-9)
        buffer[2] shouldBe (cubic(0.25) plusOrMinus 1e-9)
    }
})
