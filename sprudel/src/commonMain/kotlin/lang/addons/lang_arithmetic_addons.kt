@file:Suppress("ObjectPropertyName")

package io.peekandpoke.klang.sprudel.lang.addons

import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.lang.*
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpret

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangArithmeticAddonsInit = false

// -- negateValue  -----------------------------------------------------------------------------------------------------

private fun applyFlipSign(pattern: SprudelPattern): SprudelPattern {
    return pattern.mul(-1.0)
}

internal val SprudelPattern._flipSign by dslPatternExtension { pattern, _, _ -> applyFlipSign(pattern) }

internal val String._flipSign by dslStringExtension { pattern, _, _ -> applyFlipSign(pattern) }

internal val _flipSign: PatternMapperFn by dslObject { { p -> p._flipSign() } }
internal val PatternMapperFn._flipSign by dslPatternMapperExtension { m, _, _ -> m.chain { p -> p._flipSign() } }

// ===== USER-FACING OVERLOADS =====

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
fun SprudelPattern.flipSign(): SprudelPattern = this._flipSign(emptyList())

/**
 * Flips the sign of numerical values in a string pattern.
 *
 * ```KlangScript(Playable)
 * "<[1 2 3 4] [-1 -2 -3 -4]>".flipSign().scale("C4:major").n()
 * ```
 */
@SprudelDsl
fun String.flipSign(): SprudelPattern = this._flipSign(emptyList())

/**
 * Flips the sign of numerical values in a string pattern.
 *
 * ```KlangScript(Playable)
 * flipSign("<[1 2 3 4] [-1 -2 -3 -4]>").scale("C4:major").n()
 * ```
 */
@SprudelDsl
val flipSign: PatternMapperFn get() = _flipSign

/**
 * Chains a sign-flip onto this [PatternMapperFn], negating every numeric value in the result.
 *
 * ```KlangScript(Playable)
 * seq("1 -2").apply(add(1).flipSign())  // flipSign(1+1)=-2, flipSign(-2+1)=1
 * ```
 */
@SprudelDsl
fun PatternMapperFn.flipSign(): PatternMapperFn = this._flipSign()

// -- oneMinus ---------------------------------------------------------------------------------------------------------

private fun applyOneMinusValue(pattern: SprudelPattern): SprudelPattern {
    return pattern.reinterpret { evt ->
        val current = evt.data.value?.asDouble ?: 0.0
        val oneMinusCurrent = 1.0 - current

        evt.copy(data = evt.data.copy(value = oneMinusCurrent.asVoiceValue()))
    }
}

internal val SprudelPattern._oneMinusValue by dslPatternExtension { pattern, _, _ -> applyOneMinusValue(pattern) }

internal val String._oneMinusValue by dslStringExtension { pattern, _, _ -> applyOneMinusValue(pattern) }

internal val _oneMinusValue: PatternMapperFn by dslObject { { p -> p._oneMinusValue() } }
internal val PatternMapperFn._oneMinusValue by dslPatternMapperExtension { m, _, _ -> m.chain { p -> p._oneMinusValue() } }

// ===== USER-FACING OVERLOADS =====

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
fun SprudelPattern.oneMinusValue(): SprudelPattern = this._oneMinusValue(emptyList())

/** Calculates `1.0 - value` for a string pattern. */
@SprudelDsl
fun String.oneMinusValue(): SprudelPattern = this._oneMinusValue(emptyList())

/** Calculates `1.0 - value` for a string pattern. */
@SprudelDsl
val oneMinusValue: PatternMapperFn get() = _oneMinusValue

/**
 * Chains a `1 - value` operation onto this [PatternMapperFn], inverting every value within `[0, 1]`.
 *
 * ```KlangScript(Playable)
 * seq("0.2 0.8").apply(mul(2).oneMinusValue())  // 1-(0.2*2)=0.6, 1-(0.8*2)=-0.6
 * ```
 */
@SprudelDsl
fun PatternMapperFn.oneMinusValue(): PatternMapperFn = this._oneMinusValue()

// -- not --------------------------------------------------------------------------------------------------------------

private fun applyNot(pattern: SprudelPattern): SprudelPattern {
    return pattern.reinterpret { evt ->
        val current = evt.data.isTruthy()
        val withNot = !current

        evt.copy(data = evt.data.copy(value = withNot.asVoiceValue()))
    }
}

internal val SprudelPattern._not by dslPatternExtension { pattern, _, _ -> applyNot(pattern) }

internal val String._not by dslStringExtension { pattern, _, _ -> applyNot(pattern) }

internal val _not: PatternMapperFn by dslObject { { p -> p._not() } }
internal val PatternMapperFn._not by dslPatternMapperExtension { m, _, _ -> m.chain { p -> p._not() } }

// ===== USER-FACING OVERLOADS =====

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
fun SprudelPattern.not(): SprudelPattern = this._not(emptyList())

/**
 * Applies logical NOT to a string pattern's boolean values.
 *
 * ```KlangScript(Playable)
 * "1 0 0 1".not().scale("c4:minor").n()   // becomes: false true true false
 * ```
 */
@SprudelDsl
fun String.not(): SprudelPattern = this._not(emptyList())

/**
 * Applies logical NOT as a [PatternMapperFn], inverting each event's boolean value.
 *
 * ```KlangScript(Playable)
 * note("c d e f").degradeBy("1 0 1 0".apply(not))   // invert a degrade pattern into a gate
 * ```
 */
@SprudelDsl
val not: PatternMapperFn get() = _not

/**
 * Chains a logical NOT onto this [PatternMapperFn], inverting every boolean value in the result.
 *
 * ```KlangScript(Playable)
 * seq("1 0").apply(mul(1).not())  // not(1*1)=false, not(0*1)=true
 * ```
 */
@SprudelDsl
fun PatternMapperFn.not(): PatternMapperFn = this._not()

// -- abs --------------------------------------------------------------------------------------------------------------

private fun applyAbs(pattern: SprudelPattern): SprudelPattern {
    return applyUnaryOp(pattern) { v -> v.asRational?.abs()?.asVoiceValue() ?: v }
}

internal val SprudelPattern._abs by dslPatternExtension { pattern, _, _ -> applyAbs(pattern) }
internal val String._abs by dslStringExtension { pattern, _, _ -> applyAbs(pattern) }
internal val _abs: PatternMapperFn by dslObject { { p -> p._abs() } }
internal val PatternMapperFn._abs by dslPatternMapperExtension { m, _, _ -> m.chain { p -> p._abs() } }

// ===== USER-FACING OVERLOADS =====

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
fun SprudelPattern.abs(): SprudelPattern = this._abs(emptyList())

/**
 * Returns the absolute value of each event's numeric data in a string pattern.
 *
 * ```KlangScript(Playable)
 * "-3 -1 0 2".abs()   // becomes: 3 1 0 2
 * ```
 */
@SprudelDsl
fun String.abs(): SprudelPattern = this._abs(emptyList())

/**
 * Applies absolute-value as a [PatternMapperFn], making every numeric value non-negative.
 *
 * ```KlangScript(Playable)
 * seq("-3 -1 0 2").apply(abs)   // becomes: 3 1 0 2
 * ```
 */
@SprudelDsl
val abs: PatternMapperFn get() = _abs

/**
 * Chains an absolute-value operation onto this [PatternMapperFn].
 *
 * ```KlangScript(Playable)
 * seq("1 -2").apply(add(-4).abs())  // abs(1-4)=abs(-3)=3, abs(-2-4)=abs(-6)=6
 * ```
 */
@SprudelDsl
fun PatternMapperFn.abs(): PatternMapperFn = this._abs()
