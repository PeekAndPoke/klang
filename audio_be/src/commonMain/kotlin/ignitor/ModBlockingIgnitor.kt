/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer

/**
 * Shields an ABSOLUTE-frequency source from pitch modulation — the counterpart to
 * [ModApplyingIgnitor], and the reason `Osc.sine(800)` no longer wobbles inside a vibrato'd patch.
 *
 * Ledger W13, "musical vs absolute frequency, part 2". An oscillator reads `ctx.phaseMod` and
 * scales its phase increment by it no matter where its frequency came from, so before this a
 * fixed-pitch oscillator on the spine of a modded subtree was bent exactly like a note-pitched one.
 * Detune never had the problem — it multiplies the freq ARGUMENT, which an absolute-freq oscillator
 * ignores by construction (ledger D13) — so the engine's two pitch operators disagreed about what
 * counts as pitch. They agree now: both move Freq-derived pitches and leave LFOs, drones and
 * fixed-pitch resonances alone.
 *
 * Blocking at the READ rather than at the wrap is what makes this cover BOTH doors. The ignitor
 * door hands modulation down through [ModApplyingIgnitor], but sprudel's `.vibrato()`,
 * `.pitchEnvelope()` and `.accelerate()` write `ctx.phaseMod` once for the whole graph in
 * `IgniteRenderer`, with no [ModApplyingIgnitor] anywhere — nulling the context is the only thing
 * that stops both.
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
