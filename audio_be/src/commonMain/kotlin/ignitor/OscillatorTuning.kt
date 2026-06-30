/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

/**
 * Fine-tuning constants for oscillator character — the engine's "voice", collected in one place so
 * the sound can be dialed in by ear without hunting through the oscillator code.
 *
 * `SAW_*` apply to **every** saw (the single [Ignitors.sawtooth] and the [Ignitors.superSaw] voices —
 * there is only one saw shape). `SUPERSAW_*` are unison-specific (they only matter when stacking
 * detuned voices). More oscillator families (ramp, …) will add their groups here over time.
 */

// ── Saw shape (single saw + super-saw share one shape) ───────────────────────────────────────────

/** Analog flyback time in samples (constant → a larger fraction of the cycle at higher pitch, so
 *  high notes soften toward a triangle). Lower = brighter / sharper reset. Tune by ear. */
internal const val SAW_RESET_SAMPLES: Double = 2.0

/** Max flyback fraction of a cycle (`rf = 0.5` → symmetric triangle; keeps very high notes sane). */
internal const val SAW_SHAPE_MAX: Double = 0.5

// ── Ramp (mirrored saw) ──────────────────────────────────────────────────────────────────────────
// The ramp shares the saw shape (negated) but has its OWN knobs so it can be made distinct later.
// Seeded to the saw values for now — change these literals to diverge.

/** Ramp flyback time in samples. Starts equal to the saw; give it its own feel later. */
internal const val RAMP_RESET_SAMPLES: Double = SAW_RESET_SAMPLES

/** Ramp max flyback fraction of a cycle. Starts equal to the saw. */
internal const val RAMP_SHAPE_MAX: Double = SAW_SHAPE_MAX

// ── Super-saw (unison) ───────────────────────────────────────────────────────────────────────────

/** Center-dominant gain falloff: 0 = all voices equal (flat), 1 = only the center voice. */
internal const val SUPERSAW_SIDE_ATTEN: Double = 0.1

/**
 * Per-voice random *amplitude* offset (±fraction): analog non-uniformity with zero pitch effect.
 * `0.0` = off; `0.1` was the original. The on-pitch CENTER voice gets a scaled-down share (see
 * [SUPERSAW_CENTER_JITTER_SCALE]) so a high value here adds side-voice grit without "won't ring".
 * Inherited by the super-ramp/square/tri/sine unison families below.
 */
internal const val SUPERSAW_GAIN_JITTER: Double = 0.15

/**
 * How much of [SUPERSAW_GAIN_JITTER] the on-pitch CENTER voice receives (`0.0`..`1.0`).
 * `0.0` = center perfectly stable → always rings, but flatter/"boring"; `1.0` = center jittered like the
 * sides → max liveliness but the "won't ring" lottery returns. Dial by ear (the loud center voice carries
 * the perceived pitch, so a little goes a long way). Applies to all super-* unison families.
 */
internal const val SUPERSAW_CENTER_JITTER_SCALE: Double = 0.4

/** Detune spacing shape: 1.0 = even; >1 concentrates voices toward center; <1 spreads outward. */
internal const val SUPERSAW_SPREAD_POWER: Double = 1.2

// ── Super-ramp (unison) ──────────────────────────────────────────────────────────────────────────
// The super-ramp is a negated super-saw; these are its OWN unison knobs, seeded to the super-saw
// values for now. Change these literals to give the unison ramp its own character.

/** Super-ramp center-dominant gain falloff. Starts equal to the super-saw. */
internal const val SUPERRAMP_SIDE_ATTEN: Double = SUPERSAW_SIDE_ATTEN

/** Super-ramp per-voice amplitude jitter. Starts equal to the super-saw. */
internal const val SUPERRAMP_GAIN_JITTER: Double = SUPERSAW_GAIN_JITTER

/** Super-ramp detune spacing shape. Starts equal to the super-saw. */
internal const val SUPERRAMP_SPREAD_POWER: Double = SUPERSAW_SPREAD_POWER

/** Super-ramp center-voice jitter scale. Starts equal to the super-saw. */
internal const val SUPERRAMP_CENTER_JITTER_SCALE: Double = SUPERSAW_CENTER_JITTER_SCALE

// ── Super-square (unison) ────────────────────────────────────────────────────────────────────────
// The super-square stacks the pulse shape (duty 0.5) on the shared super-saw unison engine; these are
// its OWN knobs, seeded to the super-saw values. Change these literals to give it its own character.

/** Super-square center-dominant gain falloff. Starts equal to the super-saw. */
internal const val SUPERSQUARE_SIDE_ATTEN: Double = SUPERSAW_SIDE_ATTEN

/** Super-square per-voice amplitude jitter. Starts equal to the super-saw. */
internal const val SUPERSQUARE_GAIN_JITTER: Double = SUPERSAW_GAIN_JITTER

/** Super-square detune spacing shape. Starts equal to the super-saw. */
internal const val SUPERSQUARE_SPREAD_POWER: Double = SUPERSAW_SPREAD_POWER

/** Super-square center-voice jitter scale. Starts equal to the super-saw. */
internal const val SUPERSQUARE_CENTER_JITTER_SCALE: Double = SUPERSAW_CENTER_JITTER_SCALE

// ── Super-triangle (unison) ──────────────────────────────────────────────────────────────────────
// The super-triangle stacks the pulse shape with fully-open flanks (1.0/1.0); its own unison knobs,
// seeded to the super-saw values.

/** Super-triangle center-dominant gain falloff. Starts equal to the super-saw. */
internal const val SUPERTRI_SIDE_ATTEN: Double = SUPERSAW_SIDE_ATTEN

/** Super-triangle per-voice amplitude jitter. Starts equal to the super-saw. */
internal const val SUPERTRI_GAIN_JITTER: Double = SUPERSAW_GAIN_JITTER

/** Super-triangle detune spacing shape. Starts equal to the super-saw. */
internal const val SUPERTRI_SPREAD_POWER: Double = SUPERSAW_SPREAD_POWER

/** Super-triangle center-voice jitter scale. Starts equal to the super-saw. */
internal const val SUPERTRI_CENTER_JITTER_SCALE: Double = SUPERSAW_CENTER_JITTER_SCALE

// ── Super-sine (unison) ──────────────────────────────────────────────────────────────────────────
// The super-sine stacks pure sines on the shared super-saw unison engine; its own knobs, seeded to
// the super-saw values.

/** Super-sine center-dominant gain falloff. Starts equal to the super-saw. */
internal const val SUPERSINE_SIDE_ATTEN: Double = SUPERSAW_SIDE_ATTEN

/** Super-sine per-voice amplitude jitter. Starts equal to the super-saw. */
internal const val SUPERSINE_GAIN_JITTER: Double = SUPERSAW_GAIN_JITTER

/** Super-sine detune spacing shape. Starts equal to the super-saw. */
internal const val SUPERSINE_SPREAD_POWER: Double = SUPERSAW_SPREAD_POWER

/** Super-sine center-voice jitter scale. Starts equal to the super-saw. */
internal const val SUPERSINE_CENTER_JITTER_SCALE: Double = SUPERSAW_CENTER_JITTER_SCALE

// ── Pulse family (square / pulse / pulze / triangle share one shape) ──────────────────────────────
// square / pulse / pulze are one pulse oscillator (duty osc-param; 0.5 = square). Each edge is a
// finite-slope flank (no PolyBLEP — like the saw), as a fraction of its plateau: 0 = sharpest (just
// the minimum floor below), 1 = full ramp. Both flanks = 1 (at duty 0.5) → a triangle (the `triangle`
// factory hardcodes 1.0/1.0).

/** Minimum flank length in **samples** (a floor on every edge, like the saw → edges are never truly
 *  instant, so no PolyBLEP and high notes soften with pitch). Tune by ear. */
internal const val PULSE_MIN_FLANK_SAMPLES: Double = 2.0

/** Pulse rising-edge flank fraction of its plateau (0 = sharpest / min floor, 1 = full ramp). */
internal const val PULSE_RISE_FLANK: Double = 0.0

/** Pulse falling-edge flank fraction (0 = sharpest / min floor, 1 = full ramp). */
internal const val PULSE_FALL_FLANK: Double = 0.0
