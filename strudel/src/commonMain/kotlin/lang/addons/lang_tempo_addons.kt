package io.peekandpoke.klang.strudel.lang.addons

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.TimeSpan
import io.peekandpoke.klang.strudel._outerJoin
import io.peekandpoke.klang.strudel._splitQueries
import io.peekandpoke.klang.strudel.lang.*
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * ADDONS: Tempo and timing functions that are NOT available in the original strudel impl
 */

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangTempoAddonsInit = false

// -- helpers ----------------------------------------------------------------------------------------------------------

fun applyTimeMoveInCycle(
    pattern: StrudelPattern,
    args: List<StrudelDslArg<Any?>>,
    factor: Rational,
): StrudelPattern {
    if (args.isEmpty()) return pattern
    val control = args.toPattern()

    // 1. Split queries to ensure we process one cycle at a time
    // 2. Use _outerJoin to iterate source events and sample control at event time
    return pattern._splitQueries()._outerJoin(control) { srcEv, ctrlEv ->
        val shiftVal = (ctrlEv?.data?.value?.asDouble ?: 0.0)
        val shift = shiftVal.toRational() * factor

        if (shift == Rational.ZERO) return@_outerJoin srcEv

        val shiftedPart = srcEv.part.shift(shift)
        val shiftedWhole = srcEv.whole.shift(shift)

        // Clip to the cycle of the *original* event
        val cycleStart = srcEv.whole.begin.floor()
        val cycleEnd = cycleStart + Rational.ONE

        // If the event moves out of its cycle, it is dropped/clipped
        val clippedPart = shiftedPart.clipTo(cycleStart, cycleEnd) ?: return@_outerJoin null

        srcEv.copy(part = clippedPart, whole = shiftedWhole)
    }
}

// -- lateInCycle() ----------------------------------------------------------------------------------------------------

/**
 * Nudges the pattern to start later in time, but only shifts events that are already within the time window.
 * Does not pull events from future/past cycles. Used for swing and inner-cycle timing adjustments.
 */
@StrudelDsl
val StrudelPattern.lateInCycle by dslPatternExtension { p, args, _ ->
    applyTimeMoveInCycle(pattern = p, args = args, factor = Rational.ONE)
}

@StrudelDsl
val String.lateInCycle by dslStringExtension { p, args, callInfo -> p.lateInCycle(args, callInfo) }

/** Nudges the pattern to start later in time (intra-cycle) */
@StrudelDsl
val lateInCycle by dslFunction { /* args */ _, /* callInfo */ _ -> silence }

// -- earlyInCycle() ---------------------------------------------------------------------------------------------------

/**
 * Nudges the pattern to start earlier in time, but only shifts events that are already within the time window.
 * Does not pull events from future/past cycles.
 */
@StrudelDsl
val StrudelPattern.earlyInCycle by dslPatternExtension { p, args, _ ->
    applyTimeMoveInCycle(pattern = p, args = args, factor = Rational.MINUS_ONE)
}

@StrudelDsl
val String.earlyInCycle by dslStringExtension { p, args, callInfo -> p.earlyInCycle(args, callInfo) }

/** Nudges the pattern to start earlier in time (intra-cycle) */
@StrudelDsl
val earlyInCycle by dslFunction { /* args */ _, /* callInfo */ _ -> silence }

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
