/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.constants

// ─────────────────────────────────────────────────────────────────────────────
// The pitch modulators' knob defaults, one home for every reader: the field
// defaults of `IgnitorDsl.Vibrato` and `IgnitorDsl.Fm`, the runtime in
// `audio_be`, where a NON-FINITE knob value reads as unset and takes the same
// default (FM: the chain `adsr`'s `finiteOr` rule in `PitchModFactories.kt`;
// the composed vibrato: a non-finite LITERAL knob, `finiteLiteralOr` in
// `IgnitorDslRuntime.kt`, since pitch pipeline 7b), the
// `vibrato.rate` slot of `classic()`'s vibrato stage (`VibratoSlots`, which
// sprudel's `vib` fills; `vib` without a rate), and the `fm.ratio` slot of its
// FM stage (`FmSlots`, which sprudel's `fm` fills; `fm` without a ratio).
//
// The switch knobs have no entry: the pitch envelope's and accelerate's
// `semitones` and FM's `depth` default to 0, which is "off", and a non-finite
// one reads as off (the slots `penv.semitones`, `accelerate` and `fm.depth` alike). The vibrato's depth slot (`vibrato.semitones`) defaults to 0
// too. A non-finite literal depth on the node's own knob reads as
// `VIBRATO_SEMITONES` (built); in the slot it reads as the slot's 0.0 (off).
// ─────────────────────────────────────────────────────────────────────────────

/** Vibrato LFO rate in Hz. */
const val VIBRATO_RATE_HZ: Double = 5.0

/** Vibrato depth in semitones, a quarter-semitone wobble. */
const val VIBRATO_SEMITONES: Double = 0.25

/** FM modulator frequency as a ratio of the fm frequency: 1 drives the modulator at the note. */
const val FM_RATIO: Double = 1.0

// The FM depth envelope's stage defaults, one home for the `IgnitorDsl.Fm` field defaults, its Kotlin builder and
// `classic()`'s `fm.*` slots (`FmSlots`). With all four at these values the node runs NO envelope (the depth is full
// from the onset through the release tail); the envelope runs by value, when attack > 0, decay > 0, sustain < 1 or
// release > 0.

/** FM depth envelope attack in seconds. */
const val FM_ENV_ATTACK_SEC: Double = 0.0

/** FM depth envelope decay in seconds. */
const val FM_ENV_DECAY_SEC: Double = 0.0

/** FM depth envelope sustain, the held share of the depth. */
const val FM_ENV_SUSTAIN_LEVEL: Double = 1.0

/** FM depth envelope release in seconds, from the gate. */
const val FM_ENV_RELEASE_SEC: Double = 0.0
