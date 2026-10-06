/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:KlangScript.Library(KlangScriptLibraries.STDLIB)

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/*
 * The oscillator BUILDERS: what an `Ignitor.*` door hands to its `configure` lambda.
 *
 *     Ignitor.supersaw(x => x.voices(9).spread(0.1).phasePool()).lowpass(800)
 *     //           ^ x: OscSuperSawBuilder                    ^ back on the plain sound
 *
 * One builder per oscillator type, each an immutable value wrapper around its node: every knob
 * returns a NEW builder (`/dsl-design` section 1), the knobs on a builder are exactly that
 * oscillator's fields with a default (section 2, "anything with a default is a knob"), and the
 * base wrappers (`.lowpass()`, `.adsr()`, `.mul()`, ...) do not exist here, so the editor offers
 * inside the lambda only what belongs to the oscillator. The same builders are the Kotlin door:
 * `KlangScriptIgnitor.supersaw { it.voices(9).spread(0.1) }`.
 *
 * Knobs are top-level extension functions registered by KSP from `@KlangScript.Function` (the
 * shape sprudel uses for `SprudelPattern`), so each knob has ONE implementation and ONE KDoc.
 * Doors (`Ignitor.sine(freq, configure)`) live in [KlangScriptIgnitor]; the lambda plumbing and the
 * error contract in `configuredBy`.
 */

// ── Sine ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.Sine], handed to the `configure` lambda of `Ignitor.sine(...)`.
 * Knobs: `analog`, `phase`, and the partial banks `harmonics`, `octaves`, `suboctaves` with `fundamental` and
 * `analogSpread` (`docs/plans/sine-partial-banks.md`). Immutable: every knob returns a new builder.
 * `node` is the configured oscillator.
 */
data class OscSineBuilder(val node: IgnitorDsl.Sine)

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
@KlangScript.Function
fun OscSineBuilder.phase(phase: IgnitorDslLike): OscSineBuilder = copy(node = node.copy(phase = phase.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. With partial banks, the depth of every partial. */
@KlangScript.Function
fun OscSineBuilder.analog(analog: IgnitorDslLike): OscSineBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

/**
 * Gain of the sine's own partial (default 1). `0` leaves only the partial banks, so
 * `harmonics(7).fundamental(0)` puts the overtones on their own fader next to a separate sub. A signal
 * like every knob (an `Ignitor.param`, an LFO), read once per block.
 *
 * ```KlangScript
 * Ignitor.sine(x => x.harmonics(7).fundamental(0))    // overtones only, 2f .. 8f
 * ```
 */
@KlangScript.Function
fun OscSineBuilder.fundamental(gain: IgnitorDslLike): OscSineBuilder = copy(node = node.copy(fundamental = gain.toIgnitorDsl()))

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
@KlangScript.Function
fun OscSineBuilder.harmonics(count: IgnitorDslLike, rolloff: IgnitorDslLike = 1.0): OscSineBuilder =
    copy(node = node.copy(harmonics = count.toIgnitorDsl(), harmonicsRolloff = rolloff.toIgnitorDsl()))

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
@KlangScript.Function
fun OscSineBuilder.octaves(count: IgnitorDslLike, rolloff: IgnitorDslLike = 1.0): OscSineBuilder =
    copy(node = node.copy(octaves = count.toIgnitorDsl(), octavesRolloff = rolloff.toIgnitorDsl()))

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
@KlangScript.Function
fun OscSineBuilder.suboctaves(count: IgnitorDslLike, rolloff: IgnitorDslLike = 1.0): OscSineBuilder =
    copy(node = node.copy(suboctaves = count.toIgnitorDsl(), suboctavesRolloff = rolloff.toIgnitorDsl()))

/**
 * How much the partials drift against each other under `analog`, 0 to 1. `1` (default): every partial walks on
 * its own lane, the slow beating of a hand-stacked set of sines. `0`: one shared walk, the bank wobbles as a
 * single physical oscillator and its spectrum stays exactly harmonic. Between is a blend. Nothing happens while
 * `analog` is 0. Named apart from the supersaw's `spread`, which is static unison detune.
 *
 * ```KlangScript
 * Ignitor.sine(x => x.harmonics(7).analog(3).analogSpread(0))   // one drifting oscillator with seven harmonics
 * ```
 */
@KlangScript.Function
fun OscSineBuilder.analogSpread(amount: IgnitorDslLike): OscSineBuilder = copy(node = node.copy(analogSpread = amount.toIgnitorDsl()))

// ── Tri ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.Tri], handed to the `configure` lambda of `Ignitor.tri(...)`.
 * Knobs: `analog`, `phase`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscTriBuilder(val node: IgnitorDsl.Tri)

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
@KlangScript.Function
fun OscTriBuilder.phase(phase: IgnitorDslLike): OscTriBuilder = copy(node = node.copy(phase = phase.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscTriBuilder.analog(analog: IgnitorDslLike): OscTriBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

// ── Zawtooth ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.Zawtooth], handed to the `configure` lambda of `Ignitor.zawtooth(...)`.
 * Knobs: `analog`, `phase`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscZawtoothBuilder(val node: IgnitorDsl.Zawtooth)

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
@KlangScript.Function
fun OscZawtoothBuilder.phase(phase: IgnitorDslLike): OscZawtoothBuilder = copy(node = node.copy(phase = phase.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscZawtoothBuilder.analog(analog: IgnitorDslLike): OscZawtoothBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

// ── Zamp ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.Zamp], handed to the `configure` lambda of `Ignitor.zamp(...)`.
 * Knobs: `analog`, `phase`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscZampBuilder(val node: IgnitorDsl.Zamp)

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
@KlangScript.Function
fun OscZampBuilder.phase(phase: IgnitorDslLike): OscZampBuilder = copy(node = node.copy(phase = phase.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscZampBuilder.analog(analog: IgnitorDslLike): OscZampBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

// ── Impulse ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.Impulse], handed to the `configure` lambda of `Ignitor.impulse(...)`.
 * Knobs: `analog`, `phase`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscImpulseBuilder(val node: IgnitorDsl.Impulse)

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
@KlangScript.Function
fun OscImpulseBuilder.phase(phase: IgnitorDslLike): OscImpulseBuilder = copy(node = node.copy(phase = phase.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscImpulseBuilder.analog(analog: IgnitorDslLike): OscImpulseBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

// ── RawPulze ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.RawPulze], handed to the `configure` lambda of `Ignitor.pulze(...)`.
 * Knobs: `duty`, `analog`, `phase`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscPulzeBuilder(val node: IgnitorDsl.RawPulze)

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
@KlangScript.Function
fun OscPulzeBuilder.phase(phase: IgnitorDslLike): OscPulzeBuilder = copy(node = node.copy(phase = phase.toIgnitorDsl()))

/** Pulse width / duty cycle (0..1, default 0.5 = square). Accepts an `Ignitor.*` graph for PWM. */
@KlangScript.Function
fun OscPulzeBuilder.duty(duty: IgnitorDslLike): OscPulzeBuilder = copy(node = node.copy(duty = duty.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscPulzeBuilder.analog(analog: IgnitorDslLike): OscPulzeBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

// ── Pulze ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.Pulze], handed to the `configure` lambda of `Ignitor.square(...)`.
 * Knobs: `duty`, `analog`, `flankSamples`, `riseFlank`, `fallFlank`, `phase`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscSquareBuilder(val node: IgnitorDsl.Pulze)

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
@KlangScript.Function
fun OscSquareBuilder.phase(phase: IgnitorDslLike): OscSquareBuilder = copy(node = node.copy(phase = phase.toIgnitorDsl()))

/** Pulse width / duty cycle (0..1, default 0.5 = square). Accepts an `Ignitor.*` graph for PWM. */
@KlangScript.Function
fun OscSquareBuilder.duty(duty: IgnitorDslLike): OscSquareBuilder = copy(node = node.copy(duty = duty.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscSquareBuilder.analog(analog: IgnitorDslLike): OscSquareBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

/** Minimum flank length in samples, a floor on every edge that softens with pitch (default 2.0). */
@KlangScript.Function
fun OscSquareBuilder.flankSamples(flankSamples: Double): OscSquareBuilder = copy(node = node.copy(flankSamples = flankSamples))

/** Rising-edge flank fraction of the plateau: 0 = sharpest (the floor), 1 = full ramp (default 0.0). */
@KlangScript.Function
fun OscSquareBuilder.riseFlank(riseFlank: Double): OscSquareBuilder = copy(node = node.copy(riseFlank = riseFlank))

/** Falling-edge flank fraction of the plateau: 0 = sharpest (the floor), 1 = full ramp (default 0.0). */
@KlangScript.Function
fun OscSquareBuilder.fallFlank(fallFlank: Double): OscSquareBuilder = copy(node = node.copy(fallFlank = fallFlank))

// ── Sawtooth ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.Sawtooth], handed to the `configure` lambda of `Ignitor.saw(...)`.
 * Knobs: `analog`, `resetSamples`, `shapeMax`, `phase`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscSawBuilder(val node: IgnitorDsl.Sawtooth)

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
@KlangScript.Function
fun OscSawBuilder.phase(phase: IgnitorDslLike): OscSawBuilder = copy(node = node.copy(phase = phase.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscSawBuilder.analog(analog: IgnitorDslLike): OscSawBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

/** Analog flyback time in samples: lower = brighter, sharper reset; higher = softer (default 1.0). */
@KlangScript.Function
fun OscSawBuilder.resetSamples(resetSamples: Double): OscSawBuilder = copy(node = node.copy(resetSamples = resetSamples))

/** Max flyback fraction of a cycle: 0.5 = symmetric-triangle limit; keeps high notes sane (default 0.5). */
@KlangScript.Function
fun OscSawBuilder.shapeMax(shapeMax: Double): OscSawBuilder = copy(node = node.copy(shapeMax = shapeMax))

// ── Ramp ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.Ramp], handed to the `configure` lambda of `Ignitor.ramp(...)`.
 * Knobs: `analog`, `resetSamples`, `shapeMax`, `phase`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscRampBuilder(val node: IgnitorDsl.Ramp)

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
@KlangScript.Function
fun OscRampBuilder.phase(phase: IgnitorDslLike): OscRampBuilder = copy(node = node.copy(phase = phase.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscRampBuilder.analog(analog: IgnitorDslLike): OscRampBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

/** Analog flyback time in samples: lower = brighter, sharper reset; higher = softer (default 1.0). */
@KlangScript.Function
fun OscRampBuilder.resetSamples(resetSamples: Double): OscRampBuilder = copy(node = node.copy(resetSamples = resetSamples))

/** Max flyback fraction of a cycle: 0.5 = symmetric-triangle limit; keeps high notes sane (default 0.5). */
@KlangScript.Function
fun OscRampBuilder.shapeMax(shapeMax: Double): OscRampBuilder = copy(node = node.copy(shapeMax = shapeMax))

// ── SuperSaw ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.SuperSaw], handed to the `configure` lambda of `Ignitor.supersaw(...)`.
 * Knobs: `voices`, `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`, `phase`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscSuperSawBuilder(val node: IgnitorDsl.SuperSaw)

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
@KlangScript.Function
fun OscSuperSawBuilder.phase(phase: IgnitorDslLike): OscSuperSawBuilder = copy(node = node.copy(phase = phase.toIgnitorDsl()))

/** Number of detuned voices in the stack (default 8). Accepts a number or an `Ignitor.*` graph (read once per block). */
@KlangScript.Function
fun OscSuperSawBuilder.voices(voices: IgnitorDslLike): OscSuperSawBuilder = copy(node = node.copy(voices = voices.toIgnitorDsl()))

/** Unison frequency spread between the voices (default 0.2). Same knob as the pattern-level `.spread()`. */
@KlangScript.Function
fun OscSuperSawBuilder.spread(spread: IgnitorDslLike): OscSuperSawBuilder = copy(node = node.copy(spread = spread.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscSuperSawBuilder.analog(analog: IgnitorDslLike): OscSuperSawBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

/** How much the voices drift against each other under `analog`, 0 to 1. `1` (default): every voice walks
 *  on its own lane, the organic unison of separate oscillators. `0`: one shared walk, the stack wobbles as
 *  a single oscillator and its unison detune stays static. Between is a blend. Nothing happens while
 *  `analog` is 0. Same knob as on `Ignitor.sine`; named apart from `spread`, which is the static unison detune. */
@KlangScript.Function
fun OscSuperSawBuilder.analogSpread(amount: IgnitorDslLike): OscSuperSawBuilder =
    copy(node = node.copy(analogSpread = amount.toIgnitorDsl()))

/** Detune spacing shape: 1 = even, above 1 concentrates toward the center, below 1 spreads outward. */
@KlangScript.Function
fun OscSuperSawBuilder.spreadPower(spreadPower: Double): OscSuperSawBuilder = copy(node = node.copy(spreadPower = spreadPower))

/** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
@KlangScript.Function
fun OscSuperSawBuilder.sideAtten(sideAtten: Double): OscSuperSawBuilder = copy(node = node.copy(sideAtten = sideAtten))

/** Per-voice random amplitude offset (a fraction, plus or minus); 0 = off. */
@KlangScript.Function
fun OscSuperSawBuilder.gainJitter(gainJitter: Double): OscSuperSawBuilder = copy(node = node.copy(gainJitter = gainJitter))

/** Fraction of `gainJitter` the on-pitch center voice gets: 0 = stable center, 1 = jittered like the sides. */
@KlangScript.Function
fun OscSuperSawBuilder.centerJitter(centerJitter: Double): OscSuperSawBuilder = copy(node = node.copy(centerJitterScale = centerJitter))

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
@KlangScript.Function
fun OscSuperSawBuilder.phasePool(
    on: Double = 1.0,
    kMin: Double = 0.30,
    kMax: Double = 0.55,
    drawTries: Double = 5.0,
    poolSize: Double = 256.0,
    refreshEvery: Double = 10.0,
    selection: String = "normal",
    warmup: Double = 16.0,
): OscSuperSawBuilder = copy(
    node = node.copy(
        phasePool = on, kMin = kMin, kMax = kMax, drawTries = drawTries,
        poolSize = poolSize, refreshEvery = refreshEvery, selection = selection, warmup = warmup,
    ),
)

// ── SuperSine ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.SuperSine], handed to the `configure` lambda of `Ignitor.supersine(...)`.
 * Knobs: `voices`, `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`, `phase`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscSuperSineBuilder(val node: IgnitorDsl.SuperSine)

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
@KlangScript.Function
fun OscSuperSineBuilder.phase(phase: IgnitorDslLike): OscSuperSineBuilder = copy(node = node.copy(phase = phase.toIgnitorDsl()))

/** Number of detuned voices in the stack (default 8). Accepts a number or an `Ignitor.*` graph (read once per block). */
@KlangScript.Function
fun OscSuperSineBuilder.voices(voices: IgnitorDslLike): OscSuperSineBuilder = copy(node = node.copy(voices = voices.toIgnitorDsl()))

/** Unison frequency spread between the voices (default 0.2). Same knob as the pattern-level `.spread()`. */
@KlangScript.Function
fun OscSuperSineBuilder.spread(spread: IgnitorDslLike): OscSuperSineBuilder = copy(node = node.copy(spread = spread.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscSuperSineBuilder.analog(analog: IgnitorDslLike): OscSuperSineBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

/** How much the voices drift against each other under `analog`, 0 to 1. `1` (default): every voice walks
 *  on its own lane, the organic unison of separate oscillators. `0`: one shared walk, the stack wobbles as
 *  a single oscillator and its unison detune stays static. Between is a blend. Nothing happens while
 *  `analog` is 0. Same knob as on `Ignitor.sine`; named apart from `spread`, which is the static unison detune. */
@KlangScript.Function
fun OscSuperSineBuilder.analogSpread(amount: IgnitorDslLike): OscSuperSineBuilder =
    copy(node = node.copy(analogSpread = amount.toIgnitorDsl()))

/** Detune spacing shape: 1 = even, above 1 concentrates toward the center, below 1 spreads outward. */
@KlangScript.Function
fun OscSuperSineBuilder.spreadPower(spreadPower: Double): OscSuperSineBuilder = copy(node = node.copy(spreadPower = spreadPower))

/** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
@KlangScript.Function
fun OscSuperSineBuilder.sideAtten(sideAtten: Double): OscSuperSineBuilder = copy(node = node.copy(sideAtten = sideAtten))

/** Per-voice random amplitude offset (a fraction, plus or minus); 0 = off. */
@KlangScript.Function
fun OscSuperSineBuilder.gainJitter(gainJitter: Double): OscSuperSineBuilder = copy(node = node.copy(gainJitter = gainJitter))

/** Fraction of `gainJitter` the on-pitch center voice gets: 0 = stable center, 1 = jittered like the sides. */
@KlangScript.Function
fun OscSuperSineBuilder.centerJitter(centerJitter: Double): OscSuperSineBuilder = copy(node = node.copy(centerJitterScale = centerJitter))

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
@KlangScript.Function
fun OscSuperSineBuilder.phasePool(
    on: Double = 1.0,
    kMin: Double = 0.50,
    kMax: Double = 0.80,
    drawTries: Double = 40.0,
    poolSize: Double = 256.0,
    refreshEvery: Double = 10.0,
    selection: String = "normal",
    warmup: Double = 16.0,
): OscSuperSineBuilder = copy(
    node = node.copy(
        phasePool = on, kMin = kMin, kMax = kMax, drawTries = drawTries,
        poolSize = poolSize, refreshEvery = refreshEvery, selection = selection, warmup = warmup,
    ),
)

// ── SuperSquare ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.SuperSquare], handed to the `configure` lambda of `Ignitor.supersquare(...)`.
 * Knobs: `voices`, `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`, `phase`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscSuperSquareBuilder(val node: IgnitorDsl.SuperSquare)

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
@KlangScript.Function
fun OscSuperSquareBuilder.phase(phase: IgnitorDslLike): OscSuperSquareBuilder = copy(node = node.copy(phase = phase.toIgnitorDsl()))

/** Number of detuned voices in the stack (default 8). Accepts a number or an `Ignitor.*` graph (read once per block). */
@KlangScript.Function
fun OscSuperSquareBuilder.voices(voices: IgnitorDslLike): OscSuperSquareBuilder = copy(node = node.copy(voices = voices.toIgnitorDsl()))

/** Unison frequency spread between the voices (default 0.2). Same knob as the pattern-level `.spread()`. */
@KlangScript.Function
fun OscSuperSquareBuilder.spread(spread: IgnitorDslLike): OscSuperSquareBuilder = copy(node = node.copy(spread = spread.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscSuperSquareBuilder.analog(analog: IgnitorDslLike): OscSuperSquareBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

/** How much the voices drift against each other under `analog`, 0 to 1. `1` (default): every voice walks
 *  on its own lane, the organic unison of separate oscillators. `0`: one shared walk, the stack wobbles as
 *  a single oscillator and its unison detune stays static. Between is a blend. Nothing happens while
 *  `analog` is 0. Same knob as on `Ignitor.sine`; named apart from `spread`, which is the static unison detune. */
@KlangScript.Function
fun OscSuperSquareBuilder.analogSpread(amount: IgnitorDslLike): OscSuperSquareBuilder =
    copy(node = node.copy(analogSpread = amount.toIgnitorDsl()))

/** Detune spacing shape: 1 = even, above 1 concentrates toward the center, below 1 spreads outward. */
@KlangScript.Function
fun OscSuperSquareBuilder.spreadPower(spreadPower: Double): OscSuperSquareBuilder = copy(node = node.copy(spreadPower = spreadPower))

/** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
@KlangScript.Function
fun OscSuperSquareBuilder.sideAtten(sideAtten: Double): OscSuperSquareBuilder = copy(node = node.copy(sideAtten = sideAtten))

/** Per-voice random amplitude offset (a fraction, plus or minus); 0 = off. */
@KlangScript.Function
fun OscSuperSquareBuilder.gainJitter(gainJitter: Double): OscSuperSquareBuilder = copy(node = node.copy(gainJitter = gainJitter))

/** Fraction of `gainJitter` the on-pitch center voice gets: 0 = stable center, 1 = jittered like the sides. */
@KlangScript.Function
fun OscSuperSquareBuilder.centerJitter(centerJitter: Double): OscSuperSquareBuilder = copy(node = node.copy(centerJitterScale = centerJitter))

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
@KlangScript.Function
fun OscSuperSquareBuilder.phasePool(
    on: Double = 1.0,
    kMin: Double = 0.30,
    kMax: Double = 0.55,
    drawTries: Double = 5.0,
    poolSize: Double = 256.0,
    refreshEvery: Double = 10.0,
    selection: String = "normal",
    warmup: Double = 16.0,
): OscSuperSquareBuilder = copy(
    node = node.copy(
        phasePool = on, kMin = kMin, kMax = kMax, drawTries = drawTries,
        poolSize = poolSize, refreshEvery = refreshEvery, selection = selection, warmup = warmup,
    ),
)

// ── SuperTri ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.SuperTri], handed to the `configure` lambda of `Ignitor.supertri(...)`.
 * Knobs: `voices`, `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`, `phase`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscSuperTriBuilder(val node: IgnitorDsl.SuperTri)

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
@KlangScript.Function
fun OscSuperTriBuilder.phase(phase: IgnitorDslLike): OscSuperTriBuilder = copy(node = node.copy(phase = phase.toIgnitorDsl()))

/** Number of detuned voices in the stack (default 8). Accepts a number or an `Ignitor.*` graph (read once per block). */
@KlangScript.Function
fun OscSuperTriBuilder.voices(voices: IgnitorDslLike): OscSuperTriBuilder = copy(node = node.copy(voices = voices.toIgnitorDsl()))

/** Unison frequency spread between the voices (default 0.2). Same knob as the pattern-level `.spread()`. */
@KlangScript.Function
fun OscSuperTriBuilder.spread(spread: IgnitorDslLike): OscSuperTriBuilder = copy(node = node.copy(spread = spread.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscSuperTriBuilder.analog(analog: IgnitorDslLike): OscSuperTriBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

/** How much the voices drift against each other under `analog`, 0 to 1. `1` (default): every voice walks
 *  on its own lane, the organic unison of separate oscillators. `0`: one shared walk, the stack wobbles as
 *  a single oscillator and its unison detune stays static. Between is a blend. Nothing happens while
 *  `analog` is 0. Same knob as on `Ignitor.sine`; named apart from `spread`, which is the static unison detune. */
@KlangScript.Function
fun OscSuperTriBuilder.analogSpread(amount: IgnitorDslLike): OscSuperTriBuilder =
    copy(node = node.copy(analogSpread = amount.toIgnitorDsl()))

/** Detune spacing shape: 1 = even, above 1 concentrates toward the center, below 1 spreads outward. */
@KlangScript.Function
fun OscSuperTriBuilder.spreadPower(spreadPower: Double): OscSuperTriBuilder = copy(node = node.copy(spreadPower = spreadPower))

/** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
@KlangScript.Function
fun OscSuperTriBuilder.sideAtten(sideAtten: Double): OscSuperTriBuilder = copy(node = node.copy(sideAtten = sideAtten))

/** Per-voice random amplitude offset (a fraction, plus or minus); 0 = off. */
@KlangScript.Function
fun OscSuperTriBuilder.gainJitter(gainJitter: Double): OscSuperTriBuilder = copy(node = node.copy(gainJitter = gainJitter))

/** Fraction of `gainJitter` the on-pitch center voice gets: 0 = stable center, 1 = jittered like the sides. */
@KlangScript.Function
fun OscSuperTriBuilder.centerJitter(centerJitter: Double): OscSuperTriBuilder = copy(node = node.copy(centerJitterScale = centerJitter))

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
@KlangScript.Function
fun OscSuperTriBuilder.phasePool(
    on: Double = 1.0,
    kMin: Double = 0.40,
    kMax: Double = 0.65,
    drawTries: Double = 16.0,
    poolSize: Double = 256.0,
    refreshEvery: Double = 10.0,
    selection: String = "normal",
    warmup: Double = 16.0,
): OscSuperTriBuilder = copy(
    node = node.copy(
        phasePool = on, kMin = kMin, kMax = kMax, drawTries = drawTries,
        poolSize = poolSize, refreshEvery = refreshEvery, selection = selection, warmup = warmup,
    ),
)

// ── SuperRamp ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.SuperRamp], handed to the `configure` lambda of `Ignitor.superramp(...)`.
 * Knobs: `voices`, `spread`, `analog`, `analogSpread`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`, `phase`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscSuperRampBuilder(val node: IgnitorDsl.SuperRamp)

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
@KlangScript.Function
fun OscSuperRampBuilder.phase(phase: IgnitorDslLike): OscSuperRampBuilder = copy(node = node.copy(phase = phase.toIgnitorDsl()))

/** Number of detuned voices in the stack (default 8). Accepts a number or an `Ignitor.*` graph (read once per block). */
@KlangScript.Function
fun OscSuperRampBuilder.voices(voices: IgnitorDslLike): OscSuperRampBuilder = copy(node = node.copy(voices = voices.toIgnitorDsl()))

/** Unison frequency spread between the voices (default 0.2). Same knob as the pattern-level `.spread()`. */
@KlangScript.Function
fun OscSuperRampBuilder.spread(spread: IgnitorDslLike): OscSuperRampBuilder = copy(node = node.copy(spread = spread.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscSuperRampBuilder.analog(analog: IgnitorDslLike): OscSuperRampBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

/** How much the voices drift against each other under `analog`, 0 to 1. `1` (default): every voice walks
 *  on its own lane, the organic unison of separate oscillators. `0`: one shared walk, the stack wobbles as
 *  a single oscillator and its unison detune stays static. Between is a blend. Nothing happens while
 *  `analog` is 0. Same knob as on `Ignitor.sine`; named apart from `spread`, which is the static unison detune. */
@KlangScript.Function
fun OscSuperRampBuilder.analogSpread(amount: IgnitorDslLike): OscSuperRampBuilder =
    copy(node = node.copy(analogSpread = amount.toIgnitorDsl()))

/** Detune spacing shape: 1 = even, above 1 concentrates toward the center, below 1 spreads outward. */
@KlangScript.Function
fun OscSuperRampBuilder.spreadPower(spreadPower: Double): OscSuperRampBuilder = copy(node = node.copy(spreadPower = spreadPower))

/** Center-dominant gain falloff: 0 = all voices equal, 1 = only the center voice. */
@KlangScript.Function
fun OscSuperRampBuilder.sideAtten(sideAtten: Double): OscSuperRampBuilder = copy(node = node.copy(sideAtten = sideAtten))

/** Per-voice random amplitude offset (a fraction, plus or minus); 0 = off. */
@KlangScript.Function
fun OscSuperRampBuilder.gainJitter(gainJitter: Double): OscSuperRampBuilder = copy(node = node.copy(gainJitter = gainJitter))

/** Fraction of `gainJitter` the on-pitch center voice gets: 0 = stable center, 1 = jittered like the sides. */
@KlangScript.Function
fun OscSuperRampBuilder.centerJitter(centerJitter: Double): OscSuperRampBuilder = copy(node = node.copy(centerJitterScale = centerJitter))

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
@KlangScript.Function
fun OscSuperRampBuilder.phasePool(
    on: Double = 1.0,
    kMin: Double = 0.30,
    kMax: Double = 0.55,
    drawTries: Double = 5.0,
    poolSize: Double = 256.0,
    refreshEvery: Double = 10.0,
    selection: String = "normal",
    warmup: Double = 16.0,
): OscSuperRampBuilder = copy(
    node = node.copy(
        phasePool = on, kMin = kMin, kMax = kMax, drawTries = drawTries,
        poolSize = poolSize, refreshEvery = refreshEvery, selection = selection, warmup = warmup,
    ),
)

// ── Pluck ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.Pluck], handed to the `configure` lambda of `Ignitor.pluck(...)`.
 * Knobs: `decay`, `brightness`, `pickPosition`, `stiffness`, `analog`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscPluckBuilder(val node: IgnitorDsl.Pluck)

/** Loop decay per pass (default 0.996). Higher = longer sustain (0..1). */
@KlangScript.Function
fun OscPluckBuilder.decay(decay: IgnitorDslLike): OscPluckBuilder = copy(node = node.copy(decay = decay.toIgnitorDsl()))

/** Initial-burst brightness / pick hardness (default 0.5). 0 = mellow, 1 = bright. */
@KlangScript.Function
fun OscPluckBuilder.brightness(brightness: IgnitorDslLike): OscPluckBuilder = copy(node = node.copy(brightness = brightness.toIgnitorDsl()))

/** Relative pick position along the string (default 0.5). 0 = bridge, 1 = nut. */
@KlangScript.Function
fun OscPluckBuilder.pickPosition(pickPosition: IgnitorDslLike): OscPluckBuilder = copy(node = node.copy(pickPosition = pickPosition.toIgnitorDsl()))

/** String stiffness (default 0.0). Higher = more inharmonic, bell-like. */
@KlangScript.Function
fun OscPluckBuilder.stiffness(stiffness: IgnitorDslLike): OscPluckBuilder = copy(node = node.copy(stiffness = stiffness.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscPluckBuilder.analog(analog: IgnitorDslLike): OscPluckBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

// ── SuperPluck ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.SuperPluck], handed to the `configure` lambda of `Ignitor.superpluck(...)`.
 * Knobs: `voices`, `spread`, `decay`, `brightness`, `pickPosition`, `stiffness`, `analog`, `analogSpread`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscSuperPluckBuilder(val node: IgnitorDsl.SuperPluck)

/** Number of detuned voices in the stack (default 8). Accepts a number or an `Ignitor.*` graph (read once per block). */
@KlangScript.Function
fun OscSuperPluckBuilder.voices(voices: IgnitorDslLike): OscSuperPluckBuilder = copy(node = node.copy(voices = voices.toIgnitorDsl()))

/** Unison frequency spread between the voices (default 0.2). Same knob as the pattern-level `.spread()`. */
@KlangScript.Function
fun OscSuperPluckBuilder.spread(spread: IgnitorDslLike): OscSuperPluckBuilder = copy(node = node.copy(spread = spread.toIgnitorDsl()))

/** Loop decay per pass (default 0.996). Higher = longer sustain (0..1). */
@KlangScript.Function
fun OscSuperPluckBuilder.decay(decay: IgnitorDslLike): OscSuperPluckBuilder = copy(node = node.copy(decay = decay.toIgnitorDsl()))

/** Initial-burst brightness / pick hardness (default 0.5). 0 = mellow, 1 = bright. */
@KlangScript.Function
fun OscSuperPluckBuilder.brightness(brightness: IgnitorDslLike): OscSuperPluckBuilder = copy(node = node.copy(brightness = brightness.toIgnitorDsl()))

/** Relative pick position along the string (default 0.5). 0 = bridge, 1 = nut. */
@KlangScript.Function
fun OscSuperPluckBuilder.pickPosition(pickPosition: IgnitorDslLike): OscSuperPluckBuilder = copy(node = node.copy(pickPosition = pickPosition.toIgnitorDsl()))

/** String stiffness (default 0.0). Higher = more inharmonic, bell-like. */
@KlangScript.Function
fun OscSuperPluckBuilder.stiffness(stiffness: IgnitorDslLike): OscSuperPluckBuilder = copy(node = node.copy(stiffness = stiffness.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscSuperPluckBuilder.analog(analog: IgnitorDslLike): OscSuperPluckBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

/** How much the voices drift against each other under `analog`, 0 to 1. `1` (default): every voice walks
 *  on its own lane, the organic unison of separate oscillators. `0`: one shared walk, the stack wobbles as
 *  a single oscillator and its unison detune stays static. Between is a blend. Nothing happens while
 *  `analog` is 0. Same knob as on `Ignitor.sine`; named apart from `spread`, which is the static unison detune. */
@KlangScript.Function
fun OscSuperPluckBuilder.analogSpread(amount: IgnitorDslLike): OscSuperPluckBuilder =
    copy(node = node.copy(analogSpread = amount.toIgnitorDsl()))
