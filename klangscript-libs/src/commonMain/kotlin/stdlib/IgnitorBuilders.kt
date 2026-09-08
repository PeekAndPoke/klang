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
 * The oscillator BUILDERS: what an `Osc.*` door hands to its `configure` lambda.
 *
 *     Osc.supersaw(x => x.voices(9).spread(0.1).phasePool()).lowpass(800)
 *     //           ^ x: OscSuperSawBuilder                    ^ back on the plain sound
 *
 * One builder per oscillator type, each an immutable value wrapper around its node: every knob
 * returns a NEW builder (`/dsl-design` section 1), the knobs on a builder are exactly that
 * oscillator's fields with a default (section 2, "anything with a default is a knob"), and the
 * base wrappers (`.lowpass()`, `.adsr()`, `.mul()`, ...) do not exist here, so the editor offers
 * inside the lambda only what belongs to the oscillator. The same builders are the Kotlin door:
 * `KlangScriptOsc.supersaw { it.voices(9).spread(0.1) }`.
 *
 * Knobs are top-level extension functions registered by KSP from `@KlangScript.Function` (the
 * shape sprudel uses for `SprudelPattern`), so each knob has ONE implementation and ONE KDoc.
 * Doors (`Osc.sine(freq, configure)`) live in [KlangScriptOsc]; the lambda plumbing and the
 * error contract in `configuredBy`.
 */

// ── Sine ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.Sine], handed to the `configure` lambda of `Osc.sine(...)`.
 * Knobs: `analog`, and the partial banks `harmonics`, `octaves`, `suboctaves` with `fundamental` and
 * `analogSpread` (`docs/plans/sine-partial-banks.md`). Immutable: every knob returns a new builder.
 * `node` is the configured oscillator.
 */
data class OscSineBuilder(val node: IgnitorDsl.Sine)

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. With partial banks, the depth of every partial. */
@KlangScript.Function
fun OscSineBuilder.analog(analog: IgnitorDslLike): OscSineBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

/**
 * Gain of the sine's own partial (default 1). `0` leaves only the partial banks, so
 * `harmonics(7).fundamental(0)` puts the overtones on their own fader next to a separate sub. A signal
 * like every knob (an `Osc.param`, an LFO), read once per block.
 *
 * ```KlangScript
 * Osc.sine(x => x.harmonics(7).fundamental(0))    // overtones only, 2f .. 8f
 * ```
 */
@KlangScript.Function
fun OscSineBuilder.fundamental(gain: IgnitorDslLike): OscSineBuilder = copy(node = node.copy(fundamental = gain.toIgnitorDsl()))

/**
 * Adds `count` sine partials at `2f, 3f, 4f ...` of THIS sine's frequency, its harmonic series, rendered in
 * one pass with the sine. A partial at multiple `m` has gain `m ^ -rolloff`: `rolloff` 1 (default) is the
 * sawtooth law, 2 is triangle-soft, 0 is flat and buzzy. `count` 0 is off, at most 64. The multiples are of the sine, not of
 * the note: `Osc.sine(Osc.freq().mul(2), x => x.harmonics(3))` is `2f, 4f, 6f, 8f`, the even series. Partials
 * at or above Nyquist stay silent. Both arguments are signals read once per block, so
 * `harmonics(12, Osc.param("rolloff", 1))` puts brightness on the pattern; a moving `count` steps on every
 * removal, a moving `rolloff` does not. Banks sum: `harmonics(7)` plus
 * `octaves(3)` doubles the shared partials, as two written sines would.
 *
 * ```KlangScript
 * Osc.sine(x => x.harmonics(7))                   // a bass: f plus 2f .. 8f, the ear rebuilds the fundamental on small speakers
 * Osc.sine(x => x.harmonics(8, Osc.sine(0.2).range(0.7, 2)))   // breathing brightness
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
 * Osc.sine(Osc.freq().mul(2), x => x.octaves(5)).mul(1/2)   // 2f .. 64f at 1/2 .. 1/64, an octave stack over a saw
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
 * Osc.sine(x => x.suboctaves(1, 0))    // f and f/2 at equal level
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
 * Osc.sine(x => x.harmonics(7).analog(3).analogSpread(0))   // one drifting oscillator with seven harmonics
 * ```
 */
@KlangScript.Function
fun OscSineBuilder.analogSpread(amount: IgnitorDslLike): OscSineBuilder = copy(node = node.copy(analogSpread = amount.toIgnitorDsl()))

// ── Triangle ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.Triangle], handed to the `configure` lambda of `Osc.triangle(...)`.
 * Knobs: `analog`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscTriangleBuilder(val node: IgnitorDsl.Triangle)

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscTriangleBuilder.analog(analog: IgnitorDslLike): OscTriangleBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

// ── Zawtooth ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.Zawtooth], handed to the `configure` lambda of `Osc.zawtooth(...)`.
 * Knobs: `analog`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscZawtoothBuilder(val node: IgnitorDsl.Zawtooth)

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscZawtoothBuilder.analog(analog: IgnitorDslLike): OscZawtoothBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

// ── Zamp ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.Zamp], handed to the `configure` lambda of `Osc.zamp(...)`.
 * Knobs: `analog`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscZampBuilder(val node: IgnitorDsl.Zamp)

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscZampBuilder.analog(analog: IgnitorDslLike): OscZampBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

// ── Impulse ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.Impulse], handed to the `configure` lambda of `Osc.impulse(...)`.
 * Knobs: `analog`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscImpulseBuilder(val node: IgnitorDsl.Impulse)

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscImpulseBuilder.analog(analog: IgnitorDslLike): OscImpulseBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

// ── RawPulze ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.RawPulze], handed to the `configure` lambda of `Osc.pulze(...)`.
 * Knobs: `duty`, `analog`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscPulzeBuilder(val node: IgnitorDsl.RawPulze)

/** Pulse width / duty cycle (0..1, default 0.5 = square). Accepts an `Osc.*` graph for PWM. */
@KlangScript.Function
fun OscPulzeBuilder.duty(duty: IgnitorDslLike): OscPulzeBuilder = copy(node = node.copy(duty = duty.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscPulzeBuilder.analog(analog: IgnitorDslLike): OscPulzeBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

// ── Pulze ─────────────────────────────────────────────────────────────────

/**
 * Builder for [IgnitorDsl.Pulze], handed to the `configure` lambda of `Osc.square(...)`.
 * Knobs: `duty`, `analog`, `flankSamples`, `riseFlank`, `fallFlank`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscSquareBuilder(val node: IgnitorDsl.Pulze)

/** Pulse width / duty cycle (0..1, default 0.5 = square). Accepts an `Osc.*` graph for PWM. */
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
 * Builder for [IgnitorDsl.Sawtooth], handed to the `configure` lambda of `Osc.saw(...)`.
 * Knobs: `analog`, `resetSamples`, `shapeMax`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscSawBuilder(val node: IgnitorDsl.Sawtooth)

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
 * Builder for [IgnitorDsl.Ramp], handed to the `configure` lambda of `Osc.ramp(...)`.
 * Knobs: `analog`, `resetSamples`, `shapeMax`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscRampBuilder(val node: IgnitorDsl.Ramp)

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
 * Builder for [IgnitorDsl.SuperSaw], handed to the `configure` lambda of `Osc.supersaw(...)`.
 * Knobs: `voices`, `spread`, `analog`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscSuperSawBuilder(val node: IgnitorDsl.SuperSaw)

/** Number of detuned voices in the stack (default 8). Accepts a number or an `Osc.*` graph (read once per block). */
@KlangScript.Function
fun OscSuperSawBuilder.voices(voices: IgnitorDslLike): OscSuperSawBuilder = copy(node = node.copy(voices = voices.toIgnitorDsl()))

/** Unison frequency spread between the voices (default 0.2). Same knob as the pattern-level `.spread()`. */
@KlangScript.Function
fun OscSuperSawBuilder.spread(spread: IgnitorDslLike): OscSuperSawBuilder = copy(node = node.copy(spread = spread.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscSuperSawBuilder.analog(analog: IgnitorDslLike): OscSuperSawBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

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
 * Builder for [IgnitorDsl.SuperSine], handed to the `configure` lambda of `Osc.supersine(...)`.
 * Knobs: `voices`, `spread`, `analog`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscSuperSineBuilder(val node: IgnitorDsl.SuperSine)

/** Number of detuned voices in the stack (default 8). Accepts a number or an `Osc.*` graph (read once per block). */
@KlangScript.Function
fun OscSuperSineBuilder.voices(voices: IgnitorDslLike): OscSuperSineBuilder = copy(node = node.copy(voices = voices.toIgnitorDsl()))

/** Unison frequency spread between the voices (default 0.2). Same knob as the pattern-level `.spread()`. */
@KlangScript.Function
fun OscSuperSineBuilder.spread(spread: IgnitorDslLike): OscSuperSineBuilder = copy(node = node.copy(spread = spread.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscSuperSineBuilder.analog(analog: IgnitorDslLike): OscSuperSineBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

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
 * Builder for [IgnitorDsl.SuperSquare], handed to the `configure` lambda of `Osc.supersquare(...)`.
 * Knobs: `voices`, `spread`, `analog`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscSuperSquareBuilder(val node: IgnitorDsl.SuperSquare)

/** Number of detuned voices in the stack (default 8). Accepts a number or an `Osc.*` graph (read once per block). */
@KlangScript.Function
fun OscSuperSquareBuilder.voices(voices: IgnitorDslLike): OscSuperSquareBuilder = copy(node = node.copy(voices = voices.toIgnitorDsl()))

/** Unison frequency spread between the voices (default 0.2). Same knob as the pattern-level `.spread()`. */
@KlangScript.Function
fun OscSuperSquareBuilder.spread(spread: IgnitorDslLike): OscSuperSquareBuilder = copy(node = node.copy(spread = spread.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscSuperSquareBuilder.analog(analog: IgnitorDslLike): OscSuperSquareBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

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
 * Builder for [IgnitorDsl.SuperTri], handed to the `configure` lambda of `Osc.supertri(...)`.
 * Knobs: `voices`, `spread`, `analog`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscSuperTriBuilder(val node: IgnitorDsl.SuperTri)

/** Number of detuned voices in the stack (default 8). Accepts a number or an `Osc.*` graph (read once per block). */
@KlangScript.Function
fun OscSuperTriBuilder.voices(voices: IgnitorDslLike): OscSuperTriBuilder = copy(node = node.copy(voices = voices.toIgnitorDsl()))

/** Unison frequency spread between the voices (default 0.2). Same knob as the pattern-level `.spread()`. */
@KlangScript.Function
fun OscSuperTriBuilder.spread(spread: IgnitorDslLike): OscSuperTriBuilder = copy(node = node.copy(spread = spread.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscSuperTriBuilder.analog(analog: IgnitorDslLike): OscSuperTriBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

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
 * Builder for [IgnitorDsl.SuperRamp], handed to the `configure` lambda of `Osc.superramp(...)`.
 * Knobs: `voices`, `spread`, `analog`, `spreadPower`, `sideAtten`, `gainJitter`, `centerJitter`, `phasePool`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscSuperRampBuilder(val node: IgnitorDsl.SuperRamp)

/** Number of detuned voices in the stack (default 8). Accepts a number or an `Osc.*` graph (read once per block). */
@KlangScript.Function
fun OscSuperRampBuilder.voices(voices: IgnitorDslLike): OscSuperRampBuilder = copy(node = node.copy(voices = voices.toIgnitorDsl()))

/** Unison frequency spread between the voices (default 0.2). Same knob as the pattern-level `.spread()`. */
@KlangScript.Function
fun OscSuperRampBuilder.spread(spread: IgnitorDslLike): OscSuperRampBuilder = copy(node = node.copy(spread = spread.toIgnitorDsl()))

/** Analog drift amount (per-voice micro-pitch instability); 0 = perfectly stable. Latches at note-on. */
@KlangScript.Function
fun OscSuperRampBuilder.analog(analog: IgnitorDslLike): OscSuperRampBuilder = copy(node = node.copy(analog = analog.toIgnitorDsl()))

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
 * Builder for [IgnitorDsl.Pluck], handed to the `configure` lambda of `Osc.pluck(...)`.
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
 * Builder for [IgnitorDsl.SuperPluck], handed to the `configure` lambda of `Osc.superpluck(...)`.
 * Knobs: `voices`, `spread`, `decay`, `brightness`, `pickPosition`, `stiffness`, `analog`. Immutable: every knob returns a new builder. `node` is the configured
 * oscillator.
 */
data class OscSuperPluckBuilder(val node: IgnitorDsl.SuperPluck)

/** Number of detuned voices in the stack (default 8). Accepts a number or an `Osc.*` graph (read once per block). */
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
