/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:KlangScript.Library(KlangScriptLibraries.STDLIB)

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystParam
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.constants.AUTHORED_LIMITER_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.AUTHORED_LIMITER_LOOKAHEAD_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_RATIO
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_THRESHOLD_DB
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError

/*
 * Builders for a chain, on an orbit (`katalyst(...)`) or at the output (`master(...)`), one chain
 * type for both positions. `Katalyst(k => ...)` hands a [KatalystBuilder] to the lambda; every
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
 * fixed value of the chain, not a slot: only a slot (`Katalyst.param(...)`, `Katalyst.slot.*`) listens to the
 * orbit's `katp` state.
 *
 * The knobs use the same names and scales as their sprudel twins, so a number means the same on
 * an orbit and at the output. At the output nothing fills a slot (it stays at its default) and a
 * `duck` stage is inert.
 *
 * Every knob takes a number OR a Katalyst param (`IgnitorDslLike`, converted by [toKatalystKnob]). An
 * `Ignitor.param(...)` is a script error there; the reason is on [toKatalystKnob]. An expression over a param
 * (`Ignitor.param("room", 5).mul(2)`) is folded ONCE, when the chain is built, and never listens: hand the knob a
 * `Katalyst.param` and do the arithmetic on the pattern side. The chain reads its knobs once per block, so a signal-rate node on one is coerced, never rejected. The one
 * exception is the compressor's and the limiter's `lookahead`, a plain number fixed when the chain is built (it
 * sizes a delay ring).
 */

/**
 * Converts a Katalyst knob argument to the [IgnitorDsl] its stage carries: a number to a [IgnitorDsl.Constant], a
 * [KatalystParam] to its [KatalystParam.param] (the slot `katp` writes), any other [IgnitorDsl] as it is (the chain
 * folds it to a number once, when it is built). The one conversion of every Katalyst builder knob, the Katalyst `eq`
 * sections included.
 *
 * A BARE [IgnitorDsl.Param] is a [KlangScriptTypeError] (decision Q2 of `docs/plans/ignitor-katalyst-naming.md`,
 * 2026-10-03). THIS IS THE ONE HOME of the reason: an Ignitor param is the voice's slot by intent. Its author drives
 * it with `ignp`, which writes the voice's map and never reaches the chain, so on a chain the knob would sit at its
 * default while the song moves it. (The engine itself reads any `Param` on a chain from the orbit's `katp` state by
 * name; the type split is what tells the two apart.) Not a coercion, for the reason [ignitorSlotName] gives: the
 * wrong KIND of argument, which no clamp can make mean what the author wanted.
 *
 * An expression over a param (`Ignitor.param("room", 5).mul(2)`) is accepted and folded once at build, so it does not
 * listen: hand the knob a `Katalyst.param`. (Not refused: a tree walk for a `Param` would also trip on the
 * `Slots.analog` default every oscillator carries, so `k.phaser(rate = Ignitor.sine(0.2))` would be refused.)
 *
 * Anything that is neither a number, a Katalyst param nor a sound (a string, a lambda, an object ...) is a type error with
 * its own text: "a Katalyst knob takes a number, a Kat.param or a Kat.slot; got ...".
 *
 * Both errors are thrown without a source location (this function has no call site to give); the native-call
 * guard (`guardNativeCall` in klangscript) gives them the location of the stage call, so the editor marks
 * `k.reverb(...)`. Guard: `KlangScriptKatalystDoorParitySpec`, "a wrong knob value is reported at the stage call".
 */
fun IgnitorDslLike.toKatalystKnob(): IgnitorDsl = when (this) {
    is KatalystParam -> param
    is IgnitorDsl.Param -> throw KlangScriptTypeError("an Ignitor param in a Katalyst chain; use Kat.param", operation = "Katalyst knob")
    is IgnitorDsl -> this
    is Number -> IgnitorDsl.Constant(this.toDouble())
    else -> throw KlangScriptTypeError(
        "a Katalyst knob takes a number, a Kat.param or a Kat.slot; got ${describeArgument(this)}",
        operation = "Katalyst knob",
    )
}

// ── The chain ────────────────────────────────────────────────────────────────

/**
 * Builder for a [KatalystDsl] chain, handed to the `configure` lambda of `Katalyst(...)`. Knobs:
 * `classic`, `body`, `vowel`, `delay`, `reverb`, `phaser`, `compressor`, `limiter`, `distort`, `duck`, `eq`,
 * `gain`, each appending a stage (`limiter` appends a compressor with limiter numbers), `serial`,
 * which runs the builder through functions of stages in order, and `parallel`, which runs branches side by side and
 * sums them.
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
                material = catalogueIndex(material, bare.material, BodyMaterials::indexOf, knob = { it.toKatalystKnob() }),
                wet = wet?.toKatalystKnob() ?: bare.wet,
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
                vowel = catalogueIndex(vowel, bare.vowel, VowelBands::indexOf, knob = { it.toKatalystKnob() }),
                wet = wet?.toKatalystKnob() ?: bare.wet,
                floor = bare.floor,
            )
        ).configuredBy("Katalyst vowel", configure).node
    )
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
                wet = wet?.toKatalystKnob() ?: bare.wet,
                time = time?.toKatalystKnob() ?: bare.time,
                feedback = feedback?.toKatalystKnob() ?: bare.feedback,
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
            wet = wet?.toKatalystKnob() ?: bare.wet,
            size = size?.toKatalystKnob() ?: bare.size,
            lowpass = lowpass?.toKatalystKnob() ?: bare.lowpass,
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
                rate = rate?.toKatalystKnob() ?: bare.rate,
                wet = wet?.toKatalystKnob() ?: bare.wet,
                center = center?.toKatalystKnob() ?: bare.center,
                sweep = sweep?.toKatalystKnob() ?: bare.sweep,
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
 * @param lookahead how far ahead it sees, in seconds (default 0, off). The gain starts closing
 *   BEFORE a transient arrives, and `attack` becomes the gain-smoothing length (widen both
 *   together). The cost is latency: this orbit (at the output, this whole playback) runs late
 *   by `floor(lookahead * sampleRate)` frames (none below 8 frames, about 0.2 ms), and nothing
 *   compensates. A plain number fixed with the chain, at most 0.05. No orbit twin: declare it here.
 */
@KlangScript.Function
fun KatalystBuilder.compressor(
    threshold: IgnitorDslLike? = null,
    ratio: IgnitorDslLike? = null,
    knee: IgnitorDslLike? = null,
    attack: IgnitorDslLike? = null,
    release: IgnitorDslLike? = null,
    lookahead: Double? = null,
): KatalystBuilder {
    val bare = KatalystStageDsl.Compressor()

    return plus(
        KatalystStageDsl.Compressor(
            threshold = threshold?.toKatalystKnob() ?: bare.threshold,
            ratio = ratio?.toKatalystKnob() ?: bare.ratio,
            knee = knee?.toKatalystKnob() ?: bare.knee,
            attack = attack?.toKatalystKnob() ?: bare.attack,
            release = release?.toKatalystKnob() ?: bare.release,
            lookahead = lookahead ?: bare.lookahead,
        )
    )
}

/**
 * Appends a limiter: the compressor stage with limiter numbers, a ceiling rather than a squeeze.
 * Flat, like every dynamics stage, and in the compressor's parameter order. It is a preset, not a
 * stage of its own: what it appends IS a `compressor`, so a limiter and a compressor are one DSP
 * and one wire word.
 *
 * The limiter numbers live on the DOOR, and the stage does not know it was a limiter: a knob the
 * engine cannot read (a signal-rate node, or a slot whose value is unset or non-finite) falls back to the
 * COMPRESSOR's shared constant (threshold -20, ratio 4, knee 6, attack 0.003, release 0.1), not to
 * the limiter's. Write a slot's default as the number you want.
 *
 * ```
 * master(Katalyst(k => k.gain(1.5).limiter()))
 * katalyst(Katalyst(k => k.gain(1.5).limiter(lookahead = 0.005)))
 * ```
 *
 * @param threshold ceiling in dBFS (default -1). Orbit twin: `compressor(threshold = ...)`.
 * @param ratio compression ratio (default 20, about a brick wall). Orbit twin:
 *   `compressor(ratio = ...)`.
 * @param knee soft-knee width in dB (default 2). A hard corner injects harmonics on every crossing.
 *   Orbit twin: `compressor(knee = ...)`.
 * @param attack how fast the gain closes, in seconds (default 0.001). With no lookahead a one-pole
 *   attack: short keeps transient punch. With a lookahead the gain-smoothing length; widen both
 *   together. Orbit twin: `compressor(attack = ...)`.
 * @param release how fast the gain opens again, in seconds (default 0.1). Orbit twin:
 *   `compressor(release = ...)`.
 * @param lookahead how far ahead it sees, in seconds (default 0, off). Lets the limiter close the
 *   gain before a transient arrives instead of chasing it, which is what stops loud hits punching
 *   through. The cost is latency: this orbit (at the output, this whole playback) runs late by
 *   `floor(lookahead * sampleRate)` frames (none below 8 frames, about 0.2 ms), and nothing
 *   compensates. A plain number fixed with the chain, at most 0.05. No orbit twin: declare it here.
 */
@KlangScript.Function
fun KatalystBuilder.limiter(
    threshold: IgnitorDslLike? = null,
    ratio: IgnitorDslLike? = null,
    knee: IgnitorDslLike? = null,
    attack: IgnitorDslLike? = null,
    release: IgnitorDslLike? = null,
    lookahead: Double? = null,
): KatalystBuilder = plus(
    KatalystStageDsl.Compressor(
        threshold = threshold?.toKatalystKnob() ?: IgnitorDsl.Constant(LIMITER_THRESHOLD_DB),
        ratio = ratio?.toKatalystKnob() ?: IgnitorDsl.Constant(LIMITER_RATIO),
        knee = knee?.toKatalystKnob() ?: IgnitorDsl.Constant(LIMITER_KNEE_DB),
        attack = attack?.toKatalystKnob() ?: IgnitorDsl.Constant(AUTHORED_LIMITER_ATTACK_SECONDS),
        release = release?.toKatalystKnob() ?: IgnitorDsl.Constant(LIMITER_RELEASE_SECONDS),
        lookahead = lookahead ?: AUTHORED_LIMITER_LOOKAHEAD_SECONDS,
    )
)

/**
 * Appends a distortion of the bus mix: the voice's `distort` at the bus position, same knobs, same
 * scales, same shapes. A clipper before the master limiter is this stage with `soft` (or `hard`) and
 * a small [amount]; saturation is a gentle shape (`tube`, `softsat`) at a moderate one. Flat, as the
 * voice's door is.
 *
 * It bends the SUM: every note of the bus goes through one curve, so they intermodulate. That is
 * the glue a master saturator gives and the growl of an amp fed by a whole chord; pushed hard, chords
 * turn to mush.
 *
 * ```
 * master(Katalyst(k => k.gain(1.1).distort(0.15).limiter(threshold = -3.0)))   // clip, then limit
 * katalyst(Katalyst(k => k.distort(0.6, "tube").eq(e => e.band(freq = 2700, q = 2.0, db = 3.0))))
 * ```
 *
 * @param amount the drive into the shape, `10^(amount * 1.2)`: 0.1 is about +2.4 dB, 0.25 about
 *   +6 dB, 0.5 (the default) about +12 dB. At or below 0 the stage is off. A number, or a
 *   `Katalyst.param` slot that a pattern moves with `katp` (the change glides). No orbit twin:
 *   sprudel's `distort(...)` is the voice's door.
 * @param shape the waveshaper by name, from the voice's list (`soft`, `hard`, `tube`, `softsat`,
 *   `gentle`, ...); an unknown name is `soft`. Fixed with the chain.
 * @param oversample the oversampling factor (2, 4, 8; 0 or 1 is none). Fixed with the chain. It
 *   delays this orbit (at the output, the whole playback) by 4 frames at 2x and 6 at 4x and 8x, and
 *   on a whole mix today's oversampler also dulls the top (about -2 dB at 16 kHz and -4 dB at 20 kHz
 *   at 2x), so the default is 0, as on the voice.
 */
@KlangScript.Function
fun KatalystBuilder.distort(
    amount: IgnitorDslLike? = null,
    shape: String = "soft",
    oversample: Int = 0,
): KatalystBuilder {
    val bare = KatalystStageDsl.Distort()

    return plus(
        KatalystStageDsl.Distort(
            amount = amount?.toKatalystKnob() ?: bare.amount,
            shape = DistortionShapes.indexOf(shape).toInt(),
            oversample = oversample,
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
            orbit = orbit?.toKatalystKnob() ?: bare.orbit,
            depth = depth?.toKatalystKnob() ?: bare.depth,
            attack = attack?.toKatalystKnob() ?: bare.attack,
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
    val built = EqBuilder(IgnitorDsl.Eq(inner = IgnitorDsl.Silence), onKatalyst = true).configuredBy("Katalyst eq", configure).node

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
    plus(KatalystStageDsl.Gain(gain = gain.toKatalystKnob()))

/**
 * Runs the chain through [stages] in series, in the order written: `k.serial(a, b, c)` is `c(b(a(k)))`, the same
 * chain as the nested calls. A stage is any function from a builder to a builder, so a group of stages
 * (a room, a bus, a mastering block) becomes a value and a chain is written as the list it is.
 * With no stage, `serial()` returns the chain as it is.
 *
 * ```KlangScript
 * let hall    = k => k.reverb(0.25, 7, 4500)
 * let ceiling = k => k.gain(1.4).limiter(threshold = -3.0, lookahead = 0.005)
 * Katalyst(k => k.serial(hall, ceiling))
 * ```
 *
 * One stage into the next, as `Ignitor`'s `serial`. Not sprudel's `apply(f, g)`, which stacks the
 * results side by side. Every stage is checked on the way: a stage that is null, returns nothing or returns
 * something other than the builder is a script error naming the stage; a stage that is not a function at all is
 * refused at the call ("expected a function, got a number").
 *
 * @param stages functions from a builder to a builder, applied first to last.
 */
@KlangScript.Function
fun KatalystBuilder.serial(vararg stages: (KatalystBuilder) -> KatalystBuilder): KatalystBuilder =
    runSerialStages("Katalyst serial", this, stages, returns = "builder", example = "k => k.gain(0.8)") { it is KatalystBuilder }

/**
 * Runs the bus through [branches] side by side, from this position, and SUMS them: the twin of [serial]. Each branch
 * is a function from a builder to a builder and receives an EMPTY one, the bus at this point, so a branch is the chain
 * of stages it appends; a branch that appends nothing is the dry bus.
 *
 * ```KlangScript
 * // parallel distortion: the dry bus and a distorted copy, a quarter of its level
 * Katalyst(k => k.parallel(dry => dry, wet => wet.distort(0.5).gain(0.25)))
 * ```
 *
 * The sum is plain (two identical branches are twice the level, +6 dB); a branch's own `gain` sets the blend. A `reverb`
 * or `delay` adds its return on top of the dry it is fed, so a branch with one carries the dry as well. A branch
 * that delays the bus (a compressor's lookahead, an oversampled distort) is matched by delaying the others, so the sum
 * does not comb. With no branch, `parallel()` returns the chain as it is; with one, it appends that branch's stages in
 * place, as written: a `classic()` in a branch is that branch's classic block, even next to one outside it (the
 * at-most-once rule of [classic] holds per builder, and a branch is a builder of its own). A `duck` inside a branch is
 * the orbit's duck, as anywhere in the chain. Every branch is checked like a stage of
 * [serial]: one that is null, returns nothing or returns something other than the builder is a script error naming
 * it.
 *
 * @param branches functions from a builder to a builder, each given an empty one.
 */
@KlangScript.Function
fun KatalystBuilder.parallel(vararg branches: (KatalystBuilder) -> KatalystBuilder): KatalystBuilder {
    val built = branches.mapIndexed { index, branch ->
        runStage<KatalystBuilder, KatalystBuilder>(
            door = "Katalyst parallel",
            noun = "branch",
            index = index,
            stage = branch,
            input = KatalystBuilder(KatalystDsl(emptyList())),
            returns = "builder",
            example = "b => b.distort(0.5)",
            isResult = { it is KatalystBuilder },
        ).node
    }

    return when (built.size) {
        0 -> this
        1 -> copy(node = KatalystDsl(node.stages + built[0].stages))
        else -> plus(KatalystStageDsl.Parallel(branches = built))
    }
}

// ── Body ─────────────────────────────────────────────────────────────────────

/** Builder for a [KatalystStageDsl.Body] stage. Knob: `floor`. */
data class KatalystBodyBuilder(val node: KatalystStageDsl.Body)

/**
 * Minimum dry share kept in the mix, 0 to 1 (default 0.4). Lower makes the body MORE audible: its
 * modes sit over less dry. Orbit twin: `body(floor = ...)`.
 */
@KlangScript.Function
fun KatalystBodyBuilder.floor(floor: IgnitorDslLike): KatalystBodyBuilder =
    copy(node = node.copy(floor = floor.toKatalystKnob()))

// ── Vowel ────────────────────────────────────────────────────────────────────

/** Builder for a [KatalystStageDsl.Vowel] stage. Knob: `floor`. */
data class KatalystVowelBuilder(val node: KatalystStageDsl.Vowel)

/**
 * Minimum dry share kept between the formants, 0 to 1 (default 0.2). Much lower than the body's: a
 * vowel is a source strongly shaped by its formants. Orbit twin: `vowel(floor = ...)`.
 */
@KlangScript.Function
fun KatalystVowelBuilder.floor(floor: IgnitorDslLike): KatalystVowelBuilder =
    copy(node = node.copy(floor = floor.toKatalystKnob()))

// ── Delay ────────────────────────────────────────────────────────────────────

/** Builder for a [KatalystStageDsl.Delay] stage. Knob: `cap`. */
data class KatalystDelayBuilder(val node: KatalystStageDsl.Delay)

/** Level the recirculating signal saturates toward (default 1.0). Orbit twin: `delay(cap = ...)`. */
@KlangScript.Function
fun KatalystDelayBuilder.cap(cap: IgnitorDslLike): KatalystDelayBuilder =
    copy(node = node.copy(cap = cap.toKatalystKnob()))

// ── Phaser ───────────────────────────────────────────────────────────────────

/** Builder for a [KatalystStageDsl.Phaser] stage. Knob: `floor`. */
data class KatalystPhaserBuilder(val node: KatalystStageDsl.Phaser)

/** Minimum dry coefficient of the wet/dry law (default 1.0, purely additive). Orbit twin: `phaser(floor = ...)`. */
@KlangScript.Function
fun KatalystPhaserBuilder.floor(floor: IgnitorDslLike): KatalystPhaserBuilder =
    copy(node = node.copy(floor = floor.toKatalystKnob()))
