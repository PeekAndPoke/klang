/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * `Ignitor.slot`: the canonical open parameter slots for sprudel-compatible custom sounds.
 *
 * Each slot is the same `IgnitorDsl.Param(name, default)` singleton that built-in
 * sounds use, exposed for custom sounds that want to opt in to sprudel
 * modulation (the `analog`, `voices`, `spread`, ... knobs on the oscillator builders).
 *
 * ```KlangScript(Executable)
 * let pad = Ignitor.sine(x => x.analog(Ignitor.slot.analog)).lowpass(2000).classic()
 * note("c").sound(pad)
 * ```
 *
 * Reachable only as `Ignitor.slot` (and `Ign.slot`): registered with `@TypeExtensions` on itself,
 * like the slot groups, so it adds no global name. The Kotlin door is `IgnitorDsl.Slots`.
 *
 * Whether a custom sound answers the pattern depends on its door: the oscillator doors keep the
 * node's open slots (`Ignitor.sine()` reads `analog` from the pattern, `Ignitor.supersaw()` also
 * `voices` and `spread`), while the noise doors seal their knobs to constants and the pluck doors
 * seal all but `analog`. Placing
 * `Ignitor.slot.<name>` on a knob opts it in: the slot reads `ignitorParams[name]` at voice-trigger
 * time and falls back to its default.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptIgnitorSlots::class)
object KlangScriptIgnitorSlots {
    override fun toString(): String = "[Ignitor.slot]"

    /** Open `analog` slot (default 0.0). Mirrors sprudel `.analog(x)`. */
    @KlangScript.Property
    val analog: IgnitorDsl = IgnitorDsl.Slots.analog

    /** Open `voices` slot (default 8.0). Used by super-oscillators. */
    @KlangScript.Property
    val voices: IgnitorDsl = IgnitorDsl.Slots.voices

    /** Open `spread` slot (default 0.2). Used by super-oscillators. */
    @KlangScript.Property
    val spread: IgnitorDsl = IgnitorDsl.Slots.spread

    /** Open `duty` slot (default 0.5). Used by pulze. */
    @KlangScript.Property
    val duty: IgnitorDsl = IgnitorDsl.Slots.duty

    /** Open `density` slot (default 0.2). Used by dust / crackle. */
    @KlangScript.Property
    val density: IgnitorDsl = IgnitorDsl.Slots.density

    /** Open `feedback` slot (default 0.996): the loop feedback per pass, 0.9 to 0.999. Used by pluck. */
    @KlangScript.Property
    val feedback: IgnitorDsl = IgnitorDsl.Slots.feedback

    /** Open `brightness` slot (default 0.5). Used by pluck. */
    @KlangScript.Property
    val brightness: IgnitorDsl = IgnitorDsl.Slots.brightness

    /** Open `pickPosition` slot (default 0.5). Used by pluck. */
    @KlangScript.Property
    val pickPosition: IgnitorDsl = IgnitorDsl.Slots.pickPosition

    /** Open `stiffness` slot (default 0.0). Used by pluck. */
    @KlangScript.Property
    val stiffness: IgnitorDsl = IgnitorDsl.Slots.stiffness

    /** Open `rate` slot (default 1.0). Used by perlin / berlin noise. */
    @KlangScript.Property
    val rate: IgnitorDsl = IgnitorDsl.Slots.rate

    /** Open `octaves` slot (default 1.0): the fBm octaves, 1 plain, the engine caps at 8. Used by perlin / berlin noise. */
    @KlangScript.Property
    val octaves: IgnitorDsl = IgnitorDsl.Slots.octaves

    /** Open `persistence` slot (default 0.5): the fBm amplitude falloff per octave, 0 to 1. Used by perlin / berlin noise. */
    @KlangScript.Property
    val persistence: IgnitorDsl = IgnitorDsl.Slots.persistence

    /** Open `color` slot (default 0.0): the spectral tilt, -1 to 1, 0 flat. Used by whitenoise; sprudel's `sndNoise(color)` writes it. */
    @KlangScript.Property
    val color: IgnitorDsl = IgnitorDsl.Slots.color

    /** Open `leak` slot (default 0.02): the per-sample white leak, lower is deeper. Used by brownnoise; sprudel's `sndBrown(leak)` writes it. */
    @KlangScript.Property
    val leak: IgnitorDsl = IgnitorDsl.Slots.leak

    /** Open `tail` slot (default 1.0): the amplitude exponent, above 1 mostly tiny pops and rare loud ones. Used by dust; sprudel's `sndDust` writes it. */
    @KlangScript.Property
    val tail: IgnitorDsl = IgnitorDsl.Slots.tail

    /** Open `bipolar` slot (default 0.0): above 0.5 every pop takes a random sign. Used by dust. */
    @KlangScript.Property
    val bipolar: IgnitorDsl = IgnitorDsl.Slots.bipolar

    /** Open `chaos` slot (default 1.5): the crackle map's drive, about 1 sparse to 2 dense. Used by crackle; sprudel's `sndCrackle(chaos)` writes it. */
    @KlangScript.Property
    val chaos: IgnitorDsl = IgnitorDsl.Slots.chaos

    /**
     * Open `declick` slot (default 0.0, off): the envelope's de-click one-pole, in seconds. Every authored
     * `adsr` reads it unless its builder sets `declick(...)`; no sprudel door writes it (`classic()`'s envelope
     * de-clicks with a constant instead).
     */
    @KlangScript.Property
    val declick: IgnitorDsl = IgnitorDsl.Slots.declick

    /**
     * Open `pregain` slot (default 1.0): how hard the pattern plays INTO the instrument, the
     * level at which the signal meets the instrument's first nonlinearity. Mirrors sprudel
     * `.pregain(x)`.
     *
     * An ordinary slot, so it does what the tree wires it to and nothing otherwise: an instrument
     * that never places it ignores `pregain(x)` bit for bit. `.pregain()` is the short spelling
     * of `.mul(Ignitor.slot.pregain)`, and the place to put it is in front of the nonlinearity it
     * should drive:
     *
     * ```
     * Ignitor.saw().pregain().distort(0.5)   // play harder, get dirtier
     * ```
     *
     * It changes TIMBRE only where something nonlinear follows it AND that nonlinearity still has
     * somewhere to go. With nothing nonlinear after it, it is just a level; on a CLIPPING shape
     * already in hard saturation it is neither, because a clipper holds the tone and the level
     * alike. The tone-neutral level word is `gain`, the channel fader after the whole instrument.
     *
     * The wavefolders (`fold`, `linearfold`, `sineshaper`) never saturate, so there this slot is
     * the fold depth and the strongest tone knob at any drive, and turning it down can make the
     * sound louder rather than quieter.
     */
    @KlangScript.Property
    val pregain: IgnitorDsl = IgnitorDsl.Slots.pregain

    // ── The slots of the classic tail, one group per stage (`Ignitor.slot.lpf.freq`) ──
    //
    // What `x.classic()` places, and what a tail of your own places when it wants the pattern's
    // voice doors to reach it. Named `<door>.<param>`: the sprudel door's name, then the engine door's
    // word (`lpf.freq`, `crush.bits`, `adsr.attack`); the Kotlin door is `IgnitorDsl.Slots.lpf.freq`, the
    // same object.

    /** The pitch envelope stage's slots: `semitones` (the switch), `attack`, `decay`, `sustain`, `release`. `classic()`'s first stage. */
    @KlangScript.Property
    val penv: KlangScriptIgnitorPenvSlots = KlangScriptIgnitorPenvSlots

    /** The pitch envelope's curve slots: `attack`, `decay`, `release`. */
    @KlangScript.Property
    val penvCurves: KlangScriptIgnitorPenvCurvesSlots = KlangScriptIgnitorPenvCurvesSlots

    /**
     * The one-pole lowpass stage's slot (default 0.0, off): the cutoff in Hz that the pattern's `onepole(hz)`
     * writes. `classic()`'s first amplitude stage, behind the pitch stages.
     */
    @KlangScript.Property
    val onepole: IgnitorDsl = IgnitorDsl.Slots.onepole

    /** The crush stage's slot: `Ignitor.slot.crush.bits`. */
    @KlangScript.Property
    val crush: KlangScriptIgnitorCrushSlots = KlangScriptIgnitorCrushSlots

    /** The coarse stage's slot: `Ignitor.slot.coarse.factor`. */
    @KlangScript.Property
    val coarse: KlangScriptIgnitorCoarseSlots = KlangScriptIgnitorCoarseSlots

    /** The distort stage's slots: `amount`, `shape`, `oversample`. */
    @KlangScript.Property
    val distort: KlangScriptIgnitorDistortSlots = KlangScriptIgnitorDistortSlots

    /** The highpass stage's slots: `freq`, `q`, `passes`, `env`, `attack`, `decay`, `sustain`, `release`. */
    @KlangScript.Property
    val hpf: KlangScriptIgnitorHpfSlots = KlangScriptIgnitorHpfSlots

    /** The bandpass stage's slots: `freq`, `q`, `env`, `attack`, `decay`, `sustain`, `release`. */
    @KlangScript.Property
    val bpf: KlangScriptIgnitorBpfSlots = KlangScriptIgnitorBpfSlots

    /** The notch stage's slots: `freq`, `q`, `env`, `attack`, `decay`, `sustain`, `release`. */
    @KlangScript.Property
    val notch: KlangScriptIgnitorNotchSlots = KlangScriptIgnitorNotchSlots

    /** The lowpass stage's slots: `freq`, `q`, `passes`, `env`, `attack`, `decay`, `sustain`, `release`. */
    @KlangScript.Property
    val lpf: KlangScriptIgnitorLpfSlots = KlangScriptIgnitorLpfSlots

    /** The tremolo stage's slots: `depth`, `rate`, `shape`. */
    @KlangScript.Property
    val tremolo: KlangScriptIgnitorTremoloSlots = KlangScriptIgnitorTremoloSlots

    /** The amplitude envelope's slots: `attack`, `decay`, `sustain`, `release`, `on`. */
    @KlangScript.Property
    val adsr: KlangScriptIgnitorAdsrSlots = KlangScriptIgnitorAdsrSlots

    /** The amplitude envelope's curve slots: `attack`, `decay`, `release`. */
    @KlangScript.Property
    val adsrCurves: KlangScriptIgnitorAdsrCurvesSlots = KlangScriptIgnitorAdsrCurvesSlots

    /** The highpass envelope's curve slots: `attack`, `decay`, `release`. */
    @KlangScript.Property
    val hpfCurves: KlangScriptIgnitorHpfCurvesSlots = KlangScriptIgnitorHpfCurvesSlots

    /** The bandpass envelope's curve slots: `attack`, `decay`, `release`. */
    @KlangScript.Property
    val bpfCurves: KlangScriptIgnitorBpfCurvesSlots = KlangScriptIgnitorBpfCurvesSlots

    /** The notch envelope's curve slots: `attack`, `decay`, `release`. */
    @KlangScript.Property
    val notchCurves: KlangScriptIgnitorNotchCurvesSlots = KlangScriptIgnitorNotchCurvesSlots

    /** The lowpass envelope's curve slots: `attack`, `decay`, `release`. */
    @KlangScript.Property
    val lpfCurves: KlangScriptIgnitorLpfCurvesSlots = KlangScriptIgnitorLpfCurvesSlots
}
