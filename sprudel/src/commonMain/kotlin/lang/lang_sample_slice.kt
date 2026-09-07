/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

// -- slice() ----------------------------------------------------------------------------------------------------------

private fun applySlice(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val nArg = args.getOrNull(0)
    val nVal = maxOf(1, nArg?.value?.asIntOrNull() ?: 1)

    // TODO: support dynamic index pattern

    val indexArg = args.getOrNull(1)
    val indexVal = indexArg?.value?.asIntOrNull() ?: 0

    val start = indexVal.toDouble() / nVal
    val end = (indexVal + 1.0) / nVal

    return source.begin(start).end(end)
}

/**
 * Plays a specific slice of the sample by dividing it into equal parts.
 *
 * Splits the sample into `n` equal segments and plays only the one at `index` (0-based).
 * Implemented by setting [begin] and [end] appropriately. Combine with a pattern for
 * the index to sequence through different slices.
 *
 * ```KlangScript(Playable)
 * s("breaks").slice(8, 0)                // play the first of 8 slices
 * ```
 *
 * ```KlangScript(Playable)
 * s("breaks").slice(8, "0 1 2 3 4 5 6 7")    // sequence through all 8 slices
 * ```
 *
 * @param n Number of equal slices to divide the sample into. Integer.
 * @param index Zero-based index of the slice to play. Integer.
 * @category sampling
 * @tags slice, segment, chop, sample, begin, end
 */
@KlangScript.Function
fun SprudelPattern.slice(n: PatternLike, index: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applySlice(this, listOf(n, index).asSprudelDslArgs(callInfo))

/**
 * Plays a specific slice of the sample on a string pattern.
 * See [SprudelPattern.slice] for full documentation.
 */
@KlangScript.Function
fun String.slice(n: PatternLike, index: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).slice(n, index, callInfo)

/**
 * Returns a [PatternMapperFn] that plays a specific slice of the sample.
 *
 * @param n Number of equal slices to divide the sample into.
 * @param index Zero-based index of the slice to play.
 * @return A [PatternMapperFn] that sets begin and end to the given slice.
 *
 * ```KlangScript(Playable)
 * s("breaks").apply(slice(8, 0))   // first of 8 slices via mapper
 * ```
 *
 * @category sampling
 * @tags slice, segment, chop, sample, begin, end
 */
@KlangScript.Function
fun slice(n: PatternLike, index: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.slice(n, index, callInfo) }

/** Chains a slice onto this [PatternMapperFn]; plays the given slice of the sample. */
@KlangScript.Function
fun PatternMapperFn.slice(n: PatternLike, index: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.slice(n, index, callInfo) }

// -- splice() ---------------------------------------------------------------------------------------------------------

private fun applySplice(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // Get number of slices (also used as speed multiplier)
    val nArg = args.getOrNull(0)
    val nVal = maxOf(1, nArg?.value?.asIntOrNull() ?: 1)

    // Apply slice, then multiply speed by n to maintain timing
    return applySlice(source, args).speed(nVal.toDouble())
}

/**
 * Plays a specific slice of the sample at the original sample tempo.
 *
 * Like [slice], but multiplies [speed] by `n` to compensate for the shorter segment
 * duration, so each slice plays at the same pitch and rate as the original sample.
 * Useful for beat-slicing without pitch artifacts.
 *
 * ```KlangScript(Playable)
 * s("breaks").splice(8, 0)               // first of 8 slices at original pitch
 * ```
 *
 * ```KlangScript(Playable)
 * s("breaks").splice(8, "0 2 4 6")      // every other slice, original tempo
 * ```
 *
 * @param n Number of equal slices to divide the sample into; also used as speed multiplier. Integer.
 * @param index Zero-based index of the slice to play. Integer.
 * @category sampling
 * @tags splice, slice, chop, sample, speed, tempo
 */
@KlangScript.Function
fun SprudelPattern.splice(n: PatternLike, index: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applySplice(this, listOf(n, index).asSprudelDslArgs(callInfo))

/**
 * Plays a specific slice of the sample at the original tempo on a string pattern.
 * See [SprudelPattern.splice] for full documentation.
 */
@KlangScript.Function
fun String.splice(n: PatternLike, index: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).splice(n, index, callInfo)

/**
 * Returns a [PatternMapperFn] that plays a specific slice at the original sample tempo.
 *
 * @param n Number of equal slices to divide the sample into.
 * @param index Zero-based index of the slice to play.
 * @return A [PatternMapperFn] that sets begin, end, and compensates speed for the slice.
 *
 * ```KlangScript(Playable)
 * s("breaks").apply(splice(8, 0))  // first of 8 slices at original pitch via mapper
 * ```
 *
 * @category sampling
 * @tags splice, slice, chop, sample, speed, tempo
 */
@KlangScript.Function
fun splice(n: PatternLike, index: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.splice(n, index, callInfo) }

/** Chains a splice onto this [PatternMapperFn]; plays the given slice at original sample tempo. */
@KlangScript.Function
fun PatternMapperFn.splice(n: PatternLike, index: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.splice(n, index, callInfo) }
