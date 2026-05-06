package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.resolveDistortionShape
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import kotlin.math.pow

/**
 * Distortion effect with selectable waveshaper shapes.
 *
 * - Exponential drive curve for perceptually even control
 * - Per-shape output normalization to prevent volume jumps
 * - **DC blocker always applied** when amount > 0 — defends against rail-lock
 *   at extreme drive even with symmetric shapes (was previously only applied
 *   to asymmetric shapes like `diode` and `rectify`). Uses the shared
 *   [LowPassHighPassFilters.DcBlocker] (block-based, replaced 3 inline copies
 *   in 2026-04-29 — see that file's header).
 * - Optional oversampling to reduce aliasing from nonlinear processing.
 *
 * Note: this renderer does NOT apply `softCap` after the DC blocker, unlike
 * `Ignitor.distort()` and `Ignitor.clip()`. The voice-strip pipeline has its
 * own downstream bounding stages.
 */
class DistortionRenderer(
    private val amount: Double,
    shape: String = "soft",
    oversampleStages: Int = 0,
) : BlockRenderer {

    private val drive: Double = 10.0.pow(amount * 1.2)
    private val waveshaper: (Double) -> Double
    private val outputGain: Double

    private val dcBlocker = LowPassHighPassFilters.DcBlocker()

    private val oversampler: Oversampler? =
        if (oversampleStages > 0) Oversampler(oversampleStages) else null

    init {
        val resolved = resolveDistortionShape(shape)
        waveshaper = resolved.fn
        outputGain = resolved.outputGain
    }

    override fun render(ctx: BlockContext) {
        if (amount <= 0.0) return

        val os = oversampler
        if (os != null) {
            // Pass 1: oversampled waveshape into the audio buffer (in-place).
            os.process(ctx.audioBuffer, ctx.offset, ctx.length, ctx.scratchBuffers) { sample ->
                (waveshaper(sample * drive) * outputGain)
            }
        } else {
            // Pass 1 (direct): drive + waveshape into the audio buffer (in-place).
            val d = drive
            val g = outputGain
            val fn = waveshaper
            val buf = ctx.audioBuffer
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buf[i] = fn(buf[i] * d) * g
            }
        }

        // Pass 2: DC-block in-place.
        dcBlocker.process(ctx.audioBuffer, ctx.offset, ctx.length)
    }
}
