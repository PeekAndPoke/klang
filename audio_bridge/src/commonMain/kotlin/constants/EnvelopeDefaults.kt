/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.constants

import io.peekandpoke.klang.audio_bridge.AdsrCurve

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
 * Consumers: `StageDsl.Vca.expK` (amp VCA, per-engine, until the Pipeline DSL retires) and the
 * envelope law `EnvelopeCore`, where it is the ONE bend of every other envelope's exponential stages:
 * the Ignitor `adsr`'s per-envelope `expK` knob was removed in phase 3 step 3c (maintainer,
 * 2026-09-25; a per-curve bend is a later design).
 */
const val ADSR_EXP_K: Double = 3.0

/**
 * The curve of every stage of a MODULATION envelope that the author did not shape with
 * `curves` (`x => x.adsr(a, d, s, r, e => e.curves(...))`): the Ignitor filter nodes' cutoff
 * envelope (`IgnitorDsl.Lowpass` and its three siblings), the Ignitor pitch envelope
 * (`IgnitorDsl.PitchEnvelope`) and the Ignitor FM index envelope (`IgnitorDsl.Fm`, which has no
 * curve knob yet), and on the voice strip the filter, FM and pitch envelopes (`VoiceFactory`; the
 * pitch envelope's curves come from sprudel's `penvCurves` when named). The Ignitor
 * curve knobs default to this curve's `AdsrCurves` index, and a knob that cannot be read at build,
 * or reads as a bad index, falls back to it too.
 *
 * It is [AdsrCurve.Exponential], the house curve every other envelope already had
 * ([AdsrCurve.Default], bending at [ADSR_EXP_K]): decision D3 of `docs/tasks/builtin-instruments.md`
 * (maintainer, 2026-09-25), which moved the Ignitor filter, pitch and FM envelopes off the LINEAR
 * law they had before, a deliberate sound change. It stays a constant of its own, and not a
 * reference to [AdsrCurve.Default], because the maintainer recorded the modulation envelopes'
 * default as ONE decision of its own.
 */
val MOD_ENV_CURVE: AdsrCurve = AdsrCurve.Exponential

/**
 * Sustain level of the IGNITOR `adsr(...)` surface, and what `AdsrIgnitor` substitutes for a
 * NON-FINITE one.
 *
 * Scope: the substitution is the IGNITOR envelope's. The voice STRIP's VCA substitutes its own
 * default for a non-finite sustain, [VOICE_ADSR_SUSTAIN_LEVEL]; the strip's filter and FM envelopes
 * have no non-finite guard.
 *
 * Deliberately NOT the same number as `AdsrDef.defaultSynth.sustain` (1.0), which is the voice
 * STRIP's VCA default: the strip's envelope is an amp applied to a finished voice and holds it at
 * full level when nothing is written, while an ignitor's own envelope is a shape inside the
 * instrument and 0.7 is what the door has always meant by "a sustained note". Do not unify them
 * without deciding which sound moves.
 *
 * Consumers: `IgnitorDsl.Adsr.sustainLevel`'s default, and `AdsrIgnitor`'s non-finite
 * substitution (see its `finiteOr` note; the `Param` leaf's unset rule cannot reach a value that
 * was authored non-finite).
 */
const val ADSR_SUSTAIN_LEVEL: Double = 0.7

// ── The VOICE envelope: what every voice gets when the pattern writes nothing ──
//
// `AdsrDef.Std.defaultSynth` (the voice strip's VCA) and the envelope slots of `classic()`
// (`IgnitorDsl.Slots.adsr`) both read these four, so the classic tail's unwritten envelope is the
// strip's unwritten envelope by construction. They are NOT the Ignitor `adsr(...)` node's own
// defaults (sustain [ADSR_SUSTAIN_LEVEL], release 0.3): see [ADSR_SUSTAIN_LEVEL] for why the two
// differ and must not be unified without deciding which sound moves.

/** Voice envelope attack in seconds. */
const val VOICE_ADSR_ATTACK_SEC: Double = 0.01

/** Voice envelope decay in seconds. */
const val VOICE_ADSR_DECAY_SEC: Double = 0.1

/**
 * Voice envelope sustain level, 0 to 1: the voice holds at full level when nothing is written. Also what
 * the strip VCA reads a non-finite sustain as.
 */
const val VOICE_ADSR_SUSTAIN_LEVEL: Double = 1.0

/**
 * Voice envelope release in seconds. Also the lifetime of an `adsrOff` voice past its gate, on the
 * strip and on `classic()` alike (the off envelope still reports this release as its tail).
 */
const val VOICE_ADSR_RELEASE_SEC: Double = 0.05

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
 * is the voice's output; a custom pipeline that puts the VCA ahead of stateful stages leaves them
 * ringing from a zero input. See `EnvelopeRenderer.renderGate`.
 *
 * **Why an envelope-less voice needs this and an ADSR one does not.** The VCA runs after the
 * exciter and its amp (last in `modern`; see the scope note above for custom orders). With a curve it
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
