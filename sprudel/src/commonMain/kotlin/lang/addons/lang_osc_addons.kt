/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang.addons

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel.lang.PatternLike
import io.peekandpoke.klang.sprudel.lang.PatternMapperFn
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.asDoubleOrNull
import io.peekandpoke.klang.sprudel.lang.chain
import io.peekandpoke.klang.sprudel.lang.toVoiceValuePattern
import io.peekandpoke.klang.sprudel.lang.voiceSetter
import io.peekandpoke.klang.sprudel.putOscParam

// -- oscparam() / oscp() ----------------------------------------------------------------------------------------------

private fun applyOscparam(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) return source
    val key = args[0].value?.toString() ?: return source
    val valueArgs = args.drop(1)
    val mutation = voiceSetter { putOscParam(key, it?.asDoubleOrNull()) }
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
 * note("c3 e3").oscparam("onepole", "<12000 3700>") // pattern-cycle the value
 * ```
 *
 * @param key The oscillator parameter name (e.g. "analog", "onepole", "density").
 * @param value The parameter value.
 * @return A new pattern with the oscillator parameter set.
 * @alias oscp
 * @category tonal
 * @tags oscillator, parameter, osc, addon
 */
@KlangScript.Function
fun SprudelPattern.oscparam(key: String, value: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyOscparam(this, listOf(key, value).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets an oscillator parameter.
 *
 * @alias oscp
 */
@KlangScript.Function
fun String.oscparam(key: String, value: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).oscparam(key, value, callInfo)

/**
 * Creates a [PatternMapperFn] that sets an oscillator parameter.
 *
 * @alias oscp
 */
@KlangScript.Function
fun oscparam(key: String, value: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.oscparam(key, value, callInfo) }

/**
 * Chains an oscillator-parameter-set onto this [PatternMapperFn].
 *
 * @alias oscp
 */
@KlangScript.Function
fun PatternMapperFn.oscparam(key: String, value: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.oscparam(key, value, callInfo) }

/**
 * Alias for [oscparam].
 *
 * @alias oscparam
 */
@KlangScript.Function
fun SprudelPattern.oscp(key: String, value: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.oscparam(key, value, callInfo)

/**
 * Alias for [oscparam]. Parses this string as a pattern and sets an oscillator parameter.
 *
 * @alias oscparam
 */
@KlangScript.Function
fun String.oscp(key: String, value: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).oscparam(key, value, callInfo)

/**
 * Alias for [oscparam]. Creates a [PatternMapperFn] that sets an oscillator parameter.
 *
 * @alias oscparam
 */
@KlangScript.Function
fun oscp(key: String, value: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.oscparam(key, value, callInfo) }

/**
 * Alias for [oscparam]. Chains an oscillator-parameter-set onto this [PatternMapperFn].
 *
 * @alias oscparam
 */
@KlangScript.Function
fun PatternMapperFn.oscp(key: String, value: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.oscparam(key, value, callInfo) }

// -- analog() ---------------------------------------------------------------------------------------------------------

private val analogMutation = voiceSetter {
    putOscParam("analog", it?.asDoubleOrNull())
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
@KlangScript.Function
fun String.analog(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).analog(amount, callInfo)

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
@KlangScript.Function
fun PatternMapperFn.analog(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.analog(amount, callInfo) }

// -- duty() -----------------------------------------------------------------------------------------------------------

private val dutyMutation = voiceSetter {
    putOscParam("duty", it?.asDoubleOrNull())
}

private fun applyDuty(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args, dutyMutation)
}

/**
 * Sets the pulse width / duty cycle of the pulse oscillator (`square` / `pulse` / `pulze`).
 *
 * `duty` is the fraction of each cycle the wave is high: `0.5` is a symmetric square, lower values give
 * a narrower positive pulse, higher values a wider one. It can be modulated (PWM). Has no effect on
 * non-pulse oscillators.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("pulse").duty("<0.5 0.25 0.1>")   // PWM-style duty sweep
 * ```
 *
 * @param amount The duty cycle between 0.0 and 1.0 (default 0.5).
 * @return A new pattern with the duty cycle applied.
 * @category tonal
 * @tags duty, pulse, square, pwm, oscillator, addon
 */
@KlangScript.Function
fun SprudelPattern.duty(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDuty(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the pulse duty cycle.
 *
 * @param amount The duty cycle between 0.0 and 1.0 (default 0.5).
 */
@KlangScript.Function
fun String.duty(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).duty(amount, callInfo)

/**
 * Creates a [PatternMapperFn] that sets the pulse duty cycle.
 *
 * @param amount The duty cycle between 0.0 and 1.0 (default 0.5).
 */
@KlangScript.Function
fun duty(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.duty(amount, callInfo) }

/**
 * Chains a duty-set onto this [PatternMapperFn].
 *
 * @param amount The duty cycle between 0.0 and 1.0 (default 0.5).
 */
@KlangScript.Function
fun PatternMapperFn.duty(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.duty(amount, callInfo) }

// -- onepole() --------------------------------------------------------------------------------------------------------

private val onepoleMutation = voiceSetter {
    putOscParam("onepole", it?.asDoubleOrNull())
}

private fun applyOnepole(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args, onepoleMutation)
}

/**
 * Puts a one-pole lowpass on the oscillator at [freq] Hz — the gentlest filter there is
 * (6 dB/oct, no resonance). Musically it is a "warmth" knob: lower frequencies are darker.
 * `0` (or omitting the call) means no filter.
 *
 * This is deliberately a DIFFERENT thing from [lpf]: `lpf` is the resonant 12 dB/oct SVF
 * and never secretly swaps character, `onepole` is the soft tone control. (Renamed from
 * `warmth(0..1)` in the pitch/unit unification, 2026-08-24 — the old value was the raw
 * filter coefficient, sample-rate dependent; sites were converted via
 * `freq = sr/π · atan((1−w)/w)` at 48 kHz — scalar sites sound-identical; the two
 * patterned `saw.range` sites are endpoint-exact, mid-sweep the atan curve differs
 * inaudibly.)
 *
 * ```KlangScript(Playable)
 * note("c d e f").onepole(3700)          // warm, muffled sawtooth
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").onepole("<12000 3700 1700>")  // stepwise darker
 * ```
 *
 * @param freq The one-pole cutoff in Hz. 0 = no filter; lower = warmer/darker.
 *
 * @category tonal
 * @tags onepole, warmth, oscillator, filter, low-pass, tone, addon
 */
@KlangScript.Function
fun SprudelPattern.onepole(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyOnepole(this, listOfNotNull(freq).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the oscillator one-pole lowpass (see
 * [SprudelPattern.onepole]).
 *
 * ```KlangScript(Playable)
 * note("c d e f").s("square").onepole("<12000 3700 1700>")  // stepwise darker
 * ```
 *
 * @param freq The one-pole cutoff in Hz. 0 = no filter; lower = warmer/darker.
 */
@KlangScript.Function
fun String.onepole(freq: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).onepole(freq, callInfo)

/**
 * Creates a [PatternMapperFn] that sets the oscillator one-pole lowpass.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(onepole("<12000 3700 1700>"))  // stepwise darker
 * ```
 *
 * @param freq The one-pole cutoff in Hz. 0 = no filter; lower = warmer/darker.
 */
@KlangScript.Function
fun onepole(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.onepole(freq, callInfo) }

/**
 * Chains a onepole-set onto this [PatternMapperFn], applying the one-pole lowpass after the
 * previous step.
 *
 * ```KlangScript(Playable)
 * seq("1700 3700").apply(mul(2).onepole())  // mul doubles values, onepole() reads them as Hz
 * ```
 *
 * @param freq The one-pole cutoff in Hz. 0 = no filter; lower = warmer/darker.
 */
@KlangScript.Function
fun PatternMapperFn.onepole(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.onepole(freq, callInfo) }
