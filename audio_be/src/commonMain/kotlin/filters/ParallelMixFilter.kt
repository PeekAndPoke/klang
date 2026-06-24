/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.max

/**
 * Dry/wet mix wrapper for a wet-producing [AudioFilter], modelling a resonator the signal
 * passes *through* — a broadband floor plus the inner filter's (resonant) peaks:
 *
 * ```
 * dryGain = max(floor, 1 − amount·(1 − floor))
 * out     = dryGain·dry + amount·wet
 * ```
 *
 * The one formula spans the whole useful range via [floor]:
 * - `floor = 0` → pure **crossfade** `dry·(1−amount) + wet·amount`.
 * - `floor = 1` → pure **additive** `dry + wet·amount`.
 * - `0 < floor < 1` → **physical body**: the dry never drops below `floor`, so the broadband is
 *   never stripped (no "lost highs/lows"), and the total stays bounded (no volume crank as
 *   `amount` rises). This matches a real passive resonator — it emphasizes the resonant bands
 *   relative to a broadband transmission floor, it does not add energy on top.
 *
 * `amount > 1` keeps the dry pinned at `floor` while the resonances keep rising (stays raw — no
 * upper clamp). `amount = 0` short-circuits to a bit-identical dry bypass.
 *
 * Keeps the resonator and the blend as separate concerns: [inner] (e.g. [BodyFilter]) is a pure
 * wet filter with the standard `AudioFilter` API, and this wrapper is the one place the dry/wet
 * math lives — reusable for any wet-only filter (the formant/vowel filter can wrap the same way).
 *
 * `amount` is coerced `>= 0` and `floor` to `[0, 1]` (user-facing → coerce, never throw). One
 * per-instance scratch buffer, resized on growth (no per-block allocation).
 */
class ParallelMixFilter(
    private val inner: AudioFilter,
    amount: Double,
    floor: Double = 0.0,
) : AudioFilter {

    private val amount: Double = if (amount.isFinite()) amount.coerceAtLeast(0.0) else 0.0
    private val floor: Double = if (floor.isFinite()) floor.coerceIn(0.0, 1.0) else 0.0

    // Block-constant (amount/floor are fixed at construction) — precompute the dry coefficient.
    private val dryGain: Double = max(this.floor, 1.0 - this.amount * (1.0 - this.floor))

    private var wetBuffer: AudioBuffer = AudioBuffer(0)

    override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
        // amount == 0 → out = dry. Leave the buffer untouched (bit-identical) and skip the inner.
        if (amount <= 0.0) return

        if (wetBuffer.size < length) {
            wetBuffer = AudioBuffer(length)
        }

        // 1. Copy the dry input, then run the inner filter on the copy → it becomes the wet signal.
        buffer.copyInto(wetBuffer, 0, offset, offset + length)
        inner.process(wetBuffer, 0, length)

        // 2. Blend: dry attenuated to dryGain (≥ floor) + the resonant peaks on top.
        for (i in 0 until length) {
            buffer[offset + i] = buffer[offset + i] * dryGain + wetBuffer[i] * amount
        }
    }
}
