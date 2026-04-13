package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import kotlin.math.pow
import kotlin.math.round

/**
 * BitCrush effect — reduces bit depth for a lo-fi digital sound.
 * Quantizes the amplitude to discrete levels.
 *
 * Midtread symmetric quantizer: `round(x * halfLevels) / halfLevels`, with the
 * output clamped to `[-1, 1]`. The grid is symmetric around zero (zero input →
 * zero output), which means:
 * - **No DC offset bias** — an ADSR-shaped signal fades cleanly to silence without
 *   the voice-boundary clicks that an asymmetric `floor`-based quantizer produces.
 * - **No amplitude inflation** — the output clamp catches the non-integer
 *   `halfLevels` case where `round(x * hl)/hl` could otherwise exceed the input
 *   range (e.g. at `amount = 1.5`, `hl ≈ 1.414`, unit input would naively map to
 *   `2/1.414 ≈ 1.414`). Clamp pins it to the topmost in-range grid point.
 * - **Continuous `levels`** — modulating `amount` sweeps the grid spacing
 *   smoothly instead of stepping across `log2(N)` boundaries.
 *
 * **Bypass below `amount = 1.0`.** With fewer than 2 quantization levels the grid
 * step exceeds the input range entirely — bypass is the only sensible behavior.
 * The minimum musically meaningful setting is `amount = 1.0` (3 effective levels:
 * −1, 0, +1).
 *
 * Optional oversampling reduces aliasing from the staircase quantization —
 * opt in via the `oversampleStages` constructor param. Default is raw (no oversampling),
 * which preserves the classic aliased character.
 */
class CrushRenderer(amount: Double, oversampleStages: Int = 0) : BlockRenderer {

    private val levels: Double = if (amount >= 1.0) 2.0.pow(amount) else 0.0
    private val halfLevels: Double = levels / 2.0

    private val oversampler: Oversampler? =
        if (oversampleStages > 0) Oversampler(oversampleStages) else null

    override fun render(ctx: BlockContext) {
        // Bypass: need at least 2 levels (amount >= 1.0) for the quantizer to be
        // remotely bounded by the input range. Sub-1.0 amounts would inflate by
        // factor `1/halfLevels`, which can exceed 2× — clearly broken.
        if (levels < 2.0) return

        val os = oversampler
        if (os != null) {
            renderOversampled(ctx, os)
        } else {
            renderDirect(ctx)
        }
    }

    private fun renderDirect(ctx: BlockContext) {
        val hl = halfLevels
        val buf = ctx.audioBuffer
        for (i in 0 until ctx.length) {
            val idx = ctx.offset + i
            val q = round(buf[idx].toDouble() * hl) / hl
            buf[idx] = q.coerceIn(-1.0, 1.0).toFloat()
        }
    }

    private fun renderOversampled(ctx: BlockContext, os: Oversampler) {
        val hl = halfLevels
        os.process(ctx.audioBuffer, ctx.offset, ctx.length, ctx.scratchBuffers) { sample ->
            val q = round(sample.toDouble() * hl) / hl
            q.coerceIn(-1.0, 1.0).toFloat()
        }
    }
}
