/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.constants

// ─────────────────────────────────────────────────────────────────────────────
// The pitch modulators' knob defaults, one home for every reader: the field
// defaults of `IgnitorDsl.Vibrato` and `IgnitorDsl.Fm`, the runtime in
// `audio_be` (`PitchModFactories.kt`), where a NON-FINITE knob value reads as
// unset and takes the same default (the chain `adsr`'s `finiteOr` rule), and
// the sprudel pitch path in `VoiceFactory` (`vib` without a rate, `fmh` unset).
//
// The switch knobs have no entry: the pitch envelope's and accelerate's
// `semitones` and FM's `depth` default to 0, which is "off", and a non-finite
// one reads as off.
// ─────────────────────────────────────────────────────────────────────────────

/** Vibrato LFO rate in Hz. */
const val VIBRATO_RATE_HZ: Double = 5.0

/** Vibrato depth in semitones, a quarter-semitone wobble. */
const val VIBRATO_SEMITONES: Double = 0.25

/** FM modulator frequency as a ratio of the fm frequency: 1 drives the modulator at the note. */
const val FM_RATIO: Double = 1.0
