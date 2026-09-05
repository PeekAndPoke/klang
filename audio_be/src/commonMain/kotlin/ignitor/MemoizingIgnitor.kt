/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer

/**
 * Wraps an [Ignitor] so that its output is computed at most once per block.
 *
 * When the same DSL node is referenced multiple times in a signal graph (e.g.
 * `let s = Osc.sine().adsr(...); s + s.shimmer()`), each reader would otherwise
 * invoke the underlying generator independently. For stateful sources (phase
 * accumulators, noise seeds) this produces divergent samples — which contradicts
 * the `let` binding mental model.
 *
 * [MemoizingIgnitor] records the inputs of its first call per block and hands
 * the cached output buffer to every subsequent call within the same block.
 *
 * **Cache invalidation key:** `(voiceElapsedFrames, offset, length, freqHz)`.
 * If any of these changes between calls, the inner Ignitor runs again. Typical
 * causes of invalidation:
 * - New block (`voiceElapsedFrames` advanced by previous block's length).
 * - Sub-block render (different `offset` / `length` within one block).
 * - Caller applied pitch modulation that alters `freqHz`.
 *
 * Since the D13 redesign a `detune` can no longer hand one shared instance two `freqHz`
 * values in the same window (the subtree forks at BUILD time — see the detune context in
 * `IgnitorBuildCache`; `ModApplyingIgnitor` modulates via `ctx.phaseMod`, never the freq
 * argument). The freq component stays LOAD-BEARING regardless (review round 1 — do not drop
 * it): two doors still rewrite the freq argument for a subtree shareable with the spine — the
 * fm MODULATOR runs at `fmFreq x ratio` (default: the note — `let m = ...; x.fm(m, ...) + m` splits the key on m),
 * and the wave/super oscillators read their own params at `actualFreq` — the E8 record, plus
 * hand-built Kotlin graphs, which own their sharing.
 *
 * The cache buffer grows lazily to match the largest `output.size` seen.
 */
class MemoizingIgnitor(val inner: Ignitor) : Ignitor {

    private var consumers: Int = 1
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

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        if (consumers <= 1) {
            inner.generate(buffer, freqHz, ctx)
            return
        }

        val miss = ctx.voiceElapsedFrames != cachedVoiceElapsedFrames ||
                ctx.offset != cachedOffset ||
                ctx.length != cachedLength ||
                freqHz != cachedFreqHz

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

        cache.copyInto(buffer, ctx.offset, ctx.offset, ctx.windowEnd)
    }
}
