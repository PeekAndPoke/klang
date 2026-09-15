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
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.fastSin
import io.peekandpoke.klang.audio_be.wrapPhase
import kotlin.math.abs
import kotlin.math.acos
import kotlin.random.Random

/**
 * The single-lane drift sites (the mono sine, the single-voice saw, the sample player) step their
 * [AnalogDrift] lane once per block, at the block rate, and ramp the multiplier linearly across
 * the block (2026-09-15; a lane step per sample before). Each is held against a reference
 * accumulator that models exactly that: the lane built at [analogDriftStepRate] from the same
 * seed, [AnalogDrift.beginBlock] once per block, `m` from `blockStart` to `blockEnd` in `length`
 * steps. A lane stepped at the wrong rate, a ramp that does not advance, or a block that does not
 * start where the last one ended all show as a value mismatch.
 */
class AnalogDriftRampSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 400
    val analog = 20.0
    val freqHz = 220.0

    fun ctx(seed: Int): IgniteContext = IgniteContext(
        sampleRate = sampleRate, voiceDurationFrames = sampleRate * 10, gateEndFrame = sampleRate * 10, releaseFrames = 0,
        scratchBuffers = ScratchBuffers(blockFrames), random = Random(seed),
    )

    /**
     * [firstOffset] renders the first block as a partial window (a mid-block onset): the ramp
     * must then span that window's length. [withPhaseMod] installs a unity pitch-mod buffer, so
     * the consumers take their `phaseMod` loops with the same expected output.
     */
    fun render(sig: Ignitor, seed: Int, firstOffset: Int = 0, withPhaseMod: Boolean = false): DoubleArray {
        val c = ctx(seed)
        val buf = AudioBuffer(blockFrames)
        val out = DoubleArray(blocks * blockFrames)

        if (withPhaseMod) {
            c.phaseMod = DoubleArray(blockFrames) { 1.0 }
        }

        repeat(blocks) { b ->
            val offset = if (b == 0) firstOffset else 0

            c.updateOffsetAndLength(offset, blockFrames - offset)
            c.voiceElapsedFrames = b * blockFrames
            sig.generate(buf, freqHz, c)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buf[i]
            }
        }

        return out
    }

    /** The lane a site builds from [seed]: its first draws off the voice stream are the lane's. */
    fun lane(seed: Int): AnalogDrift = AnalogDrift(analog, analogDriftStepRate(sampleRate, blockFrames), Random(seed))

    /**
     * Drives [step] once per sample with the block ramp's multiplier, the reference for every site:
     * one lane step per block, `m` linear from the block's start to its end.
     */
    fun rampReference(seed: Int, firstOffset: Int = 0, step: (m: Double) -> Unit) {
        val d = lane(seed)

        repeat(blocks) { b ->
            d.beginBlock()

            val length = if (b == 0) blockFrames - firstOffset else blockFrames
            var m = d.blockStart
            val dm = (d.blockEnd - m) / length

            repeat(length) {
                step(m)
                m += dm
            }
        }
    }

    fun assertClose(actual: DoubleArray, expected: DoubleArray, clue: String) {
        var worst = 0.0
        var at = -1

        for (i in actual.indices) {
            val e = abs(actual[i] - expected[i])

            if (e > worst) {
                worst = e
                at = i
            }
        }

        withClue("$clue: worst deviation at sample $at") { worst shouldBeLessThan 1e-9 }
    }

    /** The sine's expected render: the reference ramp on a radian accumulator. */
    fun sineExpected(firstOffset: Int): DoubleArray {
        val expected = DoubleArray(blocks * blockFrames)
        val inc = TWO_PI * freqHz / sampleRate
        var phase = 0.0
        var i = firstOffset

        rampReference(3, firstOffset) { m ->
            expected[i++] = fastSin(phase)
            phase = (phase + inc * m).wrapPhase(TWO_PI)
        }

        return expected
    }

    /** The saw's expected render: resetSamples = 0 makes it a bare ramp of its phase, -1 + 2 · phase. */
    fun sawExpected(firstOffset: Int): DoubleArray {
        val expected = DoubleArray(blocks * blockFrames)
        val dt = freqHz / sampleRate
        var phase = 0.0
        var i = firstOffset

        rampReference(3, firstOffset) { m ->
            expected[i++] = -1.0 + 2.0 * phase
            phase = (phase + dt * m).wrapPhase(1.0)
        }

        return expected
    }

    "the mono sine's phase follows the block ramp, on both of its loops" {
        assertClose(render(Ignitors.sine(analog = ConstantIgnitor(analog)), seed = 3), sineExpected(0), "sine")
        assertClose(
            render(Ignitors.sine(analog = ConstantIgnitor(analog)), seed = 3, withPhaseMod = true),
            sineExpected(0), "sine under a unity phaseMod",
        )
    }

    "the single-voice saw's phase follows the block ramp, on both of its loops" {
        val saw = { Ignitors.sawtooth(analog = ConstantIgnitor(analog), resetSamples = 0.0) }

        assertClose(render(saw(), seed = 3), sawExpected(0), "saw")
        assertClose(render(saw(), seed = 3, withPhaseMod = true), sawExpected(0), "saw under a unity phaseMod")
    }

    "a mid-block onset ramps across the partial first window, not across a whole block" {
        // The first window is 91 samples long (a voice starting 37 frames into its block): the
        // ramp must reach the block's end value at the window's end, so the next block starts
        // where this one ended. A ramp sized by the block would leave a step at the note attack.
        val sine = Ignitors.sine(analog = ConstantIgnitor(analog))
        val saw = Ignitors.sawtooth(analog = ConstantIgnitor(analog), resetSamples = 0.0)

        assertClose(render(sine, seed = 3, firstOffset = 37), sineExpected(37), "sine, first window 37..128")
        assertClose(render(saw, seed = 3, firstOffset = 37), sawExpected(37), "saw, first window 37..128")
    }

    "the sample player's playhead follows the block ramp" {
        // A linear pcm ramp: the output IS the playhead (over the sample's length), so the drift
        // on the playback rate reads straight off it.
        val length = 100_000
        val pcm = DoubleArray(length) { it.toDouble() / length }
        val sig = SampleIgnitor(
            pcm = pcm, rate = 1.0, playhead = 0.0, loopStart = 0.0, loopEnd = 0.0, isLooping = false,
            stopFrame = length.toDouble(), analog = analog, sampleRate = sampleRate, blockFrames = blockFrames,
            rng = Random(3),
        )
        val out = render(sig, seed = 99)
        val expected = DoubleArray(out.size)
        var ph = 0.0
        var i = 0

        rampReference(3) { m ->
            expected[i++] = ph / length
            ph += m
        }

        assertClose(out, expected, "sample player")
    }

    "a one-voice supersine's increment wanders across blocks and only ramps within them" {
        // The sine stack has no bit-exact reference here (its phase comes off the phase pool), so
        // the increment is read off three consecutive samples, exact for a sinusoid:
        // cos(2π · inc) = (s[n+1] + s[n-1]) / (2 · s[n]). A stack whose lanes never advance holds
        // one increment for the whole render; one that steps per block without a ramp jumps at
        // every block edge by a whole block's change.
        val out = render(
            Ignitors.superSine(voices = ConstantIgnitor(1.0), analog = ConstantIgnitor(analog), rng = Random(1)),
            seed = 3,
        )
        val dt = freqHz / sampleRate
        val incs = DoubleArray(out.size)
        val valid = BooleanArray(out.size)

        for (n in 1 until out.size - 1) {
            // away from the zero crossings, where the estimate is well conditioned
            if (abs(out[n]) > 0.5) {
                incs[n] = acos(((out[n + 1] + out[n - 1]) / (2.0 * out[n])).coerceIn(-1.0, 1.0)) / TWO_PI
                valid[n] = true
            }
        }

        var lo = Double.MAX_VALUE
        var hi = -Double.MAX_VALUE
        var worstStep = 0.0

        for (n in 2 until out.size - 1) {
            if (!valid[n]) {
                continue
            }

            lo = minOf(lo, incs[n])
            hi = maxOf(hi, incs[n])

            if (valid[n - 1]) {
                worstStep = maxOf(worstStep, abs(incs[n] - incs[n - 1]))
            }
        }

        // The lane wanders: over 400 blocks (about 1 s) analog = 20 moves the increment by cents.
        withClue("increment wanders, spread ${(hi - lo) / dt} relative") { (hi - lo) / dt shouldBeGreaterThan 1e-5 }
        // And it ramps: a block's whole change spread over 128 samples, never a block-edge step.
        // The three-sample estimate has a ripple of its own on a ramping increment (a δ/2 · cot θ
        // term, amplified by 1/sin w in the acos): about 2e-4 relative here, measured 1.8e-4. A
        // block-edge step of a block's whole change measured 0.019, a hundred times that.
        withClue("largest sample-to-sample change ${worstStep / dt} relative") { worstStep / dt shouldBeLessThan 2e-3 }
    }

    "the lane's block step is small and continuous: a block starts where the last ended" {
        // A property of the lane alone (the consumers' ramps are pinned above): every block
        // starts at the previous block's end, and a block's whole change spread over its samples
        // is a small per-sample slope.
        val d = lane(3)
        var previous = d.blockEnd
        var worstJump = 0.0

        repeat(blocks) {
            d.beginBlock()

            val m0 = d.blockStart
            val dm = (d.blockEnd - m0) / blockFrames

            withClue("block starts where the last ended") { m0 shouldBe previous }

            worstJump = maxOf(worstJump, abs(dm))
            previous = d.blockEnd
        }

        // ±analog cents at three sigma is 20 · 5.8e-4 = 0.012 on the multiplier, walked over a
        // 50 ms fast layer: a block's change is a small part of that, a sample's a 128th of it.
        worstJump shouldBeLessThan 1e-5
    }
})
