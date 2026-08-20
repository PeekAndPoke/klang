/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.filters.EqCore

/**
 * Thin per-voice adapter driving the freq-agnostic [EqCore]: resolves each section's params
 * per block via [Ignitors.readParam] — the ONLY layer where `Freq`-backed params can exist
 * (the guitar chain's tracking highpass `highpass(Osc.freq().mul(k))`); the planned
 * MasterFx/Katalyst surfaces hand the core scalars directly. The upstream renders into the
 * caller's buffer and the core processes IN PLACE — no scratch pass, which is the fused
 * chain's whole win over per-node rendering.
 *
 * COEFFICIENT CACHE — NODE-TYPE-based, PER SECTION: a section whose params are all
 * [ParamIgnitor]/[ConstantIgnitor] (per-voice constants) configures ONCE per voice; anything
 * else reconfigures per block. [FreqIgnitor] is DELIBERATELY excluded from the static set:
 * it is block-constant but NOT voice-constant (`.detune(lfo)` changes freqHz per block), so
 * widening the predicate to `controlRateValueOrNull != null` would freeze a tracking cutoff
 * at block 0. DELIBERATELY WIDER than `SvfIgnitor`'s Param-only predicate: Constants are
 * voice-constant too, and caching them is output-identical (computeSvfCoeffs is pure) —
 * "restoring" Param-only parity would drop the cache for every Constant-authored section
 * (four of the guitar tail's six). PER SECTION, not per Eq: the guitar chain has ONE
 * expression-backed param among ~6 sections; a per-Eq predicate would recompute every
 * section's tan() every block.
 */
internal class EqIgnitor(
    private val upstream: Ignitor,
    private val sections: List<Section>,
) : Ignitor {

    /**
     * One build-time section: the [EqCore] type Int plus the runtime params. [db] is BELL's,
     * [gain] is RAW_TAP's; null on every other type (no dummy params — mirrors the sealed
     * wire variants).
     */
    internal class Section(
        val type: Int,
        val freqHz: Ignitor,
        val q: Ignitor,
        val db: Ignitor? = null,
        val gain: Ignitor? = null,
    ) {
        /** Per-voice-static — all params Param/Constant-backed (see the class KDoc). */
        val isStatic: Boolean =
            isVoiceConstant(freqHz) && isVoiceConstant(q) &&
                (db == null || isVoiceConstant(db)) &&
                (gain == null || isVoiceConstant(gain))

        private fun isVoiceConstant(p: Ignitor): Boolean =
            p is ParamIgnitor || p is ConstantIgnitor
    }

    private val core = EqCore(sections.size)
    private val configured = BooleanArray(sections.size)

    // White-box pins for the two OUTPUT-INVISIBLE perf invariants (the
    // hasRawTap/inputCopyCapacity precedent): the 0 dB skip (the core's explicit branch is
    // equally bit-transparent — the skip only saves the recurrence) and the static
    // coefficient cache (reconfiguring a static section re-derives identical coefficients —
    // "configures ONCE per voice" is only observable as skipped work).
    internal var staticZeroDbSkips = 0
        private set

    internal var staticConfigureSkips = 0
        private set

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        // Upstream renders FIRST — matching the chained path's RNG order: the source seeds
        // its drift/unison/noise draws from THE VOICE'S stream (seeded-voice-rng) BEFORE any
        // section param's scratch render draws (an expression-backed param falls back to a
        // full block render inside readParam). Resolving params first would reorder those
        // draws and silently change the sound vs the chained graph — pinned both white-box
        // (the probe row) and black-box (the same-seeded drift parity row). EqCore consumes no RNG and no
        // ctx, so resolving ALL params between upstream and process (where the chain
        // interleaves them with recurrences) is order-neutral.
        upstream.generate(buffer, freqHz, ctx)

        for (i in sections.indices) {
            val s = sections[i]

            if (s.isStatic && configured[i]) {
                staticConfigureSkips++
                continue
            }

            val db = s.db?.let { Ignitors.readParam(it, freqHz, ctx) } ?: 0.0

            if (s.isStatic && s.type == EqCore.BELL && db == 0.0) {
                // Adapter-owned 0 dB skip (EqCore's KDoc assigns it here): a per-voice-
                // constant 0 dB bell can never move off zero, so retire the slot to
                // PASSTHROUGH instead of running its transparent recurrence every block.
                // NON-static 0 dB bells keep configuring + running state — the core's
                // explicit branch is what makes their zero crossings click-free.
                core.disableSection(i)
                staticZeroDbSkips++
            } else {
                core.configureSection(
                    index = i,
                    type = s.type,
                    freqHz = Ignitors.readParam(s.freqHz, freqHz, ctx),
                    q = Ignitors.readParam(s.q, freqHz, ctx),
                    db = db,
                    gain = s.gain?.let { Ignitors.readParam(it, freqHz, ctx) } ?: 0.0,
                    sampleRate = ctx.sampleRate.toDouble(),
                )
            }

            configured[i] = true
        }

        core.process(buffer, ctx.offset, ctx.length)
    }
}
