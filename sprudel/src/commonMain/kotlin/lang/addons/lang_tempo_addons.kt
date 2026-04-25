@file:Suppress("ObjectPropertyName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang.addons

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.common.math.Rational.Companion.toRational
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.TimeSpan
import io.peekandpoke.klang.sprudel._outerJoin
import io.peekandpoke.klang.sprudel._splitQueries
import io.peekandpoke.klang.sprudel.lang.PatternLike
import io.peekandpoke.klang.sprudel.lang.PatternMapperFn
import io.peekandpoke.klang.sprudel.lang.SprudelDsl
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.chain
import io.peekandpoke.klang.sprudel.lang.toPattern
import io.peekandpoke.klang.sprudel.lang.toVoiceValuePattern
import io.peekandpoke.klang.sprudel.lang.voiceValueModifier
// -- helpers ----------------------------------------------------------------------------------------------------------

private fun applyTimeMoveInCycle(
    pattern: SprudelPattern,
    args: List<SprudelDslArg<Any?>>,
    factor: Rational,
): SprudelPattern {
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
 * Nudges events later within their cycle by the given fraction of a cycle.
 *
 * Only shifts events already within the queried time window — does not pull events
 * from adjacent cycles. Useful for swing and intra-cycle timing adjustments.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").lateInCycle(0.02)                     // subtle late nudge
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd hh sd oh").lateInCycle("<0 0.1 0.3 0.5>")        // cycle through nudge amounts
 * ```
 *
 * @param amount Fraction of a cycle to nudge events later. Positive values push events right.
 *
 * @category tempo
 * @tags lateInCycle, timing, swing, nudge, offset, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.lateInCycle(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyTimeMoveInCycle(pattern = this, args = listOf(amount).asSprudelDslArgs(callInfo), factor = Rational.ONE)

/**
 * Parses this string as a pattern and nudges events later within their cycle.
 *
 * ```KlangScript(Playable)
 * "bd hh sd oh".lateInCycle("<0 0.1 0.3 0.5>").s()       // cycle through nudge amounts
 * ```
 *
 * @param amount Fraction of a cycle to nudge events later.
 */
@SprudelDsl
@KlangScript.Function
fun String.lateInCycle(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lateInCycle(amount, callInfo)

/**
 * Creates a [PatternMapperFn] that nudges events later within their cycle.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").apply(lateInCycle(0.1))                // nudge via mapper
 * ```
 *
 * @param amount Fraction of a cycle to nudge events later.
 *
 * @category tempo
 * @tags lateInCycle, timing, swing, nudge, offset, addon
 */
@SprudelDsl
@KlangScript.Function
fun lateInCycle(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.lateInCycle(amount, callInfo) }

/**
 * Chains a late-nudge onto this [PatternMapperFn], shifting events later within their cycle.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(stretchBy(2).lateInCycle(0.1))         // stretch then nudge late
 * ```
 *
 * @param amount Fraction of a cycle to nudge events later.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.lateInCycle(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.lateInCycle(amount, callInfo) }

// -- earlyInCycle() ---------------------------------------------------------------------------------------------------

/**
 * Nudges events earlier within their cycle by the given fraction of a cycle.
 *
 * Only shifts events already within the queried time window — does not pull events
 * from adjacent cycles. Complementary to [lateInCycle].
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").earlyInCycle(0.02)                     // subtle early nudge
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd hh sd oh").earlyInCycle("<0 0.1 0.3 0.5>")        // cycle through nudge amounts
 * ```
 *
 * @param amount Fraction of a cycle to nudge events earlier. Positive values push events left.
 *
 * @category tempo
 * @tags earlyInCycle, timing, nudge, offset, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.earlyInCycle(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyTimeMoveInCycle(pattern = this, args = listOf(amount).asSprudelDslArgs(callInfo), factor = Rational.MINUS_ONE)

/**
 * Parses this string as a pattern and nudges events earlier within their cycle.
 *
 * ```KlangScript(Playable)
 * "bd hh sd oh".earlyInCycle("<0 0.1 0.3 0.5>").s()       // cycle through nudge amounts
 * ```
 *
 * @param amount Fraction of a cycle to nudge events earlier.
 */
@SprudelDsl
@KlangScript.Function
fun String.earlyInCycle(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).earlyInCycle(amount, callInfo)

/**
 * Creates a [PatternMapperFn] that nudges events earlier within their cycle.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").apply(earlyInCycle(0.1))                // nudge via mapper
 * ```
 *
 * @param amount Fraction of a cycle to nudge events earlier.
 *
 * @category tempo
 * @tags earlyInCycle, timing, nudge, offset, addon
 */
@SprudelDsl
@KlangScript.Function
fun earlyInCycle(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.earlyInCycle(amount, callInfo) }

/**
 * Chains an early-nudge onto this [PatternMapperFn], shifting events earlier within their cycle.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(stretchBy(2).earlyInCycle(0.1))         // stretch then nudge early
 * ```
 *
 * @param amount Fraction of a cycle to nudge events earlier.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.earlyInCycle(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.earlyInCycle(amount, callInfo) }

// -- stretchBy() ------------------------------------------------------------------------------------------------------

private fun applyStretchBy(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

/**
 * Multiplies the duration of each event in this pattern by the given factor, without affecting onset time.
 *
 * A factor of `2.0` doubles each event's duration; `0.5` halves it. Events can overlap
 * (factor > 1) or leave gaps (factor < 1).
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").stretchBy(2)       // each note lasts twice as long
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").stretchBy("<1 2 0.5>")   // cycle through duration multipliers
 * ```
 *
 * @param factor The duration multiplier. Values > 1 extend events; values < 1 shorten them.
 *
 * @category tempo
 * @tags stretchBy, duration, stretch, event length, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.stretchBy(factor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyStretchBy(this, listOf(factor).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and multiplies the duration of each event by the given factor.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".stretchBy(0.5)           // each note lasts half its original duration
 * ```
 *
 * @param factor The duration multiplier. Values > 1 extend events; values < 1 shorten them.
 */
@SprudelDsl
@KlangScript.Function
fun String.stretchBy(factor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).stretchBy(factor, callInfo)

/**
 * Creates a [PatternMapperFn] that multiplies the duration of each event by the given factor.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(stretchBy(2))     // each note lasts twice as long via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(stretchBy("<1 2 0.5>")) // cycle through duration multipliers via mapper
 * ```
 *
 * @param factor The duration multiplier. Values > 1 extend events; values < 1 shorten them.
 *
 * @category tempo
 * @tags stretchBy, duration, stretch, event length, addon
 */
@SprudelDsl
@KlangScript.Function
fun stretchBy(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.stretchBy(factor, callInfo) }

/**
 * Chains a duration-stretch onto this [PatternMapperFn], multiplying each event's duration.
 *
 * ```KlangScript(Playable)
 * note("c3 d3").apply(lateInCycle(0.1).stretchBy(2))   // nudge late then double duration
 * ```
 *
 * @param factor The duration multiplier. Values > 1 extend events; values < 1 shorten them.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.stretchBy(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.stretchBy(factor, callInfo) }
