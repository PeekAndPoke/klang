/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// Shared constants and helpers for [AnalogDrift], the one two-layer OU drift
// lane (fast one-pole jitter + slow Ornstein–Uhlenbeck). The tuning constants
// and the coefficient math live here so they're defined in exactly one place;
// [DriftLanes] stacks the lanes for the multi-voice oscillators and adds no
// coefficients of its own.
// ─────────────────────────────────────────────────────────────────────────────

/** Fast-jitter time constant: ~50 ms — micro-wobble of a stable VCO. */
internal const val ANALOG_FAST_TAU_SEC: Double = 0.05

/** Slow-drift time constant: ~10 s — lazy pitch wander. */
internal const val ANALOG_SLOW_TAU_SEC: Double = 10.0

/** Mean-reversion strength as a fraction of α. 0 = random walk, 1 = strong pull. */
internal const val ANALOG_MEAN_REVERSION_RATIO: Double = 0.5

/** Target peak deviation of the fast layer, in cents, per unit `analog`. */
internal const val ANALOG_FAST_PEAK_CENTS: Double = 0.2

/** Target peak deviation of the slow layer, in cents, per unit `analog`. */
internal const val ANALOG_SLOW_PEAK_CENTS: Double = 0.8

/** One cent as a multiplicative offset: `2^(1/1200) - 1`. */
internal const val ANALOG_CENT_PER_MUL: Double = 5.7780e-4

/** "Peak" = how many σ we treat as the perceptual envelope. */
internal const val ANALOG_PEAK_SIGMAS: Double = 3.0

/** σ of uniform [-1, 1]: `1/√3`. */
internal const val ANALOG_SIGMA_X: Double = 0.5773502691896257

/** `1 / Int.MAX_VALUE` — maps a signed Int to ≈ [-1, 1]. */
internal const val ANALOG_INT_INV: Double = 1.0 / 2147483647.0

// ─────────────────────────────────────────────────────────────────────────────
// The coefficient law, as pure functions: [AnalogDrift.seed] evaluates them into its own fields, so a lane carries
// no holder object and its seed allocates nothing. `stepRate` is the rate the lane is stepped at (the block rate for
// every oscillator lane since 2026-09-15).
// ─────────────────────────────────────────────────────────────────────────────

/** A layer's one-pole coefficient: `1 / (tau * rate)`, for its time constant [tauSec] at [stepRate] steps per second. */
internal fun analogDriftAlpha(tauSec: Double, stepRate: Int): Double = 1.0 / (tauSec * stepRate.toDouble())

/** The slow layer's mean reversion, a fraction of its [alphaSlow]. */
internal fun analogDriftBetaSlow(alphaSlow: Double): Double = alphaSlow * ANALOG_MEAN_REVERSION_RATIO

/**
 * Steady-state RMS of the fast smoother given uniform [-1, 1] white noise input (σ²_x = 1/3), the exact AR(1) form:
 * y' = (1 - a) y + a x has σ²_y = a² / (1 - (1 - a)²) × σ²_x, which is a / (2 - a). The small-a approximation (a / 2)
 * was within 0.01 % at a sample-rate step and 1.4 % off at the block rate, where the lanes step since 2026-09-15.
 */
internal fun analogDriftSigmaFast(alphaFast: Double): Double =
    sqrt(alphaFast * alphaFast / (1.0 - (1.0 - alphaFast) * (1.0 - alphaFast))) * ANALOG_SIGMA_X

/** The slow layer's steady-state RMS, the OU with `a + b` in the recurrence (see [analogDriftSigmaFast]). */
internal fun analogDriftSigmaSlow(alphaSlow: Double, betaSlow: Double): Double =
    sqrt(alphaSlow * alphaSlow / (1.0 - (1.0 - alphaSlow - betaSlow) * (1.0 - alphaSlow - betaSlow))) * ANALOG_SIGMA_X

/** A layer's output scale: `analog × target_cents × cent_to_mul / (3σ)`, 3σ being about the peak amplitude. */
internal fun analogDriftScale(analog: Double, peakCents: Double, sigma: Double): Double =
    analog * peakCents * ANALOG_CENT_PER_MUL / (ANALOG_PEAK_SIGMAS * sigma)

/**
 * The rate an [AnalogDrift] lane is stepped at when it advances once per block (2026-09-15, every
 * oscillator lane does): blocks per second. The coefficients follow the rate, so the time
 * constants in seconds and the peak cents are the same as they were per sample; what is gone is
 * the noise above HALF the block rate (about 1 % of the fast layer's variance, the fast layer
 * being a fifth of the depth) and, with the linear ramp across the block, a first-order-hold
 * tilt of up to -3.9 dB at that edge. The filter drift has run at this rate since before.
 */
internal fun analogDriftStepRate(sampleRate: Int, blockFrames: Int): Int =
    (sampleRate / blockFrames.coerceAtLeast(1)).coerceAtLeast(1)

/** Standard-normal sample via Box-Muller. Two `rng.nextDouble()` calls. */
internal fun analogDriftGaussian(rng: Random): Double {
    val u1 = rng.nextDouble().coerceAtLeast(1e-12)
    val u2 = rng.nextDouble()
    return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
}
