/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

// ═════════════════════════════════════════════════════════════════════════════════════════════
// The slot groups of the classic tail on the script door: `OscSlot.lpf.freq`, `OscSlot.adsr.attack`.
//
// Each group is the script face of one group of `IgnitorDsl.Slots` (the Kotlin door), and every
// property hands back THE SAME `Param` object, so a script tail and a Kotlin tail place identical
// slots. The names, the defaults and which sprudel reader each one mirrors are documented once, in
// `audio_bridge`'s `IgnitorDslClassic.kt`.
//
// Each group is its own type, registered with `@TypeExtensions` on itself rather than as an
// `@Object`, so it is reachable only through `OscSlot` and adds no global name.
// ═════════════════════════════════════════════════════════════════════════════════════════════

/** `OscSlot.crush`: the crush stage's slot, `amount` (`crush.amount`). */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptCrushSlots::class)
object KlangScriptCrushSlots {
    override fun toString(): String = "[OscSlot.crush]"

    /** Bit-crush amount, default 0 (off). Mirrors sprudel's `crush.amount`. */
    @KlangScript.Property
    val amount: IgnitorDsl = IgnitorDsl.Slots.crush.amount
}

/** `OscSlot.coarse`: the coarse stage's slot, `amount` (`coarse.amount`). */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptCoarseSlots::class)
object KlangScriptCoarseSlots {
    override fun toString(): String = "[OscSlot.coarse]"

    /** Sample-rate reduction amount, default 0 (off). Mirrors sprudel's `coarse.amount`. */
    @KlangScript.Property
    val amount: IgnitorDsl = IgnitorDsl.Slots.coarse.amount
}

/** `OscSlot.distort`: the distort stage's slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptDistortSlots::class)
object KlangScriptDistortSlots {
    override fun toString(): String = "[OscSlot.distort]"

    /** Drive amount, default 0 (off). Mirrors sprudel's `distort.amount`. */
    @KlangScript.Property
    val amount: IgnitorDsl = IgnitorDsl.Slots.distort.amount

    /** The waveshaper as its index in the shape list, default `soft`. Mirrors sprudel's `distort(shape = ...)`. */
    @KlangScript.Property
    val shape: IgnitorDsl = IgnitorDsl.Slots.distort.shape

    /** Oversampling factor, default 0 (none). Mirrors sprudel's `distort.oversample`. */
    @KlangScript.Property
    val oversample: IgnitorDsl = IgnitorDsl.Slots.distort.oversample
}

/** `OscSlot.hpf`: the highpass stage's slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptHpfSlots::class)
object KlangScriptHpfSlots {
    override fun toString(): String = "[OscSlot.hpf]"

    /** Cutoff in Hz, default unset (no filter). Mirrors sprudel's `hpf.freq`. */
    @KlangScript.Property
    val freq: IgnitorDsl = IgnitorDsl.Slots.hpf.freq

    /** Resonance, default 0.707. Mirrors sprudel's `hpf.q`. */
    @KlangScript.Property
    val q: IgnitorDsl = IgnitorDsl.Slots.hpf.q

    /** Cascade count, default 1. Mirrors sprudel's `hpf.passes`. */
    @KlangScript.Property
    val passes: IgnitorDsl = IgnitorDsl.Slots.hpf.passes

    /** Cutoff-envelope depth in semitones, default unset. Mirrors sprudel's `hpf.env`. */
    @KlangScript.Property
    val env: IgnitorDsl = IgnitorDsl.Slots.hpf.env

    /** Cutoff-envelope attack in seconds. Mirrors sprudel's `hpf.attack`. */
    @KlangScript.Property
    val attack: IgnitorDsl = IgnitorDsl.Slots.hpf.attack

    /** Cutoff-envelope decay in seconds. Mirrors sprudel's `hpf.decay`. */
    @KlangScript.Property
    val decay: IgnitorDsl = IgnitorDsl.Slots.hpf.decay

    /** Cutoff-envelope sustain share. Mirrors sprudel's `hpf.sustain`. */
    @KlangScript.Property
    val sustain: IgnitorDsl = IgnitorDsl.Slots.hpf.sustain

    /** Cutoff-envelope release in seconds. Mirrors sprudel's `hpf.release`. */
    @KlangScript.Property
    val release: IgnitorDsl = IgnitorDsl.Slots.hpf.release
}

/** `OscSlot.bpf`: the bandpass stage's slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptBpfSlots::class)
object KlangScriptBpfSlots {
    override fun toString(): String = "[OscSlot.bpf]"

    /** Center in Hz, default unset (no filter). Mirrors sprudel's `bpf.freq`. */
    @KlangScript.Property
    val freq: IgnitorDsl = IgnitorDsl.Slots.bpf.freq

    /** Resonance, default 0.707. Mirrors sprudel's `bpf.q`. */
    @KlangScript.Property
    val q: IgnitorDsl = IgnitorDsl.Slots.bpf.q

    /** Cutoff-envelope depth in semitones, default unset. Mirrors sprudel's `bpf.env`. */
    @KlangScript.Property
    val env: IgnitorDsl = IgnitorDsl.Slots.bpf.env

    /** Cutoff-envelope attack in seconds. Mirrors sprudel's `bpf.attack`. */
    @KlangScript.Property
    val attack: IgnitorDsl = IgnitorDsl.Slots.bpf.attack

    /** Cutoff-envelope decay in seconds. Mirrors sprudel's `bpf.decay`. */
    @KlangScript.Property
    val decay: IgnitorDsl = IgnitorDsl.Slots.bpf.decay

    /** Cutoff-envelope sustain share. Mirrors sprudel's `bpf.sustain`. */
    @KlangScript.Property
    val sustain: IgnitorDsl = IgnitorDsl.Slots.bpf.sustain

    /** Cutoff-envelope release in seconds. Mirrors sprudel's `bpf.release`. */
    @KlangScript.Property
    val release: IgnitorDsl = IgnitorDsl.Slots.bpf.release
}

/** `OscSlot.notch`: the notch stage's slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptNotchSlots::class)
object KlangScriptNotchSlots {
    override fun toString(): String = "[OscSlot.notch]"

    /** Center in Hz, default unset (no filter). Mirrors sprudel's `notch.freq`. */
    @KlangScript.Property
    val freq: IgnitorDsl = IgnitorDsl.Slots.notch.freq

    /** Resonance, default 0.707. Mirrors sprudel's `notch.q`. */
    @KlangScript.Property
    val q: IgnitorDsl = IgnitorDsl.Slots.notch.q

    /** Cutoff-envelope depth in semitones, default unset. Mirrors sprudel's `notch.env`. */
    @KlangScript.Property
    val env: IgnitorDsl = IgnitorDsl.Slots.notch.env

    /** Cutoff-envelope attack in seconds. Mirrors sprudel's `notch.attack`. */
    @KlangScript.Property
    val attack: IgnitorDsl = IgnitorDsl.Slots.notch.attack

    /** Cutoff-envelope decay in seconds. Mirrors sprudel's `notch.decay`. */
    @KlangScript.Property
    val decay: IgnitorDsl = IgnitorDsl.Slots.notch.decay

    /** Cutoff-envelope sustain share. Mirrors sprudel's `notch.sustain`. */
    @KlangScript.Property
    val sustain: IgnitorDsl = IgnitorDsl.Slots.notch.sustain

    /** Cutoff-envelope release in seconds. Mirrors sprudel's `notch.release`. */
    @KlangScript.Property
    val release: IgnitorDsl = IgnitorDsl.Slots.notch.release
}

/** `OscSlot.lpf`: the lowpass stage's slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptLpfSlots::class)
object KlangScriptLpfSlots {
    override fun toString(): String = "[OscSlot.lpf]"

    /** Cutoff in Hz, default unset (no filter). Mirrors sprudel's `lpf.freq`. */
    @KlangScript.Property
    val freq: IgnitorDsl = IgnitorDsl.Slots.lpf.freq

    /** Resonance, default 0.707. Mirrors sprudel's `lpf.q`. */
    @KlangScript.Property
    val q: IgnitorDsl = IgnitorDsl.Slots.lpf.q

    /** Cascade count, default 1. Mirrors sprudel's `lpf.passes`. */
    @KlangScript.Property
    val passes: IgnitorDsl = IgnitorDsl.Slots.lpf.passes

    /** Cutoff-envelope depth in semitones, default unset. Mirrors sprudel's `lpf.env`. */
    @KlangScript.Property
    val env: IgnitorDsl = IgnitorDsl.Slots.lpf.env

    /** Cutoff-envelope attack in seconds. Mirrors sprudel's `lpf.attack`. */
    @KlangScript.Property
    val attack: IgnitorDsl = IgnitorDsl.Slots.lpf.attack

    /** Cutoff-envelope decay in seconds. Mirrors sprudel's `lpf.decay`. */
    @KlangScript.Property
    val decay: IgnitorDsl = IgnitorDsl.Slots.lpf.decay

    /** Cutoff-envelope sustain share. Mirrors sprudel's `lpf.sustain`. */
    @KlangScript.Property
    val sustain: IgnitorDsl = IgnitorDsl.Slots.lpf.sustain

    /** Cutoff-envelope release in seconds. Mirrors sprudel's `lpf.release`. */
    @KlangScript.Property
    val release: IgnitorDsl = IgnitorDsl.Slots.lpf.release
}

/** `OscSlot.tremolo`: the tremolo stage's slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptTremoloSlots::class)
object KlangScriptTremoloSlots {
    override fun toString(): String = "[OscSlot.tremolo]"

    /** Depth, default 0 (off). Mirrors sprudel's `tremolo.depth`. */
    @KlangScript.Property
    val depth: IgnitorDsl = IgnitorDsl.Slots.tremolo.depth

    /** The LFO rate in Hz, default 0. Mirrors sprudel's `tremolo.sync`. */
    @KlangScript.Property
    val sync: IgnitorDsl = IgnitorDsl.Slots.tremolo.sync

    /** The LFO shape as its index in the shape list, default `sine`. Mirrors sprudel's `tremolo(shape = ...)`. */
    @KlangScript.Property
    val shape: IgnitorDsl = IgnitorDsl.Slots.tremolo.shape

    /** Skew, -1 to 1, default 0. Mirrors sprudel's `tremolo.skew`. */
    @KlangScript.Property
    val skew: IgnitorDsl = IgnitorDsl.Slots.tremolo.skew

    /** Start phase in cycles, default 0. Mirrors sprudel's `tremolo.phase`. */
    @KlangScript.Property
    val phase: IgnitorDsl = IgnitorDsl.Slots.tremolo.phase
}

/** `OscSlot.adsr`: the amplitude envelope's slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptAdsrSlots::class)
object KlangScriptAdsrSlots {
    override fun toString(): String = "[OscSlot.adsr]"

    /** Attack in seconds, default 0.01. Mirrors sprudel's `adsr.attack`. */
    @KlangScript.Property
    val attack: IgnitorDsl = IgnitorDsl.Slots.adsr.attack

    /** Decay in seconds, default 0.1. Mirrors sprudel's `adsr.decay`. */
    @KlangScript.Property
    val decay: IgnitorDsl = IgnitorDsl.Slots.adsr.decay

    /** Sustain level, default 1.0. Mirrors sprudel's `adsr.sustain`. */
    @KlangScript.Property
    val sustain: IgnitorDsl = IgnitorDsl.Slots.adsr.sustain

    /** Release in seconds, default 0.05. Mirrors sprudel's `adsr.release`. */
    @KlangScript.Property
    val release: IgnitorDsl = IgnitorDsl.Slots.adsr.release

    /** The envelope's switch, default 1 (on); 0 is off. What sprudel's `adsrOn()` / `adsrOff()` write. */
    @KlangScript.Property
    val on: IgnitorDsl = IgnitorDsl.Slots.adsr.on
}

/** `OscSlot.adsrCurves`: the amplitude envelope's curve slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptAdsrCurvesSlots::class)
object KlangScriptAdsrCurvesSlots {
    override fun toString(): String = "[OscSlot.adsrCurves]"

    /** The attack's curve as its index in the curve list, default `exp`. Mirrors sprudel's `adsrCurves(attack = ...)`. */
    @KlangScript.Property
    val attack: IgnitorDsl = IgnitorDsl.Slots.adsrCurves.attack

    /** The decay's curve, default `exp`. Mirrors sprudel's `adsrCurves(decay = ...)`. */
    @KlangScript.Property
    val decay: IgnitorDsl = IgnitorDsl.Slots.adsrCurves.decay

    /** The release's curve, default `exp`. Mirrors sprudel's `adsrCurves(release = ...)`. */
    @KlangScript.Property
    val release: IgnitorDsl = IgnitorDsl.Slots.adsrCurves.release
}
