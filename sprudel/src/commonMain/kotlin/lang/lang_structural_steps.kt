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
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel._stepJoin
import io.peekandpoke.klang.sprudel._withHapTime
import io.peekandpoke.klang.sprudel._withQueryTime
import io.peekandpoke.klang.sprudel.createSprudelVoiceData
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern
import io.peekandpoke.klang.sprudel.pattern.ControlValueProvider
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
import io.peekandpoke.klang.sprudel.pattern.SegmentPattern
import io.peekandpoke.klang.sprudel.withSteps

// -- segment() --------------------------------------------------------------------------------------------------------

private fun applySegment(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val nArg = args.firstOrNull()

    val nPattern: SprudelPattern = when (val nVal = nArg?.value) {
        is SprudelPattern -> nVal

        else -> parseMiniNotation(nArg ?: SprudelDslArg.of("1")) { text, _ ->
            AtomicPattern(createSprudelVoiceData().voiceValueModifier(text))
        }
    }

    // One implementation for static and patterned n. (A `struct("x".fast(n))` path for static n
    // existed until 2026-09-07; it was dead, and it read a continuous source at the query start
    // rather than at the slice start, which SegmentPattern does on purpose.)
    return SegmentPattern.control(source, nPattern)
}

/**
 * Samples the pattern at a rate of `n` events per cycle.
 *
 * Useful for turning a continuous pattern (e.g. from a signal) into a discrete stepped one.
 *
 * @param n Number of segments per cycle. Can be an integer or a mini-notation string.
 * @return A discrete pattern with `n` evenly-spaced samples per cycle.
 *
 * ```KlangScript(Playable)
 * note(saw.range(40, 52).segment(24))  // smooth saw wave sampled at 24 steps
 * ```
 *
 * ```KlangScript(Playable)
 * note(sine.range(48, 60).segment(8))  // sine wave at 8 steps per cycle
 * ```
 *
 * @alias seg
 * @category structural
 * @tags segment, seg, sample, discrete, quantize
 */
@KlangScript.Function
fun SprudelPattern.segment(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applySegment(this, listOf(n).asSprudelDslArgs(callInfo))

/**
 * Like [segment] applied to a mini-notation string.
 *
 * @param n Number of segments per cycle. Can be an integer or a mini-notation string.
 * @return A discrete pattern with `n` evenly-spaced samples per cycle.
 *
 * ```KlangScript(Playable)
 * "0".segment(4).note()  // four evenly-spaced notes per cycle
 * ```
 *
 * @alias seg
 * @category structural
 * @tags segment, seg, sample, discrete, quantize
 */
@KlangScript.Function
fun String.segment(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).segment(n, callInfo)

/**
 * Returns a [PatternMapperFn] that samples the source pattern at `n` events per cycle.
 *
 * @param n Number of segments per cycle. Can be an integer or a mini-notation string.
 * @return A [PatternMapperFn] that discretises the source into `n` evenly-spaced samples.
 *
 * ```KlangScript(Playable)
 * sine.range(40, 60).apply(segment(8)).note()  // via mapper
 * ```
 *
 * @alias seg
 * @category structural
 * @tags segment, seg, sample, discrete, quantize
 */
@KlangScript.Function
fun segment(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.segment(n, callInfo) }

/** Chains a segment onto this [PatternMapperFn]; samples the result at `n` events per cycle. */
@KlangScript.Function
fun PatternMapperFn.segment(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.segment(n, callInfo) }

/**
 * Alias for [segment] — samples the pattern at a rate of `n` events per cycle.
 *
 * @param n Number of segments per cycle.
 * @return A discrete pattern with `n` evenly-spaced samples per cycle.
 *
 * ```KlangScript(Playable)
 * note(saw.range(40, 52).seg(24))  // smooth saw wave sampled at 24 steps
 * ```
 *
 * ```KlangScript(Playable)
 * note(sine.range(48, 60).seg(8))  // sine wave at 8 steps per cycle
 * ```
 *
 * @alias segment
 * @category structural
 * @tags seg, segment, sample, discrete, quantize
 */
@KlangScript.Function
fun SprudelPattern.seg(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.segment(n, callInfo)

/**
 * Alias for [segment] applied to a mini-notation string.
 *
 * @param n Number of segments per cycle.
 * @return A discrete pattern with `n` evenly-spaced samples per cycle.
 *
 * ```KlangScript(Playable)
 * "0".seg(4).note()  // four evenly-spaced notes per cycle
 * ```
 *
 * @alias segment
 * @category structural
 * @tags seg, segment, sample, discrete, quantize
 */
@KlangScript.Function
fun String.seg(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).seg(n, callInfo)

/**
 * Returns a [PatternMapperFn] that is an alias for [segment] — samples the source at `n` events per cycle.
 *
 * @param n Number of segments per cycle.
 * @return A [PatternMapperFn] that discretises the source into `n` evenly-spaced samples.
 *
 * ```KlangScript(Playable)
 * sine.range(40, 60).apply(seg(8)).note()  // via mapper
 * ```
 *
 * @alias segment
 * @category structural
 * @tags seg, segment, sample, discrete, quantize
 */
@KlangScript.Function
fun seg(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.seg(n, callInfo) }

/** Chains a seg onto this [PatternMapperFn]; alias for [PatternMapperFn.segment]. */
@KlangScript.Function
fun PatternMapperFn.seg(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.seg(n, callInfo) }

// -- ratio ------------------------------------------------------------------------------------------------------------

private val ratioMutation = voiceSetter { inputValue ->
    // Parse colon notation like "5:4" into a ratio
    // Convert to string first to handle both string and numeric inputs
    val parts = inputValue?.toString()?.split(":") ?: emptyList()

    val ratioValue = if (parts.size > 1) {
        // Parse all parts as numbers and divide them: "5:4" -> 5/4 = 1.25
        // Guard: if any divisor is zero, the fold produces NaN/Infinity → takeIf discards it
        val numbers = parts.mapNotNull { it.toDoubleOrNull() }
        if (numbers.isNotEmpty()) {
            numbers.drop(1).fold(numbers[0]) { acc, divisor -> acc / divisor }.takeIf { it.isFinite() }
        } else {
            null
        }
    } else {
        // Single value without colon, try to parse as number
        inputValue?.asDoubleOrNull()
    }

    value = ratioValue?.asVoiceValue()
}

/**
 * Parses colon-separated ratios into numbers: `"5:4"` → 1.25, `"3:2"` → 1.5, `"12:3:2"` → 2.0.
 *
 * Useful for specifying tuning ratios or rhythmic proportions using familiar ratio notation.
 *
 * @param values One or more ratio strings or numbers to convert.
 * @return A pattern of the computed ratio values.
 *
 * ```KlangScript(Playable)
 * ratio("5:4", "3:2", "2:1").note()  // major third, fifth, octave as ratios
 * ```
 *
 * ```KlangScript(Playable)
 * ratio("3:2").note()  // perfect fifth ratio
 * ```
 * @category structural
 * @tags ratio, tuning, fraction, colon, notation
 */
@KlangScript.Function
fun ratio(vararg values: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    values.toList().asSprudelDslArgs(callInfo).toPattern(ratioMutation)

/** Converts colon-ratio notation in the pattern's values to numbers. */
@KlangScript.Function
fun SprudelPattern.ratio(@Suppress("unused") callInfo: CallInfo? = null): SprudelPattern =
    this.reinterpretVoice { it.ratioMutation(it.value?.asString) }

/** Converts colon-ratio notation in the mini-notation string to numbers. */
@KlangScript.Function
fun String.ratio(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).ratio(callInfo)

// -- pace() / steps() -------------------------------------------------------------------------------------------------

private fun applyPace(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val targetSteps = args.firstOrNull()?.value?.asDoubleOrNull() ?: 1.0
    val currentSteps = source.numSteps ?: 1.0

    if (targetSteps <= 0.0 || currentSteps <= 0.0) {
        return source
    }

    // Calculate speed adjustment: fast(targetSteps / currentSteps)
    val speedFactor = targetSteps / currentSteps

    return source.fast(speedFactor)
}

/**
 * Adjusts this pattern's speed so it plays exactly `n` steps per cycle.
 *
 * Computes the speed factor relative to the pattern's natural step count and applies [fast].
 *
 * @param n Target number of steps per cycle.
 * @return The pattern sped up or slowed down to fit `n` steps per cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").pace(8)   // 4-step pattern runs at 8 steps/cycle (double speed)
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f g").pace(4) // 5-step pattern runs at 4 steps/cycle
 * ```
 *
 * @alias steps
 * @category structural
 * @tags pace, steps, tempo, speed, cycle
 */
@KlangScript.Function
fun SprudelPattern.pace(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPace(this, listOf(n).asSprudelDslArgs(callInfo))

/** Adjusts this string pattern to play `n` steps per cycle. */
@KlangScript.Function
fun String.pace(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pace(n, callInfo)

/**
 * Returns a [PatternMapperFn] that adjusts the source to play `n` steps per cycle.
 *
 * @param n Target number of steps per cycle.
 * @return A [PatternMapperFn] that speeds up or slows down the source to fit `n` steps.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(pace(8))   // via mapper
 * ```
 *
 * @alias steps
 * @category structural
 * @tags pace, steps, tempo, speed, cycle
 */
@KlangScript.Function
fun pace(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pace(n, callInfo) }

/** Chains a pace onto this [PatternMapperFn]; adjusts to play `n` steps per cycle. */
@KlangScript.Function
fun PatternMapperFn.pace(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pace(n, callInfo) }

/**
 * Alias for [pace] — adjusts this pattern's speed so it plays `n` steps per cycle.
 *
 * @param n Target number of steps per cycle.
 * @return The pattern sped up or slowed down to fit `n` steps per cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").steps(8)   // 4-step pattern runs at 8 steps/cycle
 * ```
 *
 * @alias pace
 * @category structural
 * @tags steps, pace, tempo, speed, cycle
 */
@KlangScript.Function
fun SprudelPattern.steps(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.pace(n, callInfo)

/** Alias for [pace] on a string pattern. */
@KlangScript.Function
fun String.steps(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).steps(n, callInfo)

/** Returns a [PatternMapperFn] — alias for [pace] — that adjusts to play `n` steps per cycle. */
@KlangScript.Function
fun steps(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.steps(n, callInfo) }

/** Chains a steps (alias for [pace]) onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.steps(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.steps(n, callInfo) }

// -- take() -----------------------------------------------------------------------------------------------------------

private fun applyTake(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val takeArg = args.firstOrNull() ?: return source

    val control: ControlValueProvider = takeArg.asControlValueProvider(1.0.asVoiceValue())

    val takePattern = when (control) {
        is ControlValueProvider.Static -> AtomicPattern(createSprudelVoiceData { value = control.value })
        is ControlValueProvider.Pattern -> control.pattern
    }

    return takePattern._stepJoin { event ->
        val n = event.data.value?.asDouble ?: return@_stepJoin null
        val steps = source.numSteps

        if (steps != null && steps > 0.0) {
            val end = n / steps

            if (end <= 0.0) return@_stepJoin silence
            if (end >= 1.0) return@_stepJoin source

            // Take(n) keeps first n steps.
            // Zoom window [0, end] to [0, 1]
            source._withQueryTime { t -> t.scaleBy(end) }
                ._withHapTime { t -> t.divBy(end) }
                .withSteps(n)
        } else {
            silence
        }
    }
}

/**
 * Keeps only the first `n` steps of this pattern, stretched to fill the cycle.
 *
 * @param n Number of steps to keep from the start.
 * @return A pattern containing only the first `n` steps, stretched to fill one cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").take(2)  // keeps "c d", stretched to fill the cycle
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").take(3)  // keeps first 3 sounds
 * ```
 *
 * @category structural
 * @tags take, slice, truncate, steps, cycle
 */
@KlangScript.Function
fun SprudelPattern.take(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyTake(this, listOf(n).asSprudelDslArgs(callInfo))

/** Keeps the first `n` steps of this string pattern, stretched to fill the cycle. */
@KlangScript.Function
fun String.take(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).take(n, callInfo)

/**
 * Returns a [PatternMapperFn] that keeps only the first `n` steps of the source.
 *
 * @param n Number of steps to keep from the start.
 * @return A [PatternMapperFn] that truncates the source to `n` steps.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(take(2))  // via mapper
 * ```
 *
 * @category structural
 * @tags take, slice, truncate, steps, cycle
 */
@KlangScript.Function
fun take(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.take(n, callInfo) }

/** Chains a take onto this [PatternMapperFn]; keeps only the first `n` steps. */
@KlangScript.Function
fun PatternMapperFn.take(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.take(n, callInfo) }

// -- drop() -----------------------------------------------------------------------------------------------------------

private fun applyDrop(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val dropArg = args.firstOrNull() ?: return source

    val control: ControlValueProvider = dropArg.asControlValueProvider(0.0.asVoiceValue())

    val dropPattern = when (control) {
        is ControlValueProvider.Static -> AtomicPattern(createSprudelVoiceData { value = control.value })
        is ControlValueProvider.Pattern -> control.pattern
    }

    return dropPattern._stepJoin { event ->
        val n = event.data.value?.asDouble ?: return@_stepJoin null
        val steps = source.numSteps

        if (steps != null && steps > 0.0) {
            if (n > 0.0) {
                // drop from start: zoom(n/steps, 1)
                val start = n / steps
                if (start >= 1.0) return@_stepJoin silence
                // Zoom window [start, 1] to [0, 1]
                // Map query t in [0, 1] to [start, 1] -> t' = start + t * (1 - start)
                val durationCycles = (1.0 - start)
                val startTime = CycleTime.ofCycles(start)

                source
                    ._withQueryTime { t -> startTime + t.scaleBy(durationCycles) }
                    ._withHapTime { t -> (t - startTime).divBy(durationCycles) }
                    .withSteps(steps - n)
            } else {
                // drop from end: zoom(0, (steps+n)/steps)
                // n is negative
                val end = (steps + n) / steps
                if (end <= 0.0) return@_stepJoin silence

                // Zoom window [0, end] to [0, 1]
                // Map query t in [0, 1] to [0, end] -> t' = t * end
                source._withQueryTime { t -> t.scaleBy(end) }
                    ._withHapTime { t -> t.divBy(end) }
                    .withSteps(steps + n)
            }
        } else {
            silence
        }
    }
}

/**
 * Skips the first `n` steps of this pattern and stretches the remainder to fill the cycle.
 *
 * Use a negative `n` to drop from the end instead.
 *
 * @param n Number of steps to skip from the start (negative = skip from end).
 * @return A pattern with the first `n` steps removed, stretched to fill one cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").drop(1)  // drops "c", plays "d e f" stretched
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").drop(2)  // drops "bd sd", plays "hh cp" stretched
 * ```
 *
 * @category structural
 * @tags drop, skip, slice, steps, cycle
 */
@KlangScript.Function
fun SprudelPattern.drop(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyDrop(this, listOf(n).asSprudelDslArgs(callInfo))

/** Skips the first `n` steps of this string pattern, stretched to fill the cycle. */
@KlangScript.Function
fun String.drop(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).drop(n, callInfo)

/**
 * Returns a [PatternMapperFn] that skips the first `n` steps of the source.
 *
 * @param n Number of steps to skip from the start.
 * @return A [PatternMapperFn] that drops the first `n` steps of the source.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(drop(1))  // via mapper
 * ```
 *
 * @category structural
 * @tags drop, skip, slice, steps, cycle
 */
@KlangScript.Function
fun drop(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.drop(n, callInfo) }

/** Chains a drop onto this [PatternMapperFn]; skips the first `n` steps. */
@KlangScript.Function
fun PatternMapperFn.drop(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.drop(n, callInfo) }

