/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers

/**
 * Pins [Oversampler.groupDelaySamples] against the oversampler itself: an impulse through a round trip with an
 * identity shaper, whose response at the base rate is the system's impulse response. Its centroid,
 * `sum(k * h[k]) / sum(h[k])`, is the group delay at DC, exactly, for any LTI response (the derivative of the phase
 * at 0); the round trip is LTI at the base rate (zero-stuffing, a filter and decimation by the same factor). The
 * Katalyst `distort` stage delays its dry by this, rounded (`KatalystDistortEffect.latencyFrames`).
 *
 * The figures the KDoc carried until 2026-10-09 (5.75 and 6.625 for 4x and 8x) were wrong; these rows are why the
 * corrected ones (5.5 and 6.25) stand.
 */
class OversamplerGroupDelaySpec : StringSpec({

    val blockFrames = 128

    /** The base-rate impulse response of a round trip with [stages] stages, impulse at [at]. */
    fun response(stages: Int, at: Int, blocks: Int): DoubleArray {
        val os = Oversampler(stages)
        val scratch = ScratchBuffers(blockFrames)
        val out = DoubleArray(blocks * blockFrames)

        for (b in 0 until blocks) {
            val block = AudioBuffer(blockFrames)
            val index = at - b * blockFrames

            if (index in 0 until blockFrames) {
                block[index] = 1.0
            }

            os.roundTrip(buffer = block, offset = 0, length = blockFrames, scratch = scratch) { _, _ -> }

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = block[i]
            }
        }

        return out
    }

    "the group delay is 1 + 6 * (1 - 2^-stages): 4.0, 5.5 and 6.25 input samples, and 0 without oversampling" {
        Oversampler.groupDelaySamples(0) shouldBe 0.0
        Oversampler.groupDelaySamples(-1) shouldBe 0.0
        Oversampler.groupDelaySamples(1) shouldBe 4.0
        Oversampler.groupDelaySamples(2) shouldBe 5.5
        Oversampler.groupDelaySamples(3) shouldBe 6.25
    }

    "the measured centroid of the round trip's impulse response IS the group delay, the impulse inside a block and across a seam" {
        for (stages in 1..3) {
            // 60: well inside the first block; 125: the response straddles the seam into the second block.
            for (at in listOf(60, 125)) {
                val h = response(stages = stages, at = at, blocks = 3)
                var sum = 0.0
                var moment = 0.0

                for (k in h.indices) {
                    sum += h[k]
                    moment += k * h[k]
                }

                withClue("stages $stages, impulse at $at: unity DC gain") { sum shouldBe (1.0 plusOrMinus 1e-12) }
                withClue("stages $stages, impulse at $at: the centroid") {
                    (moment / sum - at) shouldBe (Oversampler.groupDelaySamples(stages) plusOrMinus 1e-9)
                }
            }
        }
    }
})
