/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.DistortionCore
import io.peekandpoke.klang.audio_be.parseDistortionShape
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * Distortion effect with selectable waveshaper shapes.
 *
 * The law is [DistortionCore], the ONE copy the Ignitor's fused `Distort` node (the one `classic()`
 * builds) renders through too (phase 3 step 4, decision D2 option A): the drive inside the
 * oversampler, the DC blocker on every shape, NO soft cap. This class only adapts the strip's
 * contract: amount, shape and oversampling are read once, at construction, the block is rendered in
 * place, and an amount at or below 0 bypasses the stage.
 *
 * Note: unlike `Ignitor.distort()` and `Ignitor.shape()` (the doors' `Shape(Drive(...))` law), this
 * renderer does NOT apply `softCap`. The voice-strip pipeline has its own downstream bounding stages.
 */
class DistortionRenderer(
    private val amount: Double,
    shape: String = "soft",
    oversampleStages: Int = 0,
) : BlockRenderer {

    private val drive: Double = DistortionCore.drive(amount)

    private val core = DistortionCore(parseDistortionShape(shape), oversampleStages)

    override fun render(ctx: BlockContext) {
        if (amount <= 0.0) {
            return
        }

        core.process(ctx.audioBuffer, ctx.offset, ctx.length, drive, ctx.scratchBuffers)
    }
}
