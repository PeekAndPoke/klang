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
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel._liftData
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

// -- begin() ----------------------------------------------------------------------------------------------------------

private val beginMutation = voiceSetter { begin = it?.asDoubleOrNull() }

private fun applyBegin(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.begin }, update = beginMutation)
    }

    return source._liftOrReinterpretNumericalField(args, beginMutation)
}

/**
 * Sets the sample start position as a fraction of the total sample length (0–1).
 *
 * `0` starts playback from the very beginning; `1` would start at the very end (silence).
 * Useful for skipping intros or targeting specific sections of a longer sample. Combine
 * with [end] to play only a segment.
 *
 * @param pos Start position in [0, 1]; 0 = beginning, 1 = end.
 * @return A pattern with the sample start position set.
 *
 * ```KlangScript(Playable)
 * s("breaks").begin(0.5)            // start halfway through the sample
 * ```
 *
 * ```KlangScript(Playable)
 * s("breaks").begin("<0 0.25 0.5>") // cycle through three start positions
 * ```
 *
 * @category sampling
 * @tags begin, start, sample, position, offset
 */
@KlangScript.Function
fun SprudelPattern.begin(pos: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyBegin(this, listOfNotNull(pos).asSprudelDslArgs(callInfo))

/** Sets the sample start position (0–1) on a string pattern. */
@KlangScript.Function
fun String.begin(pos: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).begin(pos, callInfo)

/**
 * The sample start position of each event (0..1), as a value other setters can read.
 *
 * Bare `begin` reads what the chain has set so far, so it comes after whatever set the field
 * (`begin(...)` or an alias). Call it, `begin(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * ```KlangScript(Playable)
 * s("breaks*4").begin(0.25).begin(mul("1 2 1 3"))                         // start points that jump
 * ```
 *
 * ```KlangScript(Playable)
 * s("breaks*2").begin("0 0.5").end(begin.add(0.25))                      // a quarter of the sample from each start
 * ```
 *
 * @category sampling
 * @tags begin, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("begin")
object begin : FieldAccessor({ it.begin }) {

    /**
     * Returns a [PatternMapperFn] that sets the sample start position (0–1).
     *
     * @param pos Start position in [0, 1].
     * @return A [PatternMapperFn] that sets the begin field on the source pattern.
     *
     * ```KlangScript(Playable)
     * s("breaks").apply(begin(0.5))     // via mapper
     * ```
     */
    @KlangScript.Invoke
    operator fun invoke(pos: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.begin(pos, callInfo) }
}


/** Chains a begin onto this [PatternMapperFn]; sets the sample start position (0–1). */
@KlangScript.Function
fun PatternMapperFn.begin(pos: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.begin(pos, callInfo) }

// -- end() ------------------------------------------------------------------------------------------------------------

private val endMutation = voiceSetter { end = it?.asDoubleOrNull() }

private fun applyEnd(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.end }, update = endMutation)
    }

    return source._liftOrReinterpretNumericalField(args, endMutation)
}

/**
 * Sets the sample end position as a fraction of the total sample length (0–1).
 *
 * `1` plays to the very end of the sample; `0` would end immediately (silence). Use with
 * [begin] to play only a segment of a sample.
 *
 * @param pos End position in [0, 1]; 1 = end of sample.
 * @return A pattern with the sample end position set.
 *
 * ```KlangScript(Playable)
 * s("breaks").end(0.5)            // play only the first half of the sample
 * ```
 *
 * ```KlangScript(Playable)
 * s("breaks").begin(0.25).end(0.75)  // play the middle 50% of the sample
 * ```
 *
 * @category sampling
 * @tags end, stop, sample, position, offset
 */
@KlangScript.Function
fun SprudelPattern.end(pos: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyEnd(this, listOfNotNull(pos).asSprudelDslArgs(callInfo))

/** Sets the sample end position (0–1) on a string pattern. */
@KlangScript.Function
fun String.end(pos: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).end(pos, callInfo)

/**
 * The sample end position of each event (0..1), as a value other setters can read.
 *
 * Bare `end` reads what the chain has set so far, so it comes after whatever set the field
 * (`end(...)` or an alias). Call it, `end(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * ```KlangScript(Playable)
 * s("breaks*4").end(0.5).end(mul(perlin.seg(4).range(0.5, 1)))           // the cut point wanders
 * ```
 *
 * ```KlangScript(Playable)
 * s("breaks*2").end("0.5 1").begin(end.sub(0.25))                        // start a quarter before each end
 * ```
 *
 * @category sampling
 * @tags end, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("end")
object end : FieldAccessor({ it.end }) {

    /**
     * Returns a [PatternMapperFn] that sets the sample end position (0–1).
     *
     * @param pos End position in [0, 1].
     * @return A [PatternMapperFn] that sets the end field on the source pattern.
     *
     * ```KlangScript(Playable)
     * s("breaks").apply(end(0.5))     // via mapper
     * ```
     */
    @KlangScript.Invoke
    operator fun invoke(pos: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.end(pos, callInfo) }
}


/** Chains an end onto this [PatternMapperFn]; sets the sample end position (0–1). */
@KlangScript.Function
fun PatternMapperFn.end(pos: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.end(pos, callInfo) }

// -- speed() ----------------------------------------------------------------------------------------------------------

private val speedMutation = voiceSetter { speed = it?.asDoubleOrNull() }

private fun applySpeed(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.speed }, update = speedMutation)
    }

    return source._liftOrReinterpretNumericalField(args, speedMutation)
}

/**
 * Sets the sample playback speed as a multiplier.
 *
 * `1` is normal speed; `2` doubles the speed (one octave up); `0.5` halves the speed
 * (one octave down). A negative speed sounds only when `begin` is set (it then plays backwards
 * to the sample start); with `begin` unset the playhead starts at 0 and the voice is silent.
 *
 * @param rate Speed multiplier; 1 = normal, 2 = double, 0.5 = half; negative only sounds with `begin` set (backwards to the start).
 * @return A pattern with the playback speed set.
 *
 * ```KlangScript(Playable)
 * s("breaks").speed(2)              // double speed, one octave up
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").speed("<1 0.5>")          // alternate normal and half speed per cycle
 * ```
 *
 * @category sampling
 * @tags speed, playback, pitch, rate
 */
@KlangScript.Function
fun SprudelPattern.speed(rate: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySpeed(this, listOfNotNull(rate).asSprudelDslArgs(callInfo))

/** Sets the sample playback speed on a string pattern. */
@KlangScript.Function
fun String.speed(rate: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).speed(rate, callInfo)

/**
 * The playback speed of each event, as a value other setters can read.
 *
 * Bare `speed` reads what the chain has set so far, so it comes after whatever set the field
 * (`speed(...)` or an alias). Call it, `speed(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * ```KlangScript(Playable)
 * s("breaks*4").speed(1).speed(mul("1 0.5 1 2"))                           // normal, half, normal, double
 * ```
 *
 * ```KlangScript(Playable)
 * s("breaks*2").speed("0.5 2").gain(speed.mul(0.4))                      // faster is louder
 * ```
 *
 * @category sampling
 * @tags speed, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("speed")
object speed : FieldAccessor({ it.speed }) {

    /**
     * Returns a [PatternMapperFn] that sets the sample playback speed.
     *
     * @param rate Speed multiplier; 1 = normal, 2 = double, 0.5 = half; negative only sounds with `begin` set (backwards to the start).
     * @return A [PatternMapperFn] that sets the speed field on the source pattern.
     *
     * ```KlangScript(Playable)
     * s("breaks").apply(speed(2))       // double speed via mapper
     * ```
     */
    @KlangScript.Invoke
    operator fun invoke(rate: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.speed(rate, callInfo) }
}


/** Chains a speed onto this [PatternMapperFn]; sets the sample playback speed. */
@KlangScript.Function
fun PatternMapperFn.speed(rate: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.speed(rate, callInfo) }

// -- unit() -----------------------------------------------------------------------------------------------------------

private val unitMutation = voiceSetter { unit = it?.asVoiceValue()?.asString }

private fun applyUnit(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.isEmpty()) return source
    val control = args.toPattern(unitMutation)
    return source._liftData(control)
}

/**
 * Sets the time unit used for sample playback duration.
 *
 * Determines how [speed] and other timing-related parameters are interpreted. The value
 * `"c"` (cycles) makes the sample stretch or compress to fit the pattern's cycle timing.
 * Used internally by [loopAt] and [loopAtCps].
 *
 * @param value Time unit string — `"c"` for cycles, `"s"` for seconds.
 * @return A pattern with the time unit set.
 *
 * ```KlangScript(Playable)
 * s("breaks").unit("c").slow(2)     // stretch sample to fill 2 cycles
 * ```
 *
 * ```KlangScript(Playable)
 * s("breaks").speed(0.5).unit("c")  // half-speed in cycle units
 * ```
 *
 * @category sampling
 * @tags unit, time unit, cycles, sample, timing
 */
@KlangScript.Function
fun SprudelPattern.unit(value: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyUnit(this, listOf(value).asSprudelDslArgs(callInfo))

/** Sets the time unit for sample playback on a string pattern. */
@KlangScript.Function
fun String.unit(value: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).unit(value, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the time unit for sample playback.
 *
 * @param value Time unit string — `"c"` for cycles, `"s"` for seconds.
 * @return A [PatternMapperFn] that sets the unit field on the source pattern.
 *
 * ```KlangScript(Playable)
 * s("breaks").apply(unit("c")).slow(2)   // via mapper
 * ```
 *
 * @category sampling
 * @tags unit, time unit, cycles, sample, timing
 */
@KlangScript.Function
fun unit(value: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.unit(value, callInfo) }

/** Chains a unit onto this [PatternMapperFn]; sets the time unit for sample playback. */
@KlangScript.Function
fun PatternMapperFn.unit(value: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.unit(value, callInfo) }

// -- cut() ------------------------------------------------------------------------------------------------------------

private val cutMutation = voiceSetter { cut = it?.asIntOrNull() }

private fun applyCut(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.cut?.toDouble() }, update = cutMutation)
    }

    return source._liftOrReinterpretNumericalField(args, cutMutation)
}

/**
 * Assigns the sample to a cut group (choke group) by number.
 *
 * Samples in the same cut group cut each other off when a new sample in the group triggers.
 * This is useful for hi-hats: an open hi-hat stops when the closed hi-hat hits. Group `0`
 * means no choke.
 *
 * @param group Cut group number; 0 = no choke.
 * @return A pattern with the cut group assigned.
 *
 * ```KlangScript(Playable)
 * stack(s("hh*4").cut(1), s("~ oh ~").cut(1))  // open hh choked by closed hh
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").cut("<0 1>")                        // alternate between no-cut and cut-group-1
 * ```
 *
 * @category sampling
 * @tags cut, choke, group, hi-hat, sample
 */
@KlangScript.Function
fun SprudelPattern.cut(group: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyCut(this, listOfNotNull(group).asSprudelDslArgs(callInfo))

/** Assigns the sample to a cut group (choke group) on a string pattern. */
@KlangScript.Function
fun String.cut(group: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).cut(group, callInfo)

/**
 * The cut group of each event, as a value other setters can read.
 *
 * Bare `cut` reads what the chain has set so far, so it comes after whatever set the field
 * (`cut(...)` or an alias). Call it, `cut(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * ```KlangScript(Playable)
 * s("hh*4").cut(1).cut(add("0 1 0 1"))                                    // two cut groups, alternating
 * ```
 *
 * ```KlangScript(Playable)
 * stack(s("hh*4").cut(1), s("oh*2").cut(2)).gain(cut.mul(0.4))            // the cut group sets the level
 * ```
 *
 * @category sampling
 * @tags cut, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("cut")
object cut : FieldAccessor({ it.cut?.toDouble() }) {

    /**
     * Returns a [PatternMapperFn] that assigns the sample to a cut group.
     *
     * @param group Cut group number; 0 = no choke.
     * @return A [PatternMapperFn] that sets the cut field on the source pattern.
     *
     * ```KlangScript(Playable)
     * s("hh*4").apply(cut(1))         // assign to cut group 1 via mapper
     * ```
     */
    @KlangScript.Invoke
    operator fun invoke(group: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.cut(group, callInfo) }
}


/** Chains a cut onto this [PatternMapperFn]; assigns the sample to the given cut group. */
@KlangScript.Function
fun PatternMapperFn.cut(group: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.cut(group, callInfo) }
