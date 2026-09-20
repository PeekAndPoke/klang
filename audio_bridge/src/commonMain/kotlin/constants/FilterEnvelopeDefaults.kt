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
// ONE place). Two surfaces read them today:
//
//  - `FilterEnvDef.resolve()` / `FilterEnvDef.default`, the voice-strip path
//    (a `FilterDef` carries a nullable envelope and resolves it at note-on);
//  - the four Ignitor DSL filter nodes and the doors that fill them
//    (`fillFilterEnvelope`), whose stage-knob defaults ARE these values.
//
// What that buys, stated exactly: the two surfaces read the same STAGE TIMES
// and the same DEPTH from this one declaration, so `lpf(800, env = 24)` and
// `lowpass(800, env = 24)` start from the same numbers. It does NOT make the
// two sweeps the same SHAPE, and nobody arriving here should read it that way:
// the node's envelope segments are linear and the strip's are the house
// exponential curve, a difference of up to 806 cents at the same instant.
// `IgnitorDsl.Lowpass.env` is the one home of that comparison and of what
// decision D3 still owes on it.
//
// The values are the literals `FilterEnvDef` carried from the start; moving
// them here changed no number. The DEPTH is deliberately NOT a node default:
// on a filter node `env = 0` is what says "no envelope" (it is the `hasEnv`
// test in `IgnitorFilters`), so the node defaults its depth to 0 and uses
// [FILTER_ENV_DEPTH_SEMITONES] only where a surface has a separate way to say
// the envelope is present, which is what the nullable strip envelope has.
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
