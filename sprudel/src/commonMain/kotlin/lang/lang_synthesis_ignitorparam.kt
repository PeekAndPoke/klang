/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.script.stdlib.IgnitorSlotLike
import io.peekandpoke.klang.script.stdlib.ignitorSlotName
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.putIgnitorParam

// -- ignitorParam() / ignp() -----------------------------------------------------------------------------------------

/**
 * Writes the slot [slot] (already a NAME, resolved at the call by [ignitorSlotName]) with the value pattern
 * [value]. The four forms of both names resolve the slot first and then land here, so a wrong slot argument is a
 * script error when the door is CALLED, the mapper forms included, not when a pattern is queried.
 */
private fun applyIgnitorParam(source: SprudelPattern, slot: String, value: PatternLike, callInfo: CallInfo?): SprudelPattern {
    val valueArgs = listOf(slot, value).asSprudelDslArgs(callInfo).drop(1)
    val mutation = voiceSetter { putIgnitorParam(slot, it?.asDoubleOrNull()) }
    return source._liftOrReinterpretStringField(valueArgs, mutation)
}

/** The slot name of an `ignitorParam` call, or the script error naming the door and the fix. */
private fun ignitorParamSlot(slot: IgnitorSlotLike?, callInfo: CallInfo?): String =
    ignitorSlotName(slot, door = "ignitorParam", twin = "katalystParam", location = callInfo?.callLocation)

/** The slot name of an `ignp` call, or the script error naming the door and the fix. */
private fun ignpSlot(slot: IgnitorSlotLike?, callInfo: CallInfo?): String =
    ignitorSlotName(slot, door = "ignp", twin = "katp", location = callInfo?.callLocation)

/**
 * Writes one Ignitor slot, [per voice](/manuals/lexikon/voice): by its name, or by the param object itself.
 *
 * Direct access to the `ignitorParams` map, for slots that have no dedicated door of their own.
 * Slots used elsewhere in this library are `analog`, `onepole` and `density`.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("supersaw").ignitorParam("analog", 4)
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").ignitorParam("onepole", "<12000 3700>") // pattern-cycle the value
 * ```
 *
 * The slot can be the param OBJECT instead of its name: a classic slot (`Ignitor.slot.lpf.freq`), or a param of
 * your own instrument held in a variable. Only the NAME is written; the default stays the instrument's.
 *
 * ```KlangScript(Playable)
 * let cutoff = Ignitor.param("cutoff", 800)
 * let pad = Ignitor.saw().lowpass(cutoff).classic()
 * note("c3 e3").sound(pad).ignitorParam(cutoff, "<400 2000>")
 * ```
 *
 * A Katalyst param (`Katalyst.param(...)`, `Katalyst.slot.*`) is the orbit chain's slot, which `katalystParam`
 * writes; handing one to this door is a script error at the call, and so is a number, a sound or an expression
 * over a param (`Ignitor.param("x", 1).mul(2)` is not a slot).
 *
 * @param slot The Ignitor slot (required): its name, or the Ignitor param itself.
 * @param value The slot value.
 * @return A new pattern with the slot written.
 * @alias ignp
 * @scope voice
 * @category tonal
 * @tags ignitor, parameter, slot
 */
@KlangScript.Function
fun SprudelPattern.ignitorParam(slot: IgnitorSlotLike?, value: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyIgnitorParam(this, ignitorParamSlot(slot, callInfo), value, callInfo)

/**
 * Parses this string as a pattern and writes one Ignitor slot, by name or by the param object.
 *
 * @alias ignp
 */
@KlangScript.Function
fun String.ignitorParam(slot: IgnitorSlotLike?, value: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    val name = ignitorParamSlot(slot, callInfo)
    return applyIgnitorParam(this.toVoiceValuePattern(callInfo?.receiverLocation), name, value, callInfo)
}

/**
 * Creates a [PatternMapperFn] that writes one Ignitor slot, by name or by the param object.
 *
 * @alias ignp
 */
@KlangScript.Function
fun ignitorParam(slot: IgnitorSlotLike?, value: PatternLike, callInfo: CallInfo? = null): PatternMapperFn {
    val name = ignitorParamSlot(slot, callInfo)
    return { p -> applyIgnitorParam(p, name, value, callInfo) }
}

/**
 * Chains an Ignitor slot write onto this [PatternMapperFn].
 *
 * @alias ignp
 */
@KlangScript.Function
fun PatternMapperFn.ignitorParam(slot: IgnitorSlotLike?, value: PatternLike, callInfo: CallInfo? = null): PatternMapperFn {
    val name = ignitorParamSlot(slot, callInfo)
    return this.chain { p -> applyIgnitorParam(p, name, value, callInfo) }
}

/**
 * Alias for [ignitorParam]: writes one Ignitor slot, by name or by the param object.
 *
 * @param slot The Ignitor slot (required): its name, or the Ignitor param itself.
 * @param value The slot value.
 * @alias ignitorParam
 * @scope voice
 * @category tonal
 * @tags ignp, ignitor, parameter, slot
 */
@KlangScript.Function
fun SprudelPattern.ignp(slot: IgnitorSlotLike?, value: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyIgnitorParam(this, ignpSlot(slot, callInfo), value, callInfo)

/**
 * Alias for [ignitorParam]. Parses this string as a pattern and writes one Ignitor slot.
 *
 * @alias ignitorParam
 */
@KlangScript.Function
fun String.ignp(slot: IgnitorSlotLike?, value: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    val name = ignpSlot(slot, callInfo)
    return applyIgnitorParam(this.toVoiceValuePattern(callInfo?.receiverLocation), name, value, callInfo)
}

/**
 * Alias for [ignitorParam]. Creates a [PatternMapperFn] that writes one Ignitor slot.
 *
 * @alias ignitorParam
 */
@KlangScript.Function
fun ignp(slot: IgnitorSlotLike?, value: PatternLike, callInfo: CallInfo? = null): PatternMapperFn {
    val name = ignpSlot(slot, callInfo)
    return { p -> applyIgnitorParam(p, name, value, callInfo) }
}

/**
 * Alias for [ignitorParam]. Chains an Ignitor slot write onto this [PatternMapperFn].
 *
 * @alias ignitorParam
 */
@KlangScript.Function
fun PatternMapperFn.ignp(slot: IgnitorSlotLike?, value: PatternLike, callInfo: CallInfo? = null): PatternMapperFn {
    val name = ignpSlot(slot, callInfo)
    return this.chain { p -> applyIgnitorParam(p, name, value, callInfo) }
}

// -- analog() ---------------------------------------------------------------------------------------------------------

private val analogMutation = voiceSetter {
    putIgnitorParam("analog", it?.asDoubleOrNull())
}

private fun applyAnalog(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.ignitorParams?.get("analog") }, update = analogMutation)
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
 * The character is **how analog**, one unitless scale, not a unit: `0` is ideal (off, and costs
 * nothing), `1` to `8` is usual (the built-in songs live in that band), `10` is strong. Each part of the
 * instrument maps it through its own multipliers and has its own tells per unit: an oscillator drifts about
 * a cent of peak pitch per unit (`analog(8)` up to about eight either side of the note); in the built-in
 * instruments the lowpass and highpass saturate their resonance, and every filter takes a per-voice cutoff
 * tolerance and wanders its cutoff.
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
 * @param character How analog, a character scale: 0 is ideal, 1 to 8 usual, 10 strong (an oscillator drifts about a cent per unit).
 * @return A new pattern with analog drift applied.
 * @scope voice
 * @category tonal
 * @tags analog, drift, oscillator, warmth, vco
 */
@KlangScript.Function
fun SprudelPattern.analog(character: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyAnalog(this, listOfNotNull(character).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets how analog the sound is.
 *
 * ```KlangScript(Playable)
 * "c3 e3".analog(4).s("supersaw").note()
 * ```
 *
 * @param character How analog, a character scale: 0 is ideal, 1 to 8 usual, 10 strong (an oscillator drifts about a cent per unit).
 * @return A new pattern with analog drift applied.
 * @scope voice
 * @category tonal
 * @tags analog, drift, oscillator, warmth, vco
 */
@KlangScript.Function
fun String.analog(character: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).analog(character, callInfo)

/**
 * How analog each event is (the `analog` character), as a value other setters can read.
 *
 * Bare `analog` reads what the chain has set so far, so it comes after whatever set the field
 * (`analog(...)`). Call it, `analog(...)`, to set the field; a mapper argument applies to the field.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("supersaw").analog(2).analog(mul("1 3"))                 // the second note drifts more
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("supersaw").analog("1 4").unison(spread = analog.div(20))       // more drift, wider
 * ```
 *
 * @scope voice
 * @category tonal
 * @tags analog, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("analog")
object analog : FieldAccessor({ it.ignitorParams?.get("analog") }) {

    /**
     * Creates a [PatternMapperFn] that sets how analog the sound is.
     *
     * ```KlangScript(Playable)
     * note("c3 e3").apply(analog(4))
     * ```
     *
     * @param character How analog, a character scale: `0` is ideal, `1` to `8` usual, `10` strong.
     * @return A [PatternMapperFn] that sets analog drift.
     */
    @KlangScript.Invoke
    operator fun invoke(character: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.analog(character, callInfo) }
}


/**
 * Chains an analog-drift-set onto this [PatternMapperFn].
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(gain(0.8).analog(4))
 * ```
 *
 * @param character How analog, a character scale: `0` is ideal, `1` to `8` usual, `10` strong.
 */
@KlangScript.Function
fun PatternMapperFn.analog(character: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.analog(character, callInfo) }

// -- duty() -----------------------------------------------------------------------------------------------------------

private val dutyMutation = voiceSetter {
    putIgnitorParam("duty", it?.asDoubleOrNull())
}

private fun applyDuty(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.ignitorParams?.get("duty") }, update = dutyMutation)
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
 * @param amount Duty cycle, 0 to 1. Default 0.5.
 * @return A new pattern with the duty cycle applied.
 * @scope voice
 * @category tonal
 * @tags duty, pulse, square, pwm, oscillator
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
 * @scope voice
 * @category tonal
 * @tags duty, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("duty")
object duty : FieldAccessor({ it.ignitorParams?.get("duty") }) {

    /**
     * Creates a [PatternMapperFn] that sets the pulse duty cycle.
     *
     * @param amount The duty cycle between 0.0 and 1.0 (default 0.5).
     */
    @KlangScript.Invoke
    operator fun invoke(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.duty(amount, callInfo) }
}


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
    putIgnitorParam("onepole", it?.asDoubleOrNull())
}

private fun applyOnepole(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.ignitorParams?.get("onepole") }, update = onepoleMutation)
    }

    return source._liftOrReinterpretStringField(args, onepoleMutation)
}

/**
 * Puts a one-pole lowpass on the oscillator at [freq] Hz, the gentlest filter there is
 * (6 dB/oct, no resonance). Musically it is a "warmth" knob: lower frequencies are darker.
 * `0` (or omitting the call) means no filter.
 *
 * This is deliberately a DIFFERENT thing from [lpf]: `lpf` is the resonant 12 dB/oct SVF
 * and never secretly swaps character, `onepole` is the soft tone control. It was renamed from
 * `warmth(0..1)` in the pitch and unit unification, 2026-08-24, when the value changed from a raw,
 * sample-rate dependent filter coefficient to a frequency in Hz.
 *
 * ```KlangScript(Playable)
 * note("c d e f").onepole(3700)          // warm, muffled sawtooth
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").onepole("<12000 3700 1700>")  // stepwise darker
 * ```
 *
 * @param freq Cutoff in Hz. 0 is no filter, lower is darker.
 *
 * @scope voice
 * @category tonal
 * @tags onepole, warmth, oscillator, filter, low-pass, tone
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
 * @scope voice
 * @category tonal
 * @tags onepole, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("onepole")
object onepole : FieldAccessor({ it.ignitorParams?.get("onepole") }) {

    /**
     * Creates a [PatternMapperFn] that sets the oscillator one-pole lowpass.
     *
     * ```KlangScript(Playable)
     * note("c d e f").apply(onepole("<12000 3700 1700>"))  // stepwise darker
     * ```
     *
     * @param freq The one-pole cutoff in Hz. 0 = no filter; lower = warmer/darker.
     */
    @KlangScript.Invoke
    operator fun invoke(freq: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.onepole(freq, callInfo) }
}


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
