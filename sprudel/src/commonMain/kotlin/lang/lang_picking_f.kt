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
import io.peekandpoke.klang.sprudel._bind
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

// -- pickF() ----------------------------------------------------------------------------------------------------------

/**
 * Apply functions from a list based on a pattern of indices.
 * Indices are clamped to the list size.
 *
 * Example: `s("bd [rim hh]").pickF("<0 1 2>", [rev, jux(rev()), fast(2)])`
 * (`rev` takes optional arguments, so passing it ON to another function needs the call:
 * `jux(rev())`, not `jux(rev)`. See docs/tasks/future/native-interop-function-values.md.)
 */
private fun applyPickF(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val lookupArg = args.getOrNull(0) ?: return pattern
    val funcsArg = args.getOrNull(1) ?: return pattern

    // Get the list of functions
    val funcsList = funcsArg.value as? List<*> ?: return pattern
    val mappers = funcsList.mapNotNull { patternMapper(it) }
    if (mappers.isEmpty()) return pattern

    return lookupArg.toPattern()._bind { indexEvent ->
        val index = (indexEvent.data.value?.asInt ?: 0).coerceIn(0, mappers.size - 1)
        val selectedFunction = mappers.getOrNull(index) ?: { it }
        selectedFunction(pattern)
    }
}

/**
 * Applies a function from a list to this pattern, selected by an index pattern (clamped).
 *
 * The first argument is the index pattern; the second is a list of pattern-transforming functions.
 * The index (clamped to bounds) selects which function is applied to this pattern.
 *
 * @param args Index pattern followed by the function list — `pickF(indexPat, [fn1, fn2, ...])`.
 * @return A pattern with the selected function applied.
 *
 * ```KlangScript(Playable)
 * s("bd rim hh").pickF("<0 1 2>", [rev, fast(2), jux(rev())])  // fn selected by index
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").pickF(n("0 1"), [slow(2), fast(3)])       // 0→slow(2), 1→fast(3)
 * ```
 *
 * @category structural
 * @tags pickF, pick, apply, transform, function, index
 */
@KlangScript.Function
fun SprudelPattern.pickF(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPickF(this, args.toList().asSprudelDslArgs(callInfo))

/**
 * Applies a function from a list to this string pattern, selected by an index pattern (clamped).
 *
 * @param args Index pattern followed by the function list.
 * @return A pattern with the selected function applied.
 *
 * ```KlangScript(Playable)
 * "bd rim hh".pickF("<0 1 2>", [rev, fast(2), jux(rev())]).s()  // string source, fn by index
 * ```
 *
 * @category structural
 * @tags pickF, pick, apply, transform, function, index
 */
@KlangScript.Function
fun String.pickF(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pickF(*args, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] that applies a function from a list to the source, selected by index (clamped).
 *
 * The first argument is the index pattern; the second is the function list. Apply using `.apply()`.
 *
 * @param args Index pattern followed by the function list.
 * @return A [PatternMapperFn] that applies the selected function to the source pattern.
 *
 * ```KlangScript(Playable)
 * s("bd rim hh").apply(pickF("<0 1 2>", [rev, fast(2), jux(rev())]))  // via mapper
 * ```
 *
 * @category structural
 * @tags pickF, pick, apply, transform, function, index
 */
@KlangScript.Function
fun pickF(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pickF(*args, callInfo = callInfo) }

/**
 * Chains a pickF onto this [PatternMapperFn]; applies a function selected by clamped index.
 *
 * @param args Index pattern followed by the function list.
 * @return A new [PatternMapperFn] composing this mapper with the function-select operation.
 */
@KlangScript.Function
fun PatternMapperFn.pickF(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pickF(*args, callInfo = callInfo) }

// -- pickmodF() -------------------------------------------------------------------------------------------------------

/**
 * Apply functions from a list based on a pattern of indices.
 * Indices wrap around (modulo) if greater than list size.
 */
private fun applyPickmodF(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val lookupArg = args.getOrNull(0) ?: return pattern
    val funcsArg = args.getOrNull(1) ?: return pattern

    // Get the list of functions
    val funcsList = funcsArg.value as? List<*> ?: return pattern
    val mappers = funcsList.mapNotNull { patternMapper(it) }
    if (mappers.isEmpty()) return pattern

    // Similar to pickF but with modulo wrapping

    return lookupArg.toPattern()._bind { indexEvent ->
        val index = (((indexEvent.data.value?.asInt ?: 0) % mappers.size) + mappers.size) % mappers.size
        val selectedFunction = mappers.getOrNull(index) ?: { it }
        selectedFunction(pattern)
    }
}

/**
 * Like [pickF] but wraps out-of-bounds indices with modulo arithmetic.
 *
 * The first argument is the index pattern; the second is the function list. Indices wrap cyclically.
 *
 * @param args Index pattern followed by the function list.
 * @return A pattern with the modulo-selected function applied.
 *
 * ```KlangScript(Playable)
 * s("bd rim hh").pickmodF("<0 1 2 3>", [rev, fast(2)])          // 2→0, 3→1 mod 2
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").pickmodF(n("0 5"), [slow(2), fast(3)])       // 5→1 mod 2
 * ```
 *
 * @category structural
 * @tags pickmodF, pickF, modulo, apply, transform, function, index
 */
@KlangScript.Function
fun SprudelPattern.pickmodF(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPickmodF(this, args.toList().asSprudelDslArgs(callInfo))

/**
 * Like [pickF] but wraps indices with modulo — this string pattern is the source.
 *
 * @param args Index pattern followed by the function list.
 * @return A pattern with the modulo-selected function applied.
 *
 * ```KlangScript(Playable)
 * "bd rim hh".pickmodF("<0 1 2 3>", [rev, fast(2)]).s()  // string source, modulo index
 * ```
 *
 * @category structural
 * @tags pickmodF, pickF, modulo, apply, transform, function, index
 */
@KlangScript.Function
fun String.pickmodF(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pickmodF(*args, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] that applies a function from a list with modulo-wrapped index.
 *
 * Like [pickF] but indices wrap cyclically. Apply using `.apply()`.
 *
 * @param args Index pattern followed by the function list.
 * @return A [PatternMapperFn] that applies the modulo-selected function to the source pattern.
 *
 * ```KlangScript(Playable)
 * s("bd rim hh").apply(pickmodF("<0 1 2 3>", [rev, fast(2)]))  // via mapper, modulo
 * ```
 *
 * @category structural
 * @tags pickmodF, pickF, modulo, apply, transform, function, index
 */
@KlangScript.Function
fun pickmodF(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pickmodF(*args, callInfo = callInfo) }

/**
 * Chains a pickmodF onto this [PatternMapperFn]; applies a function selected by modulo-wrapped index.
 *
 * @param args Index pattern followed by the function list.
 * @return A new [PatternMapperFn] composing this mapper with the modulo function-select operation.
 */
@KlangScript.Function
fun PatternMapperFn.pickmodF(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pickmodF(*args, callInfo = callInfo) }
