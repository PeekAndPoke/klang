/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.pitch

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext

/**
 * P2 guard (pitch-param unification, 2026-08-24): `accelerate` is SEMITONES over the voice
 * on the STRIP path too — `ratio = 2^((semitones/12)·progress)`. The ignitor path has its
 * own absolute row in `PitchModFactoriesSpec`; both exist because the law is applied in two
 * independent kernels (the `drivePerAnalog` lesson). The pre-P2 unit was octaves: without
 * the /12 this row reads 2^12 instead of 2 at the halfway point.
 */
class AccelerateSemitoneLawSpec : StringSpec({

    "strip path: accelerate(24 st) is exactly ratio 2.0 at half the voice" {
        val frames = 1024
        val renderer = AccelerateRenderer(
            accelerate = Voice.Accelerate(semitones = 24.0),
            startFrame = 0.0,
            endFrame = frames.toDouble(),
        )
        val noOp = object : Ignitor {
            override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) = Unit
        }
        val ctx = BlockContext(
            audioBuffer = AudioBuffer(frames),
            freqModBuffer = DoubleArray(frames),
            scratchBuffers = ScratchBuffers(frames),
            sampleRate = 48000,
            startFrame = 0.0,
            endFrame = frames.toDouble(),
            gateEndFrame = frames.toDouble(),
            freqHz = 220.0,
            signal = noOp,
            signalCtx = IgniteContext(
                sampleRate = 48000,
                voiceDurationFrames = frames,
                gateEndFrame = frames,
                releaseFrames = 0,
                scratchBuffers = ScratchBuffers(frames),
            ),
            cylinders = Cylinders(blockFrames = frames, sampleRate = 48000),
        )
        ctx.updateOffsetAndLength(0, frames)
        ctx.blockStart = 0.0
        renderer.render(ctx)

        // 24 semitones = 2 octaves over the whole voice; at progress 0.5 that is 2^1 = 2.0
        ctx.freqModBuffer[frames / 2] shouldBe (2.0 plusOrMinus 0.01)
        // and the end approaches 2^2 = 4
        ctx.freqModBuffer[frames - 1] shouldBe (4.0 plusOrMinus 0.02)
    }
})
