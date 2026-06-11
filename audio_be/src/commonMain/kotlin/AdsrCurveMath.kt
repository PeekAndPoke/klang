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

// ─────────────────────────────────────────────────────────────────────────────
// De-click smoother on the final amplitude-envelope gain.
//
// The shape curves are C0-continuous (the value reaches its endpoints exactly)
// but NOT C1-continuous: at a segment join (attack→decay peak, gate-off,
// release→silence) the gain changes slope abruptly. That corner is a fixed-size
// event that radiates a broadband click; on a low note the slow carrier can't
// mask it, so it reads as a "plop", while a high note's fast carrier hides it.
// A short one-pole low-pass on the gain rounds the corner without altering the
// envelope's character. See AdsrPlopAnalysisTest for the corner/floor metric:
// ~0.5ms gives ≈25x corner reduction at 40Hz with a 0-residual tail and only
// softens sub-5ms attacks. Tunable by ear, like ADSR_EXP_K.
// ─────────────────────────────────────────────────────────────────────────────

/** Time constant (seconds) of the VCA-gain de-click one-pole. */
internal const val ENV_DECLICK_SECONDS: Double = 0.001

/** Per-sample one-pole coefficient for [ENV_DECLICK_SECONDS] at [sampleRate] Hz. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun envDeclickCoeff(sampleRate: Double): Double =
    1.0 - exp(-1.0 / (ENV_DECLICK_SECONDS * sampleRate))
