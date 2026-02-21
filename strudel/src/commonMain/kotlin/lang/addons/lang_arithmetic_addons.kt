@file:Suppress("ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang.addons

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.lang.StrudelDsl
import io.peekandpoke.klang.strudel.lang.dslPatternExtension
import io.peekandpoke.klang.strudel.lang.dslStringExtension
import io.peekandpoke.klang.strudel.lang.mul
import io.peekandpoke.klang.strudel.pattern.ReinterpretPattern.Companion.reinterpret

/**
 * ADDONS: function that are NOT available in the original strudel impl
 */

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangArithmeticAddonsInit = false

// -- negateValue  -----------------------------------------------------------------------------------------------------

private fun applyFlipSign(pattern: StrudelPattern): StrudelPattern {
    return pattern.mul(-1.0)
}

internal val StrudelPattern._flipSign by dslPatternExtension { pattern, _, _ -> applyFlipSign(pattern) }

internal val String._flipSign by dslStringExtension { pattern, _, _ -> applyFlipSign(pattern) }

// ===== USER-FACING OVERLOADS =====

/**
 * Flips the sign of numerical values in each event's voice data.
 *
 * Multiplies the current value by `-1`, turning positive values negative and vice versa.
 * Useful for inverting modulation signals or creating mirror effects.
 *
 * ```KlangScript
 * rand.flipSign()                    // random values in range [-1, 0]
 * ```
 *
 * ```KlangScript
 * sine.range(0, 1).flipSign()        // invert a unipolar sine to [-1, 0]
 * ```
 *
 * @category arithmetic
 * @tags flipSign, negate, invert, arithmetic, value, addon
 */
@StrudelDsl
fun StrudelPattern.flipSign(): StrudelPattern = this._flipSign(emptyList())

/** Flips the sign of numerical values in a string pattern. */
@StrudelDsl
fun String.flipSign(): StrudelPattern = this._flipSign(emptyList())

// -- oneMinus ---------------------------------------------------------------------------------------------------------

private fun applyOneMinusValue(pattern: StrudelPattern): StrudelPattern {
    return pattern.reinterpret { evt ->
        val current = evt.data.value?.asDouble ?: 0.0
        val oneMinusCurrent = 1.0 - current

        evt.copy(data = evt.data.copy(value = oneMinusCurrent.asVoiceValue()))
    }
}

internal val StrudelPattern._oneMinusValue by dslPatternExtension { pattern, _, _ -> applyOneMinusValue(pattern) }

internal val String._oneMinusValue by dslStringExtension { pattern, _, _ -> applyOneMinusValue(pattern) }

// ===== USER-FACING OVERLOADS =====

/**
 * Calculates `1.0 - value` for each event's voice data.
 *
 * Inverts a value within the `[0, 1]` range. Useful for reversing the direction of
 * modulation or envelope signals without leaving the unipolar range.
 *
 * ```KlangScript
 * rand.oneMinusValue()               // invert random values: high becomes low
 * ```
 *
 * ```KlangScript
 * sine.range(0, 1).oneMinusValue()   // flip a rising sine to a falling sine
 * ```
 *
 * @category arithmetic
 * @tags oneMinusValue, invert, complement, arithmetic, value, addon
 */
@StrudelDsl
fun StrudelPattern.oneMinusValue(): StrudelPattern = this._oneMinusValue(emptyList())

/** Calculates `1.0 - value` for a string pattern. */
@StrudelDsl
fun String.oneMinusValue(): StrudelPattern = this._oneMinusValue(emptyList())

// -- not --------------------------------------------------------------------------------------------------------------

private fun applyNot(pattern: StrudelPattern): StrudelPattern {
    return pattern.reinterpret { evt ->
        val current = evt.data.isTruthy()
        val withNot = !current

        evt.copy(data = evt.data.copy(value = withNot.asVoiceValue()))
    }
}

internal val StrudelPattern._not by dslPatternExtension { pattern, _, _ -> applyNot(pattern) }

internal val String._not by dslStringExtension { pattern, _, _ -> applyNot(pattern) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies logical NOT to each event's boolean value.
 *
 * Truthy values become `false`; falsy values become `true`. Useful for inverting
 * gate or trigger patterns.
 *
 * ```KlangScript
 * "1 0 0 1".not()   // becomes: false true true false
 * ```
 *
 * ```KlangScript
 * degradeBy(0.5).not()   // invert a degrade pattern into a gate
 * ```
 *
 * @category arithmetic
 * @tags not, logical, boolean, gate, invert, addon
 */
@StrudelDsl
fun StrudelPattern.not(): StrudelPattern = this._not(emptyList())

/** Applies logical NOT to a string pattern's boolean values. */
@StrudelDsl
fun String.not(): StrudelPattern = this._not(emptyList())