/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:KlangScript.Library(KlangScriptLibraries.STDLIB)

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/*
 * Builders for an orbit chain. `Katalyst(k => ...)` hands a [KatalystBuilder] to the lambda; every
 * knob appends ONE stage, in written order (the order IS the chain), and returns a new builder.
 * A stage's musical inputs are the parameters of its door, `wet` first wherever there is one; a
 * secondary knob (`floor`, `cap`) sits on the stage's own builder behind a `configure` lambda:
 *
 *     katalyst(Katalyst(k => k
 *         .eq(e => e.band(freq = 300, q = 0.8, db = 2.0))
 *         .reverb(0.15, 3)
 *         .compressor(threshold = -21, ratio = 3)
 *     ))
 *
 * Every door parameter is optional. An omitted one is exactly what the bare stage carries (the
 * defaults of the stage's [KatalystStageDsl] data class: the shared touched constants, or the "never set"
 * marker on a name knob), so `k.reverb()` means what it always meant and `k.reverb(0.3)` what
 * `k.reverb(r => r.wet(0.3))` meant before the door shapes of phase 3 step 3d. It is a
 * fixed value of the chain, not a slot: only a slot (`Katalyst.param(...)`, or any `Param` such as
 * `Osc.param(...)`) listens to the orbit's `katp` state.
 *
 * Effects are the same DSP the master stages use; only the host differs, and the knobs use the
 * same names and scales as their sprudel twins, so a number means the same on either bus.
 *
 * Every knob takes a number OR an `Osc.param(...)` slot (`IgnitorDslLike`, the same door the
 * oscillator knobs use). The chain reads its knobs once per block, so a signal-rate node on one is
 * coerced, never rejected.
 */

// ── The chain ────────────────────────────────────────────────────────────────

/**
 * Builder for a [KatalystDsl] chain, handed to the `configure` lambda of `Katalyst(...)`. Knobs:
 * `classic`, `body`, `vowel`, `delay`, `reverb`, `phaser`, `compressor`, `duck`, `eq`, `gain`,
 * each appending a stage.
 */
data class KatalystBuilder(val node: KatalystDsl) {
    internal fun plus(stage: KatalystStageDsl): KatalystBuilder = copy(node = KatalystDsl(node.stages + stage))
}

/**
 * Appends the familiar block at once: body, vowel, delay, reverb, phaser, compressor, the group
 * fader at unity and the duck, in that order, with every knob a named slot.
 *
 * `Katalyst(k => k.classic().eq(...))` therefore reads as "the chain an orbit has always run, plus
 * an EQ at the end". The fader is the one stage that is not history: it is bit-transparent at
 * unity, and it is there so `katp("gain.gain", x)` reaches a group fader on every orbit (the
 * signal-flow plan, section 6, spot C). `k.classic().gain(0.8)` is legal and is what it looks
 * like, two faders in series: the unity slot, then 0.8.
 *
 * **At most once per builder** (decided with the maintainer, 2026-09-18): a second `classic()` in
 * the same builder returns the builder unchanged, because duplicated stages read the same
 * slot names and one `reverb(0.3)` would run reverb into reverb. Writing a stage out twice
 * (`k.reverb(...).reverb(...)`) is a different thing and still stacks, in written order: that is an
 * author asking for two rooms, not for the familiar chain twice.
 *
 * The duck it brings is an unset one (no source orbit, so no ducking). Appending a `duck(...)`
 * after it is how you set one: the cylinder runs exactly one ducking effect, so the LAST duck in
 * the chain wins.
 */
@KlangScript.Function
fun KatalystBuilder.classic(): KatalystBuilder {
    if (node.stages.containsInOrder(KatalystDsl.classic.stages)) {
        return this
    }

    return copy(node = KatalystDsl(node.stages + KatalystDsl.classic.stages))
}

/**
 * True when [other] appears in this list as a contiguous run, which is what "the classic block is
 * already in this builder" means. Content equality, not identity: a chain hand-built from the same
 * stages is the same chain everywhere else in the Katalyst, so it must be here too.
 *
 * A plain double loop over two short lists, run once per `classic()` call at construction time and
 * never on an audio path.
 */
private fun List<KatalystStageDsl>.containsInOrder(other: List<KatalystStageDsl>): Boolean {
    if (other.isEmpty() || other.size > size) {
        return false
    }

    for (start in 0..(size - other.size)) {
        var matches = true

        for (i in other.indices) {
            if (this[start + i] != other[i]) {
                matches = false
                break
            }
        }

        if (matches) {
            return true
        }
    }

    return false
}

/**
 * Appends a resonant body: a bank of narrow modes over a broadband floor, the box a sound sits in.
 *
 * A material NAME is converted to the stage's `material` INDEX here, through the one shared
 * `BodyMaterials.indexOf`, so a chain and a pattern mean the same box by the same word. An unknown
 * name is `none`, and so is no material at all: the stage is declared and off.
 *
 * **Why [material] takes more than a name.** The material is an index slot (Katalyst step 5a-2,
 * 2026-09-18), so a chain that wants it to MOVE writes `k.body(material = Katalyst.param("mat", 3))`
 * and listens to `katp("mat", n)`, and a plain number picks a fixed box by its index. The door
 * therefore accepts a name, a number or a slot; anything else is a script-level type error.
 *
 * @param wet how much of the orbit runs through the body, 0 to 1 (default 0.5). Orbit twin:
 *   `body(wet = ...)`.
 * @param material a material name (`"wood"`, `"glass"`, `"tube"`, ...), an index into the
 *   catalogue, or a `Katalyst.param` slot carrying one; omitted leaves the stage off. Orbit twin:
 *   `body(material = "wood")`, or `katp("body.material", n)` for the number.
 * @param configure receives the [KatalystBodyBuilder] (knob: `floor`) and returns it.
 */
@KlangScript.Function
fun KatalystBuilder.body(
    wet: IgnitorDslLike? = null,
    material: IgnitorDslLike? = null,
    configure: ((KatalystBodyBuilder) -> KatalystBodyBuilder)? = null,
): KatalystBuilder {
    val bare = KatalystStageDsl.Body()

    return plus(
        KatalystBodyBuilder(
            KatalystStageDsl.Body(
                material = catalogueIndex(material, bare.material, BodyMaterials::indexOf),
                wet = wet?.toIgnitorDsl() ?: bare.wet,
                floor = bare.floor,
            )
        ).configuredBy("Katalyst body", configure).node
    )
}

/**
 * Appends a formant bank: the vowel a sound sings.
 *
 * A vowel NAME is converted to the stage's `vowel` INDEX here, through the one shared
 * `VowelBands.indexOf`, so a bare `"a"` is the soprano register on this door exactly as it is on
 * the pattern one. An unknown name is `none`, and so is no vowel at all.
 *
 * **Why [vowel] takes more than a name.** The vowel is an index slot, as the body's material is
 * (see [body]): `k.vowel(vowel = Katalyst.param("vw", 1))` listens to `katp("vw", n)`, and a plain
 * number picks a fixed vowel by its index.
 *
 * @param wet how much of the orbit runs through the formant bank, 0 to 1 (default 0.5). Orbit
 *   twin: `vowel(wet = ...)`.
 * @param vowel a vowel name, optionally `voice:vowel` (`"a"`, `"soprano:o"`), an index into the
 *   catalogue, or a `Katalyst.param` slot carrying one; omitted leaves the stage off. Orbit twin:
 *   `vowel(vowel = "a")`, or `katp("vowel.vowel", n)` for the number.
 * @param configure receives the [KatalystVowelBuilder] (knob: `floor`) and returns it.
 */
@KlangScript.Function
fun KatalystBuilder.vowel(
    wet: IgnitorDslLike? = null,
    vowel: IgnitorDslLike? = null,
    configure: ((KatalystVowelBuilder) -> KatalystVowelBuilder)? = null,
): KatalystBuilder {
    val bare = KatalystStageDsl.Vowel()

    return plus(
        KatalystVowelBuilder(
            KatalystStageDsl.Vowel(
                vowel = catalogueIndex(vowel, bare.vowel, VowelBands::indexOf),
                wet = wet?.toIgnitorDsl() ?: bare.wet,
                floor = bare.floor,
            )
        ).configuredBy("Katalyst vowel", configure).node
    )
}

/**
 * A name knob as the index the stage carries: a name through its catalogue's `indexOf`, a number or
 * a slot as it is, and nothing at all as the bare stage's own value (the "never set" marker).
 */
private fun catalogueIndex(value: IgnitorDslLike?, bare: IgnitorDsl, indexOf: (String) -> Double): IgnitorDsl =
    when (value) {
        null -> bare
        is String -> IgnitorDsl.Constant(indexOf(value))
        else -> value.toIgnitorDsl()
    }

/**
 * Appends an orbit delay (the shared delay line).
 *
 * @param wet how much of the orbit goes into the delay (default 0.25; 0.0 = off). Orbit twin:
 *   `delay(wet = ...)`.
 * @param time delay time in seconds (default 0.25). Orbit twin: `delay(time = ...)`.
 * @param feedback feedback amount (default 0.3). At or above 1.0 the delay recirculates without
 *   loss and self-oscillates, allowed, with `cap` deciding how loud. Orbit twin:
 *   `delay(feedback = ...)`.
 * @param configure receives the [KatalystDelayBuilder] (knob: `cap`) and returns it.
 */
@KlangScript.Function
fun KatalystBuilder.delay(
    wet: IgnitorDslLike? = null,
    time: IgnitorDslLike? = null,
    feedback: IgnitorDslLike? = null,
    configure: ((KatalystDelayBuilder) -> KatalystDelayBuilder)? = null,
): KatalystBuilder {
    val bare = KatalystStageDsl.Delay()

    return plus(
        KatalystDelayBuilder(
            KatalystStageDsl.Delay(
                wet = wet?.toIgnitorDsl() ?: bare.wet,
                time = time?.toIgnitorDsl() ?: bare.time,
                feedback = feedback?.toIgnitorDsl() ?: bare.feedback,
                cap = bare.cap,
            )
        ).configuredBy("Katalyst delay", configure).node
    )
}

/**
 * Appends an orbit reverb (the shared Freeverb). Flat: every knob is a musical input.
 *
 * @param wet how much of the orbit goes into the reverb (default 0.25; 0.0 = off). Orbit twin:
 *   `reverb(wet = ...)`.
 * @param size tail length, on the SAME scale as sprudel `reverb(size = ...)`: typical 1 to 10,
 *   default 5. 3 is about a 1 s tail, 5 about 1.4 s, 10 about 12.5 s; the shortest reachable is
 *   about 0.7 s, and above 10 is bounded at 10. Orbit twin: `reverb(size = ...)`.
 * @param lowpass high-frequency damping of the tail as a lowpass cutoff in Hz: lower is darker.
 *   Omitted, the engine's fixed default damping applies. Orbit twin: `reverb(lowpass = ...)`.
 */
@KlangScript.Function
fun KatalystBuilder.reverb(
    wet: IgnitorDslLike? = null,
    size: IgnitorDslLike? = null,
    lowpass: IgnitorDslLike? = null,
): KatalystBuilder {
    val bare = KatalystStageDsl.Reverb()

    return plus(
        KatalystStageDsl.Reverb(
            wet = wet?.toIgnitorDsl() ?: bare.wet,
            size = size?.toIgnitorDsl() ?: bare.size,
            lowpass = lowpass?.toIgnitorDsl() ?: bare.lowpass,
        )
    )
}

/**
 * Appends an orbit phaser: a sweeping all-pass notch comb.
 *
 * A bare `k.phaser()` is declared and silent, because its default wet is 0; give it a `wet`.
 *
 * @param wet wet amount, 0 to 1 (default 0, off). Orbit twin: `phaser(wet = ...)`.
 * @param rate sweep rate in Hz (default 0, standing still). Orbit twin: `phaser(rate = ...)`.
 * @param center center frequency of the sweep in Hz (default 1000). Orbit twin:
 *   `phaser(center = ...)`.
 * @param sweep width of the sweep around the center, in Hz (default 1000). Orbit twin:
 *   `phaser(sweep = ...)`.
 * @param configure receives the [KatalystPhaserBuilder] (knob: `floor`) and returns it.
 */
@KlangScript.Function
fun KatalystBuilder.phaser(
    wet: IgnitorDslLike? = null,
    rate: IgnitorDslLike? = null,
    center: IgnitorDslLike? = null,
    sweep: IgnitorDslLike? = null,
    configure: ((KatalystPhaserBuilder) -> KatalystPhaserBuilder)? = null,
): KatalystBuilder {
    val bare = KatalystStageDsl.Phaser()

    return plus(
        KatalystPhaserBuilder(
            KatalystStageDsl.Phaser(
                rate = rate?.toIgnitorDsl() ?: bare.rate,
                wet = wet?.toIgnitorDsl() ?: bare.wet,
                center = center?.toIgnitorDsl() ?: bare.center,
                sweep = sweep?.toIgnitorDsl() ?: bare.sweep,
                floor = bare.floor,
            )
        ).configuredBy("Katalyst phaser", configure).node
    )
}

/**
 * Appends the orbit compressor: the group dynamics. Flat, like every dynamics stage: each knob is
 * a musical input.
 *
 * Put it after the EQ so the detector sees the corrected spectrum and a low cut turns into
 * headroom.
 *
 * @param threshold ceiling in dBFS where gain reduction starts (default -20). Orbit twin:
 *   `compressor(threshold = ...)`.
 * @param ratio compression ratio above the threshold (default 4, i.e. 4:1). Orbit twin:
 *   `compressor(ratio = ...)`.
 * @param knee soft-knee width in dB (default 6). A hard corner injects harmonics on every
 *   crossing. Orbit twin: `compressor(knee = ...)`.
 * @param attack how fast the gain closes, in seconds (default 0.003). Orbit twin:
 *   `compressor(attack = ...)`.
 * @param release how fast the gain opens again, in seconds (default 0.1). Orbit twin:
 *   `compressor(release = ...)`.
 */
@KlangScript.Function
fun KatalystBuilder.compressor(
    threshold: IgnitorDslLike? = null,
    ratio: IgnitorDslLike? = null,
    knee: IgnitorDslLike? = null,
    attack: IgnitorDslLike? = null,
    release: IgnitorDslLike? = null,
): KatalystBuilder {
    val bare = KatalystStageDsl.Compressor()

    return plus(
        KatalystStageDsl.Compressor(
            threshold = threshold?.toIgnitorDsl() ?: bare.threshold,
            ratio = ratio?.toIgnitorDsl() ?: bare.ratio,
            knee = knee?.toIgnitorDsl() ?: bare.knee,
            attack = attack?.toIgnitorDsl() ?: bare.attack,
            release = release?.toIgnitorDsl() ?: bare.release,
        )
    )
}

/**
 * Appends a sidechain duck: this orbit is pulled down whenever the orbit it listens to sounds.
 * Flat, like every dynamics stage.
 *
 * Declared in the chain, but run after every orbit has been processed, so where it sits in the
 * list makes no difference.
 *
 * @param orbit the orbit to listen to. Omitted means no ducking: the stage carries the wire's
 *   non-finite "never set" marker, and the runtime tests `isFinite()` rather than comparing, so a
 *   finite negative is an orbit REQUEST like any other and not an off switch. Orbit twin:
 *   `duck(orbit = ...)`.
 * @param depth how far this orbit is pulled down, 0 to 1 (default 0, no ducking). Orbit twin:
 *   `duck(depth = ...)`.
 * @param attack how fast the duck closes, in seconds (default 0.1). Orbit twin:
 *   `duck(attack = ...)`.
 */
@KlangScript.Function
fun KatalystBuilder.duck(
    orbit: IgnitorDslLike? = null,
    depth: IgnitorDslLike? = null,
    attack: IgnitorDslLike? = null,
): KatalystBuilder {
    val bare = KatalystStageDsl.Duck()

    return plus(
        KatalystStageDsl.Duck(
            orbit = orbit?.toIgnitorDsl() ?: bare.orbit,
            depth = depth?.toIgnitorDsl() ?: bare.depth,
            attack = attack?.toIgnitorDsl() ?: bare.attack,
        )
    )
}

/**
 * Appends the mix equalizer: one stage whose sections apply left to right.
 *
 * The same [EqBuilder] the oscillator's `.eq(...)` uses, so a section means the same thing on a
 * voice and on an orbit.
 *
 * @param configure receives the [EqBuilder] (knobs: `band`, `tap`) and returns it.
 */
@KlangScript.Function
fun KatalystBuilder.eq(configure: ((EqBuilder) -> EqBuilder)? = null): KatalystBuilder {
    // [EqBuilder] wraps the voice-side [IgnitorDsl.Eq] node, which needs an `inner` to filter.
    // A chain stage has no inner: it filters whatever the orbit hands it. So the builder gets
    // [IgnitorDsl.Silence] as a placeholder and only its SECTIONS travel into the stage.
    val built = EqBuilder(IgnitorDsl.Eq(inner = IgnitorDsl.Silence)).configuredBy("Katalyst eq", configure).node

    return plus(KatalystStageDsl.Eq(sections = built.sections))
}

/**
 * Appends make-up gain on the orbit: the group fader, after the inserts.
 *
 * **It APPENDS**, and `classic()` already brought one (its `gain.gain` slot at unity), so
 * `k.classic().gain(0.8)` is two faders in series and the engine multiplies them: `katp` at 0.5
 * makes 0.4. That is not a special case and nothing collapses them, which is the point of a stage
 * list. Writing `Katalyst.param("gain.gain", ...)` as the knob of a SECOND stage is the sharp
 * edge: both stages then read the same key, so one `katp("gain.gain", 0.5)` applies twice and the
 * orbit lands at 0.25. Give a second fader its own slot name if it should move on its own.
 *
 * @param gain linear gain factor (1.0 = unity, 2.0 is about +6 dB).
 */
@KlangScript.Function
fun KatalystBuilder.gain(gain: IgnitorDslLike = 1.0): KatalystBuilder =
    plus(KatalystStageDsl.Gain(gain = gain.toIgnitorDsl()))

// ── Body ─────────────────────────────────────────────────────────────────────

/** Builder for a [KatalystStageDsl.Body] stage. Knob: `floor`. */
data class KatalystBodyBuilder(val node: KatalystStageDsl.Body)

/**
 * Minimum dry share kept in the mix, 0 to 1 (default 0.4). Lower makes the body MORE audible: its
 * modes sit over less dry. Orbit twin: `body(floor = ...)`.
 */
@KlangScript.Function
fun KatalystBodyBuilder.floor(floor: IgnitorDslLike): KatalystBodyBuilder =
    copy(node = node.copy(floor = floor.toIgnitorDsl()))

// ── Vowel ────────────────────────────────────────────────────────────────────

/** Builder for a [KatalystStageDsl.Vowel] stage. Knob: `floor`. */
data class KatalystVowelBuilder(val node: KatalystStageDsl.Vowel)

/**
 * Minimum dry share kept between the formants, 0 to 1 (default 0.2). Much lower than the body's: a
 * vowel is a source strongly shaped by its formants. Orbit twin: `vowel(floor = ...)`.
 */
@KlangScript.Function
fun KatalystVowelBuilder.floor(floor: IgnitorDslLike): KatalystVowelBuilder =
    copy(node = node.copy(floor = floor.toIgnitorDsl()))

// ── Delay ────────────────────────────────────────────────────────────────────

/** Builder for a [KatalystStageDsl.Delay] stage. Knob: `cap`. */
data class KatalystDelayBuilder(val node: KatalystStageDsl.Delay)

/** Level the recirculating signal saturates toward (default 1.0). Orbit twin: `delay(cap = ...)`. */
@KlangScript.Function
fun KatalystDelayBuilder.cap(cap: IgnitorDslLike): KatalystDelayBuilder =
    copy(node = node.copy(cap = cap.toIgnitorDsl()))

// ── Phaser ───────────────────────────────────────────────────────────────────

/** Builder for a [KatalystStageDsl.Phaser] stage. Knob: `floor`. */
data class KatalystPhaserBuilder(val node: KatalystStageDsl.Phaser)

/** Minimum dry coefficient of the wet/dry law (default 1.0, purely additive). Orbit twin: `phaser(floor = ...)`. */
@KlangScript.Function
fun KatalystPhaserBuilder.floor(floor: IgnitorDslLike): KatalystPhaserBuilder =
    copy(node = node.copy(floor = floor.toIgnitorDsl()))
