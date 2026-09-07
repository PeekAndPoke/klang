/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang.addons

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.FieldAccessor
import io.peekandpoke.klang.sprudel.lang.PatternLike
import io.peekandpoke.klang.sprudel.lang.PatternMapperFn
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.asDoubleOrNull
import io.peekandpoke.klang.sprudel.lang.chain
import io.peekandpoke.klang.sprudel.lang.singleMapperOrNull
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
 * note("c3 e3").s("supersaw").oscparam("analog", 4)
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
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.oscParams?.get("analog") }, update = analogMutation)
    }

    return source._liftOrReinterpretStringField(args, analogMutation)
}

/**
 * Adds analog oscillator drift to the sound.
 *
 * Simulates the micro-pitch instabilities of real analog VCOs by adding tiny random
 * perturbations to the oscillator's phase increment. For unison/super oscillators,
 * each voice drifts independently, creating lush analog-like chorusing.
 *
 * The amount is the **peak drift in cents**: `analog(1)` wobbles up to about a cent
 * either side of the note, `analog(8)` up to eight. `0.0` is off (and costs nothing).
 * Typical values run from `1` to `8`; the built-in songs live in that band.
 *
 * Two layers make it up: a fast jitter (~50 ms) and a slow wander (~10 s). The slow
 * layer starts CENTRED, so notes attack in tune and the wander only develops on notes
 * held long enough to hear it: short plucks stay put, pads breathe.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").analog(4)   // lush analog supersaw
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("sine").analog("<0 2 6>")   // cycle through drift amounts
 * ```
 *
 * @param amount The peak analog drift in cents; `0.0` is off, `1` to `8` is the usual band.
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
 * "c3 e3".analog(4).s("supersaw").note()
 * ```
 *
 * @param amount The peak analog drift in cents; `0.0` is off, `1` to `8` is the usual band.
 * @return A new pattern with analog drift applied.
 * @category tonal
 * @tags analog, drift, oscillator, warmth, vco, addon
 */
@KlangScript.Function
fun String.analog(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).analog(amount, callInfo)

/**
 * The analog drift amount of each event, as a value other setters can read.
 *
 * Bare `analog` reads what the chain has set so far, so it comes after whatever set the field
 * (`analog(...)`). Call it, `analog(...)`, to set the field; a mapper argument applies to the field.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("supersaw").analog(2).analog(mul("1 3"))                 // the second note drifts more
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("supersaw").analog("1 4").spread(analog.div(20))       // more drift, wider
 * ```
 *
 * @category tonal
 * @tags analog, accessor, addon
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("analog")
object Analog : FieldAccessor({ it.oscParams?.get("analog") }) {

    /**
     * Creates a [PatternMapperFn] that sets the analog drift amount.
     *
     * ```KlangScript(Playable)
     * note("c3 e3").apply(analog(4))
     * ```
     *
     * @param amount The peak analog drift in cents; `0.0` is off, `1` to `8` is the usual band.
     * @return A [PatternMapperFn] that sets analog drift.
     */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.analog(amount, callInfo) }
}

/** The [Analog] accessor as a value, so the Kotlin door reads like the script. */
val analog: Analog = Analog

/**
 * Chains an analog-drift-set onto this [PatternMapperFn].
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(gain(0.8).analog(4))
 * ```
 *
 * @param amount The peak analog drift in cents; `0.0` is off, `1` to `8` is the usual band.
 */
@KlangScript.Function
fun PatternMapperFn.analog(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.analog(amount, callInfo) }

// -- duty() -----------------------------------------------------------------------------------------------------------

private val dutyMutation = voiceSetter {
    putOscParam("duty", it?.asDoubleOrNull())
}

private fun applyDuty(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.oscParams?.get("duty") }, update = dutyMutation)
    }

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
 * The pulse duty cycle of each event, as a value other setters can read.
 *
 * Bare `duty` reads what the chain has set so far, so it comes after whatever set the field
 * (`duty(...)`). Call it, `duty(...)`, to set the field; a mapper argument applies to the field.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("pulze").duty(0.5).duty(mul("1 0.5"))                    // the second note thinner
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("pulze").duty("0.2 0.5").pan(duty)                      // wider pulse, further right
 * ```
 *
 * @category tonal
 * @tags duty, accessor, addon
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("duty")
object Duty : FieldAccessor({ it.oscParams?.get("duty") }) {

    /**
     * Creates a [PatternMapperFn] that sets the pulse duty cycle.
     *
     * @param amount The duty cycle between 0.0 and 1.0 (default 0.5).
     */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.duty(amount, callInfo) }
}

/** The [Duty] accessor as a value, so the Kotlin door reads like the script. */
val duty: Duty = Duty

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
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.oscParams?.get("onepole") }, update = onepoleMutation)
    }

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
 * The one-pole lowpass cutoff of each event, as a value other setters can read.
 *
 * Bare `onepole` reads what the chain has set so far, so it comes after whatever set the field
 * (`onepole(...)`). Call it, `onepole(...)`, to set the field; a mapper argument applies to the field.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").onepole(2000).onepole(mul("1 2"))                // the second note brighter
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").onepole("1000 4000").lpf(onepole.mul(2))         // the SVF an octave above the one-pole
 * ```
 *
 * @category tonal
 * @tags onepole, accessor, addon
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("onepole")
object Onepole : FieldAccessor({ it.oscParams?.get("onepole") }) {

    /**
     * Creates a [PatternMapperFn] that sets the oscillator one-pole lowpass.
     *
     * ```KlangScript(Playable)
     * note("c d e f").apply(onepole("<12000 3700 1700>"))  // stepwise darker
     * ```
     *
     * @param freq The one-pole cutoff in Hz. 0 = no filter; lower = warmer/darker.
     */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.onepole(freq, callInfo) }
}

/** The [Onepole] accessor as a value, so the Kotlin door reads like the script. */
val onepole: Onepole = Onepole

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
