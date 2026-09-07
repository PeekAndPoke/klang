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

// -- compressor ------------------------------------------------------------------------------------------------------

private val compressorThresholdMutation = voiceSetter { compressorThreshold = it?.asDoubleOrNull() ?: compressorThreshold }

private fun applyCompressorThreshold(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.compressorThreshold }, update = compressorThresholdMutation)
    }

    return source._liftOrReinterpretNumericalField(args, compressorThresholdMutation)
}

private val compressorRatioMutation = voiceSetter { compressorRatio = it?.asDoubleOrNull() ?: compressorRatio }

private fun applyCompressorRatio(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.compressorRatio }, update = compressorRatioMutation)
    }

    return source._liftOrReinterpretNumericalField(args, compressorRatioMutation)
}

private val compressorKneeMutation = voiceSetter { compressorKnee = it?.asDoubleOrNull() ?: compressorKnee }

private fun applyCompressorKnee(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.compressorKnee }, update = compressorKneeMutation)
    }

    return source._liftOrReinterpretNumericalField(args, compressorKneeMutation)
}

private val compressorAttackMutation = voiceSetter { compressorAttack = it?.asDoubleOrNull() ?: compressorAttack }

private fun applyCompressorAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.compressorAttack }, update = compressorAttackMutation)
    }

    return source._liftOrReinterpretNumericalField(args, compressorAttackMutation)
}

private val compressorReleaseMutation = voiceSetter { compressorRelease = it?.asDoubleOrNull() ?: compressorRelease }

private fun applyCompressorRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.compressorRelease }, update = compressorReleaseMutation)
    }

    return source._liftOrReinterpretNumericalField(args, compressorReleaseMutation)
}

/**
 * The orbit compressor: threshold, ratio, knee, attack and release.
 *
 * Levels above the threshold are turned down by the ratio; the knee softens the onset, attack and release set how fast it moves.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`compressor(ratio = mul(2))`), and the numeric slots read back as `compressor.threshold`, `compressor.ratio`, `compressor.knee`, `compressor.attack`, `compressor.release`.
 * With no argument at all, the pattern's own values are reinterpreted as `threshold`.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh sd").compressor(-20, 4, 6, 0.003, 0.1)                     // a firm hand on the drum bus
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh sd").compressor(-20, 4).compressor(ratio = mul("<1 2>"))   // twice the ratio every other bar
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh sd").compressor(-20, 4).postgain(compressor.threshold.mul(-0.02).add(1))   // make-up gain from the threshold
 * ```
 *
 * @param threshold Level in dB above which compression starts (for example -20).
 * @param ratio Compression ratio; 4 means 4:1 above the threshold.
 * @param knee Knee width in dB; 0 is a hard knee, 6 and above soft.
 * @param attack Attack in seconds, how fast the compression engages (for example 0.003).
 * @param release Release in seconds, how fast it lets go (for example 0.1).
 * @param-tool threshold SprudelCompressorEditor, SprudelCompressorSequenceEditor
 *
 * @category dynamics
 * @tags compressor, threshold, ratio, knee, attack, release
 */
@KlangScript.Function
fun SprudelPattern.compressor(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch threshold: reinterpret runs only on a fully bare call.
    var p = if (threshold != null || !(ratio != null || knee != null || attack != null || release != null)) {
        applyCompressorThreshold(this, listOfNotNull(threshold).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (ratio != null) p = applyCompressorRatio(p, listOf<Any?>(ratio).asSprudelDslArgs(callInfo?.forParam(1)))
    if (knee != null) p = applyCompressorKnee(p, listOf<Any?>(knee).asSprudelDslArgs(callInfo?.forParam(2)))
    if (attack != null) p = applyCompressorAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(3)))
    if (release != null) p = applyCompressorRelease(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(4)))
    return p
}

/** Parses this string as a pattern, then applies [compressor]. */
@KlangScript.Function
fun String.compressor(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).compressor(threshold, ratio, knee, attack, release, callInfo)

/** Chains a [compressor] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.compressor(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.compressor(threshold, ratio, knee, attack, release, callInfo) }

/**
 * The `compressor` object: `compressor(...)` sets the slots, and each numeric slot reads back as a child,
 * `compressor.threshold`, `compressor.ratio`, `compressor.knee`, `compressor.attack`, `compressor.release`.
 *
 * @category dynamics
 * @tags compressor, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("compressor")
object compressor {

    /** The threshold slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val threshold: FieldAccessor = FieldAccessor { it.compressorThreshold }

    /** The ratio slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val ratio: FieldAccessor = FieldAccessor { it.compressorRatio }

    /** The knee slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val knee: FieldAccessor = FieldAccessor { it.compressorKnee }

    /** The attack slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val attack: FieldAccessor = FieldAccessor { it.compressorAttack }

    /** The release slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val release: FieldAccessor = FieldAccessor { it.compressorRelease }

    /** The setter, see [SprudelPattern.compressor]. */
    @KlangScript.Invoke
    operator fun invoke(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.compressor(threshold, ratio, knee, attack, release, callInfo) }
}

/**
 * `comp`, the short name of [compressor]: the same door, use whichever reads better.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh sd").comp(-20, 4, 6, 0.003, 0.1)
 * ```
 *
 * @category dynamics
 * @tags comp, compressor
 * @param-tool threshold SprudelCompressorEditor, SprudelCompressorSequenceEditor
 */
@KlangScript.Function
fun SprudelPattern.comp(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    compressor(threshold, ratio, knee, attack, release, callInfo)

/** Parses this string as a pattern, then applies [comp]. */
@KlangScript.Function
fun String.comp(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.compressor(threshold, ratio, knee, attack, release, callInfo)

/**
 * Alias of [compressor]: the same object under its short name.
 *
 * @category dynamics
 * @tags comp, compressor, accessor
 */
@KlangScript.Constant
val comp: compressor = compressor

/** Chains a [comp] step onto this [PatternMapperFn] (see [SprudelPattern.comp]). */
@KlangScript.Function
fun PatternMapperFn.comp(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.compressor(threshold, ratio, knee, attack, release, callInfo)

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
 * Stacks detuned copies of the oscillator; `spread` is the detune in semitones, `pan` the stereo width.
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
 * @param voices Number of unison voices, 1 to 16.
 * @param spread Detune spread in semitones.
 * @param pan Stereo spread, 0 to 1. Reserved: the engine does not read it yet.

 *
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
// were removed 2026-09-07, see docs/tasks-archive/2026-09/20260907-sprudel-field-accessors.md). Read them as adsr.attack etc.

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
 * The `adsrCurves` object: `adsrCurves(attack, decay, release)` sets the stage curves by name.
 * The slots are names, not numbers, so the object carries the setter only and no readers
 * (maintainer decision, 2026-09-07).
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").adsr(0.01, 0.2, 0.7, 0.5).apply(adsrCurves("square", "exponential", "scurve"))
 * ```
 *
 * @category dynamics
 * @tags adsr, curve, envelope, shape
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("adsrCurves")
object adsrCurves {

    /** The setter, see [SprudelPattern.adsrCurves]. */
    @KlangScript.Invoke
    operator fun invoke(
        attack: PatternLike? = null,
        decay: PatternLike? = null,
        release: PatternLike? = null,
        callInfo: CallInfo? = null,
    ): PatternMapperFn = { p -> p.adsrCurves(attack, decay, release, callInfo) }
}

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

// -- duck ------------------------------------------------------------------------------------------------------------

private val duckOrbitMutation = voiceSetter { duckCylinder = it?.asIntOrNull() ?: duckCylinder }

private fun applyDuckOrbit(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.duckCylinder?.toDouble() }, update = duckOrbitMutation)
    }

    return source._liftOrReinterpretNumericalField(args, duckOrbitMutation)
}

private val duckDepthMutation = voiceSetter { duckDepth = it?.asDoubleOrNull() }

private fun applyDuckDepth(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.duckDepth }, update = duckDepthMutation)
    }

    return source._liftOrReinterpretNumericalField(args, duckDepthMutation)
}

private val duckAttackMutation = voiceSetter { duckAttack = it?.asDoubleOrNull() }

private fun applyDuckAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.duckAttack }, update = duckAttackMutation)
    }

    return source._liftOrReinterpretNumericalField(args, duckAttackMutation)
}

/**
 * Sidechain ducking: the orbit that triggers it, the depth and the recovery time.
 *
 * The pattern carrying `duck(...)` is ducked whenever the named orbit plays; the duck-down is instant, `attack` is the recovery.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`duck(depth = mul(2))`), and the numeric slots read back as `duck.orbit`, `duck.depth`, `duck.attack`.
 * With no argument at all, the pattern's own values are reinterpreted as `orbit`.
 *
 * ```KlangScript(Playable)
 * stack(s("bd*4").orbit(1), note("c2*8").s("saw").duck(1, 0.8, 0.2))          // the bass ducks under the kick
 * ```
 *
 * ```KlangScript(Playable)
 * stack(s("bd*4").orbit(1), note("c2*8").s("saw").duck(1, 0.8).duck(depth = mul("<1 0.5>")))   // half as deep every other bar
 * ```
 *
 * ```KlangScript(Playable)
 * stack(s("bd*4").orbit(1), note("c2*8").s("saw").duck(1, "0.3 0.9").gain(duck.depth))   // deeper duck, louder bass
 * ```
 *
 * @param orbit Orbit index whose voices trigger the duck.
 * @param depth Depth, 0 (no ducking) to 1 (full silence).
 * @param attack Recovery time in seconds after the trigger stops.

 *
 * @category dynamics
 * @tags duck, orbit, depth, attack
 */
@KlangScript.Function
fun SprudelPattern.duck(orbit: PatternLike? = null, depth: PatternLike? = null, attack: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch orbit: reinterpret runs only on a fully bare call.
    var p = if (orbit != null || !(depth != null || attack != null)) {
        applyDuckOrbit(this, listOfNotNull(orbit).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (depth != null) p = applyDuckDepth(p, listOf<Any?>(depth).asSprudelDslArgs(callInfo?.forParam(1)))
    if (attack != null) p = applyDuckAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(2)))
    return p
}

/** Parses this string as a pattern, then applies [duck]. */
@KlangScript.Function
fun String.duck(orbit: PatternLike? = null, depth: PatternLike? = null, attack: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).duck(orbit, depth, attack, callInfo)

/** Chains a [duck] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.duck(orbit: PatternLike? = null, depth: PatternLike? = null, attack: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.duck(orbit, depth, attack, callInfo) }

/**
 * The `duck` object: `duck(...)` sets the slots, and each numeric slot reads back as a child,
 * `duck.orbit`, `duck.depth`, `duck.attack`.
 *
 * @category dynamics
 * @tags duck, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("duck")
object duck {

    /** The orbit slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val orbit: FieldAccessor = FieldAccessor { it.duckCylinder?.toDouble() }

    /** The depth slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val depth: FieldAccessor = FieldAccessor { it.duckDepth }

    /** The attack slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val attack: FieldAccessor = FieldAccessor { it.duckAttack }

    /** The setter, see [SprudelPattern.duck]. */
    @KlangScript.Invoke
    operator fun invoke(orbit: PatternLike? = null, depth: PatternLike? = null, attack: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.duck(orbit, depth, attack, callInfo) }
}
