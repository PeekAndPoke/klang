/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import kotlin.math.pow

/**
 * THE voice strip's distort law, one copy for two hosts (phase 3 step 4, decision D2 option A,
 * 2026-09-25): the strip's `DistortionRenderer` and the Ignitor's fused `Distort` node (the one
 * `classic()` builds) are thin hosts that only adapt their block contract. `ClassicStripParitySpec`
 * proves the two bit-identical.
 *
 * The law, per block:
 *  1. every sample is DRIVEN and SHAPED in one expression, `shape(x * drive)`, and NaN-guarded. With
 *     an oversampler, that expression runs on the UPSAMPLED stream, so the drive is applied INSIDE
 *     the oversampler (driving at the base rate first and upsampling after is not the same in the last
 *     bits: linear interpolation does not round the same way);
 *  2. the DC blocker, on every shape, not only the asymmetric ones: at extreme drive any input
 *     asymmetry rail-locks a symmetric shaper toward +-1 and leaves a DC bias;
 *  3. NO soft cap. The strip has its own downstream bounding stages, and `classic()` rebuilds the
 *     strip. The Ignitor `distort`/`shape` doors are a DIFFERENT law on purpose (D2 kept both): they
 *     build `Shape(Drive(...))`, drive at the base rate and cap their output. Whether the cap belongs
 *     here too is `docs/tasks/oversampling-regions.md`'s question, not this class's.
 *
 * The bypass (an amount at or below 0) belongs to the hosts, not here: the strip skips the stage for
 * the note, the fused node is not built for a leaf amount at or below 0 and runs at unity drive for a
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

    /** The drive of the block being processed, read by [oversampledTransform]. */
    private var blockDrive: Double = 1.0

    /**
     * The oversampled block transform, built ONCE per instance: a lambda that captured the block's
     * locals would be a new closure object on every block. It reads the shape and [blockDrive] into
     * locals first, so the expression per sample is the one a per-block lambda evaluated.
     */
    private val oversampledTransform: (AudioBuffer, Int) -> Unit = { work, count ->
        val s = shape
        val d = blockDrive

        // NaN-guard fused into the per-sample loop: see the Oversampler.process KDoc.
        for (i in 0 until count) {
            work[i] = applyDistortionShape(s, work[i] * d).nanGuard()
        }
    }

    /**
     * Drives, shapes and DC-blocks `buffer[offset, offset + length)` in place, at [drive] (a gain, see
     * [drive] in the companion for the amount conversion).
     */
    fun process(buffer: AudioBuffer, offset: Int, length: Int, drive: Double, scratchBuffers: ScratchBuffers) {
        val os = oversampler

        if (os != null) {
            blockDrive = drive
            os.process(buffer, offset, length, scratchBuffers, oversampledTransform)
        } else {
            val s = shape
            val d = drive
            val end = offset + length

            // NaN guard inline: a NaN escaping here would permanently corrupt the DC blocker's IIR state.
            for (i in offset until end) {
                buffer[i] = applyDistortionShape(s, buffer[i] * d).nanGuard()
            }
        }

        dcBlocker.process(buffer, offset, length)
    }

    companion object {
        /**
         * The drive GAIN of a distort amount, `10^(amount * 1.2)`: exponential for a perceptually even
         * knob. The one conversion; the Ignitor `drive` door reads it too.
         */
        fun drive(amount: Double): Double = 10.0.pow(amount * 1.2)
    }
}
