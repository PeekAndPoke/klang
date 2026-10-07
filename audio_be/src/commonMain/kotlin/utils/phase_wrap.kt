/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

import kotlin.math.floor

/**
 * Wraps this phase into `[0, period)`, up to rounding at both ends:
 * - the one-step branch (less than a period out) can return [period] itself, when a tiny negative plus [period]
 *   rounds up (`(-1e-20).wrapPhase(1.0)` is `1.0`);
 * - the modulo branch (two or more periods out) rounds in its product and its subtraction, so it can return a
 *   tiny negative, up to one ulp of the INPUT below 0 (`106.81415022205296.wrapPhase(TWO_PI)` is
 *   `-1.4210854715202004e-14`, one ulp of 106.8), or [period] itself (`(-0.20000000000000004).wrapPhase(0.1)` is
 *   `0.1`).
 *
 * Above [period] by at most the same one input ulp, and only once one ulp of the input exceeds [period] itself (a
 * phase past about 1e17 for 2π, far beyond anything the engine accumulates); near `Double.MAX_VALUE` the quotient or
 * the product can overflow to ±Inf instead. The engine's periods are 2π and 1. Measured, not proven: a sweep of the 13 doubles around every `k * period` for
 * `|k| < 20000` and the periods 1, 2π, 0.1 and 3 found no miss below 0 larger than one input ulp and none above
 * [period]. `fastSin`'s fold absorbs both edges; `wrapToUnitCycle` maps its own rounding case to 0.
 *
 * Fast path: when this phase overshoots by ≤1 period (the common case for stable oscillators),
 * uses a single conditional subtract: JS `%` on doubles is much slower than this for the
 * normal hot-loop case. Off-path: when this phase is way out of range (e.g. an upstream pitch
 * mod produced an extreme ratio) or non-finite, falls back to a single modulo step (and
 * recovers `0.0` for `Inf`/`NaN`) so we never enter an O(N) subtract loop or hang the audio
 * thread. See `audio/ref/numerical-safety.md`.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun Double.wrapPhase(period: Double): Double {
    if (!this.isFinite()) {
        return 0.0
    }

    var p = this

    if (p >= 2.0 * period || p < -period) {
        // Way out of range: use modulo so we don't loop millions of times.
        p -= period * floor(p / period)
    } else {
        // Common case: at most one overshoot in either direction.
        if (p >= period) {
            p -= period
        } else if (p < 0.0) {
            p += period
        }
    }

    return p
}

/**
 * This value as a fraction of one cycle, wrapped into `[0, 1)`: an oscillator's `phase` input (1.25 is 0.25,
 * -0.25 is 0.75). A value in `[0, 1)` comes back unchanged, bit for bit, so does any `x + n` for an integer `n`
 * small enough to keep `x` exact. Non-finite reads as 0 (a non-finite value reads as unset), and the one rounding
 * case, a tiny negative whose wrap rounds up to exactly 1, is 0 as well.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun Double.wrapToUnitCycle(): Double {
    if (!this.isFinite()) {
        return 0.0
    }

    val r = this - floor(this)

    return if (r >= 1.0) 0.0 else r
}

/**
 * Fast modulo for values that overshoot by at most one period.
 *
 * Typical for per-sample phase accumulators where the phase increments by a small dt each
 * sample and can overshoot [period] by at most one step. Avoids JS `%` on doubles which
 * internally computes a full floating-point division (`a - floor(a/b) * b`).
 *
 * Use this instead of `value % period` in per-sample DSP loops. NOT safe for values that
 * could overshoot by more than one period; use [wrapPhase] for those cases.
 *
 * Into `[0, period]`, with the same one-ulp edge as [wrapPhase]'s one-step branch: a value a hair below 0 lands on [period]
 * exactly (`(-1e-20).smallNumFastMod(1.0)` is `1.0`).
 */
@Suppress("NOTHING_TO_INLINE")
inline fun Double.smallNumFastMod(period: Double): Double {
    return if (this >= period) {
        this - period
    } else if (this < 0.0) {
        this + period
    } else {
        this
    }
}

/**
 * An oscillator's per-sample wrap with the choice made once per block: [wrapPhase] when [safe], else
 * [smallNumFastMod]. A caller sets [safe] when the increment may reach a period or more (a phase modulation, a
 * drift, a large increment); otherwise one conditional step is enough and cheaper.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun Double.wrapPhaseFastOrSafe(period: Double, safe: Boolean): Double =
    if (safe) wrapPhase(period) else smallNumFastMod(period)
