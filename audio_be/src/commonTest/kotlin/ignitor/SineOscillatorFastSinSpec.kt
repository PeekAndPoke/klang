/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.utils.FAST_SIN_MAX_ERROR
import io.peekandpoke.klang.audio_be.utils.TWO_PI
import io.peekandpoke.klang.audio_be.utils.wrapPhase
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** This file's one seeded stream: every run draws the same, and successive builds still draw
 *  differently (as they did from the process-wide stream these calls used before). */
private val testRandom = Random(0x5EED)

/**
 * The sine oscillator runs on `fastSin` with the same phase as a library-`sin` accumulator. `utils/FastSinSpec` pins
 * the polynomial itself.
 */
class SineOscillatorFastSinSpec : StringSpec({

    "the sine oscillator renders sin(phase) to the bound, so the swap is inaudible by construction" {
        // A plain sine at an awkward frequency for 4096 frames against a phase accumulator that
        // calls the library sin: the largest deviation must stay under the bound. Guards that the
        // ignitor really runs on the polynomial with the SAME phase, not a different accumulator.
        val sampleRate = 48000
        val blockFrames = 128
        val freq = 439.7
        val ctx = IgniteContext(
            sampleRate = sampleRate, voiceDurationFrames = 48000, gateEndFrame = 48000,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = testRandom,
        )
        val sine = Ignitors.sine()
        val buf = AudioBuffer(blockFrames)
        var phase = 0.0
        val inc = TWO_PI * freq / sampleRate
        var worst = 0.0

        for (b in 0 until 32) {
            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            sine.generate(buf, freq, ctx)

            for (i in 0 until blockFrames) {
                worst = maxOf(worst, abs(buf[i] - sin(phase)))
                phase = (phase + inc).wrapPhase(TWO_PI)
            }

            ctx.voiceElapsedFrames += blockFrames
        }

        worst shouldBeLessThan FAST_SIN_MAX_ERROR
    }
})
