/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.constants

// ─────────────────────────────────────────────────────────────────────────────
// Defaults of the per-orbit bus effects, the Katalyst stages: delay, reverb,
// phaser, compressor, duck, body, vowel, distort. The master limiter's live in
// `MasterLimiterDefaults.kt`. At the end, two timings that are not knob
// defaults but belong to the orbit knobs and stages whose jump is audible:
// [KNOB_GLIDE_SECONDS] for a level or dynamics glide, [BANK_CROSSFADE_SECONDS]
// for a filter bank crossfade.
//
// ONE declaration that every surface reads, so they cannot drift apart:
// - the stage knob defaults (`KatalystStageDsl` and its builders, the script
//   door's `KlangScriptKatalystSlots`) and the classic chain's slots;
// - the sprudel compound doors (`delay(...)`, `reverb(...)`, `phaser(...)`,
//   `compressor(...)`, `duck(...)`, `body(...)`, `vowel(...)`), which fill the
//   companions of the stage a call names (`/dsl-design` §4 is the rule, its one
//   home), and the sprudel editor tools;
// - the engine: `KatalystChainBuilder`'s knob fallbacks, and the NaN rule for a
//   slot written raw through `katp`, which takes the same constant (the slot
//   writers in `KatalystSlotWriters.kt`, or the stage's own `configure` where it
//   compares what it is handed). That is the NaN rule, not a second fill;
// - the shared DSP's own defaults (`DelayLine`, `Reverb`, `Phaser`,
//   `LowPassHighPassFilters`, `KatalystResonatorEffect`).
// The values are the ones the engine used before they had a name; naming them
// retuned nothing (Katalyst DSL step 1, 2026-09-17; the delay and reverb pair,
// `docs/tasks-archive/2026-09/20260916-delay-names-and-send-defaults.md`, lived
// in `SendEffectDefaults.kt` until 2026-10-08).
//
// These are the TOUCHED defaults: what a knob means once the author has reached
// for its effect. Musical, not neutral (maintainer, 2026-09-16): a reached-for
// knob that is left unset means a usable sound. What an UNTOUCHED effect carries
// is a separate question, and the answer is the engine's own untouched value, a
// zero or [SLOT_UNSET], never one of these (see `KatalystDsl.classic`).
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
 * engine reads it that way everywhere a `Double?` cannot reach (the slot writers and the stages'
 * `configure`, every shared DSP setter, the `KatalystDelayEffect` and `KatalystReverbEffect`
 * off-configs). NaN is simply the non-finite value we WRITE, so an unset slot has one spelling.
 *
 * A consumer must test `isFinite()`, never `== SLOT_UNSET`: NaN is not equal to itself, and an
 * infinity means unset too.
 */
const val SLOT_UNSET: Double = Double.NaN

// ── Delay ────────────────────────────────────────────────────────────────────

/** Delay wet: how much of the orbit mix feeds the line, 0..1. */
const val DELAY_WET: Double = 0.25

/** Delay time in seconds. */
const val DELAY_TIME_SECONDS: Double = 0.25

/** Delay feedback; at or above 1.0 the delay self-oscillates, bounded by the cap. */
const val DELAY_FEEDBACK: Double = 0.3

/** Level the recirculating delay signal saturates toward. 1.0 is the engine's plain soft cap. */
const val DELAY_CAP: Double = 1.0

// ── Reverb ───────────────────────────────────────────────────────────────────

/** Reverb wet: how much of the orbit mix feeds the room, 0..1. */
const val REVERB_WET: Double = 0.25

/**
 * Reverb size on the authored ~0..10 scale. `5.0 / 10 == 0.5`, the Freeverb default, about a
 * 1.4 s tail.
 */
const val REVERB_SIZE: Double = 5.0

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

/**
 * The compressor's lookahead in seconds: 0, no lookahead and no latency. A lookahead delays whatever
 * runs through the stage by that much, so it is the author's opt-in, never a default.
 */
const val COMPRESSOR_LOOKAHEAD_SECONDS: Double = 0.0

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

// ── Distort ──────────────────────────────────────────────────────────────────

/**
 * Distort amount of a Katalyst `distort` stage that names none: the drive `10^(amount * 1.2)` into
 * the shape, so 0.5 is about +12 dB. The same 0.5 the Ignitor's `Distort` and `Drive` nodes default
 * to, so a bare `distort` means the same push on a voice and on a bus.
 */
const val DISTORT_AMOUNT: Double = 0.5

/**
 * Oversampling factor of a Katalyst `distort` stage that names none: 2x (maintainer, 2026-10-10,
 * `docs/tasks-archive/2026-10/20261010-iir-oversampler.md` step 5). A bus bends the whole mix, so
 * its aliasing lands on every note at once; the IIR half-band is flat to 20 kHz, so oversampling no
 * longer dulls the mix as the FIR did. It costs 3 frames of latency on that bus and a round trip per
 * channel per block. A deliberate asymmetry with the voice, whose `distort` stays at 0 (a per-note
 * cost, opted into per sound). The typed door `KatalystBuilder.distort` reads it; the script variant (`scriptDistort`) bakes the same literal.
 */
const val DISTORT_OVERSAMPLE: Int = 2

// ── Knob glide ───────────────────────────────────────────────────────────────

/**
 * How long an orbit knob takes to reach a new value, in seconds: a gliding knob moves there over
 * this time instead of jumping, because two patterns on one orbit take turns owning it and each
 * owner change can move every knob of the chain at once (`audio/ref/knob-glide.md`, decided 2026-09-19).
 *
 * A safety net against clicks, applied per knob where a jump is AUDIBLE, not by default to every
 * knob. It grew well past its pilot (the orbit reverb's size, whose damping was measured to need
 * none) through Katalyst 5c-7 to 5c-9: it is now the orbit fader and gain, the compressor's blend
 * and its per-sample knobs (a move that RELAXES the compressor follows its release instead, never
 * faster than this glide, since 2026-10-17, `CompressorKnobMove`), the phaser's coefficients and breakpoint, the duck's weight and depth,
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
 * Its own constant since Katalyst step 5c-11 (2026-09-20): a bank swap is not a level move, what
 * travels is a whole filter, so the fade is the time two materials are heard at once. It ran at
 * 20 ms from 5c-11 until 2026-10-17 and is 50 ms since, **decided by ear** (the maintainer, on the
 * pair `tmp/listening/knob-glide/body-low-crossfade-*.wav`, `docs/tasks/by-ear/knob-glide-listening.md`
 * item 1): "i cannot really hear a difference between the 20ms and the 50ms, so i would say to be on
 * the safe side we take the 50ms." So it now has the same VALUE as [KNOB_GLIDE_SECONDS] and stays a
 * constant of its own, because the concept is another one: the swap counts its fade in SAMPLES
 * (2205 at 44.1 kHz, 2400 at 48 kHz), the knob glide in whole blocks (17 blocks, 2176 samples, at
 * 44.1 kHz).
 *
 * **What the safe side bought, measured** (Katalyst 5c-11; 16383-tap Blackman-Harris windowed sinc
 * per band, stopband -142 dB at 110 Hz for the 60 Hz lowpass and -164 dB at 2970 Hz for the 8 kHz
 * highpass, floors taken on a static render; sources band-limited to 3 kHz at 55, 110 and 220 Hz,
 * 15 body materials, 6 vowels, worst of 8 change instants). Above 8 kHz both lengths are click-free
 * with margin (20 ms: worst body -74.9 dB relative to the signal, worst vowel -81.6, against floors
 * of -147 to -161). Below 60 Hz a body material change read -39.6 dB relative to the signal at 20 ms
 * and -49.0 dB at 50 ms, 9.4 dB quieter, loudest where a low mode starts cold (`croon` to `tube`,
 * `wood` to `cedar`, `bell` to `wood`): the class of the phaser "blub" of 5c-9.
 *
 * **What it costs: the rate at which a material can change, a block count.** A change that arrives
 * while a fade runs is parked (latest wins) and installed when the fade lands, so the swap can start
 * a change once every `ceil(fadeLen / blockFrames)` blocks: 18 blocks (52.2 ms) at 44.1 kHz, 19 blocks
 * (50.7 ms) at 48 kHz, about 19 changes per second. In note values: 16ths pass at any tempo up to about
 * 287 BPM; 32nds pass up to about 143 BPM at 44.1 kHz and 148 BPM at 48 kHz and park above (at 174 BPM a
 * 32nd is 43.1 ms apart); 64ths park above about 72 BPM (at 20 ms they passed up to about 184 BPM at 44.1 kHz and 176 BPM at 48 kHz).
 * Past the limit the parking does not THIN a run, it ALIASES it: the changes that survive are
 * phase-locked, so a four-material pattern at twice the limit is heard as a two-material alternation
 * with two of the four never sounding at all.
 */
const val BANK_CROSSFADE_SECONDS: Double = 0.05
