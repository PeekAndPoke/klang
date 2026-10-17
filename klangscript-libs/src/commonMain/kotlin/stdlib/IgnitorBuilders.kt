/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:KlangScript.Library(KlangScriptLibraries.STDLIB)

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.OscImpulseBuilder
import io.peekandpoke.klang.audio_bridge.OscPluckBuilder
import io.peekandpoke.klang.audio_bridge.OscPulzeBuilder
import io.peekandpoke.klang.audio_bridge.OscRampBuilder
import io.peekandpoke.klang.audio_bridge.OscSawBuilder
import io.peekandpoke.klang.audio_bridge.OscSineBuilder
import io.peekandpoke.klang.audio_bridge.OscSquareBuilder
import io.peekandpoke.klang.audio_bridge.OscSuperPluckBuilder
import io.peekandpoke.klang.audio_bridge.OscSuperRampBuilder
import io.peekandpoke.klang.audio_bridge.OscSuperSawBuilder
import io.peekandpoke.klang.audio_bridge.OscSuperSineBuilder
import io.peekandpoke.klang.audio_bridge.OscSuperSquareBuilder
import io.peekandpoke.klang.audio_bridge.OscSuperTriBuilder
import io.peekandpoke.klang.audio_bridge.OscTriBuilder
import io.peekandpoke.klang.audio_bridge.OscZampBuilder
import io.peekandpoke.klang.audio_bridge.OscZawtoothBuilder
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/*
 * The oscillator builders' SCRIPT knobs: what an `Ignitor.*` door's `configure` lambda can call.
 *
 *     Ignitor.supersaw(x => x.voices(9).spread(0.1).phasePool()).lowpass(800)
 *     //           ^ x: OscSuperSawBuilder                    ^ back on the plain sound
 *
 * The builders themselves live in `audio_bridge` (`IgnitorBuilders.kt` there, since the typed-doors rollout S1 and
 * S2), one immutable value wrapper per oscillator type with its TYPED member knobs, and are the same builders on the
 * Kotlin door: `Ign.supersaw { it.voices(9.ign).spread(0.1.ign) }`. The knobs below are `internal` extensions
 * registered by KSP from `@KlangScript.Function`, each with a Kotlin name of its own (`scriptVoices`, the script word
 * in `name`) that converts its `Any` arguments and calls the member, so a renamed or retyped member is a compile error,
 * never a self-call (`/dsl-design` section 3, the typed-door pattern). The user-facing KDoc of every knob lives here
 * (KSP reads KDoc only from its own module). Doors (`Ignitor.sine(freq, configure)`) live in [KlangScriptIgnitor];
 * the lambda plumbing and the error contract in `configuredBy`.
 */

// ── Sine ─────────────────────────────────────────────────────────────────

/**
 * Where in its cycle the sine runs: a fraction of one cycle added to its phase every sample (default 0). Phase 0 is
 * `sin(0)`, the upward zero crossing. 0.5 is half a cycle on, and it wraps, no clamp: 1.25 is 0.25, -0.25 is 0.75. A
 * number is the start phase; a signal moves the phase while the note plays, which is phase modulation (a jump clicks).
 * With partial banks every partial moves by the same fraction of its OWN cycle (the fundamental by `phase`): 0.5
 * inverts the whole wave, other values change how the partials line up (0.25 starts every partial on its peak, the
 * peakiest alignment, which matters ahead of a `distort` or `shape`). A partial that joins the bank mid-note (a moving
 * count) starts at the phase applied so far, so at a phase other than 0 or 0.5 it enters with a step of up to its own
 * gain (0.25 and 0.75 the largest), a click (raw); at 0 or 0.5 it enters on a zero crossing.
 *
 * ```KlangScript
 * Ignitor.sine(4, x => x.phase(0.25))   // an LFO that starts at its peak
 * ```
 */
@KlangScript.Function(name = "phase")
internal fun OscSineBuilder.scriptPhase(phase: IgnitorDslLike): OscSineBuilder =
    this.phase(phase = phase.toIgnitorDsl())

/** How analog the oscillator is, a unitless character scale: 0 ideal (perfectly stable), 1 to 8 usual, 10 strong; its tell is the pitch drift, about one cent of peak per unit. Latches at note-on. With partial banks, the depth of every partial. */
@KlangScript.Function(name = "analog")
internal fun OscSineBuilder.scriptAnalog(character: IgnitorDslLike): OscSineBuilder =
    this.analog(character = character.toIgnitorDsl())

/**
 * Gain of the sine's own partial (default 1). `0` leaves only the partial banks, so
 * `harmonics(7).fundamental(0)` puts the overtones on their own fader next to a separate sub. A signal
 * like every knob (an `Ignitor.param`, an LFO), read once per block.
 *
 * ```KlangScript
 * Ignitor.sine(x => x.harmonics(7).fundamental(0))    // overtones only, 2f .. 8f
 * ```
 */
@KlangScript.Function(name = "fundamental")
internal fun OscSineBuilder.scriptFundamental(gain: IgnitorDslLike): OscSineBuilder =
    this.fundamental(gain = gain.toIgnitorDsl())

/**
 * Adds `count` sine partials at `2f, 3f, 4f ...` of THIS sine's frequency, its harmonic series, rendered in
 * one pass with the sine. A partial at multiple `m` has gain `m ^ -rolloff`: `rolloff` 1 (default) is the
 * sawtooth law, 2 is triangle-soft, 0 is flat and buzzy. `count` 0 is off, at most 64. The multiples are of the sine, not of
 * the note: `Ignitor.sine(Ignitor.freq().mul(2), x => x.harmonics(3))` is `2f, 4f, 6f, 8f`, the even series. Partials
 * at or above Nyquist stay silent. Both arguments are signals read once per block, so
 * `harmonics(12, Ignitor.param("rolloff", 1))` puts brightness on the pattern; a moving `count` steps on every
 * removal (and, with a `phase` other than 0 or 0.5, on every addition too), a moving `rolloff` does not. Banks sum: `harmonics(7)` plus
 * `octaves(3)` doubles the shared partials, as two written sines would.
 *
 * ```KlangScript
 * Ignitor.sine(x => x.harmonics(7))                   // a bass: f plus 2f .. 8f, the ear rebuilds the fundamental on small speakers
 * Ignitor.sine(x => x.harmonics(8, Ignitor.sine(0.2).range(0.7, 2)))   // breathing brightness
 * ```
 */
@KlangScript.Function(name = "harmonics")
internal fun OscSineBuilder.scriptHarmonics(count: IgnitorDslLike, rolloff: IgnitorDslLike = 1.0): OscSineBuilder =
    this.harmonics(count = count.toIgnitorDsl(), rolloff = rolloff.toIgnitorDsl())

/**
 * Adds `count` sine partials at `2f, 4f, 8f ...` of THIS sine's frequency, one per octave. A partial at multiple
 * `m` has gain `m ^ -rolloff` (default 1, the sawtooth law sampled at the octaves). `count` 0 is off, at most 64. Climbs fast:
 * five octaves reach `32f`, a brightness and grind device rather than fundamental reconstruction. Partials at or
 * above Nyquist stay silent. Signals read once per block.
 *
 * ```KlangScript
 * Ignitor.sine(Ignitor.freq().mul(2), x => x.octaves(5)).mul(1/2)   // 2f .. 64f at 1/2 .. 1/64, an octave stack over a saw
 * ```
 */
@KlangScript.Function(name = "octaves")
internal fun OscSineBuilder.scriptOctaves(count: IgnitorDslLike, rolloff: IgnitorDslLike = 1.0): OscSineBuilder =
    this.octaves(count = count.toIgnitorDsl(), rolloff = rolloff.toIgnitorDsl())

/**
 * Adds `count` sine partials at `f/2, f/4, f/8 ...` below THIS sine, the sub oscillator every mono synth has. The
 * partial at `f/m` has gain `m ^ -rolloff` (default 1: the sub at half gain, `count` at most 64); `suboctaves(1, 0)` is the classic
 * equal-level sub. A sub at a comparable level MOVES THE PERCEIVED PITCH down an octave (the ear takes `f/2` as the
 * fundamental), which is the point. No lower limit: two sub-octaves on a low E are 10 Hz, headroom for nothing
 * audible; the highpass is yours. Signals read once per block.
 *
 * ```KlangScript
 * Ignitor.sine(x => x.suboctaves(1, 0))    // f and f/2 at equal level
 * ```
 */
@KlangScript.Function(name = "suboctaves")
internal fun OscSineBuilder.scriptSuboctaves(count: IgnitorDslLike, rolloff: IgnitorDslLike = 1.0): OscSineBuilder =
    this.suboctaves(count = count.toIgnitorDsl(), rolloff = rolloff.toIgnitorDsl())

/**
 * Adds ONE sine partial at `ratio` times THIS sine's frequency, at `gain`, starting `phase` into its cycle; every call
 * adds one more, kept in the order written. Any ratio, no integer rule, so a cluster of inharmonic partials is one
 * sine (a drum's thud, a bell, a metal plate). `gain` is linear and raw (default 1), and a negative gain is the same
 * partial at `phase + 0.5`. `phase` is a fraction of one cycle like the oscillators' `phase` (default 0, the upward zero
 * crossing; 0.5 is the inverted partial, 0.25 starts on its peak); in a cluster the start phases decide how peaky the
 * onset is. The partials sum raw with the sine's own partial and the other banks: a ratio of 1 doubles the
 * fundamental, so a pure cluster writes `fundamental(0)`. A partial at or above Nyquist stays silent. Every argument is
 * a number or a signal: a signal `gain` plays every sample, so each mode of a bell can have its own envelope
 * (`partial(2.76, Ignitor.constant(0.5).adsr(0.001, 0.8, 0, 0.1))`, the same as that partial as its own sine under the
 * same `adsr`); the ratio and the phase are read once per block (a moving ratio steps, a moving phase glides across the
 * block). `analog` drifts every partial, `analogSpread` says whether they drift as one or each on its own. At most
 * 256 per sine; a song that adds more plays the first 256.
 *
 * ```KlangScript
 * Ignitor.sine(x => x.fundamental(0).partial(0.6571, 0.52).partial(0.8714, 0.652, 0.5))   // two partials of a thud
 * Ignitor.sine(x => x.partial(ratio = 2.76, gain = 0.5, phase = 0.25))   // the sine and a bell-like overtone
 * Ignitor.sine(x => x.fundamental(0).partial(1, Ignitor.constant(1).adsr(0.001, 0.8, 0, 0.1))
 *   .partial(2.76, Ignitor.constant(0.5).adsr(0.001, 0.3, 0, 0.1)))    // a bell: each mode with its own decay
 * ```
 */
@KlangScript.Function(name = "partial")
internal fun OscSineBuilder.scriptPartial(ratio: IgnitorDslLike, gain: IgnitorDslLike = 1.0, phase: IgnitorDslLike = 0.0): OscSineBuilder =
    this.partial(ratio = ratio.toIgnitorDsl(), gain = gain.toIgnitorDsl(), phase = phase.toIgnitorDsl())

/**
 * How much the partials drift against each other under `analog`, 0 to 1. `1` (default): every partial walks on
 * its own lane, the slow beating of a hand-stacked set of sines. `0`: one shared walk, the bank wobbles as a
 * single physical oscillator and its ratios stay exact. Between is a blend. Nothing happens while
 * `analog` is 0. Named apart from the supersaw's `spread`, which is static unison detune.
 *
 * ```KlangScript
 * Ignitor.sine(x => x.harmonics(7).analog(3).analogSpread(0))   // one drifting oscillator with seven harmonics
 * ```
 */
@KlangScript.Function(name = "analogSpread")
internal fun OscSineBuilder.scriptAnalogSpread(amount: IgnitorDslLike): OscSineBuilder =
    this.analogSpread(amount = amount.toIgnitorDsl())

// ── Tri ─────────────────────────────────────────────────────────────────

/**
 * Where in its cycle the triangle runs: a fraction of one cycle added to its phase every sample (default 0). Phase 0
 * is -1, its lowest point, rising to +1 at 0.5. 0.5 is half a cycle on, and it wraps, no clamp: 1.25 is 0.25, -0.25 is
 * 0.75. A number is the start phase; a signal moves the phase while the note plays, which is phase modulation (a jump
 * clicks).
 *
 * ```KlangScript
 * Ignitor.tri(2, x => x.phase(0.5))   // starts at its top
 * ```
 */
@KlangScript.Function(name = "phase")
internal fun OscTriBuilder.scriptPhase(phase: IgnitorDslLike): OscTriBuilder = this.phase(phase = phase.toIgnitorDsl())

/** How analog the oscillator is, a unitless character scale: 0 ideal (perfectly stable), 1 to 8 usual, 10 strong; its tell is the pitch drift, about one cent of peak per unit. Latches at note-on. */
@KlangScript.Function(name = "analog")
internal fun OscTriBuilder.scriptAnalog(character: IgnitorDslLike): OscTriBuilder =
    this.analog(character = character.toIgnitorDsl())

// ── Zawtooth ─────────────────────────────────────────────────────────────────

/**
 * Where in its cycle the zawtooth runs: a fraction of one cycle added to its phase every sample (default 0). Phase 0
 * is -1, the bottom of its rise (the reset ends the cycle). 0.5 is half a cycle on, and it wraps, no clamp: 1.25 is
 * 0.25, -0.25 is 0.75. A number is the start phase; a signal moves the phase while the note plays, which is phase
 * modulation (a jump clicks).
 *
 * ```KlangScript
 * Ignitor.zawtooth(x => x.phase(0.5))   // starts mid-rise, at 0
 * ```
 */
@KlangScript.Function(name = "phase")
internal fun OscZawtoothBuilder.scriptPhase(phase: IgnitorDslLike): OscZawtoothBuilder =
    this.phase(phase = phase.toIgnitorDsl())

/** How analog the oscillator is, a unitless character scale: 0 ideal (perfectly stable), 1 to 8 usual, 10 strong; its tell is the pitch drift, about one cent of peak per unit. Latches at note-on. */
@KlangScript.Function(name = "analog")
internal fun OscZawtoothBuilder.scriptAnalog(character: IgnitorDslLike): OscZawtoothBuilder =
    this.analog(character = character.toIgnitorDsl())

// ── Zamp ─────────────────────────────────────────────────────────────────

/**
 * Where in its cycle the zamp runs: a fraction of one cycle added to its phase every sample (default 0). Phase 0 is
 * +1, the top of its fall (the reset ends the cycle). 0.5 is half a cycle on, and it wraps, no clamp: 1.25 is 0.25,
 * -0.25 is 0.75. A number is the start phase; a signal moves the phase while the note plays, which is phase modulation
 * (a jump clicks).
 *
 * ```KlangScript
 * Ignitor.zamp(x => x.phase(0.5))   // starts mid-fall, at 0
 * ```
 */
@KlangScript.Function(name = "phase")
internal fun OscZampBuilder.scriptPhase(phase: IgnitorDslLike): OscZampBuilder =
    this.phase(phase = phase.toIgnitorDsl())

/** How analog the oscillator is, a unitless character scale: 0 ideal (perfectly stable), 1 to 8 usual, 10 strong; its tell is the pitch drift, about one cent of peak per unit. Latches at note-on. */
@KlangScript.Function(name = "analog")
internal fun OscZampBuilder.scriptAnalog(character: IgnitorDslLike): OscZampBuilder =
    this.analog(character = character.toIgnitorDsl())

// ── Impulse ─────────────────────────────────────────────────────────────────

/**
 * Where in its cycle the impulse runs: a fraction of one cycle added to its phase every sample (default 0). Phase 0 is
 * the spike: the note starts on it. 0.5 is half a cycle on, and it wraps, no clamp: 1.25 is 0.25, -0.25 is 0.75. A
 * number is the start phase; a signal moves the phase while the note plays, which is phase modulation (a jump clicks).
 * The spike sits where the shifted phase passes 0 going forward, so a start phase of 0.25 spikes first after three
 * quarters of a cycle, and a step back (within half a cycle) spikes nothing.
 *
 * ```KlangScript
 * Ignitor.impulse(2, x => x.phase(0.5))   // the first spike half a cycle in
 * ```
 */
@KlangScript.Function(name = "phase")
internal fun OscImpulseBuilder.scriptPhase(phase: IgnitorDslLike): OscImpulseBuilder =
    this.phase(phase = phase.toIgnitorDsl())

/** How analog the oscillator is, a unitless character scale: 0 ideal (perfectly stable), 1 to 8 usual, 10 strong; its tell is the pitch drift, about one cent of peak per unit. Latches at note-on. */
@KlangScript.Function(name = "analog")
internal fun OscImpulseBuilder.scriptAnalog(character: IgnitorDslLike): OscImpulseBuilder =
    this.analog(character = character.toIgnitorDsl())

// ── RawPulze ─────────────────────────────────────────────────────────────────

/**
 * Where in its cycle the raw pulse runs: a fraction of one cycle added to its phase every sample (default 0). Phase 0
 * is +1, the start of its high plateau: the instant rising edge sits at the wrap (high until `duty`, then low). So
 * `pulze` starts high where the rounded `square` starts low, on the other side of its rising edge. 0.5 is half a cycle
 * on, and it wraps, no clamp: 1.25 is 0.25, -0.25 is 0.75. A number is the start phase; a signal moves the phase while
 * the note plays, which is phase modulation (a jump clicks).
 *
 * ```KlangScript
 * Ignitor.pulze(2, x => x.phase(0.5))   // a gate LFO that starts closed (low), then opens
 * ```
 */
@KlangScript.Function(name = "phase")
internal fun OscPulzeBuilder.scriptPhase(phase: IgnitorDslLike): OscPulzeBuilder =
    this.phase(phase = phase.toIgnitorDsl())

/** Pulse width / duty cycle: `width` is the share of each cycle the wave is high, 0 to 1 (default 0.5, a square). Accepts an `Ignitor.*` graph for PWM. */
@KlangScript.Function(name = "duty")
internal fun OscPulzeBuilder.scriptDuty(width: IgnitorDslLike): OscPulzeBuilder =
    this.duty(width = width.toIgnitorDsl())

/** How analog the oscillator is, a unitless character scale: 0 ideal (perfectly stable), 1 to 8 usual, 10 strong; its tell is the pitch drift, about one cent of peak per unit. Latches at note-on. */
@KlangScript.Function(name = "analog")
internal fun OscPulzeBuilder.scriptAnalog(character: IgnitorDslLike): OscPulzeBuilder =
    this.analog(character = character.toIgnitorDsl())

// ── Pulze ─────────────────────────────────────────────────────────────────

/**
 * Where in its cycle the square runs: a fraction of one cycle added to its phase every sample (default 0). Phase 0 is
 * -1, the foot of its rising edge (rise, high until `duty`, fall, low), where the raw `pulze` starts high. 0.5 is half
 * a cycle on, and it wraps, no clamp: 1.25 is 0.25, -0.25 is 0.75. A number is the start phase; a signal moves the
 * phase while the note plays, which is phase modulation (a jump clicks). A fast-moving phase also squeezes the soft
 * edges (sized from the note's own pitch), which then alias as the raw twin's do.
 *
 * ```KlangScript
 * Ignitor.square(2, x => x.phase(0.5))   // an LFO that starts on its falling edge
 * ```
 */
@KlangScript.Function(name = "phase")
internal fun OscSquareBuilder.scriptPhase(phase: IgnitorDslLike): OscSquareBuilder =
    this.phase(phase = phase.toIgnitorDsl())

/** Pulse width / duty cycle: `width` is the share of each cycle the wave is high, 0 to 1 (default 0.5, a square). Accepts an `Ignitor.*` graph for PWM. */
@KlangScript.Function(name = "duty")
internal fun OscSquareBuilder.scriptDuty(width: IgnitorDslLike): OscSquareBuilder =
    this.duty(width = width.toIgnitorDsl())

/** How analog the oscillator is, a unitless character scale: 0 ideal (perfectly stable), 1 to 8 usual, 10 strong; its tell is the pitch drift, about one cent of peak per unit. Latches at note-on. */
@KlangScript.Function(name = "analog")
internal fun OscSquareBuilder.scriptAnalog(character: IgnitorDslLike): OscSquareBuilder =
    this.analog(character = character.toIgnitorDsl())

/** Minimum flank length in samples, a floor on every edge that softens with pitch (default 2.0). */
@KlangScript.Function(name = "flankSamples")
internal fun OscSquareBuilder.scriptFlankSamples(flankSamples: Double): OscSquareBuilder =
    this.flankSamples(flankSamples = flankSamples)

/** Rising-edge flank fraction of the plateau: 0 = sharpest (the floor), 1 = full ramp (default 0.0). */
@KlangScript.Function(name = "riseFlank")
internal fun OscSquareBuilder.scriptRiseFlank(riseFlank: Double): OscSquareBuilder =
    this.riseFlank(riseFlank = riseFlank)

/** Falling-edge flank fraction of the plateau: 0 = sharpest (the floor), 1 = full ramp (default 0.0). */
@KlangScript.Function(name = "fallFlank")
internal fun OscSquareBuilder.scriptFallFlank(fallFlank: Double): OscSquareBuilder =
    this.fallFlank(fallFlank = fallFlank)

// ── Saw ─────────────────────────────────────────────────────────────────

/**
 * Where in its cycle the sawtooth runs: a fraction of one cycle added to its phase every sample (default 0). Phase 0
 * is -1, the bottom of its rise (the flyback ends the cycle). 0.5 is half a cycle on, and it wraps, no clamp: 1.25 is
 * 0.25, -0.25 is 0.75. A number is the start phase; a signal moves the phase while the note plays, which is phase
 * modulation (a jump clicks). A fast-moving phase also squeezes the soft edges (sized from the note's own pitch),
 * which then alias as the raw twin's do.
 *
 * ```KlangScript
 * Ignitor.saw(x => x.phase(0.5))   // starts mid-rise, at 0
 * ```
 */
@KlangScript.Function(name = "phase")
internal fun OscSawBuilder.scriptPhase(phase: IgnitorDslLike): OscSawBuilder = this.phase(phase = phase.toIgnitorDsl())

/** How analog the oscillator is, a unitless character scale: 0 ideal (perfectly stable), 1 to 8 usual, 10 strong; its tell is the pitch drift, about one cent of peak per unit. Latches at note-on. */
@KlangScript.Function(name = "analog")
internal fun OscSawBuilder.scriptAnalog(character: IgnitorDslLike): OscSawBuilder = this.analog(character = character.toIgnitorDsl())

/** Analog flyback time in samples: lower = brighter, sharper reset; higher = softer (default 1.0). */
@KlangScript.Function(name = "resetSamples")
internal fun OscSawBuilder.scriptResetSamples(resetSamples: Double): OscSawBuilder = this.resetSamples(resetSamples = resetSamples)

/** Max flyback fraction of a cycle: 0.5 = symmetric-triangle limit; keeps high notes sane (default 0.5). */
@KlangScript.Function(name = "shapeMax")
internal fun OscSawBuilder.scriptShapeMax(shapeMax: Double): OscSawBuilder = this.shapeMax(shapeMax = shapeMax)

// ── Ramp ─────────────────────────────────────────────────────────────────

/**
 * Where in its cycle the ramp runs: a fraction of one cycle added to its phase every sample (default 0). Phase 0 is
 * +1, the top of its fall (the flyback ends the cycle). 0.5 is half a cycle on, and it wraps, no clamp: 1.25 is 0.25,
 * -0.25 is 0.75. A number is the start phase; a signal moves the phase while the note plays, which is phase modulation
 * (a jump clicks). A fast-moving phase also squeezes the soft edges (sized from the note's own pitch), which then
 * alias as the raw twin's do.
 *
 * ```KlangScript
 * Ignitor.ramp(x => x.phase(0.5))   // starts mid-fall, at 0
 * ```
 */
@KlangScript.Function(name = "phase")
internal fun OscRampBuilder.scriptPhase(phase: IgnitorDslLike): OscRampBuilder =
    this.phase(phase = phase.toIgnitorDsl())

/** How analog the oscillator is, a unitless character scale: 0 ideal (perfectly stable), 1 to 8 usual, 10 strong; its tell is the pitch drift, about one cent of peak per unit. Latches at note-on. */
@KlangScript.Function(name = "analog")
internal fun OscRampBuilder.scriptAnalog(character: IgnitorDslLike): OscRampBuilder =
    this.analog(character = character.toIgnitorDsl())

/** Analog flyback time in samples: lower = brighter, sharper reset; higher = softer (default 1.0). */
@KlangScript.Function(name = "resetSamples")
internal fun OscRampBuilder.scriptResetSamples(resetSamples: Double): OscRampBuilder =
    this.resetSamples(resetSamples = resetSamples)

/** Max flyback fraction of a cycle: 0.5 = symmetric-triangle limit; keeps high notes sane (default 0.5). */
@KlangScript.Function(name = "shapeMax")
internal fun OscRampBuilder.scriptShapeMax(shapeMax: Double): OscRampBuilder = this.shapeMax(shapeMax = shapeMax)

// ── SuperSaw ─────────────────────────────────────────────────────────────────

/**
 * Shifts the whole stack in its cycle: a fraction of one cycle added to every voice's phase every sample (default 0),
 * every voice by the same fraction of its own cycle, so the spread of start phases stays as drawn. Each voice starts
 * at its own random phase (or the phase pool's), measured from the saw's phase 0, which is -1, the bottom of its rise.
 * Because those start phases are drawn anew for every note, a constant shift on its own is not audible: the stack's
 * use is a MOVING phase, phase modulation of the whole stack. It wraps, no clamp (1.25 is 0.25); a jump clicks. A
 * fast-moving phase also squeezes the soft edges (sized from the note's own pitch), which then alias as the raw twin's
 * do.
 *
 * ```KlangScript
 * Ignitor.supersaw(x => x.voices(5).phase(Ignitor.sine(5).mul(0.2)))   // phase modulation: a 5 Hz wobble on every voice
 * ```
 */
@KlangScript.Function(name = "phase")
internal fun OscSuperSawBuilder.scriptPhase(phase: IgnitorDslLike): OscSuperSawBuilder =
    this.phase(phase = phase.toIgnitorDsl())

/** Number of detuned voices in the stack (default 8). Accepts a number or an `Ignitor.*` graph (read once per block). */
@KlangScript.Function(name = "voices")
internal fun OscSuperSawBuilder.scriptVoices(voices: IgnitorDslLike): OscSuperSawBuilder =
    this.voices(voices = voices.toIgnitorDsl())

/** Unison frequency spread between the voices (default 0.2). Same knob as the pattern-level `.spread()`. */
@KlangScript.Function(name = "spread")
internal fun OscSuperSawBuilder.scriptSpread(spread: IgnitorDslLike): OscSuperSawBuilder =
    this.spread(spread = spread.toIgnitorDsl())

/** How analog the oscillator is, a unitless character scale: 0 ideal (perfectly stable), 1 to 8 usual, 10 strong; its tell is the pitch drift, about one cent of peak per unit. Latches at note-on. */
@KlangScript.Function(name = "analog")
internal fun OscSuperSawBuilder.scriptAnalog(character: IgnitorDslLike): OscSuperSawBuilder =
    this.analog(character = character.toIgnitorDsl())

/** How much the voices drift against each other under `analog`, 0 to 1. `1` (default): every voice walks
 *  on its own lane, the organic unison of separate oscillators. `0`: one shared walk, the stack wobbles as
 *  a single oscillator and its unison detune stays static. Between is a blend. Nothing happens while
 *  `analog` is 0. Same knob as on `Ignitor.sine`; named apart from `spread`, which is the static unison detune. */
@KlangScript.Function(name = "analogSpread")
internal fun OscSuperSawBuilder.scriptAnalogSpread(amount: IgnitorDslLike): OscSuperSawBuilder =
    this.analogSpread(amount = amount.toIgnitorDsl())

/** Detune spacing shape: 1 = even, above 1 concentrates toward the center, below 1 spreads outward. */
@KlangScript.Function(name = "spreadPower")
internal fun OscSuperSawBuilder.scriptSpreadPower(spreadPower: Double): OscSuperSawBuilder =
    this.spreadPower(spreadPower = spreadPower)

/** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
@KlangScript.Function(name = "sideAtten")
internal fun OscSuperSawBuilder.scriptSideAtten(sideAtten: Double): OscSuperSawBuilder =
    this.sideAtten(sideAtten = sideAtten)

/** Per-voice random amplitude offset (a fraction, plus or minus); 0 = off. */
@KlangScript.Function(name = "gainJitter")
internal fun OscSuperSawBuilder.scriptGainJitter(gainJitter: Double): OscSuperSawBuilder =
    this.gainJitter(gainJitter = gainJitter)

/** Fraction of `gainJitter` the on-pitch center voice gets: 0 = stable center, 1 = jittered like the sides. */
@KlangScript.Function(name = "centerJitter")
internal fun OscSuperSawBuilder.scriptCenterJitter(centerJitter: Double): OscSuperSawBuilder =
    this.centerJitter(centerJitter = centerJitter)

/**
 * Configure the banded start-phase pool in ONE call. Every param is an optional plain
 * literal, so named-arg subsets work: `.phasePool()` (on, family defaults),
 * `.phasePool(kMin = 0.2)`, `.phasePool(refreshEvery = 0)` (all-named or all-positional,
 * KlangScript forbids mixing). Defaults mirror this family's engine constants.
 *
 * **Selection modes** (`selection`, value-colon form `"name[:width[:outliers]]"`):
 * - `"normal"` (default): normal-distribution serving over the pool's vocabulary, centered on
 *   the median (most typical) take. `width` sets the spread: `0` = always the median take,
 *   `0.1` = tight, `0.5` = default, `1` and above is near uniform (`"normal:1.5"` is random
 *   with a slight center edge). `outliers` (0..1, default 0) is the probability of serving an
 *   EXTREME take instead, the vocabulary's lowest- or highest-K entry (coin-flip side).
 *   `"normal:0.1:0.05"` = tight, 5% wild plucks.
 * - `"random"`: a uniformly random vocabulary entry each note (still band-accepted takes).
 * - `"roundrobin"` (opt-in): cycle the vocabulary; can gargle audibly.
 *
 * Unrecognized names or coefficients coerce to their defaults.
 *
 * @param on 1 = banded start-phase selection on, 0 = off (the engine default).
 * @param kMin accepted coherence band, lower edge (0 = cancelled, 1 = phase-aligned).
 * @param kMax accepted coherence band, upper edge. The band is also a timbre control.
 * @param drawTries candidate phase sets scored per draw (engine caps at 64).
 * @param poolSize vocabulary size per pool key (engine caps at 1024).
 * @param refreshEvery notes between fresh pool draws; 0 = frozen pool.
 * @param selection serving mode, `"name[:width[:outliers]]"`, see **Selection modes** above.
 * @param warmup entries seeded eagerly at pool creation (work-capped; 0 = fully lazy).
 */
@KlangScript.Function(name = "phasePool")
internal fun OscSuperSawBuilder.scriptPhasePool(
    on: Double = 1.0,
    kMin: Double = 0.30,
    kMax: Double = 0.55,
    drawTries: Double = 5.0,
    poolSize: Double = 256.0,
    refreshEvery: Double = 10.0,
    selection: String = "normal",
    warmup: Double = 16.0,
): OscSuperSawBuilder = this.phasePool(
    on = on,
    kMin = kMin,
    kMax = kMax,
    drawTries = drawTries,
    poolSize = poolSize,
    refreshEvery = refreshEvery,
    selection = selection,
    warmup = warmup,
)

// ── SuperSine ─────────────────────────────────────────────────────────────────

/**
 * Shifts the whole stack in its cycle: a fraction of one cycle added to every voice's phase every sample (default 0),
 * every voice by the same fraction of its own cycle, so the spread of start phases stays as drawn. Each voice starts
 * at its own random phase (or the phase pool's), measured from the sine's phase 0, which is `sin(0)`, the upward zero
 * crossing. Because those start phases are drawn anew for every note, a constant shift on its own is not audible: the
 * stack's use is a MOVING phase, phase modulation of the whole stack. It wraps, no clamp (1.25 is 0.25); a jump
 * clicks.
 *
 * ```KlangScript
 * Ignitor.supersine(x => x.voices(5).phase(Ignitor.sine(5).mul(0.2)))   // phase modulation: a 5 Hz wobble on every voice
 * ```
 */
@KlangScript.Function(name = "phase")
internal fun OscSuperSineBuilder.scriptPhase(phase: IgnitorDslLike): OscSuperSineBuilder =
    this.phase(phase = phase.toIgnitorDsl())

/** Number of detuned voices in the stack (default 8). Accepts a number or an `Ignitor.*` graph (read once per block). */
@KlangScript.Function(name = "voices")
internal fun OscSuperSineBuilder.scriptVoices(voices: IgnitorDslLike): OscSuperSineBuilder =
    this.voices(voices = voices.toIgnitorDsl())

/** Unison frequency spread between the voices (default 0.2). Same knob as the pattern-level `.spread()`. */
@KlangScript.Function(name = "spread")
internal fun OscSuperSineBuilder.scriptSpread(spread: IgnitorDslLike): OscSuperSineBuilder =
    this.spread(spread = spread.toIgnitorDsl())

/** How analog the oscillator is, a unitless character scale: 0 ideal (perfectly stable), 1 to 8 usual, 10 strong; its tell is the pitch drift, about one cent of peak per unit. Latches at note-on. */
@KlangScript.Function(name = "analog")
internal fun OscSuperSineBuilder.scriptAnalog(character: IgnitorDslLike): OscSuperSineBuilder =
    this.analog(character = character.toIgnitorDsl())

/** How much the voices drift against each other under `analog`, 0 to 1. `1` (default): every voice walks
 *  on its own lane, the organic unison of separate oscillators. `0`: one shared walk, the stack wobbles as
 *  a single oscillator and its unison detune stays static. Between is a blend. Nothing happens while
 *  `analog` is 0. Same knob as on `Ignitor.sine`; named apart from `spread`, which is the static unison detune. */
@KlangScript.Function(name = "analogSpread")
internal fun OscSuperSineBuilder.scriptAnalogSpread(amount: IgnitorDslLike): OscSuperSineBuilder =
    this.analogSpread(amount = amount.toIgnitorDsl())

/** Detune spacing shape: 1 = even, above 1 concentrates toward the center, below 1 spreads outward. */
@KlangScript.Function(name = "spreadPower")
internal fun OscSuperSineBuilder.scriptSpreadPower(spreadPower: Double): OscSuperSineBuilder =
    this.spreadPower(spreadPower = spreadPower)

/** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
@KlangScript.Function(name = "sideAtten")
internal fun OscSuperSineBuilder.scriptSideAtten(sideAtten: Double): OscSuperSineBuilder =
    this.sideAtten(sideAtten = sideAtten)

/** Per-voice random amplitude offset (a fraction, plus or minus); 0 = off. */
@KlangScript.Function(name = "gainJitter")
internal fun OscSuperSineBuilder.scriptGainJitter(gainJitter: Double): OscSuperSineBuilder =
    this.gainJitter(gainJitter = gainJitter)

/** Fraction of `gainJitter` the on-pitch center voice gets: 0 = stable center, 1 = jittered like the sides. */
@KlangScript.Function(name = "centerJitter")
internal fun OscSuperSineBuilder.scriptCenterJitter(centerJitter: Double): OscSuperSineBuilder =
    this.centerJitter(centerJitter = centerJitter)

/**
 * Configure the banded start-phase pool in ONE call. Every param is an optional plain
 * literal, so named-arg subsets work: `.phasePool()` (on, family defaults),
 * `.phasePool(kMin = 0.2)`, `.phasePool(refreshEvery = 0)` (all-named or all-positional,
 * KlangScript forbids mixing). Defaults mirror this family's engine constants.
 *
 * **Selection modes** (`selection`, value-colon form `"name[:width[:outliers]]"`):
 * - `"normal"` (default): normal-distribution serving over the pool's vocabulary, centered on
 *   the median (most typical) take. `width` sets the spread: `0` = always the median take,
 *   `0.1` = tight, `0.5` = default, `1` and above is near uniform (`"normal:1.5"` is random
 *   with a slight center edge). `outliers` (0..1, default 0) is the probability of serving an
 *   EXTREME take instead, the vocabulary's lowest- or highest-K entry (coin-flip side).
 *   `"normal:0.1:0.05"` = tight, 5% wild plucks.
 * - `"random"`: a uniformly random vocabulary entry each note (still band-accepted takes).
 * - `"roundrobin"` (opt-in): cycle the vocabulary; can gargle audibly.
 *
 * Unrecognized names or coefficients coerce to their defaults.
 *
 * @param on 1 = banded start-phase selection on, 0 = off (the engine default).
 * @param kMin accepted coherence band, lower edge (0 = cancelled, 1 = phase-aligned).
 * @param kMax accepted coherence band, upper edge. The band is also a timbre control.
 * @param drawTries candidate phase sets scored per draw (engine caps at 64).
 * @param poolSize vocabulary size per pool key (engine caps at 1024).
 * @param refreshEvery notes between fresh pool draws; 0 = frozen pool.
 * @param selection serving mode, `"name[:width[:outliers]]"`, see **Selection modes** above.
 * @param warmup entries seeded eagerly at pool creation (work-capped; 0 = fully lazy).
 */
@KlangScript.Function(name = "phasePool")
internal fun OscSuperSineBuilder.scriptPhasePool(
    on: Double = 1.0,
    kMin: Double = 0.50,
    kMax: Double = 0.80,
    drawTries: Double = 40.0,
    poolSize: Double = 256.0,
    refreshEvery: Double = 10.0,
    selection: String = "normal",
    warmup: Double = 16.0,
): OscSuperSineBuilder = this.phasePool(
    on = on,
    kMin = kMin,
    kMax = kMax,
    drawTries = drawTries,
    poolSize = poolSize,
    refreshEvery = refreshEvery,
    selection = selection,
    warmup = warmup,
)

// ── SuperSquare ─────────────────────────────────────────────────────────────────

/**
 * Shifts the whole stack in its cycle: a fraction of one cycle added to every voice's phase every sample (default 0),
 * every voice by the same fraction of its own cycle, so the spread of start phases stays as drawn. Each voice starts
 * at its own random phase (or the phase pool's), measured from the square's phase 0, which is -1, the foot of its
 * rising edge. Because those start phases are drawn anew for every note, a constant shift on its own is not audible:
 * the stack's use is a MOVING phase, phase modulation of the whole stack. It wraps, no clamp (1.25 is 0.25); a jump
 * clicks. A fast-moving phase also squeezes the soft edges (sized from the note's own pitch), which then alias as the
 * raw twin's do.
 *
 * ```KlangScript
 * Ignitor.supersquare(x => x.voices(5).phase(Ignitor.sine(5).mul(0.2)))   // phase modulation: a 5 Hz wobble on every voice
 * ```
 */
@KlangScript.Function(name = "phase")
internal fun OscSuperSquareBuilder.scriptPhase(phase: IgnitorDslLike): OscSuperSquareBuilder =
    this.phase(phase = phase.toIgnitorDsl())

/** Number of detuned voices in the stack (default 8). Accepts a number or an `Ignitor.*` graph (read once per block). */
@KlangScript.Function(name = "voices")
internal fun OscSuperSquareBuilder.scriptVoices(voices: IgnitorDslLike): OscSuperSquareBuilder =
    this.voices(voices = voices.toIgnitorDsl())

/** Unison frequency spread between the voices (default 0.2). Same knob as the pattern-level `.spread()`. */
@KlangScript.Function(name = "spread")
internal fun OscSuperSquareBuilder.scriptSpread(spread: IgnitorDslLike): OscSuperSquareBuilder =
    this.spread(spread = spread.toIgnitorDsl())

/** How analog the oscillator is, a unitless character scale: 0 ideal (perfectly stable), 1 to 8 usual, 10 strong; its tell is the pitch drift, about one cent of peak per unit. Latches at note-on. */
@KlangScript.Function(name = "analog")
internal fun OscSuperSquareBuilder.scriptAnalog(character: IgnitorDslLike): OscSuperSquareBuilder =
    this.analog(character = character.toIgnitorDsl())

/** How much the voices drift against each other under `analog`, 0 to 1. `1` (default): every voice walks
 *  on its own lane, the organic unison of separate oscillators. `0`: one shared walk, the stack wobbles as
 *  a single oscillator and its unison detune stays static. Between is a blend. Nothing happens while
 *  `analog` is 0. Same knob as on `Ignitor.sine`; named apart from `spread`, which is the static unison detune. */
@KlangScript.Function(name = "analogSpread")
internal fun OscSuperSquareBuilder.scriptAnalogSpread(amount: IgnitorDslLike): OscSuperSquareBuilder =
    this.analogSpread(amount = amount.toIgnitorDsl())

/** Detune spacing shape: 1 = even, above 1 concentrates toward the center, below 1 spreads outward. */
@KlangScript.Function(name = "spreadPower")
internal fun OscSuperSquareBuilder.scriptSpreadPower(spreadPower: Double): OscSuperSquareBuilder =
    this.spreadPower(spreadPower = spreadPower)

/** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
@KlangScript.Function(name = "sideAtten")
internal fun OscSuperSquareBuilder.scriptSideAtten(sideAtten: Double): OscSuperSquareBuilder =
    this.sideAtten(sideAtten = sideAtten)

/** Per-voice random amplitude offset (a fraction, plus or minus); 0 = off. */
@KlangScript.Function(name = "gainJitter")
internal fun OscSuperSquareBuilder.scriptGainJitter(gainJitter: Double): OscSuperSquareBuilder =
    this.gainJitter(gainJitter = gainJitter)

/** Fraction of `gainJitter` the on-pitch center voice gets: 0 = stable center, 1 = jittered like the sides. */
@KlangScript.Function(name = "centerJitter")
internal fun OscSuperSquareBuilder.scriptCenterJitter(centerJitter: Double): OscSuperSquareBuilder =
    this.centerJitter(centerJitter = centerJitter)

/**
 * Configure the banded start-phase pool in ONE call. Every param is an optional plain
 * literal, so named-arg subsets work: `.phasePool()` (on, family defaults),
 * `.phasePool(kMin = 0.2)`, `.phasePool(refreshEvery = 0)` (all-named or all-positional,
 * KlangScript forbids mixing). Defaults mirror this family's engine constants.
 *
 * **Selection modes** (`selection`, value-colon form `"name[:width[:outliers]]"`):
 * - `"normal"` (default): normal-distribution serving over the pool's vocabulary, centered on
 *   the median (most typical) take. `width` sets the spread: `0` = always the median take,
 *   `0.1` = tight, `0.5` = default, `1` and above is near uniform (`"normal:1.5"` is random
 *   with a slight center edge). `outliers` (0..1, default 0) is the probability of serving an
 *   EXTREME take instead, the vocabulary's lowest- or highest-K entry (coin-flip side).
 *   `"normal:0.1:0.05"` = tight, 5% wild plucks.
 * - `"random"`: a uniformly random vocabulary entry each note (still band-accepted takes).
 * - `"roundrobin"` (opt-in): cycle the vocabulary; can gargle audibly.
 *
 * Unrecognized names or coefficients coerce to their defaults.
 *
 * @param on 1 = banded start-phase selection on, 0 = off (the engine default).
 * @param kMin accepted coherence band, lower edge (0 = cancelled, 1 = phase-aligned).
 * @param kMax accepted coherence band, upper edge. The band is also a timbre control.
 * @param drawTries candidate phase sets scored per draw (engine caps at 64).
 * @param poolSize vocabulary size per pool key (engine caps at 1024).
 * @param refreshEvery notes between fresh pool draws; 0 = frozen pool.
 * @param selection serving mode, `"name[:width[:outliers]]"`, see **Selection modes** above.
 * @param warmup entries seeded eagerly at pool creation (work-capped; 0 = fully lazy).
 */
@KlangScript.Function(name = "phasePool")
internal fun OscSuperSquareBuilder.scriptPhasePool(
    on: Double = 1.0,
    kMin: Double = 0.30,
    kMax: Double = 0.55,
    drawTries: Double = 5.0,
    poolSize: Double = 256.0,
    refreshEvery: Double = 10.0,
    selection: String = "normal",
    warmup: Double = 16.0,
): OscSuperSquareBuilder = this.phasePool(
    on = on,
    kMin = kMin,
    kMax = kMax,
    drawTries = drawTries,
    poolSize = poolSize,
    refreshEvery = refreshEvery,
    selection = selection,
    warmup = warmup,
)

// ── SuperTri ─────────────────────────────────────────────────────────────────

/**
 * Shifts the whole stack in its cycle: a fraction of one cycle added to every voice's phase every sample (default 0),
 * every voice by the same fraction of its own cycle, so the spread of start phases stays as drawn. Each voice starts
 * at its own random phase (or the phase pool's), measured from the triangle's phase 0, which is -1, its lowest point.
 * Because those start phases are drawn anew for every note, a constant shift on its own is not audible: the stack's
 * use is a MOVING phase, phase modulation of the whole stack. It wraps, no clamp (1.25 is 0.25); a jump clicks.
 *
 * ```KlangScript
 * Ignitor.supertri(x => x.voices(5).phase(Ignitor.sine(5).mul(0.2)))   // phase modulation: a 5 Hz wobble on every voice
 * ```
 */
@KlangScript.Function(name = "phase")
internal fun OscSuperTriBuilder.scriptPhase(phase: IgnitorDslLike): OscSuperTriBuilder =
    this.phase(phase = phase.toIgnitorDsl())

/** Number of detuned voices in the stack (default 8). Accepts a number or an `Ignitor.*` graph (read once per block). */
@KlangScript.Function(name = "voices")
internal fun OscSuperTriBuilder.scriptVoices(voices: IgnitorDslLike): OscSuperTriBuilder =
    this.voices(voices = voices.toIgnitorDsl())

/** Unison frequency spread between the voices (default 0.2). Same knob as the pattern-level `.spread()`. */
@KlangScript.Function(name = "spread")
internal fun OscSuperTriBuilder.scriptSpread(spread: IgnitorDslLike): OscSuperTriBuilder =
    this.spread(spread = spread.toIgnitorDsl())

/** How analog the oscillator is, a unitless character scale: 0 ideal (perfectly stable), 1 to 8 usual, 10 strong; its tell is the pitch drift, about one cent of peak per unit. Latches at note-on. */
@KlangScript.Function(name = "analog")
internal fun OscSuperTriBuilder.scriptAnalog(character: IgnitorDslLike): OscSuperTriBuilder =
    this.analog(character = character.toIgnitorDsl())

/** How much the voices drift against each other under `analog`, 0 to 1. `1` (default): every voice walks
 *  on its own lane, the organic unison of separate oscillators. `0`: one shared walk, the stack wobbles as
 *  a single oscillator and its unison detune stays static. Between is a blend. Nothing happens while
 *  `analog` is 0. Same knob as on `Ignitor.sine`; named apart from `spread`, which is the static unison detune. */
@KlangScript.Function(name = "analogSpread")
internal fun OscSuperTriBuilder.scriptAnalogSpread(amount: IgnitorDslLike): OscSuperTriBuilder =
    this.analogSpread(amount = amount.toIgnitorDsl())

/** Detune spacing shape: 1 = even, above 1 concentrates toward the center, below 1 spreads outward. */
@KlangScript.Function(name = "spreadPower")
internal fun OscSuperTriBuilder.scriptSpreadPower(spreadPower: Double): OscSuperTriBuilder =
    this.spreadPower(spreadPower = spreadPower)

/** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
@KlangScript.Function(name = "sideAtten")
internal fun OscSuperTriBuilder.scriptSideAtten(sideAtten: Double): OscSuperTriBuilder =
    this.sideAtten(sideAtten = sideAtten)

/** Per-voice random amplitude offset (a fraction, plus or minus); 0 = off. */
@KlangScript.Function(name = "gainJitter")
internal fun OscSuperTriBuilder.scriptGainJitter(gainJitter: Double): OscSuperTriBuilder =
    this.gainJitter(gainJitter = gainJitter)

/** Fraction of `gainJitter` the on-pitch center voice gets: 0 = stable center, 1 = jittered like the sides. */
@KlangScript.Function(name = "centerJitter")
internal fun OscSuperTriBuilder.scriptCenterJitter(centerJitter: Double): OscSuperTriBuilder =
    this.centerJitter(centerJitter = centerJitter)

/**
 * Configure the banded start-phase pool in ONE call. Every param is an optional plain
 * literal, so named-arg subsets work: `.phasePool()` (on, family defaults),
 * `.phasePool(kMin = 0.2)`, `.phasePool(refreshEvery = 0)` (all-named or all-positional,
 * KlangScript forbids mixing). Defaults mirror this family's engine constants.
 *
 * **Selection modes** (`selection`, value-colon form `"name[:width[:outliers]]"`):
 * - `"normal"` (default): normal-distribution serving over the pool's vocabulary, centered on
 *   the median (most typical) take. `width` sets the spread: `0` = always the median take,
 *   `0.1` = tight, `0.5` = default, `1` and above is near uniform (`"normal:1.5"` is random
 *   with a slight center edge). `outliers` (0..1, default 0) is the probability of serving an
 *   EXTREME take instead, the vocabulary's lowest- or highest-K entry (coin-flip side).
 *   `"normal:0.1:0.05"` = tight, 5% wild plucks.
 * - `"random"`: a uniformly random vocabulary entry each note (still band-accepted takes).
 * - `"roundrobin"` (opt-in): cycle the vocabulary; can gargle audibly.
 *
 * Unrecognized names or coefficients coerce to their defaults.
 *
 * @param on 1 = banded start-phase selection on, 0 = off (the engine default).
 * @param kMin accepted coherence band, lower edge (0 = cancelled, 1 = phase-aligned).
 * @param kMax accepted coherence band, upper edge. The band is also a timbre control.
 * @param drawTries candidate phase sets scored per draw (engine caps at 64).
 * @param poolSize vocabulary size per pool key (engine caps at 1024).
 * @param refreshEvery notes between fresh pool draws; 0 = frozen pool.
 * @param selection serving mode, `"name[:width[:outliers]]"`, see **Selection modes** above.
 * @param warmup entries seeded eagerly at pool creation (work-capped; 0 = fully lazy).
 */
@KlangScript.Function(name = "phasePool")
internal fun OscSuperTriBuilder.scriptPhasePool(
    on: Double = 1.0,
    kMin: Double = 0.40,
    kMax: Double = 0.65,
    drawTries: Double = 16.0,
    poolSize: Double = 256.0,
    refreshEvery: Double = 10.0,
    selection: String = "normal",
    warmup: Double = 16.0,
): OscSuperTriBuilder = this.phasePool(
    on = on,
    kMin = kMin,
    kMax = kMax,
    drawTries = drawTries,
    poolSize = poolSize,
    refreshEvery = refreshEvery,
    selection = selection,
    warmup = warmup,
)

// ── SuperRamp ─────────────────────────────────────────────────────────────────

/**
 * Shifts the whole stack in its cycle: a fraction of one cycle added to every voice's phase every sample (default 0),
 * every voice by the same fraction of its own cycle, so the spread of start phases stays as drawn. Each voice starts
 * at its own random phase (or the phase pool's), measured from the ramp's phase 0, which is +1, the top of its fall.
 * Because those start phases are drawn anew for every note, a constant shift on its own is not audible: the stack's
 * use is a MOVING phase, phase modulation of the whole stack. It wraps, no clamp (1.25 is 0.25); a jump clicks. A
 * fast-moving phase also squeezes the soft edges (sized from the note's own pitch), which then alias as the raw twin's
 * do.
 *
 * ```KlangScript
 * Ignitor.superramp(x => x.voices(5).phase(Ignitor.sine(5).mul(0.2)))   // phase modulation: a 5 Hz wobble on every voice
 * ```
 */
@KlangScript.Function(name = "phase")
internal fun OscSuperRampBuilder.scriptPhase(phase: IgnitorDslLike): OscSuperRampBuilder =
    this.phase(phase = phase.toIgnitorDsl())

/** Number of detuned voices in the stack (default 8). Accepts a number or an `Ignitor.*` graph (read once per block). */
@KlangScript.Function(name = "voices")
internal fun OscSuperRampBuilder.scriptVoices(voices: IgnitorDslLike): OscSuperRampBuilder =
    this.voices(voices = voices.toIgnitorDsl())

/** Unison frequency spread between the voices (default 0.2). Same knob as the pattern-level `.spread()`. */
@KlangScript.Function(name = "spread")
internal fun OscSuperRampBuilder.scriptSpread(spread: IgnitorDslLike): OscSuperRampBuilder =
    this.spread(spread = spread.toIgnitorDsl())

/** How analog the oscillator is, a unitless character scale: 0 ideal (perfectly stable), 1 to 8 usual, 10 strong; its tell is the pitch drift, about one cent of peak per unit. Latches at note-on. */
@KlangScript.Function(name = "analog")
internal fun OscSuperRampBuilder.scriptAnalog(character: IgnitorDslLike): OscSuperRampBuilder =
    this.analog(character = character.toIgnitorDsl())

/** How much the voices drift against each other under `analog`, 0 to 1. `1` (default): every voice walks
 *  on its own lane, the organic unison of separate oscillators. `0`: one shared walk, the stack wobbles as
 *  a single oscillator and its unison detune stays static. Between is a blend. Nothing happens while
 *  `analog` is 0. Same knob as on `Ignitor.sine`; named apart from `spread`, which is the static unison detune. */
@KlangScript.Function(name = "analogSpread")
internal fun OscSuperRampBuilder.scriptAnalogSpread(amount: IgnitorDslLike): OscSuperRampBuilder =
    this.analogSpread(amount = amount.toIgnitorDsl())

/** Detune spacing shape: 1 = even, above 1 concentrates toward the center, below 1 spreads outward. */
@KlangScript.Function(name = "spreadPower")
internal fun OscSuperRampBuilder.scriptSpreadPower(spreadPower: Double): OscSuperRampBuilder =
    this.spreadPower(spreadPower = spreadPower)

/** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
@KlangScript.Function(name = "sideAtten")
internal fun OscSuperRampBuilder.scriptSideAtten(sideAtten: Double): OscSuperRampBuilder =
    this.sideAtten(sideAtten = sideAtten)

/** Per-voice random amplitude offset (a fraction, plus or minus); 0 = off. */
@KlangScript.Function(name = "gainJitter")
internal fun OscSuperRampBuilder.scriptGainJitter(gainJitter: Double): OscSuperRampBuilder =
    this.gainJitter(gainJitter = gainJitter)

/** Fraction of `gainJitter` the on-pitch center voice gets: 0 = stable center, 1 = jittered like the sides. */
@KlangScript.Function(name = "centerJitter")
internal fun OscSuperRampBuilder.scriptCenterJitter(centerJitter: Double): OscSuperRampBuilder =
    this.centerJitter(centerJitter = centerJitter)

/**
 * Configure the banded start-phase pool in ONE call. Every param is an optional plain
 * literal, so named-arg subsets work: `.phasePool()` (on, family defaults),
 * `.phasePool(kMin = 0.2)`, `.phasePool(refreshEvery = 0)` (all-named or all-positional,
 * KlangScript forbids mixing). Defaults mirror this family's engine constants.
 *
 * **Selection modes** (`selection`, value-colon form `"name[:width[:outliers]]"`):
 * - `"normal"` (default): normal-distribution serving over the pool's vocabulary, centered on
 *   the median (most typical) take. `width` sets the spread: `0` = always the median take,
 *   `0.1` = tight, `0.5` = default, `1` and above is near uniform (`"normal:1.5"` is random
 *   with a slight center edge). `outliers` (0..1, default 0) is the probability of serving an
 *   EXTREME take instead, the vocabulary's lowest- or highest-K entry (coin-flip side).
 *   `"normal:0.1:0.05"` = tight, 5% wild plucks.
 * - `"random"`: a uniformly random vocabulary entry each note (still band-accepted takes).
 * - `"roundrobin"` (opt-in): cycle the vocabulary; can gargle audibly.
 *
 * Unrecognized names or coefficients coerce to their defaults.
 *
 * @param on 1 = banded start-phase selection on, 0 = off (the engine default).
 * @param kMin accepted coherence band, lower edge (0 = cancelled, 1 = phase-aligned).
 * @param kMax accepted coherence band, upper edge. The band is also a timbre control.
 * @param drawTries candidate phase sets scored per draw (engine caps at 64).
 * @param poolSize vocabulary size per pool key (engine caps at 1024).
 * @param refreshEvery notes between fresh pool draws; 0 = frozen pool.
 * @param selection serving mode, `"name[:width[:outliers]]"`, see **Selection modes** above.
 * @param warmup entries seeded eagerly at pool creation (work-capped; 0 = fully lazy).
 */
@KlangScript.Function(name = "phasePool")
internal fun OscSuperRampBuilder.scriptPhasePool(
    on: Double = 1.0,
    kMin: Double = 0.30,
    kMax: Double = 0.55,
    drawTries: Double = 5.0,
    poolSize: Double = 256.0,
    refreshEvery: Double = 10.0,
    selection: String = "normal",
    warmup: Double = 16.0,
): OscSuperRampBuilder = this.phasePool(
    on = on,
    kMin = kMin,
    kMax = kMax,
    drawTries = drawTries,
    poolSize = poolSize,
    refreshEvery = refreshEvery,
    selection = selection,
    warmup = warmup,
)

// ── Pluck ─────────────────────────────────────────────────────────────────

/** Loop feedback per pass, 0.9 to 0.999 (default 0.996); higher rings longer. A coefficient, not a time. */
@KlangScript.Function(name = "feedback")
internal fun OscPluckBuilder.scriptFeedback(feedback: IgnitorDslLike): OscPluckBuilder =
    this.feedback(feedback = feedback.toIgnitorDsl())

/** Initial-burst brightness / pick hardness (default 0.5). 0 = mellow, 1 = bright. */
@KlangScript.Function(name = "brightness")
internal fun OscPluckBuilder.scriptBrightness(brightness: IgnitorDslLike): OscPluckBuilder =
    this.brightness(brightness = brightness.toIgnitorDsl())

/** Relative pick position along the string (default 0.5). 0 = bridge, 1 = nut. */
@KlangScript.Function(name = "pickPosition")
internal fun OscPluckBuilder.scriptPickPosition(pickPosition: IgnitorDslLike): OscPluckBuilder =
    this.pickPosition(pickPosition = pickPosition.toIgnitorDsl())

/** String stiffness (default 0.0). Higher = more inharmonic, bell-like. */
@KlangScript.Function(name = "stiffness")
internal fun OscPluckBuilder.scriptStiffness(stiffness: IgnitorDslLike): OscPluckBuilder =
    this.stiffness(stiffness = stiffness.toIgnitorDsl())

/** How analog the oscillator is, a unitless character scale: 0 ideal (perfectly stable), 1 to 8 usual, 10 strong; its tell is the pitch drift, about one cent of peak per unit. Latches at note-on. */
@KlangScript.Function(name = "analog")
internal fun OscPluckBuilder.scriptAnalog(character: IgnitorDslLike): OscPluckBuilder =
    this.analog(character = character.toIgnitorDsl())

// ── SuperPluck ─────────────────────────────────────────────────────────────────

/** Number of detuned voices in the stack (default 8). Accepts a number or an `Ignitor.*` graph (read once per block). */
@KlangScript.Function(name = "voices")
internal fun OscSuperPluckBuilder.scriptVoices(voices: IgnitorDslLike): OscSuperPluckBuilder =
    this.voices(voices = voices.toIgnitorDsl())

/** Unison frequency spread between the voices (default 0.2). Same knob as the pattern-level `.spread()`. */
@KlangScript.Function(name = "spread")
internal fun OscSuperPluckBuilder.scriptSpread(spread: IgnitorDslLike): OscSuperPluckBuilder =
    this.spread(spread = spread.toIgnitorDsl())

/** Loop feedback per pass, 0.9 to 0.999 (default 0.996); higher rings longer. A coefficient, not a time. */
@KlangScript.Function(name = "feedback")
internal fun OscSuperPluckBuilder.scriptFeedback(feedback: IgnitorDslLike): OscSuperPluckBuilder =
    this.feedback(feedback = feedback.toIgnitorDsl())

/** Initial-burst brightness / pick hardness (default 0.5). 0 = mellow, 1 = bright. */
@KlangScript.Function(name = "brightness")
internal fun OscSuperPluckBuilder.scriptBrightness(brightness: IgnitorDslLike): OscSuperPluckBuilder =
    this.brightness(brightness = brightness.toIgnitorDsl())

/** Relative pick position along the string (default 0.5). 0 = bridge, 1 = nut. */
@KlangScript.Function(name = "pickPosition")
internal fun OscSuperPluckBuilder.scriptPickPosition(pickPosition: IgnitorDslLike): OscSuperPluckBuilder =
    this.pickPosition(pickPosition = pickPosition.toIgnitorDsl())

/** String stiffness (default 0.0). Higher = more inharmonic, bell-like. */
@KlangScript.Function(name = "stiffness")
internal fun OscSuperPluckBuilder.scriptStiffness(stiffness: IgnitorDslLike): OscSuperPluckBuilder =
    this.stiffness(stiffness = stiffness.toIgnitorDsl())

/** How analog the oscillator is, a unitless character scale: 0 ideal (perfectly stable), 1 to 8 usual, 10 strong; its tell is the pitch drift, about one cent of peak per unit. Latches at note-on. */
@KlangScript.Function(name = "analog")
internal fun OscSuperPluckBuilder.scriptAnalog(character: IgnitorDslLike): OscSuperPluckBuilder =
    this.analog(character = character.toIgnitorDsl())

/** How much the voices drift against each other under `analog`, 0 to 1. `1` (default): every voice walks
 *  on its own lane, the organic unison of separate oscillators. `0`: one shared walk, the stack wobbles as
 *  a single oscillator and its unison detune stays static. Between is a blend. Nothing happens while
 *  `analog` is 0. Same knob as on `Ignitor.sine`; named apart from `spread`, which is the static unison detune. */
@KlangScript.Function(name = "analogSpread")
internal fun OscSuperPluckBuilder.scriptAnalogSpread(amount: IgnitorDslLike): OscSuperPluckBuilder =
    this.analogSpread(amount = amount.toIgnitorDsl())
