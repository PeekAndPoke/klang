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
 *     bits: the interpolating all-pass filters do not round the same way);
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

                // NaN-guarded in the shaping loop: see "NaN and state" in the Oversampler KDoc.
                shapeRun(buffer = work, from = 0, to = count, drive = drive)

                os.decimate(work = work, target = buffer, offset = offset, length = length)
            }
        } else {
            // NaN-guarded in the shaping loop: a NaN escaping here would permanently corrupt the DC blocker's IIR state.
            shapeRun(buffer = buffer, from = offset, to = offset + length, drive = drive)
        }

        dcBlocker.process(buffer = buffer, offset = offset, length = length)
    }

    /**
     * [process] with a drive that MOVES across the block, from [driveFrom] to [driveTo], for a host whose drive
     * glides (the Katalyst stage's `amount`). The drive ramps linearly per sample of the stream the shaper sees (the
     * oversampled one with an oversampler), written from the END so the last sample carries [driveTo] exactly, and it
     * is applied where [process] applies its drive: on the shaper's input, inside the oversampler. So the
     * oversampler's history stays in the input's own domain, and a block that glides and a block that does not meet
     * without a seam (round 1 of `docs/tasks-archive/2026-10/20261009-katalyst-distort-stage.md`: a host that pre-multiplied the input at the
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

                // NaN-guarded in the shaping loop: see "NaN and state" in the Oversampler KDoc.
                shapeRunRamped(buffer = work, offset = 0, length = count, driveFrom = driveFrom, driveTo = driveTo)

                os.decimate(work = work, target = buffer, offset = offset, length = length)
            }
        } else {
            // NaN-guarded in the shaping loop: a NaN escaping here would permanently corrupt the DC blocker's IIR state.
            shapeRunRamped(buffer = buffer, offset = offset, length = length, driveFrom = driveFrom, driveTo = driveTo)
        }

        dcBlocker.process(buffer = buffer, offset = offset, length = length)
    }

    /**
     * Shapes `buffer[from, to)` in place at the constant [drive], NaN-guarded: `shape(x * drive)` per sample, the
     * table of [applyDistortionShape] (pinned shape by shape against it by `OversamplerDecimatorParitySpec`).
     *
     * **One loop per shape, the `when` outside the loop (V8; engine follow-up item 9, 2026-10-10), kept for SPEED.**
     * Do not fold this back into one loop. A loop over [applyDistortionShape] boxes every shaped sample that is not a
     * small integer into a heap number on V8 (Kotlin/JS leaves the `when`'s result unassigned in its `default` arm,
     * so V8 carries it tagged from sample to sample): about 2 KB per block without oversampling, 8 KB at 4x, 33 KB at
     * 16x (`tube`, production bundle), on every `Shape` and fused `Distort` node. The cheap statement form (an
     * initialized `var` assigned in a statement `when`, `audio/ref/performance.md`) removes those boxes too, but these
     * switch-free loops run 12 to 25 percent faster again than it; folding back to it gives that up with no test to
     * say so (the bits are the same either way). This KDoc is the guard; the measurement is in
     * `docs/tasks/engine-follow-ups.md`.
     */
    private fun shapeRun(buffer: AudioBuffer, from: Int, to: Int, drive: Double) = when (shape) {
        DistortionShape.SOFT -> shapeLoop(buffer = buffer, from = from, to = to, drive = drive) { ShapingFuncs.fastTanh(it) }
        DistortionShape.HARD -> shapeLoop(buffer = buffer, from = from, to = to, drive = drive) { ShapingFuncs.hardClip(it) }
        DistortionShape.GENTLE -> shapeLoop(buffer = buffer, from = from, to = to, drive = drive) { ShapingFuncs.softClip(it) * 2.0 }
        DistortionShape.CUBIC -> shapeLoop(buffer = buffer, from = from, to = to, drive = drive) { ShapingFuncs.cubicClip(it) }
        DistortionShape.DIODE -> shapeLoop(buffer = buffer, from = from, to = to, drive = drive) { ShapingFuncs.diodeClip(it) }
        DistortionShape.FOLD -> shapeLoop(buffer = buffer, from = from, to = to, drive = drive) { ShapingFuncs.sineFold(it) }
        DistortionShape.CHEBYSHEV -> shapeLoop(buffer = buffer, from = from, to = to, drive = drive) { ShapingFuncs.chebyshevT3(it) }
        DistortionShape.RECTIFY -> shapeLoop(buffer = buffer, from = from, to = to, drive = drive) { ShapingFuncs.rectify(it) }
        DistortionShape.EXP -> shapeLoop(buffer = buffer, from = from, to = to, drive = drive) { ShapingFuncs.expClip(it) }
        DistortionShape.SOFT_SAT -> shapeLoop(buffer = buffer, from = from, to = to, drive = drive) { ShapingFuncs.softSat(it) }
        DistortionShape.TUBE -> shapeLoop(buffer = buffer, from = from, to = to, drive = drive) { ShapingFuncs.tube(it) }
        DistortionShape.LINEAR_FOLD -> shapeLoop(buffer = buffer, from = from, to = to, drive = drive) { ShapingFuncs.linearFold(it) }
        DistortionShape.ZERO_SQUARE -> shapeLoop(buffer = buffer, from = from, to = to, drive = drive) { ShapingFuncs.zeroSquare(it) }
        DistortionShape.SINE_SHAPER -> shapeLoop(buffer = buffer, from = from, to = to, drive = drive) { ShapingFuncs.sineShaper(it) }
        DistortionShape.ASYM -> shapeLoop(buffer = buffer, from = from, to = to, drive = drive) { ShapingFuncs.asym(it) }
        DistortionShape.STOMP_BOX -> shapeLoop(buffer = buffer, from = from, to = to, drive = drive) { ShapingFuncs.stompBox(it) }
    }

    /**
     * [shapeRun] with the drive ramped across `buffer[offset, offset + length)` from [driveFrom] to [driveTo], written
     * from the END (see [processRamped]). The same table and the same reason for one loop per shape;
     * `OversamplerDecimatorParitySpec` pins this table against [shapeRun]'s, shape by shape (a ramp to the same drive).
     */
    private fun shapeRunRamped(buffer: AudioBuffer, offset: Int, length: Int, driveFrom: Double, driveTo: Double) = when (shape) {
        DistortionShape.SOFT -> shapeLoopRamped(buffer = buffer, offset = offset, length = length, driveFrom = driveFrom, driveTo = driveTo) { ShapingFuncs.fastTanh(it) }
        DistortionShape.HARD -> shapeLoopRamped(buffer = buffer, offset = offset, length = length, driveFrom = driveFrom, driveTo = driveTo) { ShapingFuncs.hardClip(it) }
        DistortionShape.GENTLE -> shapeLoopRamped(buffer = buffer, offset = offset, length = length, driveFrom = driveFrom, driveTo = driveTo) { ShapingFuncs.softClip(it) * 2.0 }
        DistortionShape.CUBIC -> shapeLoopRamped(buffer = buffer, offset = offset, length = length, driveFrom = driveFrom, driveTo = driveTo) { ShapingFuncs.cubicClip(it) }
        DistortionShape.DIODE -> shapeLoopRamped(buffer = buffer, offset = offset, length = length, driveFrom = driveFrom, driveTo = driveTo) { ShapingFuncs.diodeClip(it) }
        DistortionShape.FOLD -> shapeLoopRamped(buffer = buffer, offset = offset, length = length, driveFrom = driveFrom, driveTo = driveTo) { ShapingFuncs.sineFold(it) }
        DistortionShape.CHEBYSHEV -> shapeLoopRamped(buffer = buffer, offset = offset, length = length, driveFrom = driveFrom, driveTo = driveTo) { ShapingFuncs.chebyshevT3(it) }
        DistortionShape.RECTIFY -> shapeLoopRamped(buffer = buffer, offset = offset, length = length, driveFrom = driveFrom, driveTo = driveTo) { ShapingFuncs.rectify(it) }
        DistortionShape.EXP -> shapeLoopRamped(buffer = buffer, offset = offset, length = length, driveFrom = driveFrom, driveTo = driveTo) { ShapingFuncs.expClip(it) }
        DistortionShape.SOFT_SAT -> shapeLoopRamped(buffer = buffer, offset = offset, length = length, driveFrom = driveFrom, driveTo = driveTo) { ShapingFuncs.softSat(it) }
        DistortionShape.TUBE -> shapeLoopRamped(buffer = buffer, offset = offset, length = length, driveFrom = driveFrom, driveTo = driveTo) { ShapingFuncs.tube(it) }
        DistortionShape.LINEAR_FOLD -> shapeLoopRamped(buffer = buffer, offset = offset, length = length, driveFrom = driveFrom, driveTo = driveTo) { ShapingFuncs.linearFold(it) }
        DistortionShape.ZERO_SQUARE -> shapeLoopRamped(buffer = buffer, offset = offset, length = length, driveFrom = driveFrom, driveTo = driveTo) { ShapingFuncs.zeroSquare(it) }
        DistortionShape.SINE_SHAPER -> shapeLoopRamped(buffer = buffer, offset = offset, length = length, driveFrom = driveFrom, driveTo = driveTo) { ShapingFuncs.sineShaper(it) }
        DistortionShape.ASYM -> shapeLoopRamped(buffer = buffer, offset = offset, length = length, driveFrom = driveFrom, driveTo = driveTo) { ShapingFuncs.asym(it) }
        DistortionShape.STOMP_BOX -> shapeLoopRamped(buffer = buffer, offset = offset, length = length, driveFrom = driveFrom, driveTo = driveTo) { ShapingFuncs.stompBox(it) }
    }

    /** The constant-drive loop of [shapeRun], for one shape [f]. */
    private inline fun shapeLoop(buffer: AudioBuffer, from: Int, to: Int, drive: Double, f: (Double) -> Double) {
        for (i in from until to) {
            buffer[i] = f(buffer[i] * drive).nanGuard()
        }
    }

    /** The ramped loop of [shapeRunRamped], for one shape [f]: the last sample carries [driveTo] exactly. */
    private inline fun shapeLoopRamped(
        buffer: AudioBuffer,
        offset: Int,
        length: Int,
        driveFrom: Double,
        driveTo: Double,
        f: (Double) -> Double,
    ) {
        val step = (driveTo - driveFrom) / length
        val last = length - 1

        for (i in 0 until length) {
            val k = offset + i

            buffer[k] = f(buffer[k] * (driveTo - step * (last - i))).nanGuard()
        }
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
