@file:Suppress("DuplicatedCode", "ObjectPropertyName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang.addons

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel.lang.PatternLike
import io.peekandpoke.klang.sprudel.lang.PatternMapperFn
import io.peekandpoke.klang.sprudel.lang.SprudelDsl
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.asDoubleOrNull
import io.peekandpoke.klang.sprudel.lang.chain
import io.peekandpoke.klang.sprudel.lang.toVoiceValuePattern
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
@KlangScript.Function
fun SprudelPattern.oscparam(key: String, value: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyOscparam(this, listOf(key, value).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets an oscillator parameter. */
@SprudelDsl
@KlangScript.Function
fun String.oscparam(key: String, value: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().oscparam(key, value, callInfo)

/** Creates a [PatternMapperFn] that sets an oscillator parameter. */
@SprudelDsl
@KlangScript.Function
fun oscparam(key: String, value: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.oscparam(key, value, callInfo) }

/** Chains an oscillator-parameter-set onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.oscparam(key: String, value: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.oscparam(key, value, callInfo) }

/** Alias for [oscparam]. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.oscp(key: String, value: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.oscparam(key, value, callInfo)

/** Alias for [oscparam]. Parses this string as a pattern and sets an oscillator parameter. */
@SprudelDsl
@KlangScript.Function
fun String.oscp(key: String, value: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().oscp(key, value, callInfo)

/** Alias for [oscparam]. Creates a [PatternMapperFn] that sets an oscillator parameter. */
@SprudelDsl
@KlangScript.Function
fun oscp(key: String, value: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.oscp(key, value, callInfo) }

/** Alias for [oscparam]. Chains an oscillator-parameter-set onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.oscp(key: String, value: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.oscp(key, value, callInfo) }

// -- analog() ---------------------------------------------------------------------------------------------------------

private val analogMutation = voiceModifier {
    withOscParam("analog", it?.asDoubleOrNull())
}

private fun applyAnalog(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args, analogMutation)
}

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
@KlangScript.Function
fun SprudelPattern.analog(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyAnalog(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

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
@KlangScript.Function
fun String.analog(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().analog(amount, callInfo)

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
@KlangScript.Function
fun analog(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.analog(amount, callInfo) }

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
@KlangScript.Function
fun PatternMapperFn.analog(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.analog(amount, callInfo) }

// -- warmth() ---------------------------------------------------------------------------------------------------------

private val warmthMutation = voiceModifier {
    withOscParam("warmth", it?.asDoubleOrNull())
}

private fun applyWarmth(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args, warmthMutation)
}

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
@KlangScript.Function
fun SprudelPattern.warmth(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyWarmth(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

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
@KlangScript.Function
fun String.warmth(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().warmth(amount, callInfo)

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
@KlangScript.Function
fun warmth(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.warmth(amount, callInfo) }

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
@KlangScript.Function
fun PatternMapperFn.warmth(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.warmth(amount, callInfo) }
