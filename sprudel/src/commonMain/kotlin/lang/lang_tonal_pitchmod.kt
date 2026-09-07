/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData
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
 * A pitch wobble; the rate is how fast, the depth how far.
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
 * note("c4 e4").s("saw").vibrato("3 7", 0.5).penv(vibrato.rate)           // a pitch rise as wide as the rate
 * ```
 *
 * @param rate LFO rate in Hz; 3 is gentle, 5 standard, 7 nervous.
 * @param depth Depth in semitones; 0.2 is subtle, 0.5 expressive, 1 a wide wobble.

 *
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

private val penvReleaseMutation = voiceSetter { pRelease = it?.asDoubleOrNull() }

private fun applyPenvRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.pRelease }, update = penvReleaseMutation)
    }

    return source._liftOrReinterpretNumericalField(args, penvReleaseMutation)
}

private val penvCurveMutation = voiceSetter { pCurve = it?.asDoubleOrNull() }

private fun applyPenvCurve(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.pCurve }, update = penvCurveMutation)
    }

    return source._liftOrReinterpretNumericalField(args, penvCurveMutation)
}

private val penvAnchorMutation = voiceSetter { pAnchor = it?.asDoubleOrNull() }

private fun applyPenvAnchor(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.pAnchor }, update = penvAnchorMutation)
    }

    return source._liftOrReinterpretNumericalField(args, penvAnchorMutation)
}

/**
 * The pitch envelope: depth in semitones, its attack, decay and release, curve and sustain anchor.
 *
 * Pitch starts `amount` semitones away and glides home along the envelope.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`penv(attack = mul(2))`), and the numeric slots read back as `penv.amount`, `penv.attack`, `penv.decay`, `penv.release`, `penv.curve`, `penv.anchor`.
 * With no argument at all, the pattern's own values are reinterpreted as `amount`.
 *
 * ```KlangScript(Playable)
 * s("bd*4").penv(24, 0.001, 0.08)                                         // a kick with a pitch drop
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd*4").penv(24, 0.001, 0.08).penv(amount = mul("1 0.5"))            // half the drop on every second hit
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4*4").s("saw").penv("12 -12", 0.01, 0.2).lpf(penv.amount.mul(100).add(2000))   // brighter with the rise
 * ```
 *
 * @param amount Depth in semitones; 12 is an octave up, -12 an octave down, 0 no pitch envelope.
 * @param attack Attack in seconds; 0.01 is instant, 0.1 snappy.
 * @param decay Decay in seconds; 0.05 is snappy, 0.2 moderate.
 * @param release Release in seconds, how fast the pitch returns after the note ends.
 * @param curve Curve shape: 1 is linear, below 1 concave (fast start), above 1 convex (slow start).
 * @param anchor Sustain pitch offset, -1 to 1; 0 returns to the note.

 *
 * @category tonal
 * @tags penv, amount, attack, decay, release, curve, anchor
 */
@KlangScript.Function
fun SprudelPattern.penv(
    amount: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    release: PatternLike? = null,
    curve: PatternLike? = null,
    anchor: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern {
    // A tail-only call must not touch amount: reinterpret runs only on a fully bare call.
    var p = if (amount != null || !(attack != null || decay != null || release != null || curve != null || anchor != null)) {
        applyPenvAmount(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))
    } else {
        this
    }

    if (attack != null) p = applyPenvAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(1)))
    if (decay != null) p = applyPenvDecay(p, listOf<Any?>(decay).asSprudelDslArgs(callInfo?.forParam(2)))
    if (release != null) p = applyPenvRelease(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(3)))
    if (curve != null) p = applyPenvCurve(p, listOf<Any?>(curve).asSprudelDslArgs(callInfo?.forParam(4)))
    if (anchor != null) p = applyPenvAnchor(p, listOf<Any?>(anchor).asSprudelDslArgs(callInfo?.forParam(5)))

    return p
}

/** Parses this string as a pattern, then applies [penv]. */
@KlangScript.Function
fun String.penv(
    amount: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    release: PatternLike? = null,
    curve: PatternLike? = null,
    anchor: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).penv(amount, attack, decay, release, curve, anchor, callInfo)

/** Chains a [penv] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.penv(
    amount: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    release: PatternLike? = null,
    curve: PatternLike? = null,
    anchor: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.chain { p -> p.penv(amount, attack, decay, release, curve, anchor, callInfo) }

/**
 * The `penv` object: `penv(...)` sets the slots, and each numeric slot reads back as a child,
 * `penv.amount`, `penv.attack`, `penv.decay`, `penv.release`, `penv.curve`, `penv.anchor`.
 *
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

    /** The release slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val release: FieldAccessor = FieldAccessor { it.pRelease }

    /** The curve slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val curve: FieldAccessor = FieldAccessor { it.pCurve }

    /** The anchor slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val anchor: FieldAccessor = FieldAccessor { it.pAnchor }

    /** The setter, see [SprudelPattern.penv]. */
    @KlangScript.Invoke
    operator fun invoke(
        amount: PatternLike? = null,
        attack: PatternLike? = null,
        decay: PatternLike? = null,
        release: PatternLike? = null,
        curve: PatternLike? = null,
        anchor: PatternLike? = null,
        callInfo: CallInfo? = null
    ): PatternMapperFn =
        { p -> p.penv(amount, attack, decay, release, curve, anchor, callInfo) }
}

/**
 * `pamt`, the short name of [penv]: the same door, use whichever reads better.
 *
 * ```KlangScript(Playable)
 * s("bd*4").pamt(24, 0.001, 0.08)
 * ```
 *
 * @category tonal
 * @tags pamt, penv

 */
@KlangScript.Function
fun SprudelPattern.pamt(
    amount: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    release: PatternLike? = null,
    curve: PatternLike? = null,
    anchor: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    penv(amount, attack, decay, release, curve, anchor, callInfo)

/** Parses this string as a pattern, then applies [pamt]. */
@KlangScript.Function
fun String.pamt(
    amount: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    release: PatternLike? = null,
    curve: PatternLike? = null,
    anchor: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.penv(amount, attack, decay, release, curve, anchor, callInfo)

/**
 * Alias of [penv]: the same object under its short name.
 *
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
    release: PatternLike? = null,
    curve: PatternLike? = null,
    anchor: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.penv(amount, attack, decay, release, curve, anchor, callInfo)

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
 * Controls a continuous pitch change during sample playback. Useful for pitched percussion
 * or sweep effects. When called with no argument, reinterprets the current event value as
 * the semitone amount. (Unit changed from octaves to semitones in the pitch-param
 * unification, 2026-08-24 — old scripts' values are 12× subtler now.)
 *
 * ```KlangScript(Playable)
 * s("cr").accelerate(24)             // crash pitches two octaves up during playback
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh").accelerate("<0 -24 24>")   // alternate: no ramp, down, up per cycle
 * ```
 *
 * @param semitones Pitch bend over the voice's duration in SEMITONES. 0.0 = no bend, +12 = one octave up, -12 = one octave down. Default: 0.0. Typical range: -24 to 24.
 *
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
