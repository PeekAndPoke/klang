/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.constants

// ─────────────────────────────────────────────────────────────────────────────
// The PITCH envelope's defaults: the stage times and the sustain every surface
// falls back to when the author leaves a stage out.
//
// They live here because they are wire defaults in the sense of the house rule
// (`/dsl-design` section 4: defaults are the same on every surface and live in
// ONE place). Two surfaces read them (phase 3 step 5b (c1), decision D3):
//
//  - the Ignitor pitch envelope (`IgnitorDsl.PitchEnvelope`'s field defaults and
//    `pitchEnvelopeModIgnitor` in `audio_be`, its non-finite sustain too);
//  - the voice strip's pitch envelope, sprudel's `penv(amount, attack, decay,
//    sustain, release)`, which `VoiceFactory` resolves at note-on.
//
// So `penv(24)` and `pitchEnvelope(24)` sweep the same way. The CURVE is not a
// value here: both take `MOD_ENV_CURVE` (`EnvelopeDefaults.kt`). The depth has no
// default: `amount` / `semitones` is the envelope's switch, 0 is "no envelope".
//
// The numbers are the Ignitor node's, which it has carried since the pitch
// envelope moved onto `adsr` (step 3d(i)). The strip read an unset attack and
// decay as 0 before (c1), which made a bare `penv(24)` a silent no-op there.
// ─────────────────────────────────────────────────────────────────────────────

/** Pitch-envelope attack in seconds. */
const val PITCH_ENV_ATTACK_SEC: Double = 0.01

/** Pitch-envelope decay in seconds. */
const val PITCH_ENV_DECAY_SEC: Double = 0.1

/**
 * Pitch-envelope sustain, a share of the depth: 0 returns to the note after the decay. Also what a
 * NON-FINITE sustain reads as, on both surfaces.
 */
const val PITCH_ENV_SUSTAIN_LEVEL: Double = 0.0

/** Pitch-envelope release in seconds: 0 is back on the note at the gate frame. */
const val PITCH_ENV_RELEASE_SEC: Double = 0.0
