/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.math.CycleTimeSpan
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel._innerJoin
import io.peekandpoke.klang.sprudel._liftNumericField
import io.peekandpoke.klang.sprudel._outerJoin
import io.peekandpoke.klang.sprudel._withHapTime
import io.peekandpoke.klang.sprudel._withQueryTime
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.ControlValueProvider
import io.peekandpoke.klang.sprudel.pattern.FastGapPattern
import io.peekandpoke.klang.sprudel.pattern.TimeShiftPattern
import io.peekandpoke.klang.sprudel.withSteps

// Helpers /////////////////////////////////////////////////////////////////////////////////////////////////////////////

internal fun applyTimeShift(
    pattern: SprudelPattern,
    args: List<SprudelDslArg<Any?>>,
    factor: Double = 1.0,
): SprudelPattern {
    if (args.isEmpty()) return pattern

    val control = args[0].asControlValueProvider(0.0.asVoiceValue())

    return TimeShiftPattern(
        source = pattern,
        offsetProvider = control,
        factor = factor,
    )
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Tempo / Timing / Order modifiers
// ///

// -- slow() -----------------------------------------------------------------------------------------------------------

internal fun applySlow(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val factorArg = args.firstOrNull() ?: return pattern

    val result = pattern._innerJoin(factorArg) { src, factorVal ->
        val factor = factorVal?.asDouble ?: return@_innerJoin src
        if (factor == 0.0) return@_innerJoin silence
        val inverseFactor = 1.0 / factor

        src._withQueryTime { t -> t.scaleBy(inverseFactor) }
            ._withHapTime { t -> t.divBy(inverseFactor) }
            .withSteps(src.numSteps)
    }

    val staticFactor = factorArg.value?.asDoubleOrNull()

    if (staticFactor != null && staticFactor > 0.0) {
        return object : SprudelPattern by result {
            override fun estimateCycleDuration(): Double =
                pattern.estimateCycleDuration() * staticFactor
        }
    }

    return result
}

/**
 * Slows down a pattern by the given factor.
 *
 * `slow(2)` stretches the pattern so it takes 2 cycles to complete. Accepts control patterns for the factor.
 *
 * @param factor Slowdown multiplier. 2 = half speed. Default: 1 (no change). Typical range: 0.25–16. Inverse of fast().
 * @return A pattern slowed by `factor`.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").slow(2)              // half tempo — pattern spans 2 cycles
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").slow("<1 2 4>")            // varying slow factor each cycle
 * ```
 *
 * @category tempo
 * @tags slow, tempo, stretch, speed
 */
@KlangScript.Function
fun SprudelPattern.slow(factor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applySlow(this, listOf(factor).asSprudelDslArgs(callInfo))

/** Slows down this string pattern by the given factor. */
@KlangScript.Function
fun String.slow(factor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).slow(factor, callInfo)

/**
 * Returns a [PatternMapperFn] that slows down a pattern by the given factor.
 *
 * @param factor Slowdown multiplier. 2 = half speed. Default: 1 (no change). Typical range: 0.25–16.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").apply(slow(2))       // mapper form
 * ```
 *
 * @category tempo
 * @tags slow, tempo, stretch, speed
 */
@KlangScript.Function
fun slow(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.slow(factor, callInfo) }

/** Chains a slow operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.slow(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.slow(factor, callInfo) }

// -- fast() -----------------------------------------------------------------------------------------------------------

internal fun applyFast(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val factorArg = args.firstOrNull() ?: return pattern

    val result = pattern._innerJoin(factorArg) { src, factorVal ->
        val factor = factorVal?.asDouble ?: return@_innerJoin src
        if (factor == 0.0) return@_innerJoin silence

        src._withQueryTime { t -> t.scaleBy(factor) }
            ._withHapTime { t -> t.divBy(factor) }
            .withSteps(src.numSteps)
    }

    val staticFactor = factorArg.value?.asDoubleOrNull()

    if (staticFactor != null && staticFactor > 0.0) {
        return object : SprudelPattern by result {
            override fun estimateCycleDuration(): Double =
                pattern.estimateCycleDuration() / staticFactor
        }
    }

    return result
}

/**
 * Speeds up the pattern by the given factor.
 *
 * `fast(2)` plays the pattern twice per cycle. Accepts mini-notation strings and control patterns.
 *
 * @param factor Speed-up factor. Values > 1 play faster; values < 1 play slower.
 * @return A pattern sped up by `factor`.
 *
 * ```KlangScript(Playable)
 * note("c d e f").fast(2)           // 8 events per cycle instead of 4
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh").fast("<1 2 4>")     // varying speed each cycle
 * ```
 *
 * @category tempo
 * @tags fast, speed, tempo, accelerate
 */
@KlangScript.Function
fun SprudelPattern.fast(factor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyFast(this, listOf(factor).asSprudelDslArgs(callInfo))

/** Speeds up the mini-notation string pattern by `factor`. */
@KlangScript.Function
fun String.fast(factor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fast(factor, callInfo)

/**
 * Returns a [PatternMapperFn] that speeds up a pattern by the given factor.
 *
 * @param factor Speed-up multiplier. 2 = double speed (twice as many events per cycle). Default: 1 (no change). Typical range: 0.25–16.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh").apply(fast(2))      // mapper form
 * ```
 *
 * @category tempo
 * @tags fast, speed, tempo, accelerate
 */
@KlangScript.Function
fun fast(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.fast(factor, callInfo) }

/** Chains a fast operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.fast(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.fast(factor, callInfo) }

// -- hurry() ----------------------------------------------------------------------------------------------------------

private fun applyHurry(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val factorArg = args.firstOrNull() ?: return pattern
    // 1. fast(factor)
    val spedUp = applyFast(pattern, listOf(factorArg))
    // 2. speed = speed * factor
    return spedUp._liftNumericField(listOf(factorArg)) { factor ->
        val f = factor ?: 1.0
        val currentSpeed = speed ?: 1.0
        clone().also { it.speed = currentSpeed * f }
    }
}

/**
 * Speeds up the pattern like `fast()` and multiplies the `speed` audio parameter by the same factor.
 *
 * Unlike `fast()` which only changes the temporal density of events, `hurry()` also scales the
 * `speed` field (sample playback rate) so samples sound proportionally higher-pitched. This
 * mimics tape-speed acceleration. As a top-level function the second argument is the source pattern.
 *
 * @param factor Speed and pitch multiplier. 2 = double speed with pitch up one octave. Default: 1 (no change). Typical range: 0.25–16.
 * @return A pattern sped up in both timing and sample playback rate.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh").hurry(2)              // twice as fast and samples pitch up by an octave
 * ```
 *
 * ```KlangScript(Playable)
 * s("bass:1").speed(0.5).hurry(2)     // existing speed 0.5 × hurry 2 = speed 1.0
 * ```
 *
 * @category tempo
 * @tags hurry, fast, speed, pitch, accelerate
 */
@KlangScript.Function
fun SprudelPattern.hurry(factor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyHurry(this, listOf(factor).asSprudelDslArgs(callInfo))

/** Speeds up pattern and multiplies the `speed` audio parameter by the same factor. */
@KlangScript.Function
fun String.hurry(factor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hurry(factor, callInfo)

/** Returns a [PatternMapperFn] that speeds up a pattern and multiplies the `speed` parameter. */
@KlangScript.Function
fun hurry(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.hurry(factor, callInfo) }

/** Chains a hurry operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.hurry(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.hurry(factor, callInfo) }

// -- fastGap() --------------------------------------------------------------------------------------------------------

internal fun applyFastGap(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.isEmpty()) {
        return pattern
    }

    val factorProvider: ControlValueProvider =
        args.firstOrNull().asControlValueProvider(1.0.asVoiceValue())

    return FastGapPattern(source = pattern, factorProvider = factorProvider)
}

/**
 * Speeds up the pattern by `factor` but plays it only once per cycle, leaving a gap.
 *
 * Unlike `fast(n)` which tiles the pattern `n` times to fill the cycle, `fastGap(n)` compresses
 * the pattern into the first `1/n` of the cycle and leaves silence in the remaining space. As a
 * top-level function the second argument is the source pattern.
 *
 * @param factor Compression factor. 2 = play in first half, silence in second. Default: 1 (full cycle). Typical range: 1–8.
 * @return A pattern compressed into the first `1/factor` of each cycle with silence after.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").fastGap(2)         // 4 events in first half, silence in second half
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f g").fastGap(4)        // all events squeezed into first quarter
 * ```
 *
 * @alias densityGap
 * @category tempo
 * @tags fastGap, fast, gap, silence, compress, density
 */
@KlangScript.Function
fun SprudelPattern.fastGap(factor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyFastGap(this, listOf(factor).asSprudelDslArgs(callInfo))

/** Speeds up the pattern but plays it only once per cycle, leaving a gap. */
@KlangScript.Function
fun String.fastGap(factor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fastGap(factor, callInfo)

/** Returns a [PatternMapperFn] that speeds up a pattern but plays it only once per cycle. */
@KlangScript.Function
fun fastGap(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.fastGap(factor, callInfo) }

/** Chains a fastGap operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.fastGap(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.fastGap(factor, callInfo) }

/** Alias for [fastGap]. */
@KlangScript.Function
fun SprudelPattern.densityGap(factor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.fastGap(factor, callInfo)

/** Alias for [fastGap]. */
@KlangScript.Function
fun String.densityGap(factor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).densityGap(factor, callInfo)

/** Returns a [PatternMapperFn] that is an alias for [fastGap]. */
@KlangScript.Function
fun densityGap(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    fastGap(factor, callInfo)

/** Alias for [PatternMapperFn.fastGap]. */
@KlangScript.Function
fun PatternMapperFn.densityGap(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.fastGap(factor, callInfo)

// -- stretchBy() ------------------------------------------------------------------------------------------------------

private fun applyStretchBy(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.isEmpty()) return pattern

    val control = args.toPattern(voiceValueModifier)

    return pattern._outerJoin(control) { sourceEvent, controlEvent ->
        val factor = controlEvent?.data?.value?.asDouble ?: 1.0

        val newPart = sourceEvent.part
            .let { CycleTimeSpan(begin = it.begin, end = it.begin + it.duration.scaleBy(factor)) }

        val newWhole = sourceEvent.whole
            .let { CycleTimeSpan(begin = it.begin, end = it.begin + it.duration.scaleBy(factor)) }

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
 * @tags stretchBy, duration, stretch, event length
 */
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
 * @tags stretchBy, duration, stretch, event length
 */
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
@KlangScript.Function
fun PatternMapperFn.stretchBy(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.stretchBy(factor, callInfo) }
