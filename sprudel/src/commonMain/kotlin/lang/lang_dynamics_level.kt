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
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.putOscParam

// -- gain() -----------------------------------------------------------------------------------------------------------

private val gainMutation = voiceSetter { gain = it?.asDoubleOrNull() }

private fun applyGain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.gain }, update = gainMutation)
    }

    return source._liftOrReinterpretNumericalField(args, gainMutation)
}

/**
 * Sets the level each event leaves at, [per voice](/manuals/lexikon/voice).
 *
 * The one level word. It is tone-neutral and it comes last: the voice's filters, its distortion and
 * its envelope have all run by the time `gain` is applied, so turning it down makes the sound
 * smaller and changes nothing else about it. Set it once the sound is designed and only its size is
 * still wrong. Below 1 is quieter, above 1 is louder.
 *
 * A later `gain` REPLACES an earlier one, so the last call in the chain wins. To scale a level
 * that is already set instead of replacing it, pass a mapper: `gain(mul(0.5))` halves whatever is
 * there. On an event with no gain set at all a mapper does nothing, so set a level first.
 *
 * `velocity` is multiplied into it, and mute, solo and fade scale it as well. Takes a control
 * pattern, so the level can move from event to event.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").gain(0.5)              // all hits at half volume
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd*4").gain("<0.2 0.5 0.8 1.0>")    // different gain each cycle
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd*4").distort(0.7).gain(0.2)       // dirty it first, then set the level it leaves at
 * ```
 *
 * @param amount Level multiplier, 1 leaves the event as it is.
 * @param-tool amount SprudelGainEditor, SprudelGainSequenceEditor
 *
 * @scope voice
 * @category dynamics
 * @tags gain, volume, amplitude, dynamics
 */
@KlangScript.Function
fun SprudelPattern.gain(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyGain(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the gain for each event.
 *
 * ```KlangScript(Playable)
 * "bd*4".gain("0.2 0.5 0.8 1.0").s()    // different gain each beat
 * ```
 *
 * @param amount Level multiplier, 1 leaves the event as it is.
 */
@KlangScript.Function
fun String.gain(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).gain(amount, callInfo)

/**
 * The gain of each event, as a value other setters can read.
 *
 * Bare `gain` reads what the chain has set so far, so it comes after whatever set the field
 * (`gain(...)`, `adsr(...)`, an alias). Call it, `gain(...)`, to set the field; a mapper argument applies to the field.
 *
 * ```KlangScript(Playable)
 * s("hh*8").gain("0.4 1").gain(mul(perlin.seg(8).range(0.8, 1.2)))   // humanised levels
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e g").gain("0.9 0.6 0.3").velocity(gain)                      // velocity follows gain
 * ```
 *
 * @scope voice
 * @category dynamics
 * @tags gain, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("gain")
object gain : FieldAccessor({ it.gain }) {

    /**
     * Creates a [PatternMapperFn] that sets the gain for each event in a pattern.
     *
     * ```KlangScript(Playable)
     * s("hh hh hh hh").apply(gain("1.0 0.75 0.5 0.25"))
     * ```
     *
     * @param amount Level multiplier, 1 leaves the event as it is.
     */
    @KlangScript.Invoke
    operator fun invoke(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.gain(amount, callInfo) }
}

/**
 * Creates a chained [PatternMapperFn] that sets the gain after the previous mapper.
 *
 * ```KlangScript(Playable)
 * s("hh*4").apply(gain("1.0 0.5").gain(0.8))  // chain gain modifiers
 * ```
 *
 * @param amount Level multiplier, 1 leaves the event as it is.
 */
@KlangScript.Function
fun PatternMapperFn.gain(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.gain(amount, callInfo) }

// -- pregain() --------------------------------------------------------------------------------------------------------

private val pregainMutation = voiceSetter { putOscParam("pregain", it?.asDoubleOrNull()) }

private fun applyPregain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.oscParams?.get("pregain") }, update = pregainMutation)
    }

    return source._liftOrReinterpretNumericalField(args, pregainMutation)
}

/**
 * Sets how hard each event is played INTO its instrument, [per voice](/manuals/lexikon/voice).
 *
 * The other level word, and the only one that is not a fader. `gain` is the level the event LEAVES
 * at, after everything; `pregain` is the level the signal ARRIVES at inside the instrument, where
 * it meets whatever the instrument does to it. Exactly `oscparam("pregain", amount)`: it writes the
 * `pregain` slot and nothing else.
 *
 * **It does what the instrument wires it to, and nothing otherwise.** An instrument that places the
 * slot in front of a nonlinearity turns this into touch: play harder, get dirtier, the way an amp
 * does. An instrument that never places it ignores the call, note for note, and that is not a bug
 * to work around: a bare sine has no drive. Reach for `gain` when you want the size of the sound.
 *
 * **`pregain` is not the drive amount, and the two scales are not the same.** An instrument's
 * `distort(amount)` is EXPONENTIAL (about 4x at `0.5`, 16x at `1`, 250x at `2`) and it is fixed
 * inside the instrument, one setting for every note; `pregain` is LINEAR, 1 is unity, and the
 * pattern sets it per note. Design the drive once by ear, and leave `pregain` the touch.
 *
 * **Where it stops working, which is the opposite of what you might expect.** On a CLIPPING shape
 * driven into hard saturation, turning `pregain` down changes almost nothing: not the tone and
 * not the level either, because a clipper holds both. Measured on `saw.pregain().distort(2)
 * .lowpass(2500)` at `pregain` 1 against 0.4: the level comes out at 0.999 of the loud one and
 * the shape distance is 0.006, a rounding-scale no-op. So on a heavily driven lead, `pregain` is
 * the wrong knob twice over: the LEVEL has to come from `gain`, and there is no touch left to
 * find. The room to hear touch is at the gentle end of the drive (`distort(0.5)` gives a shape
 * distance of 0.223 on the same measurement), which is what the example below uses.
 *
 * **That is true of the shapes that SATURATE, which is `soft` and the other clippers.** The three
 * WAVEFOLDERS never saturate: `fold`, `linearfold` and `sineshaper` keep folding the harder you
 * drive them, so there `pregain` IS the fold depth and stays the strongest tone knob at any drive
 * (the same measurement at `distort(2)` gives a shape distance of 1.36 to 1.76 against `soft`'s
 * 0.006). It is not a level knob there either, and it is not even monotonic: on `fold` at
 * `distort(2)`, turning `pregain` DOWN to 0.4 makes the note about four times LOUDER. `rectify`
 * sits between the two families (0.054). A folder is a fine home for touch; just do not expect
 * "down" to mean "softer".
 *
 * ```KlangScript(Playable)
 * let amp = Osc.saw().pregain().distort(0.5).lowpass(2500)
 * note("c3 e3 g3 e3").sound(amp).pregain("1 0.6 1 0.4").gain(0.3)   // touch: harder notes dirtier
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").pregain(2).gain(0.3)   // the default sounds place no slot: this changes nothing
 * ```
 *
 * **Per VOICE, which is what makes it touch at all**: every note carries its own value, so
 * `"1 0.6 1 0.4"` is four different notes. The orbit's own knobs (`katp`, the bus doors) are per
 * ORBIT and belong to the first voice that sounds there, so they cannot articulate a line.
 *
 * @param amount How hard the note is played in, 1 leaves the instrument at its own level.
 *
 * @scope voice
 * @category dynamics
 * @tags pregain, drive, touch, level, oscillator
 */
@KlangScript.Function
fun SprudelPattern.pregain(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPregain(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets how hard each event is played into its instrument.
 *
 * @param amount How hard the note is played in, 1 leaves the instrument at its own level.
 */
@KlangScript.Function
fun String.pregain(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pregain(amount, callInfo)

/**
 * How hard each event is played into its instrument, as a value other setters can read.
 *
 * Bare `pregain` reads what the chain has set so far, so it comes after whatever set the slot
 * (`pregain(...)`, `oscparam("pregain", ...)`). Call it, `pregain(...)`, to set the slot; a mapper
 * argument applies to the slot, and on an event that has none it does nothing, so set one first.
 *
 * ```KlangScript(Playable)
 * let amp = Osc.saw().pregain().distort(0.5).lowpass(2500)
 * note("c3 e3").sound(amp).pregain(1).pregain(mul("1 0.5")).gain(0.3)   // the second note softer in
 * ```
 *
 * ```KlangScript(Playable)
 * let amp = Osc.saw().pregain().distort(0.5).lowpass(2500)
 * note("c3 e3").sound(amp).pregain("1 0.5").gain(pregain.mul(0.3))      // and quieter out with it
 * ```
 *
 * @scope voice
 * @category dynamics
 * @tags pregain, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("pregain")
object pregain : FieldAccessor({ it.oscParams?.get("pregain") }) {

    /**
     * Creates a [PatternMapperFn] that sets how hard each event is played into its instrument.
     *
     * @param amount How hard the note is played in, 1 leaves the instrument at its own level.
     */
    @KlangScript.Invoke
    operator fun invoke(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.pregain(amount, callInfo) }
}

/**
 * Creates a chained [PatternMapperFn] that sets the pregain after the previous mapper.
 *
 * @param amount How hard the note is played in, 1 leaves the instrument at its own level.
 */
@KlangScript.Function
fun PatternMapperFn.pregain(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pregain(amount, callInfo) }

// -- pan() ------------------------------------------------------------------------------------------------------------

private val panMutation = voiceSetter { pan = it?.asDoubleOrNull() }

private fun applyPan(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.pan }, update = panMutation)
    }

    return source._liftOrReinterpretNumericalField(args, panMutation)
}

/**
 * Sets the stereo position of each event, per voice.
 *
 * 0 is full left, 0.5 is centre, 1 is full right. A continuous pattern such as `sine.range(0, 1)`
 * sweeps the position instead of stepping it.
 *
 * ```KlangScript(Playable)
 * s("bd sd").pan(0.25)                   // slightly left
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd hh sd cp").pan("0 0.33 0.66 1")  // left to right
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*8").pan(sine.range(0, 1))        // smooth left-right sweep
 * ```
 *
 * @param amount Pan position, 0 left to 1 right.
 * @param-tool amount SprudelPanEditor, SprudelPanSequenceEditor
 *
 * @scope voice
 * @category dynamics
 * @tags pan, stereo, panning, position
 */
@KlangScript.Function
fun SprudelPattern.pan(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPan(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the stereo panning position.
 *
 * ```KlangScript(Playable)
 * "bd hh sd cp".pan("0 0.33 0.66 1").s()  // left to right
 * ```
 *
 * @param amount Pan position, 0 left to 1 right.
 */
@KlangScript.Function
fun String.pan(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pan(amount, callInfo)

/**
 * The stereo position of each event, as a value other setters can read.
 *
 * Bare `pan` reads what the chain has set so far, so it comes after whatever set the field
 * (`pan(...)`, `adsr(...)`, an alias). Call it, `pan(...)`, to set the field; a mapper argument applies to the field.
 *
 * ```KlangScript(Playable)
 * s("hh*8").pan("0 0.25 0.5 0.75 1 0.75 0.5 0.25").pan(add(perlin.seg(8).range(-0.1, 0.1)))   // a little drift
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e g").pan("0.2 0.5 0.8").gain(pan)                             // louder to the right
 * ```
 *
 * @scope voice
 * @category dynamics
 * @tags pan, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("pan")
object pan : FieldAccessor({ it.pan }) {

    /**
     * Creates a [PatternMapperFn] that sets the pan for each event in a pattern.
     *
     * ```KlangScript(Playable)
     * s("bd hh sd cp").apply(pan("0 0.33 0.66 1"))  // left to right
     * ```
     */
    @KlangScript.Invoke
    operator fun invoke(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.pan(amount, callInfo) }
}

/**
 * Creates a chained [PatternMapperFn] that sets the pan after the previous mapper.
 *
 * ```KlangScript(Playable)
 * s("bd hh sd cp").apply(pan("0 0.33 0.66 1").gain(0.8))  // pan + gain chained
 * ```
 *
 * @param amount Pan position, 0 left to 1 right.
 */
@KlangScript.Function
fun PatternMapperFn.pan(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pan(amount, callInfo) }

// -- velocity() / vel() -----------------------------------------------------------------------------------------------

private val velocityMutation = voiceSetter { velocity = it?.asDoubleOrNull() }

private fun applyVelocity(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.velocity }, update = velocityMutation)
    }

    return source._liftOrReinterpretNumericalField(args, velocityMutation)
}

/**
 * Sets the velocity of each event, per voice.
 *
 * Velocity is multiplied into `gain`, so `gain(0.5).velocity(2)` ends up at 1. Keep `gain` for the
 * level of the line and `velocity` for the accents inside it.
 *
 * ```KlangScript(Playable)
 * note("c d e f").gain(0.5).velocity("0.5 2.0")  // gain is multiplied by velocity
 * ```
 *
 * ```KlangScript(Playable)
 * note("c*4").velocity("<0.3 0.6 0.9 1.0>")  // crescendo pattern
 * ```
 *
 * ```KlangScript(Playable)
 * note("c*4").velocity(saw.range(0.25, 1.0).slow(4))  // crescendo pattern over 4 cycles
 * ```
 *
 * @param amount Velocity, multiplied into the gain.
 *
 * @scope voice
 * @alias vel
 * @category dynamics
 * @tags velocity, vel, volume, midi, dynamics
 */
@KlangScript.Function
fun SprudelPattern.velocity(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyVelocity(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the velocity (gain multiplier) for each event.
 *
 * ```KlangScript(Playable)
 * "c*4".velocity("<0.3 0.6 0.9 1.0>").note()  // crescendo pattern
 * ```
 *
 * @param amount Velocity, multiplied into the gain.
 */
@KlangScript.Function
fun String.velocity(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).velocity(amount, callInfo)

/**
 * The velocity of each event, as a value other setters can read.
 *
 * Bare `velocity` reads what the chain has set so far, so it comes after whatever set the field
 * (`velocity(...)`, `adsr(...)`, an alias). Call it, `velocity(...)`, to set the field; a mapper argument applies to the field.
 *
 * ```KlangScript(Playable)
 * s("bd sd").velocity("1 0.5").velocity(mul(0.8))                        // scale the accents
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e g").velocity("0.9 0.6 0.3").lpf(velocity.mul(4000))          // softer notes are darker
 * ```
 *
 * @scope voice
 * @category dynamics
 * @tags velocity, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("velocity")
object velocity : FieldAccessor({ it.velocity }) {

    /**
     * Create a [PatternMapperFn] that sets the velocity (gain multiplier) for each event in a pattern.
     *
     * ```KlangScript(Playable)
     * note("c*4").apply(velocity("<0.3 0.6 0.9 1.0>"))  // crescendo pattern
     * ```
     *
     * @param amount Velocity, multiplied into the gain.
     */
    @KlangScript.Invoke
    operator fun invoke(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.velocity(amount, callInfo) }
}

/**
 * Creates a chained [PatternMapperFn] that sets the velocity after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c*4").apply(velocity("<0.3 0.6 0.9>").gain(0.8))  // velocity + gain chained
 * ```
 *
 * @param amount Velocity, multiplied into the gain.
 */
@KlangScript.Function
fun PatternMapperFn.velocity(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.velocity(amount, callInfo) }

/**
 * Alias for [velocity]: the velocity of each event, multiplied into the gain.
 *
 * ```KlangScript(Playable)
 * note("c d e f").gain(0.5).vel("0.5 2.0")   // gain is multiplied by velocity
 * ```
 *
 * ```KlangScript(Playable)
 * note("c*4").vel(saw.range(0.25, 1.0).slow(4))   // crescendo pattern over 4 cycles
 * ```
 *
 * @param amount Velocity, multiplied into the gain.
 *
 * @scope voice
 * @alias velocity
 * @category dynamics
 * @tags vel, velocity, volume, midi, dynamics
 */
@KlangScript.Function
fun SprudelPattern.vel(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.velocity(amount, callInfo)

/**
 * Alias for [velocity]. Sets the velocity (gain multiplier) for each event in this pattern.
 *
 * ```KlangScript(Playable)
 * "c*4".vel("<0.3 0.6 0.9 1.0>").note()  // crescendo pattern
 * ```
 *
 * @param amount Velocity, multiplied into the gain.
 */
@KlangScript.Function
fun String.vel(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).velocity(amount, callInfo)

/**
 * Alias of [velocity]: the same accessor under another name.
 *
 * @scope voice
 * @category dynamics
 * @tags vel, velocity, accessor
 */
@KlangScript.Constant
val vel: velocity = velocity

/**
 * Alias for [velocity]. Creates a chained [PatternMapperFn] that sets the velocity after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c*4").apply(vel("<0.3 0.6 0.9>").gain(0.8))  // velocity + gain chained
 * ```
 *
 * @param amount Velocity, multiplied into the gain.
 */
@KlangScript.Function
fun PatternMapperFn.vel(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.velocity(amount, callInfo) }
