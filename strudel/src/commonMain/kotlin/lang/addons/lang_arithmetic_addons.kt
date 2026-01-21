package io.peekandpoke.klang.strudel.lang.addons

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.lang.dslPatternExtension
import io.peekandpoke.klang.strudel.lang.dslStringExtension
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
    return pattern.reinterpret { evt ->
        val current = evt.data.value?.asDouble ?: 0.0
        val negated = -current

        evt.copy(data = evt.data.copy(value = negated.asVoiceValue()))
    }
}

/** Flips the sign of numerical values of the event's voice data. */
@io.peekandpoke.klang.strudel.lang.StrudelDsl
val StrudelPattern.flipSign by dslPatternExtension { pattern, _, _ -> applyFlipSign(pattern) }

/** Flips the sign of numerical values of the event's voice data. */
@io.peekandpoke.klang.strudel.lang.StrudelDsl
val String.flipSign by dslStringExtension { pattern, _, _ -> applyFlipSign(pattern) }

// -- oneMinus ---------------------------------------------------------------------------------------------------------

private fun applyOneMinusValue(pattern: StrudelPattern): StrudelPattern {
    return pattern.reinterpret { evt ->
        val current = evt.data.value?.asDouble ?: 0.0
        val oneMinusCurrent = 1.0 - current

        evt.copy(data = evt.data.copy(value = oneMinusCurrent.asVoiceValue()))
    }
}

/** Calculates 1.0 - value of the event's voice data. */
@io.peekandpoke.klang.strudel.lang.StrudelDsl
val StrudelPattern.oneMinusValue by dslPatternExtension { pattern, _, _ -> applyOneMinusValue(pattern) }

/** Calculates 1.0 - value of the event's voice data. */
@io.peekandpoke.klang.strudel.lang.StrudelDsl
val String.oneMinusValue by dslStringExtension { pattern, _, _ -> applyOneMinusValue(pattern) }

// -- not --------------------------------------------------------------------------------------------------------------

private fun applyNot(pattern: StrudelPattern): StrudelPattern {
    return pattern.reinterpret { evt ->
        val current = evt.data.value?.asBoolean ?: false
        val withNot = !current

        evt.copy(data = evt.data.copy(value = withNot.asVoiceValue()))
    }
}

/** Calculates 1.0 - value of the event's voice data. */
@io.peekandpoke.klang.strudel.lang.StrudelDsl
val StrudelPattern.not by dslPatternExtension { pattern, _, _ -> applyNot(pattern) }

/** Calculates 1.0 - value of the event's voice data. */
@io.peekandpoke.klang.strudel.lang.StrudelDsl
val String.not by dslStringExtension { pattern, _, _ -> applyNot(pattern) }
