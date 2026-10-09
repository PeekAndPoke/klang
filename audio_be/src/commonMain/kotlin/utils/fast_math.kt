/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.pow

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
 * `wrapPhase` may miss `[0, 2π)` by rounding (a tiny negative, or `2π` itself; its KDoc says by how much), which
 * the fold absorbs, and keeps the quarter-period
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
 * The bound [fastExp2] promises against `2.0.pow(x)`, as a RELATIVE error: 1e-10, twice the
 * polynomial's fitted error (4.7e-11). A pitch ratio off by 1e-10 is 1.7e-7 cents.
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

// 2^f on [0, 1] as 1 + f + f·(f - 1)·r(f), r a degree-5 minimax polynomial (fitted 2026-09-15, max
// relative error 4.7e-11): the form pins p(0) = 1 and p(1) = 2 EXACTLY in floating point, so
// fastExp2 of an integer is that power of two bit for bit and fastExp(0) is 1, which the envelope
// curve's endpoints rely on. Published for the inline body only.
@PublishedApi
internal const val EXP2_R0 = 0.3068528153630951
@PublishedApi
internal const val EXP2_R1 = 0.06662641703363469
@PublishedApi
internal const val EXP2_R2 = 0.01112134290465686
@PublishedApi
internal const val EXP2_R3 = 0.0015072333051503507
@PublishedApi
internal const val EXP2_R4 = 0.0001650382256303314
@PublishedApi
internal const val EXP2_R5 = 2.150729820088863e-05

/**
 * `2^x` for a pitch ratio, eight multiply-adds instead of `pow`.
 *
 * The pitch paths turn semitones into a frequency ratio per sample: vibrato (`2^(sin · depth / 12)`)
 * and the pitch envelope (`2^(semitones · level / 12)`), one `pow` per sample per voice. This
 * splits `x` into an integer octave `n = floor(x)` and a fraction `f` in `[0, 1)`, evaluates `2^f`
 * by the polynomial and scales by `2^n` (the octaves 0 and -1 inline, the rest from
 * [EXP2_POW_TABLE]). Relative error under
 * [FAST_EXP2_MAX_REL_ERROR] everywhere in `(-32, 32)`, and exact at every integer (the
 * polynomial's ends are pinned), so a sweeping pitch has no step at an octave boundary. Outside
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
    val r = EXP2_R0 + f * (EXP2_R1 + f * (EXP2_R2 + f * (EXP2_R3 + f * (EXP2_R4 + f * EXP2_R5))))
    val p = 1.0 + f * (1.0 + (f - 1.0) * r)

    // A pitch path's argument is within an octave of 0 almost always (a vibrato depth, a pitch
    // envelope inside ±12 semitones): those two octaves skip the table, whose read on Kotlin/JS
    // goes through the lazy-init accessor of a top-level val.
    val scale = if (n == 0.0) 1.0 else if (n == -1.0) 0.5 else EXP2_POW_TABLE[n.toInt() + EXP2_TABLE_HALF]

    return p * scale
}

/** `log2(e)`: `e^x = 2^(x · LOG2_E)`. Published for the inline body only. */
@PublishedApi
internal const val LOG2_E = 1.4426950408889634

/**
 * `e^x` per sample: [fastExp2] of the scaled argument, one multiply on top of it.
 *
 * The envelopes' exponential curve (`exp(k · x)`, the default curve on every stage) and the
 * compressor's dB-to-linear gain (`exp(dB · ln10 / 20)`) call this instead of `kotlin.math.exp`.
 * Relative error is [FAST_EXP2_MAX_REL_ERROR] plus the rounding of the product, parts in 1e-15
 * for the arguments a pitch, an envelope or a gain produces; the fast range is `|x| < 22.18`
 * (32 octaves), beyond it (and for NaN and the infinities) the fallback is a platform `pow` of
 * the scaled argument, whose error is that product's rounding, about `|x|` ulp (5 ulp at 30,
 * 3e-14 relative at 700). `fastExp(0.0)` is exactly 1, and because the polynomial's ends are
 * pinned the error of `fastExp(x) - 1.0` shrinks with `x` (6e-15 absolute at `x = 1e-6`, parts
 * in 1e-9 of that difference), so the envelope curve near its start keeps its shape.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun fastExp(x: Double): Double = fastExp2(x * LOG2_E)
