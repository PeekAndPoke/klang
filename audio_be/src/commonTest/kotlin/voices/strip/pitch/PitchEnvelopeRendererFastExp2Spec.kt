/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.pitch

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.utils.FAST_EXP2_MAX_REL_ERROR
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceLimits
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import kotlin.math.abs
import kotlin.math.pow

/**
 * The voice strip's pitch envelope renders its ratio on `fastExp2` to the `pow` law. `utils/FastExp2Spec` pins
 * `fastExp2` itself.
 */
class PitchEnvelopeRendererFastExp2Spec : StringSpec({

    "the voice strip's pitch envelope matches the same law, writing and multiplying in" {
        // The strip renderer has the same settled shortcut on both of its paths: the first pitch
        // renderer writes the buffer, a later one multiplies into it. Both are driven here, the
        // second against a buffer prefilled with 2.0.
        val sampleRate = 48000
        val blockFrames = 128
        // Linear stages keep the level oracle below plain; the curves are pinned in EnvelopeLawSpec.
        val lin = AdsrCurve.Linear
        val env = Voice.Envelope(
            attackFrames = 0.02 * sampleRate, decayFrames = 0.05 * sampleRate, sustainLevel = 0.25, releaseFrames = 0.0,
            attackCurve = lin, decayCurve = lin, releaseCurve = lin,
        )
        val pEnv = Voice.PitchEnvelope(semitones = 9.0, envelope = env)
        val renderer = PitchEnvelopeRenderer(pEnv)
        val ctx = BlockContext(
            audioBuffer = AudioBuffer(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
            sampleRate = sampleRate,
            limits = VoiceLimits(startFrame = 0.0, gateEndFrame = 50_000.0, endFrame = 100_000.0),
        )

        for (multiplyIn in listOf(false, true)) {
            var worst = 0.0

            repeat(75) { b ->
                val offset = if (b % 2 == 0) 0 else 16

                ctx.blockStart = (b * blockFrames).toDouble()
                ctx.updateOffsetAndLength(offset = offset, length = blockFrames - offset)
                ctx.freqModBufferWritten = multiplyIn
                ctx.freqModBuffer.fill(2.0)
                renderer.render(ctx)

                for (i in 0 until offset) {
                    withClue("block $b: frame $i before the window untouched") { ctx.freqModBuffer[i] shouldBe 2.0 }
                }

                for (i in offset until blockFrames) {
                    val relPos = (b * blockFrames + i).toDouble()
                    var level = env.sustainLevel

                    if (relPos < env.attackFrames) {
                        level = relPos / env.attackFrames
                    } else if (relPos < env.attackFrames + env.decayFrames) {
                        level = 1.0 - (1.0 - env.sustainLevel) * ((relPos - env.attackFrames) / env.decayFrames)
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
