package io.peekandpoke.klang.strudel.lang.addons

import io.peekandpoke.klang.audio_bridge.VoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.StrudelPattern
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

private fun applyNegateValue(pattern: StrudelPattern): StrudelPattern {
    return pattern.reinterpret { evt ->
        val current = evt.data.value?.asDouble ?: 0.0
        val negated = -current

        evt.copy(data = evt.data.copy(value = negated.asVoiceValue()))
    }
}

/** Negates the value of the event's voice data. */
@io.peekandpoke.klang.strudel.lang.StrudelDsl
val StrudelPattern.negateValue by _root_ide_package_.io.peekandpoke.klang.strudel.lang.dslPatternExtension { pattern, _ ->
    applyNegateValue(
        pattern
    )
}

/** Negates the value of the event's voice data. */
@io.peekandpoke.klang.strudel.lang.StrudelDsl
val String.negateValue by _root_ide_package_.io.peekandpoke.klang.strudel.lang.dslStringExtension { pattern, _ ->
    applyNegateValue(
        pattern
    )
}

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
val StrudelPattern.oneMinusValue by _root_ide_package_.io.peekandpoke.klang.strudel.lang.dslPatternExtension { pattern, _ ->
    applyOneMinusValue(
        pattern
    )
}

/** Calculates 1.0 - value of the event's voice data. */
@io.peekandpoke.klang.strudel.lang.StrudelDsl
val String.oneMinusValue by _root_ide_package_.io.peekandpoke.klang.strudel.lang.dslStringExtension { pattern, _ ->
    applyOneMinusValue(
        pattern
    )
}
