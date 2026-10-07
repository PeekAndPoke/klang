/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.math.PI
import kotlin.math.nextDown
import kotlin.math.nextUp
import kotlin.math.sin

/**
 * The engine's output clip, [clipSample] and its loop [clipOutput], the real ones [MasterStage.process]
 * runs (2026-10-07, `docs/tasks-archive/2026-10/20261007-float-output.md`). The rule: in `[-1, 1]` a
 * sample passes untouched, above 1 it is 1.0, anything else -1.0. Untouched means NO quantisation: the
 * engine used to truncate every sample to 16 bits here, so a sample under one 16-bit step (1 / 32767,
 * about 3e-5) came out as 0. The rows below would see that step come back.
 */
class OutputClipSpec : StringSpec({

    "in [-1, 1] every sample passes bit for bit, including the boundaries and values under one 16-bit step" {
        val inRange = listOf(
            0.0,
            -0.0,
            1e-6,
            -1e-6,
            1e-300,
            -1e-300,
            0.123456789,
            -0.987654321,
            0.5,
            -0.5,
            1.0,
            -1.0,
            1.0.nextDown(),
            (-1.0).nextUp(),
        )

        for (sample in inRange) {
            withClue("sample $sample") {
                clipSample(sample).toRawBits() shouldBe sample.toRawBits()
            }
        }
    }

    "above 1 is exactly 1.0, below -1 is exactly -1.0" {
        val above = listOf(1.0.nextUp(), 1.0001, 1.5, 2.0, 100.0, 1e300)
        val below = listOf((-1.0).nextDown(), -1.0001, -1.5, -2.0, -100.0, -1e300)

        for (sample in above) {
            withClue("sample $sample") { clipSample(sample) shouldBe 1.0 }
        }

        for (sample in below) {
            withClue("sample $sample") { clipSample(sample) shouldBe -1.0 }
        }
    }

    "non-finite samples, today's behaviour pinned (NaN is a full-scale negative click)" {
        // NaN fails both `in [-1, 1]` and `> 1`, so it takes the last branch: -1.0. That is a click,
        // and it is pinned here as it is, not endorsed: through MasterStage.process no NaN reaches the
        // clip, because the house limiter's ring stores non-finite samples as 0.0 (MasterStageSpec
        // guards that end to end). Changing the NaN branch is a sound decision.
        clipSample(Double.NaN) shouldBe -1.0
        clipSample(Double.POSITIVE_INFINITY) shouldBe 1.0
        clipSample(Double.NEGATIVE_INFINITY) shouldBe -1.0
    }

    "the loop clips each channel into its own channel, and only the frames it is given" {
        val frames = 4
        val mix = StereoBuffer(frames + 1)
        val out = StereoBuffer(frames + 1).apply { fill(0.25) }

        doubleArrayOf(0.5, 1.5, -0.5, 1e-6, 0.75).copyInto(mix.left)
        doubleArrayOf(-1.5, -0.5, 1.5, -1e-6, -0.75).copyInto(mix.right)

        clipOutput(mix, frames, out)

        out.left.toList() shouldBe listOf(0.5, 1.0, -0.5, 1e-6, 0.25)
        out.right.toList() shouldBe listOf(-1.0, -0.5, 1.0, -1e-6, 0.25)

        withClue("the mix itself is untouched: the clip writes to the output only") {
            mix.left.toList() shouldBe listOf(0.5, 1.5, -0.5, 1e-6, 0.75)
            mix.right.toList() shouldBe listOf(-1.5, -0.5, 1.5, -1e-6, -0.75)
        }
    }

    "through MasterStage.process a value under one 16-bit step reaches the output" {
        // End to end through the real stage: a 1e-5 sine (about a third of a 16-bit step, so the old
        // 16-bit output was all zeros) must come out non-zero, and near its own amplitude (the DC
        // blocker's onset may overshoot a little, so the upper bound leaves room).
        val blockFrames = 64
        val master = MasterStage(sampleRate = 44100, blockFrames = blockFrames)
        val out = StereoBuffer(blockFrames)
        var peak = 0.0

        repeat(40) { block ->
            val mix = StereoBuffer(blockFrames)

            for (i in 0 until blockFrames) {
                val t = (block * blockFrames + i).toDouble() / 44100
                mix.left[i] = 1e-5 * sin(2.0 * PI * 440.0 * t)
            }

            master.process(mix, out)
            peak = maxOf(peak, out.peak())
        }

        withClue("peak $peak") {
            (peak > 5e-6) shouldBe true
            (peak < 2e-5) shouldBe true
        }
    }
})
