/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.filters.DEFAULT_DC_BLOCK_COEFF
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.utils.nanGuard
import kotlin.math.pow

/**
 * The one shaper core of the engine: drive, shape and DC blocker, with the oversampler around the shaping. Two nodes
 * host it and adapt only their block contract (engine tidy-up step 11, audit B2.2, 2026-10-08):
 *  - the fused `Distort` node (`FusedDistortIgnitor`, the distort stage `classic()` builds) drives with the amount's
 *    gain, and its output is the core's output: the voice strip's law (phase 3 step 4, decision D2 option A,
 *    2026-09-25), with no soft cap;
 *  - the `Shape` node (`ShapeIgnitor`, the Ignitor `shape` and `distort` doors) drives at 1.0, since its gain comes
 *    from an upstream `Drive` node, and bounds the core's output with its own soft cap (`ShapingFuncs.softCap`).
 *    `x * 1.0` is exact for every value, so the shared core is that node's law bit for bit.
 *
 * `StripLawCoresSpec` pins the law against an oracle written in the test; `OversamplerDecimatorParitySpec` pins both
 * nodes bit for bit, oversampled and at stage 0.
 *
 * The law, per block:
 *  1. every sample is DRIVEN and SHAPED in one expression, `shape(x * drive)`, and NaN-guarded. With
 *     an oversampler, that expression runs on the UPSAMPLED stream, so the drive is applied INSIDE
 *     the oversampler (driving at the base rate first and upsampling after is not the same in the last
 *     bits: linear interpolation does not round the same way);
 *  2. the DC blocker, on every shape, not only the asymmetric ones: at extreme drive any input
 *     asymmetry rail-locks a symmetric shaper toward +-1 and leaves a DC bias;
 *  3. NO soft cap here. The soft cap belongs to the `Shape` node. The two nodes are different laws on
 *     purpose (D2 kept both): the doors build `Shape(Drive(...))`, drive at the base rate and cap their
 *     output; `classic()` rebuilds the strip, which had its own downstream bounding stages. Whether the
 *     fused node should cap too is `docs/tasks/oversampling-regions.md`'s question, not this class's.
 *
 * The bypass belongs to the host, not here: the fused node is not built for a leaf amount at or below 0 and runs at
 * unity drive for a modulated one; the `Shape` node never bypasses.
 *
 * A third host, the Katalyst `distort` stage (`KatalystDistortEffect`), runs one core per channel of a bus at
 * the fused node's law, with a DC blocker of its own coefficient (see [dcBlockCoefficient]).
 *
 * @param shape the waveshaper, resolved by the host (a name through `parseDistortionShape`, an index
 *   knob through `distortionShapeAt`).
 * @param oversampleStages 2x stages of the oversampler, 0 for none (the plain path).
 * @param dcBlockCoefficient the DC blocker's pole. The default, `DEFAULT_DC_BLOCK_COEFF` (0.995, a knee
 *   near 35 Hz), is the voice's law and the two Ignitor nodes keep it. A bus carries the whole low end
 *   of a mix, where that knee takes about 2.5 dB off 40 Hz, so the Katalyst stage passes the house
 *   stage's 0.999 (near 7 Hz, `MasterStage`).
 */
internal class DistortionCore(
    private val shape: DistortionShape,
    oversampleStages: Int,
    dcBlockCoefficient: Double = DEFAULT_DC_BLOCK_COEFF,
) {
    private val oversampler: Oversampler? =
        if (oversampleStages > 0) Oversampler(oversampleStages) else null

    private val dcBlocker = LowPassHighPassFilters.DcBlocker(dcBlockCoefficient)

    /**
     * Back to a fresh core: the oversampler's filter history and the DC blocker's state cleared. For a
     * host that keeps its core across a hard cut (the Katalyst stage's `reset`); the voice builds a new
     * core per note and never calls it.
     */
    fun reset() {
        oversampler?.reset()
        dcBlocker.reset()
    }

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

    /**
     * [process] with a drive that MOVES across the block, from [driveFrom] to [driveTo], for a host whose drive
     * glides (the Katalyst stage's `amount`). The drive ramps linearly per sample of the stream the shaper sees (the
     * oversampled one with an oversampler), written from the END so the last sample carries [driveTo] exactly, and it
     * is applied where [process] applies its drive: on the shaper's input, inside the oversampler. So the
     * oversampler's history stays in the input's own domain, and a block that glides and a block that does not meet
     * without a seam (round 1 of `docs/tasks/katalyst-distort-stage.md`: a host that pre-multiplied the input at the
     * base rate instead left the history one domain off at every change, a click at both ends of each glide).
     */
    fun processRamped(
        buffer: AudioBuffer,
        offset: Int,
        length: Int,
        driveFrom: Double,
        driveTo: Double,
        scratchBuffers: ScratchBuffers,
    ) {
        val os = oversampler

        if (os != null) {
            scratchBuffers.oversample(os.factor).use { work ->
                val count = os.upsample(source = buffer, offset = offset, length = length, work = work)
                val s = shape
                val step = (driveTo - driveFrom) / count
                val last = count - 1

                // NaN-guard fused into the per-sample loop: see the Oversampler.upsample KDoc.
                for (i in 0 until count) {
                    work[i] = applyDistortionShape(s, work[i] * (driveTo - step * (last - i))).nanGuard()
                }

                os.decimate(work = work, target = buffer, offset = offset, length = length)
            }
        } else {
            val s = shape
            val step = (driveTo - driveFrom) / length
            val last = length - 1

            // NaN guard inline: a NaN escaping here would permanently corrupt the DC blocker's IIR state.
            for (i in 0 until length) {
                val k = offset + i

                buffer[k] = applyDistortionShape(s, buffer[k] * (driveTo - step * (last - i))).nanGuard()
            }
        }

        dcBlocker.process(buffer = buffer, offset = offset, length = length)
    }

    /**
     * True while the DC blocker still carries energy above [floor]: the offset it is removing decays on its own
     * after the input stops, about 23 ms per neper at the house pole. A host that is retired at the end of a ramp
     * (a chain swap) asks it so as not to cut that decay to 0 in one sample (round 1 of the Katalyst stage).
     */
    fun dcHoldsEnergy(floor: Double): Boolean = dcBlocker.holdsEnergy(floor)

    companion object {
        /**
         * The drive GAIN of a distort amount, `10^(amount * 1.2)`: exponential for a perceptually even
         * knob. The one conversion; the Ignitor `drive` door reads it too.
         */
        fun drive(amount: Double): Double = 10.0.pow(amount * 1.2)
    }
}
