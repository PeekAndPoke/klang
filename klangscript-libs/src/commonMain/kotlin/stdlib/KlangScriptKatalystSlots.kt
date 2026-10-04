/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystParam
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

// ═════════════════════════════════════════════════════════════════════════════════════════════
// The knobs of the classic chain on the script door: `Katalyst.slot.reverb.wet`, `Kat.slot.gain.gain`.
//
// The twin of `KlangScriptIgnitorSlots` on the other host. Each group is the script face of one group of
// `KatalystDsl.Slots` (the Kotlin door), and every property hands back THE SAME `KatalystParam` object, so a script
// and a Kotlin song name identical knobs. The names and the defaults are documented once, in `audio_bridge`'s
// `KatalystDslSlots.kt` and the KDoc of `KatalystDsl.classic`.
//
// Each type is registered with `@TypeExtensions` on itself rather than as an `@Object`, so it is reachable only
// through `Katalyst.slot` and adds no global name.
// ═════════════════════════════════════════════════════════════════════════════════════════════

/**
 * `Katalyst.slot`: the knobs of the classic chain, one group per stage, each a Katalyst param named `<stage>.<knob>`.
 *
 * Hand one to `katp` instead of typing its name, or place it as a knob of a chain of your own so that the same
 * `katp` write reaches it:
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").reverb(wet = 0.4).katp(Katalyst.slot.reverb.size, "<2 8>")
 * ```
 *
 * `Kat.slot` is the same. The Kotlin door is `KatalystDsl.Slots`, the same objects.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptKatalystSlots::class)
object KlangScriptKatalystSlots {
    override fun toString(): String = "[Katalyst.slot]"

    /** The body stage's knobs: `material`, `wet`, `floor`. */
    @KlangScript.Property
    val body: KlangScriptKatalystBodySlots = KlangScriptKatalystBodySlots

    /** The vowel stage's knobs: `vowel`, `wet`, `floor`. */
    @KlangScript.Property
    val vowel: KlangScriptKatalystVowelSlots = KlangScriptKatalystVowelSlots

    /** The delay stage's knobs: `wet`, `time`, `feedback`, `cap`. */
    @KlangScript.Property
    val delay: KlangScriptKatalystDelaySlots = KlangScriptKatalystDelaySlots

    /** The reverb stage's knobs: `wet`, `size`, `lowpass`. */
    @KlangScript.Property
    val reverb: KlangScriptKatalystReverbSlots = KlangScriptKatalystReverbSlots

    /** The phaser stage's knobs: `rate`, `wet`, `center`, `sweep`, `floor`. */
    @KlangScript.Property
    val phaser: KlangScriptKatalystPhaserSlots = KlangScriptKatalystPhaserSlots

    /** The compressor stage's knobs: `threshold`, `ratio`, `knee`, `attack`, `release`. */
    @KlangScript.Property
    val compressor: KlangScriptKatalystCompressorSlots = KlangScriptKatalystCompressorSlots

    /** The gain stage's knobs: `gain`. */
    @KlangScript.Property
    val gain: KlangScriptKatalystGainSlots = KlangScriptKatalystGainSlots

    /** The duck stage's knobs: `orbit`, `depth`, `attack`. */
    @KlangScript.Property
    val duck: KlangScriptKatalystDuckSlots = KlangScriptKatalystDuckSlots
}

/** `Katalyst.slot.body`: the body stage's knobs. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptKatalystBodySlots::class)
object KlangScriptKatalystBodySlots {
    override fun toString(): String = "[Katalyst.slot.body]"

    /** `body.material`. The material as its INDEX in the catalogue, default unset (no body). What `body(material = ...)` writes. */
    @KlangScript.Property
    val material: KatalystParam = KatalystDsl.Slots.body.material

    /** `body.wet`. How much of the orbit runs through the body, default unset (the engine's `BODY_WET` once a material is set). */
    @KlangScript.Property
    val wet: KatalystParam = KatalystDsl.Slots.body.wet

    /** `body.floor`. The broadband floor under the modes. */
    @KlangScript.Property
    val floor: KatalystParam = KatalystDsl.Slots.body.floor
}

/** `Katalyst.slot.vowel`: the vowel stage's knobs. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptKatalystVowelSlots::class)
object KlangScriptKatalystVowelSlots {
    override fun toString(): String = "[Katalyst.slot.vowel]"

    /** `vowel.vowel`. The vowel as its INDEX in the catalogue, default unset (no vowel). What `vowel(vowel = ...)` writes. */
    @KlangScript.Property
    val vowel: KatalystParam = KatalystDsl.Slots.vowel.vowel

    /** `vowel.wet`. How much of the orbit runs through the formant bank, default unset (the engine's `VOWEL_WET` once a vowel is set). */
    @KlangScript.Property
    val wet: KatalystParam = KatalystDsl.Slots.vowel.wet

    /** `vowel.floor`. The dry floor of the formant bank. */
    @KlangScript.Property
    val floor: KatalystParam = KatalystDsl.Slots.vowel.floor
}

/** `Katalyst.slot.delay`: the delay stage's knobs. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptKatalystDelaySlots::class)
object KlangScriptKatalystDelaySlots {
    override fun toString(): String = "[Katalyst.slot.delay]"

    /** `delay.wet`. How much of the orbit goes into the delay, default 0 (off). */
    @KlangScript.Property
    val wet: KatalystParam = KatalystDsl.Slots.delay.wet

    /** `delay.time`. Delay time in seconds, default 0 (off: the delay engages on its time). */
    @KlangScript.Property
    val time: KatalystParam = KatalystDsl.Slots.delay.time

    /** `delay.feedback`. Feedback amount, default 0. */
    @KlangScript.Property
    val feedback: KatalystParam = KatalystDsl.Slots.delay.feedback

    /** `delay.cap`. The soft cap of the recirculating line. */
    @KlangScript.Property
    val cap: KatalystParam = KatalystDsl.Slots.delay.cap
}

/** `Katalyst.slot.reverb`: the reverb stage's knobs. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptKatalystReverbSlots::class)
object KlangScriptKatalystReverbSlots {
    override fun toString(): String = "[Katalyst.slot.reverb]"

    /** `reverb.wet`. How much of the orbit goes into the reverb, default 0 (off). */
    @KlangScript.Property
    val wet: KatalystParam = KatalystDsl.Slots.reverb.wet

    /** `reverb.size`. Tail length, default 0 (off: the reverb engages on its size). */
    @KlangScript.Property
    val size: KatalystParam = KatalystDsl.Slots.reverb.size

    /** `reverb.lowpass`. Damping of the tail as a lowpass cutoff in Hz, default unset (undamped). */
    @KlangScript.Property
    val lowpass: KatalystParam = KatalystDsl.Slots.reverb.lowpass
}

/** `Katalyst.slot.phaser`: the phaser stage's knobs. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptKatalystPhaserSlots::class)
object KlangScriptKatalystPhaserSlots {
    override fun toString(): String = "[Katalyst.slot.phaser]"

    /** `phaser.rate`. Sweep rate in Hz. */
    @KlangScript.Property
    val rate: KatalystParam = KatalystDsl.Slots.phaser.rate

    /** `phaser.wet`. Wet amount, default 0 (off). */
    @KlangScript.Property
    val wet: KatalystParam = KatalystDsl.Slots.phaser.wet

    /** `phaser.center`. Center frequency of the sweep in Hz. */
    @KlangScript.Property
    val center: KatalystParam = KatalystDsl.Slots.phaser.center

    /** `phaser.sweep`. Width of the sweep in Hz. */
    @KlangScript.Property
    val sweep: KatalystParam = KatalystDsl.Slots.phaser.sweep

    /** `phaser.floor`. Minimum dry share. */
    @KlangScript.Property
    val floor: KatalystParam = KatalystDsl.Slots.phaser.floor
}

/** `Katalyst.slot.compressor`: the compressor stage's knobs, all five unset (off) until one is written. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptKatalystCompressorSlots::class)
object KlangScriptKatalystCompressorSlots {
    override fun toString(): String = "[Katalyst.slot.compressor]"

    /** `compressor.threshold`. Ceiling in dBFS where gain reduction starts. */
    @KlangScript.Property
    val threshold: KatalystParam = KatalystDsl.Slots.compressor.threshold

    /** `compressor.ratio`. Compression ratio above the threshold. */
    @KlangScript.Property
    val ratio: KatalystParam = KatalystDsl.Slots.compressor.ratio

    /** `compressor.knee`. Soft-knee width in dB. */
    @KlangScript.Property
    val knee: KatalystParam = KatalystDsl.Slots.compressor.knee

    /** `compressor.attack`. How fast the gain closes, in seconds. */
    @KlangScript.Property
    val attack: KatalystParam = KatalystDsl.Slots.compressor.attack

    /** `compressor.release`. How fast the gain opens again, in seconds. */
    @KlangScript.Property
    val release: KatalystParam = KatalystDsl.Slots.compressor.release
}

/** `Katalyst.slot.gain`: the group fader's knob. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptKatalystGainSlots::class)
object KlangScriptKatalystGainSlots {
    override fun toString(): String = "[Katalyst.slot.gain]"

    /** `gain.gain`. The orbit's group fader, default 1 (unity). The stage `gain`, its knob `gain`: `Katalyst.slot.gain.gain` reads oddly and is right. */
    @KlangScript.Property
    val gain: KatalystParam = KatalystDsl.Slots.gain.gain
}

/** `Katalyst.slot.duck`: the duck stage's knobs. */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(KlangScriptKatalystDuckSlots::class)
object KlangScriptKatalystDuckSlots {
    override fun toString(): String = "[Katalyst.slot.duck]"

    /** `duck.orbit`. The orbit to listen to, default unset (no ducking). */
    @KlangScript.Property
    val orbit: KatalystParam = KatalystDsl.Slots.duck.orbit

    /** `duck.depth`. How far this orbit is pulled down, default 0. */
    @KlangScript.Property
    val depth: KatalystParam = KatalystDsl.Slots.duck.depth

    /** `duck.attack`. How fast the duck closes, in seconds. */
    @KlangScript.Property
    val attack: KatalystParam = KatalystDsl.Slots.duck.attack
}
