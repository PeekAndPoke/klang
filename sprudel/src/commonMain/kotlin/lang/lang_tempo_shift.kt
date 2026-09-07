/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._bind
import io.peekandpoke.klang.sprudel._outerJoin
import io.peekandpoke.klang.sprudel._splitQueries
import io.peekandpoke.klang.sprudel._withQueryTime
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.mapEvents

// -- early() ----------------------------------------------------------------------------------------------------------

private fun applyEarly(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    applyTimeShift(pattern = pattern, args = args, factor = -1.0)

/**
 * Nudges the pattern to start earlier by the given number of cycles.
 *
 * Shifts all events backward in time by the specified amount. For example, `early(0.5)` moves
 * every event half a cycle earlier so that what was at position 0.5 now appears at position 0.
 * Useful for creating syncopation or aligning patterns that are slightly off-beat.
 *
 * @param amount Time shift in cycles. 0.5 = shift half a cycle earlier. Default: 0 (no shift). Typical range: 0–1.
 * @return A pattern shifted earlier by the given number of cycles.
 *
 * ```KlangScript(Playable)
 * note("c d e f").early(0.25)         // shifts the pattern a quarter cycle earlier
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").stack(s("hh*4").early(0.125))   // hi-hat slightly ahead of the beat
 * ```
 *
 * @category tempo
 * @tags early, shift, time, offset, nudge, ahead
 */
@KlangScript.Function
fun SprudelPattern.early(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyEarly(this, listOf(amount).asSprudelDslArgs(callInfo))

/** Nudges the pattern to start earlier by the given number of cycles. */
@KlangScript.Function
fun String.early(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).early(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that nudges a pattern earlier by the given number of cycles.
 *
 * @param amount Time shift in cycles. Default: 0. Typical range: 0–1.
 *
 * ```KlangScript(Playable)
 * s("hh*4").apply(early(0.125))       // mapper form
 * ```
 *
 * @category tempo
 * @tags early, shift, time, offset, nudge, ahead
 */
@KlangScript.Function
fun early(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.early(amount, callInfo) }

/** Chains an early operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.early(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.early(amount, callInfo) }

// -- late() -----------------------------------------------------------------------------------------------------------

private fun applyLate(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    applyTimeShift(pattern = pattern, args = args, factor = 1.0)

/**
 * Nudges the pattern to start later by the given number of cycles.
 *
 * Shifts all events forward in time by the specified amount. For example, `late(0.5)` moves
 * every event half a cycle later so that what was at position 0 now appears at position 0.5.
 * Useful for creating delay effects or adjusting phase relationships between patterns.
 *
 * @param amount Time shift in cycles. 0.5 = shift half a cycle later. Default: 0 (no shift). Typical range: 0–1.
 * @return A pattern shifted later by the given number of cycles.
 *
 * ```KlangScript(Playable)
 * note("c d e f").late(0.25)          // shifts the pattern a quarter cycle later
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").stack(s("hh*4").late(0.125))    // hi-hat slightly behind the beat
 * ```
 *
 * @category tempo
 * @tags late, shift, time, offset, nudge, delay, behind
 */
@KlangScript.Function
fun SprudelPattern.late(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyLate(this, listOf(amount).asSprudelDslArgs(callInfo))

/** Nudges the pattern to start later by the given number of cycles. */
@KlangScript.Function
fun String.late(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).late(amount, callInfo)

/**
 * Returns a [PatternMapperFn] that nudges a pattern later by the given number of cycles.
 *
 * @param amount Time shift in cycles. Default: 0. Typical range: 0–1.
 *
 * ```KlangScript(Playable)
 * s("hh*4").apply(late(0.125))        // mapper form
 * ```
 *
 * @category tempo
 * @tags late, shift, time, offset, nudge, delay, behind
 */
@KlangScript.Function
fun late(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.late(amount, callInfo) }

/** Chains a late operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.late(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.late(amount, callInfo) }

// -- compress() -------------------------------------------------------------------------------------------------------

internal fun applyCompress(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) {
        return pattern
    }

    // We convert both arguments to patterns to support dynamic compress
    val startCtrl = listOf(args[0]).toPattern(voiceValueModifier)
    val endCtrl = listOf(args[1]).toPattern(voiceValueModifier)

    // Bind start...
    return startCtrl._bind { startEv ->
        val b = startEv.data.value?.asDouble ?: return@_bind null

        // ...bind end
        endCtrl._bind { endEv ->
            val e = endEv.data.value?.asDouble ?: return@_bind null

            // Guard: drop spans that are inverted or fall outside 0..1.
            if (b > e || b > 1.0 || e > 1.0 || b < 0.0 || e < 0.0) {
                return@_bind null // effectively silence for this event
            }

            val duration = e - b
            if (duration == 0.0) return@_bind null

            val factor = 1.0 / duration
            val fastGapped = applyFastGap(pattern, listOf(SprudelDslArg.of(factor)))

            val bTime = CycleTime.ofCycles(b)
            fastGapped._withQueryTime { t -> t - bTime }.mapEvents { ev ->
                val shiftedPart = ev.part.shift(bTime)
                val shiftedWhole = ev.whole.shift(bTime)
                ev.copy(part = shiftedPart, whole = shiftedWhole)
            }
        }
    }
}

/**
 * Compresses the pattern into a sub-range of each cycle, leaving silence outside that range.
 *
 * `compress(start, end)` squeezes the full pattern into the window `[start, end]` within
 * each cycle and leaves a gap everywhere else. Both values are in the range 0–1.
 *
 * @param start Beginning of the time window as a cycle fraction. Default: 0. Range: 0–1.
 * @param end End of the time window as a cycle fraction. Default: 1. Range: 0–1. Must be greater than start.
 * @return A pattern compressed into `[start, end]` with silence elsewhere.
 *
 * ```KlangScript(Playable)
 * note("c d e f").compress(0, 0.5)        // all 4 events fit into first half of each cycle
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").compress(0.25, 0.75)         // pattern compressed into middle 50% of each cycle
 * ```
 *
 * @category tempo
 * @tags compress, squeeze, timespan, gap, range
 */
@KlangScript.Function
fun SprudelPattern.compress(start: PatternLike, end: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyCompress(this, listOf(start, end).asSprudelDslArgs(callInfo))

/** Compresses the pattern into `[start, end]` within each cycle, leaving silence outside. */
@KlangScript.Function
fun String.compress(start: PatternLike, end: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).compress(start, end, callInfo)

/**
 * Returns a [PatternMapperFn] that compresses a pattern into `[start, end]` per cycle.
 *
 * @param start Beginning of the time window as a cycle fraction. Range: 0–1.
 * @param end End of the time window as a cycle fraction. Range: 0–1.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(compress(0, 0.5))  // mapper form
 * ```
 *
 * @category tempo
 * @tags compress, squeeze, timespan, gap, range
 */
@KlangScript.Function
fun compress(start: PatternLike, end: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.compress(start, end, callInfo) }

/** Chains a compress operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.compress(start: PatternLike, end: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.compress(start, end, callInfo) }

// -- focus() ----------------------------------------------------------------------------------------------------------

private fun applyFocus(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) return source

    val startCtrl = listOf(args[0]).toPattern(voiceValueModifier)
    val endCtrl = listOf(args[1]).toPattern(voiceValueModifier)

    return startCtrl._bind { startEv ->
        val s = startEv.data.value?.asDouble ?: return@_bind null

        endCtrl._bind { endEv ->
            val e = endEv.data.value?.asDouble ?: return@_bind null

            if (s >= e) return@_bind null

            val dCycles = (e - s)
            val sTime = CycleTime.ofCycles(s)
            val sFlooredTime = sTime.floorToCycle()

            source._withQueryTime { t -> (t - sTime).divBy(dCycles) + sFlooredTime }.mapEvents { ev ->
                val scaledPart = ev.part.shift(-sFlooredTime).scale(dCycles).shift(sTime)
                val scaledWhole = ev.whole.shift(-sFlooredTime).scale(dCycles).shift(sTime)
                ev.copy(part = scaledPart, whole = scaledWhole)
            }
        }
    }
}

/**
 * Zooms in on a sub-range of a cycle, stretching that portion to fill the whole cycle.
 *
 * `focus(start, end)` is like the inverse of `compress`: it takes the slice `[start, end]`
 * of the original pattern and stretches it to fill a full cycle.
 *
 * @param start Beginning of the section to zoom into, as a cycle fraction. Default: 0. Range: 0–1.
 * @param end End of the section to zoom into, as a cycle fraction. Default: 1. Range: 0–1. Must be greater than start.
 * @return A pattern that zooms into `[start, end]`, stretching it to fill each cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").focus(0, 0.5)       // only the first half is shown, stretched to a full cycle
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").focus(0.25, 0.75)  // middle 50% of the pattern, stretched to fill the cycle
 * ```
 *
 * @category tempo
 * @tags focus, zoom, timespan, range, stretch
 */
@KlangScript.Function
fun SprudelPattern.focus(start: PatternLike, end: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyFocus(this, listOf(start, end).asSprudelDslArgs(callInfo))

/** Zooms in on `[start, end]` of a cycle and stretches that portion to fill each cycle. */
@KlangScript.Function
fun String.focus(start: PatternLike, end: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).focus(start, end, callInfo)

/**
 * Returns a [PatternMapperFn] that zooms in on `[start, end]` and stretches it to fill each cycle.
 *
 * @param start Beginning of the section as a cycle fraction. Range: 0–1.
 * @param end End of the section as a cycle fraction. Range: 0–1.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(focus(0, 0.5))  // mapper form
 * ```
 *
 * @category tempo
 * @tags focus, zoom, timespan, range, stretch
 */
@KlangScript.Function
fun focus(start: PatternLike, end: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.focus(start, end, callInfo) }

/** Chains a focus operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.focus(start: PatternLike, end: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.focus(start, end, callInfo) }

// -- helpers ----------------------------------------------------------------------------------------------------------

private fun applyTimeMoveInCycle(
    pattern: SprudelPattern,
    args: List<SprudelDslArg<Any?>>,
    factor: Double,
): SprudelPattern {
    if (args.isEmpty()) return pattern
    val control = args.toPattern()

    // 1. Split queries to ensure we process one cycle at a time
    // 2. Use _outerJoin to iterate source events and sample control at event time
    return pattern._splitQueries()._outerJoin(control) { srcEv, ctrlEv ->
        val shiftVal = (ctrlEv?.data?.value?.asDouble ?: 0.0)
        val shift = CycleTime.ofCycles(shiftVal * factor)

        if (shift == CycleTime.ZERO) return@_outerJoin srcEv

        val shiftedPart = srcEv.part.shift(shift)
        val shiftedWhole = srcEv.whole.shift(shift)

        // Clip to the cycle of the *original* event
        val cycleStart = srcEv.whole.begin.floorToCycle()
        val cycleEnd = cycleStart + CycleTime.ONE

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
 * @tags lateInCycle, timing, swing, nudge, offset
 */
@KlangScript.Function
fun SprudelPattern.lateInCycle(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyTimeMoveInCycle(pattern = this, args = listOf(amount).asSprudelDslArgs(callInfo), factor = 1.0)

/**
 * Parses this string as a pattern and nudges events later within their cycle.
 *
 * ```KlangScript(Playable)
 * "bd hh sd oh".lateInCycle("<0 0.1 0.3 0.5>").s()       // cycle through nudge amounts
 * ```
 *
 * @param amount Fraction of a cycle to nudge events later.
 */
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
 * @tags lateInCycle, timing, swing, nudge, offset
 */
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
 * @tags earlyInCycle, timing, nudge, offset
 */
@KlangScript.Function
fun SprudelPattern.earlyInCycle(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyTimeMoveInCycle(pattern = this, args = listOf(amount).asSprudelDslArgs(callInfo), factor = -1.0)

/**
 * Parses this string as a pattern and nudges events earlier within their cycle.
 *
 * ```KlangScript(Playable)
 * "bd hh sd oh".earlyInCycle("<0 0.1 0.3 0.5>").s()       // cycle through nudge amounts
 * ```
 *
 * @param amount Fraction of a cycle to nudge events earlier.
 */
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
 * @tags earlyInCycle, timing, nudge, offset
 */
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
@KlangScript.Function
fun PatternMapperFn.earlyInCycle(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.earlyInCycle(amount, callInfo) }
