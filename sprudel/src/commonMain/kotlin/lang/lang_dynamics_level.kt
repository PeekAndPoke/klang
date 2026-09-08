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

// -- gain() -----------------------------------------------------------------------------------------------------------

private val gainMutation = voiceSetter { gain = it?.asDoubleOrNull() }

private fun applyGain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.gain }, update = gainMutation)
    }

    return source._liftOrReinterpretNumericalField(args, gainMutation)
}

/**
 * Sets the level of each event, [per voice](/manuals/lexikon/voice).
 *
 * A plain multiplier on the voice's output: below 1 is quieter, above 1 is louder. `velocity` is
 * multiplied into it, and mute, solo and fade scale it as well. Takes a control pattern, so the
 * level can move from event to event.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").gain(0.5)              // all hits at half volume
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd*4").gain("<0.2 0.5 0.8 1.0>")    // different gain each cycle
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

// -- postgain() -------------------------------------------------------------------------------------------------------

private val postgainMutation = voiceSetter { postGain = it?.asDoubleOrNull() }

private fun applyPostgain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.postGain }, update = postgainMutation)
    }

    return source._liftOrReinterpretNumericalField(args, postgainMutation)
}

/**
 * Sets the final level trim of each event, per voice.
 *
 * `postgain` and `gain` are both output multipliers applied at the voice output, so on a single
 * voice they do the same arithmetic. The difference is what else touches them: `gain` is scaled by
 * `velocity` and by the mute/solo/fade multiplier, while `postgain` is not. So `gain` is the
 * per-note, performable level and `postgain` is the line's own final trim.
 *
 * ```KlangScript(Playable)
 * s("bd sd").postgain(1.5)                    // amplify after processing
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*8").postgain(rand.range(0.1, 1.0))   // random post-gain per hit
 * ```
 *
 * @param amount Final level trim, 1 leaves the event as it is.
 *
 * @scope voice
 * @category dynamics
 * @tags postgain, gain, volume, post-processing
 */
@KlangScript.Function
fun SprudelPattern.postgain(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPostgain(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the post-gain for each event.
 *
 * ```KlangScript(Playable)
 * "hh*8".postgain(perlin.range(0.1, 1.0).slow(4)).s()   // perlin noised post-gain
 * ```
 *
 * @param amount Final level trim, 1 leaves the event as it is.
 */
@KlangScript.Function
fun String.postgain(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).postgain(amount, callInfo)

/**
 * The post-processing gain of each event, as a value other setters can read.
 *
 * Bare `postgain` reads what the chain has set so far, so it comes after whatever set the field
 * (`postgain(...)`, `adsr(...)`, an alias). Call it, `postgain(...)`, to set the field; a mapper argument applies to the field.
 *
 * ```KlangScript(Playable)
 * s("bd*4").distort(2).postgain(0.4).postgain(mul("1 0.5 1 0.5"))       // tame every second hit
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e").postgain("0.5 0.25").gain(postgain)                        // match the two stages
 * ```
 *
 * @scope voice
 * @category dynamics
 * @tags postgain, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("postgain")
object postgain : FieldAccessor({ it.postGain }) {

    /**
     * Create a [PatternMapperFn] that sets the post-gain for each event in a pattern.
     *
     * ```KlangScript(Playable)
     * "hh*8".apply(postgain(sine.range(0.1, 1.0).slow(2))).s()   // sine post-gain over two cycles
     * ```
     *
     * @param amount Final level trim, 1 leaves the event as it is.
     */
    @KlangScript.Invoke
    operator fun invoke(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.postgain(amount, callInfo) }
}

/**
 * Creates a chained [PatternMapperFn] that sets the post-gain after the previous mapper.
 *
 * ```KlangScript(Playable)
 * s("hh*4").apply(postgain(0.8).gain(0.5))  // postgain + gain chained
 * ```
 *
 * @param amount Final level trim, 1 leaves the event as it is.
 */
@KlangScript.Function
fun PatternMapperFn.postgain(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.postgain(amount, callInfo) }
