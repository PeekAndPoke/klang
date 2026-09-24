/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitors
import io.peekandpoke.klang.audio_be.ignitor.ParamIgnitor
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.pitchEnvelopeModIgnitor
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.pitch.PitchEnvelopeRenderer
import kotlin.math.abs
import kotlin.math.pow

/**
 * [fastExp2] replaces `2.0.pow(x)` in the pitch paths' per-sample loops. Its contract: for any
 * `x` in `(-32, 32)` it matches `pow` to a relative error under [FAST_EXP2_MAX_REL_ERROR]; outside,
 * and for NaN and the infinities, it is `pow` itself.
 *
 * The bound is asserted against a dense sweep, not derived from the polynomial: the polynomial's
 * own coefficients and the octave table are the things under test.
 */
class FastExp2Spec : StringSpec({

    fun relErr(x: Double): Double = abs(fastExp2(x) / 2.0.pow(x) - 1.0)

    "matches pow to under FAST_EXP2_MAX_REL_ERROR across the whole fast range" {
        var worst = 0.0
        var worstAt = 0.0
        val steps = 640_000

        for (k in 1 until steps) {
            val x = -32.0 + 64.0 * k / steps
            val err = relErr(x)

            if (err > worst) {
                worst = err
                worstAt = x
            }
        }

        withClue("worst relative error at x = $worstAt") { worst shouldBeLessThan FAST_EXP2_MAX_REL_ERROR }
    }

    "every integer is its power of two exactly, and the fifth is exact to the bound" {
        // The polynomial's ends are pinned (p(0) = 1, p(1) = 2 in floating point), which the
        // envelope curve's endpoints rely on through fastExp(0) = 1.
        for (n in -32..31) {
            withClue("2^$n") { fastExp2(n.toDouble()) shouldBe 2.0.pow(n) }
        }

        // an equal-tempered fifth, the ratio a pitch path asks for most
        abs(fastExp2(7.0 / 12.0) - 1.4983070768766815) shouldBeLessThan FAST_EXP2_MAX_REL_ERROR
    }

    "no step at an integer boundary: the value just below n continues into the exact 2^n" {
        // A sweeping pitch crosses octaves; the value just below n comes from the polynomial's
        // upper end scaled by 2^(n-1), the value at n from its pinned lower end scaled by 2^n.
        for (n in -31..31) {
            val below = fastExp2(n - 1e-12)
            val at = fastExp2(n.toDouble())

            withClue("boundary at $n") { abs(below / at - 1.0) shouldBeLessThan 3 * FAST_EXP2_MAX_REL_ERROR }
        }
    }

    "outside the fast range and for non-finite input it is pow itself" {
        fastExp2(40.0) shouldBe 2.0.pow(40.0)
        fastExp2(-40.0) shouldBe 2.0.pow(-40.0)
        fastExp2(32.0) shouldBe 2.0.pow(32.0)
        fastExp2(-32.0) shouldBe 2.0.pow(-32.0)
        fastExp2(Double.NaN).isNaN() shouldBe true
        fastExp2(Double.POSITIVE_INFINITY) shouldBe Double.POSITIVE_INFINITY
        fastExp2(Double.NEGATIVE_INFINITY) shouldBe 0.0
    }

    "a rendered pitch envelope matches the per-sample pow law, through and past the settled point" {
        // The envelope renderer computes one ratio per sample while attack and decay run and one
        // per block once it has settled on the sustain. Both must be the same law; the reference
        // is the pow-based formula evaluated per sample, including the blocks after settling.
        val sampleRate = 48000
        val blockFrames = 128
        val attackSec = 0.02
        val decaySec = 0.05
        val semitones = 9.0
        val sustain = 0.25
        val mod = pitchEnvelopeModIgnitor(
            attackSec = ParamIgnitor("a", attackSec),
            decaySec = ParamIgnitor("d", decaySec),
            semitones = ParamIgnitor("amount", semitones),
            sustainLevel = ParamIgnitor("sustain", sustain),
        )
        val ctx = IgniteContext(
            sampleRate = sampleRate, voiceDurationFrames = sampleRate, gateEndFrame = sampleRate, releaseFrames = 0,
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val buf = AudioBuffer(blockFrames)
        val attackFrames = attackSec * sampleRate
        val decayFrames = decaySec * sampleRate
        var worst = 0.0
        var settledBlocks = 0

        // 0.2 s: attack and decay take 0.07 s, the rest is settled. Every other block renders a
        // window that starts 16 frames in (a voice onset mid-block), so the settled path must
        // honour the window like the per-sample loop does; the frames before it hold a sentinel.
        repeat(75) { b ->
            val offset = if (b % 2 == 0) 0 else 16

            buf.fill(-1.0)
            ctx.updateOffsetAndLength(offset, blockFrames - offset)
            ctx.voiceElapsedFrames = b * blockFrames + offset
            mod.generate(buf, 440.0, ctx)

            if (b * blockFrames + offset >= attackFrames + decayFrames) {
                settledBlocks++
            }

            for (i in 0 until offset) {
                withClue("block $b: frame $i before the window untouched") { buf[i] shouldBe -1.0 }
            }

            for (i in offset until blockFrames) {
                val relPos = (b * blockFrames + i).toDouble()
                var level = sustain

                if (relPos < attackFrames) {
                    level = relPos / attackFrames
                } else if (relPos < attackFrames + decayFrames) {
                    level = sustain + (1.0 - sustain) * (1.0 - (relPos - attackFrames) / decayFrames)
                }

                val expected = 2.0.pow(semitones * level / 12.0)

                worst = maxOf(worst, abs(buf[i] / expected - 1.0))
            }
        }

        withClue("settled blocks rendered") { (settledBlocks > 40) shouldBe true }
        withClue("worst relative deviation from the pow law") { worst shouldBeLessThan FAST_EXP2_MAX_REL_ERROR }
    }

    "the voice strip's pitch envelope matches the same law, writing and multiplying in" {
        // The strip renderer has the same settled shortcut on both of its paths: the first pitch
        // renderer writes the buffer, a later one multiplies into it. Both are driven here, the
        // second against a buffer prefilled with 2.0.
        val sampleRate = 48000
        val blockFrames = 128
        val pEnv = Voice.PitchEnvelope(
            attackFrames = 0.02 * sampleRate, decayFrames = 0.05 * sampleRate, releaseFrames = 0.0,
            semitones = 9.0, curve = 0.0, anchor = 0.25,
        )
        val renderer = PitchEnvelopeRenderer(pEnv, startFrame = 0.0)
        val ctx = BlockContext(
            audioBuffer = AudioBuffer(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
            sampleRate = sampleRate,
            startFrame = 0.0,
            endFrame = 100_000.0,
            gateEndFrame = 50_000.0,
            freqHz = 440.0,
            signal = Ignitors.silence(),
            signalCtx = IgniteContext(
                sampleRate = sampleRate, voiceDurationFrames = 50_000, gateEndFrame = 50_000, releaseFrames = 100,
                scratchBuffers = ScratchBuffers(blockFrames),
            ),
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
        )

        for (multiplyIn in listOf(false, true)) {
            var worst = 0.0

            repeat(75) { b ->
                val offset = if (b % 2 == 0) 0 else 16

                ctx.blockStart = (b * blockFrames).toDouble()
                ctx.updateOffsetAndLength(offset, blockFrames - offset)
                ctx.freqModBufferWritten = multiplyIn
                ctx.freqModBuffer.fill(2.0)
                renderer.render(ctx)

                for (i in 0 until offset) {
                    withClue("block $b: frame $i before the window untouched") { ctx.freqModBuffer[i] shouldBe 2.0 }
                }

                for (i in offset until blockFrames) {
                    val relPos = (b * blockFrames + i).toDouble()
                    var level = pEnv.anchor

                    if (relPos < pEnv.attackFrames) {
                        level = pEnv.anchor + (1.0 - pEnv.anchor) * (relPos / pEnv.attackFrames)
                    } else if (relPos < pEnv.attackFrames + pEnv.decayFrames) {
                        level = 1.0 - (1.0 - pEnv.anchor) * ((relPos - pEnv.attackFrames) / pEnv.decayFrames)
                    }

                    val expected = (if (multiplyIn) 2.0 else 1.0) * 2.0.pow(pEnv.semitones * level / 12.0)

                    worst = maxOf(worst, abs(ctx.freqModBuffer[i] / expected - 1.0))
                }
            }

            withClue("multiplyIn = $multiplyIn: worst relative deviation") { worst shouldBeLessThan FAST_EXP2_MAX_REL_ERROR }
            ctx.freqModBufferWritten shouldBe true
        }
    }
})
