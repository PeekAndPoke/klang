/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.common.math.lcm
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.PropertyOverridePattern
import io.peekandpoke.klang.sprudel.pattern.SequencePattern
import io.peekandpoke.klang.sprudel.pattern.StackPattern
import io.peekandpoke.klang.sprudel.withWeight
import kotlin.math.ceil
import kotlin.math.floor

// -- stepcat() / timeCat() --------------------------------------------------------------------------------------------

private fun applyStepcat(args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val segments = args.parseWeightedArgs()

    if (segments.isEmpty()) return silence

    // Parse arguments into weighted patterns
    val patterns = segments.map { (dur, pat) ->
        pat.withWeight(dur)
    }

    // Use SequencePattern which handles weighted time distribution and compression to 1 cycle
    return SequencePattern(patterns)
}

/**
 * Concatenates weighted patterns and compresses the result to fit exactly one cycle.
 *
 * Each segment is a `[duration, pattern]` pair. Duration determines the proportional share of the
 * cycle each pattern gets. A bare pattern without a duration defaults to weight 1.
 * Unlike [arrange], `stepcat` always fits everything into a single cycle regardless of durations.
 *
 * @param segments Pairs of `[duration, pattern]`, or bare patterns (weight defaults to 1).
 * @return A pattern with all segments proportionally distributed within one cycle
 *
 * ```KlangScript(Playable)
 * stepcat([1, "a"], [3, "b"]).note()  // "a" takes 1/4, "b" takes 3/4 of the cycle
 * ```
 *
 * ```KlangScript(Playable)
 * stepcat([2, note("c")], [1, note("e g")])  // "c" takes 2/3, "e g" takes 1/3
 * ```
 * @alias timeCat, timecat, s_cat
 * @category structural
 * @tags stepcat, sequence, timing, proportional, duration
 */
@KlangScript.Function
fun stepcat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyStepcat(segments.toList().asSprudelDslArgs(callInfo))

/**
 * Prepends this pattern (weight 1) and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript(Playable)
 * note("c").stepcat([3, note("e g")])  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@KlangScript.Function
fun SprudelPattern.stepcat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyStepcat(listOf(SprudelDslArg.of(this)) + segments.toList().asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern (weight 1) and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript(Playable)
 * "c".stepcat([3, "e g"]).note()  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@KlangScript.Function
fun String.stepcat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).stepcat(*segments, callInfo = callInfo)

/**
 * Alias for [stepcat]. Concatenates weighted patterns into exactly one cycle.
 *
 * @param segments Pairs of `[duration, pattern]`, or bare patterns (weight defaults to 1).
 * @return A pattern with all segments proportionally distributed within one cycle
 *
 * ```KlangScript(Playable)
 * timeCat([1, "a"], [3, "b"]).note()  // "a" takes 1/4, "b" takes 3/4
 * ```
 * @alias stepcat, timecat, s_cat
 * @category structural
 * @tags stepcat, sequence, timing, proportional, duration
 */
@KlangScript.Function
fun timeCat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    stepcat(*segments, callInfo = callInfo)

/**
 * Alias for [stepcat]. Prepends this pattern (weight 1) and arranges all segments in one cycle.
 *
 * ```KlangScript(Playable)
 * note("c").timeCat([3, note("e g")])  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@KlangScript.Function
fun SprudelPattern.timeCat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.stepcat(*segments, callInfo = callInfo)

/**
 * Alias for [stepcat]. Parses this string and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript(Playable)
 * "c".timeCat([3, "e g"]).note()  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@KlangScript.Function
fun String.timeCat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).timeCat(*segments, callInfo = callInfo)

/**
 * Alias for [stepcat]. Concatenates weighted patterns into exactly one cycle.
 *
 * @param segments Pairs of `[duration, pattern]`, or bare patterns (weight defaults to 1).
 * @return A pattern with all segments proportionally distributed within one cycle
 *
 * ```KlangScript(Playable)
 * timecat([1, "a"], [3, "b"]).note()  // "a" takes 1/4, "b" takes 3/4
 * ```
 * @alias stepcat, timeCat, s_cat
 * @category structural
 * @tags stepcat, sequence, timing, proportional, duration
 */
@KlangScript.Function
fun timecat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    stepcat(*segments, callInfo = callInfo)

/**
 * Alias for [stepcat]. Prepends this pattern (weight 1) and arranges all segments in one cycle.
 *
 * ```KlangScript(Playable)
 * note("c").timecat([3, note("e g")])  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@KlangScript.Function
fun SprudelPattern.timecat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.stepcat(*segments, callInfo = callInfo)

/**
 * Alias for [stepcat]. Parses this string and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript(Playable)
 * "c".timecat([3, "e g"]).note()  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@KlangScript.Function
fun String.timecat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).timecat(*segments, callInfo = callInfo)

/**
 * Alias for [stepcat]. Concatenates weighted patterns into exactly one cycle.
 *
 * @param segments Pairs of `[duration, pattern]`, or bare patterns (weight defaults to 1).
 * @return A pattern with all segments proportionally distributed within one cycle
 *
 * ```KlangScript(Playable)
 * s_cat([1, "a"], [3, "b"]).note()  // "a" takes 1/4, "b" takes 3/4
 * ```
 * @alias stepcat, timeCat, timecat
 * @category structural
 * @tags stepcat, sequence, timing, proportional, duration
 */
@KlangScript.Function
fun s_cat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    stepcat(*segments, callInfo = callInfo)

/**
 * Alias for [stepcat]. Prepends this pattern (weight 1) and arranges all segments in one cycle.
 *
 * ```KlangScript(Playable)
 * note("c").s_cat([3, note("e g")])  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@KlangScript.Function
fun SprudelPattern.s_cat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.stepcat(*segments, callInfo = callInfo)

/**
 * Alias for [stepcat]. Parses this string and arranges all segments proportionally in one cycle.
 *
 * ```KlangScript(Playable)
 * "c".s_cat([3, "e g"]).note()  // "c" takes 1/4, "e g" takes 3/4
 * ```
 */
@KlangScript.Function
fun String.s_cat(vararg segments: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).s_cat(*segments, callInfo = callInfo)

// -- polyrhythm() -----------------------------------------------------------------------------------------------------

/**
 * Alias for [stack]. Plays multiple patterns simultaneously to create polyrhythms.
 *
 * @param patterns Patterns to layer simultaneously.
 * @return A pattern that plays all inputs at the same time
 *
 * ```KlangScript(Playable)
 * polyrhythm(s("bd sd"), s("hh hh hh"))  // 2-beat and 3-beat patterns together
 * ```
 * @alias stack
 * @category structural
 * @tags polyrhythm, stack, layer, simultaneous, rhythm
 */
@KlangScript.Function
fun polyrhythm(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    stack(*patterns, callInfo = callInfo)

/**
 * Alias for [stack]. Layers this pattern together with additional patterns simultaneously.
 *
 * ```KlangScript(Playable)
 * s("bd sd").polyrhythm(s("hh hh hh"))  // Layer two patterns
 * ```
 */
@KlangScript.Function
fun SprudelPattern.polyrhythm(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.stack(*patterns, callInfo = callInfo)

/**
 * Alias for [stack]. Parses this string as a pattern and layers it with additional patterns.
 *
 * ```KlangScript(Playable)
 * "bd sd".polyrhythm(s("hh hh hh"))  // Layer two patterns
 * ```
 */
@KlangScript.Function
fun String.polyrhythm(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).polyrhythm(*patterns, callInfo = callInfo)

// -- cat() ------------------------------------------------------------------------------------------------------------

fun applyCat(patterns: List<SprudelPattern>): SprudelPattern {
    if (patterns.isEmpty()) return silence
    if (patterns.size == 1) return patterns[0]

    return object : SprudelPattern {
        override val weight: Double = patterns.sumOf { it.weight }
        override val numSteps: Double? = null

        override fun estimateCycleDuration(): Double {
            return patterns.fold(0.0) { acc, p -> acc + p.estimateCycleDuration() }
        }

        override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> {
            val totalDuration = estimateCycleDuration()
            if (totalDuration <= 0.0) return emptyList()

            val result = mutableListOf<SprudelPatternEvent>()

            // Find which "loops" of the total sequence we touch
            val totalDurationCycles = totalDuration
            val startLoop = floor(from.toCycles() / totalDurationCycles).toInt()
            val endLoop = ceil(to.toCycles() / totalDurationCycles).toInt()

            var currentLoop = startLoop
            while (currentLoop < endLoop) {
                val loopStart = CycleTime.ofCycles(currentLoop * totalDurationCycles)
                var currentOffset = loopStart

                for (p in patterns) {
                    val durTime = CycleTime.ofCycles(p.estimateCycleDuration())
                    val pStart = currentOffset
                    val pEnd = pStart + durTime

                    // Check intersection
                    val start = from.coerceAtLeast(pStart)
                    val end = to.coerceAtMost(pEnd)

                    if (end > start) {
                        // Map to pattern local time
                        val localStart = start - pStart
                        val localEnd = end - pStart

                        val pEvents = p.queryArcContextual(localStart, localEnd, ctx)

                        // Shift back
                        pEvents.forEach { ev ->
                            result.add(
                                ev.copy(
                                    part = ev.part.shift(pStart),
                                    whole = ev.whole.shift(pStart)
                                )
                            )
                        }
                    }
                    currentOffset += durTime
                }
                currentLoop++
            }
            return result
        }
    }
}

/**
 * Concatenates patterns in sequence, each playing for its natural cycle duration before the next begins.
 *
 * Unlike [seq], which squeezes all patterns into one cycle, `cat` plays each pattern for its full
 * natural duration. A 2-cycle pattern takes 2 cycles, then the next pattern begins.
 *
 * @param patterns Patterns to concatenate. Each plays for its natural duration.
 * @return A pattern that plays each input in turn for its natural duration
 *
 * ```KlangScript(Playable)
 * cat(note("c d"), note("e f g")).s("piano")  // 2-step then 3-step pattern in sequence
 * ```
 *
 * ```KlangScript(Playable)
 * cat(s("bd sd"), s("hh hh hh hh"))  // alternates between patterns each cycle
 * ```
 * @alias slowcat
 * @category structural
 * @tags cat, sequence, concatenate, timing
 */
@KlangScript.Function
fun cat(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyCat(patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Appends patterns to this pattern, each playing for its natural cycle duration.
 *
 * ```KlangScript(Playable)
 * note("c d").cat(note("e f g"))  // "c d" then "e f g" in sequence
 * ```
 */
@KlangScript.Function
fun SprudelPattern.cat(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyCat(listOf(this) + patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Parses this string as a pattern and concatenates it with the given patterns in sequence.
 *
 * ```KlangScript(Playable)
 * "c d".cat("e f g").note()  // "c d" then "e f g" in sequence
 * ```
 */
@KlangScript.Function
fun String.cat(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).cat(*patterns, callInfo = callInfo)

/**
 * Alias for [seq]. Concatenates patterns, squeezing them all into one cycle.
 *
 * @param patterns Patterns to squeeze into one cycle.
 * @return A pattern with all inputs squeezed into one cycle
 *
 * ```KlangScript(Playable)
 * fastcat("bd", "sd").s()  // same as seq("bd", "sd") or "bd sd"
 * ```
 * @alias seq
 * @category structural
 * @tags sequence, timing, order
 */
@KlangScript.Function
fun fastcat(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    seq(*patterns, callInfo = callInfo)

/**
 * Alias for [seq]. Appends patterns to this pattern, squeezing all into one cycle.
 *
 * ```KlangScript(Playable)
 * s("bd").fastcat(s("sd"))  // "bd sd" squeezed into one cycle
 * ```
 */
@KlangScript.Function
fun SprudelPattern.fastcat(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.seq(*patterns, callInfo = callInfo)

/**
 * Alias for [seq]. Parses this string and squeezes it together with the given patterns into one cycle.
 *
 * ```KlangScript(Playable)
 * "bd".fastcat("sd").s()  // "bd sd" squeezed into one cycle
 * ```
 */
@KlangScript.Function
fun String.fastcat(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fastcat(*patterns, callInfo = callInfo)

/**
 * Alias for [cat]. Concatenates patterns, each taking one full cycle.
 *
 * Note: this behaves like `cat` / `slowcatPrime`, maintaining
 * absolute time (cycles of inner patterns may be "skipped" while they are not playing).
 *
 * @param patterns Patterns to concatenate. Each plays for one cycle.
 * @return A pattern that plays each input for one cycle in turn
 *
 * ```KlangScript(Playable)
 * slowcat("bd sd", "hh hh hh hh").s()  // cycle 0: "bd sd", cycle 1: "hh hh hh hh"
 * ```
 * @alias cat
 * @category structural
 * @tags sequence, concatenate, timing
 */
@KlangScript.Function
fun slowcat(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    cat(*patterns, callInfo = callInfo)

/**
 * Alias for [cat]. Appends patterns to this pattern, each taking one full cycle.
 *
 * ```KlangScript(Playable)
 * s("bd sd").slowcat(s("hh hh hh hh"))  // alternates each cycle
 * ```
 */
@KlangScript.Function
fun SprudelPattern.slowcat(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.cat(*patterns, callInfo = callInfo)

/**
 * Alias for [cat]. Parses this string and concatenates with the given patterns, each taking one cycle.
 *
 * ```KlangScript(Playable)
 * "bd sd".slowcat("hh hh hh hh").s()  // alternates each cycle
 * ```
 */
@KlangScript.Function
fun String.slowcat(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).slowcat(*patterns, callInfo = callInfo)

// -- slowcatPrime() ---------------------------------------------------------------------------------------------------

/**
 * Cycles through a list of patterns infinitely, playing one pattern per cycle.
 * Preserves absolute time (does not reset pattern time to 0 for each cycle).
 */
fun applySlowcatPrime(patterns: List<SprudelPattern>): SprudelPattern {
    if (patterns.isEmpty()) return silence
    if (patterns.size == 1) return patterns[0]

    return object : SprudelPattern {
        override val weight: Double = patterns.sumOf { it.weight }
        override val numSteps: Double? = null

        override fun estimateCycleDuration(): Double = 1.0 * patterns.size

        override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> {

            val result = mutableListOf<SprudelPatternEvent>()
            val n = patterns.size
            var cycleIdx = from.cycleIndex()

            while (CycleTime.ofCycleIndex(cycleIdx) < to) {
                val cycle = CycleTime.ofCycleIndex(cycleIdx)
                val cycleEnd = cycle + CycleTime.ONE
                val queryStart = from.coerceAtLeast(cycle)
                val queryEnd = to.coerceAtMost(cycleEnd)

                // Select pattern using modulo (cycles infinitely through patterns)
                val patternIndex = cycleIdx.mod(n)
                val pattern = patterns[patternIndex]

                // Crucial: We query at absolute time (queryStart), not relative time.
                // This is what makes it "Prime".
                if (queryEnd > queryStart) {
                    result.addAll(pattern.queryArcContextual(queryStart, queryEnd, ctx))
                }

                cycleIdx++
            }

            return result
        }
    }
}

/**
 * Cycles through patterns one per cycle, preserving absolute time across pattern switches.
 *
 * Like [cat], but when a pattern resumes it continues from where it would be at absolute time,
 * rather than restarting from zero. This means inner cycles of each pattern are not reset.
 *
 * @param patterns Patterns to cycle through, one per cycle.
 * @return A pattern that cycles through each input at absolute time
 *
 * ```KlangScript(Playable)
 * slowcatPrime(note("c d e f"), note("g a b c")).s("piano")  // each pattern plays at abs time
 * ```
 * @category structural
 * @tags sequence, concatenate, timing, absolute
 */
@KlangScript.Function
fun slowcatPrime(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applySlowcatPrime(patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Appends patterns to this pattern, cycling through them one per cycle at absolute time.
 *
 * ```KlangScript(Playable)
 * note("c d").slowcatPrime(note("e f g"))  // cycles through at absolute time
 * ```
 */
@KlangScript.Function
fun SprudelPattern.slowcatPrime(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applySlowcatPrime(listOf(this) + patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Parses this string and cycles through it with given patterns, one per cycle at absolute time.
 *
 * ```KlangScript(Playable)
 * "c d".slowcatPrime("e f g").note()  // cycles through at absolute time
 * ```
 */
@KlangScript.Function
fun String.slowcatPrime(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).slowcatPrime(*patterns, callInfo = callInfo)

// -- polymeter() ------------------------------------------------------------------------------------------------------

private fun applyPolymeter(patterns: List<SprudelPattern>, baseSteps: Int? = null): SprudelPattern {
    if (patterns.isEmpty()) return silence

    // Filter for patterns that have steps defined
    val validPatterns = patterns.filter { it.numSteps != null }
    if (validPatterns.isEmpty()) return silence

    val patternSteps = validPatterns.mapNotNull { it.numSteps?.toInt() }
    val targetSteps = baseSteps ?: lcm(patternSteps).takeIf { it > 0 } ?: 4

    val adjustedPatterns = validPatterns.map { pat ->
        val steps = pat.numSteps!!.toInt()
        if (steps == targetSteps) {
            pat
        } else {
            pat.fast(targetSteps.toDouble() / steps)
        }
    }

    return PropertyOverridePattern(
        source = StackPattern(adjustedPatterns),
        stepsOverride = targetSteps.toDouble()
    )
}

/**
 * Aligns patterns with different step counts so they share a common cycle, creating polymeters.
 *
 * Patterns are sped up so that their LCM number of steps fits into one cycle. For example,
 * a 2-step and a 3-step pattern are both sped up to a 6-step cycle — the 2-step plays 3 times
 * and the 3-step plays 2 times per cycle, all in lockstep.
 *
 * @param patterns Patterns to align. Each must have a defined step count.
 * @return A pattern with all inputs aligned to their LCM step count per cycle
 *
 * ```KlangScript(Playable)
 * polymeter(note("c d"), note("c d e")).s("piano")  // 2- and 3-step in a 6-step cycle
 * ```
 *
 * ```KlangScript(Playable)
 * polymeter(s("bd sd"), s("hh hh hh"))  // 2-beat and 3-beat polyrhythm
 * ```
 * @category structural
 * @tags polymeter, rhythm, timing, alignment
 */
@KlangScript.Function
fun polymeter(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPolymeter(patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Prepends this pattern and aligns all patterns to a shared polymeter cycle.
 *
 * ```KlangScript(Playable)
 * note("c d").polymeter(note("c d e"))  // 2 and 3 steps aligned to LCM
 * ```
 */
@KlangScript.Function
fun SprudelPattern.polymeter(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPolymeter(listOf(this) + patterns.toList().asSprudelDslArgs(callInfo).toListOfPatterns())

/**
 * Parses this string as a pattern and aligns all patterns to a shared polymeter cycle.
 *
 * ```KlangScript(Playable)
 * "c d".polymeter("c d e").note()  // 2 and 3 steps aligned to LCM
 * ```
 */
@KlangScript.Function
fun String.polymeter(vararg patterns: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).polymeter(*patterns, callInfo = callInfo)

// -- polymeterSteps() -------------------------------------------------------------------------------------------------

/**
 * Like [polymeter], but with an explicit step count instead of using the LCM.
 *
 * All patterns are sped up or slowed down to fit exactly `steps` steps per cycle.
 *
 * @param args First argument is the target step count; remaining arguments are the patterns.
 * @return A pattern with all inputs adjusted to the given step count per cycle
 *
 * ```KlangScript(Playable)
 * polymeterSteps(4, note("c d"), note("c d e"))  // both fit into 4 steps per cycle
 * ```
 * @category structural
 * @tags polymeter, rhythm, timing, steps
 */
@KlangScript.Function
fun polymeterSteps(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    val argList = args.toList().asSprudelDslArgs(callInfo)
    val steps = argList.getOrNull(0)?.value?.asIntOrNull() ?: 4
    val patterns = argList.drop(1).toListOfPatterns()
    return applyPolymeter(patterns = patterns, baseSteps = steps)
}

