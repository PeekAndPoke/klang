/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

// ── DSP Constants ────────────────────────────────────────────────────────────

const val TWO_PI = PI * 2.0
const val HALF_PI = PI * 0.5

/**
 * The largest `|fastSin(x) - sin(x)|` over the engine's phase range, asserted by `FastSinSpec`:
 * -200 dB, an order above the polynomial's fitted error (1.3e-11), so the bound stays honest
 * across platforms and rounding.
 */
const val FAST_SIN_MAX_ERROR = 1e-10

// Degree-11 odd minimax polynomial for sin on [-π/2, π/2] (fitted 2026-09-15, max error 1.3e-11):
// sin(x) ≈ x · (S1 + x²·(S3 + x²·(S5 + x²·(S7 + x²·(S9 + x²·S11))))). Published for the inline body only.
@PublishedApi
internal const val SIN_S1 = 0.9999999998893945
@PublishedApi
internal const val SIN_S3 = -0.1666666654102997
@PublishedApi
internal const val SIN_S5 = 0.00833332925508402
@PublishedApi
internal const val SIN_S7 = -0.00019840702003847582
@PublishedApi
internal const val SIN_S9 = 2.7518821382393724e-06
@PublishedApi
internal const val SIN_S11 = -2.37942173904353e-08

/**
 * `sin` for an oscillator phase, six multiply-adds instead of a transcendental call.
 *
 * The oscillators spend their per-sample budget almost entirely in `kotlin.math.sin`: an
 * 8-partial bank is 384 000 calls per second per voice (measured 2026-09-15, the Orchestertrommel's
 * bank alone cost 0.027 RTF). This is the same phase-accumulator sine with the function swapped:
 * the phase, the drift and every modulation stay exactly what they were, only the waveform's
 * shape differs, by parts in 10^11 (-217 dB), which no bus carries. Pure arithmetic with a fixed
 * evaluation order, so the FUNCTION is bit-identical on JVM and JS, which `Math.sin` never
 * promised (a whole voice still is not: `pow`, `ln` and `cos` upstream of the phase, in the
 * partial gains, the detune and the drift seed, stay platform transcendentals).
 *
 * Contract: [phase] in the engine's wrapped range `[0, 2π)` (`wrapPhase(TWO_PI)`), where the
 * error is under [FAST_SIN_MAX_ERROR]. The fold below is exact for one more quarter period on
 * either side, `[-π/2, 5π/2]`; beyond that the polynomial diverges fast (-75 dB off at `3π`,
 * over full scale past `4π`), so a caller wraps FIRST and never feeds a runaway phase.
 * `wrapPhase` may miss `[0, 2π)` by an ulp, which the fold absorbs, and keeps the quarter-period
 * margin up to a magnitude of a few 1e15; an increment that large takes a frequency at the
 * `SAFE_MAX` ceiling (1e15) under a huge pitch-mod ratio on top, deep abuse that gives bounded
 * garbage. NaN in, NaN out, like `sin`; an infinite phase gives an infinity where `sin` gave
 * NaN, another reason the wrap comes first.
 * Not for waveshaping (`ShapingFuncs.sineShaper`), whose input is not a phase.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun fastSin(phase: Double): Double {
    // [0, 2π) -> x in [-π, π) with sin(phase) = -sin(x), then fold to [-π/2, π/2]: sin(π - x) = sin(x).
    var x = phase - PI

    if (x > HALF_PI) {
        x = PI - x
    } else if (x < -HALF_PI) {
        x = -PI - x
    }

    val x2 = x * x

    return -x * (SIN_S1 + x2 * (SIN_S3 + x2 * (SIN_S5 + x2 * (SIN_S7 + x2 * (SIN_S9 + x2 * SIN_S11)))))
}

/**
 * The bound [fastExp2] promises against `2.0.pow(x)`, as a RELATIVE error: 1e-10, two and a half
 * times the polynomial's fitted error (4.0e-11). A pitch ratio off by 1e-10 is 1.7e-7 cents.
 */
const val FAST_EXP2_MAX_REL_ERROR = 1e-10

/** Half the width of [EXP2_POW_TABLE]: `fastExp2` covers `(-32, 32)` itself and falls back outside. */
@PublishedApi
internal const val EXP2_TABLE_HALF = 32

/**
 * `2^n` for `n` in `[-32, 32)`, indexed by `n + 32`, built by exact doubling from `2^-32` (a power
 * of two is exact in a double; `pow` would make the table's bits a platform's business).
 * Published for the inline body only.
 */
@PublishedApi
internal val EXP2_POW_TABLE: DoubleArray = DoubleArray(2 * EXP2_TABLE_HALF).also { table ->
    var v = 1.0

    repeat(EXP2_TABLE_HALF) { v *= 0.5 }

    for (i in table.indices) {
        table[i] = v
        v *= 2.0
    }
}

// Degree-7 minimax polynomial for 2^f on [0, 1] (fitted 2026-09-15, max relative error 4.0e-11):
// 2^f ≈ E0 + f·(E1 + f·(E2 + f·(E3 + f·(E4 + f·(E5 + f·(E6 + f·E7)))))). Published for the inline body only.
@PublishedApi
internal const val EXP2_E0 = 0.9999999999597889
@PublishedApi
internal const val EXP2_E1 = 0.6931471860838887
@PublishedApi
internal const val EXP2_E2 = 0.24022638461800497
@PublishedApi
internal const val EXP2_E3 = 0.05550512685953342
@PublishedApi
internal const val EXP2_E4 = 0.009614017011914364
@PublishedApi
internal const val EXP2_E5 = 0.0013422634823946383
@PublishedApi
internal const val EXP2_E6 = 0.00014352314034646327
@PublishedApi
internal const val EXP2_E7 = 2.1498763706405082e-05

/**
 * `2^x` for a pitch ratio, seven multiply-adds and a table read instead of `pow`.
 *
 * The pitch paths turn semitones into a frequency ratio per sample: vibrato (`2^(sin · depth / 12)`)
 * and the pitch envelope (`2^(semitones · level / 12)`), one `pow` per sample per voice. This
 * splits `x` into an integer octave `n = floor(x)` and a fraction `f` in `[0, 1)`, evaluates `2^f`
 * by the polynomial and scales by `2^n` (the octaves 0 and -1 inline, the rest from
 * [EXP2_POW_TABLE]). Relative error under
 * [FAST_EXP2_MAX_REL_ERROR] everywhere in `(-32, 32)`; across an integer boundary the two
 * polynomial ends agree to the same bound, so a sweeping pitch has no step there. Outside
 * `(-32, 32)`, and for NaN and the infinities (the guard reads `!(inside)`), it IS `2.0.pow(x)`:
 * the fallback is exact, the fast path is for the ratios a pitch path produces. Pure arithmetic
 * with a fixed evaluation order on the fast path, so it is bit-identical on JVM and JS.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun fastExp2(x: Double): Double {
    if (!(x > -EXP2_TABLE_HALF.toDouble() && x < EXP2_TABLE_HALF.toDouble())) { // NaN-guard (NaN ≠ NaN), and ±Inf
        return 2.0.pow(x)
    }

    val n = floor(x)
    val f = x - n
    val p = EXP2_E0 + f * (EXP2_E1 + f * (EXP2_E2 + f * (EXP2_E3 + f * (EXP2_E4 + f * (EXP2_E5 + f * (EXP2_E6 + f * EXP2_E7))))))

    // A pitch path's argument is within an octave of 0 almost always (a vibrato depth, a pitch
    // envelope inside ±12 semitones): those two octaves skip the table, whose read on Kotlin/JS
    // goes through the lazy-init accessor of a top-level val.
    val scale = if (n == 0.0) 1.0 else if (n == -1.0) 0.5 else EXP2_POW_TABLE[n.toInt() + EXP2_TABLE_HALF]

    return p * scale
}

// ── DSP Utilities ────────────────────────────────────────────────────────────

/** Threshold below which filter state is flushed to zero to avoid denormal slowdowns. */
const val DENORMAL_THRESHOLD = 1e-15

/**
 * Returns `0.0` if this value is NaN, otherwise the value unchanged.
 *
 * Used to sterilise a SAMPLE before it enters FIR / delay-line state, where a NaN would
 * smear across the whole line and — in a recirculating ring — never scroll out. Encodes the
 * engine-wide `// NaN-guard (NaN ≠ NaN)` idiom: the IEEE-754 property that `NaN != NaN` is
 * the cheapest possible NaN test, one compare with no `abs` and no call.
 *
 * DIVISION OF LABOUR, since the master round: this guards the SAMPLE and catches NaN only;
 * [flushState] guards the IIR STATE and catches non-finite as well as denormal. The permanent
 * IIR-latch class belongs to [flushState] — do not widen this one to chase it, because an Inf
 * passing through a sample path is the raw engine behaving as designed, while an Inf settling
 * into filter state is a filter that can never recover.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun Double.nanGuard(): Double = if (this != this) 0.0 else this

/**
 * Flushes an IIR/FIR carry to zero unless it is a NORMAL, FINITE magnitude.
 *
 * Two failure modes, one guard, because both say the same thing — this value must not be
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
 * Bit-identical for every normal finite value — only the rejected branch changed — so no
 * shipped sound moves.
 *
 * Shape matters in a per-sample loop: `abs` + two compares + a select, and NO branch on the
 * data. The range test feeds a conditional move, and the sign — the one genuinely
 * unpredictable bit in an audio signal — is masked away by `abs` rather than branched on.
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
