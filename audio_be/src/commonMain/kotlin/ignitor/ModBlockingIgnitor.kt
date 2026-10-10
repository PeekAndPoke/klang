/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer

/**
 * Shields an ABSOLUTE-frequency source from pitch modulation — the counterpart to
 * [ModApplyingIgnitor], and the reason `Ignitor.sine(800)` no longer wobbles inside a vibrato'd patch.
 *
 * Ledger W13, "musical vs absolute frequency, part 2". An oscillator reads `ctx.phaseMod` and
 * scales its phase increment by it no matter where its frequency came from, so before this a
 * fixed-pitch oscillator on the spine of a modded subtree was bent exactly like a note-pitched one.
 * Detune never had the problem — it multiplies the freq ARGUMENT, which an absolute-freq oscillator
 * ignores by construction (ledger D13) — so the engine's two pitch operators disagreed about what
 * counts as pitch. They agree now: both move Freq-derived pitches and leave LFOs, drones and
 * fixed-pitch resonances alone.
 *
 * It blocks at the READ, in the context, rather than at the wrap: every pitch modulation reaches a source through
 * `ctx.phaseMod`, which [ModApplyingIgnitor] writes, and nulling it is what stops it. That is one door since pitch
 * pipeline step 4: sprudel's pitch doors (`penv`, `vib`, `accelerate`, `fm`) are `classic()` stages, so they reach a
 * source the same way as an authored pitch node. Before it there were two (sprudel's doors ran on the voice's strip,
 * which wrote `ctx.phaseMod` once for the whole graph in `IgniteRenderer`); step 5 removed the strip's unused bridge.
 *
 * Cost is two field writes per `generate` call and nothing per sample; the shielded oscillator then
 * takes its own `phaseMod == null` fast loop, which is the cheaper of the two it already had.
 */
internal class ModBlockingIgnitor(val inner: Ignitor) : Ignitor {

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        val saved = ctx.phaseMod
        ctx.phaseMod = null
        inner.generate(buffer, freqHz, ctx)
        ctx.phaseMod = saved
    }
}
