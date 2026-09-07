/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel._liftNumericField
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
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
 * Sets the gain (volume multiplier) for each event in the pattern.
 *
 * Values below 1 reduce volume; above 1 amplify. Accepts control patterns for per-event modulation.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").gain(0.5)              // all hits at half volume
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd*4").gain("<0.2 0.5 0.8 1.0>")    // different gain each cycle
 * ```
 *
 * @param amount The control value to use for gain.
 * @param-tool amount SprudelGainEditor, SprudelGainSequenceEditor
 *
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
 * @param amount The control value to use for gain.
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
     * @param amount The control value to use for gain.
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
 * @param amount The control value to use for gain.
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
 * Sets the stereo panning position for each event (0 = full left, 0.5 = centre, 1 = full right).
 *
 * Accepts control patterns or continuous patterns for animated panning effects.
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
 * @param amount The panning position for each event, ranging from 0 (full left) to 1 (full right).
 * @param-tool amount SprudelPanEditor, SprudelPanSequenceEditor
 *
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
 * @param amount The panning position for each event, ranging from 0 (full left) to 1 (full right).
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
 * @param amount The panning position for each event, ranging from 0 (full left) to 1 (full right).
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
 * Sets the gain 'velocity'. It is multiplied with the gain of the events.
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
 * @param amount The velocity value or pattern to apply to the events.
 *
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
 * @param amount The velocity value or pattern to apply to the events.
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
     * @param amount The velocity value or pattern to apply to the events.
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
 * @param amount The velocity value or pattern to apply to the events.
 */
@KlangScript.Function
fun PatternMapperFn.velocity(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.velocity(amount, callInfo) }

/**
 * Alias for [velocity]. Sets the gain 'velocity'. It is multiplied with the gain of the events.
 *
 * ```KlangScript(Playable)
 * note("c d e f").gain(0.5).vel("0.5 2.0")   // gain is multiplied by velocity
 * ```
 *
 * ```KlangScript(Playable)
 * note("c*4").vel(saw.range(0.25, 1.0).slow(4))   // crescendo pattern over 4 cycles
 * ```
 *
 * @param amount The velocity value or pattern to apply to the events.
 *
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
 * @param amount The velocity value or pattern to apply to the events.
 */
@KlangScript.Function
fun String.vel(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).velocity(amount, callInfo)

/**
 * Alias of [velocity]: the same accessor under another name.
 *
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
 * @param amount The velocity value or pattern to apply to the events.
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
 * Sets the post-gain (applied after voice processing) for each event in the pattern.
 *
 * `postgain` and `gain` are both output multipliers applied at the voice output (SendRenderer),
 * so on a single voice they do the same arithmetic. The difference is what else touches them:
 * `gain` is scaled by `velocity` and by the mute/solo/fade multiplier, while `postgain` is not.
 * So `gain` is the per-note, performable level and `postgain` is the line's own final trim.
 *
 * ```KlangScript(Playable)
 * s("bd sd").postgain(1.5)                    // amplify after processing
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*8").postgain(rand.range(0.1, 1.0))   // random post-gain per hit
 * ```
 *
 * @param amount The post-gain value or pattern to apply to the events.
 *
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
 * @param amount The post-gain value or pattern to apply to the events.
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
     * @param amount The post-gain value or pattern to apply to the events.
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
 * @param amount The post-gain value or pattern to apply to the events.
 */
@KlangScript.Function
fun PatternMapperFn.postgain(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.postgain(amount, callInfo) }

// -- compressor() / comp() --------------------------------------------------------------------------------------------

private val compressorThresholdMutation = voiceSetter {
    compressorThreshold = it?.toString()?.toDoubleOrNull() ?: compressorThreshold
}

private val compressorRatioMutation = voiceSetter {
    compressorRatio = it?.toString()?.toDoubleOrNull() ?: compressorRatio
}

private val compressorKneeMutation = voiceSetter {
    compressorKnee = it?.toString()?.toDoubleOrNull() ?: compressorKnee
}

private val compressorAttackMutation = voiceSetter {
    compressorAttack = it?.toString()?.toDoubleOrNull() ?: compressorAttack
}

private val compressorReleaseMutation = voiceSetter {
    compressorRelease = it?.toString()?.toDoubleOrNull() ?: compressorRelease
}

private fun applyCompressorThreshold(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.compressorThreshold }, update = compressorThresholdMutation)
    }

    return source._applyControlFromParams(args, compressorThresholdMutation) { src, ctrl ->
        src.compressorThreshold = ctrl.compressorThreshold ?: src.compressorThreshold
        src
    }
}

private fun applyCompressorRatio(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, compressorRatioMutation) { src, ctrl ->
        src.compressorRatio = ctrl.compressorRatio ?: src.compressorRatio
        src
    }
}

private fun applyCompressorKnee(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, compressorKneeMutation) { src, ctrl ->
        src.compressorKnee = ctrl.compressorKnee ?: src.compressorKnee
        src
    }
}

private fun applyCompressorAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, compressorAttackMutation) { src, ctrl ->
        src.compressorAttack = ctrl.compressorAttack ?: src.compressorAttack
        src
    }
}

private fun applyCompressorRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, compressorReleaseMutation) { src, ctrl ->
        src.compressorRelease = ctrl.compressorRelease ?: src.compressorRelease
        src
    }
}

/**
 * Sets dynamic range compression parameters. Each parameter is independent and patternable;
 * omitted parameters use the classic defaults in the engine.
 *
 * **Threshold:** The volume level (in decibels) at which compression starts.
 * - Logic: Signals above this level are attenuated.
 * - Range: Usually -60.0 to 0.0.
 *
 * **Ratio:** How much the signal is reduced once it exceeds the threshold.
 * - Logic: A ratio of 4.0 (4:1) means that for every 4dB the input goes over the threshold,
 *   the output only increases by 1dB.
 * - Range: 1.0 (no compression) and up. 20.0 or higher acts as a limiter.
 *
 * **Knee:** The "smoothness" of the transition into compression.
 * - Logic: A value of 0 is a "hard knee" (instant compression at threshold). Higher values (e.g., 6.0) create a
 *   "soft knee" where compression is applied gradually as the signal approaches the threshold.
 *
 * **Attack:** How quickly the compressor reacts to signals exceeding the threshold.
 * - Logic: Measured in seconds. Fast attacks (e.g., 0.003) catch peaks immediately; slow attacks let the
 *   initial "click" or transient through.
 *
 * **Release:** How quickly the compressor stops attenuating after the signal falls back below the threshold.
 * - Logic: Measured in seconds. Short release times (e.g., 0.1) return to normal quickly; long release times
 *   create a smoother, more "levelled" sound.
 *
 * **Common Configurations:**
 *
 * | Use Case          | Configuration        | Description                                                                              |
 * | ----------------- | -------------------- | ---------------------------------------------------------------------------------------- |
 * | Gentle Leveling   | `(-15, 2, 6, 0.01, 0.2)`   | Low ratio and soft knee to subtly even out a melody or pad.                              |
 * | Punchy Drums      | `(-20, 4, 3, 0.03, 0.1)`   | Slightly slower attack to let the drum "hit" (transient) pass before squeezing the tail. |
 * | Brickwall Limiter | `(-2, 40, 0, 0.001, 0.05)` | High ratio and instant attack to prevent any signal from clipping above -2dB.            |
 * | Heavy Squeeze     | `(-30, 8, 2, 0.005, 0.1)`  | Low threshold and high ratio for that "pumping" aggressive sound.                        |
 *
 * ```KlangScript(Playable)
 * s("bd sd").compressor(-20, 4, 3, 0.03, 0.1)  // standard compression
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd*4").compressor("<-10 -30>", "<2 8>", "<1 5>", "<0.01 0.005>", "<0.1 0.5>")   // alternate settings
 * ```
 *
 * ```KlangScript(Playable)
 * // Shorthand: only threshold and ratio (defaults: knee=6.0, attack=0.003, release=0.1)
 * s("hh*8").compressor(-15, 4)
 * ```
 *
 * @param threshold Level in dB above which compression starts (e.g. -20).
 * @param ratio Compression ratio (e.g. 4 means 4:1 reduction above threshold).
 * @param knee Smoothness of compression onset in dB (0 = hard knee, 6+ = soft).
 * @param attack How quickly compression engages, in seconds (e.g. 0.003).
 * @param release How quickly compression releases, in seconds (e.g. 0.1).
 *
 * @param-tool threshold SprudelCompressorEditor, SprudelCompressorSequenceEditor
 * @alias comp
 * @category dynamics
 * @tags compressor, comp, compression, threshold, ratio, dynamics
 */
@KlangScript.Function
fun SprudelPattern.compressor(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = this
    if (threshold != null) p = applyCompressorThreshold(p, listOf<Any?>(threshold).asSprudelDslArgs(callInfo?.forParam(0)))
    if (ratio != null) p = applyCompressorRatio(p, listOf<Any?>(ratio).asSprudelDslArgs(callInfo?.forParam(1)))
    if (knee != null) p = applyCompressorKnee(p, listOf<Any?>(knee).asSprudelDslArgs(callInfo?.forParam(2)))
    if (attack != null) p = applyCompressorAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(3)))
    if (release != null) p = applyCompressorRelease(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(4)))
    return p
}

/**
 * Parses this string as a pattern and sets dynamic range compression parameters.
 *
 * ```KlangScript(Playable)
 * s("bd*4").compressor("<-10 -30>", "<2 8>", "<1 5>", "<0.01 0.005>", "<0.1 0.5>")   // alternate settings
 * ```
 *
 * @param threshold Level in dB above which compression starts (e.g. -20).
 * @param ratio Compression ratio (e.g. 4 means 4:1 reduction above threshold).
 * @param knee Smoothness of compression onset in dB (0 = hard knee, 6+ = soft).
 * @param attack How quickly compression engages, in seconds (e.g. 0.003).
 * @param release How quickly compression releases, in seconds (e.g. 0.1).
 */
@KlangScript.Function
fun String.compressor(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).compressor(threshold, ratio, knee, attack, release, callInfo)

/**
 * The compressor threshold of each event in dB, as a value other setters can read.
 *
 * Bare `compressor` reads what the chain has set so far, so it comes after whatever set the field
 * (`compressor(...)` or an alias). Call it, `compressor(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `comp`.
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("saw").compressor(-12, 4).compressor(add("0 -6 0 -6"))   // harder on every second note
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").compressor("-6 -18", 4).postgain(compressor.div(-6))   // more squash, more make-up
 * ```
 *
 * @category dynamics
 * @tags compressor, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("compressor")
object compressor : FieldAccessor({ it.compressorThreshold }) {

    /**
     * Create a [PatternMapperFn] that sets dynamic range compression parameters for a pattern.
     *
     * ```KlangScript(Playable)
     * s("bd*4").apply(compressor("<-10 -30>", "<2 8>", "<1 5>", "<0.01 0.005>", "<0.1 0.5>"))   // alternate settings
     * ```

     */
    @KlangScript.Invoke
    operator fun invoke(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.compressor(threshold, ratio, knee, attack, release, callInfo) }
}


/**
 * Creates a chained [PatternMapperFn] that sets compressor parameters after the previous mapper.
 *
 * ```KlangScript(Playable)
 * s("bd*4").apply(compressor(-20, 4, 3, 0.03, 0.1).gain(0.8))  // compress + gain chained
 * ```
 *
 * @param threshold Level in dB above which compression starts (e.g. -20).
 * @param ratio Compression ratio (e.g. 4 means 4:1 reduction above threshold).
 * @param knee Smoothness of compression onset in dB (0 = hard knee, 6+ = soft).
 * @param attack How quickly compression engages, in seconds (e.g. 0.003).
 * @param release How quickly compression releases, in seconds (e.g. 0.1).
 */
@KlangScript.Function
fun PatternMapperFn.compressor(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.compressor(threshold, ratio, knee, attack, release, callInfo) }

/**
 * Alias for [compressor]. Sets dynamic range compression parameters; each parameter is
 * independent and patternable.
 *
 * ```KlangScript(Playable)
 * s("bd sd").comp(-20, 4, 3, 0.01, 0.3)                        // standard compression
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd*4").comp("<-10 -30>", "<2 8>", "<1 5>", "<0.01 0.005>", "<0.1 0.5>")   // alternate settings
 * ```
 *
 * @param threshold Level in dB above which compression starts (e.g. -20).
 * @param ratio Compression ratio (e.g. 4 means 4:1 reduction above threshold).
 * @param knee Smoothness of compression onset in dB (0 = hard knee, 6+ = soft).
 * @param attack How quickly compression engages, in seconds (e.g. 0.003).
 * @param release How quickly compression releases, in seconds (e.g. 0.1).
 *
 * @param-tool threshold SprudelCompressorEditor, SprudelCompressorSequenceEditor
 * @alias compressor
 * @category dynamics
 * @tags comp, compressor, compression, threshold, ratio, dynamics
 */
@KlangScript.Function
fun SprudelPattern.comp(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.compressor(threshold, ratio, knee, attack, release, callInfo)

/**
 * Alias for [compressor]. Parses this string as a pattern and sets compression parameters.
 *
 * ```KlangScript(Playable)
 * s("bd*4").comp("<-10 -30>", "<2 8>", "<1 5>", "<0.01 0.005>", "<0.1 0.5>")   // alternate settings
 * ```
 *
 * @param threshold Level in dB above which compression starts (e.g. -20).
 * @param ratio Compression ratio (e.g. 4 means 4:1 reduction above threshold).
 * @param knee Smoothness of compression onset in dB (0 = hard knee, 6+ = soft).
 * @param attack How quickly compression engages, in seconds (e.g. 0.003).
 * @param release How quickly compression releases, in seconds (e.g. 0.1).
 */
@KlangScript.Function
fun String.comp(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).compressor(threshold, ratio, knee, attack, release, callInfo)

/**
 * Alias of [compressor]: the same accessor under another name.
 *
 * @category dynamics
 * @tags comp, compressor, accessor
 */
@KlangScript.Constant
val comp: compressor = compressor

/**
 * Alias for [compressor]. Creates a chained [PatternMapperFn] that sets compressor parameters after the previous
 * mapper.
 *
 * ```KlangScript(Playable)
 * s("bd*4").apply(comp(-20, 4, 3, 0.03, 0.1).gain(0.8))  // compress + gain chained
 * ```
 *
 * @param threshold Level in dB above which compression starts (e.g. -20).
 * @param ratio Compression ratio (e.g. 4 means 4:1 reduction above threshold).
 * @param knee Smoothness of compression onset in dB (0 = hard knee, 6+ = soft).
 * @param attack How quickly compression engages, in seconds (e.g. 0.003).
 * @param release How quickly compression releases, in seconds (e.g. 0.1).
 */
@KlangScript.Function
fun PatternMapperFn.comp(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.compressor(threshold, ratio, knee, attack, release, callInfo) }

// -- unison() / uni() -------------------------------------------------------------------------------------------------

private val unisonMutation = voiceSetter { putOscParam("voices", it?.asDoubleOrNull()) }

private fun applyUnison(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.oscParams?.get("voices") }, update = unisonMutation)
    }

    return source._liftOrReinterpretStringField(args, unisonMutation)
}

/**
 * Sets the number of unison voices for oscillator stacking effects (e.g. supersaw).
 *
 * Higher values produce a thicker, chorus-like sound. Use with `spread` to set how far
 * apart the stacked voices are detuned (in semitones).
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").unison(5)               // 5 stacked sawtooth oscillators
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").unison("<3 6 10 16>").spread(0.3)  // unison pattern
 * ```
 *
 * @param voices The number of unison voices.
 *
 * @alias uni
 * @category dynamics
 * @tags unison, uni, voices, stacking, supersaw
 */
@KlangScript.Function
fun SprudelPattern.unison(voices: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyUnison(this, listOfNotNull(voices).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the number of unison voices.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".s("supersaw").unison("<1 5 10 16>").spread(0.3).note()  // unison pattern
 * ```
 *
 * @param voices The number of unison voices.
 */
@KlangScript.Function
fun String.unison(voices: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).unison(voices, callInfo)

/**
 * The unison voice count of each event, as a value other setters can read.
 *
 * Bare `unison` reads what the chain has set so far, so it comes after whatever set the field
 * (`unison(...)` or an alias). Call it, `unison(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `uni`, `voices`.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("supersaw").unison(3).unison(mul("1 2"))                // the second note thicker
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("supersaw").unison("3 7").spread(unison.div(20))        // more voices, wider
 * ```
 *
 * @category dynamics
 * @tags unison, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("unison")
object unison : FieldAccessor({ it.oscParams?.get("voices") }) {

    /**
     * Create a [PatternMapperFn] that sets the number of unison voices for a pattern.
     *
     * ```KlangScript(Playable)
     * "c3 e3 g3".s("supersaw").apply(unison("<1 5 10 16>")).spread(0.3).note()  // unison pattern
     * ```
     *
     * @param voices The number of unison voices.
     */
    @KlangScript.Invoke
    operator fun invoke(voices: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.unison(voices, callInfo) }
}


/**
 * Creates a chained [PatternMapperFn] that sets the number of unison voices after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").apply(unison(5).spread(0.3))  // unison + spread chained
 * ```
 *
 * @param voices The number of unison voices.
 */
@KlangScript.Function
fun PatternMapperFn.unison(voices: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.unison(voices, callInfo) }

/**
 * Alias for [unison]. Sets the number of unison voices for oscillator stacking effects.
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").uni(5)               // 5 stacked sawtooth oscillators
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").uni("<1 5 10 16>").spread(0.3)  // unison pattern
 * ```
 *
 * @param voices The number of unison voices.
 *
 * @alias unison
 * @category dynamics
 * @tags uni, unison, voices, stacking, supersaw
 */
@KlangScript.Function
fun SprudelPattern.uni(voices: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.unison(voices, callInfo)

/**
 * Alias for [unison]. Parses this string as a pattern and sets the number of unison voices.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".s("supersaw").uni("<1 5 10 16>").spread(0.3).note()  // unison pattern
 * ```
 */
@KlangScript.Function
fun String.uni(voices: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).unison(voices, callInfo)

/**
 * Alias of [unison]: the same accessor under another name.
 *
 * @category dynamics
 * @tags uni, unison, accessor
 */
@KlangScript.Constant
val uni: unison = unison

/**
 * Alias for [unison]. Creates a chained [PatternMapperFn] that sets the number of unison voices after the previous
 * mapper.
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").apply(uni(5).spread(0.3))  // unison + spread chained
 * ```
 *
 * @param voices The number of unison voices.
 */
@KlangScript.Function
fun PatternMapperFn.uni(voices: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.unison(voices, callInfo) }

/**
 * Alias for [unison]. Sets the number of unison voices — the name that matches the ignitor `voices` param.
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").voices(5)               // 5 stacked sawtooth oscillators
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").voices("<3 6 10 16>").spread(0.3)  // unison-count pattern
 * ```
 *
 * @param voices The number of unison voices.
 *
 * @alias unison
 * @category dynamics
 * @tags voices, unison, uni, stacking, supersaw
 */
@KlangScript.Function
fun SprudelPattern.voices(voices: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.unison(voices, callInfo)

/**
 * Alias for [unison]. Parses this string as a pattern and sets the number of unison voices.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".s("supersaw").voices("<1 5 10 16>").spread(0.3).note()  // unison-count pattern
 * ```
 *
 * @param voices The number of unison voices.
 */
@KlangScript.Function
fun String.voices(voices: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).unison(voices, callInfo)

/**
 * Alias of [unison]: the same accessor under another name.
 *
 * @category dynamics
 * @tags voices, unison, accessor
 */
@KlangScript.Constant
val voices: unison = unison

/**
 * Alias for [unison]. Creates a chained [PatternMapperFn] that sets the number of unison voices after the previous
 * mapper.
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").apply(voices(5).spread(0.3))  // unison + spread chained
 * ```
 *
 * @param voices The number of unison voices.
 */
@KlangScript.Function
fun PatternMapperFn.voices(voices: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.unison(voices, callInfo) }

// -- spread() ---------------------------------------------------------------------------------------------------------

private val spreadMutation = voiceSetter { putOscParam("spread", it?.asDoubleOrNull()) }

private fun applySpread(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.oscParams?.get("spread") }, update = spreadMutation)
    }

    return source._liftOrReinterpretStringField(args, spreadMutation)
}

/**
 * Sets the unison frequency spread (in semitones) for super-oscillators.
 *
 * Controls how far each unison voice is detuned from the on-pitch center voice — the classic
 * supersaw "detune" width. Use with `unison` to set the number of voices; higher values
 * produce a wider, more chorused sound. (Renamed from `detune()`: in Klang, `detune` shifts an
 * oscillator's *pitch* — this fans the unison stack apart, so it is `spread`.)
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").unison(5).spread(0.1)   // 5 voices spread ±0.05 semitones
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("supersaw").spread("<0.05 0.10 0.20 0.40>")  // escalating spread each beat
 * ```
 *
 * @param amount The unison spread in semitones.
 *
 * @category dynamics
 * @tags spread, detune, unison, supersaw
 */
@KlangScript.Function
fun SprudelPattern.spread(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySpread(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the unison frequency spread.
 *
 * ```KlangScript(Playable)
 * "c3*4".spread("<0.05 0.10 0.20 0.40>").s("supersaw").note() // escalating spread each beat
 * ```
 *
 * @param amount The unison spread in semitones.
 */
@KlangScript.Function
fun String.spread(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).spread(amount, callInfo)

/**
 * The unison detune spread of each event, as a value other setters can read.
 *
 * Bare `spread` reads what the chain has set so far, so it comes after whatever set the field
 * (`spread(...)` or an alias). Call it, `spread(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("supersaw").unison(5).spread(0.2).spread(mul("1 2"))    // the second note wider
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("supersaw").unison(5).spread("0.1 0.4").analog(spread.mul(10))   // wider detune, more drift
 * ```
 *
 * @category dynamics
 * @tags spread, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("spread")
object spread : FieldAccessor({ it.oscParams?.get("spread") }) {

    /**
     * Creates a [PatternMapperFn] that sets the unison frequency spread for a pattern.
     *
     * ```KlangScript(Playable)
     * note("c3*4").s("supersaw").apply(spread("<0.05 0.10 0.20 0.40>"))  // escalating spread each beat
     * ```
     * @param amount The unison spread in semitones.
     */
    @KlangScript.Invoke
    operator fun invoke(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.spread(amount, callInfo) }
}


/**
 * Creates a chained [PatternMapperFn] that sets the unison frequency spread after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").apply(unison(5).spread(0.1))  // unison + spread chained
 * ```
 *
 * @param amount The unison spread in semitones.
 */
@KlangScript.Function
fun PatternMapperFn.spread(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.spread(amount, callInfo) }

// -- panSpread() ------------------------------------------------------------------------------------------------------

private val panSpreadMutation = voiceSetter { putOscParam("panSpread", it?.asDoubleOrNull()) }

private fun applyPanSpread(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.oscParams?.get("panSpread") }, update = panSpreadMutation)
    }

    return source._liftOrReinterpretStringField(args, panSpreadMutation)
}

/**
 * Sets the stereo pan spread for unison/supersaw voices (0 = mono, 1 = full stereo spread).
 *
 * Controls how widely the unison voices are spread across the stereo field. Use with
 * `unison` to set the number of voices.
 *
 * NOTE: the super-oscillators are currently summed to mono, so `panSpread` is wired but not yet
 * audible — kept as a forward hook for per-voice stereo placement.
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").unison(5).panSpread(0.8)   // wide stereo spread (future)
 * ```
 *
 * @param amount The stereo pan spread, between 0 and 1.
 *
 * @category dynamics
 * @tags panSpread, pan, stereo, unison, supersaw
 */
@KlangScript.Function
fun SprudelPattern.panSpread(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyPanSpread(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the stereo pan spread for unison voices.
 *
 * @param amount The stereo pan spread, between 0 and 1.
 */
@KlangScript.Function
fun String.panSpread(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).panSpread(amount, callInfo)

/**
 * The unison stereo spread of each event, as a value other setters can read. Reserved: no engine
 * stage reads `panSpread` yet, the value travels but changes nothing.
 *
 * Bare `panSpread` reads what the chain has set so far, so it comes after whatever set the field
 * (`panSpread(...)` or an alias). Call it, `panSpread(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("supersaw").unison(5).panSpread(0.5).panSpread(mul("1 0.5"))   // reserved, inaudible for now
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("supersaw").unison(5).panSpread("0.2 0.8").spread(panSpread.div(4))   // the detune follows a reserved value
 * ```
 *
 * @category dynamics
 * @tags panSpread, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("panSpread")
object panSpread : FieldAccessor({ it.oscParams?.get("panSpread") }) {

    /**
     * Creates a [PatternMapperFn] that sets the stereo pan spread for unison voices.
     *
     * @param amount The stereo pan spread, between 0 and 1.
     */
    @KlangScript.Invoke
    operator fun invoke(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.panSpread(amount, callInfo) }
}


/**
 * Creates a chained [PatternMapperFn] that sets the stereo pan spread after the previous mapper.
 *
 * @param amount The stereo pan spread, between 0 and 1.
 */
@KlangScript.Function
fun PatternMapperFn.panSpread(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.panSpread(amount, callInfo) }

// -- density() / d() --------------------------------------------------------------------------------------------------

private val densityMutation = voiceSetter { putOscParam("density", it?.asDoubleOrNull()) }

private fun applyDensity(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.oscParams?.get("density") }, update = densityMutation)
    }

    return source._liftOrReinterpretStringField(args, densityMutation)
}

/**
 * Sets the oscillator density for supersaw or impulse density for the dust generator.
 *
 * For supersaw: controls how tightly packed the oscillators are.
 * For noise generators (e.g. `dust`): controls the number of events per second.
 * (Note: `crackle` is a chaotic generator now — it is driven by `chaos`, not `density`.)
 *
 * ```KlangScript(Playable)
 * note("a").s("dust").density(0.2)   // 40 noise events per second
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").unison(7).density("<0 0.5 1 2>")  // tight supersaw
 * ```
 *
 * @param amount The oscillator density.
 *
 * @alias d
 * @category dynamics
 * @tags density, d, supersaw, dust, noise
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
 * note("c3").s("supersaw").apply(unison(7).density(0.5))  // unison + density chained
 * ```
 *
 * @param amount The oscillator density.
 */
@KlangScript.Function
fun PatternMapperFn.density(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.density(amount, callInfo) }

/**
 * Alias for [density]. Sets the oscillator density for supersaw or impulse density for dust.
 *
 * ```KlangScript(Playable)
 * note("a").s("dust").d(40)   // 40 noise events per second
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3").s("supersaw").unison(7).d(0.5)   // tight supersaw
 * ```
 *
 * @param amount The oscillator density. Integer, typically 1-16. Higher values produce denser sound.
 *
 * @alias density
 * @category dynamics
 * @tags d, density, supersaw, dust, noise
 */
@KlangScript.Function
fun SprudelPattern.d(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.density(amount, callInfo)

/**
 * Alias for [density]. Parses this string as a pattern and sets the oscillator or noise density.
 *
 * ```KlangScript(Playable)
 * "a".d(40).s("dust").note()   // 40 noise events per second
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
 * note("c3").s("supersaw").apply(unison(7).d(0.5))  // unison + density chained
 * ```
 *
 * @param amount The oscillator density.
 */
@KlangScript.Function
fun PatternMapperFn.d(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.density(amount, callInfo) }

// -- ADSR stages ------------------------------------------------------------------------------------------------------
// attack, decay, sustain and release are slots of adsr(), not doors of their own (the single doors
// were removed 2026-09-07, see docs/tasks/sprudel-field-accessors.md). Read them as adsr.attack etc.

private val attackMutation = voiceSetter { attack = it?.asDoubleOrNull() }
private val decayMutation = voiceSetter { decay = it?.asDoubleOrNull() }
private val sustainMutation = voiceSetter { sustain = it?.asDoubleOrNull() }
private val releaseMutation = voiceSetter { release = it?.asDoubleOrNull() }

private fun applyStage(
    source: SprudelPattern,
    args: List<SprudelDslArg<Any?>>,
    read: (SprudelVoiceData) -> Double?,
    update: VoiceModifierFn,
): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = read, update = update)
    }

    return source._liftOrReinterpretStringField(args, update)
}

private fun applyAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    applyStage(source, args, read = { it.attack }, update = attackMutation)

private fun applyDecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    applyStage(source, args, read = { it.decay }, update = decayMutation)

private fun applySustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    applyStage(source, args, read = { it.sustain }, update = sustainMutation)

private fun applyRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    applyStage(source, args, read = { it.release }, update = releaseMutation)

// -- ADSR adsr() ------------------------------------------------------------------------------------------------------

/**
 * Sets the four ADSR envelope parameters. Each parameter is independent and patternable;
 * omitted parameters keep their previous values.
 *
 * attack/decay/release are seconds, sustain is a 0-1 level.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("sine").adsr(0.01, 0.2, 0.7, 0.5)          // standard ADSR
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").adsr("<0.01 0.5>", "<0.1 0.5>", "<0.5 0.8>", "<0.2 1.0>")  // alternate envelopes
 * ```
 *
 * A mapper on a slot applies to that slot and leaves the others alone; the slots can be read
 * back as `adsr.attack`, `adsr.decay`, `adsr.sustain`, `adsr.release`:
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").adsr(0.01, 0.2, 0.7, 0.5).adsr(attack = mul("1 10"))   // the second note swells
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").adsr("0.05 0.3", 0.2, 0.7).adsr(release = adsr.attack)   // symmetric envelope
 * ```
 *
 * @param attack Attack time in seconds — how quickly the note rises from silence to full volume.
 * @param decay Decay time in seconds — how quickly the volume falls from peak to sustain level.
 * @param sustain Sustain level (0–1) — the volume held while the note is pressed.
 * @param release Release time in seconds — how long the note takes to fade to silence after note-off.
 * @param-tool attack SprudelAdsrEditor, SprudelAdsrSequenceEditor
 *
 * @category dynamics
 * @tags adsr, attack, decay, sustain, release, envelope
 */
@KlangScript.Function
fun SprudelPattern.adsr(
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null,
): SprudelPattern {
    var p = this
    if (attack != null) p = applyAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(0)))
    if (decay != null) p = applyDecay(p, listOf<Any?>(decay).asSprudelDslArgs(callInfo?.forParam(1)))
    if (sustain != null) p = applySustain(p, listOf<Any?>(sustain).asSprudelDslArgs(callInfo?.forParam(2)))
    if (release != null) p = applyRelease(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(3)))
    return p
}

/**
 * Parses this string as a pattern and sets the ADSR envelope parameters.
 *
 * ```KlangScript(Playable)
 * "c3*4".adsr(0.01, 0.1, 0.5, 0.2).note()
 * ```
 *
 * @param attack Attack time in seconds.
 * @param decay Decay time in seconds.
 * @param sustain Sustain level (0–1).
 * @param release Release time in seconds.
 */
@KlangScript.Function
fun String.adsr(
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null,
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).adsr(attack, decay, sustain, release, callInfo)

/**
 * The amplitude envelope of each event: `adsr(attack, decay, sustain, release)` sets it, and its
 * four slots can be read back as `adsr.attack`, `adsr.decay`, `adsr.sustain`, `adsr.release`.
 *
 * Each slot is independent: an omitted slot keeps its value, a mapper on a slot applies to that
 * slot (`adsr(attack = mul(2))`), and a read comes after whatever set the slot in the chain.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").adsr("0.05 0.3", 0.2, 0.7).adsr(release = adsr.attack)   // symmetric envelope
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").adsr("0.05 0.3", 0.2, 0.7, 0.4).lpf(adsr.attack.mul(8000))   // slower attack, brighter
 * ```
 *
 * @category dynamics
 * @tags adsr, attack, decay, sustain, release, envelope, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("adsr")
object adsr {

    /** The attack time of each event, as a value other setters can read. */
    @KlangScript.Property
    val attack: FieldAccessor = FieldAccessor { it.attack }

    /** The decay time of each event, as a value other setters can read. */
    @KlangScript.Property
    val decay: FieldAccessor = FieldAccessor { it.decay }

    /** The sustain level of each event, as a value other setters can read. */
    @KlangScript.Property
    val sustain: FieldAccessor = FieldAccessor { it.sustain }

    /** The release time of each event, as a value other setters can read. */
    @KlangScript.Property
    val release: FieldAccessor = FieldAccessor { it.release }

    /**
     * Creates a [PatternMapperFn] that sets the ADSR envelope parameters for each event.
     *
     * ```KlangScript(Playable)
     * note("c3*4").s("sine").apply(adsr(0.01, 0.1, 0.5, 0.2))
     * ```
     *
     * @param attack Attack time in seconds.
     * @param decay Decay time in seconds.
     * @param sustain Sustain level (0–1).
     * @param release Release time in seconds.
     */
    @KlangScript.Invoke
    operator fun invoke(
        attack: PatternLike? = null,
        decay: PatternLike? = null,
        sustain: PatternLike? = null,
        release: PatternLike? = null,
        callInfo: CallInfo? = null,
    ): PatternMapperFn =
        { p -> p.adsr(attack, decay, sustain, release, callInfo) }
}


// -- ADSR curves ------------------------------------------------------------------------------------------------------

private fun parseAdsrCurveName(name: String?): AdsrCurve? = when (name?.trim()?.lowercase()) {
    "linear", "lin" -> AdsrCurve.Linear
    "square", "sq", "quad", "quadratic" -> AdsrCurve.Square
    "cube", "cb", "cubic" -> AdsrCurve.Cube
    "scurve", "s", "smooth", "sigmoid" -> AdsrCurve.SCurve
    "invsquare", "inv", "isquare", "concave" -> AdsrCurve.InvSquare
    "exponential", "exp", "expo" -> AdsrCurve.Exponential
    else -> null
}

private val attackCurveMutation = voiceSetter {
    attackCurve = parseAdsrCurveName(it?.toString()) ?: attackCurve
}

private val decayCurveMutation = voiceSetter {
    decayCurve = parseAdsrCurveName(it?.toString()) ?: decayCurve
}

private val releaseCurveMutation = voiceSetter {
    releaseCurve = parseAdsrCurveName(it?.toString()) ?: releaseCurve
}

private fun applyAttackCurve(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, attackCurveMutation) { src, ctrl ->
        src.attackCurve = ctrl.attackCurve ?: src.attackCurve
        src
    }
}

private fun applyDecayCurve(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, decayCurveMutation) { src, ctrl ->
        src.decayCurve = ctrl.decayCurve ?: src.decayCurve
        src
    }
}

private fun applyReleaseCurve(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, releaseCurveMutation) { src, ctrl ->
        src.releaseCurve = ctrl.releaseCurve ?: src.releaseCurve
        src
    }
}

private val adsrCurveMutation = voiceSetter {
    val curve = parseAdsrCurveName(it?.toString())
    if (curve != null) {
        attackCurve = curve
        decayCurve = curve
        releaseCurve = curve
    }
}

private fun applyAdsrCurve(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, adsrCurveMutation) { src, ctrl ->
        src.attackCurve = ctrl.attackCurve ?: src.attackCurve
        src.decayCurve = ctrl.decayCurve ?: src.decayCurve
        src.releaseCurve = ctrl.releaseCurve ?: src.releaseCurve
        src
    }
}

/**
 * Sets per-stage ADSR shape curves. Each stage is an independent parameter; omitted
 * stages keep their current curve — e.g. `adsrCurves(release = "scurve")` changes only
 * the release.
 *
 * Available curves (aliases in parentheses):
 *  - `linear` (`lin`) — straight ramp.
 *  - `square` (`sq`, `quad`, `quadratic`) — convex: slow-in rise / fast initial drop, long tail.
 *  - `cube` (`cb`, `cubic`) — a more pronounced `square`.
 *  - `scurve` (`s`, `smooth`, `sigmoid`) — ease-in-out, **zero slope at both ends**: no onset
 *    snap and no release "plop" (smoothest).
 *  - `invsquare` (`inv`, `isquare`, `concave`) — concave mirror of `square`: strong start, eases
 *    gently into the endpoint.
 *  - `exponential` (`exp`, `expo`) — a true exponential (convex, long tail).
 *
 * Default when unset: `exp` on EVERY stage — the engine-wide default on every door
 * (maintainer decision, 2026-08-24).
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").adsr(0.01, 0.2, 0.7, 0.5).adsrCurves("square", "exponential", "scurve")
 * ```
 *
 * @param attack Curve name for the attack stage. Omit to keep the current curve.
 * @param decay Curve name for the decay stage. Omit to keep the current curve.
 * @param release Curve name for the release stage. Omit to keep the current curve.
 *
 * @category dynamics
 * @tags adsr, curve, envelope, shape
 */
@KlangScript.Function
fun SprudelPattern.adsrCurves(
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null,
): SprudelPattern {
    var p = this
    if (attack != null) p = applyAttackCurve(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(0)))
    if (decay != null) p = applyDecayCurve(p, listOf<Any?>(decay).asSprudelDslArgs(callInfo?.forParam(1)))
    if (release != null) p = applyReleaseCurve(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(2)))
    return p
}

/**
 * Parses this string as a pattern and sets per-stage ADSR shape curves.
 *
 * @param attack Curve name for the attack stage — `linear` / `square` / `cube` / `scurve` /
 *   `invsquare` / `exponential`. Omit to keep the current curve.
 * @param decay Curve name for the decay stage. Omit to keep the current curve.
 * @param release Curve name for the release stage. Omit to keep the current curve.
 */
@KlangScript.Function
fun String.adsrCurves(
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null,
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).adsrCurves(attack, decay, release, callInfo)

/**
 * Creates a [PatternMapperFn] that sets per-stage ADSR shape curves for each event.
 *
 * @param attack Curve name for the attack stage — `linear` / `square` / `cube` / `scurve` /
 *   `invsquare` / `exponential`. Omit to keep the current curve.
 * @param decay Curve name for the decay stage. Omit to keep the current curve.
 * @param release Curve name for the release stage. Omit to keep the current curve.
 */
@KlangScript.Function
fun adsrCurves(
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null,
): PatternMapperFn =
    { p -> p.adsrCurves(attack, decay, release, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets per-stage ADSR shape curves after the previous mapper.
 *
 * @param attack Curve name for the attack stage. Omit to keep the current curve.
 * @param decay Curve name for the decay stage. Omit to keep the current curve.
 * @param release Curve name for the release stage. Omit to keep the current curve.
 */
@KlangScript.Function
fun PatternMapperFn.adsrCurves(
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null,
): PatternMapperFn =
    this.chain { p -> p.adsrCurves(attack, decay, release, callInfo) }

/**
 * Sets the same ADSR shape curve on all three stages (attack, decay, release).
 *
 * Accepts `linear`, `square`, `cube`, `scurve`, `invsquare`, or `exponential` (see the
 * `adsrCurves` docs for the aliases and the shape of each).
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").adsr(0.01, 0.2, 0.7, 0.5).adsrCurve("scurve")
 * ```
 *
 * @param params Curve name — `linear`, `square`, `cube`, `scurve`, `invsquare`, or `exponential`.
 *
 * @category dynamics
 * @tags adsr, curve, envelope, shape
 */
@KlangScript.Function
fun SprudelPattern.adsrCurve(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyAdsrCurve(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the same curve on all three ADSR stages.
 *
 * @param params Curve name — `linear`, `square`, `cube`, `scurve`, `invsquare`, or `exponential`.
 */
@KlangScript.Function
fun String.adsrCurve(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).adsrCurve(params, callInfo)

/**
 * Creates a [PatternMapperFn] that applies the same ADSR shape curve to all three stages
 * for each event.
 *
 * @param params Curve name — `linear`, `square`, `cube`, `scurve`, `invsquare`, or `exponential`.
 */
@KlangScript.Function
fun adsrCurve(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.adsrCurve(params, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets all ADSR parameters after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("sine").apply(gain(0.8).adsr(0.01, 0.2, 0.7, 0.5))  // gain + adsr chained
 * ```
 *
 * @param attack Attack time in seconds.
 * @param decay Decay time in seconds.
 * @param sustain Sustain level (0–1).
 * @param release Release time in seconds.
 */
@KlangScript.Function
fun PatternMapperFn.adsr(
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null,
): PatternMapperFn =
    this.chain { p -> p.adsr(attack, decay, sustain, release, callInfo) }

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Routing
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// -- orbit() / o() ----------------------------------------------------------------------------------------------------

private val orbitMutation = voiceSetter {
    cylinder = it?.asIntOrNull()
}

private fun applyOrbit(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.cylinder?.toDouble() }, update = orbitMutation)
    }

    return source._liftOrReinterpretStringField(args, orbitMutation)
}

/**
 * Routes the pattern to an audio output orbit (channel group) for independent effect processing.
 *
 * Each orbit can have its own reverb, delay, and other effects applied independently.
 * Use different orbit numbers to send patterns to different effect buses.
 *
 * ```KlangScript(Playable)
 * s("bd sd").orbit(1)                           // send drums to orbit 1
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").orbit(2).room(wet = 0.8, size = 4)  // melodic line on orbit 2 with reverb
 * ```
 *
 * @param index The orbit index to route events to.
 *
 * @alias o
 * @category dynamics
 * @tags orbit, o, routing, effects, bus, channel
 */
@KlangScript.Function
fun SprudelPattern.orbit(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyOrbit(this, listOfNotNull(index).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and routes it to the given audio output orbit.
 *
 * ```KlangScript(Playable)
 * "bd sd".orbit(1).s()   // send drums to orbit 1
 * ```
 *
 * @param index The orbit index to route events to.
 */
@KlangScript.Function
fun String.orbit(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).orbit(index, callInfo)

/**
 * The orbit (effect bus) of each event, as a value other setters can read.
 *
 * Bare `orbit` reads what the chain has set so far, so it comes after whatever set the field
 * (`orbit(...)` or an alias). Call it, `orbit(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `o`.
 *
 * ```KlangScript(Playable)
 * s("bd sd").orbit(0).orbit(add("0 1"))                                    // the snare on orbit 1
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").orbit("0 1").room(orbit.mul(0.3))                         // more reverb on the higher orbit
 * ```
 *
 * @category dynamics
 * @tags orbit, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("orbit")
object orbit : FieldAccessor({ it.cylinder?.toDouble() }) {

    /**
     * Creates a [PatternMapperFn] that routes events to the given audio output orbit.
     *
     * ```KlangScript(Playable)
     * s("bd sd").apply(orbit(1))   // send drums to orbit 1
     * ```
     *
     * @param index The orbit index to route events to.
     */
    @KlangScript.Invoke
    operator fun invoke(index: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.orbit(index, callInfo) }
}


/**
 * Creates a chained [PatternMapperFn] that routes events to the given orbit after the previous mapper.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(gain(0.8).orbit(1))  // gain + orbit chained
 * ```
 *
 * @param index The orbit index to route events to.
 */
@KlangScript.Function
fun PatternMapperFn.orbit(index: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.orbit(index, callInfo) }

/**
 * Alias for [orbit]. Routes the pattern to an audio output orbit for independent effect processing.
 *
 * ```KlangScript(Playable)
 * s("bd sd").o(1)                       // send drums to orbit 1
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").o(2).room(0.8)          // melodic line on orbit 2 with reverb
 * ```
 *
 * @param index The orbit index to route events to.
 *
 * @alias orbit
 * @category dynamics
 * @tags o, orbit, routing, effects, bus, channel
 */
@KlangScript.Function
fun SprudelPattern.o(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.orbit(index, callInfo)

/**
 * Alias for [orbit]. Parses this string as a pattern and routes it to the given orbit.
 *
 * ```KlangScript(Playable)
 * "bd sd".o(1).s()   // send drums to orbit 1
 * ```
 *
 * @param index The orbit index to route events to.
 */
@KlangScript.Function
fun String.o(index: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).orbit(index, callInfo)

/**
 * Alias of [orbit]: the same accessor under another name.
 *
 * @category dynamics
 * @tags o, orbit, accessor
 */
@KlangScript.Constant
val o: orbit = orbit

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Ducking / Sidechain
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// -- duckorbit() / duck() ---------------------------------------------------------------------------------------------

private val duckOrbitMutation = voiceSetter {
    duckCylinder = it?.asIntOrNull()
}

private fun applyDuckOrbit(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.duckCylinder?.toDouble() }, update = duckOrbitMutation)
    }

    return source._liftNumericField(args, duckOrbitMutation)
}

/**
 * Sets the target orbit to listen to for sidechain ducking.
 *
 * The pattern's volume is reduced when audio is detected on the specified orbit.
 * Use with `duckdepth` to set the attenuation amount and `duckattack` for the recovery time.
 *
 * ```KlangScript(Playable)
 * s("bd*4").orbit(1)                              // kick drum on orbit 1
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").duckorbit(1).duckdepth(0.8)   // duck when kick plays on orbit 1
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 *
 * @alias duck
 * @category dynamics
 * @tags duckorbit, duck, sidechain, ducking, dynamics
 */
@KlangScript.Function
fun SprudelPattern.duckorbit(orbitIndex: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDuckOrbit(this, listOfNotNull(orbitIndex).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the sidechain source orbit for ducking.
 *
 * ```KlangScript(Playable)
 * "c3 e3".duckorbit(1).duckdepth(0.8).note()   // duck when orbit 1 plays
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 */
@KlangScript.Function
fun String.duckorbit(orbitIndex: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).duckorbit(orbitIndex, callInfo)

/**
 * The orbit each event ducks, as a value other setters can read.
 *
 * Bare `duckorbit` reads what the chain has set so far, so it comes after whatever set the field
 * (`duckorbit(...)` or an alias). Call it, `duckorbit(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `duck`.
 *
 * ```KlangScript(Playable)
 * stack(note("c3").s("saw").duckdepth(0.8).duckorbit(1).duckorbit(add("<0 1>")), s("bd*4").orbit(1), s("hh*8").orbit(2))   // ducked by the kick, next cycle by the hats
 * ```
 *
 * ```KlangScript(Playable)
 * stack(note("c3").s("saw").duckorbit("<1 2>").duckdepth(duckorbit.mul(0.4)), s("bd*4").orbit(1), s("hh*8").orbit(2))   // ducked deeper by the hats than by the kick
 * ```
 *
 * @category dynamics
 * @tags duckorbit, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("duckorbit")
object duckorbit : FieldAccessor({ it.duckCylinder?.toDouble() }) {

    /**
     * Creates a [PatternMapperFn] that sets the sidechain source orbit for ducking.
     *
     * ```KlangScript(Playable)
     * note("c3 e3 g3").apply(duckorbit(1)).duckdepth(0.8)   // duck when orbit 1 plays
     * ```
     *
     * @param orbitIndex The orbit index to listen to for the sidechain trigger.
     */
    @KlangScript.Invoke
    operator fun invoke(orbitIndex: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.duckorbit(orbitIndex, callInfo) }
}


/**
 * Creates a chained [PatternMapperFn] that sets the sidechain source orbit after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(gain(0.8).duckorbit(1))  // gain + duckorbit chained
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 */
@KlangScript.Function
fun PatternMapperFn.duckorbit(orbitIndex: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.duckorbit(orbitIndex, callInfo) }

/**
 * Alias for [duckorbit]. Sets the target orbit to listen to for sidechain ducking.
 *
 * ```KlangScript(Playable)
 * stack(
 *   s("bd*4").orbit(0),                               // kick drum on orbit 0
 *   note("c3 e3").orbit(1).duck(0).duckdepth(1.0),    // duck when kick plays on orbit 0
 * )
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 *
 * @alias duckorbit
 * @category dynamics
 * @tags duck, duckorbit, sidechain, ducking, dynamics
 */
@KlangScript.Function
fun SprudelPattern.duck(orbitIndex: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.duckorbit(orbitIndex, callInfo)

/**
 * Alias for [duckorbit]. Parses this string as a pattern and sets the sidechain source orbit.
 *
 * ```KlangScript(Playable)
 * "c3 e3".duck(0).duckdepth(0.8).note()   // duck when orbit 0 plays
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 */
@KlangScript.Function
fun String.duck(orbitIndex: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).duckorbit(orbitIndex, callInfo)

/**
 * Alias of [duckorbit]: the same accessor under another name.
 *
 * @category dynamics
 * @tags duck, duckorbit, accessor
 */
@KlangScript.Constant
val duck: duckorbit = duckorbit

/**
 * Alias for [duckorbit]. Creates a chained [PatternMapperFn] that sets the sidechain source orbit after the
 * previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(gain(0.8).duck(0))  // gain + duck chained
 * ```
 *
 * @param orbitIndex The orbit index to listen to for the sidechain trigger.
 */
@KlangScript.Function
fun PatternMapperFn.duck(orbitIndex: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.duckorbit(orbitIndex, callInfo) }

// -- duckattack() / duckatt() -----------------------------------------------------------------------------------------

private val duckAttackMutation = voiceSetter { duckAttack = it?.asDoubleOrNull() }

private fun applyDuckAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.duckAttack }, update = duckAttackMutation)
    }

    return source._liftNumericField(args, duckAttackMutation)
}

/**
 * Sets the duck release (return-to-normal) time in seconds for sidechain ducking.
 *
 * Controls how quickly the ducked pattern returns to its full volume after the sidechain
 * trigger stops. Shorter values snap back quickly; longer values create a pumping effect.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").duck(1).duckdepth(0.8).duckattack(0.2)   // 200 ms recovery
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").duckattack("<0.05 0.1 0.3 0.5>")          // varying recovery times
 * ```
 *
 * @param time The recovery time in seconds.
 *
 * @alias duckatt
 * @category dynamics
 * @tags duckattack, duckatt, sidechain, ducking, release, dynamics
 */
@KlangScript.Function
fun SprudelPattern.duckattack(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDuckAttack(this, listOfNotNull(time).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the duck release time.
 *
 * ```KlangScript(Playable)
 * "c3*4".duckattack("<0.05 0.1 0.3 0.5>").note()   // varying recovery times
 * ```
 *
 * @param time The recovery time in seconds.
 */
@KlangScript.Function
fun String.duckattack(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).duckattack(time, callInfo)

/**
 * The ducking recovery time of each event, as a value other setters can read. The duck-down is
 * instant; this smooths the return (named attack for strudel compatibility).
 *
 * Bare `duckattack` reads what the chain has set so far, so it comes after whatever set the field
 * (`duckattack(...)` or an alias). Call it, `duckattack(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `duckatt`.
 *
 * ```KlangScript(Playable)
 * stack(note("c3").s("saw").duckorbit(1).duckdepth(0.8).duckattack(0.05).duckattack(mul("<1 4>")), s("bd*4").orbit(1))   // a slower recovery every other cycle
 * ```
 *
 * ```KlangScript(Playable)
 * stack(note("c3").s("saw").duckorbit(1).duckattack("<0.02 0.1>").duckdepth(duckattack.mul(5)), s("bd*4").orbit(1))   // slower recovery, deeper duck
 * ```
 *
 * @category dynamics
 * @tags duckattack, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("duckattack")
object duckattack : FieldAccessor({ it.duckAttack }) {

    /**
     * Creates a [PatternMapperFn] that sets the duck release time.
     *
     * ```KlangScript(Playable)
     * note("c3 e3").apply(duckattack(0.2)).duck(1).duckdepth(0.8)   // 200 ms recovery
     * ```
     *
     * @param time The recovery time in seconds.
     */
    @KlangScript.Invoke
    operator fun invoke(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.duckattack(time, callInfo) }
}


/**
 * Creates a chained [PatternMapperFn] that sets the duck recovery time after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(duck(1).duckattack(0.2))  // duck + duckattack chained
 * ```
 *
 * @param time The recovery time in seconds.
 */
@KlangScript.Function
fun PatternMapperFn.duckattack(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.duckattack(time, callInfo) }

/**
 * Alias for [duckattack]. Sets the duck release (return-to-normal) time in seconds.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").duck(1).duckdepth(0.8).duckatt(0.2)   // 200 ms recovery
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").duckatt("<0.05 0.1 0.3 0.5>")          // varying recovery times
 * ```
 *
 * @param time The recovery time in seconds.
 *
 * @alias duckattack
 * @category dynamics
 * @tags duckatt, duckattack, sidechain, ducking, release, dynamics
 */
@KlangScript.Function
fun SprudelPattern.duckatt(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.duckattack(time, callInfo)

/**
 * Alias for [duckattack]. Parses this string as a pattern and sets the duck release time.
 *
 * ```KlangScript(Playable)
 * "c3*4".duckatt("<0.05 0.1 0.3 0.5>").note()   // varying recovery times
 * ```
 *
 * @param time The recovery time in seconds.
 */
@KlangScript.Function
fun String.duckatt(time: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).duckattack(time, callInfo)

/**
 * Alias of [duckattack]: the same accessor under another name.
 *
 * @category dynamics
 * @tags duckatt, duckattack, accessor
 */
@KlangScript.Constant
val duckatt: duckattack = duckattack

/**
 * Alias for [duckattack]. Creates a chained [PatternMapperFn] that sets the duck recovery time after the previous
 * mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(duck(1).duckatt(0.2))  // duck + duckatt chained
 * ```
 *
 * @param time The recovery time in seconds.
 */
@KlangScript.Function
fun PatternMapperFn.duckatt(time: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.duckattack(time, callInfo) }

// -- duckdepth() ------------------------------------------------------------------------------------------------------

private val duckDepthMutation = voiceSetter { duckDepth = it?.asDoubleOrNull() }

private fun applyDuckDepth(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.duckDepth }, update = duckDepthMutation)
    }

    return source._liftNumericField(args, duckDepthMutation)
}

/**
 * Sets the ducking depth (0.0 = no ducking, 1.0 = full silence) for sidechain ducking.
 *
 * Controls how much the pattern is attenuated when the sidechain trigger fires.
 * Use with `duckorbit` to set the sidechain source and `duckattack` for recovery time.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").duck(1).duckdepth(0.8)           // 80% attenuation on sidechain
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").duckdepth("<0.3 0.6 0.9 1.0>")   // escalating ducking depth
 * ```
 *
 * @param amount The ducking depth between 0.0 (no ducking) and 1.0 (full silence).
 *
 * @category dynamics
 * @tags duckdepth, sidechain, ducking, attenuation, dynamics
 */
@KlangScript.Function
fun SprudelPattern.duckdepth(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyDuckDepth(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the ducking depth.
 *
 * ```KlangScript(Playable)
 * "c3*4".duckdepth("<0.3 0.6 0.9 1.0>").note()   // escalating ducking depth
 * ```
 *
 * @param amount The ducking depth between 0.0 (no ducking) and 1.0 (full silence).
 */
@KlangScript.Function
fun String.duckdepth(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).duckdepth(amount, callInfo)

/**
 * The ducking depth of each event, as a value other setters can read.
 *
 * Bare `duckdepth` reads what the chain has set so far, so it comes after whatever set the field
 * (`duckdepth(...)` or an alias). Call it, `duckdepth(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * ```KlangScript(Playable)
 * stack(note("c3").s("saw").duckorbit(1).duckdepth(0.5).duckdepth(mul("<1 0.5>")), s("bd*4").orbit(1))   // a shallower duck every other cycle
 * ```
 *
 * ```KlangScript(Playable)
 * stack(note("c3").s("saw").duckorbit(1).duckdepth("<0.3 0.9>").duckattack(duckdepth.div(10)), s("bd*4").orbit(1))   // deeper duck, slower recovery
 * ```
 *
 * @category dynamics
 * @tags duckdepth, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("duckdepth")
object duckdepth : FieldAccessor({ it.duckDepth }) {

    /**
     * Creates a [PatternMapperFn] that sets the ducking depth.
     *
     * ```KlangScript(Playable)
     * note("c3*4").apply(duckdepth("<0.3 0.6 0.9 1.0>"))   // escalating ducking depth
     * ```
     *
     * @param amount The ducking depth between 0.0 (no ducking) and 1.0 (full silence).
     */
    @KlangScript.Invoke
    operator fun invoke(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.duckdepth(amount, callInfo) }
}


/**
 * Creates a chained [PatternMapperFn] that sets the ducking depth after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3*4").apply(duck(1).duckdepth(0.8))  // duck + duckdepth chained
 * ```
 *
 * @param amount The ducking depth between 0.0 (no ducking) and 1.0 (full silence).
 */
@KlangScript.Function
fun PatternMapperFn.duckdepth(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.duckdepth(amount, callInfo) }
