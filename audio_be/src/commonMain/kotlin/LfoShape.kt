/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import kotlin.math.sin

/**
 * LFO waveforms for modulation sources. Internal-only enum — the DSL surface stays
 * string-based (sprudel / klangscript) and is mapped via [parseLfoShape].
 *
 * The vocabulary is deliberately the OSCILLATOR vocabulary: every accepted name is one the
 * user already knows from `s(...)` and the ignitor registry, aliases included. There are no
 * LFO-only names — in particular no `rampup`/`rampdown` pair, because the house already calls
 * the falling one [RAMP] (`Ignitors.ramp`, built at `polarity = -1.0`: the negated sawtooth)
 * and the rising one [SAWTOOTH].
 *
 * Dispatch happens inside a per-sample loop, so [lfoNorm] is `inline`: it folds [skewPhase]
 * and the `when` into the call site, where holding a `(Double) -> Double` instead would cost
 * a virtual `Function1` dispatch per sample.
 */
internal enum class LfoShape {
    SINE, TRIANGLE, SQUARE, SAWTOOTH, RAMP,
}

/**
 * Maps a DSL shape name to the enum. Unknown / null → [LfoShape.SINE]: an unrecognised name
 * degrades to the shipped waveform, never to silence and never to a throw (it arrives from a
 * user pattern value). Case-insensitive; the aliases mirror the ignitor registry's.
 */
internal fun parseLfoShape(shape: String?): LfoShape = when (shape?.lowercase()) {
    "triangle", "tri" -> LfoShape.TRIANGLE
    "square", "sqr", "pulse" -> LfoShape.SQUARE
    "sawtooth", "saw" -> LfoShape.SAWTOOTH
    "ramp" -> LfoShape.RAMP
    else -> LfoShape.SINE // "sine", "sin", null + fallback
}

/** Skew 0.0 — the duty at which the [skewPhase] warp is an exact identity. */
internal const val LFO_SYMMETRIC_DUTY = 0.5

/**
 * Division guard for [skewPhase], NOT a taste limit (the Motor stays raw): at duty 0 or 1 one
 * half of the waveform has zero width and the warp divides by zero. A thousandth of a cycle
 * for one half is already a step edge, so the musical extreme is untouched.
 */
private const val LFO_MIN_DUTY = 0.001

private const val LFO_MAX_DUTY = 1.0 - LFO_MIN_DUTY

/** Reciprocal of [TWO_PI]: the per-sample radian → cycle conversion is a multiply, not a divide.
 *  `internal`, not `private`: [lfoNorm] is an inline function and cannot reach a private one. */
internal const val INV_TWO_PI = 1.0 / TWO_PI

/**
 * Resolves an authored skew (-1..+1, 0 = symmetric) to the cycle position [skewPhase] splits
 * the waveform at.
 *
 * Positive skew means the LFO spends more of the cycle HIGH and negative more of it LOW, on
 * every waveform — the parameter-parity rule: one parameter, one audible meaning. For four of
 * the five shapes that is simply "stretch the waveform's first half", because their first half
 * is their loud half. [LfoShape.SAWTOOTH] is the exception (its level IS the cycle position,
 * so its first half is the QUIET one) and its duty therefore runs the other way; without the
 * flip, skew +0.6 would raise the mean level to 0.65-0.80 on four shapes and drop it to 0.35
 * on the fifth.
 *
 * A non-finite skew reads as symmetric: the shipped waveform, and it heals rather than
 * poisoning the voice.
 */
internal fun lfoDutyOf(skew: Double, shape: LfoShape): Double {
    if (!skew.isFinite()) {
        return LFO_SYMMETRIC_DUTY
    }

    val signed = if (shape == LfoShape.SAWTOOTH) -skew else skew

    return (LFO_SYMMETRIC_DUTY + LFO_SYMMETRIC_DUTY * signed).coerceIn(LFO_MIN_DUTY, LFO_MAX_DUTY)
}

/** First-half scale for [skewPhase]; hoisted out of the per-sample loop by the caller. */
internal fun lfoScaleFirst(duty: Double): Double = 0.5 / duty

/** Second-half scale for [skewPhase]; hoisted out of the per-sample loop by the caller. */
internal fun lfoScaleSecond(duty: Double): Double = 0.5 / (1.0 - duty)

/**
 * Warps a normalized phase so the waveform's first half occupies [duty] of the cycle.
 * [scaleFirst] and [scaleSecond] are [lfoScaleFirst] / [lfoScaleSecond] of that same duty,
 * passed in because they are loop-invariant and a per-sample divide is not.
 *
 * At [LFO_SYMMETRIC_DUTY] this is the BIT-EXACT identity, not merely a mathematical one: both
 * scales are exactly `1.0` there, `t * 1.0` is t, and `0.5 + (t - 0.5)` is exact for
 * `t >= 0.5` by Sterbenz. That is what lets an unskewed LFO be the shipped waveform.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun skewPhase(t: Double, duty: Double, scaleFirst: Double, scaleSecond: Double): Double {
    if (t < duty) {
        return t * scaleFirst
    }

    return 0.5 + (t - duty) * scaleSecond
}

/**
 * Evaluates the LFO at radian [phase] and returns a NORMALIZED level in `[0, 1]`: 1 = full,
 * 0 = the deepest point of the modulation.
 *
 * Landmarks are shared where the shapes have them: [LfoShape.SINE], [LfoShape.TRIANGLE] and
 * [LfoShape.SQUARE] all spend the cycle's FIRST half above the midpoint and split at t = 0.5,
 * so switching between them does not move the modulation against the beat. Where each ENTERS
 * that half differs by construction: sine and triangle at the midpoint rising, the square
 * already at its top — it has no midpoint to rise from. [LfoShape.SAWTOOTH] rises across the
 * whole cycle and [LfoShape.RAMP] is its mirror (the house relationship: ramp is the negated
 * sawtooth), so both put their edge AT the cycle boundary, where the authored phase offset
 * can place it deliberately. Under skew the two are TIME mirrors — `ramp(t) == saw(1 - t)` at
 * every duty; that coincides with the level negation only at skew 0.
 *
 * PRECONDITION: [phase] is already wrapped into `[0, TWO_PI)`, which is what keeps every
 * branch inside `[0, 1]`. `wrapPhase` delivers that for any rate the audio range can produce,
 * but its modulo arm rounds for absurd arguments (|rate| past ~1e7 Hz), and a phase that
 * escapes the period carries a LINEAR shape out of range with it — sine is the only branch
 * immune by construction. Not clamped: the reachable regime is the audio one.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun lfoNorm(
    shape: LfoShape,
    phase: Double,
    duty: Double,
    scaleFirst: Double,
    scaleSecond: Double,
): Double {
    // The unskewed sine is the shipped tremolo, evaluated on the radian accumulator ITSELF.
    // The normalized round trip (phase * INV_TWO_PI, then * TWO_PI) can move the argument by
    // an ulp, so this fast path is what keeps existing content bit-identical.
    if (shape == LfoShape.SINE && duty == LFO_SYMMETRIC_DUTY) {
        return (sin(phase) + 1.0) * 0.5
    }

    val t = skewPhase(phase * INV_TWO_PI, duty, scaleFirst, scaleSecond)

    return when (shape) {
        LfoShape.SINE -> (sin(t * TWO_PI) + 1.0) * 0.5

        LfoShape.TRIANGLE -> when {
            t < 0.25 -> 0.5 + 2.0 * t
            t < 0.75 -> 1.5 - 2.0 * t
            else -> 2.0 * t - 1.5
        }

        LfoShape.SQUARE -> if (t < 0.5) 1.0 else 0.0

        LfoShape.SAWTOOTH -> t

        LfoShape.RAMP -> 1.0 - t
    }
}
