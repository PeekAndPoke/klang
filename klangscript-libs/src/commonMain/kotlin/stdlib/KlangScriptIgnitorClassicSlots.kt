/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

// ═════════════════════════════════════════════════════════════════════════════════════════════
// The slot groups of the classic tail on the script door: `Ignitor.slot.lpf.freq`, `Ignitor.slot.adsr.attack`.
//
// Each group is the script face of one group of `IgnitorDsl.Slots` (the Kotlin door), and every
// property hands back THE SAME `Param` object, so a script tail and a Kotlin tail place identical
// slots. The names, the defaults and which sprudel reader each one mirrors are documented once, in
// `audio_bridge`'s `IgnitorDslClassic.kt`.
//
// Each group is its own type, registered with `@TypeExtensions` on itself rather than as an
// `@Object`, so it is reachable only through `Ignitor.slot` and adds no global name.
// ═════════════════════════════════════════════════════════════════════════════════════════════

/** `Ignitor.slot.penv`: the pitch envelope stage's slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptIgnitorPenvSlots::class)
object KlangScriptIgnitorPenvSlots {
    override fun toString(): String = "[Ignitor.slot.penv]"

    /** The pitch at the envelope's peak in semitones, default 0 (off: no envelope). Mirrors sprudel's `penv.semitones`. */
    @KlangScript.Property
    val semitones: IgnitorDsl = IgnitorDsl.Slots.penv.semitones

    /** Attack in seconds, default 0.01. Mirrors sprudel's `penv.attack`. */
    @KlangScript.Property
    val attack: IgnitorDsl = IgnitorDsl.Slots.penv.attack

    /** Decay in seconds, default 0.1. Mirrors sprudel's `penv.decay`. */
    @KlangScript.Property
    val decay: IgnitorDsl = IgnitorDsl.Slots.penv.decay

    /** The held share of `semitones`, default 0 (back on the note). Mirrors sprudel's `penv.sustain`. */
    @KlangScript.Property
    val sustain: IgnitorDsl = IgnitorDsl.Slots.penv.sustain

    /** Release in seconds from the gate, default 0. Mirrors sprudel's `penv.release`. */
    @KlangScript.Property
    val release: IgnitorDsl = IgnitorDsl.Slots.penv.release
}

/** `Ignitor.slot.penvCurves`: the pitch envelope's curve slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptIgnitorPenvCurvesSlots::class)
object KlangScriptIgnitorPenvCurvesSlots {
    override fun toString(): String = "[Ignitor.slot.penvCurves]"

    /** The attack's curve as its index in the curve list, default `exp`. Mirrors sprudel's `penvCurves(attack = ...)`. */
    @KlangScript.Property
    val attack: IgnitorDsl = IgnitorDsl.Slots.penvCurves.attack

    /** The decay's curve, default `exp`. Mirrors sprudel's `penvCurves(decay = ...)`. */
    @KlangScript.Property
    val decay: IgnitorDsl = IgnitorDsl.Slots.penvCurves.decay

    /** The release's curve, default `exp`. Mirrors sprudel's `penvCurves(release = ...)`. */
    @KlangScript.Property
    val release: IgnitorDsl = IgnitorDsl.Slots.penvCurves.release
}

/** `Ignitor.slot.crush`: the crush stage's slot, `bits` (`crush.bits`). */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptIgnitorCrushSlots::class)
object KlangScriptIgnitorCrushSlots {
    override fun toString(): String = "[Ignitor.slot.crush]"

    /** Bit depth, default 0 (off; below 1 the stage passes through). Mirrors sprudel's `crush.bits`. */
    @KlangScript.Property
    val bits: IgnitorDsl = IgnitorDsl.Slots.crush.bits
}

/** `Ignitor.slot.coarse`: the coarse stage's slot, `factor` (`coarse.factor`). */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptIgnitorCoarseSlots::class)
object KlangScriptIgnitorCoarseSlots {
    override fun toString(): String = "[Ignitor.slot.coarse]"

    /** Sample-hold factor, default 0 (off; at 1 or less nothing is held). Mirrors sprudel's `coarse.factor`. */
    @KlangScript.Property
    val factor: IgnitorDsl = IgnitorDsl.Slots.coarse.factor
}

/** `Ignitor.slot.distort`: the distort stage's slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptIgnitorDistortSlots::class)
object KlangScriptIgnitorDistortSlots {
    override fun toString(): String = "[Ignitor.slot.distort]"

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

/** `Ignitor.slot.hpf`: the highpass stage's slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptIgnitorHpfSlots::class)
object KlangScriptIgnitorHpfSlots {
    override fun toString(): String = "[Ignitor.slot.hpf]"

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

/** `Ignitor.slot.bpf`: the bandpass stage's slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptIgnitorBpfSlots::class)
object KlangScriptIgnitorBpfSlots {
    override fun toString(): String = "[Ignitor.slot.bpf]"

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

/** `Ignitor.slot.notch`: the notch stage's slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptIgnitorNotchSlots::class)
object KlangScriptIgnitorNotchSlots {
    override fun toString(): String = "[Ignitor.slot.notch]"

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

/** `Ignitor.slot.lpf`: the lowpass stage's slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptIgnitorLpfSlots::class)
object KlangScriptIgnitorLpfSlots {
    override fun toString(): String = "[Ignitor.slot.lpf]"

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

/** `Ignitor.slot.tremolo`: the tremolo stage's slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptIgnitorTremoloSlots::class)
object KlangScriptIgnitorTremoloSlots {
    override fun toString(): String = "[Ignitor.slot.tremolo]"

    /** Depth, default 0 (off). Mirrors sprudel's `tremolo.depth`. */
    @KlangScript.Property
    val depth: IgnitorDsl = IgnitorDsl.Slots.tremolo.depth

    /** The LFO rate in Hz, default 0. Mirrors sprudel's `tremolo.rate`. */
    @KlangScript.Property
    val rate: IgnitorDsl = IgnitorDsl.Slots.tremolo.rate

    /** The LFO shape as its index in the shape list, default `sine`. Mirrors sprudel's `tremolo(shape = ...)`. */
    @KlangScript.Property
    val shape: IgnitorDsl = IgnitorDsl.Slots.tremolo.shape
}

/** `Ignitor.slot.adsr`: the amplitude envelope's slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptIgnitorAdsrSlots::class)
object KlangScriptIgnitorAdsrSlots {
    override fun toString(): String = "[Ignitor.slot.adsr]"

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

/** `Ignitor.slot.adsrCurves`: the amplitude envelope's curve slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptIgnitorAdsrCurvesSlots::class)
object KlangScriptIgnitorAdsrCurvesSlots {
    override fun toString(): String = "[Ignitor.slot.adsrCurves]"

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

/** `Ignitor.slot.hpfCurves`: the highpass envelope's curve slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptIgnitorHpfCurvesSlots::class)
object KlangScriptIgnitorHpfCurvesSlots {
    override fun toString(): String = "[Ignitor.slot.hpfCurves]"

    /** The attack's curve as its index in the curve list, default `exp`. Mirrors sprudel's `hpfCurves(attack = ...)`. */
    @KlangScript.Property
    val attack: IgnitorDsl = IgnitorDsl.Slots.hpfCurves.attack

    /** The decay's curve, default `exp`. Mirrors sprudel's `hpfCurves(decay = ...)`. */
    @KlangScript.Property
    val decay: IgnitorDsl = IgnitorDsl.Slots.hpfCurves.decay

    /** The release's curve, default `exp`. Mirrors sprudel's `hpfCurves(release = ...)`. */
    @KlangScript.Property
    val release: IgnitorDsl = IgnitorDsl.Slots.hpfCurves.release
}

/** `Ignitor.slot.bpfCurves`: the bandpass envelope's curve slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptIgnitorBpfCurvesSlots::class)
object KlangScriptIgnitorBpfCurvesSlots {
    override fun toString(): String = "[Ignitor.slot.bpfCurves]"

    /** The attack's curve as its index in the curve list, default `exp`. Mirrors sprudel's `bpfCurves(attack = ...)`. */
    @KlangScript.Property
    val attack: IgnitorDsl = IgnitorDsl.Slots.bpfCurves.attack

    /** The decay's curve, default `exp`. Mirrors sprudel's `bpfCurves(decay = ...)`. */
    @KlangScript.Property
    val decay: IgnitorDsl = IgnitorDsl.Slots.bpfCurves.decay

    /** The release's curve, default `exp`. Mirrors sprudel's `bpfCurves(release = ...)`. */
    @KlangScript.Property
    val release: IgnitorDsl = IgnitorDsl.Slots.bpfCurves.release
}

/** `Ignitor.slot.notchCurves`: the notch envelope's curve slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptIgnitorNotchCurvesSlots::class)
object KlangScriptIgnitorNotchCurvesSlots {
    override fun toString(): String = "[Ignitor.slot.notchCurves]"

    /** The attack's curve as its index in the curve list, default `exp`. Mirrors sprudel's `notchCurves(attack = ...)`. */
    @KlangScript.Property
    val attack: IgnitorDsl = IgnitorDsl.Slots.notchCurves.attack

    /** The decay's curve, default `exp`. Mirrors sprudel's `notchCurves(decay = ...)`. */
    @KlangScript.Property
    val decay: IgnitorDsl = IgnitorDsl.Slots.notchCurves.decay

    /** The release's curve, default `exp`. Mirrors sprudel's `notchCurves(release = ...)`. */
    @KlangScript.Property
    val release: IgnitorDsl = IgnitorDsl.Slots.notchCurves.release
}

/** `Ignitor.slot.lpfCurves`: the lowpass envelope's curve slots. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptIgnitorLpfCurvesSlots::class)
object KlangScriptIgnitorLpfCurvesSlots {
    override fun toString(): String = "[Ignitor.slot.lpfCurves]"

    /** The attack's curve as its index in the curve list, default `exp`. Mirrors sprudel's `lpfCurves(attack = ...)`. */
    @KlangScript.Property
    val attack: IgnitorDsl = IgnitorDsl.Slots.lpfCurves.attack

    /** The decay's curve, default `exp`. Mirrors sprudel's `lpfCurves(decay = ...)`. */
    @KlangScript.Property
    val decay: IgnitorDsl = IgnitorDsl.Slots.lpfCurves.decay

    /** The release's curve, default `exp`. Mirrors sprudel's `lpfCurves(release = ...)`. */
    @KlangScript.Property
    val release: IgnitorDsl = IgnitorDsl.Slots.lpfCurves.release
}
