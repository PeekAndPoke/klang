/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.utils.copyRangeInto

/**
 * Wraps an [Ignitor] so that its output is computed at most once per block.
 *
 * When the same DSL node is referenced multiple times in a signal graph (e.g.
 * `let s = Ignitor.sine().adsr(...); s + s.shimmer()`), each reader would otherwise
 * invoke the underlying generator independently. For stateful sources (phase
 * accumulators, noise seeds) this produces divergent samples — which contradicts
 * the `let` binding mental model.
 *
 * [MemoizingIgnitor] records the inputs of its first call per block and hands
 * the cached output buffer to every subsequent call within the same block.
 *
 * **Cache invalidation key:** `(voiceElapsedFrames, offset, length, freqHz)`, without
 * `freqHz` when [freqInvariant]. If any of these changes between calls, the inner Ignitor
 * runs again. Typical causes of invalidation:
 * - New block (`voiceElapsedFrames` advanced by previous block's length).
 * - Sub-block render (different `offset` / `length` within one block).
 * - Caller applied pitch modulation that alters `freqHz` (only when not [freqInvariant]).
 *
 * Since the D13 redesign a `detune` can no longer hand one shared instance two `freqHz`
 * values in the same window (the subtree forks at BUILD time — see the detune context in
 * `IgnitorBuildCache`; `ModApplyingIgnitor` modulates via `ctx.phaseMod`, never the freq
 * argument). The freq component stays LOAD-BEARING for a subtree that reads `Freq` (review round 1: do not drop
 * it): two doors still rewrite the freq argument for a subtree shareable with the spine — the
 * fm MODULATOR runs at `fmFreq x ratio` (default: the note — `let m = ...; x.fm(m, ...) + m` splits the key on m),
 * and the wave/super oscillators read their own params at `actualFreq` — the E8 record, plus
 * hand-built Kotlin graphs, which own their sharing.
 *
 * **[freqInvariant]** (`docs/tasks-archive/2026-10/20261007-shared-modulator-memo-rate.md`, 2026-10-07): the build sets it
 * ([markFreqInvariant], at the first share) when the wrapped subtree reads no `IgnitorDsl.Freq`
 * and carries no pitch mod. Such a subtree renders the same samples at any `freqHz` (the house
 * rule: runtime code consumes the freq argument only to forward it, so freq-dependence is visible
 * structurally; the same predicate as the D13 detune fold, `IgnitorBuildCache.usesMusicalFreq`),
 * so a second caller with a different `freqHz` takes the cache. Without it, one LFO in the `phase`
 * or `duty` of two oscillators at different pitches ran twice per block: double rate, each
 * oscillator getting alternate blocks. A subtree that DOES read `Freq` (anywhere: an LFO's rate,
 * its depth, a scaling next to it) keeps the freq key: called at two pitches it renders twice, once
 * per pitch, and every stateful node only in it advances twice (the task file's residue; authors build
 * such a modulator once per layer).
 * `ctx.phaseMod` is not in the key either: only a sample leaf reads it without a `Freq` leaf.
 *
 * The cache buffer grows lazily to match the largest `output.size` seen.
 */
class MemoizingIgnitor(val inner: Ignitor) : Ignitor {

    private var consumers: Int = 1

    /** True when [inner]'s output does not depend on `freqHz`; the cache then ignores it. */
    var freqInvariant: Boolean = false
        private set
    private var cache: AudioBuffer = AudioBuffer(0)

    /**
     * Pure delegation. Every node that reports non-null here is stateless (Constant/Param/Freq
     * leaves and pointwise combinators over them), so bypassing the block cache for the scalar
     * read has no side effects and equals the cached buffer's samples bit-for-bit. Without this
     * override, the wrapper that [buildIgnitor] puts around every identity-cached non-leaf node
     * (Variants and the pitch-mod nodes dissolve instead of being wrapped) reported `null` and
     * forced composite constant subtrees (e.g. `Times(Freq, Param)`) onto the scratch-render
     * fallback in [Ignitor.blockStartValue].
     */
    override fun controlRateValueOrNull(freqHz: Double): Double? =
        inner.controlRateValueOrNull(freqHz)

    override val isBlockConstant: Boolean = inner.isBlockConstant

    // Cache key components. Sentinel values guarantee a miss on the first call.
    private var cachedVoiceElapsedFrames: Int = Int.MIN_VALUE
    private var cachedOffset: Int = Int.MIN_VALUE
    private var cachedLength: Int = Int.MIN_VALUE
    private var cachedFreqHz: Double = Double.NaN

    fun incConsumers() {
        consumers++
    }

    /** See [freqInvariant]. Build time only. */
    fun markFreqInvariant() {
        freqInvariant = true
    }

    /**
     * Caches per block even with a single reader: a pitch-mod memo (`combineMods` in `IgnitorDslRuntime.kt`), read by
     * every pitched source under it, caches always rather than counting its readers. Build time only.
     */
    fun cachePerBlock() {
        if (consumers < 2) {
            consumers = 2
        }
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        if (consumers <= 1) {
            inner.generate(buffer, freqHz, ctx)
            return
        }

        val miss = ctx.voiceElapsedFrames != cachedVoiceElapsedFrames ||
                ctx.offset != cachedOffset ||
                ctx.length != cachedLength ||
                (!freqInvariant && freqHz != cachedFreqHz)

        if (miss) {
            if (cache.size < buffer.size) {
                cache = AudioBuffer(buffer.size)
            }
            inner.generate(cache, freqHz, ctx)
            cachedVoiceElapsedFrames = ctx.voiceElapsedFrames
            cachedOffset = ctx.offset
            cachedLength = ctx.length
            cachedFreqHz = freqHz
        }

        cache.copyRangeInto(destination = buffer, destinationOffset = ctx.offset, startIndex = ctx.offset, endIndex = ctx.windowEnd)
    }
}
