/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.max

/**
 * Parallel wet path with a dry floor: copies the input, runs [inner] on the copy, and blends
 * the result back via the shared wet/dry law (C4 of the filter unification):
 *
 * ```
 * out = max(floor, cos(w*pi/2)^2) * dry  +  sin(w*pi/2)^2 * wet      (p = 2, correlated)
 * ```
 *
 * The p = 2 (equal-AMPLITUDE) branch applies because the wet is the dry through a resonator
 * bank — coherent in the passbands, so amplitudes add. `floor` is the minimum dry
 * coefficient (the body's physical floor is 0.4, the vowel's 0.2); above the pinning
 * threshold the dry stays at the floor while the wet keeps rising — that is what a floor is
 * for. `amount` (the w knob) lives on [0, 1]; the pre-C4 raw extension above 1 is a
 * DELETED capability (plan: Helper domain — no song used it), values above 1 behave as 1.
 * `amount <= 0` bypasses bit-identically (the inner filter never runs).
 */
class ParallelMixFilter(
    private val inner: AudioFilter,
    amount: Double,
    floor: Double = 0.0,
) : AudioFilter {

    // C4: the shared law lives on w in [0, 1]; the old amount > 1 raw extension is a
    // deliberately DELETED capability (plan: Helper domain) — no song used it.
    private val amount: Double = if (amount.isFinite()) amount.coerceIn(0.0, 1.0) else 0.0
    private val floor: Double = if (floor.isFinite()) floor.coerceIn(0.0, 1.0) else 0.0

    // Block-constant (amount/floor are fixed at construction) — precompute both coefficients
    // via the shared wet/dry law (C4), correlated branch (p = 2): the wet is the dry through a
    // resonator bank, coherent in the passbands, so amplitudes add.
    private val dryGain: Double = WetDryMix.dryCoeff(this.amount, this.floor, p = 2)
    private val wetGain: Double = WetDryMix.wetCoeff(this.amount, p = 2)

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
            buffer[offset + i] = buffer[offset + i] * dryGain + wetBuffer[i] * wetGain
        }
    }
}
