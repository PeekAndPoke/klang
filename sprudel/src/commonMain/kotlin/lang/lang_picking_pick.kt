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
import io.peekandpoke.klang.sprudel.pattern.BindPattern

// =====================================================================================================================
// Core pick() Implementation
// =====================================================================================================================

/**
 * Implementation of pick() - selects patterns from a lookup by index/key and flattens with innerJoin.
 *
 * @param lookup List or Map of patterns/values to pick from
 * @param pat Pattern providing the indices/keys for selection
 * @param modulo If true, wrap out-of-bounds indices; if false, clamp them
 * @param baseLocation Source location for error reporting
 */
private fun applyPickInner(
    lookup: Any,
    pat: SprudelPattern,
    modulo: Boolean,
    baseLocation: SourceLocation? = null,
): SprudelPattern {
    // Validate and reify lookup
    val reifiedLookup = reifyLookup(lookup, baseLocation) ?: return silence
    val isList = lookup is List<*>

    // Create key extractor function based on list vs map
    val keyExtractor: (SprudelVoiceData, Boolean, Int) -> Any? = if (isList) {
        { data, mod, len -> extractIndex(data, mod, len) }
    } else {
        { data, _, _ -> extractKey(data) }
    }

    // Create and return the pick pattern using generic BindPattern
    return BindPattern(
        outer = pat,
        transform = { selectorEvent ->
            val key = keyExtractor(selectorEvent.data, modulo, reifiedLookup.size)
            if (key != null) reifiedLookup[key] else null
        }
    )
}

/** Helper to dispatch pick calls based on lookup type */
private fun dispatchPick(
    lookup: Any?,
    pat: SprudelPattern,
    modulo: Boolean,
    baseLocation: SourceLocation? = null,
): SprudelPattern {
    if (lookup == null) return silence

    return when (lookup) {
        is List<*> -> {
            @Suppress("UNCHECKED_CAST")
            applyPickInner(lookup as List<Any>, pat, modulo, baseLocation)
        }

        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            applyPickInner(lookup as Map<String, Any>, pat, modulo, baseLocation)
        }

        else -> silence
    }
}

private fun applyPick(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>, baseLocation: SourceLocation?): SprudelPattern {
    if (args.isEmpty()) return pattern
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    return dispatchPick(lookup = lookup, pat = pattern, modulo = false, baseLocation = baseLocation)
}

/**
 * Selects patterns from a lookup using this pattern's values as indices, clamping out-of-bounds indices.
 *
 * Each event value is used as a zero-based integer index (or string key for map lookups) to
 * select a pattern from the vararg lookup. Out-of-bounds integer indices are clamped to the
 * nearest valid position. The selected pattern's timing structure is used (inner-join).
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map for key-based lookup.
 * @return A pattern playing the selected items in the order given by this pattern's values.
 *
 * ```KlangScript(Playable)
 * seq("<0 1 2!2 3>").pick("g a", "e f", "f g f g" , "g c d").note()
 * ```
 *
 * ```KlangScript(Playable)
 * seq("<0 1 [2,0]>").pick("bd sd", "cp cp", "hh hh").s()
 * ```
 *
 * @category structural
 * @tags pick, select, index, lookup
 */
@KlangScript.Function
fun SprudelPattern.pick(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPick(this, args.toList().asSprudelDslArgs(callInfo), callInfo?.receiverLocation)

/**
 * Selects patterns from a [List] lookup using this pattern's event values as zero-based indices.
 *
 * Convenience overload accepting an explicit [List]. Indices are clamped to valid bounds.
 *
 * @param lookup List of items to pick from; index 0 is first, last index is max.
 * @return A pattern playing the item at each event's integer index (clamped).
 *
 * ```KlangScript(Playable)
 * seq("<0 1 2!2 3>").pick(["g a", "e f", "f g f g" , "g c d"]).note()
 * ```
 */
fun SprudelPattern.pick(lookup: List<PatternLike>): SprudelPattern =
    applyPick(this, listOf(lookup).asSprudelDslArgs(), null)

/**
 * Selects patterns from a [Map] lookup using this pattern's event values as string keys.
 *
 * Convenience overload accepting an explicit [Map]. Events whose values have no matching key
 * produce no output.
 *
 * @param lookup Map of string keys to items; each event value is used as a lookup key.
 * @return A pattern playing the item for each matched key; unmatched keys produce silence.
 *
 * ```KlangScript(Playable)
 * seq("<a!2 [a,b] b>").pick({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
fun SprudelPattern.pick(lookup: Map<String, PatternLike>): SprudelPattern =
    applyPick(this, listOf(lookup).asSprudelDslArgs(), null)

/**
 * Selects patterns from a lookup using this string parsed as a mini-notation index pattern.
 *
 * The string is parsed as mini-notation to produce a sequence of index/key values. Those
 * values select from the vararg lookup items with clamped out-of-bounds handling.
 * Equivalent to `seq(this).pick(*args)`.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A pattern playing the selected items driven by this string's parsed values.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".pick("bd", "sd", "hh").s().fast(2)
 * ```
 *
 * @category structural
 * @tags pick, select, index, lookup
 */
@KlangScript.Function
fun String.pick(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pick(*args, callInfo = callInfo)

/**
 * Selects patterns from a [List] using this string parsed as a mini-notation index pattern.
 *
 * @param lookup List of items to pick from; integer indices, clamped.
 * @return A pattern playing the item at each event's integer index (clamped).
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".pick(["bd", "sd", "hh"]).s().fast(2)
 * ```
 */
fun String.pick(lookup: List<PatternLike>): SprudelPattern =
    this.toVoiceValuePattern().pick(lookup)

/**
 * Selects patterns from a [Map] using this string parsed as a mini-notation key pattern.
 *
 * @param lookup Map of string keys to items; each parsed event value is a lookup key.
 * @return A pattern playing the item for each matched key; unmatched keys produce silence.
 *
 * ```KlangScript(Playable)
 * "<a!2 [a,b] b>".pick({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
fun String.pick(lookup: Map<String, PatternLike>): SprudelPattern =
    this.toVoiceValuePattern().pick(lookup)

/**
 * Returns a [PatternMapperFn] that selects patterns from a lookup, clamping out-of-bounds indices.
 *
 * The source pattern's event values are used as zero-based indices (or string keys for maps)
 * to pick from the lookup. Apply the returned mapper to a pattern using `.apply()`. Indices
 * outside valid bounds are clamped to the nearest valid position.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A [PatternMapperFn] that maps a source pattern to a pick result.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".apply(pick("bd", "sd", "hh")).s().fast(2)
 * ```
 *
 * @category structural
 * @tags pick, select, index, lookup
 */
@KlangScript.Function
fun pick(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pick(*args, callInfo = callInfo) }

/**
 * Returns a [PatternMapperFn] that selects from a [List] lookup with clamped integer indices.
 *
 * @param lookup List of items; source pattern values are used as zero-based indices (clamped).
 * @return A [PatternMapperFn] that maps a source pattern to a pick result.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".apply(pick(["bd", "sd", "hh"])).s().fast(2)
 * ```
 */
fun pick(lookup: List<PatternLike>): PatternMapperFn = { p -> p.pick(lookup) }

/**
 * Returns a [PatternMapperFn] that selects from a [Map] lookup using string keys.
 *
 * @param lookup Map of string keys to items; source pattern values are used as keys.
 * @return A [PatternMapperFn] that maps a source pattern to a pick result.
 *
 * ```KlangScript(Playable)
 * "<a!2 [a,b] b>".apply(pick({a: "bd(3,8)", b: "sd sd"})).s()
 * ```
 */
fun pick(lookup: Map<String, PatternLike>): PatternMapperFn = { p -> p.pick(lookup) }

/**
 * Chains a pick onto this [PatternMapperFn]; the chained result picks from the given lookup.
 */
@KlangScript.Function
fun PatternMapperFn.pick(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pick(*args, callInfo = callInfo) }

/**
 * Chains a pick from a [List] lookup onto this [PatternMapperFn]; indices are clamped.
 */
fun PatternMapperFn.pick(lookup: List<PatternLike>): PatternMapperFn =
    this.chain { p -> p.pick(lookup) }

/**
 * Chains a pick from a [Map] lookup onto this [PatternMapperFn]; string keys are used.
 */
fun PatternMapperFn.pick(lookup: Map<String, PatternLike>): PatternMapperFn =
    this.chain { p -> p.pick(lookup) }

// -- pickmod() ----------------------------------------------------------------------------------------------------------------------------

private fun applyPickmod(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.isEmpty()) return pattern
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    val lookupLocation = args.firstOrNull()?.location
    return dispatchPick(lookup, pattern, modulo = true, lookupLocation)
}

/**
 * Like [pick] but wraps out-of-bounds indices with modulo arithmetic.
 *
 * Each event value is used as a zero-based integer index (or string key for map lookups).
 * Integer indices wrap cyclically — index 3 with a 3-item lookup maps to 0; negative
 * indices are handled correctly (e.g. -1 maps to the last item).
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map for key-based lookup.
 * @return A pattern playing the selected items with modulo-wrapped index resolution.
 *
 * ```KlangScript(Playable)
 * seq("<0 1 2!2 3>").pickmod("g a", "e f", "f g f g" , "g c d").note()
 * ```
 *
 * ```KlangScript(Playable)
 * seq("<0 1 [2,0]>").pickmod("bd sd", "cp cp", "hh hh").s()
 * ```
 *
 * @category structural
 * @tags pickmod, pick, modulo, wrap, index, lookup
 */
@KlangScript.Function
fun SprudelPattern.pickmod(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPickmod(this, args.toList().asSprudelDslArgs(callInfo))

/**
 * Like [pick] but wraps indices with modulo — [List] lookup using this pattern's event values.
 *
 * @param lookup List of items; indices wrap cyclically with modulo.
 * @return A pattern playing the item at each event's wrapped integer index.
 *
 * ```KlangScript(Playable)
 * seq("<0 1 2!2 3>").pickmod(["g a", "e f", "f g f g" , "g c d"]).note()
 * ```
 */
fun SprudelPattern.pickmod(lookup: List<PatternLike>): SprudelPattern =
    applyPickmod(this, listOf(lookup).asSprudelDslArgs())

/**
 * Like [pick] but wraps indices with modulo — [Map] lookup using this pattern's event values.
 *
 * @param lookup Map of string keys; source pattern event values are used as keys.
 * @return A pattern playing the item for each matched key; unmatched keys produce silence.
 *
 * ```KlangScript(Playable)
 * seq("<a!2 [a,b] b>").pickmod({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
fun SprudelPattern.pickmod(lookup: Map<String, PatternLike>): SprudelPattern =
    applyPickmod(this, listOf(lookup).asSprudelDslArgs())

/**
 * Like [pick] but wraps indices with modulo — this string is parsed as a mini-notation index pattern.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A pattern playing the selected items driven by this string's parsed values (modulo-wrapped).
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".pickmod("bd", "sd", "hh").s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickmod, pick, modulo, wrap, index, lookup
 */
@KlangScript.Function
fun String.pickmod(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pickmod(*args, callInfo = callInfo)

/**
 * Like [pick] but wraps indices with modulo — [List] lookup, this string as index pattern.
 *
 * @param lookup List of items; this string is parsed as mini-notation to produce integer indices.
 * @return A pattern playing the item at each event's wrapped integer index.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".pickmod(["bd", "sd", "hh"]).s().fast(2)
 * ```
 */
fun String.pickmod(lookup: List<PatternLike>): SprudelPattern =
    this.toVoiceValuePattern().pickmod(lookup)

/**
 * Like [pick] but wraps indices with modulo — [Map] lookup, this string as key pattern.
 *
 * @param lookup Map of string keys; this string is parsed as mini-notation to produce keys.
 * @return A pattern playing the item for each matched key; unmatched keys produce silence.
 *
 * ```KlangScript(Playable)
 * "<a!2 [a,b] b>".pickmod({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
fun String.pickmod(lookup: Map<String, PatternLike>): SprudelPattern =
    this.toVoiceValuePattern().pickmod(lookup)

/**
 * Returns a [PatternMapperFn] that selects from a lookup with modulo-wrapped index values.
 *
 * Like [pick] but indices wrap cyclically. Apply the returned mapper to a pattern using `.apply()`.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A [PatternMapperFn] that maps a source pattern to a modulo-pick result.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".apply(pickmod("bd", "sd", "hh")).s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickmod, pick, modulo, wrap, index, lookup
 */
@KlangScript.Function
fun pickmod(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pickmod(*args, callInfo = callInfo) }

/**
 * Returns a [PatternMapperFn] that selects from a [List] lookup with modulo-wrapped indices.
 *
 * @param lookup List of items; source pattern values are used as integer indices (modulo-wrapped).
 * @return A [PatternMapperFn] that maps a source pattern to a modulo-pick result.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".apply(pickmod(["bd", "sd", "hh"])).s().fast(2)
 * ```
 */
fun pickmod(lookup: List<PatternLike>): PatternMapperFn = { p -> p.pickmod(lookup) }

/**
 * Returns a [PatternMapperFn] that selects from a [Map] lookup using string keys.
 *
 * @param lookup Map of string keys to items; source pattern values are used as keys.
 * @return A [PatternMapperFn] that maps a source pattern to a map-pick result.
 *
 * ```KlangScript(Playable)
 * "<a!2 [a,b] b>".apply(pickmod({a: "bd(3,8)", b: "sd sd"})).s()
 * ```
 */
fun pickmod(lookup: Map<String, PatternLike>): PatternMapperFn = { p -> p.pickmod(lookup) }

/**
 * Chains a modulo-pick onto this [PatternMapperFn]; indices wrap cyclically.
 */
@KlangScript.Function
fun PatternMapperFn.pickmod(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pickmod(*args, callInfo = callInfo) }

/**
 * Chains a modulo-pick from a [List] lookup onto this [PatternMapperFn].
 */
fun PatternMapperFn.pickmod(lookup: List<PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickmod(lookup) }

/**
 * Chains a modulo-pick from a [Map] lookup onto this [PatternMapperFn].
 */
fun PatternMapperFn.pickmod(lookup: Map<String, PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickmod(lookup) }
