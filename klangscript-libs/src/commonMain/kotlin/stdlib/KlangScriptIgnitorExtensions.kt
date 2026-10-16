/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.AdsrBuilder
import io.peekandpoke.klang.audio_bridge.BandFilterBuilder
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.EqBuilder
import io.peekandpoke.klang.audio_bridge.FilterBuilder
import io.peekandpoke.klang.audio_bridge.FmBuilder
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.PhaserBuilder
import io.peekandpoke.klang.audio_bridge.PitchEnvelopeBuilder
import io.peekandpoke.klang.audio_bridge.ShimmerBuilder
import io.peekandpoke.klang.audio_bridge.TremoloBuilder
import io.peekandpoke.klang.audio_bridge.VibratoBuilder
import io.peekandpoke.klang.audio_bridge.abs
import io.peekandpoke.klang.audio_bridge.accelerate
import io.peekandpoke.klang.audio_bridge.adsr
import io.peekandpoke.klang.audio_bridge.bandpass
import io.peekandpoke.klang.audio_bridge.blend
import io.peekandpoke.klang.audio_bridge.ceil
import io.peekandpoke.klang.audio_bridge.clamp
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.coarse
import io.peekandpoke.klang.audio_bridge.constants.SHIMMER_PITCHES
import io.peekandpoke.klang.audio_bridge.crush
import io.peekandpoke.klang.audio_bridge.detune
import io.peekandpoke.klang.audio_bridge.distort
import io.peekandpoke.klang.audio_bridge.div
import io.peekandpoke.klang.audio_bridge.drive
import io.peekandpoke.klang.audio_bridge.eq
import io.peekandpoke.klang.audio_bridge.exp
import io.peekandpoke.klang.audio_bridge.floor
import io.peekandpoke.klang.audio_bridge.fm
import io.peekandpoke.klang.audio_bridge.frac
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lerp
import io.peekandpoke.klang.audio_bridge.log
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.max
import io.peekandpoke.klang.audio_bridge.min
import io.peekandpoke.klang.audio_bridge.minus
import io.peekandpoke.klang.audio_bridge.mod
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.neg
import io.peekandpoke.klang.audio_bridge.notch
import io.peekandpoke.klang.audio_bridge.octaveDown
import io.peekandpoke.klang.audio_bridge.octaveUp
import io.peekandpoke.klang.audio_bridge.onepole
import io.peekandpoke.klang.audio_bridge.phaser
import io.peekandpoke.klang.audio_bridge.pitchEnvelope
import io.peekandpoke.klang.audio_bridge.pitchMod
import io.peekandpoke.klang.audio_bridge.pitchModSemitones
import io.peekandpoke.klang.audio_bridge.plus
import io.peekandpoke.klang.audio_bridge.pow
import io.peekandpoke.klang.audio_bridge.pregain
import io.peekandpoke.klang.audio_bridge.range
import io.peekandpoke.klang.audio_bridge.rangex
import io.peekandpoke.klang.audio_bridge.recip
import io.peekandpoke.klang.audio_bridge.round
import io.peekandpoke.klang.audio_bridge.select
import io.peekandpoke.klang.audio_bridge.shape
import io.peekandpoke.klang.audio_bridge.shimmer
import io.peekandpoke.klang.audio_bridge.sign
import io.peekandpoke.klang.audio_bridge.sq
import io.peekandpoke.klang.audio_bridge.sqrt
import io.peekandpoke.klang.audio_bridge.tanh
import io.peekandpoke.klang.audio_bridge.times
import io.peekandpoke.klang.audio_bridge.tremolo
import io.peekandpoke.klang.audio_bridge.vibrato
import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError

/**
 * Accepts [IgnitorDsl] or [Number]. Numbers are converted to [IgnitorDsl.Constant] automatically.
 */
typealias IgnitorDslLike = Any

/**
 * Converts an [IgnitorDslLike] value to [IgnitorDsl]. Numbers become [IgnitorDsl.Constant] (not
 * overridable by ignitorParams). Anything else is a script-level type error naming what arrived, so a
 * lambda that landed on a sound slot (`Ignitor.whitenoise(x => ...)`, which has no `configure`) reads
 * as "got a function", not as an internal error.
 *
 * A Katalyst knob ([KatalystKnobDsl]: a `Kat.param`, a `Kat.slot`, or an expression over them) has its own message:
 * it is the chain's knob by intent, its author drives it with `katp`, which writes the orbit's map and never reaches
 * the voice, so in an Ignitor tree the knob would sit at its default while the song moves it. The mirror of the Katalyst knobs' refusal of an Ignitor
 * node (`toKatalystKnob`; decision Q2 of `docs/tasks-archive/2026-10/20261004-ignitor-katalyst-naming.md`, D1 of
 * `docs/tasks-archive/2026-10/20261012-katalyst-knob-expressions.md`).
 */
fun IgnitorDslLike.toIgnitorDsl(): IgnitorDsl = when (this) {
    is IgnitorDsl -> this
    is Number -> IgnitorDsl.Constant(this.toDouble())
    is KatalystKnobDsl -> throw KlangScriptTypeError("a Katalyst knob in an Ignitor tree; use Ign.param", operation = "sound parameter")
    is Function<*> -> throw KlangScriptTypeError("expected a sound or a number, got a function", operation = "sound parameter")
    else -> throw KlangScriptTypeError("expected a sound or a number, got ${this::class.simpleName}", operation = "sound parameter")
}

/**
 * The slot argument of `ignitorParam` / `ignp`: a slot NAME (`"lpf.freq"`) or an Ignitor param OBJECT
 * (`Ignitor.slot.lpf.freq`, `Ignitor.param("cutoff", 800)`, `IgnitorDsl.Slots.lpf.freq`). Resolved by
 * [ignitorSlotName] on both doors, so the script door and the Kotlin door cannot drift.
 */
typealias IgnitorSlotLike = Any

/**
 * The slot argument of `katalystParam` / `katp`: a slot NAME (`"reverb.wet"`) or a Katalyst param OBJECT
 * (`Katalyst.slot.reverb.wet`, `Katalyst.param("room", 5)`, `KatalystDsl.Slots.reverb.wet`). Resolved by
 * [katalystSlotName] on both doors.
 */
typealias KatalystSlotLike = Any

/**
 * The slot NAME an `ignitorParam` / `ignp` call writes: a string as it is, an Ignitor param by its name. Only the
 * name is written; the param's default stays the tree's.
 *
 * Everything else is a [KlangScriptTypeError] raised at the call, naming [door] and the fix: a Katalyst param
 * ("use [twin]"), a number (decision Q8: `ignp(42, x)` used to write the slot `"42"`), a sound or an expression over
 * a param (`Ignitor.param("x", 1).mul(2)` is not a slot), null (a `let` never assigned), anything else. [slot] is
 * nullable so that a script `null` reaches this message instead of the interpreter's generic conversion error.
 *
 * **Why a typed error and not a coercion.** The stone rule "coerce user-reachable inputs, never `require()` them"
 * protects the Motor from VALUES out of range. This is the wrong KIND of argument: no coercion can make a Katalyst
 * param or a sound mean the slot the author wanted, and writing it anyway would fill a map the author's knob never
 * reads. It is
 * the class of a configure lambda returning the wrong type (`configuredBy`, `/dsl-design` checklist 13), it never
 * reaches the audio path, and it is a script error naming the door, never a `require()`.
 *
 * @param door the door the author called (`"ignp"`, `"ignitorParam"`), for the message
 * @param twin the same door on the other host (`"katp"`, `"katalystParam"`), the fix for a Katalyst param
 * @param location the call's source location, so the editor marks the call
 */
fun ignitorSlotName(slot: IgnitorSlotLike?, door: String, twin: String, location: SourceLocation? = null): String =
    when (slot) {
        is String -> slot
        is IgnitorDsl.Param -> slot.name
        is KatalystKnobDsl.Param -> throw KlangScriptTypeError(
            message = "a Katalyst param passed to $door; use $twin",
            operation = door,
            location = location,
        )

        else -> throw KlangScriptTypeError(
            message = "$door expects a slot name or an Ignitor param, got ${describeArgument(slot)}",
            operation = door,
            location = location,
        )
    }

/**
 * The slot NAME a `katalystParam` / `katp` call writes: a string as it is, a Katalyst param by its name. The mirror
 * of [ignitorSlotName], with the same reasons for a typed error; an Ignitor param is the wrong host ("use [twin]"), and
 * a Katalyst expression (`Kat.param("fader", 1).times(0.5)`) is not a slot: `katp` writes the slot by name, and the
 * expression over it follows.
 *
 * @param door the door the author called (`"katp"`, `"katalystParam"`), for the message
 * @param twin the same door on the other host (`"ignp"`, `"ignitorParam"`), the fix for an Ignitor param
 * @param location the call's source location, so the editor marks the call
 */
fun katalystSlotName(slot: KatalystSlotLike?, door: String, twin: String, location: SourceLocation? = null): String =
    when (slot) {
        is String -> slot
        is KatalystKnobDsl.Param -> slot.name
        is IgnitorDsl.Param -> throw KlangScriptTypeError(
            message = "an Ignitor param passed to $door; use $twin",
            operation = door,
            location = location,
        )

        else -> throw KlangScriptTypeError(
            message = if (isKatalystExpression(slot)) {
                "$door expects a slot name or a Katalyst param; an expression is not a slot, write its param"
            } else {
                "$door expects a slot name or a Katalyst param, got ${describeArgument(slot)}"
            },
            operation = door,
            location = location,
        )
    }

/**
 * Whether [value] is a Katalyst knob EXPRESSION, any [KatalystKnobDsl] node but the two leaves. Exhaustive over the
 * knob type, so a node that joins it decides here; the modulators of step 5 (`Sine` to `BerlinNoise`) are expressions.
 */
internal fun isKatalystExpression(value: Any?): Boolean = when (value) {
    is KatalystKnobDsl -> when (value) {
        is KatalystKnobDsl.Constant, is KatalystKnobDsl.Param -> false

        is KatalystKnobDsl.Plus, is KatalystKnobDsl.Minus, is KatalystKnobDsl.Times, is KatalystKnobDsl.Div,
        is KatalystKnobDsl.Pow, is KatalystKnobDsl.Min, is KatalystKnobDsl.Max, is KatalystKnobDsl.Neg,
        is KatalystKnobDsl.Abs, is KatalystKnobDsl.Range,
        is KatalystKnobDsl.Sine, is KatalystKnobDsl.Saw, is KatalystKnobDsl.Ramp, is KatalystKnobDsl.Tri,
        is KatalystKnobDsl.Square, is KatalystKnobDsl.PerlinNoise, is KatalystKnobDsl.BerlinNoise,
        -> true
    }

    else -> false
}

/** What a wrong argument IS, in the words a song uses, for the resolvers' and the Katalyst knobs' messages. */
internal fun describeArgument(value: Any?): String = when (value) {
    null -> "null"
    is String -> "a string"
    is Number -> "a number"
    is Boolean -> "a boolean"
    is IgnitorDsl -> "a sound"
    is KatalystKnobDsl -> if (isKatalystExpression(value)) "a Kat expression" else "a Katalyst knob"
    is KatalystDsl -> "a Katalyst chain"
    is Function<*> -> "a function"
    is List<*> -> "an array"
    is Map<*, *> -> "an object"
    else -> value::class.simpleName ?: "a value"
}

/**
 * A name knob as the index its node carries (a body material, a vowel, a waveshaper, an LFO shape):
 * a NAME through its catalogue's `indexOf`, a number or a slot as it is, and nothing at all as
 * [bare], the stage's own value. The one conversion every such door shares (Katalyst step 5a-2 for
 * `body` and `vowel`, phase 3 step 3b for `shape`, `distort` and the tremolo builder), so a name,
 * its index and a slot carrying the index are one knob on every door. This form is the Ignitor doors'
 * ([toIgnitorDsl]); the Katalyst doors use the one with a `constant` and a `knob` of their own.
 */
internal fun catalogueIndex(value: IgnitorDslLike?, bare: IgnitorDsl, indexOf: (String) -> Double): IgnitorDsl =
    catalogueIndex(value = value, bare = bare, indexOf = indexOf, constant = { IgnitorDsl.Constant(it) }, knob = { it.toIgnitorDsl() })

/**
 * The name-knob rule of the other [catalogueIndex], for a knob type [K] of either DSL: [constant] wraps the index a
 * name resolves to, [knob] converts a number or a slot (`toKatalystKnob` on the Katalyst doors).
 */
internal fun <K> catalogueIndex(
    value: IgnitorDslLike?,
    bare: K,
    indexOf: (String) -> Double,
    constant: (Double) -> K,
    knob: (IgnitorDslLike) -> K,
): K =
    when (value) {
        null -> bare
        is String -> constant(indexOf(value))
        else -> knob(value)
    }

/**
 * Extension methods on [IgnitorDsl] for KlangScript.
 *
 * Enables chaining: `Ignitor.sine().lowpass(1000).adsr(0.01, 0.1, 0.5, 0.3)`
 * Any numeric parameter also accepts an IgnitorDsl for audio-rate modulation:
 * `Ignitor.sine().lowpass(Ignitor.perlin())` — modulated cutoff.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(IgnitorDsl::class)
object KlangScriptIgnitorExtensions {

    // ── Filters ──────────────────────────────────────────────────────────────

    /**
     * Applies a resonant lowpass filter. Cutoff and Q accept Number or IgnitorDsl.
     *
     * The door carries the filter's musical inputs, [freq] and [q]; everything else is a knob on
     * the [FilterBuilder] the lambda receives: `passes`, `analog`, `humanize`, and the cutoff
     * envelope as `env(semitones)` plus ONE `adsr(attack, decay, sustain, release,
     * configure)` call, the chain `adsr`'s shape, whose own lambda shapes the stages with `curves`.
     * `env` and `adsr` are a compound pair: naming either switches the envelope on and the other
     * fills from the constants (`fillFilterEnvelope`, after the lambda).
     *
     * ```KlangScript
     * Ignitor.saw().lowpass(800, 1.2, x => x.passes(2).analog(3))
     * Ignitor.saw().lowpass(800, x => x.env(24).adsr(0.005, 0.3, 0.2, 0.2))   // a pluck; q stays 0.707
     * Ignitor.saw().lowpass(800, 1.2, x => x.env(24).adsr(0.01, 0.3, 0.2, 0.5, e => e.curves("lin", "exp", "exp")))
     * ```
     *
     * The cutoff envelope runs the law, the sampling and the default curve (exponential) of
     * sprudel's `lpf(env = ...)`: see [IgnitorDsl.Lowpass.env] and decision D3.
     *
     * @param configure receives the [FilterBuilder] (knobs: `passes`, `analog`, `humanize`, `env`,
     * `adsr`) and returns it.
     */
    @KlangScript.Method
    fun lowpass(
        self: IgnitorDsl,
        freq: IgnitorDslLike,
        q: IgnitorDslLike = 0.707,
        configure: ((FilterBuilder) -> FilterBuilder)? = null,
    ): IgnitorDsl = self.lowpass(freq = freq.toIgnitorDsl(), q = q.toIgnitorDsl()) {
        it.configuredBy("lowpass", configure)
    }

    /**
     * Applies a resonant highpass filter. The knobs are [lowpass]'s, on the same [FilterBuilder].
     *
     * @param configure receives the [FilterBuilder] and returns it.
     */
    @KlangScript.Method
    fun highpass(
        self: IgnitorDsl,
        freq: IgnitorDslLike,
        q: IgnitorDslLike = 0.707,
        configure: ((FilterBuilder) -> FilterBuilder)? = null,
    ): IgnitorDsl = self.highpass(freq = freq.toIgnitorDsl(), q = q.toIgnitorDsl()) {
        it.configuredBy("highpass", configure)
    }

    /**
     * Applies a one-pole lowpass at [freq] Hz — the gentlest filter there is (6 dB/oct, no
     * resonance); musically a warmth/tone control. ONE name on every door (formerly
     * `warmth` / `onePoleLowpass`).
     */
    @KlangScript.Method
    fun onepole(self: IgnitorDsl, freq: IgnitorDslLike): IgnitorDsl = self.onepole(freq = freq.toIgnitorDsl())

    /**
     * SVF bandpass filter. Passes frequencies near the cutoff, attenuates others. The knobs are
     * [lowpass]'s without `passes`, on a [BandFilterBuilder]; its `analog` scales `humanize` only
     * (the saturation is not implemented for this tap).
     *
     * @param configure receives the [BandFilterBuilder] (knobs: `analog`, `humanize`, `env`,
     * `adsr`) and returns it.
     */
    @KlangScript.Method
    fun bandpass(
        self: IgnitorDsl,
        freq: IgnitorDslLike,
        q: IgnitorDslLike = 0.707,
        configure: ((BandFilterBuilder) -> BandFilterBuilder)? = null,
    ): IgnitorDsl = self.bandpass(freq = freq.toIgnitorDsl(), q = q.toIgnitorDsl()) {
        it.configuredBy("bandpass", configure)
    }

    /**
     * Turns the graph optimizer off for this sound (`optimizer(0)`), so it renders exactly as
     * written instead of having its filter chain fused.
     *
     * Fusing is meant to be inaudible, so this is a listening tool: put `.optimizer(0)` on a
     * sound, compare by ear, and take it off again. Anything but 0 leaves the optimizer on.
     */
    @KlangScript.Method
    fun optimizer(self: IgnitorDsl, on: Int = 1): IgnitorDsl =
        IgnitorDsl.OptimizerHint(inner = self, on = on)

    /**
     * Opens an equalizer on [self] and configures its sections in the lambda. Everything in one
     * equalizer runs in ONE pass instead of one node each, which drops the scratch buffer, the
     * extra buffer traffic and the virtual call for every section after the first (the per-sample
     * filter loop per section stays, by design). The saving grows with the section count and is
     * much larger in the browser than on desktop JVM.
     *
     * Two kinds of section on the [EqBuilder], and the difference is audible:
     * - `band(freq, q, db)` is an ordinary EQ band. Bands apply one after another, so two
     *   overlapping boosts compound.
     * - `tap(freq, q, gain)` takes the sound going INTO the equalizer, filters that, and mixes
     *   it back in. Taps mix with the original rather than stacking on each other.
     *
     * Plain filters written after it (`.lowpass()`, `.notch()`, ...) are folded into the same
     * single pass automatically, so you do not have to write them as bands to get the saving,
     * but only NEIGHBOURING filters merge: anything else in between (`.distort()`, `.mul()`,
     * `.tremolo()`, ...) is a wall and starts a new pass. Use `.optimizer(0)` to render a sound
     * exactly as written and compare by ear.
     *
     * An `.eq()` directly on an equalizer continues it (the lambda appends to its sections); an
     * `.eq()` written after other filters opens a SECOND equalizer. Careful when adding a `tap`
     * onto a sound someone ELSE built: if that sound already ends in an equalizer, your `.eq()`
     * continues theirs, and your tap then reads THEIR input rather than their output. Bands are
     * unaffected.
     *
     * @param configure receives the [EqBuilder] (knobs: `band`, `tap`) and returns it.
     *
     * ```KlangScript
     * Ignitor.saw().eq(e => e.band(300, 1.0, -4).tap(850, 0.707, 1.7)).lowpass(5000)
     * ```
     */
    @KlangScript.Method
    fun eq(self: IgnitorDsl, configure: ((EqBuilder) -> EqBuilder)? = null): IgnitorDsl =
        self.eq { it.configuredBy("eq", configure) }

    /**
     * SVF notch (band-reject) filter. The knobs are [bandpass]'s, on a [BandFilterBuilder].
     *
     * @param configure receives the [BandFilterBuilder] and returns it.
     */
    @KlangScript.Method
    fun notch(
        self: IgnitorDsl,
        freq: IgnitorDslLike,
        q: IgnitorDslLike = 0.707,
        configure: ((BandFilterBuilder) -> BandFilterBuilder)? = null,
    ): IgnitorDsl = self.notch(freq = freq.toIgnitorDsl(), q = q.toIgnitorDsl()) {
        it.configuredBy("notch", configure)
    }

    // ── Envelope ─────────────────────────────────────────────────────────────

    /**
     * Applies an ADSR amplitude envelope: attack, decay and release in seconds, the sustain as a
     * level 0 to 1. All four accept a Number or an IgnitorDsl. The lambda receives an [AdsrBuilder]
     * whose knobs shape the stages and de-click the gain:
     *
     * ```KlangScript
     * Ignitor.saw().adsr(0.01, 0.3, 0.5, 0.2)
     * Ignitor.saw().adsr(0.005, 1.0, 0.0, 0.03, e => e.curves("linear", "linear", "linear"))
     * Ignitor.sine().adsr(0.001, 0.4, 0.0, 0.1, e => e.declick(0.0005))
     * ```
     *
     * Unshaped stages are exponential, every amplitude envelope's default, and every exponential
     * stage bends at the engine's one curvature.
     *
     * @param configure receives the [AdsrBuilder] (knobs: `curves`, `declick`) and returns it.
     */
    @KlangScript.Method
    fun adsr(
        self: IgnitorDsl,
        attack: IgnitorDslLike,
        decay: IgnitorDslLike,
        sustain: IgnitorDslLike,
        release: IgnitorDslLike,
        configure: ((AdsrBuilder) -> AdsrBuilder)? = null,
    ): IgnitorDsl = self.adsr(
        attack = attack.toIgnitorDsl(),
        decay = decay.toIgnitorDsl(),
        sustain = sustain.toIgnitorDsl(),
        release = release.toIgnitorDsl(),
    ) { it.configuredBy("adsr", configure) }

    // ── The classic tail ─────────────────────────────────────────────────────

    /**
     * Wraps this sound in the classic synth voice: the FM, the pitch envelope, the accelerate and the vibrato on the source, then the
     * pattern's one-pole lowpass, crush, coarse, distort, highpass, bandpass, notch, lowpass, tremolo and the amplitude envelope, in that
     * order, every one of them driven by a slot the pattern's doors fill (`Ignitor.slot.fm.depth`, `Ignitor.slot.penv.semitones`,
     * `Ignitor.slot.accelerate`, `Ignitor.slot.vibrato.semitones`,
     * `Ignitor.slot.onepole`, `Ignitor.slot.lpf.freq`, `Ignitor.slot.adsr.attack`, ...). A stage the note does not write is not built, so an untouched
     * `classic()` costs one envelope and nothing else.
     *
     * Make it the LAST call (an `.optimizer(...)` hint after it is fine, it is not a stage). An instrument that ends
     * in `classic()` is the whole voice: the voice doors (`onepole(...)`, `lpf(...)`, `adsr(...)`, ...) reach its
     * slots, and nothing runs after it but the channel (`gain`, `pan`). Every built-in sound (`sound("saw")`) IS a
     * source with this tail. Every instrument is played as its tree. Around it the engine adds only
     * the teardown fade when the tree does not end in its own envelope (`BuiltIgnitor.endsInEnvelope`), and the
     * channel after (`gain`, `pan`); so without `classic()` the doors of `classic()` reach only the slots the tree
     * places itself. The pattern's FM (`fm`), pitch envelope (`penv`), `accelerate` and vibrato (`vib`) are `classic()`
     * stages: an instrument without `classic()` ignores them, as it ignores `lpf` or `adsr`. A pattern also reaches the slots by name:
     * `ignp("lpf.freq", 1800)`.
     *
     * Want another order? Write your own tail from the same `Ignitor.slot` slots, as far as a door takes them: every
     * filter's `freq`, `q`, `passes`, `env` and envelope stages, `crush`, `coarse`, the tremolo's knobs, the pitch envelope's
     * `penv.*` and `penvCurves.*` (`pitchEnvelope(Ignitor.slot.penv.semitones, ...)`), the flat `accelerate`
     * (`accelerate(Ignitor.slot.accelerate)`), the vibrato's `vibrato.*`
     * (`vibrato(Ignitor.slot.vibrato.rate, Ignitor.slot.vibrato.semitones, v => v.range(Ignitor.slot.vibrato.rangeFrom,
     * Ignitor.slot.vibrato.rangeTo).phase(Ignitor.slot.vibrato.phase))`), the FM's `fm.*`
     * (`fm(Ignitor.sine(x => x.analog(0)), Ignitor.slot.fm.ratio, Ignitor.slot.fm.depth, ...)`) and the envelope's stages and curves. The pattern's `onepole` is a slot too (`Ignitor.slot.onepole`): the engine no longer hangs one
     * around the instrument. Two groups only `classic()` can place: `adsr.on` (no door has the switch) and `distort.*` (the `distort` door
     * builds a drive into a shaper that always runs and caps its output; `classic()` uses the one distort node that
     * switches off as a whole, so a slot on the door's distort would shape every note, written or not).
     *
     * ```KlangScript
     * let guitar = Ignitor.saw().distort(0.4).classic()
     * ```
     *
     * No arguments: everything it does is a slot. It is the same function as the Kotlin
     * `IgnitorDsl.classic()`, which is the one place the order is written.
     */
    @KlangScript.Method
    fun classic(self: IgnitorDsl): IgnitorDsl = self.classic()

    // ── Effects ──────────────────────────────────────────────────────────────

    /**
     * Pre-amplification stage. Boosts signal level before clipping: gain without a curve, every
     * colour belongs to `shape`.
     */
    @KlangScript.Method
    fun drive(self: IgnitorDsl, amount: IgnitorDslLike): IgnitorDsl = self.drive(amount = amount.toIgnitorDsl())

    /**
     * Pure waveshaping without drive. Applies a nonlinear transfer function per sample.
     *
     * Shapes:
     *  - **Symmetric soft:** "soft" (tanh), "gentle", "softsat", "cubic", "exp", "sineshaper".
     *  - **Symmetric hard / wavefolding:** "hard", "zerosquare", "chebyshev", "fold", "linearfold".
     *  - **Asymmetric (even harmonics):** "diode", "tube", "asym", "stompbox", "rectify".
     *
     * @param shape a shape NAME, converted to its index in `DistortionShapes` (an unknown name is
     *   "soft"); or the index itself as a number; or a slot carrying it (`Ignitor.param("drive-shape", 10)`),
     *   the door being the only way to write one. Read once per note.
     * @param oversample the oversampling factor (2 = 2x, 4 = 4x, 8 = 8x; 0 or 1 = off; a non-power of
     *   two is floored), a number or a slot, read once per note. A stopgap until oversampling regions.
     */
    @KlangScript.Method
    fun shape(self: IgnitorDsl, shape: IgnitorDslLike = "soft", oversample: IgnitorDslLike = 0): IgnitorDsl =
        self.shape(shape = shapeIndex(shape), oversample = oversample.toIgnitorDsl())

    /**
     * Waveshaping distortion. Convenience for drive(amount) + shape(shape, oversample).
     *
     * Shapes:
     *  - **Symmetric soft:** "soft" (tanh), "gentle", "softsat", "cubic", "exp", "sineshaper".
     *  - **Symmetric hard / wavefolding:** "hard", "zerosquare", "chebyshev", "fold", "linearfold".
     *  - **Asymmetric (even harmonics):** "diode", "tube", "asym", "stompbox", "rectify".
     *
     * [shape] and [oversample] take what [shape]'s do: a name, a number or a slot.
     */
    @KlangScript.Method
    fun distort(
        self: IgnitorDsl,
        amount: IgnitorDslLike,
        shape: IgnitorDslLike = "soft",
        oversample: IgnitorDslLike = 0,
    ): IgnitorDsl = self.distort(amount = amount.toIgnitorDsl(), shape = shapeIndex(shape), oversample = oversample.toIgnitorDsl())

    /** A `shape` argument as the waveshaper's index knob: a name through `DistortionShapes`, a number or a slot as it is. */
    private fun shapeIndex(shape: IgnitorDslLike): IgnitorDsl =
        catalogueIndex(shape, IgnitorDsl.Constant(DistortionShapes.SOFT_INDEX.toDouble()), DistortionShapes::indexOf)

    /** Applies bit-depth reduction (bitcrusher): [bits] is the bit depth, `2^bits` levels; below 1 it passes through. */
    @KlangScript.Method
    fun crush(self: IgnitorDsl, bits: IgnitorDslLike): IgnitorDsl = self.crush(bits = bits.toIgnitorDsl())

    /** Applies sample-rate reduction: [factor] is the sample-hold factor; at 1 or less nothing is held. */
    @KlangScript.Method
    fun coarse(self: IgnitorDsl, factor: IgnitorDslLike): IgnitorDsl = self.coarse(factor = factor.toIgnitorDsl())

    /**
     * Applies a multi-stage phaser effect. [wet] comes FIRST, as on every door that has one, and
     * both [wet] and [rate] are required, because a positional rate follows the wet. The dry floor
     * is a knob on the [PhaserBuilder]: `.phaser(0.3, 0.5, x => x.floor(0.2))`.
     *
     * @param wet wet/dry balance, 0..1 (0 is a bit-exact bypass).
     * @param rate sweep rate in Hz.
     * @param center sweep center frequency in Hz (default 1000).
     * @param sweep sweep width in Hz (default 1000).
     * @param configure receives the [PhaserBuilder] (knob: `floor`) and returns it.
     */
    @KlangScript.Method
    fun phaser(
        self: IgnitorDsl,
        wet: IgnitorDslLike,
        rate: IgnitorDslLike,
        center: IgnitorDslLike = 1000.0,
        sweep: IgnitorDslLike = 1000.0,
        configure: ((PhaserBuilder) -> PhaserBuilder)? = null,
    ): IgnitorDsl = self.phaser(
        wet = wet.toIgnitorDsl(),
        rate = rate.toIgnitorDsl(),
        center = center.toIgnitorDsl(),
        sweep = sweep.toIgnitorDsl(),
    ) { it.configuredBy("phaser", configure) }

    /**
     * Applies amplitude tremolo: an oscillator at [rate] Hz pulls the level down by up to [depth]
     * (0 to 1) by default; the builder's `range` moves the swing (a swell upward, or both ways). The LFO's shape and where its swing sits are knobs on the [TremoloBuilder]:
     * `.tremolo(4, 0.8, x => x.shape("square"))`, `.tremolo(4, 0.3, x => x.range(0, 1))` (a swell upward instead of
     * the classic dip). Rate first, like every Ignitor LFO door; the pattern door is `tremolo(depth, rate, shape)`.
     *
     * @param configure receives the [TremoloBuilder] (knobs: `shape`, `range`) and returns it.
     */
    @KlangScript.Method
    fun tremolo(
        self: IgnitorDsl,
        rate: IgnitorDslLike,
        depth: IgnitorDslLike,
        configure: ((TremoloBuilder) -> TremoloBuilder)? = null,
    ): IgnitorDsl = self.tremolo(rate = rate.toIgnitorDsl(), depth = depth.toIgnitorDsl()) {
        it.configuredBy("tremolo", configure)
    }

    /**
     * Applies a granular shimmer cloud with configurable pitch transpositions and feedback. [wet]
     * comes FIRST, as on every door that has one; the dry floor is a knob on the [ShimmerBuilder]:
     * `.shimmer(0.4, 0.5, 4000, [0, 7, 12], x => x.floor(0.2))`.
     *
     * @param wet Wet/dry balance, 0..1. Default 0.5. 0 is a bit-exact bypass.
     * @param feedback Cascade feedback (0..0.95). Default 0.5.
     * @param tone Feedback-path LPF cutoff in Hz. Default 4000.
     * @param pitches Array of semitone transpositions. Default [0, 7, 12]. Example: [0, 4, 7, 11] for maj7.
     *   An empty list spawns no grains: only the dry plays, at its wet/dry level (silent at wet 1). An entry
     *   that is not a number is a type error.
     * @param configure receives the [ShimmerBuilder] (knob: `floor`) and returns it.
     */
    @KlangScript.Method
    fun shimmer(
        self: IgnitorDsl,
        wet: IgnitorDslLike = 0.5,
        feedback: IgnitorDslLike = 0.5,
        tone: IgnitorDslLike = 4000.0,
        pitches: Any? = null,
        configure: ((ShimmerBuilder) -> ShimmerBuilder)? = null,
    ): IgnitorDsl {
        val pitchList = when (pitches) {
            // A wrong TYPE is the door's usual typed error, as on `wet`, `feedback` and `tone`, at script time and
            // never on the audio thread; skipping the entry would silently change the chord.
            is List<*> -> pitches.map {
                (it as? Number)?.toDouble() ?: throw KlangScriptTypeError(
                    message = "shimmer pitches expect numbers, got ${describeArgument(it)}",
                    operation = "shimmer",
                )
            }
            else -> SHIMMER_PITCHES
        }

        return self.shimmer(
            wet = wet.toIgnitorDsl(),
            feedback = feedback.toIgnitorDsl(),
            tone = tone.toIgnitorDsl(),
            pitches = pitchList,
        ) { it.configuredBy("shimmer", configure) }
    }

    // ── FM Synthesis ─────────────────────────────────────────────────────────

    /**
     * Applies FM synthesis with a modulator ignitor. The modulation index envelope is a knob on the
     * [FmBuilder]: `.fm(Ignitor.sine(), 1.4, 300, x => x.adsr(0.001, 0.5, 0, 0.05))`, the built-in `sgbell`.
     *
     * A pitch node means what it wraps. Above the fm it moves the whole operator, the note's pitch
     * (`Ignitor.sine().fm(Ignitor.sine(), 3.5, 400).vibrato(6, 0.5)`; a modulator with an absolute frequency,
     * `Ignitor.sine(330)`, stays at it); on the modulator, the modulator alone
     * (`Ignitor.sine().fm(Ignitor.sine().vibrato(6, 0.5), 3.5, 400)`); on the carrier, the carrier alone
     * (`Ignitor.sine().vibrato(6, 0.5).fm(Ignitor.sine(), 3.5, 400)`). One fm serves one pitch: over
     * `x + x.detune(7)`, give each layer its own fm, inside its detune, and sum them,
     * `x.fm(m1, ...) + x.fm(m2, ...).detune(7)`.
     *
     * @param configure receives the [FmBuilder] (knob: `adsr`) and returns it.
     */
    @KlangScript.Method
    fun fm(
        self: IgnitorDsl,
        modulator: IgnitorDslLike,
        ratio: IgnitorDslLike,
        depth: IgnitorDslLike,
        configure: ((FmBuilder) -> FmBuilder)? = null,
    ): IgnitorDsl = self.fm(
        modulator = modulator.toIgnitorDsl(),
        ratio = ratio.toIgnitorDsl(),
        depth = depth.toIgnitorDsl(),
    ) { it.configuredBy("fm", configure) }

    // ── Pitch Modulation ─────────────────────────────────────────────────────

    /** Shifts pitch by semitones. */
    @KlangScript.Method
    fun detune(self: IgnitorDsl, semitones: IgnitorDslLike): IgnitorDsl = self.detune(semitones = semitones.toIgnitorDsl())

    /** Shifts pitch up one octave (+12 semitones). */
    @KlangScript.Method
    fun octaveUp(self: IgnitorDsl): IgnitorDsl = self.octaveUp()

    /** Shifts pitch down one octave (-12 semitones). */
    @KlangScript.Method
    fun octaveDown(self: IgnitorDsl): IgnitorDsl = self.octaveDown()

    /**
     * Applies pitch vibrato: [rate] Hz LFO, [semitones] deep. A signal depth is followed sample by sample
     * (`Ignitor.saw().vibrato(5, Ignitor.sine(0.5).mul(0.3).plus(0.3))` swells and fades); a depth at or below 0
     * is no vibrato. Where the swing sits and where the LFO starts are knobs on the [VibratoBuilder]:
     * `.vibrato(5, 0.5, v => v.range(0, 1).phase(0.75))` swings only upward from the note (a guitar's vibrato; phase 0
     * is the middle of the swing), `.vibrato(5, 0.5, v => v.phase(0.25))` starts every note at the top of the wobble. The pattern door is `vib(rate, semitones, rangeFrom, rangeTo, phase)`.
     *
     * @param configure receives the [VibratoBuilder] (knobs: `range`, `phase`) and returns it.
     */
    @KlangScript.Method
    fun vibrato(
        self: IgnitorDsl,
        rate: IgnitorDslLike,
        semitones: IgnitorDslLike,
        configure: ((VibratoBuilder) -> VibratoBuilder)? = null,
    ): IgnitorDsl = self.vibrato(rate = rate.toIgnitorDsl(), semitones = semitones.toIgnitorDsl()) {
        it.configuredBy("vibrato", configure)
    }

    /** Applies a pitch glide of [semitones] from the onset to the gate close, held through the release. */
    @KlangScript.Method
    fun accelerate(self: IgnitorDsl, semitones: IgnitorDslLike): IgnitorDsl = self.accelerate(semitones = semitones.toIgnitorDsl())

    /**
     * Pitch modulation by any signal, the LINEAR law: the frequency is multiplied by `1 + mod`, per sample. [mod] is
     * a deviation, unitless: 0 is no change, 1.0 an octave up, -0.5 an octave down, -1.0 stops the oscillator. FM's
     * law: `Ignitor.sine().pitchMod(Ignitor.sine(5).mul(0.01))` swings the pitch 1 % either way. In semitones:
     * `pitchModSemitones`.
     */
    @KlangScript.Method
    fun pitchMod(self: IgnitorDsl, mod: IgnitorDslLike): IgnitorDsl = self.pitchMod(mod = mod.toIgnitorDsl())

    /**
     * Pitch modulation by any signal, in SEMITONES: the frequency is multiplied by `2^(mod / 12)`, per sample. 12 is
     * an octave up, 7 a fifth, -12 an octave down, 0 the note. `Ignitor.saw().pitchModSemitones(Ignitor.sine(5).mul(0.5))`
     * is a vibrato half a semitone deep; `pitchModSemitones(7)` plays a fifth up. The pitch law of `vibrato`,
     * `accelerate` and `pitchEnvelope`, for any signal. The linear law (`1 + mod`, FM's): `pitchMod`.
     */
    @KlangScript.Method
    fun pitchModSemitones(self: IgnitorDsl, mod: IgnitorDslLike): IgnitorDsl = self.pitchModSemitones(mod = mod.toIgnitorDsl())

    /**
     * Applies a pitch envelope (pitch sweep over time). [semitones] is the shift at the envelope
     * peak; the stages are ONE `adsr` call on the [PitchEnvelopeBuilder], the chain `adsr`'s
     * pattern: `pitchEnvelope(24, x => x.adsr(0.001, 0.05, 0, 0))` sweeps a kick from two octaves
     * up down to the note. That `adsr` takes its own lambda to shape the stages with `curves`
     * (`x => x.adsr(0.001, 0.05, 0, 0, e => e.curves("lin", "exp", "exp"))`); unshaped stages are
     * exponential. Without the lambda the stages are `adsr(0.01, 0.1, 0, 0)`. The release returns the
     * pitch to the note from the gate's end and does not extend the voice's life.
     *
     * @param configure receives the [PitchEnvelopeBuilder] (knob: `adsr`) and returns it.
     */
    @KlangScript.Method
    fun pitchEnvelope(
        self: IgnitorDsl,
        semitones: IgnitorDslLike,
        configure: ((PitchEnvelopeBuilder) -> PitchEnvelopeBuilder)? = null,
    ): IgnitorDsl = self.pitchEnvelope(semitones = semitones.toIgnitorDsl()) {
        it.configuredBy("pitchEnvelope", configure)
    }

    // ── Analog Drift ────────────────────────────────────────────────────────


    // ── Composition ──────────────────────────────────────────────────────────

    /**
     * Runs this signal through the stages in series, in the order written: `x.serial(a, b, c)` is `c(b(a(x)))`.
     * A stage is any function from a signal to a signal, so a signal chain is written as the list it is,
     * with any number of stages; a rig is a stage too. With no stage, `serial()` returns the signal as it is.
     *
     * ```KlangScript
     * let pedal = x => x.distort(0.4, "soft")
     * let cab   = x => x.highpass(100).lowpass(5000)
     * let rig   = x => x.serial(pedal, cab)
     * let guitar = Ignitor.saw().serial(rig).adsr(0.005, 0.8, 0.0, 0.05).classic()
     * ```
     *
     * One stage into the next. Not sprudel's `apply(f, g)`, which stacks the results side by side.
     * It builds what the Kotlin `IgnitorDsl.serial(...)` builds, and checks every stage on the way: a stage that is
     * null, returns nothing or returns something other than a signal is a script error naming the stage; a stage that is
     * not a function at all is refused at the call ("expected a function, got a number").
     *
     * @param stages functions from a signal to a signal, applied first to last.
     */
    @KlangScript.Method
    fun serial(self: IgnitorDsl, vararg stages: (IgnitorDsl) -> IgnitorDsl): IgnitorDsl =
        runSerialStages("Ignitor serial", self, stages, returns = "signal", example = "x => x.lowpass(800)") { it is IgnitorDsl }

    /**
     * Runs this signal through the branches side by side and SUMS them, the twin of `serial`: `x.parallel(a, b)` is
     * `a(x) + b(x)`, and every branch reads the same `x`, built once (a pitch node in a branch, a `vibrato` or a
     * `detune`, forks it into a second instance, as it forks any shared signal).
     *
     * ```KlangScript
     * // parallel distortion: the clean signal and a screaming copy of its highs, a little under it
     * let screamer = x => x.parallel(clean => clean, dirt => dirt.highpass(720).distort(0.35).mul(0.6))
     * let guitar = Ignitor.saw().serial(screamer).adsr(0.005, 0.8, 0.0, 0.05).classic()
     * ```
     *
     * The sum is plain (two identical branches are twice the level); a branch's own `mul` sets the blend. A branch
     * that delays the signal (an oversampled `distort` or `shape`) is matched in phase by the others, so the sum does
     * not comb; a plain `plus` does not do that. With no branch, `parallel()` returns the signal as it is; with one,
     * that branch's output. It builds what the Kotlin `IgnitorDsl.parallel(...)` builds, and checks every branch as
     * `serial` checks a stage.
     *
     * @param branches functions from a signal to a signal, each given this signal.
     */
    @KlangScript.Method
    fun parallel(self: IgnitorDsl, vararg branches: (IgnitorDsl) -> IgnitorDsl): IgnitorDsl {
        val built = branches.mapIndexed { index, branch ->
            runStage<IgnitorDsl, IgnitorDsl>(
                door = "Ignitor parallel",
                noun = "branch",
                index = index,
                stage = branch,
                input = self,
                returns = "signal",
                example = "x => x.lowpass(800)",
                isResult = { it is IgnitorDsl },
            )
        }

        return when (built.size) {
            0 -> self
            1 -> built[0]
            else -> IgnitorDsl.Parallel(branches = built)
        }
    }

    /**
     * A dry/wet blend, the linear law: `x.blend(wet, f)` is `x.parallel(d => d.mul(1 - wet), w => f(w).mul(wet))`, so 0 is
     * the dry signal and 1 is the branch alone. `wet` comes first, as on every door with one.
     *
     * ```KlangScript
     * // a quarter of a hard distortion under the clean string
     * let edge = x => x.blend(0.25, y => y.distort(0.6, "hard"))
     * ```
     *
     * Linear is right for a branch that stays correlated with the dry (distortion, filters). [wet] may be a number, a
     * slot or a signal (an LFO moves the blend); a number that is not finite reads as 0. The branch is checked as a
     * `parallel` branch is, and a late branch is aligned the same way.
     *
     * @param wet the share of the branch, 0 to 1.
     * @param branch a function from the signal to a signal.
     */
    @KlangScript.Method
    fun blend(self: IgnitorDsl, wet: IgnitorDslLike, branch: (IgnitorDsl) -> IgnitorDsl): IgnitorDsl {
        return self.blend(wet = wet.toIgnitorDsl()) { signal ->
            runStage<IgnitorDsl, IgnitorDsl>(
                door = "Ignitor blend",
                noun = "branch",
                index = 0,
                stage = branch,
                input = signal,
                returns = "signal",
                example = "x => x.distort(0.5)",
                isResult = { it is IgnitorDsl },
            )
        }
    }

    /**
     * Splits this signal into frequency BANDS, processes each band on its own and sums them again: multiband
     * distortion, a saturated mid range over a clean low end, an exciter on the highs. Read from the bottom up:
     *
     * ```KlangScript
     * // the lows clean, the mids crunchy, the highs untouched
     * Ignitor.saw().bands(b => b.cut(250).band(mid => mid.distort(0.4)).cut(3000))
     * ```
     *
     * `band(f)` adds a processor to the band being written (two on one band are summed), `cut(freq)` closes it and
     * starts the next one up; a band with no `band()` passes untouched, and a cut below the one before it is moved up
     * to it. The crossover is Linkwitz-Riley: with nothing processed the bands sum to flat level, with the phase
     * turned around each cut (the waveform changes, the balance does not). Every band reads the same signal, built
     * once; a late band (an oversampled `distort`) is matched by delaying the others, as in `parallel`. With no
     * `configure`, or no `cut`, it is the one band.
     *
     * @param configure receives the bands builder and returns it.
     */
    @KlangScript.Method
    fun bands(self: IgnitorDsl, configure: ((IgnitorBandsBuilder) -> IgnitorBandsBuilder)? = null): IgnitorDsl =
        IgnitorBandsBuilder().configuredBy("Ignitor bands", configure).split(self)

    // ── Arithmetic ───────────────────────────────────────────────────────────
    //
    // The script door of the arithmetic (typed-doors rollout S4): every word takes a number or a signal, converts it
    // once (`toIgnitorDsl`) and calls the ONE typed function of `audio_bridge` (`self.mul(...)`), aliases included.
    // Internal, renamed variants with the script word in `name = "..."` (the typed-door pattern, `/dsl-design` §3
    // point 3); the Kotlin door is `saw * 0.5.ign`. The `min` / `max` crossing lives once, in the typed functions.

    /**
     * Adds two ignitor signals together (summing).
     *
     * @alias add
     */
    @KlangScript.Method(name = "plus")
    internal fun scriptPlus(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        self.plus(other.toIgnitorDsl())

    /**
     * Adds two ignitor signals together (summing). Alias for [plus].
     *
     * @alias plus
     */
    @KlangScript.Method(name = "add")
    internal fun scriptAdd(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        self.plus(other.toIgnitorDsl())

    /**
     * Subtracts another signal from this one.
     *
     * @alias sub
     */
    @KlangScript.Method(name = "minus")
    internal fun scriptMinus(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        self.minus(other.toIgnitorDsl())

    /**
     * Subtracts another signal from this one. Alias for [minus].
     *
     * @alias minus
     */
    @KlangScript.Method(name = "sub")
    internal fun scriptSub(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        self.minus(other.toIgnitorDsl())

    /**
     * Multiplies two ignitor signals (ring modulation / amplitude modulation).
     *
     * @alias mul
     */
    @KlangScript.Method(name = "times")
    internal fun scriptTimes(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        self.times(other.toIgnitorDsl())

    /**
     * Scales the signal by a factor (IgnitorDsl or Number). Alias for [times].
     *
     * @alias times
     */
    @KlangScript.Method(name = "mul")
    internal fun scriptMul(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        self.mul(other.toIgnitorDsl())

    /**
     * Places the `pregain` slot here: how hard the pattern plays INTO whatever follows.
     *
     * Exactly `mul(Ignitor.slot.pregain)`, and written as a call to [mul] so the two spellings cannot
     * drift into two operand orders: one tree, one content id. It takes no argument on purpose:
     * the slot IS the parameter, and the pattern moves it with `pregain(x)`.
     *
     * Put it in front of the nonlinearity it should drive, once. It changes TIMBRE only because
     * of what follows it; with nothing nonlinear after it, it is a plain level, and `gain` is
     * the tone-neutral level word.
     *
     * ```KlangScript
     * Ignitor.saw().pregain().distort(0.5)
     * ```
     */
    @KlangScript.Method(name = "pregain")
    internal fun scriptPregain(self: IgnitorDsl): IgnitorDsl =
        self.pregain()

    /**
     * Divides the signal by a divisor (IgnitorDsl or Number).
     *
     * Zero divisors are substituted with `1e-30` to keep the engine `NaN`-free.
     */
    @KlangScript.Method(name = "div")
    internal fun scriptDiv(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        self.div(other.toIgnitorDsl())

    /**
     * Negates this signal (flips polarity).
     *
     * @alias negate
     */
    @KlangScript.Method(name = "neg")
    internal fun scriptNeg(self: IgnitorDsl): IgnitorDsl =
        self.neg()

    /**
     * Negates this signal (flips polarity). Alias for [neg].
     *
     * @alias neg
     */
    @KlangScript.Method(name = "negate")
    internal fun scriptNegate(self: IgnitorDsl): IgnitorDsl =
        self.neg()

    /** Absolute value of this signal (full-wave rectification). */
    @KlangScript.Method(name = "abs")
    internal fun scriptAbs(self: IgnitorDsl): IgnitorDsl =
        self.abs()

    /**
     * Raises this signal to the power of [exp] (per-sample).
     * Signed-magnitude: negative bases produce `-(|base|^exp)` to avoid `NaN`.
     *
     */
    @KlangScript.Method(name = "pow")
    internal fun scriptPow(self: IgnitorDsl, exp: IgnitorDslLike): IgnitorDsl =
        self.pow(exp.toIgnitorDsl())

    /**
     * Raises this signal to the power of [exp]. Alias for [pow].
     *
     */
    @KlangScript.Method(name = "power")
    internal fun scriptPower(self: IgnitorDsl, exp: IgnitorDslLike): IgnitorDsl =
        self.pow(exp.toIgnitorDsl())

    /**
     * Enforces a minimum allowed value per sample: this signal, but at least [other].
     *
     * Reads as "take self, at a minimum other", the same meaning `min` carries on a number
     * and on a pattern. Anything below [other] is raised to it.
     *
     * The node crossing is deliberate: a floor is the per-sample *maximum* of the two
     * signals, so the `min` door builds [IgnitorDsl.Max]. Do not "correct" it.
     *
     * ```KlangScript(Playable)
     * sine.range(-1, 1).min(0)   // half-wave rectify
     * ```
     */
    @KlangScript.Method(name = "min")
    internal fun scriptMin(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        self.min(other.toIgnitorDsl())

    /**
     * Enforces a maximum allowed value per sample: this signal, but at most [other].
     *
     * Reads as "take self, at a maximum other", the same meaning `max` carries on a number
     * and on a pattern. Anything above [other] is lowered to it.
     *
     * The node crossing is deliberate: a cap is the per-sample *minimum* of the two
     * signals, so the `max` door builds [IgnitorDsl.Min]. Do not "correct" it.
     *
     * ```KlangScript(Playable)
     * sine.range(0, 2).max(0.8)   // cap the signal at 0.8
     * ```
     */
    @KlangScript.Method(name = "max")
    internal fun scriptMax(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        self.max(other.toIgnitorDsl())

    /** Bounds this signal to the range `[lo, hi]` per sample. */
    @KlangScript.Method(name = "clamp")
    internal fun scriptClamp(self: IgnitorDsl, lo: IgnitorDslLike, hi: IgnitorDslLike): IgnitorDsl =
        self.clamp(lo = lo.toIgnitorDsl(), hi = hi.toIgnitorDsl())

    /** `e^x` per sample. */
    @KlangScript.Method(name = "exp")
    internal fun scriptExp(self: IgnitorDsl): IgnitorDsl =
        self.exp()

    /** Natural logarithm per sample. Signed-magnitude; `log(0) = 0` (no `-Inf`). */
    @KlangScript.Method(name = "log")
    internal fun scriptLog(self: IgnitorDsl): IgnitorDsl =
        self.log()

    /** Square root per sample. Signed-magnitude (no `NaN` for negatives). */
    @KlangScript.Method(name = "sqrt")
    internal fun scriptSqrt(self: IgnitorDsl): IgnitorDsl =
        self.sqrt()

    /** Sign of this signal: `-1`, `0`, or `+1`. */
    @KlangScript.Method(name = "sign")
    internal fun scriptSign(self: IgnitorDsl): IgnitorDsl =
        self.sign()

    /** `tanh(x)` per sample (smooth saturation curve). */
    @KlangScript.Method(name = "tanh")
    internal fun scriptTanh(self: IgnitorDsl): IgnitorDsl =
        self.tanh()

    /**
     * Linear interpolation: `this·(1−t) + other·t`.
     *
     * @alias mix
     */
    @KlangScript.Method(name = "lerp")
    internal fun scriptLerp(self: IgnitorDsl, other: IgnitorDslLike, t: IgnitorDslLike): IgnitorDsl =
        self.lerp(other = other.toIgnitorDsl(), t = t.toIgnitorDsl())

    /**
     * Crossfade: `this·(1−t) + other·t`. Alias for [lerp].
     *
     * @alias lerp
     */
    @KlangScript.Method(name = "mix")
    internal fun scriptMix(self: IgnitorDsl, other: IgnitorDslLike, t: IgnitorDslLike): IgnitorDsl =
        self.lerp(other = other.toIgnitorDsl(), t = t.toIgnitorDsl())

    /**
     * Lets this signal swing between [from] and [to], per sample. The standard LFO scaler.
     *
     * The oscillators swing between `-1` and `1`; `range` maps `-1` to [from] and `1` to [to], linearly. Where the
     * swing sits is up to the two values:
     *
     * | Call              | The signal moves                                      |
     * |-------------------|-------------------------------------------------------|
     * | `range(0, 1)`     | only upward, between 0 and 1                          |
     * | `range(-1, 0)`    | only downward, between -1 and 0                       |
     * | `range(-1, 1)`    | both ways, centred on 0 (the oscillator as it is)     |
     * | `range(-0.5, 1)`  | mostly upward, dipping a little below 0               |
     * | `range(1, 0)`     | the same swing turned upside down                     |
     *
     * Sprudel's signals swing between `0` and `1` instead, and their `range(from, to)` gives the same result:
     * `x.range(200, 400)` swings between 200 and 400 in both DSLs. To get from `0..1` to `-1..1` without a range,
     * `x.mul(2).minus(1)`.
     *
     * ```KlangScript
     * Ignitor.saw().lowpass(Ignitor.sine(0.5).range(400, 2000))   // the cutoff sweeps 400 to 2000 Hz
     * Ignitor.sine().mul(Ignitor.sine(5).range(0.6, 1))            // a tremolo that only ever dips
     * ```
     */
    @KlangScript.Method(name = "range")
    internal fun scriptRange(self: IgnitorDsl, from: IgnitorDslLike, to: IgnitorDslLike): IgnitorDsl =
        self.range(from = from.toIgnitorDsl(), to = to.toIgnitorDsl())

    /**
     * Lets this signal swing between [from] and [to] exponentially, per sample: the exponential twin of [range],
     * perceptually even for frequencies.
     *
     * `range` takes equal steps of the signal to equal DIFFERENCES, `rangex` to equal RATIOS:
     * `from · (to / from)^((x + 1) / 2)`. So in a sweep from 200 to 3200 Hz each octave takes the same share of the
     * swing (with a saw or a triangle, the same time), which is what the ear hears as even; with `range` the low
     * octaves would rush by. A sine dwells at its ends, so it lingers in the lowest and the highest octave.
     *
     * | The oscillator is | `rangex(200, 3200)` gives                  |
     * |-------------------|--------------------------------------------|
     * | `-1`              | 200, [from]                                |
     * | `0`               | 800, the geometric mean `√(from · to)`     |
     * | `1`               | 3200, [to]                                 |
     *
     * `rangex(3200, 200)` turns the swing upside down. Both values are frequencies or other positive amounts: a
     * value at or below 0 (or not a number) is coerced to 0.0001, so nothing breaks, but that end of the sweep sits
     * at almost nothing.
     * Sprudel's `rangex(from, to)` is the same mapping on its `0..1` signals.
     *
     * ```KlangScript
     * Ignitor.saw().lowpass(Ignitor.sine(0.2).rangex(200, 3200))   // the cutoff sweeps four octaves, evenly
     * ```
     */
    @KlangScript.Method(name = "rangex")
    internal fun scriptRangex(self: IgnitorDsl, from: IgnitorDslLike, to: IgnitorDslLike): IgnitorDsl =
        self.rangex(from = from.toIgnitorDsl(), to = to.toIgnitorDsl())

    /** Per-sample floor. */
    @KlangScript.Method(name = "floor")
    internal fun scriptFloor(self: IgnitorDsl): IgnitorDsl =
        self.floor()

    /** Per-sample ceiling. */
    @KlangScript.Method(name = "ceil")
    internal fun scriptCeil(self: IgnitorDsl): IgnitorDsl =
        self.ceil()

    /** Per-sample round to nearest integer. */
    @KlangScript.Method(name = "round")
    internal fun scriptRound(self: IgnitorDsl): IgnitorDsl =
        self.round()

    /** Per-sample fractional part: `x − floor(x)`. */
    @KlangScript.Method(name = "frac")
    internal fun scriptFrac(self: IgnitorDsl): IgnitorDsl =
        self.frac()

    /**
     * Per-sample modulo. Zero divisors substituted with `1e-30` to avoid `NaN`.
     *
     * On a signal `mod` and `rem` are the same operation, the engine's `%`: the sign follows the LEFT
     * operand, so a negative value stays negative. This differs from `mod` on a number, which is the
     * floor modulo (`(-1).mod(12)` is 11); to wrap a negative signal into a range, add the range and
     * take `mod` again.
     */
    @KlangScript.Method(name = "mod")
    internal fun scriptMod(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        self.mod(other.toIgnitorDsl())

    /**
     * Per-sample modulo. Alias for [mod] (matches Kotlin's `rem`): on a signal the two are the same
     * operation, unlike `mod` and `rem` on a number.
     */
    @KlangScript.Method(name = "rem")
    internal fun scriptRem(self: IgnitorDsl, other: IgnitorDslLike): IgnitorDsl =
        self.mod(other.toIgnitorDsl())

    /**
     * Per-sample reciprocal: `1 / x`. Zero inputs substituted with `1e-30` to avoid `NaN`.
     *
     * @alias reciprocal
     */
    @KlangScript.Method(name = "recip")
    internal fun scriptRecip(self: IgnitorDsl): IgnitorDsl =
        self.recip()

    /**
     * Per-sample reciprocal: `1 / x`. Alias for [recip].
     *
     * @alias recip
     */
    @KlangScript.Method(name = "reciprocal")
    internal fun scriptReciprocal(self: IgnitorDsl): IgnitorDsl =
        self.recip()

    /** Per-sample square: `x · x`. */
    @KlangScript.Method(name = "sq")
    internal fun scriptSq(self: IgnitorDsl): IgnitorDsl =
        self.sq()

    /** Per-sample conditional: when this signal `> 0` use [whenTrue], else [whenFalse]. */
    @KlangScript.Method(name = "select")
    internal fun scriptSelect(self: IgnitorDsl, whenTrue: IgnitorDslLike, whenFalse: IgnitorDslLike): IgnitorDsl =
        self.select(whenTrue = whenTrue.toIgnitorDsl(), whenFalse = whenFalse.toIgnitorDsl())
}
