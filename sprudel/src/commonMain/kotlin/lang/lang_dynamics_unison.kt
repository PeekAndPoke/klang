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
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.putOscParam

// -- unison ----------------------------------------------------------------------------------------------------------

private val unisonVoicesMutation = voiceSetter { putOscParam("voices", it?.asDoubleOrNull()) }

private fun applyUnisonVoices(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.oscParams?.get("voices") }, update = unisonVoicesMutation)
    }

    return source._liftOrReinterpretNumericalField(args, unisonVoicesMutation)
}

private val unisonSpreadMutation = voiceSetter { putOscParam("spread", it?.asDoubleOrNull()) }

private fun applyUnisonSpread(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.oscParams?.get("spread") }, update = unisonSpreadMutation)
    }

    return source._liftOrReinterpretNumericalField(args, unisonSpreadMutation)
}

private val unisonPanMutation = voiceSetter { putOscParam("panSpread", it?.asDoubleOrNull()) }

private fun applyUnisonPan(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.oscParams?.get("panSpread") }, update = unisonPanMutation)
    }

    return source._liftOrReinterpretNumericalField(args, unisonPanMutation)
}

/**
 * Unison: voice count, detune spread and stereo spread.
 *
 * Stacks detuned copies of the oscillator [per voice](/manuals/lexikon/voice); `spread` is the
 * detune in semitones, `pan` the stereo width.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`unison(spread = mul(2))`), and the numeric slots read back as `unison.voices`, `unison.spread`, `unison.pan`.
 * With no argument at all, the pattern's own values are reinterpreted as `voices`.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("supersaw").unison(5, 0.3)                              // five voices, a third of a semitone apart
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("supersaw").unison(5, 0.3).unison(spread = mul("<1 3>"))   // wider every other bar
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("supersaw").unison("3 7", 0.2).gain(unison.voices.mul(0.1))   // more voices, louder
 * ```
 *
 * @param voices Voices. Typically 1 to 16.
 * @param spread Detune spread in semitones.
 * @param pan Stereo spread, 0 to 1. Reserved, not read yet.
 *
 * @scope voice
 * @category dynamics
 * @tags unison, voices, spread, pan
 */
@KlangScript.Function
fun SprudelPattern.unison(voices: PatternLike? = null, spread: PatternLike? = null, pan: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch voices: reinterpret runs only on a fully bare call.
    var p = if (voices != null || !(spread != null || pan != null)) {
        applyUnisonVoices(this, listOfNotNull(voices).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (spread != null) p = applyUnisonSpread(p, listOf<Any?>(spread).asSprudelDslArgs(callInfo?.forParam(1)))
    if (pan != null) p = applyUnisonPan(p, listOf<Any?>(pan).asSprudelDslArgs(callInfo?.forParam(2)))
    return p
}

/** Parses this string as a pattern, then applies [unison]. */
@KlangScript.Function
fun String.unison(voices: PatternLike? = null, spread: PatternLike? = null, pan: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).unison(voices, spread, pan, callInfo)

/** Chains a [unison] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.unison(voices: PatternLike? = null, spread: PatternLike? = null, pan: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.unison(voices, spread, pan, callInfo) }

/**
 * The `unison` object: `unison(...)` sets the slots, and each numeric slot reads back as a child,
 * `unison.voices`, `unison.spread`, `unison.pan`.
 *
 * @scope voice
 * @category dynamics
 * @tags unison, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("unison")
object unison {

    /** The voices slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val voices: FieldAccessor = FieldAccessor { it.oscParams?.get("voices") }

    /** The spread slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val spread: FieldAccessor = FieldAccessor { it.oscParams?.get("spread") }

    /** The pan slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val pan: FieldAccessor = FieldAccessor { it.oscParams?.get("panSpread") }

    /** The setter, see [SprudelPattern.unison]. */
    @KlangScript.Invoke
    operator fun invoke(voices: PatternLike? = null, spread: PatternLike? = null, pan: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.unison(voices, spread, pan, callInfo) }
}

/**
 * `uni`, the short name of [unison]: the same door, use whichever reads better.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("supersaw").uni(5, 0.3)
 * ```
 *
 * @scope voice
 * @category dynamics
 * @tags uni, unison
 */
@KlangScript.Function
fun SprudelPattern.uni(voices: PatternLike? = null, spread: PatternLike? = null, pan: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    unison(voices, spread, pan, callInfo)

/** Parses this string as a pattern, then applies [uni]. */
@KlangScript.Function
fun String.uni(voices: PatternLike? = null, spread: PatternLike? = null, pan: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.unison(voices, spread, pan, callInfo)

/**
 * Alias of [unison]: the same object under its short name.
 *
 * @scope voice
 * @category dynamics
 * @tags uni, unison, accessor
 */
@KlangScript.Constant
val uni: unison = unison

/** Chains a [uni] step onto this [PatternMapperFn] (see [SprudelPattern.uni]). */
@KlangScript.Function
fun PatternMapperFn.uni(voices: PatternLike? = null, spread: PatternLike? = null, pan: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.unison(voices, spread, pan, callInfo)

// -- density() / d() --------------------------------------------------------------------------------------------------

private val densityMutation = voiceSetter { putOscParam("density", it?.asDoubleOrNull()) }

private fun applyDensity(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.oscParams?.get("density") }, update = densityMutation)
    }

    return source._liftOrReinterpretStringField(args, densityMutation)
}

/**
 * Sets the impulse rate of the `dust` generator.
 *
 * The value is 0 to 1 and scales a 200 per second ceiling, so 0.2 is 40 impulses per second.
 *
 * `dust` is the only generator that reads it: the super oscillators take `voices`, `spread` and
 * `analog`, so reach for `spread` to tighten a supersaw, and `crackle` is driven by `chaos`.
 *
 * ```KlangScript(Playable)
 * note("a").s("dust").density(0.2)   // 40 impulses per second
 * ```
 *
 * ```KlangScript(Playable)
 * note("a").s("dust").density("<0.1 0.6>")   // sparse, then busy
 * ```
 *
 * @param amount Impulse rate for `dust`, 0 to 1.
 *
 * @alias d
 * @scope voice
 * @category dynamics
 * @tags density, d, dust, noise
 */
@KlangScript.Function
fun SprudelPattern.density(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDensity(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the oscillator or noise density.
 *
 * ```KlangScript(Playable)
 * "a".density(0.2).s("dust").note()   // 40 noise events per second
 * ```
 *
 * @param amount The oscillator density.
 */
@KlangScript.Function
fun String.density(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).density(amount, callInfo)

/**
 * The noise density of each event (`dust`; 0..1, mapped to grains per second), as a value
 * other setters can read.
 *
 * Bare `density` reads what the chain has set so far, so it comes after whatever set the field
 * (`density(...)` or an alias). Call it, `density(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `d`.
 *
 * ```KlangScript(Playable)
 * s("dust*2").density(0.3).density(add("0 0.4"))                           // the second grain cloud denser
 * ```
 *
 * ```KlangScript(Playable)
 * s("dust*2").density("0.2 0.8").gain(density)                             // denser, louder
 * ```
 *
 * @scope voice
 * @category dynamics
 * @tags density, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("density")
object density : FieldAccessor({ it.oscParams?.get("density") }) {

    /**
     * Parses this string as a pattern and sets the oscillator or noise density.
     *
     * ```KlangScript(Playable)
     * "a".apply(density(0.2)).s("dust").note()   // 40 noise events per second
     * ```
     * @param amount The oscillator density.
     */
    @KlangScript.Invoke
    operator fun invoke(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.density(amount, callInfo) }
}

/**
 * Creates a chained [PatternMapperFn] that sets the oscillator or noise density after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("a").s("dust").apply(gain(0.8).density(0.5))  // gain + density chained
 * ```
 *
 * @param amount The oscillator density.
 */
@KlangScript.Function
fun PatternMapperFn.density(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.density(amount, callInfo) }

/**
 * Alias for [density]. Sets the impulse rate of the `dust` generator.
 *
 * ```KlangScript(Playable)
 * note("a").s("dust").d(0.2)   // 40 impulses per second
 * ```
 *
 * ```KlangScript(Playable)
 * note("a").s("dust").d("<0.1 0.6>")   // sparse, then busy
 * ```
 *
 * @param amount Impulse rate for `dust`, 0 to 1.
 *
 * @alias density
 * @scope voice
 * @category dynamics
 * @tags d, density, dust, noise
 */
@KlangScript.Function
fun SprudelPattern.d(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.density(amount, callInfo)

/**
 * Alias for [density]. Parses this string as a pattern and sets the oscillator or noise density.
 *
 * ```KlangScript(Playable)
 * "a".d(0.2).s("dust").note()   // 40 noise events per second
 * ```
 *
 * @param amount The oscillator density.
 */
@KlangScript.Function
fun String.d(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).density(amount, callInfo)

/**
 * Alias of [density]: the same accessor under another name.
 *
 * @scope voice
 * @category dynamics
 * @tags d, density, accessor
 */
@KlangScript.Constant
val d: density = density

/**
 * Alias for [density]. Creates a chained [PatternMapperFn] that sets the oscillator or noise density after the
 * previous mapper.
 *
 * ```KlangScript(Playable)
 * note("a").s("dust").apply(gain(0.8).d(0.5))  // gain + density chained
 * ```
 *
 * @param amount The oscillator density.
 */
@KlangScript.Function
fun PatternMapperFn.d(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.density(amount, callInfo) }
