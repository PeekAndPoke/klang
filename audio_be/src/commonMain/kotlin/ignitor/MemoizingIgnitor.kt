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
 * **Cache invalidation key:** `(voiceElapsedFrames, the root context's voiceElapsedFrames, offset, length, freqHz)`,
 * without `freqHz` when [freqInvariant]; the root's count is the voice's own inside an oversampling region (whose
 * scaled count saturates, audit L5-07) and this context's outside one. If any of these changes between calls, the
 * inner Ignitor runs again. Typical causes of invalidation:
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
 * **[freqInvariant]** (KLANG-00278 (shared modulator memo rate), 2026-10-07): the build sets it
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
 * The cache buffer is allocated at BUILD, when the memo turns into a caching one ([incConsumers], [cachePerBlock]),
 * at the voice's block size (tidy-up step 10: nothing allocates in generate). A caller handing in a larger buffer
 * still grows it at render, as before.
 */
class MemoizingIgnitor(val inner: Ignitor) : Ignitor {

    private var consumers: Int = 1

    /** True when [inner]'s output does not depend on `freqHz`; the cache then ignores it. */
    var freqInvariant: Boolean = false
        private set
    // A memo that never caches (one reader, the usual case) keeps the one shared empty buffer: a buffer of size 0 is
    // never written, and a caching memo replaces it at build ([reserveCache]). One per memo was a typed array per built
    // node per note on JS (audit L5-04).
    private var cache: AudioBuffer = NO_CACHE

    /**
     * Pure delegation, the flag with it. Every node that reports [isBlockConstant] is stateless (Constant/Param/Freq
     * leaves and pointwise combinators over them), so bypassing the block cache for the scalar
     * read has no side effects and equals the cached buffer's samples bit-for-bit. Without this
     * override, the wrapper that [buildIgnitor] puts around every identity-cached non-leaf node
     * (Variants and the pitch-mod nodes dissolve instead of being wrapped) would answer the NaN
     * default under a true flag, so every reader of a wrapped composite constant subtree (e.g.
     * `Times(Freq, Param)`) would read NaN. The parity table's `memoizing` row guards it.
     */
    override fun controlRateValue(freqHz: Double): Double =
        inner.controlRateValue(freqHz)

    override val isBlockConstant: Boolean = inner.isBlockConstant

    // Cache key components. Sentinel values guarantee a miss on the first call.
    private var cachedVoiceElapsedFrames: Int = Int.MIN_VALUE
    private var cachedRootElapsedFrames: Int = Int.MIN_VALUE
    private var cachedOffset: Int = Int.MIN_VALUE
    private var cachedLength: Int = Int.MIN_VALUE
    private var cachedFreqHz: Double = Double.NaN

    /** One more reader. From two on the memo caches per block, in a buffer of [blockFrames] allocated here. Build time only. */
    fun incConsumers(blockFrames: Int) {
        consumers++
        reserveCache(blockFrames)
    }

    /** See [freqInvariant]. Build time only. */
    fun markFreqInvariant() {
        freqInvariant = true
    }

    /**
     * Caches per block even with a single reader: a pitch-mod memo (`combineMods` in `IgnitorDslRuntime.kt`), read by
     * every pitched source under it, caches always rather than counting its readers. The cache buffer, [blockFrames]
     * long, is allocated here. Build time only.
     */
    fun cachePerBlock(blockFrames: Int) {
        if (consumers < 2) {
            consumers = 2
        }

        reserveCache(blockFrames)
    }

    private fun reserveCache(blockFrames: Int) {
        if (cache.size < blockFrames) {
            cache = AudioBuffer(blockFrames)
        }
    }

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        if (consumers <= 1) {
            inner.generate(buffer, freqHz, ctx)
            return
        }

        // Inside an oversampling region the context's frame count is the voice's times the factor, held at
        // `Int.MAX_VALUE` once that product overflows (46.6 minutes into a voice at 16x): from there on it is the same
        // in every block, and the key alone repeated one block for the rest of the voice (audit L5-07). The voice's own
        // count (the region's root context) still moves per block, so it is part of the key; outside a region it is
        // this context's count.
        val rootElapsed = (ctx.rootContext ?: ctx).voiceElapsedFrames
        val miss = ctx.voiceElapsedFrames != cachedVoiceElapsedFrames ||
                rootElapsed != cachedRootElapsedFrames ||
                ctx.offset != cachedOffset ||
                ctx.length != cachedLength ||
                (!freqInvariant && freqHz != cachedFreqHz)

        if (miss) {
            // Only a buffer larger than the block reserved at build gets here: a voice renders into block-sized buffers
            // (its own and the scratch pool's), so this is a test rig's larger buffer.
            if (cache.size < buffer.size) {
                cache = AudioBuffer(buffer.size)
            }
            inner.generate(cache, freqHz, ctx)
            cachedVoiceElapsedFrames = ctx.voiceElapsedFrames
            cachedRootElapsedFrames = rootElapsed
            cachedOffset = ctx.offset
            cachedLength = ctx.length
            cachedFreqHz = freqHz
        }

        cache.copyRangeInto(destination = buffer, destinationOffset = ctx.offset, startIndex = ctx.offset, endIndex = ctx.windowEnd)
    }
}

/** The empty cache every non-caching [MemoizingIgnitor] shares; size 0, so nothing ever writes into it. */
private val NO_CACHE: AudioBuffer = AudioBuffer(0)
