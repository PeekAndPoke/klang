/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.constants

// ─────────────────────────────────────────────────────────────────────────────
// Defaults of the per-orbit bus effects that are NOT sends: phaser, compressor,
// duck, body, vowel. The send pair (delay, reverb) lives next door in
// `SendEffectDefaults.kt`; the master limiter in `MasterLimiterDefaults.kt`.
// At the end, two timings that are not knob defaults but belong to the orbit
// knobs and stages whose jump is audible, sends included: [KNOB_GLIDE_SECONDS]
// for a level or dynamics glide, [BANK_CROSSFADE_SECONDS] for a filter bank
// crossfade.
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
 * engine reads it that way everywhere a `Double?` cannot reach (the `KatalystSlots` resolvers,
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

// ── Knob glide ───────────────────────────────────────────────────────────────

/**
 * How long an orbit knob takes to reach a new value, in seconds: a gliding knob moves there over
 * this time instead of jumping, because two patterns on one orbit take turns owning it and each
 * owner change can move every knob of the chain at once (`docs/plans/knob-glide.md`, decided 2026-09-19).
 *
 * A safety net against clicks, applied per knob where a jump is AUDIBLE, not by default to every
 * knob. It grew well past its pilot (the orbit reverb's size, whose damping was measured to need
 * none) through Katalyst 5c-7 to 5c-9: it is now the orbit fader and gain, the compressor's blend
 * and its per-sample knobs, the phaser's coefficients and breakpoint, the duck's weight and depth,
 * the delay's `wet` and its tap crossfade, and the send `wet`s. What it is NOT, since 5c-11, is
 * the filter-bank crossfade: that is [BANK_CROSSFADE_SECONDS] below. `KnobGlide` rounds this one
 * to whole render blocks. Not a knob today; it may become a user knob one day, and this is where
 * its default would then live.
 */
const val KNOB_GLIDE_SECONDS: Double = 0.05

// ── Bank crossfade ───────────────────────────────────────────────────────────

/**
 * How long ONE BANK CROSSFADE takes, in seconds: the time `KatalystFilterSwap` spends blending the
 * bank in service into the arriving one, and the time the body, the vowel and the orbit EQ take to
 * fade in from dry or out to it.
 *
 * Its own constant since Katalyst step 5c-11 (decided with the maintainer, 2026-09-20), SHORTER
 * than [KNOB_GLIDE_SECONDS], which keeps every level and dynamics glide at 50 ms (the compressor's
 * release measurably gets worse if that one is shortened). A bank swap is not a level move: what
 * travels is a whole filter, so the fade is the time two materials are heard at once, and 50 ms of
 * it smears an articulation.
 *
 * **The rate limit is tempo and rate SPECIFIC, and it is a block count.** A fade occupies
 * `ceil(fadeLen / blockFrames)` blocks, so the swap can start a change every 20.3 ms at 44.1 kHz
 * (7 blocks of 128) and every 21.3 ms at 48 kHz, not every 20 ms. 64ths at 174 BPM are 21.55 ms
 * apart and clear it on both; at 176 BPM they are 21.3 ms apart and every change parks at 48 kHz. Past the limit the
 * parking does not THIN a run, it ALIASES it: the changes that survive are phase-locked, so a
 * four-material pattern at twice the limit is heard as a two-material alternation with two of the
 * four never sounding at all.
 *
 * **Measured, and the verdict SPLITS by band** (Katalyst 5c-11; the round-1 numbers were taken
 * through an 8th-order Butterworth whose skirt leaked the signal into both measured bands and are
 * retracted). Instrument: a 16383-tap Blackman-Harris windowed sinc per band, stopband -142 dB at
 * 110 Hz for the 60 Hz lowpass and -164 dB at 2970 Hz for the 8 kHz highpass, floors taken on a
 * static render. Sources band-limited to 3 kHz at 55, 110 and 220 Hz, 15 body materials, 6 vowels,
 * single changes, the on and off edges and runs at 174 BPM, worst of 8 change instants:
 *
 * - **Above 8 kHz, 20 ms holds with margin.** Worst body -74.9 dB relative to the signal, worst
 *   vowel -81.6 dB, worst edge -80.7 dB, worst run -83.0 dB, against floors of -147 to -161 dB.
 * - **Below 60 Hz it does NOT settle the question.** The worst burst at 20 ms is about -38 to
 *   -40 dB relative to the signal (a material change, an on or off edge and a 64th-note run all
 *   land there), and 8 to 10 dB LOUDER than the same case at 50 ms. That is the level and the
 *   class of the phaser "blub" of 5c-9. It is loudest where a low mode starts cold, and it falls
 *   about 10 dB per octave of source pitch, which says it is the transition spreading the source's
 *   own low partials downward rather than the bank ringing.
 *
 * So 20 ms is proven click-free and NOT proven thump-free; the low half is the maintainer's by ear
 * at the 5c listening checkpoint.
 *
 * **If it has to move, the trade is low end against rate, and it is sharp.** A fade occupies whole
 * blocks, so each candidate time has its own rate ceiling (128-frame blocks, 174 BPM, where a 16th
 * is 86.2 ms, a 32nd 43.1 and a 64th 21.55):
 *
 * | fade | 44.1 kHz | 48 kHz | the fastest rate that does not park |
 * |---|---|---|---|
 * | 20 ms | 882 samples, 7 blocks, 20.3 ms | 960, 8 blocks, 21.3 ms | **64ths**, and only just |
 * | 30 ms | 1323 samples, 11 blocks, 31.9 ms | 1440, 12 blocks, 32.0 ms | 32nds |
 * | 50 ms | 2205 samples, 18 blocks, 52.2 ms | 2400, 19 blocks, 50.7 ms | 16ths |
 *
 * So **20 ms is the only one of the three that lets 64ths through**, and 30 ms buys a quieter low
 * end at the price of parking them. How much quieter depends on which instrument is asked, and the
 * two do not agree: on the isolated sweep 30 ms recovers 4.8 of the 9.4 dB (about half), on the
 * rendered diagnostic 6.4 of 7.9 dB (about 80 percent). Either way it recovers a substantial part
 * of the cost, and neither number is worth quoting alone.
 */
const val BANK_CROSSFADE_SECONDS: Double = 0.02
