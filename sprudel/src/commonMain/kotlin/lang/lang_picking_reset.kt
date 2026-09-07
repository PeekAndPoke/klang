/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.PickResetPattern

// -- pickReset() ------------------------------------------------------------------------------------------------------

private fun applyPickReset(
    lookup: Any,
    pat: SprudelPattern,
    modulo: Boolean,
    baseLocation: SourceLocation? = null,
): SprudelPattern {
    val reifiedLookup = reifyLookup(lookup, baseLocation) ?: return silence
    val isList = lookup is List<*>

    val keyExtractor: (SprudelVoiceData, Boolean, Int) -> Any? = if (isList) {
        { data, mod, len -> extractIndex(data, mod, len) }
    } else {
        { data, _, _ -> extractKey(data) }
    }

    return PickResetPattern(
        selector = pat,
        lookup = reifiedLookup,
        modulo = modulo,
        extractKey = keyExtractor
    )
}

private fun dispatchPickReset(
    lookup: Any?,
    pat: SprudelPattern,
    modulo: Boolean,
    baseLocation: SourceLocation? = null,
): SprudelPattern {
    if (lookup == null) return silence

    return when (lookup) {
        is List<*> -> {
            @Suppress("UNCHECKED_CAST")
            applyPickReset(lookup as List<Any>, pat, modulo, baseLocation)
        }

        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            applyPickReset(lookup as Map<String, Any>, pat, modulo, baseLocation)
        }

        else -> silence
    }
}

private fun applyPickResetExtension(
    pattern: SprudelPattern,
    args: List<SprudelDslArg<Any?>>,
    baseLocation: SourceLocation?
): SprudelPattern {
    if (args.isEmpty()) return pattern
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    return dispatchPickReset(lookup, pattern, modulo = false, baseLocation)
}

/**
 * Like [pick] but resets the chosen pattern to its initial phase each time it is triggered.
 *
 * Similar to [pickRestart] but "reset" aligns the chosen pattern to the global cycle boundary
 * of the trigger rather than always starting from beat zero. Indices are clamped.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map for key-based lookup.
 * @return A pattern that resets the selected item's phase on each trigger.
 *
 * ```KlangScript(Playable)
 * seq("<0 1 2!2 3>").pickReset("g a", "e f", "f g f g" , "g c d").note()
 * ```
 *
 * ```KlangScript(Playable)
 * seq("<0 1 [2,0]>").pickReset("bd sd", "cp cp", "hh hh").s()
 * ```
 *
 * @category structural
 * @tags pickReset, pick, reset, trigger, select, index
 */
@KlangScript.Function
fun SprudelPattern.pickReset(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPickResetExtension(this, args.toList().asSprudelDslArgs(callInfo), callInfo?.receiverLocation)

/**
 * Like [pick] but resets the chosen pattern — [List] lookup using this pattern's event values.
 *
 * @param lookup List of items; integer indices, clamped. Selected item's phase resets on each trigger.
 * @return A pattern that resets the selected item's phase on each trigger.
 *
 * ```KlangScript(Playable)
 * seq("<0 1 2!2 3>").pickReset(["g a", "e f", "f g f g" , "g c d"]).note()
 * ```
 */
fun SprudelPattern.pickReset(lookup: List<PatternLike>): SprudelPattern =
    applyPickResetExtension(this, listOf(lookup).asSprudelDslArgs(), null)

/**
 * Like [pick] but resets the chosen pattern — [Map] lookup using this pattern's event values as keys.
 *
 * @param lookup Map of string keys to items; unmatched keys produce no output.
 * @return A pattern that resets the selected item's phase on each trigger.
 *
 * ```KlangScript(Playable)
 * seq("<a!2 [a,b] b>").pickReset({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
fun SprudelPattern.pickReset(lookup: Map<String, PatternLike>): SprudelPattern =
    applyPickResetExtension(this, listOf(lookup).asSprudelDslArgs(), null)

/**
 * Like [pick] but resets the chosen pattern — this string parsed as a mini-notation index pattern.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A pattern that resets the selected item's phase on each trigger.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".pickReset("bd", "sd", "hh").s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickReset, pick, reset, trigger, select, index
 */
@KlangScript.Function
fun String.pickReset(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pickReset(*args, callInfo = callInfo)

/**
 * Like [pick] but resets the chosen pattern — [List] lookup, this string as index pattern.
 *
 * @param lookup List of items; integer indices, clamped.
 * @return A pattern that resets the selected item's phase on each trigger.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".pickReset(["bd", "sd", "hh"]).s().fast(2)
 * ```
 */
fun String.pickReset(lookup: List<PatternLike>): SprudelPattern =
    this.toVoiceValuePattern().pickReset(lookup)

/**
 * Like [pick] but resets the chosen pattern — [Map] lookup, this string as key pattern.
 *
 * @param lookup Map of string keys; unmatched keys produce no output.
 * @return A pattern that resets the selected item's phase on each trigger.
 *
 * ```KlangScript(Playable)
 * "<a!2 [a,b] b>".pickReset({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
fun String.pickReset(lookup: Map<String, PatternLike>): SprudelPattern =
    this.toVoiceValuePattern().pickReset(lookup)

/**
 * Returns a [PatternMapperFn] that selects from a lookup and resets the chosen pattern's phase on trigger.
 *
 * Like [pick] but the selected pattern's phase is reset on each trigger. Apply using `.apply()`.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A [PatternMapperFn] that maps a source pattern to a reset-pick result.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".apply(pickReset("bd", "sd", "hh")).s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickReset, pick, reset, trigger, select, index
 */
@KlangScript.Function
fun pickReset(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pickReset(*args, callInfo = callInfo) }

/**
 * Returns a [PatternMapperFn] that selects from a [List] lookup and resets the chosen pattern.
 *
 * @param lookup List of items; source pattern values are used as zero-based indices (clamped).
 * @return A [PatternMapperFn] that maps a source pattern to a reset-pick result.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".apply(pickReset(["bd", "sd", "hh"])).s().fast(2)
 * ```
 */
fun pickReset(lookup: List<PatternLike>): PatternMapperFn = { p -> p.pickReset(lookup) }

/**
 * Returns a [PatternMapperFn] that selects from a [Map] lookup and resets the chosen pattern.
 *
 * @param lookup Map of string keys; source pattern values are used as keys.
 * @return A [PatternMapperFn] that maps a source pattern to a reset-pick result.
 *
 * ```KlangScript(Playable)
 * "<a!2 [a,b] b>".apply(pickReset({a: "bd(3,8)", b: "sd sd"})).s()
 * ```
 */
fun pickReset(lookup: Map<String, PatternLike>): PatternMapperFn = { p -> p.pickReset(lookup) }

/**
 * Chains a pickReset onto this [PatternMapperFn]; clamped indices, resets phase on trigger.
 */
@KlangScript.Function
fun PatternMapperFn.pickReset(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pickReset(*args, callInfo = callInfo) }

/**
 * Chains a pickReset from a [List] lookup onto this [PatternMapperFn].
 */
fun PatternMapperFn.pickReset(lookup: List<PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickReset(lookup) }

/**
 * Chains a pickReset from a [Map] lookup onto this [PatternMapperFn].
 */
fun PatternMapperFn.pickReset(lookup: Map<String, PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickReset(lookup) }

// -- pickmodReset() ---------------------------------------------------------------------------------------------------

private fun applyPickmodResetExtension(
    pattern: SprudelPattern,
    args: List<SprudelDslArg<Any?>>,
    baseLocation: SourceLocation?
): SprudelPattern {
    if (args.isEmpty()) return pattern
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    return dispatchPickReset(lookup, pattern, modulo = true, baseLocation)
}

/**
 * Like [pickReset] but wraps out-of-bounds indices with modulo arithmetic.
 *
 * Selects patterns with modulo-wrapped indices and resets the chosen pattern's phase on
 * each trigger. Negative indices are handled correctly.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map for key-based lookup.
 * @return A pattern that resets the selected item's phase on each trigger; indices wrap cyclically.
 *
 * ```KlangScript(Playable)
 * seq("<0 1 2!2 3>").pickmodReset("g a", "e f", "f g f g" , "g c d").note()
 * ```
 *
 * ```KlangScript(Playable)
 * seq("<0 1 [2,0]>").pickmodReset("bd sd", "cp cp", "hh hh").s()
 * ```
 *
 * @category structural
 * @tags pickmodReset, pickReset, modulo, reset, trigger, index
 */
@KlangScript.Function
fun SprudelPattern.pickmodReset(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPickmodResetExtension(this, args.toList().asSprudelDslArgs(callInfo), callInfo?.receiverLocation)

/**
 * Like [pickReset] but wraps indices with modulo — [List] lookup using this pattern's event values.
 *
 * @param lookup List of items; indices wrap cyclically with modulo.
 * @return A pattern that resets the selected item's phase on each trigger.
 *
 * ```KlangScript(Playable)
 * seq("<0 1 2!2 3>").pickmodReset(["g a", "e f", "f g f g" , "g c d"]).note()
 * ```
 */
fun SprudelPattern.pickmodReset(lookup: List<PatternLike>): SprudelPattern =
    applyPickmodResetExtension(this, listOf(lookup).asSprudelDslArgs(), null)

/**
 * Like [pickReset] but wraps indices with modulo — [Map] lookup using this pattern's event values as keys.
 *
 * @param lookup Map of string keys; unmatched keys produce no output.
 * @return A pattern that resets the selected item's phase on each trigger.
 *
 * ```KlangScript(Playable)
 * seq("<a!2 [a,b] b>").pickmodReset({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
fun SprudelPattern.pickmodReset(lookup: Map<String, PatternLike>): SprudelPattern =
    applyPickmodResetExtension(this, listOf(lookup).asSprudelDslArgs(), null)

/**
 * Like [pickReset] but wraps indices with modulo — this string parsed as a mini-notation index pattern.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A pattern that resets the selected item's phase on each trigger; indices wrap cyclically.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".pickmodReset("bd", "sd", "hh").s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickmodReset, pickReset, modulo, reset, trigger, index
 */
@KlangScript.Function
fun String.pickmodReset(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pickmodReset(*args, callInfo = callInfo)

/**
 * Like [pickReset] but wraps indices with modulo — [List] lookup, this string as index pattern.
 *
 * @param lookup List of items; indices wrap cyclically.
 * @return A pattern that resets the selected item's phase on each trigger.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".pickmodReset(["bd", "sd", "hh"]).s().fast(2)
 * ```
 */
fun String.pickmodReset(lookup: List<PatternLike>): SprudelPattern =
    this.toVoiceValuePattern().pickmodReset(lookup)

/**
 * Like [pickReset] but wraps indices with modulo — [Map] lookup, this string as key pattern.
 *
 * @param lookup Map of string keys; unmatched keys produce no output.
 * @return A pattern that resets the selected item's phase on each trigger.
 *
 * ```KlangScript(Playable)
 * "<a!2 [a,b] b>".pickmodReset({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
fun String.pickmodReset(lookup: Map<String, PatternLike>): SprudelPattern =
    this.toVoiceValuePattern().pickmodReset(lookup)

/**
 * Returns a [PatternMapperFn] that selects from a lookup with modulo indices and resets the chosen pattern.
 *
 * Like [pickReset] but wraps indices cyclically. Apply the returned mapper using `.apply()`.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A [PatternMapperFn] that maps a source pattern to a modulo reset-pick result.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".apply(pickmodReset("bd", "sd", "hh")).s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickmodReset, pickReset, modulo, reset, trigger, index
 */
@KlangScript.Function
fun pickmodReset(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pickmodReset(*args, callInfo = callInfo) }

/**
 * Returns a [PatternMapperFn] that selects from a [List] with modulo indices and resets phase.
 *
 * @param lookup List of items; source pattern values are used as integer indices (modulo-wrapped).
 * @return A [PatternMapperFn] that maps a source pattern to a modulo reset-pick result.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".apply(pickmodReset(["bd", "sd", "hh"])).s().fast(2)
 * ```
 */
fun pickmodReset(lookup: List<PatternLike>): PatternMapperFn = { p -> p.pickmodReset(lookup) }

/**
 * Returns a [PatternMapperFn] that selects from a [Map] lookup and resets the chosen pattern.
 *
 * @param lookup Map of string keys; source pattern values are used as keys.
 * @return A [PatternMapperFn] that maps a source pattern to a modulo reset-pick result.
 *
 * ```KlangScript(Playable)
 * "<a!2 [a,b] b>".apply(pickmodReset({a: "bd(3,8)", b: "sd sd"})).s()
 * ```
 */
fun pickmodReset(lookup: Map<String, PatternLike>): PatternMapperFn = { p -> p.pickmodReset(lookup) }

/**
 * Chains a pickmodReset onto this [PatternMapperFn]; modulo indices, resets phase on trigger.
 */
@KlangScript.Function
fun PatternMapperFn.pickmodReset(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pickmodReset(*args, callInfo = callInfo) }

/**
 * Chains a pickmodReset from a [List] lookup onto this [PatternMapperFn].
 */
fun PatternMapperFn.pickmodReset(lookup: List<PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickmodReset(lookup) }

/**
 * Chains a pickmodReset from a [Map] lookup onto this [PatternMapperFn].
 */
fun PatternMapperFn.pickmodReset(lookup: Map<String, PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickmodReset(lookup) }
