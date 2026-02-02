package io.peekandpoke.klang.strudel.lang.addons

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.TimeSpan
import io.peekandpoke.klang.strudel._outerJoin
import io.peekandpoke.klang.strudel.lang.*
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * ADDONS: Tempo and timing functions that are NOT available in the original strudel impl
 */

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangTempoAddonsInit = false

// -- stretchBy() ------------------------------------------------------------------------------------------------------

private fun applyStretchBy(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return pattern

    val control = args.toPattern(voiceValueModifier)

    return pattern._outerJoin(control) { sourceEvent, controlEvent ->
        val factor = controlEvent?.data?.value?.asDouble ?: 1.0
        val factorRat = factor.toRational()

        val newPart = sourceEvent.part
            .let { TimeSpan(begin = it.begin, end = it.begin + it.duration * factorRat) }

        val newWhole = sourceEvent.whole
            .let { TimeSpan(begin = it.begin, end = it.begin + it.duration * factorRat) }

        sourceEvent.copy(part = newPart, whole = newWhole)
    }
}

/** Multiplies the duration of each event by the given factor */
@StrudelDsl
val stretchBy by dslFunction { args, /* callInfo */ _ ->
    if (args.size < 2) return@dslFunction silence
    val pattern = args.drop(1).toPattern(voiceValueModifier)
    applyStretchBy(pattern, args.take(1))
}

/** Multiplies the duration of each event by the given factor */
@StrudelDsl
val StrudelPattern.stretchBy by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStretchBy(p, args)
}

/** Multiplies the duration of each event by the given factor */
@StrudelDsl
val String.stretchBy by dslStringExtension { p, args, callInfo -> p.stretchBy(args, callInfo) }
