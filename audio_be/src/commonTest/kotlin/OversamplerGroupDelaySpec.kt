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
 * Katalyst `distort` stage delays its dry by this, rounded (`KatalystDistortEffect.latencyFrames`), and a voice
 * declares it exactly (`BuiltIgnitor.latencySamples`).
 *
 * Since 2026-10-10 the kernel is an IIR half-band: its impulse response never quite ends, so the window is long (the
 * tail beyond it is far below the tolerance) and the delay is the LOW-FREQUENCY one; it rises toward the top
 * (`Oversampler`'s KDoc). The FIR's figures were 4.0, 5.5 and 6.25.
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

    "the group delay is 3.07, 4.40, 5.06 and 5.39 input samples at 2x to 16x (the KDoc's figures), and 0 without oversampling" {
        Oversampler.groupDelaySamples(0) shouldBe 0.0
        Oversampler.groupDelaySamples(-1) shouldBe 0.0
        Oversampler.groupDelaySamples(1) shouldBe (3.07 plusOrMinus 0.005)
        Oversampler.groupDelaySamples(2) shouldBe (4.40 plusOrMinus 0.005)
        Oversampler.groupDelaySamples(3) shouldBe (5.06 plusOrMinus 0.005)
        Oversampler.groupDelaySamples(4) shouldBe (5.39 plusOrMinus 0.005)
    }

    "the measured centroid of the round trip's impulse response IS the group delay, the impulse inside a block and across a seam" {
        for (stages in 1..4) {
            // 60: well inside the first block; 125: the response starts right at the seam into the second block.
            for (at in listOf(60, 125)) {
                val h = response(stages = stages, at = at, blocks = 12)
                var sum = 0.0
                var moment = 0.0

                for (k in h.indices) {
                    sum += h[k]
                    moment += k * h[k]
                }

                withClue("stages $stages, impulse at $at: unity DC gain") { sum shouldBe (1.0 plusOrMinus 1e-9) }
                withClue("stages $stages, impulse at $at: the centroid") {
                    (moment / sum - at) shouldBe (Oversampler.groupDelaySamples(stages) plusOrMinus 1e-9)
                }
            }
        }
    }
})
