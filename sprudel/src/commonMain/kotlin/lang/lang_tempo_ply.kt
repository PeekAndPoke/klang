/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._bindSqueeze
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.AtomicInfinitePattern
import io.peekandpoke.klang.sprudel.pattern.SequencePattern
import io.peekandpoke.klang.sprudel.withSteps

// -- ply() ------------------------------------------------------------------------------------------------------------

private fun applyPly(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.isEmpty()) {
        return pattern
    }

    val factorArg = args[0]

    // Calculate new steps if factor is purely static
    val staticFactor = factorArg.value?.asDoubleOrNull()
    val newSteps = if (staticFactor != null) pattern.numSteps?.times(staticFactor) else null

    // Convert factor to pattern (supports static values and control patterns)
    val factorPattern = factorArg.toPattern()

    val result = pattern._bindSqueeze { event ->
        // pure(x) -> infinite pattern of the event's data
        val infiniteAtom = AtomicInfinitePattern(event.data)

        // To support "Patterned Ply" (Tidal style) where the factor pattern is aligned with the cycle,
        // we must project the global factor pattern into the event's local timeframe.
        // factorPattern.zoom(begin, end) takes the slice of factor corresponding to the event
        // and stretches it to 0..1, which matches the squeezed context.
        val localFactor = if (staticFactor == null) {
            factorPattern.zoom(event.whole.begin.toCycles(), event.whole.end.toCycles())
        } else {
            factorPattern
        }

        // ._fast(factor)
        infiniteAtom.fast(localFactor)
    }

    return if (newSteps != null) result.withSteps(newSteps) else result
}

/**
 * Repeats each event `n` times within its original timespan.
 *
 * Each event in the pattern is subdivided into `n` equal copies squeezed into the same
 * duration. For example, `ply(3)` on a 2-event pattern produces 6 events: 3 copies of the
 * first event followed by 3 copies of the second. Accepts control patterns for `n`.
 *
 * @param n Number of repetitions per event. Default: 1 (no repetition). Typical range: 1–16. Accepts control patterns.
 * @return A pattern with each event repeated `n` times within its timespan.
 *
 * ```KlangScript(Playable)
 * note("c d").ply(3)                  // c c c d d d — 6 events squeezed into 1 cycle
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").ply("<1 2 4>")      // varying subdivision each cycle
 * ```
 *
 * @category tempo
 * @tags ply, repeat, subdivide, multiply, density
 */
@KlangScript.Function
fun SprudelPattern.ply(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPly(this, listOf(n).asSprudelDslArgs(callInfo))

/** Repeats each event `n` times within its original timespan. */
@KlangScript.Function
fun String.ply(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).ply(n, callInfo)

/**
 * Returns a [PatternMapperFn] that repeats each event `n` times within its timespan.
 *
 * @param n Number of repetitions per event. Default: 1. Typical range: 1–16.
 *
 * ```KlangScript(Playable)
 * note("c d").apply(ply(3))           // mapper form
 * ```
 *
 * @category tempo
 * @tags ply, repeat, subdivide, multiply, density
 */
@KlangScript.Function
fun ply(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.ply(n, callInfo) }

/** Chains a ply operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.ply(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.ply(n, callInfo) }

// -- plyWith() --------------------------------------------------------------------------------------------------------

/**
 * Helper function to apply a function n times to a value.
 * Applies the transform n times in succession (the applyN concept).
 */
private fun applyFunctionNTimes(n: Int, func: PatternMapperFn, pattern: SprudelPattern): SprudelPattern {
    var result = pattern
    repeat(n) {
        result = func(result)
    }
    return result
}

private fun applyPlyWith(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) {
        return pattern
    }

    val factorArg = args[0]
    val funcArg = args[1]
    val func = funcArg.toPatternMapper() ?: return pattern

    // Calculate new steps if factor is purely static
    val staticFactor = factorArg.value?.asDoubleOrNull()?.toInt()

    val newSteps = if (staticFactor != null) {
        pattern.numSteps?.times(staticFactor.toDouble())
    } else {
        null
    }

    val result = pattern._bindSqueeze { event ->
        val factor = staticFactor ?: event.data.value?.asDouble?.toInt() ?: 1

        if (factor <= 0) {
            return@_bindSqueeze null
        }

        // Create factor number of patterns, applying func 0, 1, 2, ... (factor-1) times
        val patterns = (0 until factor).map { i ->
            val atomPattern = AtomicInfinitePattern(event.data)
            applyFunctionNTimes(i, func, atomPattern)
        }

        // Concatenate all patterns - SequencePattern squashes them into one cycle
        // _bindSqueeze will squeeze this into the event's timespan
        if (patterns.size == 1) patterns.first() else SequencePattern(patterns)
    }

    return if (newSteps != null) result.withSteps(newSteps) else result
}

/**
 * Repeats each event `n` times within its timespan, applying `transform` cumulatively.
 *
 * Like `ply(n)` but instead of plain copies, each repetition applies `transform` one more
 * time: copy 0 is unmodified, copy 1 has `transform` applied once, copy 2 twice, and so on.
 * This creates escalating variations within each event's slot.
 *
 * @param factor Number of repetitions per event slot. Typical range: 1–16.
 * @param transform Pattern transformation applied cumulatively — copy 0 is unmodified, copy 1 has it applied once, etc.
 * @return A pattern with `n` progressively transformed copies of each event per slot.
 *
 * ```KlangScript(Playable)
 * note("c").plyWith(4, x => x.add(7))   // c, g, d5, a5 — each copy adds 7 semitones more
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").plyWith(3, x => x.fast(2))    // original, then 2x speed, then 4x speed in same slot
 * ```
 *
 * @alias plywith
 * @category tempo
 * @tags plyWith, repeat, transform, cumulative, subdivide
 */
@KlangScript.Function
fun SprudelPattern.plyWith(factor: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyPlyWith(this, listOf(factor, transform).asSprudelDslArgs(callInfo))

/** Repeats each event `n` times, applying `transform` cumulatively (0, 1, 2 … times). */
@KlangScript.Function
fun String.plyWith(factor: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).plyWith(factor, transform, callInfo)

/**
 * Returns a [PatternMapperFn] that repeats each event `n` times, applying `transform` cumulatively.
 *
 * @param factor Number of repetitions per event slot.
 * @param transform Pattern transformation applied cumulatively per copy.
 *
 * ```KlangScript(Playable)
 * note("c").apply(plyWith(4, x => x.add(7)))   // mapper form
 * ```
 *
 * @alias plywith
 * @category tempo
 * @tags plyWith, repeat, transform, cumulative, subdivide
 */
@KlangScript.Function
fun plyWith(factor: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.plyWith(factor, transform, callInfo) }

/** Chains a plyWith operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.plyWith(factor: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.plyWith(factor, transform, callInfo) }

/**
 * Alias for `plyWith`.
 *
 * @param factor Number of repetitions per event slot.
 * @param transform Pattern transformation applied cumulatively per copy.
 *
 * @alias plyWith
 * @category tempo
 * @tags plywith, plyWith, repeat, transform, cumulative
 */
@KlangScript.Function
fun SprudelPattern.plywith(factor: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.plyWith(factor, transform, callInfo)

/** Alias for [plyWith] on a string pattern. */
@KlangScript.Function
fun String.plywith(factor: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).plywith(factor, transform, callInfo)

/** Returns a [PatternMapperFn] that is an alias for [plyWith]. */
@KlangScript.Function
fun plywith(factor: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    plyWith(factor, transform, callInfo)

/** Alias for [PatternMapperFn.plyWith]. */
@KlangScript.Function
fun PatternMapperFn.plywith(factor: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.plyWith(factor, transform, callInfo)

// -- plyForEach() -----------------------------------------------------------------------------------------------------

private fun applyPlyForEach(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) {
        return pattern
    }

    val factorArg = args[0]
    val funcArg = args[1]

    // Extract the function from the argument
    val func = funcArg.value as? ((SprudelPattern, Int) -> SprudelPattern) ?: return pattern

    // Calculate new steps if factor is purely static
    val staticFactor = factorArg.value?.asDoubleOrNull()?.toInt()

    val newSteps = if (staticFactor != null) {
        pattern.numSteps?.times(staticFactor)
    } else {
        null
    }

    val result = pattern._bindSqueeze { event ->
        val factor = staticFactor ?: event.data.value?.asDoubleOrNull()?.toInt() ?: 1

        if (factor <= 0) {
            return@_bindSqueeze null
        }

        // Start with the original value, then add transformed versions for i = 1 to factor-1
        val atomPattern = AtomicInfinitePattern(event.data)

        val patterns = buildList {
            // First pattern is the original
            add(atomPattern)
            // Then add transformed patterns for i = 1 to factor-1
            for (i in 1 until factor) {
                add(func(atomPattern, i))
            }
        }

        // Concatenate all patterns - SequencePattern squashes them into one cycle
        // _bindSqueeze will squeeze this into the event's timespan
        if (patterns.size == 1) patterns.first() else SequencePattern(patterns)
    }

    return if (newSteps != null) result.withSteps(newSteps) else result
}

/**
 * Repeats each event `n` times within its timespan, passing the iteration index to `transform`.
 *
 * Similar to `plyWith` but the transform function receives both the pattern and the index
 * (0-based). Copy 0 is always unmodified; copies 1 through `n-1` receive their index so the
 * transform can produce index-specific variations. As a top-level function the third argument
 * is the source pattern.
 *
 * @param factor Number of repetitions per event slot. Typical range: 1–16.
 * @param transform Function receiving (pattern, index) where index is 0-based; copy 0 is always unmodified.
 * @return A pattern with `n` index-specific copies of each event per slot.
 *
 * ```KlangScript(Playable)
 * note("c").plyForEach(4, (pat, i) => pat.add(i * 2))   // c, d, e, f# — index * 2 semitones
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").plyForEach(3, (pat, i) => pat.gain(1.0 - i * 0.3))  // fading copies
 * ```
 *
 * @alias plyforeach
 * @category tempo
 * @tags plyForEach, repeat, transform, index, subdivide
 */
@KlangScript.Function
fun SprudelPattern.plyForEach(factor: Int, transform: (SprudelPattern, Int) -> SprudelPattern, callInfo: CallInfo? = null): SprudelPattern =
    applyPlyForEach(this, listOf(factor, transform).asSprudelDslArgs(callInfo))

/** Repeats each event `n` times, passing the iteration index to `transform`. */
@KlangScript.Function
fun String.plyForEach(factor: Int, transform: (SprudelPattern, Int) -> SprudelPattern, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).plyForEach(factor, transform, callInfo)

/** Returns a [PatternMapperFn] that repeats each event `n` times, passing the index to `transform`. */
@KlangScript.Function
fun plyForEach(factor: Int, transform: (SprudelPattern, Int) -> SprudelPattern, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.plyForEach(factor, transform, callInfo) }

/** Chains a plyForEach operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.plyForEach(
    factor: Int,
    transform: (SprudelPattern, Int) -> SprudelPattern,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.chain { p -> p.plyForEach(factor, transform, callInfo) }

/**
 * Alias for `plyForEach`.
 *
 * @param factor Number of repetitions per event slot.
 * @param transform Function receiving (pattern, index) where index is 0-based.
 *
 * @alias plyForEach
 * @category tempo
 * @tags plyforeach, plyForEach, repeat, transform, index
 */
@KlangScript.Function
fun SprudelPattern.plyforeach(factor: Int, transform: (SprudelPattern, Int) -> SprudelPattern, callInfo: CallInfo? = null): SprudelPattern =
    this.plyForEach(factor, transform, callInfo)

/** Alias for [plyForEach] on a string pattern. */
@KlangScript.Function
fun String.plyforeach(factor: Int, transform: (SprudelPattern, Int) -> SprudelPattern, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).plyforeach(factor, transform, callInfo)

/** Returns a [PatternMapperFn] that is an alias for [plyForEach]. */
@KlangScript.Function
fun plyforeach(factor: Int, transform: (SprudelPattern, Int) -> SprudelPattern, callInfo: CallInfo? = null): PatternMapperFn =
    plyForEach(factor, transform, callInfo)

/** Alias for [PatternMapperFn.plyForEach]. */
@KlangScript.Function
fun PatternMapperFn.plyforeach(
    factor: Int,
    transform: (SprudelPattern, Int) -> SprudelPattern,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.plyForEach(factor, transform, callInfo)
