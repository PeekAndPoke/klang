/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

// ── DSP Constants ────────────────────────────────────────────────────────────

const val TWO_PI = PI * 2.0

// ── DSP Utilities ────────────────────────────────────────────────────────────

/** Threshold below which filter state is flushed to zero to avoid denormal slowdowns. */
const val DENORMAL_THRESHOLD = 1e-15

/**
 * Returns `0.0` if this value is NaN, otherwise the value unchanged.
 *
 * Used to sterilise sample values before they enter IIR / FIR state where a
 * single NaN would propagate forever (IIR: state becomes NaN; FIR: NaN smears
 * across the entire delay line until it scrolls out). Encodes the engine-wide
 * `// NaN-guard (NaN ≠ NaN)` idiom — the IEEE-754 property that `NaN != NaN`
 * is the cheapest finite NaN test.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun Double.nanGuard(): Double = if (this != this) 0.0 else this

/** Flushes a value to zero if it is below the denormal threshold. */
@Suppress("NOTHING_TO_INLINE")
inline fun Double.flushDenormal(): Double = if (abs(this) < DENORMAL_THRESHOLD) 0.0 else this

/**
 * Wraps this phase into `[0, period)`.
 *
 * Fast path: when this phase overshoots by ≤1 period (the common case for stable oscillators),
 * uses a single conditional subtract — JS `%` on doubles is much slower than this for the
 * normal hot-loop case. Off-path: when this phase is way out of range (e.g. an upstream pitch
 * mod produced an extreme ratio) or non-finite, falls back to a single modulo step (and
 * recovers `0.0` for `Inf`/`NaN`) so we never enter an O(N) subtract loop or hang the audio
 * thread. See `audio/ref/numerical-safety.md`.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun Double.wrapPhase(period: Double): Double {
    if (!this.isFinite()) return 0.0
    var p = this
    if (p >= 2.0 * period || p < -period) {
        // Way out of range — use modulo so we don't loop millions of times.
        p -= period * floor(p / period)
    } else {
        // Common case: at most one overshoot in either direction.
        if (p >= period) {
            p -= period
        } else if (p < 0.0) p += period
    }
    return p
}

/**
 * First-order PolyBLEP residual for anti-aliased discontinuities.
 *
 * Used in band-limited oscillators (saw, square, pulse) to smooth the signal discontinuity.
 * The receiver `t` is the normalised phase (`0..1`); [dt] is the normalised phase increment
 * per sample. Returns the correction to subtract from the naive waveform at the discontinuity.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun Double.polyBlep(dt: Double): Double {
    val t = this
    var correction = 0.0
    if (t < dt) {
        val r = t / dt
        correction += r + r - r * r - 1.0
    }
    if (t > 1.0 - dt) {
        val r = (t - 1.0) / dt
        correction += r * r + r + r + 1.0
    }
    return correction
}

/**
 * The **one** piecewise-linear waveform shape behind saw / ramp / square / pulse / triangle (and their
 * raw variants). A `±1` trapezoid in four segments: a **rise** ramp `−1→+1` over `[0, riseEnd]`, a
 * **high** plateau `+1` to [highEnd], a **fall** ramp `+1→−1` to [fallEnd], then a **low** plateau `−1`.
 *
 * Slopes are precomputed by the caller (`riseSlope = 2/riseEnd`, `fallSlope = 2/fallLen`) so this is
 * **multiply-only**. A segment of zero length is simply skipped (empty plateaus → saw/triangle; empty
 * ramps → instant/raw edges). No PolyBLEP — a finite-slope edge is inherently band-limited.
 *
 * Configs (see `WaveVoiceState`): saw = empty plateaus (`highEnd = riseEnd`, `fallEnd = 1`); square/pulse
 * = min-flank ramps + plateaus; triangle = `riseEnd = 0.5`, `fallEnd = 1`; raw = ramp length 0.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun waveTrapezoid(
    p: Double,
    riseEnd: Double,
    highEnd: Double,
    fallEnd: Double,
    riseSlope: Double,
    fallSlope: Double,
): Double = when {
    p < riseEnd -> -1.0 + p * riseSlope                   // rise −1 → +1
    p < highEnd -> 1.0                                    // high plateau
    p < fallEnd -> 1.0 - (p - highEnd) * fallSlope        // fall +1 → −1
    else -> -1.0                                          // low plateau
}

/**
 * Fast modulo for values that overshoot by at most one period.
 *
 * Typical for per-sample phase accumulators where the phase increments by a small dt each
 * sample and can overshoot [period] by at most one step. Avoids JS `%` on doubles which
 * internally computes a full floating-point division (`a - floor(a/b) * b`).
 *
 * Use this instead of `value % period` in per-sample DSP loops. NOT safe for values that
 * could overshoot by more than one period — use [wrapPhase] for those cases.
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
 * Treats this value as a frequency in Hz and returns the frequency shifted by
 * [detuneSemitones] semitones. E.g. `440.0.applySemitoneDetuneToFrequency(12.0) == 880.0`.
 */
fun Double.applySemitoneDetuneToFrequency(detuneSemitones: Double): Double =
    this * 2.0.pow(detuneSemitones / 12.0)

// ═══════════════════════════════════════════════════════════════════════════════
// Numerical Safety Bounds
// ═══════════════════════════════════════════════════════════════════════════════
//
// Klang's safety contract — see `audio/ref/numerical-safety.md` for the full story.
//
// Every arithmetic operator that can produce `NaN`/`Inf` clamps either its inputs
// (divisor-class ops: Div, Mod, Recip) or its output (output-clamp ops: Times,
// Pow, Exp, Sq, Mul-by-constant). Naturally bounded ops (Plus, Minus, Lerp,
// Range, Clamp, Min, Max, Abs, Neg, Sign, Floor, Ceil, Round, Frac, Tanh, Sqrt,
// Log) need no extra guard.
//
// Values match the SuperCollider / ChucK / STK convention (`zapgremlins`,
// `CK_DDN_*`): ±300 dBFS, well below any audible signal, well above subnormal.
// `1 / SAFE_MIN = SAFE_MAX` ensures a reciprocal of the smallest allowed
// divisor lands at the largest allowed output — round-trip safe. (Design intent, not an FP
// bit-fact: 1.0 / 1e-15 evaluates to 9.999999999999999e14, just BELOW SAFE_MAX — so a
// reciprocal of a safeDiv'd value can never actually engage safeOut.)

/**
 * Smallest allowed magnitude for a divisor (or reciprocal input) in audio arithmetic.
 *
 * Values closer to zero are clamped to `±SAFE_MIN` (sign preserved) to prevent
 * `1/x` from overflowing. ≈ -300 dBFS — well below any audible signal.
 */
const val SAFE_MIN: AudioSample = 1e-15

/**
 * Largest allowed output magnitude for ops that can grow values (`Times`, `Pow`,
 * `Exp`, `Sq`, `Mul-by-constant`).
 *
 * Outputs above this are clamped to `±SAFE_MAX`. ≈ +300 dBFS — vastly above any
 * musical signal, well below `Double.MAX_VALUE` (`≈ 1.8e308`). Squaring two
 * `SAFE_MAX` values gives `1e30`, still finite Double.
 */
const val SAFE_MAX: AudioSample = 1e15

/**
 * Clamp a divisor's magnitude to `≥ SAFE_MIN`, preserving sign.
 *
 * Substitutes `0.0` and `NaN` with `+SAFE_MIN`. `±Inf` passes through unchanged
 * (since `±Inf` is already a valid divisor — `a / ±Inf = ±0`); the resulting
 * `0` or any `NaN` from `Inf - Inf` patterns is scrubbed downstream by [safeOut].
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun safeDiv(d: AudioSample): AudioSample = when {
    d.isNaN() -> SAFE_MIN
    d > SAFE_MIN -> d
    d < -SAFE_MIN -> d
    d < 0.0 -> -SAFE_MIN
    else -> SAFE_MIN
}

/** Clamp an output value to `[-SAFE_MAX, +SAFE_MAX]`. Scrubs `NaN` to `0`. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun safeOut(v: AudioSample): AudioSample = when {
    v.isNaN() -> 0.0
    v > SAFE_MAX -> SAFE_MAX
    v < -SAFE_MAX -> -SAFE_MAX
    else -> v
}
