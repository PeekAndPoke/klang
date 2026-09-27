/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.constants

// ─────────────────────────────────────────────────────────────────────────────
// The filter CUTOFF envelope's defaults: the stage times and the depth every
// surface falls back to when a call names the envelope but leaves a stage out.
//
// They live here because they are wire defaults in the sense of the house rule
// (`/dsl-design` section 4: defaults are the same on every surface and live in
// ONE place). Their readers are the four Ignitor DSL filter nodes and the doors
// that fill them (`fillFilterEnvelope`), whose stage-knob defaults ARE these
// values, and the build-time fill of `classic()`'s unset depth. The bridge's
// `FilterEnvDef`, the voice strip's nullable envelope that also read them, left
// with the typed `VoiceData.filters` field in phase 3 step 9.
//
// So `lpf(800, env = 24)` and `lowpass(800, env = 24)` start from the same
// numbers. The CURVE through them is not a value here: every surface takes
// `MOD_ENV_CURVE` (`EnvelopeDefaults.kt`, decision D3). `IgnitorDsl.Lowpass.env`
// says which envelope law this is.
//
// The values are the literals the strip's envelope carried from the start;
// moving them here changed no number. The DEPTH is deliberately NOT a node default:
// on a filter node `env = 0` is what says "no envelope" (it is the `hasEnv`
// test in `IgnitorFilters`), so the node defaults its depth to 0 and uses
// [FILTER_ENV_DEPTH_SEMITONES] only where a surface has a separate way to say
// the envelope is present (the retired strip's nullable envelope had one; the
// slot fill's "a stage knob was written" is the one left).
// ─────────────────────────────────────────────────────────────────────────────

/** Filter-envelope attack in seconds. */
const val FILTER_ENV_ATTACK_SEC: Double = 0.01

/** Filter-envelope decay in seconds. */
const val FILTER_ENV_DECAY_SEC: Double = 0.1

/** Filter-envelope sustain share of the depth, 0 to 1. */
const val FILTER_ENV_SUSTAIN_LEVEL: Double = 1.0

/** Filter-envelope release in seconds. */
const val FILTER_ENV_RELEASE_SEC: Double = 0.1

/**
 * Filter-envelope depth in SEMITONES (C3 of the filter unification): the sweep is pitch-linear,
 * `cutoff = base * 2^(depth/12 * env)`. 7 is about the old 0.5 ratio (`12*log2(1.5)`).
 */
const val FILTER_ENV_DEPTH_SEMITONES: Double = 7.0
