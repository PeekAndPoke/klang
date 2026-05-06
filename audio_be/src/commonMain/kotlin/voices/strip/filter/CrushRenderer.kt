package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import kotlin.math.floor
import kotlin.math.pow

/**
 * BitCrush effect — reduces bit depth for a lo-fi digital sound.
 * Quantizes the amplitude to discrete levels.
 *
 * Asymmetric `floor`-based quantizer: `floor(x * halfLevels) / halfLevels`, with
 * the output clamped to `[-1, 1]`. Floor biases every sample DOWN to the next
 * grid step, so a symmetric input produces an amplitude-modulated DC offset of
 * roughly `−0.5 / halfLevels` — this asymmetry IS the classic audible character
 * of a bit crusher. A symmetric `round` quantizer is mathematically cleaner but
 * sounds almost identical across `amount` values, especially once the oversampled
 * path's decimation low-pass filters away its tiny symmetric quantization noise.
 *
 * - **Clamp** catches the non-integer `halfLevels` case where `floor(x*hl)/hl`
 *   can exceed `-1` on the negative side (e.g. at `amount = 1.5`, `hl ≈ 1.414`,
 *   `x = -1` maps to `floor(-1.414)/1.414 = -2/1.414 ≈ -1.414`).
 * - **Continuous `levels`** — modulating `amount` sweeps the grid spacing
 *   smoothly instead of stepping across `log2(N)` boundaries.
 *
 * **Bypass below `amount = 1.0`.** With fewer than 2 quantization levels the grid
 * step exceeds the input range entirely — bypass is the only sensible behavior.
 * The minimum musically meaningful setting is `amount = 1.0` (effectively two
 * grid steps plus the clamp).
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
            val q = floor(buf[idx] * hl) / hl
            buf[idx] = q.coerceIn(-1.0, 1.0)
        }
    }

    private fun renderOversampled(ctx: BlockContext, os: Oversampler) {
        val hl = halfLevels
        os.process(ctx.audioBuffer, ctx.offset, ctx.length, ctx.scratchBuffers) { sample ->
            val q = floor(sample * hl) / hl
            q.coerceIn(-1.0, 1.0)
        }
    }
}
