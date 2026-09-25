/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

// -- vibrato ---------------------------------------------------------------------------------------------------------

private val vibratoRateMutation = voiceSetter { vibrato = it?.asDoubleOrNull() ?: vibrato }

private fun applyVibratoRate(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.vibrato }, update = vibratoRateMutation)
    }

    return source._liftOrReinterpretNumericalField(args, vibratoRateMutation)
}

private val vibratoDepthMutation = voiceSetter { vibratoMod = it?.asDoubleOrNull() }

private fun applyVibratoDepth(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.vibratoMod }, update = vibratoDepthMutation)
    }

    return source._liftOrReinterpretNumericalField(args, vibratoDepthMutation)
}

/**
 * Vibrato: LFO rate in Hz and depth in semitones.
 *
 * A pitch wobble on the note [per voice](/manuals/lexikon/voice): the rate is how fast, the depth
 * how far. A rate of 3 Hz is gentle, 5 standard, 7 nervous; a depth of 0.2 semitones is subtle,
 * 0.5 expressive, 1 a wide wobble.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`vibrato(depth = mul(2))`), and the numeric slots read back as `vibrato.rate`, `vibrato.depth`.
 * With no argument at all, the pattern's own values are reinterpreted as `rate`.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").s("saw").vibrato(5, 0.5)                                  // a singing vibrato
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").s("saw").vibrato(5, 0.5).vibrato(rate = mul("1 1.5"))     // the second note wobbles faster
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").s("saw").vibrato("3 7", 0.5).penv(vibrato.rate)           // an onset blip as many semitones wide as the rate
 * ```
 *
 * @param rate LFO rate in Hz.
 * @param depth Depth in semitones.
 *
 * @scope voice
 * @category tonal
 * @tags vibrato, rate, depth
 */
@KlangScript.Function
fun SprudelPattern.vibrato(rate: PatternLike? = null, depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch rate: reinterpret runs only on a fully bare call.
    var p = if (rate != null || !(depth != null)) {
        applyVibratoRate(this, listOfNotNull(rate).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (depth != null) p = applyVibratoDepth(p, listOf<Any?>(depth).asSprudelDslArgs(callInfo?.forParam(1)))
    return p
}

/** Parses this string as a pattern, then applies [vibrato]. */
@KlangScript.Function
fun String.vibrato(rate: PatternLike? = null, depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).vibrato(rate, depth, callInfo)

/** Chains a [vibrato] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.vibrato(rate: PatternLike? = null, depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.vibrato(rate, depth, callInfo) }

/**
 * The `vibrato` object: `vibrato(...)` sets the slots, and each numeric slot reads back as a child,
 * `vibrato.rate`, `vibrato.depth`.
 *
 * @scope voice
 * @category tonal
 * @tags vibrato, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("vibrato")
object vibrato {

    /** The rate slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val rate: FieldAccessor = FieldAccessor { it.vibrato }

    /** The depth slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val depth: FieldAccessor = FieldAccessor { it.vibratoMod }

    /** The setter, see [SprudelPattern.vibrato]. */
    @KlangScript.Invoke
    operator fun invoke(rate: PatternLike? = null, depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.vibrato(rate, depth, callInfo) }
}

/**
 * `vib`, the short name of [vibrato]: the same door, use whichever reads better.
 *
 * ```KlangScript(Playable)
 * note("c4 e4").s("saw").vib(5, 0.5)
 * ```
 *
 * @scope voice
 * @category tonal
 * @tags vib, vibrato
 */
@KlangScript.Function
fun SprudelPattern.vib(rate: PatternLike? = null, depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    vibrato(rate, depth, callInfo)

/** Parses this string as a pattern, then applies [vib]. */
@KlangScript.Function
fun String.vib(rate: PatternLike? = null, depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.vibrato(rate, depth, callInfo)

/**
 * Alias of [vibrato]: the same object under its short name.
 *
 * @scope voice
 * @category tonal
 * @tags vib, vibrato, accessor
 */
@KlangScript.Constant
val vib: vibrato = vibrato

/** Chains a [vib] step onto this [PatternMapperFn] (see [SprudelPattern.vib]). */
@KlangScript.Function
fun PatternMapperFn.vib(rate: PatternLike? = null, depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.vibrato(rate, depth, callInfo)

// -- penv ------------------------------------------------------------------------------------------------------------

private val penvAmountMutation = voiceSetter { pEnv = it?.asDoubleOrNull() ?: pEnv }

private fun applyPenvAmount(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.pEnv }, update = penvAmountMutation)
    }

    return source._liftOrReinterpretNumericalField(args, penvAmountMutation)
}

private val penvAttackMutation = voiceSetter { pAttack = it?.asDoubleOrNull() }

private fun applyPenvAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.pAttack }, update = penvAttackMutation)
    }

    return source._liftOrReinterpretNumericalField(args, penvAttackMutation)
}

private val penvDecayMutation = voiceSetter { pDecay = it?.asDoubleOrNull() }

private fun applyPenvDecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.pDecay }, update = penvDecayMutation)
    }

    return source._liftOrReinterpretNumericalField(args, penvDecayMutation)
}

private val penvSustainMutation = voiceSetter { pSustain = it?.asDoubleOrNull() }

private fun applyPenvSustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.pSustain }, update = penvSustainMutation)
    }

    return source._liftOrReinterpretNumericalField(args, penvSustainMutation)
}

private val penvReleaseMutation = voiceSetter { pRelease = it?.asDoubleOrNull() }

private fun applyPenvRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.pRelease }, update = penvReleaseMutation)
    }

    return source._liftOrReinterpretNumericalField(args, penvReleaseMutation)
}

/**
 * The pitch envelope: its depth in semitones, and its attack, decay, sustain and release.
 *
 * The pitch rises `amount` semitones away from the note over `attack`, falls back to the `sustain`
 * share of `amount` over `decay`, holds there while the note is on, and after the note ends returns
 * to the note over `release`. 12 is an octave up, -12 an octave down, 0 no pitch envelope at all.
 * An unwritten stage is the Ignitor `pitchEnvelope`'s default: attack 0.01, decay 0.1, sustain 0
 * (back on the note), release 0 (on the note at the note's end). Each stage bends exponentially
 * unless [penvCurves] shapes it. The release does not make the note ring longer.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`penv(attack = mul(2))`), and the slots read back as `penv.amount`, `penv.attack`,
 * `penv.decay`, `penv.sustain`, `penv.release`. With no argument at all, the pattern's own values
 * are reinterpreted as `amount`.
 *
 * ```KlangScript(Playable)
 * note("c2*4").s("sine").penv(24, 0.001, 0.08)                               // a kick: two octaves down onto the note
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 ~ e3 ~").s("saw").penv(12, 0.02, 0.1, 0.5, 0.3).adsr(0.01, 0.1, 0.8, 0.5)   // holds half an octave up, glides home after
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2*4").s("sine").penv(24, 0.001, 0.08).penv(amount = mul("1 0.5"))   // half the drop on every second hit
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4*4").s("saw").penv("12 -12", 0.01, 0.2).lpf(penv.amount.mul(100).add(2000))   // brighter with the rise
 * ```
 *
 * @param amount Depth in semitones, the pitch at the envelope's peak.
 * @param attack Attack in seconds.
 * @param decay Decay in seconds.
 * @param sustain Held share of `amount` while the note is on; 0 is the note itself.
 * @param release Release in seconds, back to the note after the note ends.
 *
 * @scope voice
 * @category tonal
 * @tags penv, amount, attack, decay, sustain, release, pitch envelope
 */
@KlangScript.Function
fun SprudelPattern.penv(
    amount: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern {
    // A tail-only call must not touch amount: reinterpret runs only on a fully bare call.
    var p = if (amount != null || !(attack != null || decay != null || sustain != null || release != null)) {
        applyPenvAmount(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))
    } else {
        this
    }

    if (attack != null) p = applyPenvAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(1)))
    if (decay != null) p = applyPenvDecay(p, listOf<Any?>(decay).asSprudelDslArgs(callInfo?.forParam(2)))
    if (sustain != null) p = applyPenvSustain(p, listOf<Any?>(sustain).asSprudelDslArgs(callInfo?.forParam(3)))
    if (release != null) p = applyPenvRelease(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(4)))

    return p
}

/** Parses this string as a pattern, then applies [penv]. */
@KlangScript.Function
fun String.penv(
    amount: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).penv(amount, attack, decay, sustain, release, callInfo)

/** Chains a [penv] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.penv(
    amount: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.chain { p -> p.penv(amount, attack, decay, sustain, release, callInfo) }

/**
 * The `penv` object: `penv(...)` sets the slots, and each slot reads back as a child,
 * `penv.amount`, `penv.attack`, `penv.decay`, `penv.sustain`, `penv.release`.
 *
 * @scope voice
 * @category tonal
 * @tags penv, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("penv")
object penv {

    /** The amount slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val amount: FieldAccessor = FieldAccessor { it.pEnv }

    /** The attack slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val attack: FieldAccessor = FieldAccessor { it.pAttack }

    /** The decay slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val decay: FieldAccessor = FieldAccessor { it.pDecay }

    /** The sustain slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val sustain: FieldAccessor = FieldAccessor { it.pSustain }

    /** The release slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val release: FieldAccessor = FieldAccessor { it.pRelease }

    /** The setter, see [SprudelPattern.penv]. */
    @KlangScript.Invoke
    operator fun invoke(
        amount: PatternLike? = null,
        attack: PatternLike? = null,
        decay: PatternLike? = null,
        sustain: PatternLike? = null,
        release: PatternLike? = null,
        callInfo: CallInfo? = null
    ): PatternMapperFn =
        { p -> p.penv(amount, attack, decay, sustain, release, callInfo) }
}

/**
 * `pamt`, the short name of [penv]: the same door, use whichever reads better.
 *
 * ```KlangScript(Playable)
 * note("c2*4").s("sine").pamt(24, 0.001, 0.08)
 * ```
 *
 * @scope voice
 * @category tonal
 * @tags pamt, penv
 */
@KlangScript.Function
fun SprudelPattern.pamt(
    amount: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    penv(amount, attack, decay, sustain, release, callInfo)

/** Parses this string as a pattern, then applies [pamt]. */
@KlangScript.Function
fun String.pamt(
    amount: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.penv(amount, attack, decay, sustain, release, callInfo)

/**
 * Alias of [penv]: the same object under its short name.
 *
 * @scope voice
 * @category tonal
 * @tags pamt, penv, accessor
 */
@KlangScript.Constant
val pamt: penv = penv

/** Chains a [pamt] step onto this [PatternMapperFn] (see [SprudelPattern.pamt]). */
@KlangScript.Function
fun PatternMapperFn.pamt(
    amount: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.penv(amount, attack, decay, sustain, release, callInfo)

// -- penvCurves ------------------------------------------------------------------------------------------------------

// The curve names have ONE home, `AdsrCurves` in audio_bridge; the rule is `adsrCurves`': an unknown
// name, like an omitted stage, keeps the stage's current curve.
private val penvAttackCurveMutation = voiceSetter {
    pAttackCurve = AdsrCurves.curveOf(it?.toString()) ?: pAttackCurve
}

private val penvDecayCurveMutation = voiceSetter {
    pDecayCurve = AdsrCurves.curveOf(it?.toString()) ?: pDecayCurve
}

private val penvReleaseCurveMutation = voiceSetter {
    pReleaseCurve = AdsrCurves.curveOf(it?.toString()) ?: pReleaseCurve
}

private fun applyPenvAttackCurve(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, penvAttackCurveMutation) { src, ctrl ->
        src.pAttackCurve = ctrl.pAttackCurve ?: src.pAttackCurve
        src
    }
}

private fun applyPenvDecayCurve(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, penvDecayCurveMutation) { src, ctrl ->
        src.pDecayCurve = ctrl.pDecayCurve ?: src.pDecayCurve
        src
    }
}

private fun applyPenvReleaseCurve(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, penvReleaseCurveMutation) { src, ctrl ->
        src.pReleaseCurve = ctrl.pReleaseCurve ?: src.pReleaseCurve
        src
    }
}

/**
 * Sets the shape of each stage of the pitch envelope ([penv]), the way [adsrCurves] shapes the
 * amplitude envelope. Each stage is independent; an omitted stage, or an unknown curve name, keeps
 * its current curve, so `penvCurves(decay = "linear")` changes only the decay.
 *
 * The curves are the same six as `adsrCurves`: `linear`, `square`, `cube`, `scurve`, `invsquare`,
 * `exponential`, with the same aliases. Unset, every stage is `exponential`, the default of every
 * modulation envelope on every surface (decision D3). A curve alone switches nothing on: without a
 * `penv` amount there is no pitch envelope to shape.
 *
 * ```KlangScript(Playable)
 * note("c2*4").s("sine").penv(24, 0.001, 0.08).penvCurves(decay = "linear")   // a straight drop, the old sweep
 * ```
 *
 * @param attack Curve name for the attack stage.
 * @param decay Curve name for the decay stage.
 * @param release Curve name for the release stage.
 *
 * @scope voice
 * @category tonal
 * @tags penv, curve, envelope, shape, pitch envelope
 */
@KlangScript.Function
fun SprudelPattern.penvCurves(
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null,
): SprudelPattern {
    var p = this
    if (attack != null) p = applyPenvAttackCurve(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(0)))
    if (decay != null) p = applyPenvDecayCurve(p, listOf<Any?>(decay).asSprudelDslArgs(callInfo?.forParam(1)))
    if (release != null) p = applyPenvReleaseCurve(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(2)))
    return p
}

/**
 * Parses this string as a pattern and sets the pitch envelope's stage curves (see [penvCurves]).
 *
 * @param attack Curve name for the attack stage.
 * @param decay Curve name for the decay stage.
 * @param release Curve name for the release stage.
 */
@KlangScript.Function
fun String.penvCurves(
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null,
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).penvCurves(attack, decay, release, callInfo)

/**
 * The `penvCurves` object: `penvCurves(attack, decay, release)` sets the pitch envelope's stage curves
 * by name. The slots are names, not numbers, so the object carries the setter only and no readers
 * (the `adsrCurves` rule).
 *
 * ```KlangScript(Playable)
 * note("c2*4").s("sine").penv(24, 0.001, 0.08).apply(penvCurves("linear", "linear", "linear"))
 * ```
 *
 * @scope voice
 * @category tonal
 * @tags penv, curve, envelope, shape, pitch envelope
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("penvCurves")
object penvCurves {

    /** The setter, see [SprudelPattern.penvCurves]. */
    @KlangScript.Invoke
    operator fun invoke(
        attack: PatternLike? = null,
        decay: PatternLike? = null,
        release: PatternLike? = null,
        callInfo: CallInfo? = null,
    ): PatternMapperFn = { p -> p.penvCurves(attack, decay, release, callInfo) }
}

/**
 * Creates a chained [PatternMapperFn] that sets the pitch envelope's stage curves after the previous mapper.
 *
 * @param attack Curve name for the attack stage. Omit to keep the current curve.
 * @param decay Curve name for the decay stage. Omit to keep the current curve.
 * @param release Curve name for the release stage. Omit to keep the current curve.
 */
@KlangScript.Function
fun PatternMapperFn.penvCurves(
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null,
): PatternMapperFn =
    this.chain { p -> p.penvCurves(attack, decay, release, callInfo) }

// -- accelerate() -----------------------------------------------------------------------------------------------------

private val accelerateUpdate: SprudelVoiceData.(Double?) -> SprudelVoiceData = { semitones ->
    clone().also { it.accelerate = semitones }
}

private fun applyAccelerate(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.accelerate }, update = accelerateUpdate)
    }

    return source._liftOrReinterpretNumericalField(args, accelerateUpdate)
}

/**
 * Sets the playback acceleration (pitch ramp) for each event, in SEMITONES over the event's
 * duration: `accelerate(12)` glides one octave up, `accelerate(-12)` one octave down.
 *
 * A continuous pitch change during sample playback, good for pitched percussion and sweeps. With
 * no argument it reinterprets the current event value as the semitone amount. (The unit changed
 * from octaves to semitones in the pitch-param unification, 2026-08-24, so old scripts' values are
 * 12× subtler now.)
 *
 * ```KlangScript(Playable)
 * s("cr").accelerate(24)             // crash pitches two octaves up during playback
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh").accelerate("<0 -24 24>")   // alternate: no ramp, down, up per cycle
 * ```
 *
 * @param semitones Pitch bend in semitones. Typically -24 to 24.
 *
 * @scope voice
 * @category tonal
 * @tags accelerate, pitch ramp, pitch bend, playback speed
 */
@KlangScript.Function
fun SprudelPattern.accelerate(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyAccelerate(this, listOfNotNull(semitones).asSprudelDslArgs(callInfo))

/** Sets the playback acceleration on a string pattern. */
@KlangScript.Function
fun String.accelerate(semitones: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).accelerate(semitones, callInfo)

/**
 * The pitch ramp of each event in semitones, as a value other setters can read.
 *
 * Bare `accelerate` reads what the chain has set so far, so it comes after whatever set the field
 * (`accelerate(...)` or an alias). Call it, `accelerate(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * ```KlangScript(Playable)
 * s("bd*4").accelerate(-2).accelerate(mul("1 2 1 4"))                       // deeper drops on every second hit
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4 e4").accelerate("2 -2").pan(accelerate.mul(0.25).add(0.5))    // up goes right, down goes left
 * ```
 *
 * @scope voice
 * @category tonal
 * @tags accelerate, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("accelerate")
object accelerate : FieldAccessor({ it.accelerate }) {

    /**
     * Returns a [PatternMapperFn] that sets the playback acceleration (pitch ramp).
     * When called with no argument, reinterprets the current event value as an acceleration amount.
     *
     * ```KlangScript(Playable)
     * s("hh").apply(accelerate(24))  // mapper form
     * ```
     */
    @KlangScript.Invoke
    operator fun invoke(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.accelerate(semitones, callInfo) }
}

/** Chains an accelerate operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.accelerate(semitones: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.accelerate(semitones, callInfo) }
