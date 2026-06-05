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

/** Per-voice random *amplitude* offset (±fraction): analog non-uniformity with zero pitch effect. */
internal const val SUPERSAW_GAIN_JITTER: Double = 0.1

/** Detune spacing shape: 1.0 = even; >1 concentrates voices toward center; <1 spreads outward. */
internal const val SUPERSAW_DETUNE_POWER: Double = 1.2

// ── Super-ramp (unison) ──────────────────────────────────────────────────────────────────────────
// The super-ramp is a negated super-saw; these are its OWN unison knobs, seeded to the super-saw
// values for now. Change these literals to give the unison ramp its own character.

/** Super-ramp center-dominant gain falloff. Starts equal to the super-saw. */
internal const val SUPERRAMP_SIDE_ATTEN: Double = SUPERSAW_SIDE_ATTEN

/** Super-ramp per-voice amplitude jitter. Starts equal to the super-saw. */
internal const val SUPERRAMP_GAIN_JITTER: Double = SUPERSAW_GAIN_JITTER

/** Super-ramp detune spacing shape. Starts equal to the super-saw. */
internal const val SUPERRAMP_DETUNE_POWER: Double = SUPERSAW_DETUNE_POWER

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
