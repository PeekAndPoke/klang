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
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.ShufflePattern

// -- shuffle() --------------------------------------------------------------------------------------------------------

private fun applyShuffle(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val nPattern = (args.getOrNull(0) ?: SprudelDslArg.of(4)).toPattern()
    return ShufflePattern(source = p, nPattern = nPattern)
}

/**
 * Slices the pattern into `n` equal parts and plays them in a new random order each cycle.
 *
 * Each slice is played exactly once per cycle — the order changes but nothing is omitted.
 * Compare with [scramble], which may repeat or skip slices.
 *
 * @param n Number of equal slices to divide the pattern into and reorder.
 * @return A pattern with its slices randomly permuted each cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").shuffle(4)                 // random permutation of 4 quarter-cycle slices
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").shuffle(4)                // randomly reorder the 4 drum hits each cycle
 * ```
 *
 * @category random
 * @tags shuffle, random, reorder, slice, permutation
 */
@KlangScript.Function
fun SprudelPattern.shuffle(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyShuffle(this, listOf(n).asSprudelDslArgs(callInfo))

/** Slices the pattern into `n` equal parts and plays them in a new random order each cycle. */
@KlangScript.Function
fun String.shuffle(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).shuffle(n, callInfo)

/**
 * Returns a [PatternMapperFn] that slices the source into `n` parts and plays them in random order.
 *
 * @param n Number of equal slices to divide the pattern into and reorder.
 * @return A [PatternMapperFn] that randomly reorders `n` slices each cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(shuffle(4))          // random permutation via mapper
 * ```
 *
 * @category random
 * @tags shuffle, random, reorder, slice, permutation
 */
@KlangScript.Function
fun shuffle(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.shuffle(n, callInfo) }

/** Chains a shuffle onto this [PatternMapperFn]; randomly reorders `n` equal slices each cycle. */
@KlangScript.Function
fun PatternMapperFn.shuffle(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.shuffle(n, callInfo) }

// -- scramble() -------------------------------------------------------------------------------------------------------

private fun applyScramble(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val nArg: SprudelDslArg<Any?> = args.getOrNull(0) ?: SprudelDslArg.of(4)
    val nValue = nArg.value ?: 4
    val indices = applyIrand(listOf(nArg)).segment(nValue)
    return p.bite(nValue, indices)
}

/**
 * Slices the pattern into `n` equal parts and picks slices at random each cycle.
 *
 * Unlike [shuffle], which plays each slice exactly once, `scramble` picks randomly with
 * replacement: some slices may play multiple times, others not at all.
 *
 * @param n Number of equal slices to divide the pattern into and pick from.
 * @return A pattern with randomly selected (with replacement) slices each cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").scramble(4)         // random selection with repetition of 4 slices
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").scramble(4)        // random drum hit order, repeats allowed
 * ```
 *
 * @category random
 * @tags scramble, random, slice, replacement, selection
 */
@KlangScript.Function
fun SprudelPattern.scramble(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyScramble(this, listOf(n).asSprudelDslArgs(callInfo))

/** Slices the pattern into `n` equal parts and picks slices at random each cycle. */
@KlangScript.Function
fun String.scramble(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).scramble(n, callInfo)

/**
 * Returns a [PatternMapperFn] that slices the source into `n` parts and picks randomly with replacement.
 *
 * @param n Number of equal slices to divide the pattern into and pick from.
 * @return A [PatternMapperFn] that randomly picks `n` slices (with repetition) each cycle.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").apply(scramble(4)) // random drum hit order via mapper
 * ```
 *
 * @category random
 * @tags scramble, random, slice, replacement, selection
 */
@KlangScript.Function
fun scramble(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.scramble(n, callInfo) }

/** Chains a scramble onto this [PatternMapperFn]; randomly picks `n` slices with replacement. */
@KlangScript.Function
fun PatternMapperFn.scramble(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.scramble(n, callInfo) }

