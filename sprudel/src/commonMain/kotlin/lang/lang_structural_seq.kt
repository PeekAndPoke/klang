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
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.EmptyPattern
import io.peekandpoke.klang.sprudel.pattern.SequencePattern
import io.peekandpoke.klang.sprudel.pattern.StackPattern
import io.peekandpoke.klang.sprudel.withWeight
import kotlin.math.ceil
import kotlin.math.floor

// -- seq() ------------------------------------------------------------------------------------------------------------

/** Creates a sequence pattern. */
internal fun applySeq(patterns: List<SprudelPattern>): SprudelPattern {
    return when (patterns.size) {
        0 -> silence
        1 -> patterns.first()
        else -> SequencePattern(patterns)
    }
}

/**
 * Creates a sequence pattern that squeezes all patterns into one cycle.
 *
 * All patterns are evenly distributed within a single cycle. With two patterns
 * each gets half the cycle; with three patterns each gets a third, and so on.
 * A list passed as an argument is treated as a nested sub-sequence that occupies
 * the same time slot as any other single argument.
 *
 * @param patterns Patterns to squeeze into one cycle. Accepts patterns, strings, numbers,
 *                 lists (as nested sub-sequences), and other values that can be converted to patterns.
 * @return A pattern with all inputs squeezed into one cycle
 *
 * ```KlangScript(Playable)
 * seq("c d e", "f g a").note()  // Two patterns squeezed into one cycle
 * ```
 *
 * ```KlangScript(Playable)
 * seq(note("c"), note("e"), note("g"))  // Three notes, each gets 1/3 of the cycle
 * ```
 *
 * ```KlangScript(Playable)
 * seq("bd", ["sd", "oh"], "hh").s()  // Nested list as sub-sequence within its slot
 * ```
 * @category structural
 * @tags sequence, timing, control, order, pattern-creator
 */
@KlangScript.Function
fun seq(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applySeq(patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Appends patterns to this pattern and squeezes all of them into one cycle.
 *
 * This pattern and all appended patterns are evenly distributed within a single cycle.
 *
 * @param patterns Additional patterns to append to the sequence
 * @return A pattern with all inputs squeezed into one cycle
 *
 * ```KlangScript(Playable)
 * note("c e").seq("g a".note())
 * ```
 *
 * ```KlangScript(Playable)
 * "bd sd".seq("hh hh", "cp").s()
 * ```
 */
@KlangScript.Function
fun SprudelPattern.seq(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applySeq(listOf(this) + patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Converts this string to a pattern and squeezes it together with additional patterns into one cycle.
 *
 * @param patterns Additional patterns to append to the sequence
 * @return A pattern with all inputs squeezed into one cycle
 *
 * ```KlangScript(Playable)
 * "c e".seq("g a").note()  // Two patterns squeezed into one cycle
 * ```
 */
@KlangScript.Function
fun String.seq(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).seq(*patterns, callInfo = callInfo)

// -- stack() ----------------------------------------------------------------------------------------------------------

private fun applyStack(patterns: List<SprudelPattern>): SprudelPattern {
    return when (patterns.size) {
        0 -> silence
        1 -> patterns.first()
        else -> StackPattern(patterns)
    }
}

/**
 * Plays multiple patterns simultaneously, layering them on top of each other.
 *
 * Unlike [seq], which plays patterns one after another, `stack` overlays all patterns so they all
 * sound at the same time over the full cycle. This is useful for chords, polyrhythms, and combining
 * independent pattern layers.
 *
 * @param patterns Patterns to layer. Accepts patterns, strings, numbers, and other pattern-like values.
 * @return A pattern that plays all inputs simultaneously
 *
 * ```KlangScript(Playable)
 * stack(note("c e g"), s("bd sd"))  // Chord with beat underneath
 * ```
 *
 * ```KlangScript(Playable)
 * stack("c e", "g b").note()  // Two melodic lines at the same time
 * ```
 * @category structural
 * @tags stack, layer, chord, polyrhythm, simultaneous
 */
@KlangScript.Function
fun stack(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyStack(patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Layers this pattern together with additional patterns so they all play simultaneously.
 *
 * ```KlangScript(Playable)
 * note("c e g").stack(s("bd sd"))  // Melody on top of a beat
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e").stack("g b".note())  // Two melodic lines layered
 * ```
 */
@KlangScript.Function
fun SprudelPattern.stack(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyStack(listOf(this) + patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Parses this string as a pattern and layers it together with additional patterns.
 *
 * ```KlangScript(Playable)
 * "c e g".stack("g b d").note()  // Two chord voicings layered
 * ```
 */
@KlangScript.Function
fun String.stack(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).stack(*patterns, callInfo = callInfo)

// -- arrange() --------------------------------------------------------------------------------------------------------

private fun applyArrange(args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val segments = args.parseWeightedArgs()

    if (segments.isEmpty()) return silence

    val totalDuration = segments.sumOf { it.first }
    if (totalDuration <= 0.0) return silence

    // arrange() is concatenation, NOT time-scaling: each segment plays its own cycles
    // 0..dur-1 at natural speed, back to back, looping after `totalDuration` cycles.
    //
    // We place each segment at its cumulative cycle offset and query it at LOCAL time
    // (outer - segStart), shifting events back by segStart. For the common integer-duration
    // case every offset is an exact whole-cycle multiple of T, so `shift` introduces ZERO
    // rounding and per-cycle downbeats stay exactly on the grid.
    //
    // (The earlier `pat.fast(dur).withWeight(dur)` + `slow(total)` formulation scaled query
    // time through three rounded steps — `round(round(round(n*T/total)*…)*…)` — which could
    // nudge a cycle boundary a tick past n*T. The downbeat sitting exactly on n*T was then
    // dropped across the half-open query seam: rejected by the `< to` overlap test on one
    // side, and demoted from an onset to a continuation on the other. See LangArrangeSpec.)
    //
    // Cumulative starts are derived from a running Double sum and snapped once via ofCycles,
    // so fractional durations round per-boundary instead of accumulating drift in ticks.
    val starts = DoubleArray(segments.size)
    var acc = 0.0
    for (i in segments.indices) {
        starts[i] = acc
        acc += segments[i].first
    }

    return object : SprudelPattern {
        override val weight: Double = 1.0
        override val numSteps: Double? = null

        override fun estimateCycleDuration(): Double = totalDuration

        override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> {
            val result = mutableListOf<SprudelPatternEvent>()

            // Which repetitions of the whole arrangement does [from, to) touch?
            val startLoop = floor(from.toCycles() / totalDuration).toInt()
            val endLoop = ceil(to.toCycles() / totalDuration).toInt()

            var loop = startLoop
            while (loop < endLoop) {
                val loopBaseCycles = loop * totalDuration

                for (i in segments.indices) {
                    val (dur, pat) = segments[i]

                    val segStart = CycleTime.ofCycles(loopBaseCycles + starts[i])
                    val segEnd = CycleTime.ofCycles(loopBaseCycles + starts[i] + dur)

                    val qStart = from.coerceAtLeast(segStart)
                    val qEnd = to.coerceAtMost(segEnd)

                    if (qEnd > qStart) {
                        // Local (segment-relative) query window — exact when segStart is a
                        // whole-cycle offset (the integer-duration case).
                        val localStart = qStart - segStart
                        val localEnd = qEnd - segStart

                        pat.queryArcContextual(localStart, localEnd, ctx).forEach { ev ->
                            result.add(
                                ev.copy(
                                    part = ev.part.shift(segStart),
                                    whole = ev.whole.shift(segStart),
                                )
                            )
                        }
                    }
                }

                loop++
            }

            return result
        }
    }
}

/**
 * Plays each segment for a specified number of cycles, forming a repeating arrangement.
 *
 * Each segment is a `[duration, pattern]` pair where `duration` is the number of cycles that
 * pattern plays at its natural speed. A bare pattern without a duration defaults to 1 cycle.
 * The whole arrangement repeats after the sum of all durations.
 *
 * Unlike [stepcat], which compresses all patterns into a single cycle, `arrange` keeps each
 * pattern's internal tempo — a 2-cycle segment genuinely plays the pattern for 2 full cycles.
 *
 * @param segments Pairs of `[duration, pattern]`, or bare patterns (duration defaults to 1).
 * @return A pattern that plays each segment for its specified number of cycles, then repeats
 *
 * ```KlangScript(Playable)
 * arrange([2, "a b"], [1, "c"]).note()  // "a b" for 2 cycles, "c" for 1
 * ```
 *
 * ```KlangScript(Playable)
 * arrange([3, note("c e g")], [1, note("f a c")]).s("piano")  // chord changes over 4 cycles
 * ```
 *
 * ```KlangScript(Playable)
 * arrange(note("c e g"), [2, note("f a c")]).s("piano")  // Pattern without weight
 * ```
 * @category structural
 * @tags arrange, sequence, timing, duration, loop
 */
@KlangScript.Function
fun arrange(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArrange(segments.toList().asSprudelDslArgs(callInfo))

/**
 * Prepends this pattern (duration 1) and plays it followed by the given segments.
 *
 * ```KlangScript(Playable)
 * note("c e g").arrange([2, note("f a c")]).s("piano")  // 1 cycle chord, then 2 cycles
 * ```
 */
@KlangScript.Function
fun SprudelPattern.arrange(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArrange(listOf(SprudelDslArg.of(this)) + segments.toList().asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern (duration 1) and arranges it together with the given segments.
 *
 * ```KlangScript(Playable)
 * "c e g".arrange([2, "f a c"]).note()  // 1 cycle, then 2 cycles of second chord
 * ```
 */
@KlangScript.Function
fun String.arrange(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).arrange(*segments, callInfo = callInfo)

// -- stackBy() --------------------------------------------------------------------------------------------------------

private fun applyStackBy(patterns: List<SprudelPattern>, alignment: Double): SprudelPattern {
    if (patterns.isEmpty()) return silence

    // Get duration for each pattern
    val durations = patterns.map { it.estimateCycleDuration() }
    val maxDur = durations.maxOrNull() ?: 1.0

    // Align patterns by padding them with gaps to match maxDur
    val alignedPatterns = patterns.zip(durations).map { (pat, dur) ->
        if (dur == maxDur) {
            pat
        } else {
            val diff = maxDur - dur
            val leftGap = diff * alignment
            val rightGap = diff - leftGap

            val segments = mutableListOf<SprudelPattern>()

            // Use EmptyPattern instead of GapPattern for padding.
            // EmptyPattern occupies time (via weight) but produces NO events.
            // GapPattern produces "silent events" which pollute the event count.

            if (leftGap > 0.0) {
                segments.add(EmptyPattern.withWeight(leftGap))
            }

            segments.add(pat.withWeight(dur))

            if (rightGap > 0.0) {
                segments.add(EmptyPattern.withWeight(rightGap))
            }

            // SequencePattern fits total weight into 1 cycle.
            // We slow it down by maxDur to restore original speeds and placement within the larger cycle.
            SequencePattern(segments).slow(maxDur)
        }
    }

    return StackPattern(alignedPatterns)
}

/**
 * Layers patterns simultaneously, aligning shorter patterns within the span of the longest.
 *
 * Unlike [stack], which always aligns all patterns from the start, `stackBy` lets you control
 * where shorter patterns sit within the span of the longest: 0 = left-aligned, 0.5 = centered,
 * 1 = right-aligned.
 *
 * @param alignment Position within the longest pattern's span (0 = left, 0.5 = center, 1 = right).
 * @param patterns Patterns to layer. The longest determines the total span.
 * @return A pattern with all inputs layered at the given alignment
 *
 * ```KlangScript(Playable)
 * stackBy(0.5, note("c"), note("c e g"))  // short pattern centered within the long one
 * ```
 *
 * ```KlangScript(Playable)
 * stackBy(1.0, s("bd"), s("bd sd ht lt"))  // short pattern right-aligned
 * ```
 * @category structural
 * @tags stack, layer, alignment, simultaneous
 */
@KlangScript.Function
fun stackBy(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    // TODO: support control patterns
    val dslArgs = args.toList().asSprudelDslArgs(callInfo)
    val alignmentVal = dslArgs.firstOrNull()?.value?.asDoubleOrNull() ?: 0.0
    val patternList = dslArgs.drop(1).toListOfPatterns()
    return applyStackBy(patterns = patternList, alignment = alignmentVal)
}

// -- stackLeft() ------------------------------------------------------------------------------------------------------

/**
 * Layers patterns simultaneously, aligning shorter patterns to the left (start) of the longest.
 *
 * @param patterns Patterns to layer.
 * @return A pattern with all inputs layered, shorter ones left-aligned
 *
 * ```KlangScript(Playable)
 * stackLeft(note("c"), note("c e g"))  // short pattern starts at the same time as the long one
 * ```
 * @category structural
 * @tags stack, layer, alignment, simultaneous
 */
@KlangScript.Function
fun stackLeft(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyStackBy(
        patterns = patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns(),
        alignment = 0.0,
    )

// -- stackRight() -----------------------------------------------------------------------------------------------------

/**
 * Layers patterns simultaneously, aligning shorter patterns to the right (end) of the longest.
 *
 * @param patterns Patterns to layer.
 * @return A pattern with all inputs layered, shorter ones right-aligned
 *
 * ```KlangScript(Playable)
 * stackRight(note("c"), note("c e g"))  // short pattern ends at the same time as the long one
 * ```
 * @category structural
 * @tags stack, layer, alignment, simultaneous
 */
@KlangScript.Function
fun stackRight(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyStackBy(
        patterns = patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns(),
        alignment = 1.0,
    )

// -- stackCentre() ----------------------------------------------------------------------------------------------------

/**
 * Layers patterns simultaneously, aligning shorter patterns to the centre of the longest.
 *
 * @param patterns Patterns to layer.
 * @return A pattern with all inputs layered, shorter ones centred
 *
 * ```KlangScript(Playable)
 * stackCentre(note("c"), note("c e g"))  // short pattern centred within the long one
 * ```
 * @category structural
 * @tags stack, layer, alignment, simultaneous
 */
@KlangScript.Function
fun stackCentre(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyStackBy(
        patterns = patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns(),
        alignment = 0.5,
    )

// -- sequenceP() ------------------------------------------------------------------------------------------------------

/**
 * Alias for [seq]. Creates a sequence pattern that squeezes all patterns into one cycle.
 *
 * @param patterns Patterns to squeeze into one cycle.
 * @return A pattern with all inputs squeezed into one cycle
 *
 * ```KlangScript(Playable)
 * sequenceP("c d", "e f").note()  // Two patterns squeezed into one cycle
 * ```
 * @alias seq
 * @category structural
 * @tags sequence, timing, order
 */
@KlangScript.Function
fun sequenceP(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    seq(*patterns, callInfo = callInfo)

