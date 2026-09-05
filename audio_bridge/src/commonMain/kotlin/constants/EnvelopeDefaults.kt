/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
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

/**
 * Teardown fade for a voice whose VCA is switched OFF (`StageDsl.Vca.on = false` /
 * sprudel `.adsrOff()`), in seconds. The gate ramps linearly to zero over this window, ending
 * exactly on the last frame the voice renders (`floor(endFrame) - 1`).
 *
 * Scope: this guarantees silence at the VCA stage's OUTPUT. In a VCA-last pipeline (`modern`) that
 * is the voice's output; `pedal` puts the VCA second and its downstream stateful stages still ring
 * from a zero input. See `EnvelopeRenderer.renderGate`.
 *
 * **Why an envelope-less voice needs this and an ADSR one does not.** The VCA runs after the
 * exciter and its amp (last in `modern`; see the scope note above for `pedal`). With a curve it
 * drove the fully amplified signal to zero before teardown, so `Voice.render` could drop the voice
 * on a silent sample. With `on = false`
 * nothing guarantees that: an ignitor's own envelope sits BEFORE its amp stages, so a tail the
 * envelope has taken to ~1e-4 comes back out of a tube/drive stage 20 dB louder. Measured on Der
 * Schmetterling's guitar topology (`adsr → distort("tube") → highpass`): the last sample before
 * teardown was 0.015, a step straight to zero, against 0.00002 for the same patch with the
 * envelope after the amp. That step is the click.
 *
 * This is a FADE GUARD, not an envelope: it never shapes anything musical (the signal it acts on
 * is already tens of dB down), it is engine-controlled rather than authored, and its only job is
 * continuity at a boundary. Resist growing it into a "safety ADSR" — that invites the second
 * envelope back in through the safety door.
 *
 * Tunable by ear, like [ENV_DECLICK_SECONDS]. Long enough to turn the step into a ramp at low
 * notes, short enough to be inaudible against a release measured in tens of ms.
 *
 * Consumer: `EnvelopeRenderer.renderGate`.
 */
const val VCA_OFF_TEARDOWN_FADE_SECONDS: Double = 0.004
