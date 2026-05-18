package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.DistortionShape
import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.applyDistortionShape
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.nanGuard
import io.peekandpoke.klang.audio_be.parseDistortionShape
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import kotlin.math.pow

/**
 * Distortion effect with selectable waveshaper shapes.
 *
 * - Exponential drive curve for perceptually even control
 * - Per-shape output normalization (e.g. `gentle` × 2) baked into the shape dispatch
 * - **DC blocker always applied** when amount > 0 — defends against rail-lock
 *   at extreme drive even with symmetric shapes (was previously only applied
 *   to asymmetric shapes like `diode` and `rectify`). Uses the shared
 *   [LowPassHighPassFilters.DcBlocker] (block-based, replaced 3 inline copies
 *   in 2026-04-29 — see that file's header).
 * - Optional oversampling to reduce aliasing from nonlinear processing.
 *
 * Shape dispatch goes through the `inline` [applyDistortionShape] — the per-
 * sample `when` is expanded at the call site and each `ClippingFuncs.foo(x)`
 * inlines. No `(Double) -> Double` function reference is held.
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
    private val shape: DistortionShape = parseDistortionShape(shape)

    private val dcBlocker = LowPassHighPassFilters.DcBlocker()

    private val oversampler: Oversampler? =
        if (oversampleStages > 0) Oversampler(oversampleStages) else null

    override fun render(ctx: BlockContext) {
        if (amount <= 0.0) return

        val s = shape
        val d = drive
        val buf = ctx.audioBuffer
        val os = oversampler

        if (os != null) {
            // Oversampled path: NaN sterilisation owned by Oversampler.
            os.process(buf, ctx.offset, ctx.length, ctx.scratchBuffers) { work, count ->
                for (i in 0 until count) {
                    work[i] = applyDistortionShape(s, work[i] * d)
                }
            }
        } else {
            // Direct path: NaN guard inline — a NaN escaping here would permanently
            // corrupt the downstream IIR DcBlocker.
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buf[i] = applyDistortionShape(s, buf[i] * d).nanGuard()
            }
        }

        dcBlocker.process(buf, ctx.offset, ctx.length)
    }
}
