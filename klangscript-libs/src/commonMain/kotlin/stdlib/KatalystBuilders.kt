/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:KlangScript.Library(KlangScriptLibraries.STDLIB)

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystBandsBuilder
import io.peekandpoke.klang.audio_bridge.KatalystBodyBuilder
import io.peekandpoke.klang.audio_bridge.KatalystBuilder
import io.peekandpoke.klang.audio_bridge.KatalystDelayBuilder
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystEqBuilder
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystPhaserBuilder
import io.peekandpoke.klang.audio_bridge.KatalystRampBuilder
import io.peekandpoke.klang.audio_bridge.KatalystSawBuilder
import io.peekandpoke.klang.audio_bridge.KatalystSineBuilder
import io.peekandpoke.klang.audio_bridge.KatalystSquareBuilder
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.KatalystTriBuilder
import io.peekandpoke.klang.audio_bridge.KatalystVowelBuilder
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.abs
import io.peekandpoke.klang.audio_bridge.constants.AUTHORED_LIMITER_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.AUTHORED_LIMITER_LOOKAHEAD_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.BODY_WET
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_LOOKAHEAD_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RATIO
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_THRESHOLD_DB
import io.peekandpoke.klang.audio_bridge.constants.DELAY_FEEDBACK
import io.peekandpoke.klang.audio_bridge.constants.DELAY_TIME_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DELAY_WET
import io.peekandpoke.klang.audio_bridge.constants.DISTORT_AMOUNT
import io.peekandpoke.klang.audio_bridge.constants.DUCK_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DUCK_DEPTH
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_RATIO
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_THRESHOLD_DB
import io.peekandpoke.klang.audio_bridge.constants.PHASER_CENTER_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_RATE_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_SWEEP_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_WET
import io.peekandpoke.klang.audio_bridge.constants.REVERB_SIZE
import io.peekandpoke.klang.audio_bridge.constants.REVERB_WET
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_WET
import io.peekandpoke.klang.audio_bridge.div
import io.peekandpoke.klang.audio_bridge.eqSectionKnobOrNull
import io.peekandpoke.klang.audio_bridge.kat
import io.peekandpoke.klang.audio_bridge.max
import io.peekandpoke.klang.audio_bridge.min
import io.peekandpoke.klang.audio_bridge.minus
import io.peekandpoke.klang.audio_bridge.neg
import io.peekandpoke.klang.audio_bridge.plus
import io.peekandpoke.klang.audio_bridge.pow
import io.peekandpoke.klang.audio_bridge.range
import io.peekandpoke.klang.audio_bridge.times
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
 * Every knob takes a number, a Katalyst param or a Katalyst expression over params and modulators
 * (`Kat.param("fader", 1).times(0.5)`, `Kat.sine(0.1).range(400, 1600)`; `IgnitorDslLike`, converted by
 * [toKatalystKnob]) and nothing else: any Ignitor node, an `Ignitor.param(...)` included, is a script error at the stage
 * call; the reason is on [toKatalystKnob]. The chain reads its knobs once per block. The exceptions are the knobs fixed
 * when the chain is built, plain numbers or names: the compressor's and the limiter's `lookahead` (it sizes a delay
 * ring) and `distort`'s `shape` and `oversample`; the `eq` sections, whose knobs take a number or a param only
 * ([toEqSectionKatalystKnob], over `eqSectionKnobOrNull` in `audio_bridge`).
 *
 * The builders themselves ([KatalystBuilder], its stage builders and the modulators' builders) and the knob arithmetic
 * live in `audio_bridge` (`KatalystBuilders.kt`, `KatalystKnobDsl.kt` there, since the typed-doors rollout S5) with
 * TYPED members, the one Kotlin door. Every script knob below is an `internal`, renamed variant (`scriptReverb`, the
 * script word in `name = "reverb"`) that converts its arguments once ([toKatalystKnob]) and calls the member; an
 * omitted (`null`) argument becomes the member's own default SYMBOL (`REVERB_WET.kat`), never a retyped literal
 * (`/dsl-design` section 3, the typed-door pattern). `serial`, `parallel`, `blend` and `bands` are typed members too
 * since rollout S6; their variants wrap every script stage, branch and band processor in its check ([checkedStage])
 * and keep the `blend` `wet` check script-side.
 */

/**
 * Converts a Katalyst knob argument to the [KatalystKnobDsl] its stage carries: a number to a
 * [KatalystKnobDsl.Constant], a Katalyst knob (`Kat.param(...)`, `Kat.slot.*`, an expression over them such as
 * `Kat.param("beat", 0.5).div(4)`) as it is. The one conversion of every Katalyst builder knob and of every operand of
 * a knob expression, the Katalyst `eq` sections included (they map the result onto their Ignitor section and refuse
 * an expression, see [toEqSectionKatalystKnob]).
 *
 * ANY [IgnitorDsl] is a [KlangScriptTypeError]: the Katalyst has its own knob language (decision D1 of
 * `docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md`, `docs/plans/future/signal-graph-engine.md` §6.10), and an
 * Ignitor node
 * means nothing on a bus knob (an oscillator has no bus value, a note frequency no bus note). Until 2026-10-12 such a
 * node was accepted and folded to one number when the chain was built; the type split removed that coercion.
 *
 * A BARE [IgnitorDsl.Param] keeps its own message (decision Q2 of `docs/tasks-archive/2026-10/20261004-ignitor-katalyst-naming.md`,
 * 2026-10-03). THIS IS THE ONE HOME of the reason: an Ignitor param is the voice's slot by intent. Its author drives
 * it with `ignp`, which writes the voice's map and never reaches the chain, so on a chain the knob would sit at its
 * default while the song moves it; the fix is `Kat.param`. Not a coercion, for the reason [ignitorSlotName] gives:
 * the wrong KIND of argument, which no clamp can make mean what the author wanted.
 *
 * Anything else (another Ignitor node, a string, a lambda, an object ...) is a type error with its own text: "a
 * Katalyst knob takes a number, a Kat.param or a Kat expression; got ...".
 *
 * Both errors are thrown without a source location (this function has no call site to give); the native-call
 * guard (`guardNativeCall` in klangscript) gives them the location of the stage call, so the editor marks
 * `k.reverb(...)`. Guard: `KlangScriptKatalystDoorParitySpec`, "a wrong knob value is reported at the stage call".
 */
fun IgnitorDslLike.toKatalystKnob(): KatalystKnobDsl = when (this) {
    is KatalystKnobDsl -> this
    is Number -> KatalystKnobDsl.Constant(this.toDouble())
    is IgnitorDsl.Param -> throw KlangScriptTypeError("an Ignitor param in a Katalyst chain; use Kat.param", operation = "Katalyst knob")
    else -> throw KlangScriptTypeError(
        "a Katalyst knob takes a number, a Kat.param or a Kat expression; got ${describeArgument(this)}",
        operation = "Katalyst knob",
    )
}

/**
 * A Katalyst `eq` section's script argument as its knob: converted like every chain knob ([toKatalystKnob]), then
 * PRE-CHECKED against the one section helper (`eqSectionKnobOrNull` in `audio_bridge`, with the reason for the
 * recorded asymmetry): a Katalyst expression is a [KlangScriptTypeError] here, with the script's own words ("a
 * Katalyst eq section takes a number or a Kat.param; got a Kat expression"), before the typed builder would refuse
 * it at build time (Q49 of `docs/tasks/kotlin-typed-doors.md`; `/dsl-design` section 3, the typed-door pattern,
 * point 5).
 */
internal fun IgnitorDslLike.toEqSectionKatalystKnob(): KatalystKnobDsl = toKatalystKnob().also { knob ->
    if (knob.eqSectionKnobOrNull() == null) {
        throw KlangScriptTypeError(
            "a Katalyst eq section takes a number or a Kat.param; got a Kat expression",
            operation = "Katalyst eq",
        )
    }
}

// ── The chain ────────────────────────────────────────────────────────────────

/*
 * [KatalystBuilder] (`audio_bridge`) is handed to the `configure` lambda of `Katalyst(...)`. Knobs:
 * `classic`, `body`, `vowel`, `delay`, `reverb`, `phaser`, `compressor`, `limiter`, `distort`, `duck`, `eq`,
 * `gain`, the arithmetic of the Ignitor (`plus`, `minus`, `times`, `div`, `neg`, `abs`, `pow`, `min`, `max`, with its
 * aliases), each appending a stage (`limiter` appends a compressor with limiter numbers), `serial`,
 * which runs the builder through functions of stages in order, `parallel`, which runs branches side by side and
 * sums them, and `bands`, which splits the bus into frequency bands.
 */

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
@KlangScript.Function(name = "classic")
internal fun KatalystBuilder.scriptClassic(): KatalystBuilder = this.classic()

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
@KlangScript.Function(name = "body")
internal fun KatalystBuilder.scriptBody(
    wet: IgnitorDslLike? = null,
    material: IgnitorDslLike? = null,
    configure: ((KatalystBodyBuilder) -> KatalystBodyBuilder)? = null,
): KatalystBuilder = this.body(
    material = catalogueIndex(material, SLOT_UNSET.kat, BodyMaterials::indexOf, constant = { it.kat }, knob = { it.toKatalystKnob() }),
    wet = wet?.toKatalystKnob() ?: BODY_WET.kat,
) { it.configuredBy("Katalyst body", configure) }

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
@KlangScript.Function(name = "vowel")
internal fun KatalystBuilder.scriptVowel(
    wet: IgnitorDslLike? = null,
    vowel: IgnitorDslLike? = null,
    configure: ((KatalystVowelBuilder) -> KatalystVowelBuilder)? = null,
): KatalystBuilder = this.vowel(
    vowel = catalogueIndex(vowel, SLOT_UNSET.kat, VowelBands::indexOf, constant = { it.kat }, knob = { it.toKatalystKnob() }),
    wet = wet?.toKatalystKnob() ?: VOWEL_WET.kat,
) { it.configuredBy("Katalyst vowel", configure) }

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
@KlangScript.Function(name = "delay")
internal fun KatalystBuilder.scriptDelay(
    wet: IgnitorDslLike? = null,
    time: IgnitorDslLike? = null,
    feedback: IgnitorDslLike? = null,
    configure: ((KatalystDelayBuilder) -> KatalystDelayBuilder)? = null,
): KatalystBuilder = this.delay(
    wet = wet?.toKatalystKnob() ?: DELAY_WET.kat,
    time = time?.toKatalystKnob() ?: DELAY_TIME_SECONDS.kat,
    feedback = feedback?.toKatalystKnob() ?: DELAY_FEEDBACK.kat,
) { it.configuredBy("Katalyst delay", configure) }

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
@KlangScript.Function(name = "reverb")
internal fun KatalystBuilder.scriptReverb(
    wet: IgnitorDslLike? = null,
    size: IgnitorDslLike? = null,
    lowpass: IgnitorDslLike? = null,
): KatalystBuilder = this.reverb(
    wet = wet?.toKatalystKnob() ?: REVERB_WET.kat,
    size = size?.toKatalystKnob() ?: REVERB_SIZE.kat,
    // `null` is the bare stage's own value here (the engine's damping), not a stand-in for a default.
    lowpass = lowpass?.toKatalystKnob(),
)

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
@KlangScript.Function(name = "phaser")
internal fun KatalystBuilder.scriptPhaser(
    wet: IgnitorDslLike? = null,
    rate: IgnitorDslLike? = null,
    center: IgnitorDslLike? = null,
    sweep: IgnitorDslLike? = null,
    configure: ((KatalystPhaserBuilder) -> KatalystPhaserBuilder)? = null,
): KatalystBuilder = this.phaser(
    rate = rate?.toKatalystKnob() ?: PHASER_RATE_HZ.kat,
    wet = wet?.toKatalystKnob() ?: PHASER_WET.kat,
    center = center?.toKatalystKnob() ?: PHASER_CENTER_HZ.kat,
    sweep = sweep?.toKatalystKnob() ?: PHASER_SWEEP_HZ.kat,
) { it.configuredBy("Katalyst phaser", configure) }

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
@KlangScript.Function(name = "compressor")
internal fun KatalystBuilder.scriptCompressor(
    threshold: IgnitorDslLike? = null,
    ratio: IgnitorDslLike? = null,
    knee: IgnitorDslLike? = null,
    attack: IgnitorDslLike? = null,
    release: IgnitorDslLike? = null,
    lookahead: Double? = null,
): KatalystBuilder = this.compressor(
    threshold = threshold?.toKatalystKnob() ?: COMPRESSOR_THRESHOLD_DB.kat,
    ratio = ratio?.toKatalystKnob() ?: COMPRESSOR_RATIO.kat,
    knee = knee?.toKatalystKnob() ?: COMPRESSOR_KNEE_DB.kat,
    attack = attack?.toKatalystKnob() ?: COMPRESSOR_ATTACK_SECONDS.kat,
    release = release?.toKatalystKnob() ?: COMPRESSOR_RELEASE_SECONDS.kat,
    lookahead = lookahead ?: COMPRESSOR_LOOKAHEAD_SECONDS,
)

/**
 * Appends a limiter: the compressor stage with limiter numbers, a ceiling rather than a squeeze.
 * Flat, like every dynamics stage, and in the compressor's parameter order. It is a preset, not a
 * stage of its own: what it appends IS a `compressor`, so a limiter and a compressor are one DSP
 * and one wire word.
 *
 * The limiter numbers live on the DOOR, and the stage does not know it was a limiter: a knob the
 * engine cannot read (a slot whose value is unset or non-finite) falls back to the
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
@KlangScript.Function(name = "limiter")
internal fun KatalystBuilder.scriptLimiter(
    threshold: IgnitorDslLike? = null,
    ratio: IgnitorDslLike? = null,
    knee: IgnitorDslLike? = null,
    attack: IgnitorDslLike? = null,
    release: IgnitorDslLike? = null,
    lookahead: Double? = null,
): KatalystBuilder = this.limiter(
    threshold = threshold?.toKatalystKnob() ?: LIMITER_THRESHOLD_DB.kat,
    ratio = ratio?.toKatalystKnob() ?: LIMITER_RATIO.kat,
    knee = knee?.toKatalystKnob() ?: LIMITER_KNEE_DB.kat,
    attack = attack?.toKatalystKnob() ?: AUTHORED_LIMITER_ATTACK_SECONDS.kat,
    release = release?.toKatalystKnob() ?: LIMITER_RELEASE_SECONDS.kat,
    lookahead = lookahead ?: AUTHORED_LIMITER_LOOKAHEAD_SECONDS,
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
 *   delays this orbit (at the output, the whole playback) by 3 frames at 2x, 4 at 4x and 5 at 8x. The
 *   default is 2x, unlike the voice's 0: a bus bends the whole mix, so its aliasing lands on every note.
 *   `oversample = 1` keeps the raw aliased character.
 */
@KlangScript.Function(name = "distort")
internal fun KatalystBuilder.scriptDistort(
    amount: IgnitorDslLike? = null,
    shape: String = "soft",
    oversample: Int = 2,
): KatalystBuilder = this.distort(amount = amount?.toKatalystKnob() ?: DISTORT_AMOUNT.kat, shape = shape, oversample = oversample)

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
@KlangScript.Function(name = "duck")
internal fun KatalystBuilder.scriptDuck(
    orbit: IgnitorDslLike? = null,
    depth: IgnitorDslLike? = null,
    attack: IgnitorDslLike? = null,
): KatalystBuilder = this.duck(
    orbit = orbit?.toKatalystKnob() ?: SLOT_UNSET.kat,
    depth = depth?.toKatalystKnob() ?: DUCK_DEPTH.kat,
    attack = attack?.toKatalystKnob() ?: DUCK_ATTACK_SECONDS.kat,
)

/**
 * Appends the mix equalizer: one stage whose sections apply left to right.
 *
 * The voice eq's words, defaults and sections on a [KatalystEqBuilder], so a section means the same thing on a
 * voice and on an orbit; its knobs take a number or a Katalyst param.
 *
 * @param configure receives the [KatalystEqBuilder] (knobs: `band`, `tap`) and returns it.
 */
@KlangScript.Function(name = "eq")
internal fun KatalystBuilder.scriptEq(configure: ((KatalystEqBuilder) -> KatalystEqBuilder)? = null): KatalystBuilder =
    this.eq { it.configuredBy("Katalyst eq", configure) }

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
@KlangScript.Function(name = "gain")
internal fun KatalystBuilder.scriptGain(gain: IgnitorDslLike = 1.0): KatalystBuilder = this.gain(gain.toKatalystKnob())

// ── Arithmetic: the Ignitor's operators on the bus ──────────────────────────────────────────────
//
// Each door appends one stage that runs the Ignitor's per-sample law of the same operator over the bus, so the same
// numbers give the same sample on a voice and on a bus (`docs/tasks-archive/2026-10/20261011-katalyst-arithmetic.md`).
// The operand is a knob
// like every other (a number, a `Kat.param` or a Kat expression); a moving one glides, an unset (non-finite) one reads
// as the
// operator's identity, and an unset cap or floor passes the bus. A stage that turns silence into something (an offset,
// a floor above zero, a cap below it, a negative power) keeps its orbit from ever switching off, as the voice's
// arithmetic would; the doors say which.

/**
 * Adds [other] to every sample of the bus. A constant is a DC offset: before a `distort` it clips the two halves of
 * the wave differently. The output's DC blocker removes what reaches it, but an orbit whose mix never falls silent
 * never switches off, so an offset keeps its orbit running.
 *
 * @param other a number, a `Kat.param` or a Kat expression.
 * @alias add
 */
@KlangScript.Function(name = "plus")
internal fun KatalystBuilder.scriptPlus(other: IgnitorDslLike): KatalystBuilder = this.plus(other.toKatalystKnob())

/**
 * Adds [other] to every sample of the bus. Alias for [plus].
 *
 * @param other a number, a `Kat.param` or a Kat expression.
 * @alias plus
 */
@KlangScript.Function(name = "add")
internal fun KatalystBuilder.scriptAdd(other: IgnitorDslLike): KatalystBuilder = this.plus(other.toKatalystKnob())

/**
 * Subtracts [other] from every sample of the bus. A constant is a DC offset, as with [plus], and keeps its orbit
 * running.
 *
 * @param other a number, a `Kat.param` or a Kat expression.
 * @alias sub
 */
@KlangScript.Function(name = "minus")
internal fun KatalystBuilder.scriptMinus(other: IgnitorDslLike): KatalystBuilder = this.minus(other.toKatalystKnob())

/**
 * Subtracts [other] from every sample of the bus. Alias for [minus].
 *
 * @param other a number, a `Kat.param` or a Kat expression.
 * @alias minus
 */
@KlangScript.Function(name = "sub")
internal fun KatalystBuilder.scriptSub(other: IgnitorDslLike): KatalystBuilder = this.minus(other.toKatalystKnob())

/**
 * Multiplies every sample of the bus by [other], exactly as the Ignitor's `times`. For a level, `gain` is the word:
 * the fader glides the same way but does not clamp and skips unity; this is the arithmetic.
 *
 * @param other a number, a `Kat.param` or a Kat expression.
 * @alias mul
 */
@KlangScript.Function(name = "times")
internal fun KatalystBuilder.scriptTimes(other: IgnitorDslLike): KatalystBuilder = this.times(other.toKatalystKnob())

/**
 * Multiplies every sample of the bus by [other]. Alias for [times].
 *
 * @param other a number, a `Kat.param` or a Kat expression.
 * @alias times
 */
@KlangScript.Function(name = "mul")
internal fun KatalystBuilder.scriptMul(other: IgnitorDslLike): KatalystBuilder = this.times(other.toKatalystKnob())

/**
 * Divides every sample of the bus by [other]. A divisor of exactly zero gives silence, as on the Ignitor. A moving
 * divisor glides as a level (`1 / other`), so a move through zero passes through silence instead of a huge gain.
 *
 * @param other a number, a `Kat.param` or a Kat expression.
 */
@KlangScript.Function(name = "div")
internal fun KatalystBuilder.scriptDiv(other: IgnitorDslLike): KatalystBuilder = this.div(other.toKatalystKnob())

/**
 * Flips the polarity of the bus: a multiply by -1, as the Ignitor's `neg`.
 *
 * @alias negate
 */
@KlangScript.Function(name = "neg")
internal fun KatalystBuilder.scriptNeg(): KatalystBuilder = this.neg()

/**
 * Flips the polarity of the bus. Alias for [neg].
 *
 * @alias neg
 */
@KlangScript.Function(name = "negate")
internal fun KatalystBuilder.scriptNegate(): KatalystBuilder = this.neg()

/**
 * The absolute value of every sample: full-wave rectification. On a tone it sounds an octave up, with a DC offset that
 * the output removes.
 *
 * ```KlangScript
 * Katalyst(k => k.abs().distort(0.4))
 * ```
 */
@KlangScript.Function(name = "abs")
internal fun KatalystBuilder.scriptAbs(): KatalystBuilder = this.abs()

/**
 * Raises every sample of the bus to the power of [exp], keeping its sign (a negative sample gives `-(|x|^exp)`), as
 * the Ignitor's `pow`. Above 1 the quiet parts sink, below 1 they rise. A negative [exp] turns every silent sample
 * into the clamp's ceiling (1e15, the house limiter's business), so it belongs after something that never rests at
 * zero, and it keeps its orbit running.
 *
 * @param exp a number, a `Kat.param` or a Kat expression.
 * @alias power
 */
@KlangScript.Function(name = "pow")
internal fun KatalystBuilder.scriptPow(exp: IgnitorDslLike): KatalystBuilder = this.pow(exp.toKatalystKnob())

/**
 * Raises every sample of the bus to the power of [exp]. Alias for [pow].
 *
 * @param exp a number, a `Kat.param` or a Kat expression.
 * @alias pow
 */
@KlangScript.Function(name = "power")
internal fun KatalystBuilder.scriptPower(exp: IgnitorDslLike): KatalystBuilder = this.pow(exp.toKatalystKnob())

/**
 * The bus, but at least [other] per sample: anything below it is raised to it, the same meaning `min` carries on a
 * number, a pattern and a signal. A floor is the per-sample MAXIMUM, so this door builds the `Max` stage. Do not
 * "correct" the crossing. A floor above zero turns silence into DC and keeps its orbit running.
 *
 * @param other a number, a `Kat.param` or a Kat expression.
 */
@KlangScript.Function(name = "min")
internal fun KatalystBuilder.scriptMin(other: IgnitorDslLike): KatalystBuilder = this.min(other.toKatalystKnob())

/**
 * The bus, but at most [other] per sample: anything above it is lowered to it, a hard cap on the positive half. A cap
 * is the per-sample MINIMUM, so this door builds the `Min` stage. Do not "correct" the crossing. A cap below zero
 * turns silence into DC and keeps its orbit running.
 *
 * ```KlangScript
 * Katalyst(k => k.max(0.5).min(-0.5))   // hard clip at half scale
 * ```
 *
 * @param other a number, a `Kat.param` or a Kat expression.
 */
@KlangScript.Function(name = "max")
internal fun KatalystBuilder.scriptMax(other: IgnitorDslLike): KatalystBuilder = this.max(other.toKatalystKnob())

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
@KlangScript.Function(name = "serial")
internal fun KatalystBuilder.scriptSerial(vararg stages: (KatalystBuilder) -> KatalystBuilder): KatalystBuilder = this.serial(
    *checkedStages(door = "Katalyst serial", noun = "stage", stages = stages, example = "k => k.gain(0.8)"),
)

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
 * that delays the bus (a compressor's lookahead, an oversampled distort) is matched in phase by the others, so the sum
 * does not comb. With no branch, `parallel()` returns the chain as it is; with one, it appends that branch's stages in
 * place, as written: a `classic()` in a branch is that branch's classic block, even next to one outside it (the
 * at-most-once rule of [classic] holds per builder, and a branch is a builder of its own). A `duck` inside a branch is
 * the orbit's duck, as anywhere in the chain. Every branch is checked like a stage of
 * [serial]: one that is null, returns nothing or returns something other than the builder is a script error naming
 * it.
 *
 * @param branches functions from a builder to a builder, each given an empty one.
 */
@KlangScript.Function(name = "parallel")
internal fun KatalystBuilder.scriptParallel(vararg branches: (KatalystBuilder) -> KatalystBuilder): KatalystBuilder = this.parallel(
    *checkedStages(door = "Katalyst parallel", noun = "branch", stages = branches, example = "b => b.distort(0.5)"),
)

/**
 * A dry/wet blend of the bus, the linear law: `k.blend(wet, f)` sums the bus times `1 - wet` with [branch]'s stages
 * times `wet` (a `parallel` of two). The branch receives an empty builder, the bus at this position, as a `parallel`
 * branch does. `wet` comes first, as on every door with one.
 *
 * ```KlangScript
 * // a third of a crushed copy of the bus under the clean one
 * Katalyst(k => k.blend(0.3, b => b.distort(0.6, "hard")))
 * ```
 *
 * Linear is right for a branch correlated with the dry (distortion, compression, filters). A `reverb` or `delay` in the
 * branch carries the dry as well (it adds its return on top), so `blend(w, b => b.reverb(1, 7))` is the dry plus `w`
 * of the room. [wet] is a plain number here, where the Ignitor's may be a slot or a signal (recorded asymmetry):
 * `1 - wet` needed arithmetic a Katalyst param did not have until step 4 of
 * `docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md`, and taking a `Kat.param` or a Kat expression here is its
 * follow-up Q14 (`docs/tasks/katalyst-knob-followups.md` section 4). A number that is not finite reads as 0. Any
 * Ignitor node
 * is refused, `Ignitor.constant(...)` included (it was accepted until step 3 of
 * `docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md`,
 * 2026-10-12, when the Katalyst knobs stopped taking Ignitor nodes).
 *
 * @param wet the share of the branch, 0 to 1.
 * @param branch a function from a builder to a builder, given an empty one.
 */
@KlangScript.Function(name = "blend")
internal fun KatalystBuilder.scriptBlend(wet: IgnitorDslLike, branch: (KatalystBuilder) -> KatalystBuilder): KatalystBuilder {
    // The script-side check (the typed-door pattern, point 5): a plain number, or a Kat constant holding one.
    val number = when (wet) {
        is Number -> wet.toDouble()
        is KatalystKnobDsl.Constant -> wet.value
        else -> throw KlangScriptTypeError(
            message = "wet is a plain number here (a Katalyst param or a signal as wet is not built yet); " +
                    "got ${describeArgument(wet)}",
            operation = "Katalyst blend",
        )
    }

    return this.blend(
        wet = number,
        branch = checkedStage(
            door = "Katalyst blend",
            noun = "branch",
            index = 0,
            stage = branch,
            returns = "builder",
            example = "b => b.distort(0.5)",
            isResult = { it is KatalystBuilder },
        ),
    )
}

/**
 * Splits the bus into frequency BANDS from this position, processes each band on its own and sums them again: the
 * master's distortion on the mids only, a compressor on the lows. Read from the bottom up:
 *
 * ```KlangScript
 * // glue the mids, leave the kick and the hats alone
 * Katalyst(k => k.bands(b => b.cut(150).band(mid => mid.distort(0.15)).cut(5000)).limiter())
 * ```
 *
 * `band(f)` adds a processor to the band being written, a function of stages from an empty builder, as a `parallel`
 * branch (two on one band are summed); `cut(freq)` closes the band and starts the next one up; a band with no
 * `band()` passes untouched, and a cut below the one before it is moved up to it. The crossover is Linkwitz-Riley: with
 * nothing processed the bands sum to flat level, with the phase turned around each cut. A late band is matched by
 * delaying the others. With no `configure`, or no `cut`, it is the one band, its stages in place.
 *
 * @param configure receives the bands builder and returns it.
 */
@KlangScript.Function(name = "bands")
internal fun KatalystBuilder.scriptBands(configure: ((KatalystBandsBuilder) -> KatalystBandsBuilder)? = null): KatalystBuilder =
    this.bands { it.configuredBy("Katalyst bands", configure) }

/** Every script-supplied stage or branch of a Katalyst `serial` / `parallel`, wrapped in its check ([checkedStage]). */
private fun checkedStages(
    door: String,
    noun: String,
    stages: Array<out (KatalystBuilder) -> KatalystBuilder>,
    example: String,
): Array<(KatalystBuilder) -> KatalystBuilder> = Array(stages.size) { index ->
    checkedStage(
        door = door,
        noun = noun,
        index = index,
        stage = stages[index],
        returns = "builder",
        example = example,
        isResult = { it is KatalystBuilder },
    )
}

// ── Knob expressions: the Ignitor's arithmetic on a Katalyst knob ─────────────────────────────────────────────────
//
// Step 4 of `docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md` (decision D4: the same words, laws and clamps as the
// Ignitor's arithmetic, plus `range`). Each door wraps the knob it is called on in one [KatalystKnobDsl] node; an
// operand is converted by [toKatalystKnob], so it is a number (a [KatalystKnobDsl.Constant]), a `Kat.param` or another
// expression, and an Ignitor node is the same script error as on a stage knob. The engine evaluates the expression
// with the Ignitor's own laws whenever the orbit's `katp` state changes, and once per block when it holds a modulator
// (`Kat.sine` and the rest, step 5b); a slot leaf that reads a non-finite value makes the whole knob unset (its stage
// takes its constant). One implementation for both doors: the typed functions in `audio_bridge` (`KatalystKnobDsl.kt`,
// since the typed-doors rollout S5), which a Kotlin song calls directly (`Kat.param("fader", 1.0).times(0.5.kat)`); the
// script words below are `internal`, renamed variants that convert and call them.

/**
 * This knob plus [other]: an offset. The Ignitor's `plus` law, unclamped.
 *
 * ```KlangScript
 * Katalyst(k => k.reverb(0.3, Kat.param("room", 4).plus(2)))   // the room is 2 larger than the slot says
 * ```
 *
 * @param other a number, a `Kat.param` or a Kat expression.
 * @alias add
 */
@KlangScript.Function(name = "plus")
internal fun KatalystKnobDsl.scriptPlus(other: IgnitorDslLike): KatalystKnobDsl = this.plus(other.toKatalystKnob())

/**
 * This knob plus [other]. Alias for [plus].
 *
 * @param other a number, a `Kat.param` or a Kat expression.
 * @alias plus
 */
@KlangScript.Function(name = "add")
internal fun KatalystKnobDsl.scriptAdd(other: IgnitorDslLike): KatalystKnobDsl = this.plus(other.toKatalystKnob())

/**
 * This knob minus [other]. The Ignitor's `minus` law, unclamped.
 *
 * @param other a number, a `Kat.param` or a Kat expression.
 * @alias sub
 */
@KlangScript.Function(name = "minus")
internal fun KatalystKnobDsl.scriptMinus(other: IgnitorDslLike): KatalystKnobDsl = this.minus(other.toKatalystKnob())

/**
 * This knob minus [other]. Alias for [minus].
 *
 * @param other a number, a `Kat.param` or a Kat expression.
 * @alias minus
 */
@KlangScript.Function(name = "sub")
internal fun KatalystKnobDsl.scriptSub(other: IgnitorDslLike): KatalystKnobDsl = this.minus(other.toKatalystKnob())

/**
 * This knob times [other]: a scale. The Ignitor's `times` law: the product is clamped to `±1e15` and a product that is
 * not a number reads as 0.
 *
 * ```KlangScript
 * // one slot drives the fader at half its value
 * Katalyst(k => k.gain(Kat.param("fader", 1).times(0.5)))
 * ```
 *
 * @param other a number, a `Kat.param` or a Kat expression.
 * @alias mul
 */
@KlangScript.Function(name = "times")
internal fun KatalystKnobDsl.scriptTimes(other: IgnitorDslLike): KatalystKnobDsl = this.times(other.toKatalystKnob())

/**
 * This knob times [other]. Alias for [times].
 *
 * @param other a number, a `Kat.param` or a Kat expression.
 * @alias times
 */
@KlangScript.Function(name = "mul")
internal fun KatalystKnobDsl.scriptMul(other: IgnitorDslLike): KatalystKnobDsl = this.times(other.toKatalystKnob())

/**
 * This knob divided by [other]. The Ignitor's `div` law: a divisor of exactly zero gives zero.
 *
 * ```KlangScript
 * // a quarter of the beat a pattern writes, in seconds
 * Katalyst(k => k.delay(0.3, Kat.param("beat", 0.5).div(4)))
 * ```
 *
 * @param other a number, a `Kat.param` or a Kat expression.
 */
@KlangScript.Function(name = "div")
internal fun KatalystKnobDsl.scriptDiv(other: IgnitorDslLike): KatalystKnobDsl = this.div(other.toKatalystKnob())

/**
 * This knob to the power of [exp], keeping its sign (a negative base gives `-(|base|^exp)`). The Ignitor's `pow` law,
 * clamped to `±1e15`.
 *
 * @param exp a number, a `Kat.param` or a Kat expression.
 * @alias power
 */
@KlangScript.Function(name = "pow")
internal fun KatalystKnobDsl.scriptPow(exp: IgnitorDslLike): KatalystKnobDsl = this.pow(exp.toKatalystKnob())

/**
 * This knob to the power of [exp]. Alias for [pow].
 *
 * @param exp a number, a `Kat.param` or a Kat expression.
 * @alias pow
 */
@KlangScript.Function(name = "power")
internal fun KatalystKnobDsl.scriptPower(exp: IgnitorDslLike): KatalystKnobDsl = this.pow(exp.toKatalystKnob())

/**
 * This knob, but at least [other]: a floor, the same meaning `min` carries on a number, a pattern, a signal and a
 * stage. A floor is the MAXIMUM of the two, so this door builds [KatalystKnobDsl.Max]. Do not "correct" the crossing.
 *
 * @param other a number, a `Kat.param` or a Kat expression.
 */
@KlangScript.Function(name = "min")
internal fun KatalystKnobDsl.scriptMin(other: IgnitorDslLike): KatalystKnobDsl = this.min(other.toKatalystKnob())

/**
 * This knob, but at most [other]: a cap, the same meaning `max` carries on a number, a pattern, a signal and a stage.
 * A cap is the MINIMUM of the two, so this door builds [KatalystKnobDsl.Min]. Do not "correct" the crossing.
 *
 * ```KlangScript
 * // the pattern's feedback, but never above 0.9
 * Katalyst(k => k.delay(0.3, 0.25, Kat.param("fb", 0.5).max(0.9)))
 * ```
 *
 * @param other a number, a `Kat.param` or a Kat expression.
 */
@KlangScript.Function(name = "max")
internal fun KatalystKnobDsl.scriptMax(other: IgnitorDslLike): KatalystKnobDsl = this.max(other.toKatalystKnob())

/**
 * This knob with its sign flipped: a multiply by -1, clamped like `times`, as the Ignitor's `neg`.
 *
 * @alias negate
 */
@KlangScript.Function(name = "neg")
internal fun KatalystKnobDsl.scriptNeg(): KatalystKnobDsl = this.neg()

/**
 * This knob with its sign flipped. Alias for [neg].
 *
 * @alias neg
 */
@KlangScript.Function(name = "negate")
internal fun KatalystKnobDsl.scriptNegate(): KatalystKnobDsl = this.neg()

/** The absolute value of this knob. */
@KlangScript.Function(name = "abs")
internal fun KatalystKnobDsl.scriptAbs(): KatalystKnobDsl = this.abs()

/**
 * Maps this knob from `-1..1` onto [from]..[to], linearly and unclamped: `from + (x + 1) * 0.5 * (to - from)`, the
 * Ignitor's `range` law. A value outside `-1..1` lands outside the bounds.
 *
 * ```KlangScript
 * // one slot from -1 to 1 sweeps the phaser's center from 400 to 1600 Hz
 * Katalyst(k => k.phaser(wet = 0.5, center = Kat.param("sweep", 0).range(400, 1600)))
 * ```
 *
 * @param from the value at -1, a number, a `Kat.param` or a Kat expression.
 * @param to the value at 1, a number, a `Kat.param` or a Kat expression.
 */
@KlangScript.Function(name = "range")
internal fun KatalystKnobDsl.scriptRange(from: IgnitorDslLike, to: IgnitorDslLike): KatalystKnobDsl =
    this.range(from = from.toKatalystKnob(), to = to.toKatalystKnob())

// ── Body ─────────────────────────────────────────────────────────────────────
//
// The stage builders (`KatalystBodyBuilder`, `KatalystVowelBuilder`, `KatalystDelayBuilder`, `KatalystPhaserBuilder`)
// live in `audio_bridge` with their typed member knobs; the script knobs below convert and call them.

/**
 * Minimum dry share kept in the mix, 0 to 1 (default 0.4). Lower makes the body MORE audible: its
 * modes sit over less dry. Orbit twin: `body(floor = ...)`.
 */
@KlangScript.Function(name = "floor")
internal fun KatalystBodyBuilder.scriptFloor(floor: IgnitorDslLike): KatalystBodyBuilder = this.floor(floor.toKatalystKnob())

// ── Vowel ────────────────────────────────────────────────────────────────────

/**
 * Minimum dry share kept between the formants, 0 to 1 (default 0.2). Much lower than the body's: a
 * vowel is a source strongly shaped by its formants. Orbit twin: `vowel(floor = ...)`.
 */
@KlangScript.Function(name = "floor")
internal fun KatalystVowelBuilder.scriptFloor(floor: IgnitorDslLike): KatalystVowelBuilder = this.floor(floor.toKatalystKnob())

// ── Delay ────────────────────────────────────────────────────────────────────

/** Level the recirculating signal saturates toward (default 1.0). Orbit twin: `delay(cap = ...)`. */
@KlangScript.Function(name = "cap")
internal fun KatalystDelayBuilder.scriptCap(cap: IgnitorDslLike): KatalystDelayBuilder = this.cap(cap.toKatalystKnob())

// ── Phaser ───────────────────────────────────────────────────────────────────

/** Minimum dry coefficient of the wet/dry law (default 1.0, purely additive). Orbit twin: `phaser(floor = ...)`. */
@KlangScript.Function(name = "floor")
internal fun KatalystPhaserBuilder.scriptFloor(floor: IgnitorDslLike): KatalystPhaserBuilder = this.floor(floor.toKatalystKnob())

// ── The modulators' builders ─────────────────────────────────────────────────
//
// Handed to the `configure` lambda of the modulator doors on the Katalyst object (`Kat.sine(freq, configure)` and the
// rest, `KlangScriptKatalyst`): the Ignitor's oscillator builders' shape with only the knobs a bus LFO keeps (Q11 of
// `docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md`: `phase` on every periodic shape, `duty` on the square; no
// `analog`, no flank or reset knobs). Each knob takes a number, a `Kat.param` or a Kat expression. The builders live in
// `audio_bridge` with their typed member knobs; the script knobs below convert and call them.

/**
 * Where in its cycle the sine is read, in cycles (default 0, the upward zero crossing): 0.25 starts it on its peak,
 * and it wraps, no clamp. A constant is the start phase; a moving one shifts the reading point every block.
 *
 * ```KlangScript
 * Katalyst(k => k.gain(Kat.sine(0.5, x => x.phase(0.25)).range(0.5, 1)))   // starts at full level, then dips
 * ```
 */
@KlangScript.Function(name = "phase")
internal fun KatalystSineBuilder.scriptPhase(phase: IgnitorDslLike): KatalystSineBuilder = this.phase(phase.toKatalystKnob())

/**
 * Where in its cycle the saw is read, in cycles (default 0, the bottom of its rise): 0.5 starts it at 0, mid-rise, and
 * it wraps, no clamp.
 */
@KlangScript.Function(name = "phase")
internal fun KatalystSawBuilder.scriptPhase(phase: IgnitorDslLike): KatalystSawBuilder = this.phase(phase.toKatalystKnob())

/**
 * Where in its cycle the ramp is read, in cycles (default 0, the top of its fall): 0.5 starts it at 0, mid-fall, and it
 * wraps, no clamp.
 */
@KlangScript.Function(name = "phase")
internal fun KatalystRampBuilder.scriptPhase(phase: IgnitorDslLike): KatalystRampBuilder = this.phase(phase.toKatalystKnob())

/**
 * Where in its cycle the triangle is read, in cycles (default 0, its lowest point): 0.5 starts it at its top, and it
 * wraps, no clamp.
 */
@KlangScript.Function(name = "phase")
internal fun KatalystTriBuilder.scriptPhase(phase: IgnitorDslLike): KatalystTriBuilder = this.phase(phase.toKatalystKnob())

/**
 * `width` is the share of each cycle the square spends high, 0 to 1 (default 0.5, a square). A Kat expression moves
 * it, which is a pulse-width modulation of the LFO.
 *
 * ```KlangScript
 * Katalyst(k => k.gain(Kat.square(1, x => x.duty(0.25)).range(0.4, 1)))   // full level a quarter of each second
 * ```
 */
@KlangScript.Function(name = "duty")
internal fun KatalystSquareBuilder.scriptDuty(width: IgnitorDslLike): KatalystSquareBuilder = this.duty(width.toKatalystKnob())

/**
 * Where in its cycle the square is read, in cycles (default 0, the foot of its rising edge): 0.5 starts it on its
 * falling edge, and it wraps, no clamp.
 */
@KlangScript.Function(name = "phase")
internal fun KatalystSquareBuilder.scriptPhase(phase: IgnitorDslLike): KatalystSquareBuilder = this.phase(phase.toKatalystKnob())
