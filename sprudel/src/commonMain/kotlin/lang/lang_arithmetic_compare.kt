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
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpret

// -- lt() (Less Than) -------------------------------------------------------------------------------------------------

/**
 * Compares every value in the pattern to [threshold], replacing each with `1` (true) if less
 * than [threshold] or `0` (false) otherwise.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Supports control patterns: pass a mini-notation string or another
 * [SprudelPattern] as [threshold] to modulate the threshold per cycle or event.
 *
 * ```KlangScript(Playable)
 * seq("5 10").lt(8).scale("c3:major").n()  // 5<8 -> 1, 10<8 -> 0
 * ```
 *
 * ```KlangScript(Playable)
 * seq("5 10").lt("<8 6>").scale("c3:major").n()  // threshold changes each cycle
 * ```
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags lt, less than, comparison, arithmetic
 */
@KlangScript.Function
fun SprudelPattern.lt(threshold: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(threshold).asSprudelDslArgs(callInfo)) { a, b -> a lt b }

@KlangScript.Function
fun String.lt(threshold: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lt(threshold, callInfo)

@KlangScript.Function
fun lt(threshold: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.lt(threshold, callInfo) }

@KlangScript.Function
fun PatternMapperFn.lt(threshold: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.lt(threshold, callInfo) }

// -- gt() (Greater Than) ----------------------------------------------------------------------------------------------

/**
 * Compares every value in the pattern to [threshold], replacing each with `1` (true) if greater
 * than [threshold] or `0` (false) otherwise.
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags gt, greater than, comparison, arithmetic
 */
@KlangScript.Function
fun SprudelPattern.gt(threshold: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(threshold).asSprudelDslArgs(callInfo)) { a, b -> a gt b }

@KlangScript.Function
fun String.gt(threshold: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).gt(threshold, callInfo)

@KlangScript.Function
fun gt(threshold: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.gt(threshold, callInfo) }

@KlangScript.Function
fun PatternMapperFn.gt(threshold: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.gt(threshold, callInfo) }

// -- lte() (Less Than or Equal) ---------------------------------------------------------------------------------------

/**
 * Compares every value in the pattern to [threshold], replacing each with `1` (true) if less
 * than or equal to [threshold] or `0` (false) otherwise.
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags lte, less than or equal, comparison, arithmetic
 */
@KlangScript.Function
fun SprudelPattern.lte(threshold: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(threshold).asSprudelDslArgs(callInfo)) { a, b -> a lte b }

@KlangScript.Function
fun String.lte(threshold: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lte(threshold, callInfo)

@KlangScript.Function
fun lte(threshold: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.lte(threshold, callInfo) }

@KlangScript.Function
fun PatternMapperFn.lte(threshold: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.lte(threshold, callInfo) }

// -- gte() (Greater Than or Equal) ------------------------------------------------------------------------------------

/**
 * Compares every value in the pattern to [threshold], replacing each with `1` (true) if greater
 * than or equal to [threshold] or `0` (false) otherwise.
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags gte, greater than or equal, comparison, arithmetic
 */
@KlangScript.Function
fun SprudelPattern.gte(threshold: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(threshold).asSprudelDslArgs(callInfo)) { a, b -> a gte b }

@KlangScript.Function
fun String.gte(threshold: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).gte(threshold, callInfo)

@KlangScript.Function
fun gte(threshold: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.gte(threshold, callInfo) }

@KlangScript.Function
fun PatternMapperFn.gte(threshold: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.gte(threshold, callInfo) }

// -- eq() (Equal) -----------------------------------------------------------------------------------------------------

/**
 * Compares every value in the pattern to [other] for strict equality, replacing each with
 * `1` (true) if equal or `0` (false) otherwise.
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags eq, equal, equality, comparison, arithmetic
 */
@KlangScript.Function
fun SprudelPattern.eq(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(other).asSprudelDslArgs(callInfo)) { a, b -> a eq b }

@KlangScript.Function
fun String.eq(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).eq(other, callInfo)

@KlangScript.Function
fun eq(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.eq(other, callInfo) }

@KlangScript.Function
fun PatternMapperFn.eq(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.eq(other, callInfo) }

// -- eqt() (Truthiness Equal) -----------------------------------------------------------------------------------------

/**
 * Compares the truthiness of every value in the pattern to the truthiness of [other], replacing
 * each with `1` (true) if both share the same truthiness, or `0` (false) otherwise.
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags eqt, truthiness, equal, comparison, arithmetic
 */
@KlangScript.Function
fun SprudelPattern.eqt(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(other).asSprudelDslArgs(callInfo)) { a, b -> a eqt b }

@KlangScript.Function
fun String.eqt(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).eqt(other, callInfo)

@KlangScript.Function
fun eqt(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.eqt(other, callInfo) }

@KlangScript.Function
fun PatternMapperFn.eqt(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.eqt(other, callInfo) }

// -- ne() (Not Equal) -------------------------------------------------------------------------------------------------

/**
 * Compares every value in the pattern to [other] for strict inequality, replacing each with
 * `1` (true) if not equal or `0` (false) otherwise.
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags ne, not equal, inequality, comparison, arithmetic
 */
@KlangScript.Function
fun SprudelPattern.ne(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(other).asSprudelDslArgs(callInfo)) { a, b -> a ne b }

@KlangScript.Function
fun String.ne(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).ne(other, callInfo)

@KlangScript.Function
fun ne(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.ne(other, callInfo) }

@KlangScript.Function
fun PatternMapperFn.ne(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.ne(other, callInfo) }

// -- net() (Truthiness Not Equal) -------------------------------------------------------------------------------------

/**
 * Compares the truthiness of every value in the pattern to the truthiness of [other], replacing
 * each with `1` (true) if their truthiness differs, or `0` (false) otherwise.
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags net, truthiness, not equal, inequality, comparison, arithmetic
 */
@KlangScript.Function
fun SprudelPattern.net(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(other).asSprudelDslArgs(callInfo)) { a, b -> a net b }

@KlangScript.Function
fun String.net(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).net(other, callInfo)

@KlangScript.Function
fun net(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.net(other, callInfo) }

@KlangScript.Function
fun PatternMapperFn.net(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.net(other, callInfo) }

// -- and() (Logical AND) ----------------------------------------------------------------------------------------------

/**
 * Applies logical AND between every value in the pattern and [other].
 *
 * @param other The right-hand operand. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern where each value is `value && other`.
 * @category arithmetic
 * @tags and, logical, boolean, arithmetic
 */
@KlangScript.Function
fun SprudelPattern.and(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(other).asSprudelDslArgs(callInfo)) { a, b -> a and b }

@KlangScript.Function
fun String.and(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).and(other, callInfo)

@KlangScript.Function
fun and(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.and(other, callInfo) }

@KlangScript.Function
fun PatternMapperFn.and(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.and(other, callInfo) }

// -- or() (Logical OR) ------------------------------------------------------------------------------------------------

/**
 * Applies logical OR between every value in the pattern and [other].
 *
 * @param other The right-hand operand. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern where each value is `value || other`.
 * @category arithmetic
 * @tags or, logical, boolean, arithmetic
 */
@KlangScript.Function
fun SprudelPattern.or(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(other).asSprudelDslArgs(callInfo)) { a, b -> a or b }

@KlangScript.Function
fun String.or(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).or(other, callInfo)

@KlangScript.Function
fun or(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.or(other, callInfo) }

@KlangScript.Function
fun PatternMapperFn.or(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.or(other, callInfo) }

// -- not --------------------------------------------------------------------------------------------------------------

private fun applyNot(pattern: SprudelPattern): SprudelPattern {
    return pattern.reinterpret { evt ->
        val current = evt.data.isTruthy()
        val withNot = !current

        evt.copy(data = evt.data.copy(value = withNot.asVoiceValue()))
    }
}

/**
 * Applies logical NOT to each event's boolean value.
 *
 * Truthy values become `false`; falsy values become `true`. Useful for inverting
 * gate or trigger patterns.
 *
 * ```KlangScript(Playable)
 * "1 0 0 1".not().scale("c4:minor").n()   // becomes: false true true false
 * ```
 *
 * @category arithmetic
 * @tags not, logical, boolean, gate, invert
 */
@KlangScript.Function
@Suppress("UNUSED_PARAMETER") // callInfo is part of the uniform DSL signature; this unary op has no arg to locate
fun SprudelPattern.not(callInfo: CallInfo? = null): SprudelPattern = applyNot(this)

/**
 * Applies logical NOT to a string pattern's boolean values.
 *
 * ```KlangScript(Playable)
 * "1 0 0 1".not().scale("c4:minor").n()   // becomes: false true true false
 * ```
 */
@KlangScript.Function
fun String.not(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).not(callInfo)

/**
 * Applies logical NOT as a [PatternMapperFn], inverting each event's boolean value.
 *
 * ```KlangScript(Playable)
 * note("c d e f").degradeBy("1 0 1 0".apply(not))   // invert a degrade pattern into a gate
 * ```
 */
@KlangScript.Constant
val not: PatternMapperFn = { p -> p.not() }

/**
 * Chains a logical NOT onto this [PatternMapperFn], inverting every boolean value in the result.
 *
 * ```KlangScript(Playable)
 * seq("1 0").apply(mul(1).not())  // not(1*1)=false, not(0*1)=true
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.not(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.not(callInfo) }
