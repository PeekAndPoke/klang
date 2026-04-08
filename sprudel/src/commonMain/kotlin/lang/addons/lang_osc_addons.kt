@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.sprudel.lang.addons

import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel.lang.PatternLike
import io.peekandpoke.klang.sprudel.lang.PatternMapperFn
import io.peekandpoke.klang.sprudel.lang.SprudelDsl
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.asDoubleOrNull
import io.peekandpoke.klang.sprudel.lang.chain
import io.peekandpoke.klang.sprudel.lang.dslPatternExtension
import io.peekandpoke.klang.sprudel.lang.dslPatternMapper
import io.peekandpoke.klang.sprudel.lang.dslPatternMapperExtension
import io.peekandpoke.klang.sprudel.lang.dslStringExtension
import io.peekandpoke.klang.sprudel.lang.voiceModifier
import io.peekandpoke.klang.sprudel.withOscParam

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangOscAddonsInit = false

// -- oscparam() / oscp() ----------------------------------------------------------------------------------------------

private fun applyOscparam(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) return source
    val key = args[0].value?.toString() ?: return source
    val valueArgs = args.drop(1)
    val mutation = voiceModifier { withOscParam(key, it?.asDoubleOrNull()) }
    return source._liftOrReinterpretStringField(valueArgs, mutation)
}

internal val SprudelPattern._oscparam by dslPatternExtension { p, args, _ -> applyOscparam(p, args) }
internal val String._oscparam by dslStringExtension { p, args, callInfo -> p._oscparam(args, callInfo) }
internal val _oscparam by dslPatternMapper { args, callInfo -> { p -> p._oscparam(args, callInfo) } }
internal val PatternMapperFn._oscparam by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_oscparam(args, callInfo))
}

// Alias: oscp
internal val SprudelPattern._oscp by dslPatternExtension { p, args, _ -> applyOscparam(p, args) }
internal val String._oscp by dslStringExtension { p, args, callInfo -> p._oscp(args, callInfo) }
internal val _oscp by dslPatternMapper { args, callInfo -> { p -> p._oscp(args, callInfo) } }
internal val PatternMapperFn._oscp by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_oscp(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets an arbitrary oscillator parameter by key.
 *
 * Provides direct access to the `oscParams` map for custom oscillator parameters
 * that don't have dedicated DSL functions.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("supersaw").oscparam("analog", 0.2)
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").oscparam("warmth", "<0.2 0.8>")    // pattern-cycle the value
 * ```
 *
 * @param key The oscillator parameter name (e.g. "analog", "warmth", "density").
 * @param value The parameter value.
 * @return A new pattern with the oscillator parameter set.
 * @category tonal
 * @tags oscillator, parameter, osc, addon
 */
@SprudelDsl
fun SprudelPattern.oscparam(key: String, value: PatternLike): SprudelPattern =
    this._oscparam(listOf(key, value).asSprudelDslArgs())

/** Parses this string as a pattern and sets an oscillator parameter. */
@SprudelDsl
fun String.oscparam(key: String, value: PatternLike): SprudelPattern =
    this._oscparam(listOf(key, value).asSprudelDslArgs())

/** Creates a [PatternMapperFn] that sets an oscillator parameter. */
@SprudelDsl
fun oscparam(key: String, value: PatternLike): PatternMapperFn =
    _oscparam(listOf(key, value).asSprudelDslArgs())

/** Chains an oscillator-parameter-set onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.oscparam(key: String, value: PatternLike): PatternMapperFn =
    this._oscparam(listOf(key, value).asSprudelDslArgs())

/** Alias for [oscparam]. */
@SprudelDsl
fun SprudelPattern.oscp(key: String, value: PatternLike): SprudelPattern =
    this._oscp(listOf(key, value).asSprudelDslArgs())

/** Alias for [oscparam]. Parses this string as a pattern and sets an oscillator parameter. */
@SprudelDsl
fun String.oscp(key: String, value: PatternLike): SprudelPattern =
    this._oscp(listOf(key, value).asSprudelDslArgs())

/** Alias for [oscparam]. Creates a [PatternMapperFn] that sets an oscillator parameter. */
@SprudelDsl
fun oscp(key: String, value: PatternLike): PatternMapperFn =
    _oscp(listOf(key, value).asSprudelDslArgs())

/** Alias for [oscparam]. Chains an oscillator-parameter-set onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.oscp(key: String, value: PatternLike): PatternMapperFn =
    this._oscp(listOf(key, value).asSprudelDslArgs())

// -- analog() ---------------------------------------------------------------------------------------------------------

private val analogMutation = voiceModifier {
    withOscParam("analog", it?.asDoubleOrNull())
}

private fun applyAnalog(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args, analogMutation)
}

internal val SprudelPattern._analog by dslPatternExtension { p, args, /* callInfo */ _ -> applyAnalog(p, args) }
internal val String._analog by dslStringExtension { p, args, callInfo -> p._analog(args, callInfo) }
internal val _analog by dslPatternMapper { args, callInfo -> { p -> p._analog(args, callInfo) } }
internal val PatternMapperFn._analog by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_analog(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Adds analog oscillator drift to the sound.
 *
 * Simulates the micro-pitch instabilities of real analog VCOs by adding tiny random
 * perturbations to the oscillator's phase increment. For unison/super oscillators,
 * each voice drifts independently, creating lush analog-like chorusing.
 *
 * A value of `0.0` gives a perfectly stable digital sound; `1.0` gives maximum drift.
 * Typical values are `0.05`–`0.3` for subtle warmth.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").analog(0.2)   // lush analog supersaw
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("sine").analog("<0 0.1 0.3>")   // cycle through drift amounts
 * ```
 *
 * @param amount The analog drift amount between 0.0 (digital) and 1.0 (maximum drift).
 * @return A new pattern with analog drift applied.
 * @category tonal
 * @tags analog, drift, oscillator, warmth, vco, addon
 */
@SprudelDsl
fun SprudelPattern.analog(amount: PatternLike? = null): SprudelPattern =
    this._analog(listOfNotNull(amount).asSprudelDslArgs())

/**
 * Parses this string as a pattern and sets the analog drift amount.
 *
 * ```KlangScript(Playable)
 * "c3 e3".analog(0.2).s("supersaw").note()
 * ```
 *
 * @param amount The analog drift amount between 0.0 (digital) and 1.0 (maximum drift).
 * @return A new pattern with analog drift applied.
 * @category tonal
 * @tags analog, drift, oscillator, warmth, vco, addon
 */
@SprudelDsl
fun String.analog(amount: PatternLike? = null): SprudelPattern =
    this._analog(listOfNotNull(amount).asSprudelDslArgs())

/**
 * Creates a [PatternMapperFn] that sets the analog drift amount.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(analog(0.2))
 * ```
 *
 * @param amount The analog drift amount between 0.0 (digital) and 1.0 (maximum drift).
 * @return A [PatternMapperFn] that sets analog drift.
 * @category tonal
 * @tags analog, drift, oscillator, warmth, vco, addon
 */
@SprudelDsl
fun analog(amount: PatternLike? = null): PatternMapperFn =
    _analog(listOfNotNull(amount).asSprudelDslArgs())

/**
 * Chains an analog-drift-set onto this [PatternMapperFn].
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(gain(0.8).analog(0.2))
 * ```
 *
 * @param amount The analog drift amount between 0.0 (digital) and 1.0 (maximum drift).
 */
@SprudelDsl
fun PatternMapperFn.analog(amount: PatternLike? = null): PatternMapperFn =
    _analog(listOfNotNull(amount).asSprudelDslArgs())

// -- warmth() ---------------------------------------------------------------------------------------------------------

private val warmthMutation = voiceModifier {
    withOscParam("warmth", it?.asDoubleOrNull())
}

private fun applyWarmth(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args, warmthMutation)
}

internal val SprudelPattern._warmth by dslPatternExtension { p, args, /* callInfo */ _ -> applyWarmth(p, args) }
internal val String._warmth by dslStringExtension { p, args, callInfo -> p._warmth(args, callInfo) }
internal val _warmth by dslPatternMapper { args, callInfo -> { p -> p._warmth(args, callInfo) } }
internal val PatternMapperFn._warmth by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(
        _warmth(
            args,
            callInfo
        )
    )
}

// ===== USER-FACING OVERLOADS =====

/**
 * Controls the oscillator warmth (low-pass filtering amount).
 *
 * A value of `0.0` gives a bright, unfiltered sound; `1.0` gives a muffled, warm sound.
 *
 * ```KlangScript(Playable)
 * note("c d e f").warmth(0.8)          // warm, muffled sawtooth
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").warmth("<0 0.5 1>")  // cycle through warmth values
 * ```
 *
 * @param amount The warmth amount between 0.0 (bright) and 1.0 (warm/muffled).
 *
 * @category tonal
 * @tags warmth, oscillator, filter, low-pass, addon
 */
@SprudelDsl
fun SprudelPattern.warmth(amount: PatternLike? = null): SprudelPattern =
    this._warmth(listOfNotNull(amount).asSprudelDslArgs())

/**
 * Parses this string as a pattern and sets the oscillator warmth.
 *
 * ```KlangScript(Playable)
 * note("c d e f").s("square").warmth("<0 0.5 1>")  // cycle through warmth values
 * ```
 *
 * @param amount The warmth amount between 0.0 (bright) and 1.0 (warm/muffled).
 */
@SprudelDsl
fun String.warmth(amount: PatternLike? = null): SprudelPattern =
    this._warmth(listOfNotNull(amount).asSprudelDslArgs())

/**
 * Creates a [PatternMapperFn] that sets the oscillator warmth.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(warmth("<0 0.5 1>"))  // cycle through warmth values
 * ```
 *
 * @param amount The warmth amount between 0.0 (bright) and 1.0 (warm/muffled).
 */
@SprudelDsl
fun warmth(amount: PatternLike? = null): PatternMapperFn =
    _warmth(listOfNotNull(amount).asSprudelDslArgs())

/**
 * Chains a warmth-set onto this [PatternMapperFn], applying oscillator warmth after the previous step.
 *
 * ```KlangScript(Playable)
 * seq("0.2 0.4").apply(mul(2).warmth())  // mul doubles values, warmth() reads them as warmth: 0.4, 0.8
 * ```
 *
 * @param amount The warmth amount between 0.0 (bright) and 1.0 (warm/muffled).
 */
@SprudelDsl
fun PatternMapperFn.warmth(amount: PatternLike? = null): PatternMapperFn =
    _warmth(listOfNotNull(amount).asSprudelDslArgs())
