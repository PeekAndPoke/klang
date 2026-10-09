/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.utils.FAST_EXP2_MAX_REL_ERROR
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/** This file's one seeded stream: every run draws the same, and successive builds still draw
 *  differently (as they did from the process-wide stream these calls used before). */
private val testRandom = Random(0x5EED)

/**
 * The pitch envelope mod node renders its ratio on `fastExp2` to the `pow` law. `utils/FastExp2Spec` pins `fastExp2`
 * itself.
 */
class PitchEnvelopeModFastExp2Spec : StringSpec({

    "a rendered pitch envelope matches the per-sample pow law, through and past the settled point" {
        // The envelope renderer computes one ratio per sample while attack and decay run and one
        // per block once it has settled on the sustain. Both must be the same law; the reference
        // is the pow-based formula evaluated per sample, including the blocks after settling.
        val sampleRate = 48000
        val blockFrames = 128
        val attack = 0.02
        val decay = 0.05
        val semitones = 9.0
        val sustain = 0.25
        val mod = pitchEnvelopeModIgnitor(
            attack = ParamIgnitor("a", attack),
            decay = ParamIgnitor("d", decay),
            semitones = ParamIgnitor("amount", semitones),
            sustain = ParamIgnitor("sustain", sustain),
            // Pinned Linear: this row is about the ratio's exp2 against pow, and its reference below
            // writes the linear stages out; the default curve is `ModEnvelopeDefaultCurveSpec`'s.
            attackCurve = AdsrCurve.Linear,
            decayCurve = AdsrCurve.Linear,
            releaseCurve = AdsrCurve.Linear,
        )
        val ctx = IgniteContext(
            sampleRate = sampleRate, voiceDurationFrames = sampleRate, gateEndFrame = sampleRate,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = testRandom,
        )
        val buf = AudioBuffer(blockFrames)
        val attackFrames = attack * sampleRate
        val decayFrames = decay * sampleRate
        var worst = 0.0
        var settledBlocks = 0

        // 0.2 s: attack and decay take 0.07 s, the rest is settled. Every other block renders a
        // window that starts 16 frames in (a voice onset mid-block), so the settled path must
        // honour the window like the per-sample loop does; the frames before it hold a sentinel.
        repeat(75) { b ->
            val offset = if (b % 2 == 0) 0 else 16

            buf.fill(-1.0)
            ctx.updateOffsetAndLength(offset = offset, length = blockFrames - offset)
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
})
