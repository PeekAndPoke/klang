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
import io.peekandpoke.klang.sprudel.pattern.PickRestartPattern

// -- pickRestart() ----------------------------------------------------------------------------------------------------

private fun applyPickRestart(
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

    return PickRestartPattern(
        selector = pat,
        lookup = reifiedLookup,
        modulo = modulo,
        extractKey = keyExtractor
    )
}

private fun dispatchPickRestart(
    lookup: Any?,
    pat: SprudelPattern,
    modulo: Boolean,
    baseLocation: SourceLocation? = null,
): SprudelPattern {
    if (lookup == null) return silence

    return when (lookup) {
        is List<*> -> {
            @Suppress("UNCHECKED_CAST")
            applyPickRestart(lookup as List<Any>, pat, modulo, baseLocation)
        }

        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            applyPickRestart(lookup as Map<String, Any>, pat, modulo, baseLocation)
        }

        else -> silence
    }
}

private fun applyPickRestartExtension(
    pattern: SprudelPattern,
    args: List<SprudelDslArg<Any?>>,
    baseLocation: SourceLocation?
): SprudelPattern {
    if (args.isEmpty()) return pattern
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    return dispatchPickRestart(lookup, pattern, modulo = false, baseLocation)
}

/**
 * Like [pick] but restarts the chosen pattern from its beginning each time it is triggered.
 *
 * Each event's index selects a pattern from the lookup, which is then restarted from its
 * cycle beginning. Indices are clamped. Useful for building phrase-based structures where
 * each trigger always plays the selected pattern from the top.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map for key-based lookup.
 * @return A pattern that restarts the selected item on each trigger.
 *
 * ```KlangScript(Playable)
 * seq("<0 1 2!2 3>").pickRestart("g a", "e f", "f g f g" , "g c d").note()
 * ```
 *
 * ```KlangScript(Playable)
 * seq("<0 1 [2,0]>").pickRestart("bd sd", "cp cp", "hh hh").s()
 * ```
 *
 * @category structural
 * @tags pickRestart, pick, restart, trigger, select, index
 */
@KlangScript.Function
fun SprudelPattern.pickRestart(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPickRestartExtension(this, args.toList().asSprudelDslArgs(callInfo), callInfo?.receiverLocation)

/**
 * Like [pick] but restarts the chosen pattern — [List] lookup using this pattern's event values.
 *
 * @param lookup List of items; integer indices, clamped. Selected item restarts on each trigger.
 * @return A pattern that restarts the selected item on each trigger.
 *
 * ```KlangScript(Playable)
 * seq("<0 1 2!2 3>").pickRestart(["g a", "e f", "f g f g" , "g c d"]).note()
 * ```
 */
fun SprudelPattern.pickRestart(lookup: List<PatternLike>): SprudelPattern =
    applyPickRestartExtension(this, listOf(lookup).asSprudelDslArgs(), null)

/**
 * Like [pick] but restarts the chosen pattern — [Map] lookup using this pattern's event values as keys.
 *
 * @param lookup Map of string keys to items; unmatched keys produce no output.
 * @return A pattern that restarts the selected item on each trigger.
 *
 * ```KlangScript(Playable)
 * seq("<a!2 [a,b] b>").pickRestart({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
fun SprudelPattern.pickRestart(lookup: Map<String, Any>): SprudelPattern =
    applyPickRestartExtension(this, listOf(lookup).asSprudelDslArgs(), null)

/**
 * Like [pick] but restarts the chosen pattern — this string parsed as a mini-notation index pattern.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A pattern that restarts the selected item on each trigger.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".pickRestart("bd", "sd", "hh").s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickRestart, pick, restart, trigger, select, index
 */
@KlangScript.Function
fun String.pickRestart(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pickRestart(*args, callInfo = callInfo)

/**
 * Like [pick] but restarts the chosen pattern — [List] lookup, this string as index pattern.
 *
 * @param lookup List of items; integer indices, clamped.
 * @return A pattern that restarts the selected item on each trigger.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".pickRestart(["bd", "sd", "hh"]).s().fast(2)
 * ```
 */
fun String.pickRestart(lookup: List<PatternLike>): SprudelPattern =
    this.toVoiceValuePattern().pickRestart(lookup)

/**
 * Like [pick] but restarts the chosen pattern — [Map] lookup, this string as key pattern.
 *
 * @param lookup Map of string keys; unmatched keys produce no output.
 * @return A pattern that restarts the selected item on each trigger.
 *
 * ```KlangScript(Playable)
 * "<a!2 [a,b] b>".pickRestart({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
fun String.pickRestart(lookup: Map<String, Any>): SprudelPattern =
    this.toVoiceValuePattern().pickRestart(lookup)

/**
 * Returns a [PatternMapperFn] that selects from a lookup and restarts the chosen pattern on each trigger.
 *
 * Like [pick] but the selected pattern always restarts from its beginning. Apply using `.apply()`.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A [PatternMapperFn] that maps a source pattern to a restart-pick result.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".apply(pickRestart("bd", "sd", "hh")).s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickRestart, pick, restart, trigger, select, index
 */
@KlangScript.Function
fun pickRestart(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pickRestart(*args, callInfo = callInfo) }

/**
 * Returns a [PatternMapperFn] that selects from a [List] lookup and restarts the chosen pattern.
 *
 * @param lookup List of items; source pattern values are used as zero-based indices (clamped).
 * @return A [PatternMapperFn] that maps a source pattern to a restart-pick result.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".apply(pickRestart(["bd", "sd", "hh"])).s().fast(2)
 * ```
 */
fun pickRestart(lookup: List<PatternLike>): PatternMapperFn = { p -> p.pickRestart(lookup) }

/**
 * Returns a [PatternMapperFn] that selects from a [Map] lookup and restarts the chosen pattern.
 *
 * @param lookup Map of string keys; source pattern values are used as keys.
 * @return A [PatternMapperFn] that maps a source pattern to a restart-pick result.
 *
 * ```KlangScript(Playable)
 * "<a!2 [a,b] b>".apply(pickRestart({a: "bd(3,8)", b: "sd sd"})).s()
 * ```
 */
fun pickRestart(lookup: Map<String, Any>): PatternMapperFn = { p -> p.pickRestart(lookup) }

/**
 * Chains a pickRestart onto this [PatternMapperFn]; clamped indices, restarts on trigger.
 */
@KlangScript.Function
fun PatternMapperFn.pickRestart(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pickRestart(*args, callInfo = callInfo) }

/**
 * Chains a pickRestart from a [List] lookup onto this [PatternMapperFn].
 */
fun PatternMapperFn.pickRestart(lookup: List<PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickRestart(lookup) }

/**
 * Chains a pickRestart from a [Map] lookup onto this [PatternMapperFn].
 */
fun PatternMapperFn.pickRestart(lookup: Map<String, Any>): PatternMapperFn =
    this.chain { p -> p.pickRestart(lookup) }

// -- pickmodRestart() -------------------------------------------------------------------------------------------------

private fun applyPickmodRestartExtension(
    pattern: SprudelPattern,
    args: List<SprudelDslArg<Any?>>,
    baseLocation: SourceLocation?
): SprudelPattern {
    if (args.isEmpty()) return pattern
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    return dispatchPickRestart(lookup, pattern, modulo = true, baseLocation)
}

/**
 * Like [pickRestart] but wraps out-of-bounds indices with modulo arithmetic.
 *
 * Selects patterns with modulo-wrapped indices and restarts the chosen pattern from its
 * beginning on each trigger. Negative indices are handled correctly.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map for key-based lookup.
 * @return A pattern that restarts the selected item on each trigger; indices wrap cyclically.
 *
 * ```KlangScript(Playable)
 * seq("<0 1 2!2 3>").pickmodRestart("g a", "e f", "f g f g" , "g c d").note()
 * ```
 *
 * ```KlangScript(Playable)
 * seq("<0 1 [2,0]>").pickmodRestart("bd sd", "cp cp", "hh hh").s()
 * ```
 *
 * @category structural
 * @tags pickmodRestart, pickRestart, modulo, restart, trigger, index
 */
@KlangScript.Function
fun SprudelPattern.pickmodRestart(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPickmodRestartExtension(this, args.toList().asSprudelDslArgs(callInfo), callInfo?.receiverLocation)

/**
 * Like [pickRestart] but wraps indices with modulo — [List] lookup using this pattern's event values.
 *
 * @param lookup List of items; indices wrap cyclically with modulo.
 * @return A pattern that restarts the selected item on each trigger.
 *
 * ```KlangScript(Playable)
 * seq("<0 1 2!2 3>").pickmodRestart(["g a", "e f", "f g f g" , "g c d"]).note()
 * ```
 */
fun SprudelPattern.pickmodRestart(lookup: List<PatternLike>): SprudelPattern =
    applyPickmodRestartExtension(this, listOf(lookup).asSprudelDslArgs(), null)

/**
 * Like [pickRestart] but wraps indices with modulo — [Map] lookup using this pattern's event values as keys.
 *
 * @param lookup Map of string keys; unmatched keys produce no output.
 * @return A pattern that restarts the selected item on each trigger.
 *
 * ```KlangScript(Playable)
 * seq("<a!2 [a,b] b>").pickmodRestart({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
fun SprudelPattern.pickmodRestart(lookup: Map<String, Any>): SprudelPattern =
    applyPickmodRestartExtension(this, listOf(lookup).asSprudelDslArgs(), null)

/**
 * Like [pickRestart] but wraps indices with modulo — this string parsed as a mini-notation index pattern.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A pattern that restarts the selected item on each trigger; indices wrap cyclically.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".pickmodRestart("bd", "sd", "hh").s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickmodRestart, pickRestart, modulo, restart, trigger, index
 */
@KlangScript.Function
fun String.pickmodRestart(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pickmodRestart(*args, callInfo = callInfo)

/**
 * Like [pickRestart] but wraps indices with modulo — [List] lookup, this string as index pattern.
 *
 * @param lookup List of items; indices wrap cyclically.
 * @return A pattern that restarts the selected item on each trigger.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".pickmodRestart(["bd", "sd", "hh"]).s().fast(2)
 * ```
 */
fun String.pickmodRestart(lookup: List<PatternLike>): SprudelPattern =
    this.toVoiceValuePattern().pickmodRestart(lookup)

/**
 * Like [pickRestart] but wraps indices with modulo — [Map] lookup, this string as key pattern.
 *
 * @param lookup Map of string keys; unmatched keys produce no output.
 * @return A pattern that restarts the selected item on each trigger.
 *
 * ```KlangScript(Playable)
 * "<a!2 [a,b] b>".pickmodRestart({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
fun String.pickmodRestart(lookup: Map<String, PatternLike>): SprudelPattern =
    this.toVoiceValuePattern().pickmodRestart(lookup)

/**
 * Returns a [PatternMapperFn] that selects from a lookup with modulo indices and restarts the chosen pattern.
 *
 * Like [pickRestart] but wraps indices cyclically. Apply the returned mapper using `.apply()`.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A [PatternMapperFn] that maps a source pattern to a modulo restart-pick result.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".apply(pickmodRestart("bd", "sd", "hh")).s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickmodRestart, pickRestart, modulo, restart, trigger, index
 */
@KlangScript.Function
fun pickmodRestart(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pickmodRestart(*args, callInfo = callInfo) }

/**
 * Returns a [PatternMapperFn] that selects from a [List] with modulo indices and restarts.
 *
 * @param lookup List of items; source pattern values are used as integer indices (modulo-wrapped).
 * @return A [PatternMapperFn] that maps a source pattern to a modulo restart-pick result.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".apply(pickmodRestart(["bd", "sd", "hh"])).s().fast(2)
 * ```
 */
fun pickmodRestart(lookup: List<PatternLike>): PatternMapperFn = { p -> p.pickmodRestart(lookup) }

/**
 * Returns a [PatternMapperFn] that selects from a [Map] lookup and restarts the chosen pattern.
 *
 * @param lookup Map of string keys; source pattern values are used as keys.
 * @return A [PatternMapperFn] that maps a source pattern to a modulo restart-pick result.
 *
 * ```KlangScript(Playable)
 * "<a!2 [a,b] b>".apply(pickmodRestart({a: "bd(3,8)", b: "sd sd"})).s()
 * ```
 */
fun pickmodRestart(lookup: Map<String, PatternLike>): PatternMapperFn = { p -> p.pickmodRestart(lookup) }

/**
 * Chains a pickmodRestart onto this [PatternMapperFn]; modulo indices, restarts on trigger.
 */
@KlangScript.Function
fun PatternMapperFn.pickmodRestart(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pickmodRestart(*args, callInfo = callInfo) }

/**
 * Chains a pickmodRestart from a [List] lookup onto this [PatternMapperFn].
 */
fun PatternMapperFn.pickmodRestart(lookup: List<PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickmodRestart(lookup) }

/**
 * Chains a pickmodRestart from a [Map] lookup onto this [PatternMapperFn].
 */
fun PatternMapperFn.pickmodRestart(lookup: Map<String, PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickmodRestart(lookup) }
