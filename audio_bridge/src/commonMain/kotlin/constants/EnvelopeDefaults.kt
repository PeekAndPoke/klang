/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.constants

// ─────────────────────────────────────────────────────────────────────────────
// Envelope character defaults — wire defaults for `StageDsl.Vca` and the
// ignitor `adsr(...)` surface.
//
// The shape math that consumes them (`adsrExpShape`, `envDeclickCoeff`) stays
// in `audio_be/AdsrCurveMath.kt`; only the tunable values live here, so the
// authoring side and the engine cannot disagree about them.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Curvature of [io.peekandpoke.klang.audio_bridge.AdsrCurve.Exponential].
 * Larger = steeper initial change (faster decay drop / sharper attack finish).
 * Tunable by ear; `3.0` ≈ a moderate analog decay, steeper-tailed than `Square`.
 *
 * Consumers: `StageDsl.Vca.expK` (amp VCA, per-engine), and `AdsrCurveMath`'s
 * no-arg `adsrExpShape` for the filter/FM and ignitor envelopes.
 */
const val ADSR_EXP_K: Double = 3.0

/**
 * Time constant (seconds) of the VCA-gain de-click one-pole.
 *
 * The shape curves are C0-continuous (the value reaches its endpoints exactly)
 * but NOT C1-continuous: at a segment join (attack→decay peak, gate-off,
 * release→silence) the gain changes slope abruptly. That corner is a fixed-size
 * event that radiates a broadband click; on a low note the slow carrier can't
 * mask it, so it reads as a "plop", while a high note's fast carrier hides it.
 * A short one-pole low-pass on the gain rounds the corner without altering the
 * envelope's character. Measured with a corner/floor metric: ~0.5 ms gives ≈25×
 * corner reduction at 40 Hz with a 0-residual tail and only softens sub-5 ms attacks.
 * (The `AdsrPlopAnalysisTest` harness those numbers came from is no longer in the repo —
 * the figures are kept as the provenance of the value, not as a live reference.)
 * Tunable by ear, like [ADSR_EXP_K].
 *
 * Consumer: `StageDsl.Vca.declickSeconds` → `EnvelopeRenderer`.
 */
const val ENV_DECLICK_SECONDS: Double = 0.001
