/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
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

// Phase pool (docs/tasks/unison-phase-pool.md): banded best-of-M start-phase selection that kills
// the "fundamental lottery" (random-phase draws leaving 11–21 % of low notes with a cancelled
// fundamental for their whole duration). OFF by default — off is bit-identical to the legacy
// random draw. K = |Σ gₙ·e^{i2πφₙ}| / Σ gₙ ∈ [0,1]: 0 = fundamental cancelled, 1 = phase-aligned
// (thin/buzzy — the lushness IS the incoherence, so selection targets a band, never the maximum).
// ⚠️ The bands + tries below are calibrated for unison ≈ 7–11: in-band probability falls with
// voice count (K's median scales ~1/√v), so at v ≥ ~16 the higher bands are rarely reachable and
// selection drifts toward closest-candidate (= max-K). Voices-aware bands are a P2 question.

/** Banded start-phase selection: 0 = OFF (bit-identical legacy random), 1 = on. */
internal const val SUPERSAW_PHASE_POOL: Double = 0.0

/** Candidate phase sets drawn per note-on when the phase pool is on (best-of-M). */
internal const val SUPERSAW_DRAW_TRIES: Double = 5.0

/** Accepted fundamental-coherence band, lower edge. Also a timbre control — lower = hollower. */
internal const val SUPERSAW_K_MIN: Double = 0.30

/** Accepted fundamental-coherence band, upper edge. */
internal const val SUPERSAW_K_MAX: Double = 0.55

/** Pool vocabulary size per (orbit, unison, profile, band) key (engine caps at 1024). */
internal const val SUPERSAW_POOL_SIZE: Double = 256.0

/** Notes between fresh pool draws (random eviction); 0 = frozen pool (reproducible vocabulary). */
internal const val SUPERSAW_REFRESH_EVERY: Double = 10.0

/** Pool entry selection: 0 = roundRobin (cycle the array — the settled default), 1 = random. */
internal const val SUPERSAW_SELECTION: Double = 0.0

/** Entries seeded eagerly at pool creation (work-capped; 0 = fully lazy). */
internal const val SUPERSAW_WARMUP: Double = 16.0

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

/** Super-ramp phase pool knobs. Start equal to the super-saw (same harmonic statistics). */
internal const val SUPERRAMP_PHASE_POOL: Double = SUPERSAW_PHASE_POOL
internal const val SUPERRAMP_DRAW_TRIES: Double = SUPERSAW_DRAW_TRIES
internal const val SUPERRAMP_K_MIN: Double = SUPERSAW_K_MIN
internal const val SUPERRAMP_K_MAX: Double = SUPERSAW_K_MAX
internal const val SUPERRAMP_POOL_SIZE: Double = SUPERSAW_POOL_SIZE
internal const val SUPERRAMP_REFRESH_EVERY: Double = SUPERSAW_REFRESH_EVERY
internal const val SUPERRAMP_SELECTION: Double = SUPERSAW_SELECTION
internal const val SUPERRAMP_WARMUP: Double = SUPERSAW_WARMUP

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

/** Super-square phase pool knobs. Start equal to the super-saw (same odd-harmonic statistics). */
internal const val SUPERSQUARE_PHASE_POOL: Double = SUPERSAW_PHASE_POOL
internal const val SUPERSQUARE_DRAW_TRIES: Double = SUPERSAW_DRAW_TRIES
internal const val SUPERSQUARE_K_MIN: Double = SUPERSAW_K_MIN
internal const val SUPERSQUARE_K_MAX: Double = SUPERSAW_K_MAX
internal const val SUPERSQUARE_POOL_SIZE: Double = SUPERSAW_POOL_SIZE
internal const val SUPERSQUARE_REFRESH_EVERY: Double = SUPERSAW_REFRESH_EVERY
internal const val SUPERSQUARE_SELECTION: Double = SUPERSAW_SELECTION
internal const val SUPERSQUARE_WARMUP: Double = SUPERSAW_WARMUP

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

/** Super-triangle phase pool knobs. 1/k² harmonics → the fundamental nearly IS the note, so the
 *  accepted band sits higher than the saw's (provisional — by-ear pass pending, doc §4/§9.1).
 *  A higher band is rarer per draw (P ≈ 0.17 at unison 11), so the search is deeper: 16 tries
 *  → ~95 % of notes land in-band instead of falling back to closest-candidate. */
internal const val SUPERTRI_PHASE_POOL: Double = SUPERSAW_PHASE_POOL
internal const val SUPERTRI_DRAW_TRIES: Double = 16.0
internal const val SUPERTRI_K_MIN: Double = 0.40
internal const val SUPERTRI_K_MAX: Double = 0.65
internal const val SUPERTRI_POOL_SIZE: Double = SUPERSAW_POOL_SIZE
internal const val SUPERTRI_REFRESH_EVERY: Double = SUPERSAW_REFRESH_EVERY
internal const val SUPERTRI_SELECTION: Double = SUPERSAW_SELECTION
internal const val SUPERTRI_WARMUP: Double = SUPERSAW_WARMUP

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

/** Super-sine phase pool knobs. K is the ENTIRE note (no other harmonics), so the band sits
 *  highest of the family (provisional — by-ear pass pending, doc §4/§9.1).
 *  The high band is RARE per random draw (P ≈ 0.07 at unison 11) — at 5 tries 70 % of notes
 *  would miss it and the closest-candidate fallback would quietly become K-maximization (the
 *  thin/buzzy failure banding exists to avoid). 40 tries → ~94 % in-band; still ≤ 1 ms of
 *  note-on math. Guarded by the reachability cases in PhasePoolSpec. */
internal const val SUPERSINE_PHASE_POOL: Double = SUPERSAW_PHASE_POOL
internal const val SUPERSINE_DRAW_TRIES: Double = 40.0
internal const val SUPERSINE_K_MIN: Double = 0.50
internal const val SUPERSINE_K_MAX: Double = 0.80
internal const val SUPERSINE_POOL_SIZE: Double = SUPERSAW_POOL_SIZE
internal const val SUPERSINE_REFRESH_EVERY: Double = SUPERSAW_REFRESH_EVERY
internal const val SUPERSINE_SELECTION: Double = SUPERSAW_SELECTION
internal const val SUPERSINE_WARMUP: Double = SUPERSAW_WARMUP

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

// ── Crackle (chaotic recurrence) ───────────────────────────────────────────────────────────────────
// SuperCollider's Crackle map: y[n] = |chaos·y[n-1] − y[n-2] − CRACKLE_C|, then DC-blocked to bipolar
// pops. No PRNG — typically cheaper than the dust it used to alias. Tune by ear.

/** Default chaos parameter (SC's classic 1.5 → clear crackle; ~1.0 sparse, ~2.0 dense/noisy). */
internal const val CRACKLE_CHAOS_DEFAULT: Double = 1.5

/** Upper bound on chaos — the map diverges for chaos ≳ 2, so coerce there (numerical stability). */
internal const val CRACKLE_CHAOS_MAX: Double = 2.0

/** Small constant offset in the chaotic map (SC uses 0.05). */
internal const val CRACKLE_C: Double = 0.05

/** DC-blocker pole (≈35 Hz high-pass @ 44.1k) that recenters the unipolar map to bipolar pops. */
internal const val CRACKLE_DC_POLE: Double = 0.995

// ── White-noise spectral tilt ("color") ────────────────────────────────────────────────────────────
// One-pole tilt after the white source: color 0 = flat white (filter bypassed → perf-neutral default);
// <0 crossfades toward the one-pole LP (darken, −6 dB/oct above the pivot); >0 toward the complementary
// HP (brighten). Range −1..1.

/** Default tilt (0 = flat white, filter bypassed). */
internal const val NOISE_TILT_DEFAULT: Double = 0.0

/** One-pole LP coefficient for the tilt pivot (`g` in `lp += g·(x−lp)`; ≈1 kHz pivot @ 44.1k). Tune by ear. */
internal const val NOISE_TILT_LP_COEF: Double = 0.15

// ── Brown noise ──────────────────────────────────────────────────────────────────────────────────
/** Per-sample white-leak `k` in `out = (out + k·white)/(1+k)`. Lower = deeper/slower brown. */
internal const val BROWN_LEAK_DEFAULT: Double = 0.02

// ── Dust ─────────────────────────────────────────────────────────────────────────────────────────
/** Heavy-tailed amplitude exponent: 1 = uniform (default, behavior-preserving); >1 = rare-loud pops. */
internal const val DUST_TAIL_DEFAULT: Double = 1.0

/** Bipolar flag (>0.5 = on); 0 = unipolar (today's behavior). */
internal const val DUST_BIPOLAR_DEFAULT: Double = 0.0
