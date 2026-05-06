@file:Suppress("ObjectPropertyName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang.addons

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceValue
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel._innerJoin
import io.peekandpoke.klang.sprudel.lang.PatternLike
import io.peekandpoke.klang.sprudel.lang.PatternMapperFn
import io.peekandpoke.klang.sprudel.lang.SprudelDsl
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.applyArithmetic
import io.peekandpoke.klang.sprudel.lang.applyUnaryOp
import io.peekandpoke.klang.sprudel.lang.chain
import io.peekandpoke.klang.sprudel.lang.mul
import io.peekandpoke.klang.sprudel.lang.silence
import io.peekandpoke.klang.sprudel.lang.toVoiceValuePattern
import io.peekandpoke.klang.sprudel.mapEvents
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpret

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
 * @tags flipSign, negate, invert, arithmetic, value, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.flipSign(callInfo: CallInfo? = null): SprudelPattern = applyFlipSign(this)

/**
 * Flips the sign of numerical values in a string pattern.
 *
 * ```KlangScript(Playable)
 * "<[1 2 3 4] [-1 -2 -3 -4]>".flipSign().scale("C4:major").n()
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.flipSign(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).flipSign(callInfo)

/**
 * Flips the sign of numerical values as a [PatternMapperFn].
 *
 * ```KlangScript(Playable)
 * flipSign("<[1 2 3 4] [-1 -2 -3 -4]>").scale("C4:major").n()
 * ```
 */
@SprudelDsl
@KlangScript.Property
val flipSign: PatternMapperFn = { p -> p.flipSign() }

/**
 * Chains a sign-flip onto this [PatternMapperFn], negating every numeric value in the result.
 *
 * ```KlangScript(Playable)
 * seq("1 -2").apply(add(1).flipSign())  // flipSign(1+1)=-2, flipSign(-2+1)=1
 * ```
 */
@SprudelDsl
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
 * @tags oneMinusValue, invert, complement, arithmetic, value, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.oneMinusValue(callInfo: CallInfo? = null): SprudelPattern = applyOneMinusValue(this)

/** Calculates `1.0 - value` for a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.oneMinusValue(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).oneMinusValue(callInfo)

/** Calculates `1.0 - value` as a [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Property
val oneMinusValue: PatternMapperFn = { p -> p.oneMinusValue() }

/**
 * Chains a `1 - value` operation onto this [PatternMapperFn], inverting every value within `[0, 1]`.
 *
 * ```KlangScript(Playable)
 * seq("0.2 0.8").apply(mul(2).oneMinusValue())  // 1-(0.2*2)=0.6, 1-(0.8*2)=-0.6
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.oneMinusValue(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.oneMinusValue(callInfo) }

// -- not --------------------------------------------------------------------------------------------------------------

private fun applyNot(pattern: SprudelPattern): SprudelPattern {
    return pattern.reinterpret { evt ->
        val current = evt.data.isTruthy()
        val withNot = !current

        evt.copy(data = evt.data.copy(value = withNot.asVoiceValue()))
    }
}

/**
 * Applies logical NOT to each event's boolean value.
 *
 * Truthy values become `false`; falsy values become `true`. Useful for inverting
 * gate or trigger patterns.
 *
 * ```KlangScript(Playable)
 * "1 0 0 1".not().scale("c4:minor").n()   // becomes: false true true false
 * ```
 *
 * @category arithmetic
 * @tags not, logical, boolean, gate, invert, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.not(callInfo: CallInfo? = null): SprudelPattern = applyNot(this)

/**
 * Applies logical NOT to a string pattern's boolean values.
 *
 * ```KlangScript(Playable)
 * "1 0 0 1".not().scale("c4:minor").n()   // becomes: false true true false
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.not(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).not(callInfo)

/**
 * Applies logical NOT as a [PatternMapperFn], inverting each event's boolean value.
 *
 * ```KlangScript(Playable)
 * note("c d e f").degradeBy("1 0 1 0".apply(not))   // invert a degrade pattern into a gate
 * ```
 */
@SprudelDsl
@KlangScript.Property
val not: PatternMapperFn = { p -> p.not() }

/**
 * Chains a logical NOT onto this [PatternMapperFn], inverting every boolean value in the result.
 *
 * ```KlangScript(Playable)
 * seq("1 0").apply(mul(1).not())  // not(1*1)=false, not(0*1)=true
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.not(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.not(callInfo) }

// -- abs --------------------------------------------------------------------------------------------------------------

private fun applyAbs(pattern: SprudelPattern): SprudelPattern {
    return applyUnaryOp(pattern) { v -> v.asRational?.abs()?.asVoiceValue() ?: v }
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
 * @tags abs, absolute, value, arithmetic, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.abs(callInfo: CallInfo? = null): SprudelPattern = applyAbs(this)

/**
 * Returns the absolute value of each event's numeric data in a string pattern.
 *
 * ```KlangScript(Playable)
 * "-3 -1 0 2".abs()   // becomes: 3 1 0 2
 * ```
 */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Property
val abs: PatternMapperFn = { p -> p.abs() }

/**
 * Chains an absolute-value operation onto this [PatternMapperFn].
 *
 * ```KlangScript(Playable)
 * seq("1 -2").apply(add(-4).abs())  // abs(1-4)=abs(-3)=3, abs(-2-4)=abs(-6)=6
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.abs(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.abs(callInfo) }

// -- min --------------------------------------------------------------------------------------------------------------

private fun applyMinValue(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    applyArithmetic(pattern, args) { a, b ->
        val ar = a.asRational ?: return@applyArithmetic null
        val br = b.asRational ?: return@applyArithmetic null
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
 * @tags min, minimum, floor, clamp, arithmetic, value, addon
 */
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.min(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.min(other, callInfo) }

// -- max --------------------------------------------------------------------------------------------------------------

private fun applyMaxValue(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    applyArithmetic(pattern, args) { a, b ->
        val ar = a.asRational ?: return@applyArithmetic null
        val br = b.asRational ?: return@applyArithmetic null
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
 * @tags max, maximum, cap, ceiling, clamp, arithmetic, value, addon
 */
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.max(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.max(other, callInfo) }

// -- clamp ------------------------------------------------------------------------------------------------------------

private fun applyClampValue(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return pattern._innerJoin(args) { src, vMin: SprudelVoiceValue?, vMax: SprudelVoiceValue? ->
        val rMin = vMin?.asRational ?: return@_innerJoin silence
        val rMax = vMax?.asRational ?: return@_innerJoin silence

        src.mapEvents { event ->
            val sourceVal = event.data.value ?: return@mapEvents event
            val sr = sourceVal.asRational ?: return@mapEvents event

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
 * @tags clamp, clip, limit, bounds, range, min, max, arithmetic, value, addon
 */
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.clamp(min: PatternLike, max: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.clamp(min, max, callInfo) }
