/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.katalyst

import io.peekandpoke.klang.audio_be.DistortionCore
import io.peekandpoke.klang.audio_be.distortionShapeAt
import io.peekandpoke.klang.audio_be.filters.houseDcBlockCoeff
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.constants.SILENCE_FLOOR

/**
 * The bus waveshaper, an insert: the voice's `shape` at the bus position (`KatalystStageDsl.Shape`, KLANG-00467
 * (cylinder amp step 1)). It is [KatalystDistortEffect] without the switch and without the amount: the same
 * [DistortionCore] law at a drive of exactly 1.0 (the curve, then the DC blocker), left and right each through a core
 * of their own, at the rate of the bus it is in, with the house DC pole ([houseDcBlockCoeff], a corner of 7.64 Hz at
 * every rate), mapped to stay where it is in Hz inside an oversampling region.
 *
 * **Never off.** It has no knob and no state machine: it shapes for as long as its chain runs, and a chain swap fades
 * whole chains. No writer configures it. It delays nothing (no [KatalystLatentEffect]), as the distort stage.
 *
 * Its tail is the DC blocker's decay after the input stops (an asymmetric shape, `tube`, `asym`, makes DC while signal
 * flows): [hasTail] answers it, and the chain declares one (`KatalystChain.declaresTail`), so a chain swap drains it and
 * a silent master waits for it.
 */
class KatalystShapeEffect(
    /** The rate the stage runs at: the cylinder's, or inside an oversampling region the region's. */
    sampleRate: Int,
    /** The shape as its index in [DistortionShapes.names]; out of range is `soft` ([distortionShapeAt]). */
    shapeIndex: Int = DistortionShapes.SOFT_INDEX,
    /**
     * The 2x steps above the base rate the chain runs at, 0 on a cylinder; inside an oversampling region the region's
     * (KLANG-00214 (oversampling regions)): the DC blocker's pole is mapped to stay where it is in Hz.
     */
    firstStage: Int = 0,
) : KatalystEffect {

    /** The house pole at the base rate ([sampleRate] is the region's inside one), mapped up by [firstStage] below. */
    private val baseDcBlockCoefficient: Double = houseDcBlockCoeff(sampleRate = sampleRate shr firstStage)

    private val coreL = DistortionCore(
        shape = distortionShapeAt(shapeIndex.toDouble()),
        dcBlockCoefficient = DistortionCore.dcBlockCoefficientAt(coefficient = baseDcBlockCoefficient, stages = firstStage),
    )

    private val coreR = DistortionCore(
        shape = distortionShapeAt(shapeIndex.toDouble()),
        dcBlockCoefficient = DistortionCore.dcBlockCoefficientAt(coefficient = baseDcBlockCoefficient, stages = firstStage),
    )

    /** One block of the cores at unity drive, in place, each channel through its own core. */
    override fun process(ctx: KatalystContext) {
        val n = ctx.blockFrames

        coreL.process(buffer = ctx.mixBuffer.left, offset = 0, length = n, drive = 1.0)
        coreR.process(buffer = ctx.mixBuffer.right, offset = 0, length = n, drive = 1.0)
    }

    /**
     * True while a core's DC blocker still holds the offset it was removing: after the input stops its output decays
     * from that offset over tens of milliseconds, audio into a silent input. O(1).
     */
    override fun hasTail(): Boolean = coreL.dcHoldsEnergy(SILENCE_FLOOR) || coreR.dcHoldsEnergy(SILENCE_FLOOR)

    /** The cores' filter memories cleared: the clean slate for a silent cylinder. */
    override fun reset() {
        coreL.reset()
        coreR.reset()
    }

    /** Rents nothing, so retiring is the clean slate. */
    override fun retire() {
        reset()
    }
}
