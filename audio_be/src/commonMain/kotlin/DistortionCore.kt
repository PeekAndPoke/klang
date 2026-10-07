/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.utils.nanGuard
import kotlin.math.pow

/**
 * THE voice strip's distort law (phase 3 step 4, decision D2 option A, 2026-09-25), hosted by the
 * Ignitor's fused `Distort` node (the one `classic()` builds), which adapts only its block contract. It
 * was one copy for two hosts until the strip's `DistortionRenderer` retired with the strip (phase 3
 * step 9); `StripLawCoresSpec` pins the law against an oracle written in the test.
 *
 * The law, per block:
 *  1. every sample is DRIVEN and SHAPED in one expression, `shape(x * drive)`, and NaN-guarded. With
 *     an oversampler, that expression runs on the UPSAMPLED stream, so the drive is applied INSIDE
 *     the oversampler (driving at the base rate first and upsampling after is not the same in the last
 *     bits: linear interpolation does not round the same way);
 *  2. the DC blocker, on every shape, not only the asymmetric ones: at extreme drive any input
 *     asymmetry rail-locks a symmetric shaper toward +-1 and leaves a DC bias;
 *  3. NO soft cap. The strip had its own downstream bounding stages, and `classic()` rebuilds the
 *     strip. The Ignitor `distort`/`shape` doors are a DIFFERENT law on purpose (D2 kept both): they
 *     build `Shape(Drive(...))`, drive at the base rate and cap their output. Whether the cap belongs
 *     here too is `docs/tasks/oversampling-regions.md`'s question, not this class's.
 *
 * The bypass (an amount at or below 0) belongs to the host, not here: the fused node is not built for a leaf amount at or below 0 and runs at unity drive for a
 * modulated one.
 *
 * @param shape the waveshaper, resolved by the host (a name through `parseDistortionShape`, an index
 *   knob through `distortionShapeAt`).
 * @param oversampleStages 2x stages of the oversampler, 0 for none (the plain path).
 */
internal class DistortionCore(
    private val shape: DistortionShape,
    oversampleStages: Int,
) {
    private val oversampler: Oversampler? =
        if (oversampleStages > 0) Oversampler(oversampleStages) else null

    private val dcBlocker = LowPassHighPassFilters.DcBlocker()

    /**
     * Drives, shapes and DC-blocks `buffer[offset, offset + length)` in place, at [drive] (a gain, see
     * [drive] in the companion for the amount conversion).
     */
    fun process(buffer: AudioBuffer, offset: Int, length: Int, drive: Double, scratchBuffers: ScratchBuffers) {
        val os = oversampler

        if (os != null) {
            // The round trip in two halves with the loop between them, inline: no closure per block and
            // no side channel for the drive (engine tidy-up step 2, audit B4.1).
            scratchBuffers.oversample(os.factor).use { work ->
                val count = os.upsample(source = buffer, offset = offset, length = length, work = work)
                val s = shape
                val d = drive

                // NaN-guard fused into the per-sample loop: see the Oversampler.upsample KDoc.
                for (i in 0 until count) {
                    work[i] = applyDistortionShape(s, work[i] * d).nanGuard()
                }

                os.decimate(work = work, target = buffer, offset = offset, length = length)
            }
        } else {
            val s = shape
            val d = drive
            val end = offset + length

            // NaN guard inline: a NaN escaping here would permanently corrupt the DC blocker's IIR state.
            for (i in offset until end) {
                buffer[i] = applyDistortionShape(s, buffer[i] * d).nanGuard()
            }
        }

        dcBlocker.process(buffer = buffer, offset = offset, length = length)
    }

    companion object {
        /**
         * The drive GAIN of a distort amount, `10^(amount * 1.2)`: exponential for a perceptually even
         * knob. The one conversion; the Ignitor `drive` door reads it too.
         */
        fun drive(amount: Double): Double = 10.0.pow(amount * 1.2)
    }
}
