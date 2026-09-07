/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceValue
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel._innerJoin
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.mapEvents
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpret
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

// -- round() ----------------------------------------------------------------------------------------------------------

/**
 * Rounds every numeric value in the pattern to the nearest integer.
 *
 * @return A new pattern with each value rounded to the nearest integer.
 * @category arithmetic
 * @tags round, rounding, arithmetic, math
 */
@KlangScript.Function
fun SprudelPattern.round(@Suppress("unused") callInfo: CallInfo? = null): SprudelPattern =
// Half-up rounding (floor(x+0.5)) to match the previous behavior; kotlin.math.round is
    // half-to-even (banker's), which would round 2.5 -> 2.
    applyUnaryOp(this) { v -> v.asDouble?.let { floor(it + 0.5) }?.asVoiceValue() ?: v }

@KlangScript.Function
fun String.round(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).round(callInfo)

@KlangScript.Function
fun round(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.round(callInfo) }

@KlangScript.Function
fun PatternMapperFn.round(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.round(callInfo) }

// -- floor() ----------------------------------------------------------------------------------------------------------

/**
 * Floors every numeric value in the pattern to the largest integer less than or equal to the value.
 *
 * @return A new pattern with each value floored to an integer.
 * @category arithmetic
 * @tags floor, rounding, arithmetic, math
 */
@KlangScript.Function
fun SprudelPattern.floor(@Suppress("unused") callInfo: CallInfo? = null): SprudelPattern =
    applyUnaryOp(this) { v -> v.asDouble?.let { floor(it) }?.asVoiceValue() ?: v }

@KlangScript.Function
fun String.floor(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).floor(callInfo)

@KlangScript.Function
fun floor(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.floor(callInfo) }

@KlangScript.Function
fun PatternMapperFn.floor(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.floor(callInfo) }

// -- ceil() -----------------------------------------------------------------------------------------------------------

/**
 * Ceils every numeric value in the pattern to the smallest integer greater than or equal to the value.
 *
 * @return A new pattern with each value ceiled to an integer.
 * @category arithmetic
 * @tags ceil, ceiling, rounding, arithmetic, math
 */
@KlangScript.Function
fun SprudelPattern.ceil(@Suppress("unused") callInfo: CallInfo? = null): SprudelPattern =
    applyUnaryOp(this) { v -> v.asDouble?.let { ceil(it) }?.asVoiceValue() ?: v }

@KlangScript.Function
fun String.ceil(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).ceil(callInfo)

@KlangScript.Function
fun ceil(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.ceil(callInfo) }

@KlangScript.Function
fun PatternMapperFn.ceil(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.ceil(callInfo) }

// -- negateValue  -----------------------------------------------------------------------------------------------------

private fun applyFlipSign(pattern: SprudelPattern): SprudelPattern {
    return pattern.mul(-1.0)
}

/**
 * Flips the sign of numerical values in each event's voice data.
 *
 * Multiplies the current value by `-1`, turning positive values negative and vice versa.
 * Useful for inverting modulation signals or creating mirror effects.
 *
 * ```KlangScript(Playable)
 * seq("<[1 2 3 4] [-1 -2 -3 -4]>").flipSign().scale("c4:major").n()
 * ```
 *
 * ```KlangScript(Playable)
 * sine.range(0, 1).flipSign()   // invert a unipolar sine to [-1, 0]
 * ```
 *
 * @category arithmetic
 * @tags flipSign, negate, invert, arithmetic, value
 */
@KlangScript.Function
@Suppress("UNUSED_PARAMETER") // callInfo is part of the uniform DSL signature; this unary op has no arg to locate
fun SprudelPattern.flipSign(callInfo: CallInfo? = null): SprudelPattern = applyFlipSign(this)

/**
 * Flips the sign of numerical values in a string pattern.
 *
 * ```KlangScript(Playable)
 * "<[1 2 3 4] [-1 -2 -3 -4]>".flipSign().scale("C4:major").n()
 * ```
 */
@KlangScript.Function
fun String.flipSign(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).flipSign(callInfo)

/**
 * Flips the sign of numerical values as a [PatternMapperFn].
 *
 * ```KlangScript(Playable)
 * "<[1 2 3 4] [-1 -2 -3 -4]>".apply(flipSign).scale("C4:major").n()
 * ```
 */
@KlangScript.Constant
val flipSign: PatternMapperFn = { p -> p.flipSign() }

/**
 * Chains a sign-flip onto this [PatternMapperFn], negating every numeric value in the result.
 *
 * ```KlangScript(Playable)
 * seq("1 -2").apply(add(1).flipSign())  // flipSign(1+1)=-2, flipSign(-2+1)=1
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.flipSign(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.flipSign(callInfo) }

// -- oneMinus ---------------------------------------------------------------------------------------------------------

private fun applyOneMinusValue(pattern: SprudelPattern): SprudelPattern {
    return pattern.reinterpret { evt ->
        val current = evt.data.value?.asDouble ?: 0.0
        val oneMinusCurrent = 1.0 - current

        evt.copy(data = evt.data.copy(value = oneMinusCurrent.asVoiceValue()))
    }
}

/**
 * Calculates `1.0 - value` for each event's voice data.
 *
 * Inverts a value within the `[0, 1]` range. Useful for reversing the direction of
 * modulation or envelope signals without leaving the unipolar range.
 *
 * ```KlangScript(Playable)
 * rand.oneMinusValue()               // invert random values: high becomes low
 * ```
 *
 * ```KlangScript(Playable)
 * sine.range(0, 1).oneMinusValue()   // flip a rising sine to a falling sine
 * ```
 *
 * @category arithmetic
 * @tags oneMinusValue, invert, complement, arithmetic, value
 */
@KlangScript.Function
@Suppress("UNUSED_PARAMETER") // callInfo is part of the uniform DSL signature; this unary op has no arg to locate
fun SprudelPattern.oneMinusValue(callInfo: CallInfo? = null): SprudelPattern = applyOneMinusValue(this)

/** Calculates `1.0 - value` for a string pattern. */
@KlangScript.Function
fun String.oneMinusValue(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).oneMinusValue(callInfo)

/** Calculates `1.0 - value` as a [PatternMapperFn]. */
@KlangScript.Constant
val oneMinusValue: PatternMapperFn = { p -> p.oneMinusValue() }

/**
 * Chains a `1 - value` operation onto this [PatternMapperFn], inverting every value within `[0, 1]`.
 *
 * ```KlangScript(Playable)
 * seq("0.2 0.8").apply(mul(2).oneMinusValue())  // 1-(0.2*2)=0.6, 1-(0.8*2)=-0.6
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.oneMinusValue(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.oneMinusValue(callInfo) }

// -- abs --------------------------------------------------------------------------------------------------------------

private fun applyAbs(pattern: SprudelPattern): SprudelPattern {
    return applyUnaryOp(pattern) { v -> v.asDouble?.let { abs(it) }?.asVoiceValue() ?: v }
}

/**
 * Returns the absolute value of each event's numeric data.
 *
 * Negative values become positive; positive values and zero are unchanged.
 * Useful for ensuring non-negative modulation signals or working with bipolar sources.
 *
 * ```KlangScript(Playable)
 * seq("-3 -1 0 2").abs()   // becomes: 3 1 0 2
 * ```
 *
 * ```KlangScript(Playable)
 * sine.range(-1, 1).abs()  // fold negative half of sine to positive
 * ```
 *
 * @category arithmetic
 * @tags abs, absolute, value, arithmetic
 */
@KlangScript.Function
@Suppress("UNUSED_PARAMETER") // callInfo is part of the uniform DSL signature; this unary op has no arg to locate
fun SprudelPattern.abs(callInfo: CallInfo? = null): SprudelPattern = applyAbs(this)

/**
 * Returns the absolute value of each event's numeric data in a string pattern.
 *
 * ```KlangScript(Playable)
 * "-3 -1 0 2".abs()   // becomes: 3 1 0 2
 * ```
 */
@KlangScript.Function
fun String.abs(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).abs(callInfo)

/**
 * Applies absolute-value as a [PatternMapperFn], making every numeric value non-negative.
 *
 * ```KlangScript(Playable)
 * seq("-3 -1 0 2").apply(abs)   // becomes: 3 1 0 2
 * ```
 */
@KlangScript.Constant
val abs: PatternMapperFn = { p -> p.abs() }

/**
 * Chains an absolute-value operation onto this [PatternMapperFn].
 *
 * ```KlangScript(Playable)
 * seq("1 -2").apply(add(-4).abs())  // abs(1-4)=abs(-3)=3, abs(-2-4)=abs(-6)=6
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.abs(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.abs(callInfo) }

// -- min --------------------------------------------------------------------------------------------------------------

private fun applyMinValue(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    applyArithmetic(pattern, args) { a, b ->
        val ar = a.asDouble ?: return@applyArithmetic null
        val br = b.asDouble ?: return@applyArithmetic null
        if (ar >= br) a else b
    }

/**
 * Enforces a minimum allowed value of [other] on each event.
 *
 * Anything below [other] is raised to [other]; values at or above [other] pass through
 * unchanged. Supports control patterns: pass a mini-notation string or another
 * [SprudelPattern] as [other] to vary the floor per cycle or event.
 *
 * ```KlangScript(Playable)
 * seq("0 1 2 3 4").min(2).scale("c4:major").n()   // becomes: 2 2 2 3 4
 * ```
 *
 * ```KlangScript(Playable)
 * sine.range(-1, 1).min(0)   // half-wave rectify a sine
 * ```
 *
 * @param other The minimum allowed value (floor). May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern with each numeric value floored at [other].
 * @category arithmetic
 * @tags min, minimum, floor, clamp, arithmetic, value
 */
@KlangScript.Function
fun SprudelPattern.min(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyMinValue(this, listOfNotNull(other).asSprudelDslArgs(callInfo))

/**
 * Enforces a minimum allowed value of [other] on each numeric value of a string pattern.
 *
 * ```KlangScript(Playable)
 * "0 1 2 3 4".min(2).scale("c4:major").n()   // becomes: 2 2 2 3 4
 * ```
 */
@KlangScript.Function
fun String.min(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).min(other, callInfo)

/**
 * Creates a [PatternMapperFn] that enforces a minimum allowed value of [other].
 *
 * ```KlangScript(Playable)
 * seq("0 1 2 3 4").apply(min(2)).scale("c4:major").n()   // becomes: 2 2 2 3 4
 * ```
 */
@KlangScript.Function
fun min(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.min(other, callInfo) }

/**
 * Chains a `min` floor onto this [PatternMapperFn], enforcing [other] as the minimum value.
 *
 * ```KlangScript(Playable)
 * seq("1 2 3").apply(sub(2).min(0))  // min(1-2,0)=0, min(2-2,0)=0, min(3-2,0)=1
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.min(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.min(other, callInfo) }

// -- max --------------------------------------------------------------------------------------------------------------

private fun applyMaxValue(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    applyArithmetic(pattern, args) { a, b ->
        val ar = a.asDouble ?: return@applyArithmetic null
        val br = b.asDouble ?: return@applyArithmetic null
        if (ar <= br) a else b
    }

/**
 * Enforces a maximum allowed value of [other] on each event.
 *
 * Anything above [other] is lowered to [other]; values at or below [other] pass through
 * unchanged. Supports control patterns: pass a mini-notation string or another
 * [SprudelPattern] as [other] to vary the cap per cycle or event.
 *
 * ```KlangScript(Playable)
 * seq("0 1 2 3 4").max(2).scale("c4:major").n()   // becomes: 0 1 2 2 2
 * ```
 *
 * ```KlangScript(Playable)
 * sine.range(0, 2).max("<1 0.5>")   // alternate the cap each cycle
 * ```
 *
 * @param other The maximum allowed value (cap). May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern with each numeric value capped at [other].
 * @category arithmetic
 * @tags max, maximum, cap, ceiling, clamp, arithmetic, value
 */
@KlangScript.Function
fun SprudelPattern.max(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyMaxValue(this, listOfNotNull(other).asSprudelDslArgs(callInfo))

/**
 * Enforces a maximum allowed value of [other] on each numeric value of a string pattern.
 *
 * ```KlangScript(Playable)
 * "0 1 2 3 4".max(2).scale("c4:major").n()   // becomes: 0 1 2 2 2
 * ```
 */
@KlangScript.Function
fun String.max(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).max(other, callInfo)

/**
 * Creates a [PatternMapperFn] that enforces a maximum allowed value of [other].
 *
 * ```KlangScript(Playable)
 * seq("0 1 2 3 4").apply(max(2)).scale("c4:major").n()   // becomes: 0 1 2 2 2
 * ```
 */
@KlangScript.Function
fun max(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.max(other, callInfo) }

/**
 * Chains a `max` cap onto this [PatternMapperFn], enforcing [other] as the maximum value.
 *
 * ```KlangScript(Playable)
 * seq("1 2 3").apply(mul(2).max(4))  // max(1*2,4)=2, max(2*2,4)=4, max(3*2,4)=4
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.max(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.max(other, callInfo) }

// -- clamp ------------------------------------------------------------------------------------------------------------

private fun applyClampValue(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return pattern._innerJoin(args) { src, vMin: SprudelVoiceValue?, vMax: SprudelVoiceValue? ->
        val rMin = vMin?.asDouble ?: return@_innerJoin silence
        val rMax = vMax?.asDouble ?: return@_innerJoin silence

        src.mapEvents { event ->
            val sourceVal = event.data.value ?: return@mapEvents event
            val sr = sourceVal.asDouble ?: return@mapEvents event

            val clamped = when {
                sr < rMin -> vMin
                sr > rMax -> vMax
                else -> sourceVal
            }
            event.copy(data = event.data.copy(value = clamped))
        }
    }
}

/**
 * Clamps each event's numeric value into the inclusive range `[min, max]`.
 *
 * Values below [min] become [min]; values above [max] become [max]. Useful for confining
 * modulation to a safe range. Supports control patterns: both [min] and [max] may be
 * mini-notation strings or other [SprudelPattern]s to vary the bounds over time.
 *
 * Behaviour with `min > max` is undefined — the engine does not swap or validate bounds.
 *
 * ```KlangScript(Playable)
 * seq("-1 0 0.5 1 2").clamp(0, 1)   // becomes: 0 0 0.5 1 1
 * ```
 *
 * ```KlangScript(Playable)
 * sine.range(-1, 2).clamp("<0 -0.5>", 1)   // alternate lower bound each cycle
 * ```
 *
 * @param min The lower bound. May be a number, string mini-notation, or a [SprudelPattern].
 * @param max The upper bound. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern with each numeric value clamped to `[min, max]`.
 * @category arithmetic
 * @tags clamp, clip, limit, bounds, range, min, max, arithmetic, value
 */
@KlangScript.Function
fun SprudelPattern.clamp(min: PatternLike, max: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyClampValue(this, listOfNotNull(min, max).asSprudelDslArgs(callInfo))

/**
 * Clamps each numeric value of a string pattern into `[min, max]`.
 *
 * ```KlangScript(Playable)
 * "-1 0 0.5 1 2".clamp(0, 1)   // becomes: 0 0 0.5 1 1
 * ```
 */
@KlangScript.Function
fun String.clamp(min: PatternLike, max: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).clamp(min, max, callInfo)

/**
 * Creates a [PatternMapperFn] that clamps each numeric value into `[min, max]`.
 *
 * ```KlangScript(Playable)
 * seq("-1 0 0.5 1 2").apply(clamp(0, 1))   // becomes: 0 0 0.5 1 1
 * ```
 */
@KlangScript.Function
fun clamp(min: PatternLike, max: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.clamp(min, max, callInfo) }

/**
 * Chains a `clamp` onto this [PatternMapperFn], clamping every numeric value to `[min, max]`.
 *
 * ```KlangScript(Playable)
 * seq("0 1 2 3").apply(mul(2).clamp(1, 4))  // clamp(0*2,1,4)=1, clamp(1*2,1,4)=2, ...
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.clamp(min: PatternLike, max: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.clamp(min, max, callInfo) }
