/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.constants.ADSR_EXP_K
import kotlin.math.exp

// ─────────────────────────────────────────────────────────────────────────────
// Shape math for AdsrCurve.Exponential — shared by every envelope evaluator
// (EnvelopeRenderer, EnvelopeCalc, IgnitorEnvelopes) so the curve is identical
// across the amp VCA, the filter/FM envelopes, and the ignitor envelopes.
//
// The tunable values themselves (ADSR_EXP_K, ENV_DECLICK_SECONDS) live in
// `audio_bridge/constants/EnvelopeDefaults.kt` — they are the defaults of
// `StageDsl.Vca` fields, so both sides must read one declaration. Only the math
// lives here.
// ─────────────────────────────────────────────────────────────────────────────

/** Normalisation factor for [adsrExpShape] at curvature [k] — makes `g(0)=0`, `g(1)=1`. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun adsrExpNorm(k: Double): Double = 1.0 / (exp(k) - 1.0)

/** Normalisation for the global-default curvature [ADSR_EXP_K]. */
@PublishedApi
internal val ADSR_EXP_NORM: Double = adsrExpNorm(ADSR_EXP_K)

/**
 * True-exponential ADSR shape `g(x) = (e^(K·x) − 1)/(e^K − 1)` on `x ∈ [0,1]`,
 * with `g(0)=0`, `g(1)=1`. Convex (like `Square` but longer-tailed). For decay /
 * release the caller passes `omp = 1−p`, giving the natural "fast drop, long tail".
 *
 * This no-arg form uses the global-default [ADSR_EXP_K] (the filter/FM and ignitor
 * envelopes). The amp VCA passes a per-engine curvature via [adsrExpShape] below.
 *
 * NOTE: one `exp()` per call. In the per-sample renderers that's a transcendental
 * in the hot loop (the rest of the curve family is multiply-only). Acceptable while
 * Exponential is the decay-default experiment; if it shows up in benchmarks, swap
 * for a recursive multiply-only one-pole (per-sample) or a fast-exp approximation.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun adsrExpShape(x: Double): Double = (exp(ADSR_EXP_K * x) - 1.0) * ADSR_EXP_NORM

/** Parameterized exp shape at curvature [k] with precomputed [norm] = [adsrExpNorm]\(k\). */
@Suppress("NOTHING_TO_INLINE")
internal inline fun adsrExpShape(x: Double, k: Double, norm: Double): Double = (exp(k * x) - 1.0) * norm

// ─────────────────────────────────────────────────────────────────────────────
// De-click smoother on the final amplitude-envelope gain.
//
// The shape curves are C0-continuous (the value reaches its endpoints exactly)
// but NOT C1-continuous: at a segment join (attack→decay peak, gate-off,
// release→silence) the gain changes slope abruptly. That corner is a fixed-size
// event that radiates a broadband click; on a low note the slow carrier can't
// mask it, so it reads as a "plop", while a high note's fast carrier hides it.
// A short one-pole low-pass on the gain rounds the corner without altering the
// envelope's character.
//
// The time constant itself is ENV_DECLICK_SECONDS, in audio_bridge/constants — see its
// KDoc for the corner/floor measurement rather than repeating it here.
// ─────────────────────────────────────────────────────────────────────────────

/** Per-sample one-pole coefficient for a [declickSeconds] time constant at [sampleRate] Hz. */
@Suppress("NOTHING_TO_INLINE")
internal inline fun envDeclickCoeff(declickSeconds: Double, sampleRate: Double): Double =
    1.0 - exp(-1.0 / (declickSeconds * sampleRate))
