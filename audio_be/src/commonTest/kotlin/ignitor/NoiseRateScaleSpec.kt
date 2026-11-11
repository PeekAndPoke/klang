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
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * **White's tilt and brown's leak keep their corner at every rate** (KLANG-00430, audit finding L3-10, the maintainer's
 * "fix and document" of 2026-11-11). Both are one-pole filters with a per-sample coefficient given at 48 kHz; the engine
 * scales the pole to the block's rate, `pole^(48000 / fs)`, so the time constant in SECONDS, and with it the corner, is
 * the one it has at 48 kHz (brown's corner was twice as high at 96 kHz before, white's tilt pivot too).
 *
 * The oracle replays the node's own draws (the same seed, one `nextDouble() * 2 - 1` per sample) through the
 * recurrence written out here with the scaled coefficient, at 44.1, 48 and 96 kHz. A node that kept the per-sample
 * coefficient differs at every rate but 48 kHz.
 */
class NoiseRateScaleSpec : StringSpec({

    val blockFrames = 128
    val blocks = 8

    fun render(node: Ignitor, sampleRate: Int): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = sampleRate,
            gateEndFrame = sampleRate,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(1),
        )
        val buffer = AudioBuffer(blockFrames)
        val out = DoubleArray(blocks * blockFrames)

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

    val rates = listOf(44100, 48000, 96000)

    "brown noise: the pole 1 / (1 + leak) is scaled to the rate, the same time constant in seconds (L3-10)" {
        for (leak in listOf(0.02, 0.3)) {
            for (rate in rates) {
                val got = render(Ignitors.brownNoise(rng = Random(7), leak = ConstantIgnitor(leak)), rate)
                val draws = Random(7)
                val pole = (1.0 / (1.0 + leak)).pow(48000.0 / rate)
                var out = 0.0

                for (i in got.indices) {
                    val white = draws.nextDouble() * 2.0 - 1.0

                    out = pole * out + (1.0 - pole) * white

                    withClue("leak $leak at $rate Hz, sample $i") { got[i] shouldBe (out plusOrMinus 1e-12) }
                }

                // The time constant in seconds: -1 / (fs ln(pole)), 1 / (48000 ln(1 + leak)) at every rate.
                val tau = -1.0 / (rate * ln(pole))

                withClue("leak $leak at $rate Hz: the time constant") { tau shouldBe (1.0 / (48000.0 * ln(1.0 + leak)) plusOrMinus 1e-12) }
            }
        }
    }

    "white noise's tilt: the one-pole's coefficient 0.15 at 48 kHz is scaled to the rate (L3-10)" {
        for (color in listOf(-0.7, 0.5)) {
            for (rate in rates) {
                val got = render(Ignitors.whiteNoise(rng = Random(9), color = ConstantIgnitor(color)), rate)
                val draws = Random(9)
                val g = 1.0 - 0.85.pow(48000.0 / rate)
                val darken = if (color < 0.0) -color else 0.0
                val brighten = if (color > 0.0) color else 0.0
                var lp = 0.0

                for (i in got.indices) {
                    val white = draws.nextDouble() * 2.0 - 1.0

                    lp += g * (white - lp)

                    val want = white + darken * (lp - white) + brighten * ((white - lp) - white)

                    withClue("color $color at $rate Hz, sample $i") { got[i] shouldBe (want plusOrMinus 1e-12) }
                }
            }
        }
    }
})
