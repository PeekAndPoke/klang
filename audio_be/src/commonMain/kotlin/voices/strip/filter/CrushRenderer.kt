/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.CrushCore
import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * BitCrush effect: reduces bit depth for a lo-fi digital sound.
 *
 * The law is [CrushCore], the ONE copy the Ignitor `Crush` node renders through too (phase 3 step 4,
 * decision D1: an asymmetric `floor` quantizer with its small DC bias, clamped to `[-1, 1]`, a bypass
 * below `amount = 1.0`). This class only adapts the strip's contract: the amount is read once, at
 * construction, and the block is rendered in place.
 *
 * Optional oversampling reduces aliasing from the staircase quantization; opt in via the
 * `oversampleStages` constructor param. Default is raw (no oversampling), which preserves the classic
 * aliased character. The Ignitor node has no oversampler.
 */
class CrushRenderer(amount: Double, oversampleStages: Int = 0) : BlockRenderer {

    private val halfLevels: Double = CrushCore.halfLevels(amount)

    private val oversampler: Oversampler? =
        if (oversampleStages > 0) Oversampler(oversampleStages) else null

    /**
     * The oversampled block transform, built ONCE per note: a lambda that captured the block's locals
     * would be a new closure object on every block. It reads only the constructor-fixed [halfLevels],
     * so the samples are the ones a per-block lambda rendered.
     */
    private val oversampledTransform: (AudioBuffer, Int) -> Unit = { work, count ->
        // NaN-guard fused into the per-sample loop (CrushCore): see the Oversampler.process KDoc.
        CrushCore.quantize(work, work, 0, count, halfLevels)
    }

    override fun render(ctx: BlockContext) {
        // Bypass below two levels (amount < 1.0), see CrushCore.halfLevels.
        if (halfLevels == CrushCore.BYPASS) {
            return
        }

        val os = oversampler

        if (os != null) {
            os.process(ctx.audioBuffer, ctx.offset, ctx.length, ctx.scratchBuffers, oversampledTransform)
        } else {
            val buf = ctx.audioBuffer

            CrushCore.quantize(buf, buf, ctx.offset, ctx.windowEnd, halfLevels)
        }
    }
}
