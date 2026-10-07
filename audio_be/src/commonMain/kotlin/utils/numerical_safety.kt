/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

import kotlin.math.abs

/** Threshold below which filter state is flushed to zero to avoid denormal slowdowns. */
const val DENORMAL_THRESHOLD = 1e-15

/**
 * Returns `0.0` if this value is NaN, otherwise the value unchanged.
 *
 * Used to sterilise a SAMPLE before it enters FIR / delay-line state, where a NaN would
 * smear across the whole line and, in a recirculating ring, never scroll out. Encodes the
 * engine-wide `// NaN-guard (NaN ≠ NaN)` idiom: the IEEE-754 property that `NaN != NaN` is
 * the cheapest possible NaN test, one compare with no `abs` and no call.
 *
 * DIVISION OF LABOUR, since the master round: this guards the SAMPLE and catches NaN only;
 * [flushState] guards the IIR STATE and catches non-finite as well as denormal. The permanent
 * IIR-latch class belongs to [flushState]; do not widen this one to chase it, because an Inf
 * passing through a sample path is the raw engine behaving as designed, while an Inf settling
 * into filter state is a filter that can never recover.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun Double.nanGuard(): Double = if (this != this) 0.0 else this

/**
 * Returns this value if it is finite, otherwise `0.0`: NaN and both infinities read as silence, every finite value
 * (`-0.0` and denormals included) passes unchanged.
 *
 * The tap guard: a sample that is about to be weighted, summed or stored (a crossfade partner, a release tail, a
 * reverb or compressor input, a ring write), where an infinity would come back as NaN (`Inf * 0.0`, `Inf - Inf`)
 * or stay for good. One compare: `abs(x) <= Double.MAX_VALUE` is false for both infinities and for NaN (NaN fails
 * every comparison), where `isFinite()` is two tests and, on Kotlin/JS, a call.
 *
 * Its neighbours have other edge rules: [nanGuard] catches NaN only and lets an infinity through; [flushState]
 * also zeroes denormals, for IIR state.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun Double.finiteOrZero(): Double = if (abs(this) <= Double.MAX_VALUE) this else 0.0

/**
 * Flushes an IIR/FIR carry to zero unless it is a NORMAL, FINITE magnitude.
 *
 * Two failure modes, one guard, because both say the same thing: this value must not be
 * carried into the next sample:
 * - a DENORMAL costs 10-100x on some platforms (the original reason this existed);
 * - a NON-FINITE latches the filter FOREVER. An IIR whose state goes NaN can never come back,
 *   and a single `Inf` is enough: the very next sample computes `x - Inf + a*Inf`, i.e.
 *   `-Inf + Inf`, which manufactures the NaN. Ledger W7 / the master round measured where that
 *   ends: one such sample silenced the WHOLE BACKEND until a page reload, because the master DC
 *   blocker's only clearer runs once, at warmup.
 *
 * This is the engine-wide answer to that class rather than a patch at the master: house rule
 * (`/code-style` #8) already puts this call on every IIR state variable, so widening it here
 * makes every filter in the engine structurally unable to latch. Recovery is 1-2 samples.
 * Bit-identical for every normal finite value (only the rejected branch changed), so no
 * shipped sound moves.
 *
 * Shape matters in a per-sample loop: `abs` + two compares + a select, and NO branch on the
 * data. The range test feeds a conditional move, and the sign, the one genuinely
 * unpredictable bit in an audio signal, is masked away by `abs` rather than branched on.
 * `a <= Double.MAX_VALUE` rejects Inf AND NaN in a single compare (NaN fails every
 * comparison), where `isFinite()` would be two tests and, on Kotlin/JS, a call.
 *
 * See [nanGuard] for the other half of the convention: that one sterilises an incoming SAMPLE,
 * this one sterilises the STATE it would otherwise poison.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun Double.flushState(): Double {
    val a = abs(this)

    return if (a >= DENORMAL_THRESHOLD && a <= Double.MAX_VALUE) this else 0.0
}

// ═══════════════════════════════════════════════════════════════════════════════
// Numerical Safety Bounds
// ═══════════════════════════════════════════════════════════════════════════════
//
// Klang's safety contract: see `audio/ref/numerical-safety.md` for the full story.
//
// Every arithmetic operator that can produce `NaN`/`Inf` clamps either its inputs
// (divisor-class ops: Div, Mod, Recip) or its output (output-clamp ops: Times,
// Pow, Exp, Sq, Mul-by-constant, and Neg, which is a multiply by -1 since 2026-09-15).
// Naturally bounded ops (Plus, Minus, Lerp, Range, Clamp, Min, Max, Abs, Sign, Floor,
// Ceil, Round, Frac, Tanh, Sqrt, Log) need no extra guard. Div alone treats a divisor
// of exactly zero (and a block-constant infinity) as a zero result, not a clamp; a
// block-constant zero multiplier is a dead branch (see `audio/ref/numerical-safety.md`).
//
// Values match the SuperCollider / ChucK / STK convention (`zapgremlins`,
// `CK_DDN_*`): ±300 dBFS, well below any audible signal, well above subnormal.
// `1 / SAFE_MIN = SAFE_MAX` ensures a reciprocal of the smallest allowed
// divisor lands at the largest allowed output, round-trip safe. (Design intent, not an FP
// bit-fact: 1.0 / 1e-15 evaluates to 9.999999999999999e14, just BELOW SAFE_MAX, so a
// reciprocal of a safeDiv'd value can never actually engage safeOut.)

/**
 * Smallest allowed magnitude for a divisor (or reciprocal input) in audio arithmetic.
 *
 * Values closer to zero are clamped to `±SAFE_MIN` (sign preserved) to prevent
 * `1/x` from overflowing. ≈ -300 dBFS, well below any audible signal.
 */
const val SAFE_MIN: Double = 1e-15

/**
 * Largest allowed output magnitude for ops that can grow values (`Times`, `Pow`,
 * `Exp`, `Sq`, `Mul-by-constant`).
 *
 * Outputs above this are clamped to `±SAFE_MAX`. ≈ +300 dBFS, vastly above any
 * musical signal, well below `Double.MAX_VALUE` (`≈ 1.8e308`). Squaring two
 * `SAFE_MAX` values gives `1e30`, still finite Double.
 */
const val SAFE_MAX: Double = 1e15

/**
 * Clamp a divisor's magnitude to `≥ SAFE_MIN`, preserving sign.
 *
 * Substitutes `0.0` and `NaN` with `+SAFE_MIN`. `±Inf` passes through unchanged
 * (since `±Inf` is already a valid divisor: `a / ±Inf = ±0`); the resulting
 * `0` or any `NaN` from `Inf - Inf` patterns is scrubbed downstream by [safeOut].
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun safeDiv(d: Double): Double = when {
    d.isNaN() -> SAFE_MIN
    d > SAFE_MIN -> d
    d < -SAFE_MIN -> d
    d < 0.0 -> -SAFE_MIN
    else -> SAFE_MIN
}

/** Clamp an output value to `[-SAFE_MAX, +SAFE_MAX]`. Scrubs `NaN` to `0`. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun safeOut(v: Double): Double = when {
    v.isNaN() -> 0.0
    v > SAFE_MAX -> SAFE_MAX
    v < -SAFE_MAX -> -SAFE_MAX
    else -> v
}
