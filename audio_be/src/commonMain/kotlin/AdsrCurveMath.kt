package io.peekandpoke.klang.audio_be

import kotlin.math.exp

// ─────────────────────────────────────────────────────────────────────────────
// Shape math for AdsrCurve.Exponential — shared by every envelope evaluator
// (EnvelopeRenderer, EnvelopeCalc, IgnitorEnvelopes) so the curve is identical
// across the amp VCA, the filter/FM envelopes, and the ignitor envelopes.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Curvature of [io.peekandpoke.klang.audio_bridge.AdsrCurve.Exponential].
 * Larger = steeper initial change (faster decay drop / sharper attack finish).
 * Tunable by ear; `3.0` ≈ a moderate analog decay, steeper-tailed than `Square`.
 */
internal const val ADSR_EXP_K: Double = 3.0

/** Normalisation so the exponential shape is exactly 0 at x=0 and 1 at x=1. */
@PublishedApi
internal val ADSR_EXP_NORM: Double = 1.0 / (exp(ADSR_EXP_K) - 1.0)

/**
 * True-exponential ADSR shape `g(x) = (e^(K·x) − 1)/(e^K − 1)` on `x ∈ [0,1]`,
 * with `g(0)=0`, `g(1)=1`. Convex (like `Square` but longer-tailed). For decay /
 * release the caller passes `omp = 1−p`, giving the natural "fast drop, long tail".
 *
 * NOTE: one `exp()` per call. In the per-sample renderers that's a transcendental
 * in the hot loop (the rest of the curve family is multiply-only). Acceptable while
 * Exponential is the decay-default experiment; if it shows up in benchmarks, swap
 * for a recursive multiply-only one-pole (per-sample) or a fast-exp approximation.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun adsrExpShape(x: Double): Double = (exp(ADSR_EXP_K * x) - 1.0) * ADSR_EXP_NORM
