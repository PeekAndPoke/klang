/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import kotlin.math.abs

/**
 * The bound on the reverb's tail parameter, and why it exists.
 *
 * This is the one place the engine's raw-by-default rule yields: past a normalized 1.0 the comb
 * feedback exceeds unity, and a Freeverb network above unity does **not** make a bigger room. Every
 * comb sample latches at the saturation rail, so the output is pure DC — measured AC-RMS 0.0, both
 * channels bit-identical — which the master DC blocker strips while the limiter ducks the rest of
 * the mix. The `ANTI_DENORMAL` bias alone ramps it there out of silence in ~4 s. There is no sound
 * above 1.0 to preserve, so [Reverb.normalizeRoomSize] bounds it.
 */
class ReverbStabilitySpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    /** Renders [blocks] blocks, feeding a burst only into the first one, and reports the peak. */
    fun render(reverb: Reverb, blocks: Int, burst: Double = 0.8): Double {
        val input = StereoBuffer(blockFrames)
        val output = StereoBuffer(blockFrames)
        var peak = 0.0

        for (b in 0 until blocks) {
            input.clear()
            if (b == 0) {
                for (i in 0 until blockFrames) {
                    input.left[i] = burst
                    input.right[i] = burst
                }
            }
            output.clear()
            reverb.process(input, output, blockFrames)

            for (i in 0 until blockFrames) {
                val l = abs(output.left[i])
                val r = abs(output.right[i])
                if (l > peak) {
                    peak = l
                }
                if (r > peak) {
                    peak = r
                }
                // The headline guarantee: never NaN, never infinite, whatever was authored.
                output.left[i].isFinite() shouldBe true
                output.right[i].isFinite() shouldBe true
            }
        }

        return peak
    }






    "the authored room-size scale is the one both buses share" {
        Reverb.normalizeRoomSize(5.0) shouldBe 0.5
        Reverb.normalizeRoomSize(3.0) shouldBe 0.3
        Reverb.normalizeRoomSize(0.0) shouldBe 0.0
        // Bounded at the top: past 1.0 there is no longer tail, only DC.
        Reverb.normalizeRoomSize(30.0) shouldBe 1.0
    }


    "the authored scale is bounded, so a Freeverb comb can never exceed unity" {
        // feedback = normalized * 0.28 + 0.7, so normalized must stay <= 1.0 for feedback <= 0.98.
        Reverb.normalizeRoomSize(30.0) shouldBe 1.0
        Reverb.normalizeRoomSize(-5.0) shouldBe 0.0
    }

    "an out-of-range room size stays finite and free of a DC pedestal" {
        val reverb = Reverb(sampleRate = sampleRate).also {
            it.roomSize = Reverb.normalizeRoomSize(30.0)
        }

        val input = StereoBuffer(blockFrames)
        val output = StereoBuffer(blockFrames)
        var sum = 0.0
        var count = 0

        for (b in 0 until 400) {
            input.clear()
            if (b == 0) {
                for (i in 0 until blockFrames) {
                    input.left[i] = 0.8
                    input.right[i] = 0.8
                }
            }
            output.clear()
            reverb.process(input, output, blockFrames)

            for (i in 0 until blockFrames) {
                output.left[i].isFinite() shouldBe true
                if (b >= 300) {
                    sum += output.left[i]
                    count++
                }
            }
        }

        // The failure this bound prevents: a constant offset instead of a decaying tail.
        abs(sum / count) shouldBeLessThan 0.01
    }
})
