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
// `vibrato.rate`, `vibrato.rangeFrom`, `vibrato.rangeTo` and `vibrato.phase`
// slots of `classic()`'s vibrato stage (`VibratoSlots`, which sprudel's `vib`
// fills; `vib` without them), the Kotlin `vibrato` door's defaults, and the
// `fm.ratio` slot of its FM stage (`FmSlots`, which sprudel's `fm` fills; `fm`
// without a ratio).
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

/**
 * Where the vibrato's LFO swing sits by default, in the -1..1 language of the Ignitor `range` (`IgnitorDsl.Vibrato`'s
 * `rangeFrom` / `rangeTo`, the script builder's `range(from, to)`, the `vibrato.rangeFrom` / `vibrato.rangeTo` slots of
 * `classic()`, sprudel's `vib(rangeFrom = ..., rangeTo = ...)`): the LFO's -1 maps to `from`, its +1 to `to`, and the
 * result is scaled by the depth in semitones. `(-1, 1)` is the vibrato as it always was, swinging both ways; the engine
 * builds NO range at a literal `(-1, 1)` (a built one is not the identity in floating point), so the default renders
 * the bits of the unranged vibrato. `(0, 1)` swings only upward (the guitar's vibrato, maintainer 2026-10-05). Pitch
 * pipeline 7c.
 */
const val VIBRATO_RANGE_FROM: Double = -1.0

/** See [VIBRATO_RANGE_FROM]. */
const val VIBRATO_RANGE_TO: Double = 1.0

/**
 * The vibrato LFO's phase, as a fraction of one cycle (the oscillators' `phase` knob, wrapped into `[0, 1)`): 0 starts
 * the sine at `sin(0)`, rising, where the vibrato always started, the middle of the swing (the note only for a range
 * centred on 0); 0.25 starts at its peak, 0.5 falling through the middle, 0.75 at its bottom. The
 * engine builds no phase input at a literal 0, so the default renders the bits of the vibrato without the knob. Pitch
 * pipeline 7c (decision D9).
 */
const val VIBRATO_PHASE: Double = 0.0

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
