@file:Suppress("ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang.addons

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.TimeSpan
import io.peekandpoke.klang.strudel._outerJoin
import io.peekandpoke.klang.strudel._splitQueries
import io.peekandpoke.klang.strudel.lang.*
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
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

internal val StrudelPattern._lateInCycle by dslPatternExtension { p, args, _ ->
    applyTimeMoveInCycle(pattern = p, args = args, factor = Rational.ONE)
}

internal val String._lateInCycle by dslStringExtension { p, args, callInfo -> p._lateInCycle(args, callInfo) }

internal val _lateInCycle by dslFunction { /* args */ _, /* callInfo */ _ -> silence }

// ===== USER-FACING OVERLOADS =====

/**
 * Nudges events later within their cycle by the given fraction of a cycle.
 *
 * Only shifts events already within the queried time window — does not pull events
 * from adjacent cycles. Useful for swing and intra-cycle timing adjustments.
 *
 * ```KlangScript
 * s("bd sd hh cp").lateInCycle(0.02)          // subtle late nudge
 * ```
 *
 * ```KlangScript
 * s("bd sd").lateInCycle("<0 0.05 0.1>")       // cycle through nudge amounts
 * ```
 *
 * @category tempo
 * @tags lateInCycle, timing, swing, nudge, offset, addon
 */
@StrudelDsl
fun StrudelPattern.lateInCycle(amount: PatternLike): StrudelPattern =
    this._lateInCycle(listOf(amount).asStrudelDslArgs())

/** Nudges events later within their cycle in a string pattern. */
@StrudelDsl
fun String.lateInCycle(amount: PatternLike): StrudelPattern = this._lateInCycle(listOf(amount).asStrudelDslArgs())

// -- earlyInCycle() ---------------------------------------------------------------------------------------------------

internal val StrudelPattern._earlyInCycle by dslPatternExtension { p, args, _ ->
    applyTimeMoveInCycle(pattern = p, args = args, factor = Rational.MINUS_ONE)
}

internal val String._earlyInCycle by dslStringExtension { p, args, callInfo -> p._earlyInCycle(args, callInfo) }

internal val _earlyInCycle by dslFunction { /* args */ _, /* callInfo */ _ -> silence }

// ===== USER-FACING OVERLOADS =====

/**
 * Nudges events earlier within their cycle by the given fraction of a cycle.
 *
 * Only shifts events already within the queried time window — does not pull events
 * from adjacent cycles. Complementary to [lateInCycle].
 *
 * ```KlangScript
 * s("bd sd hh cp").earlyInCycle(0.02)         // subtle early nudge
 * ```
 *
 * ```KlangScript
 * s("bd sd").earlyInCycle("<0 0.05 0.1>")      // cycle through nudge amounts
 * ```
 *
 * @category tempo
 * @tags earlyInCycle, timing, nudge, offset, addon
 */
@StrudelDsl
fun StrudelPattern.earlyInCycle(amount: PatternLike): StrudelPattern =
    this._earlyInCycle(listOf(amount).asStrudelDslArgs())

/** Nudges events earlier within their cycle in a string pattern. */
@StrudelDsl
fun String.earlyInCycle(amount: PatternLike): StrudelPattern =
    this._earlyInCycle(listOf(amount).asStrudelDslArgs())

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

internal val _stretchBy by dslFunction { args, /* callInfo */ _ ->
    if (args.size < 2) return@dslFunction silence
    val pattern = args.drop(1).toPattern(voiceValueModifier)
    applyStretchBy(pattern, args.take(1))
}

internal val StrudelPattern._stretchBy by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyStretchBy(p, args)
}

internal val String._stretchBy by dslStringExtension { p, args, callInfo -> p._stretchBy(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Multiplies the duration of each event by the given factor, without affecting its onset time.
 *
 * A factor of `2.0` doubles each event's duration; `0.5` halves it. Events can overlap
 * (factor > 1) or leave gaps (factor < 1).
 *
 * ```KlangScript
 * note("c3 e3 g3").stretchBy(2)       // each note lasts twice as long
 * ```
 *
 * ```KlangScript
 * s("bd sd").stretchBy("<1 2 0.5>")   // cycle through duration multipliers
 * ```
 *
 * @category tempo
 * @tags stretchBy, duration, stretch, event length, addon
 */
@StrudelDsl
fun stretchBy(factor: PatternLike, pattern: PatternLike): StrudelPattern =
    _stretchBy(listOf(factor, pattern).asStrudelDslArgs())

/** Multiplies the duration of each event in this pattern by the given factor. */
@StrudelDsl
fun StrudelPattern.stretchBy(factor: PatternLike): StrudelPattern =
    this._stretchBy(listOf(factor).asStrudelDslArgs())

/** Multiplies the duration of each event in a string pattern by the given factor. */
@StrudelDsl
fun String.stretchBy(factor: PatternLike): StrudelPattern = this._stretchBy(listOf(factor).asStrudelDslArgs())