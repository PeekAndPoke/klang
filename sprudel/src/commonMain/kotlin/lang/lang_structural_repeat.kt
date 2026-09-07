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
import io.peekandpoke.klang.sprudel.createSprudelVoiceData
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern
import io.peekandpoke.klang.sprudel.pattern.RepeatCyclesPattern
import kotlin.math.floor

// -- repeatCycles() ---------------------------------------------------------------------------------------------------

private fun applyRepeatCycles(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val repsArg = args.firstOrNull()
    val repsVal = repsArg?.value

    val repsPattern: SprudelPattern = when (repsVal) {
        is SprudelPattern -> repsVal
        else -> parseMiniNotation(repsArg ?: SprudelDslArg.of("1")) { text, _ ->
            AtomicPattern(createSprudelVoiceData().voiceValueModifier(text))
        }
    }

    val staticReps = repsVal?.asDoubleOrNull()

    return if (staticReps != null) {
        RepeatCyclesPattern(source, staticReps)
    } else {
        RepeatCyclesPattern.control(source, repsPattern)
    }
}

/**
 * Repeats each cycle of this pattern `n` times before advancing.
 *
 * Cycle 0 plays `n` times, then cycle 1 plays `n` times, and so on. Supports control patterns.
 *
 * @param n Number of times to repeat each cycle.
 * @return A pattern where each cycle is repeated `n` times.
 *
 * ```KlangScript(Playable)
 * note("c d e f").repeatCycles(3)    // each cycle repeats 3 times
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").repeatCycles("<1 2 4>") // varying repetitions each cycle
 * ```
 *
 * @category structural
 * @tags repeatCycles, repeat, cycle, loop, stutter
 */
@KlangScript.Function
fun SprudelPattern.repeatCycles(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyRepeatCycles(this, listOf(n).asSprudelDslArgs(callInfo))

/** Repeats each cycle of this string pattern `n` times. */
@KlangScript.Function
fun String.repeatCycles(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).repeatCycles(n, callInfo)

/**
 * Returns a [PatternMapperFn] that repeats each cycle of the source `n` times.
 *
 * @param n Number of times to repeat each cycle.
 * @return A [PatternMapperFn] that repeats cycles.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(repeatCycles(3))  // via mapper
 * ```
 *
 * @category structural
 * @tags repeatCycles, repeat, cycle, loop, stutter
 */
@KlangScript.Function
fun repeatCycles(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.repeatCycles(n, callInfo) }

/** Chains a repeatCycles onto this [PatternMapperFn]; repeats each cycle `n` times. */
@KlangScript.Function
fun PatternMapperFn.repeatCycles(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.repeatCycles(n, callInfo) }

// -- extend() ---------------------------------------------------------------------------------------------------------

/**
 * Speeds up this pattern by the given factor — alias for [fast].
 *
 * `extend(2)` is identical to `fast(2)`: events play twice as fast.
 *
 * @param factor Speed-up factor. Values > 1 play faster; values < 1 play slower.
 * @return A pattern sped up by `factor`.
 *
 * ```KlangScript(Playable)
 * note("c d e f").extend(2)      // 8 events per cycle instead of 4
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh").extend("<1 2 4>") // varying speed each cycle
 * ```
 *
 * @alias fast
 * @category structural
 * @tags extend, fast, speed, tempo, accelerate
 */
@KlangScript.Function
fun SprudelPattern.extend(factor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyFast(this, listOf(factor).asSprudelDslArgs(callInfo))

/** Speeds up this string pattern by `factor` — alias for [fast]. */
@KlangScript.Function
fun String.extend(factor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).extend(factor, callInfo)

/**
 * Returns a [PatternMapperFn] that speeds up the source by `factor` — alias for [fast].
 *
 * @param factor Speed-up factor.
 * @return A [PatternMapperFn] that speeds up the source.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(extend(2))  // via mapper
 * ```
 *
 * @alias fast
 * @category structural
 * @tags extend, fast, speed, tempo, accelerate
 */
@KlangScript.Function
fun extend(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.extend(factor, callInfo) }

/** Chains an extend (alias for [fast]) onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.extend(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.extend(factor, callInfo) }

// -- timeLoop() -------------------------------------------------------------------------------------------------------

/**
 * Core implementation: loops this pattern within the given duration in cycles.
 * Effectively repeats the pattern segment `[0, duration]` every `duration` cycles.
 */
fun SprudelPattern.timeLoop(duration: Double): SprudelPattern {
    if (duration <= 0.0) return silence

    val source = this
    return object : SprudelPattern {
        override val weight: Double get() = source.weight
        override val numSteps: Double? get() = source.numSteps
        override fun estimateCycleDuration(): Double = duration

        override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> {
            val result = mutableListOf<SprudelPatternEvent>()

            // Calculate loop range covering [from, to]
            val durationCycles = duration
            val durationSpan = CycleTime.ofCycles(durationCycles)

            // Loop through cycles
            var k = floor(from.toCycles() / durationCycles).toInt()
            while (CycleTime.ofCycles(k * durationCycles) < to) {
                val loopStart = CycleTime.ofCycles(k * durationCycles)

                // Intersection of query with this loop
                val qStart = from.coerceAtLeast(loopStart)
                val qEnd = to.coerceAtMost(loopStart + durationSpan)

                if (qStart < qEnd) {
                    // Map to local time [0, duration]
                    val localStart = qStart - loopStart
                    val localEnd = qEnd - loopStart

                    // Query source at [localStart, localEnd]
                    val events = source.queryArcContextual(localStart, localEnd, ctx)

                    // Shift events back to global time
                    for (ev in events) {
                        result.add(
                            ev.copy(
                                part = ev.part.shift(loopStart),
                                whole = ev.whole.shift(loopStart)
                            )
                        )
                    }
                }

                k++
            }

            return result
        }
    }
}

private fun applyTimeLoop(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val duration = args.firstOrNull()?.value?.asDoubleOrNull() ?: return source
    return source.timeLoop(duration)
}

/**
 * Loops this pattern within a fixed window of `duration` cycles, tiling it indefinitely.
 *
 * Unlike [fast] or [slow], `timeLoop` does not stretch or compress events — it freezes the
 * segment `[0, duration]` of this pattern and tiles it. Events outside the window are never
 * played. Useful for creating ostinato figures or locking a long sequence to a shorter loop.
 *
 * ```KlangScript(Playable)
 * note("c3 d3 e3 f3 g3 a3 b3 c4").timeLoop(2)   // loop first 2 cycles of an 8-note run
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh oh").timeLoop(0.5)                 // stutter a 4-beat pattern into 2-beat loops
 * ```
 *
 * @param duration The loop window length in cycles. Must be greater than zero.
 *
 * @category structural
 * @tags timeLoop, loop, repeat, cycle, ostinato, window
 */
@KlangScript.Function
fun SprudelPattern.timeLoop(duration: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyTimeLoop(this, listOf(duration).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and loops it within a fixed window of `duration` cycles.
 *
 * ```KlangScript(Playable)
 * "c3 d3 e3 f3".timeLoop(0.5)   // loop the first half-cycle of a 4-note sequence
 * ```
 *
 * @param duration The loop window length in cycles. Must be greater than zero.
 */
@KlangScript.Function
fun String.timeLoop(duration: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).timeLoop(duration, callInfo)

/**
 * Creates a [PatternMapperFn] that loops its input within a fixed window of `duration` cycles.
 *
 * ```KlangScript(Playable)
 * note("c3 d3 e3 f3 g3 a3 b3 c4").apply(timeLoop(2))   // loop the first 2 cycles of an 8-note run
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh oh").apply(timeLoop(0.5))                 // stutter a 4-beat pattern into 2-beat loops
 * ```
 *
 * @param duration The loop window length in cycles. Must be greater than zero.
 *
 * @category structural
 * @tags timeLoop, loop, repeat, cycle, ostinato, window
 */
@KlangScript.Function
fun timeLoop(duration: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.timeLoop(duration, callInfo) }

/**
 * Chains a timeLoop operation onto this [PatternMapperFn], looping the result within `duration` cycles.
 *
 * ```KlangScript(Playable)
 * seq("0.2 0.4").apply(mul(2).timeLoop(0.5))   // mul doubles, then loop within 0.5 cycles
 * ```
 *
 * @param duration The loop window length in cycles. Must be greater than zero.
 */
@KlangScript.Function
fun PatternMapperFn.timeLoop(duration: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.timeLoop(duration, callInfo) }

// -- repeat() ---------------------------------------------------------------------------------------------------------

private fun applyRepeat(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val times = args.firstOrNull()?.value?.asIntOrNull() ?: 1
    if (times <= 0) return silence
    if (times == 1) return pattern
    val patterns = List(times) { pattern }
    return applyCat(patterns)
}

/**
 * Repeats this pattern `times` times sequentially.
 *
 * The total duration becomes `times × original_duration`. Unlike [fast], which compresses events
 * into fewer cycles, each repetition occupies its own full cycle. Useful for extending a short
 * pattern to fill multiple bars before it loops.
 *
 * ```KlangScript(Playable)
 * note("a b").repeat(2)              // plays "a b a b" spread over 2 cycles
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").repeat(4)         // loop a 4-beat bar four times before cycling
 * ```
 *
 * @param times The number of times to repeat. `0` returns silence; `1` returns the pattern unchanged.
 *
 * @category structural
 * @tags repeat, loop, duplicate, sequence
 */
@KlangScript.Function
fun SprudelPattern.repeat(times: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyRepeat(this, listOf(times).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and repeats it `times` times sequentially.
 *
 * ```KlangScript(Playable)
 * "a b".repeat(3).note()             // plays "a b a b a b" spread over 3 cycles
 * ```
 *
 * ```KlangScript(Playable)
 * "bd sd".repeat(2).s()              // double-length drum bar
 * ```
 *
 * @param times The number of times to repeat. `0` returns silence; `1` returns the pattern unchanged.
 */
@KlangScript.Function
fun String.repeat(times: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).repeat(times, callInfo)

/**
 * Creates a [PatternMapperFn] that repeats the input pattern `times` times sequentially.
 *
 * ```KlangScript(Playable)
 * note("a b").apply(repeat(2))       // plays "a b a b" spread over 2 cycles
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(repeat(3))        // triple-length drum bar via mapper
 * ```
 *
 * @param times The number of times to repeat. `0` returns silence; `1` returns the pattern unchanged.
 *
 * @category structural
 * @tags repeat, loop, duplicate, sequence
 */
@KlangScript.Function
fun repeat(times: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.repeat(times, callInfo) }

/**
 * Chains a repeat operation onto this [PatternMapperFn], repeating the result `times` times.
 *
 * ```KlangScript(Playable)
 * note("a b").apply(fast(2).repeat(2))   // fast doubles density, repeat duplicates over 2 cycles
 * ```
 *
 * @param times The number of times to repeat. `0` returns silence; `1` returns the pattern unchanged.
 */
@KlangScript.Function
fun PatternMapperFn.repeat(times: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.repeat(times, callInfo) }

