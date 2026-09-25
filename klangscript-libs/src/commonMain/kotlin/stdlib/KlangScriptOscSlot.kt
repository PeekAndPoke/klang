/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * Canonical open parameter slots for sprudel-compatible custom sounds.
 *
 * Each slot is the same `IgnitorDsl.Param(name, default)` singleton that built-in
 * sounds use, exposed for custom sounds that want to opt in to sprudel
 * modulation (the `analog`, `voices`, `spread`, ... knobs on the oscillator builders).
 *
 * ```KlangScript(Executable)
 * let pad = Osc.sine(x => x.analog(OscSlot.analog)).lowpass(2000)
 * note("c").sound(pad)
 * ```
 *
 * Also accessible via `Osc.slot.analog` (member-property chain through the Osc
 * namespace).
 *
 * Without opting in, custom sounds ignore sprudel modulation (the data-class
 * defaults are sealed `Constant(0.0)`). Opting in wires the named slot to
 * `oscParams[ name ]` lookup at voice-trigger time.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.Object("OscSlot")
object KlangScriptOscSlot {
    override fun toString(): String = "[OscSlot object]"

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

    /** Open `decay` slot (default 0.996). Used by pluck. */
    @KlangScript.Property
    val decay: IgnitorDsl = IgnitorDsl.Slots.decay

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

    /**
     * Open `pregain` slot (default 1.0): how hard the pattern plays INTO the instrument, the
     * level at which the signal meets the instrument's first nonlinearity. Mirrors sprudel
     * `.pregain(x)`.
     *
     * An ordinary slot, so it does what the tree wires it to and nothing otherwise: an instrument
     * that never places it ignores `pregain(x)` bit for bit. `.pregain()` is the short spelling
     * of `.mul(OscSlot.pregain)`, and the place to put it is in front of the nonlinearity it
     * should drive:
     *
     * ```
     * Osc.saw().pregain().distort(0.5)   // play harder, get dirtier
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

    // ── The slots of the classic tail, one group per stage (`OscSlot.lpf.freq`) ──
    //
    // What `x.classic()` places, and what a tail of your own places when it wants the pattern's
    // voice doors to reach it. Named after sprudel's readers (`lpf.freq`, `adsr.attack`); the Kotlin
    // door is `IgnitorDsl.Slots.lpf.freq`, the same object.

    /** The crush stage's slot: `OscSlot.crush.amount`. */
    @KlangScript.Property
    val crush: KlangScriptCrushSlots = KlangScriptCrushSlots

    /** The coarse stage's slot: `OscSlot.coarse.amount`. */
    @KlangScript.Property
    val coarse: KlangScriptCoarseSlots = KlangScriptCoarseSlots

    /** The distort stage's slots: `amount`, `shape`, `oversample`. */
    @KlangScript.Property
    val distort: KlangScriptDistortSlots = KlangScriptDistortSlots

    /** The highpass stage's slots: `freq`, `q`, `passes`, `env`, `attack`, `decay`, `sustain`, `release`. */
    @KlangScript.Property
    val hpf: KlangScriptHpfSlots = KlangScriptHpfSlots

    /** The bandpass stage's slots: `freq`, `q`, `env`, `attack`, `decay`, `sustain`, `release`. */
    @KlangScript.Property
    val bpf: KlangScriptBpfSlots = KlangScriptBpfSlots

    /** The notch stage's slots: `freq`, `q`, `env`, `attack`, `decay`, `sustain`, `release`. */
    @KlangScript.Property
    val notch: KlangScriptNotchSlots = KlangScriptNotchSlots

    /** The lowpass stage's slots: `freq`, `q`, `passes`, `env`, `attack`, `decay`, `sustain`, `release`. */
    @KlangScript.Property
    val lpf: KlangScriptLpfSlots = KlangScriptLpfSlots

    /** The tremolo stage's slots: `depth`, `sync`, `shape`, `skew`, `phase`. */
    @KlangScript.Property
    val tremolo: KlangScriptTremoloSlots = KlangScriptTremoloSlots

    /** The amplitude envelope's slots: `attack`, `decay`, `sustain`, `release`, `on`. */
    @KlangScript.Property
    val adsr: KlangScriptAdsrSlots = KlangScriptAdsrSlots

    /** The amplitude envelope's curve slots: `attack`, `decay`, `release`. */
    @KlangScript.Property
    val adsrCurves: KlangScriptAdsrCurvesSlots = KlangScriptAdsrCurvesSlots

    /** The highpass envelope's curve slots: `attack`, `decay`, `release`. */
    @KlangScript.Property
    val hpfCurves: KlangScriptHpfCurvesSlots = KlangScriptHpfCurvesSlots

    /** The bandpass envelope's curve slots: `attack`, `decay`, `release`. */
    @KlangScript.Property
    val bpfCurves: KlangScriptBpfCurvesSlots = KlangScriptBpfCurvesSlots

    /** The notch envelope's curve slots: `attack`, `decay`, `release`. */
    @KlangScript.Property
    val notchCurves: KlangScriptNotchCurvesSlots = KlangScriptNotchCurvesSlots

    /** The lowpass envelope's curve slots: `attack`, `decay`, `release`. */
    @KlangScript.Property
    val lpfCurves: KlangScriptLpfCurvesSlots = KlangScriptLpfCurvesSlots
}
