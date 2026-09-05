/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
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

    // ── The drain countdown (block-framing ledger D3, adopted for the reverb) ────────────────

    "drainSamplesUntilSilent: revolutions from the measured peak plus one slack revolution, in samples" {
        val rev = Reverb(sampleRate)

        // fb = roomSize x 0.28 + 0.7 = 0.84; ceil(ln(1e-5 / 1.0) / ln(0.84)) + 1 = 68 revolutions
        // of the longest comb (1617 + 23 stereo spread = 1640 samples at 44.1 kHz).
        rev.roomSize = 0.5
        rev.drainSamplesUntilSilent(peak = 1.0) shouldBe (68.0 * 1640.0)

        // roomFade OVERRIDES roomSize — the same override process() applies (fb 0.728 -> 38).
        rev.roomFade = 0.1
        rev.roomSize = 0.9
        rev.drainSamplesUntilSilent(peak = 1.0) shouldBe (38.0 * 1640.0)
        rev.roomFade = null

        // Proportional to content: a -60 dB peak needs 28 revolutions, not 68.
        rev.roomSize = 0.5
        rev.drainSamplesUntilSilent(peak = 0.001) shouldBe (28.0 * 1640.0)

        // At or below the threshold there is nothing to drain.
        rev.drainSamplesUntilSilent(peak = 0.00001) shouldBe 0.0
        rev.drainSamplesUntilSilent(peak = 0.0) shouldBe 0.0

        // Production-unreachable (normalizeRoomSize clamps to <= 1.0, so fb <= 0.98), but the
        // formula must never claim a supra-unity network drains.
        rev.roomSize = 2.0
        rev.drainSamplesUntilSilent(peak = 1.0) shouldBe Double.POSITIVE_INFINITY
    }

    "combPeakAbs measures the loudest comb cell on either channel" {
        val fresh = Reverb(sampleRate)
        fresh.combPeakAbs() shouldBe 0.0

        // A one-sample impulse writes exactly 1.0 into cell 0 of every LEFT comb (the feedback
        // contribution at that instant is the ~1e-18 anti-denormal bias, below double precision).
        val left = Reverb(sampleRate)
        val input = StereoBuffer(blockFrames)
        val output = StereoBuffer(blockFrames)
        input.left[0] = 1.0
        left.process(input, output, blockFrames)
        left.combPeakAbs() shouldBe 1.0

        // Right-only NEGATIVE content is seen too: both channels share the countdown, and the
        // scan measures magnitude.
        val right = Reverb(sampleRate)
        input.clear()
        input.right[0] = -1.0
        right.process(input, output, blockFrames)
        right.combPeakAbs() shouldBe 1.0
    }
})
