/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.AudioSample
import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.nanGuard
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

    private var lastValue: AudioSample = 0.0

    private val oversampler: Oversampler? =
        if (oversampleStages > 0) Oversampler(oversampleStages) else null

    /**
     * Bootstrap counter init: `1.0` on BOTH paths — "take a sample NOW" via the
     * `counter >= 1.0` branch. The old direct-path `0.0` + `i == 0` block latch re-armed at
     * note-relative sample `amount` for every power-of-two amount, so a block boundary landing
     * there displaced the hold grid for the rest of the note (ledger W1, live in
     * ATruthWorthLyingFor's `coarse(2)`); it also made the first hold `2 x amount` long where
     * the oversampled path held `amount` from sample 0. One bootstrap, one grid, both paths.
     * (Hold lengths are exact for dyadic amounts; non-dyadic ones drift by up to one sample as
     * `1/amount` accumulates — pre-existing float behavior on every path.)
     *
     * The non-finite latch this comment used to flag as OPEN is CLOSED (ledger W4, strip
     * half): see the bypass at the top of [render]. The audit's stated reach was wrong in both
     * directions — NaN could never reach this class (`FilterPipelineBuilder` gates on
     * `amount > 1.0`, which NaN fails), while +Inf could and did, freezing the voice.
     */
    private var counter: Double = 1.0

    /**
     * Counter increment: when running at the oversampled rate, the hold period
     * must be scaled by [Oversampler.factor] so `amount = 4` still fires every
     * 4 original-rate samples (= every `4 * factor` oversampled samples).
     */
    private val increment: Double =
        if (oversampler != null) {
            1.0 / (amount * oversampler.factor)
        } else {
            1.0 / amount
        }

    override fun render(ctx: BlockContext) {
        // Ledger W4 (strip half): passthrough-degenerate AND non-finite amounts bypass, so
        // one parameter means one thing on both doors. +Inf CAN reach here (`Inf > 1.0` passes
        // FilterPipelineBuilder's gate, and sprudel does not coerce, so any pattern value
        // evaluating to Infinity lands here — no shipped song does, and no incident is on
        // record); it gave `increment = 1.0 / Inf = 0.0`, so sample 0 was captured and the
        // counter stuck at 0.0: frozen DC for the note's LIFE, because unlike the ignitor
        // door's per-block readParam the strip amount is a per-note constant and nothing heals
        // it. NaN never reached this class at all (the builder's `> 1.0` gate rejects it), but
        // the same guard closes it for any direct constructor.
        //
        // The ignitor twin reads `!(amt > 0.0) || amt.isInfinite()`; the different threshold is
        // deliberate, not drift. That door coerces `amt` to at least 1.0, which turns (0, 1]
        // into an exact identity copy — the same OUTPUT this bypass gives. Do not "finish the
        // parity" by lowering 1.0 to 0.0: on the direct path it would still look like a no-op,
        // but (0, 1] would then be routed through the oversampler's up/downsample filters that
        // the bypass skips, and the ignitor door has no oversampler to match.
        // NaN-guard: the !(x > 1.0) form is what catches NaN.
        if (!(amount > 1.0) || amount.isInfinite()) {
            return
        }

        val os = oversampler
        if (os != null) {
            os.process(ctx.audioBuffer, ctx.offset, ctx.length, ctx.scratchBuffers) { work, count ->
                // NaN-guard fused into the per-sample loop — see Oversampler.process KDoc.
                for (i in 0 until count) {
                    work[i] = holdStep(work[i]).nanGuard()
                }
            }
        } else {
            renderDirect(ctx)
        }
    }

    private fun renderDirect(ctx: BlockContext) {
        val buf = ctx.audioBuffer
        for (i in 0 until ctx.length) {
            val idx = ctx.offset + i

            if (counter >= 1.0) {
                lastValue = buf[idx].nanGuard()
                counter -= 1.0
            }

            buf[idx] = lastValue
            counter += increment
        }
    }

    /**
     * Per-sample hold transform used in the oversampled path. State (`lastValue`,
     * `counter`) persists across samples and blocks, matching [renderDirect].
     *
     * `inline` is load-bearing: the per-sample call is invoked from inside the
     * block lambda passed to [Oversampler.process], and inlining eliminates the
     * function-call overhead on Kotlin/JS. JVM JIT inlines this anyway, but
     * marking `inline` makes the win cross-platform.
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun holdStep(sample: AudioSample): AudioSample {
        if (counter >= 1.0) {
            lastValue = sample
            counter -= 1.0
        }
        val out = lastValue
        counter += increment
        return out
    }
}
