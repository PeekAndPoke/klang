package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * Sample rate reducer (coarse) effect — holds a sample value for multiple frames.
 * Creates a lo-fi digital sound by reducing the effective sample rate.
 *
 * Optional oversampling reduces aliasing from the sample-hold step edges —
 * opt in via the `oversampleStages` constructor param. Default is raw (no oversampling),
 * which preserves the classic aliased / metallic character.
 *
 * When oversampled, the hold period is scaled by the oversampling factor so the
 * creative parameter `amount` still means "every Nth input sample".
 */
class CoarseRenderer(private val amount: Double, oversampleStages: Int = 0) : BlockRenderer {

    private var lastValue: Float = 0.0f

    private val oversampler: Oversampler? =
        if (oversampleStages > 0) Oversampler(oversampleStages) else null

    /**
     * Bootstrap counter init:
     * - Direct path: `0.0` — first sample fires via the `i == 0 && counter == 0.0` branch.
     * - Oversampled path: `1.0` — first sample fires via the `counter >= 1.0` branch
     *   (the direct-path bootstrap doesn't exist here).
     */
    private var counter: Double = if (oversampler != null) 1.0 else 0.0

    /**
     * Counter increment: when running at the oversampled rate, the hold period
     * must be scaled by [Oversampler.factor] so `amount = 4` still fires every
     * 4 original-rate samples (= every `4 * factor` oversampled samples).
     */
    private val increment: Double =
        if (oversampler != null) 1.0 / (amount * oversampler.factor)
        else 1.0 / amount

    override fun render(ctx: BlockContext) {
        if (amount <= 1.0) return

        val os = oversampler
        if (os != null) {
            os.process(ctx.audioBuffer, ctx.offset, ctx.length, ctx.scratchBuffers) { sample ->
                holdStep(sample)
            }
        } else {
            renderDirect(ctx)
        }
    }

    private fun renderDirect(ctx: BlockContext) {
        val buf = ctx.audioBuffer
        for (i in 0 until ctx.length) {
            val idx = ctx.offset + i

            if (counter >= 1.0 || (i == 0 && counter == 0.0)) {
                lastValue = buf[idx]
                counter -= 1.0
            }

            buf[idx] = lastValue
            counter += increment
        }
    }

    /**
     * Per-sample hold transform used in the oversampled path. State (`lastValue`,
     * `counter`) persists across samples and blocks, matching [renderDirect].
     */
    private fun holdStep(sample: Float): Float {
        if (counter >= 1.0) {
            lastValue = sample
            counter -= 1.0
        }
        val out = lastValue
        counter += increment
        return out
    }
}
