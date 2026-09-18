/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.constants

// ─────────────────────────────────────────────────────────────────────────────
// Defaults of the per-orbit bus effects that are NOT sends: phaser, compressor,
// duck, body, vowel. The send pair (delay, reverb) lives next door in
// `SendEffectDefaults.kt`; the master limiter in `MasterLimiterDefaults.kt`.
//
// Same contract as its neighbours: ONE declaration that every surface reads, so
// the `KatalystStageDsl` knob defaults, the engine's fill for a voice field the
// author left unset (`VoiceFactory`, `Voice.Compressor.fromParams`,
// `LowPassHighPassFilters`, `Cylinder`, `Phaser.resetForReuse`) and
// `SprudelVoiceData.toVoiceData` cannot drift apart. The values are the ones
// those fills already used; this file gave them a name, it did not retune them
// (Katalyst DSL step 1, 2026-09-17).
//
// Since Katalyst step 5a-3 (2026-09-18) the sprudel compound doors fill their
// companions from here; the rule is `/dsl-design` §4, its one home. The engine
// keeps the same constants for a slot written raw through `katp`, which is the
// NaN rule, not a second fill.
//
// These are the TOUCHED defaults: what a knob means once the author has reached
// for its effect. What an UNTOUCHED effect carries is a separate question, and
// the answer is the engine's own untouched value, a zero or [SLOT_UNSET], never
// one of these (see `KatalystDsl.classic`).
//
// "Touched" does not always mean "audible": PHASER_WET and DUCK_DEPTH are 0.0
// here, because the phaser is gated on its depth and the duck on its source, so
// reaching for either still takes one more knob. Every other effect is audible
// as soon as it is reached for.
// ─────────────────────────────────────────────────────────────────────────────

// ── Unset ────────────────────────────────────────────────────────────────────

/**
 * The wire's "this knob was never set" marker, for a slot that must carry a `Double` and has no
 * null to carry instead.
 *
 * Not a new convention: `/dsl-design` §4 already says a NON-FINITE value reads as unset, and the
 * engine reads it that way everywhere a `Double?` cannot reach (`VoiceFactory.orDefault`,
 * `MasterChain.finite`, every shared DSP setter, `KatalystDelayEffect` and `KatalystReverbEffect`
 * off-configs). NaN is simply the non-finite value we WRITE, so an unset slot has one spelling.
 *
 * A consumer must test `isFinite()`, never `== SLOT_UNSET`: NaN is not equal to itself, and an
 * infinity means unset too.
 */
const val SLOT_UNSET: Double = Double.NaN

// ── Phaser ───────────────────────────────────────────────────────────────────

/** Phaser sweep rate in Hz. 0 = the phaser stands still, which is the "unset" sound. */
const val PHASER_RATE_HZ: Double = 0.0

/** Phaser wet amount, 0..1 (wire spelling on the voice: `phaserDepth`). 0 = off. */
const val PHASER_WET: Double = 0.0

/** Center frequency of the phaser's all-pass sweep, in Hz. */
const val PHASER_CENTER_HZ: Double = 1000.0

/** Width of the phaser's sweep around [PHASER_CENTER_HZ], in Hz. */
const val PHASER_SWEEP_HZ: Double = 1000.0

/** Minimum dry coefficient of the phaser wet/dry law. 1.0 = purely additive. */
const val PHASER_FLOOR: Double = 1.0

// ── Compressor ───────────────────────────────────────────────────────────────

/** Compressor threshold in dBFS: gain reduction starts here. */
const val COMPRESSOR_THRESHOLD_DB: Double = -20.0

/** Compression ratio above the threshold (4.0 = 4:1). */
const val COMPRESSOR_RATIO: Double = 4.0

/** Soft-knee width in dB. A hard corner injects harmonics on every crossing. */
const val COMPRESSOR_KNEE_DB: Double = 6.0

/** How fast the compressor's gain closes, in seconds. */
const val COMPRESSOR_ATTACK_SECONDS: Double = 0.003

/** How fast the compressor's gain opens again, in seconds. */
const val COMPRESSOR_RELEASE_SECONDS: Double = 0.1

// ── Duck ─────────────────────────────────────────────────────────────────────

/** How far the ducked orbit is pulled down, 0..1. 0 = no ducking. */
const val DUCK_DEPTH: Double = 0.0

/** How fast the duck closes when the source orbit sounds, in seconds. */
const val DUCK_ATTACK_SECONDS: Double = 0.1

// ── Body ─────────────────────────────────────────────────────────────────────

/** How much of the orbit runs through the body resonator, 0..1 (wire spelling on the voice: `bodyMix`). */
const val BODY_WET: Double = 0.5

/**
 * Broadband transmission floor for the body resonator.
 *
 * Under the C4 law the floor PINS the dry from `w* = (2/pi)*acos(sqrt(floor))` upward
 * (~0.56 for 0.4), i.e. from the middle of the knob, not just at the top (it never drops
 * below the floor anywhere). That floor is what lets the body emphasize its resonant modes
 * over a broadband bed instead of collapsing to a few isolated tones, the way a real passive
 * body behaves. A LOWER floor makes the body more audible at a given wet (the resonances sit
 * over less dry); a higher floor is subtler. The wet itself is clamped to 0..1 since C4.
 * Tunable by ear.
 */
const val BODY_FLOOR: Double = 0.4

// ── Vowel ────────────────────────────────────────────────────────────────────

/** How much of the orbit runs through the formant bank, 0..1 (wire spelling on the voice: `vowelMix`). */
const val VOWEL_WET: Double = 0.5

/**
 * Broadband floor for the vowel/formant filter. The formant analogue of [BODY_FLOOR], but much
 * LOWER: a vowel is a source *strongly shaped by* formants (deep valleys between them), whereas a
 * body is a subtle coloration over a strong floor. The floor still keeps some source audible
 * between formants (avoids sparse/robotic). Tunable.
 */
const val VOWEL_FLOOR: Double = 0.2
