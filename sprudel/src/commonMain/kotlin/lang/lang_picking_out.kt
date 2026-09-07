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
import io.peekandpoke.klang.sprudel.pattern.MapPattern

// -- pickout() ----------------------------------------------------------------------------------------------------------------------------

private fun applyPickOuter(
    lookup: Any,
    pat: SprudelPattern,
    modulo: Boolean,
    baseLocation: SourceLocation? = null,
): SprudelPattern {
    // Validate and reify lookup
    val reifiedLookup = reifyLookup(lookup, baseLocation) ?: return silence
    val isList = lookup is List<*>

    // Create key extractor
    val keyExtractor: (SprudelVoiceData, Boolean, Int) -> Any? = if (isList) {
        { data, mod, len -> extractIndex(data, mod, len) }
    } else {
        { data, _, _ -> extractKey(data) }
    }

    // PickOuter sets the whole to the selector event's timespan (outerJoin behavior)
    // This ensures all picked events have onset at the selector's beginning
    return BindPattern(
        outer = pat,
        transform = { selectorEvent ->
            val key = keyExtractor(selectorEvent.data, modulo, reifiedLookup.size)
            val pickedPattern = if (key != null) reifiedLookup[key] else null

            // Wrap the picked pattern to set whole to selector's whole
            pickedPattern?.let { pattern ->
                MapPattern(pattern) { events ->
                    events.map { event -> event.copy(whole = selectorEvent.whole) }
                }
            }
        }
    )
}

/** Helper to dispatch pickOut calls */
private fun dispatchPickOuter(
    lookup: Any?,
    pat: SprudelPattern,
    modulo: Boolean,
    baseLocation: SourceLocation? = null,
): SprudelPattern {
    if (lookup == null) return silence

    return when (lookup) {
        is List<*> -> {
            @Suppress("UNCHECKED_CAST")
            applyPickOuter(lookup as List<Any>, pat, modulo, baseLocation)
        }

        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            applyPickOuter(lookup as Map<String, Any>, pat, modulo, baseLocation)
        }

        else -> silence
    }
}

private fun applyPickOut(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>, baseLocation: SourceLocation?): SprudelPattern {
    if (args.isEmpty()) return pattern
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    return dispatchPickOuter(lookup, pattern, modulo = false, baseLocation)
}

/**
 * Like [pick] but uses outer-join semantics: all selected events fire at the selector's onset.
 *
 * Whereas [pick] preserves the selected pattern's internal timing (inner-join), `pickOut`
 * forces every resulting event to share the onset of the selecting event. Indices are clamped.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map for key-based lookup.
 * @return A pattern playing the selected items with onset timing driven by this pattern's values.
 *
 * ```KlangScript(Playable)
 * seq("<0 1 2!2 3>").pickOut("g a", "e f", "f g f g" , "g c d").note()
 * ```
 *
 * ```KlangScript(Playable)
 * seq("<0 1 [2,0]>").pickOut("bd sd", "cp cp", "hh hh").s()
 * ```
 *
 * @category structural
 * @tags pickOut, pick, select, index, outer, outerJoin
 */
@KlangScript.Function
fun SprudelPattern.pickOut(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPickOut(this, args.toList().asSprudelDslArgs(callInfo), callInfo?.receiverLocation)

/**
 * Like [pick] but uses outer-join semantics — [List] lookup using this pattern's event values.
 *
 * @param lookup List of items; indices are clamped to valid bounds.
 * @return A pattern playing the selected items with onset timing from this pattern.
 *
 * ```KlangScript(Playable)
 * seq("<0 1 2!2 3>").pickOut(["g a", "e f", "f g f g" , "g c d"]).note()
 * ```
 */
fun SprudelPattern.pickOut(lookup: List<PatternLike>): SprudelPattern =
    applyPickOut(this, listOf(lookup).asSprudelDslArgs(), null)

/**
 * Like [pick] but uses outer-join semantics — [Map] lookup using this pattern's event values as keys.
 *
 * @param lookup Map of string keys to items; unmatched keys produce no output.
 * @return A pattern playing the selected items with onset timing from this pattern.
 *
 * ```KlangScript(Playable)
 * seq("<a!2 [a,b] b>").pickOut({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
fun SprudelPattern.pickOut(lookup: Map<String, Any>): SprudelPattern =
    applyPickOut(this, listOf(lookup).asSprudelDslArgs(), null)

/**
 * Like [pick] but uses outer-join semantics — this string is parsed as a mini-notation index pattern.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A pattern with onset timing from this string's parsed values; selected items are outer-joined.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".pickOut("bd", "sd", "hh").s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickOut, pick, select, index, outer, outerJoin
 */
@KlangScript.Function
fun String.pickOut(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pickOut(*args, callInfo = callInfo)

/**
 * Like [pick] but uses outer-join semantics — [List] lookup, this string as index pattern.
 *
 * @param lookup List of items; integer indices, clamped.
 * @return A pattern with onset timing from this string; selected items are outer-joined.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".pickOut(["bd", "sd", "hh"]).s().fast(2)
 * ```
 */
fun String.pickOut(lookup: List<PatternLike>): SprudelPattern =
    this.toVoiceValuePattern().pickOut(lookup)

/**
 * Like [pick] but uses outer-join semantics — [Map] lookup, this string as key pattern.
 *
 * @param lookup Map of string keys; unmatched keys produce no output.
 * @return A pattern with onset timing from this string; selected items are outer-joined.
 *
 * ```KlangScript(Playable)
 * "<a!2 [a,b] b>".pickOut({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
fun String.pickOut(lookup: Map<String, Any>): SprudelPattern =
    this.toVoiceValuePattern().pickOut(lookup)

/**
 * Returns a [PatternMapperFn] that selects from a lookup with outer-join semantics, clamped indices.
 *
 * Like [pick] but forces all selected events to fire at the selector's onset. Apply using `.apply()`.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A [PatternMapperFn] that maps a source pattern to an outer-join pick result.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".apply(pickOut("bd", "sd", "hh")).s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickOut, pick, select, index, outer, outerJoin
 */
@KlangScript.Function
fun pickOut(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pickOut(*args, callInfo = callInfo) }

/**
 * Returns a [PatternMapperFn] that selects from a [List] lookup with outer-join semantics.
 *
 * @param lookup List of items; source pattern values are used as zero-based indices (clamped).
 * @return A [PatternMapperFn] that maps a source pattern to an outer-join pick result.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".apply(pickOut(["bd", "sd", "hh"])).s().fast(2)
 * ```
 */
fun pickOut(lookup: List<PatternLike>): PatternMapperFn = { p -> p.pickOut(lookup) }

/**
 * Returns a [PatternMapperFn] that selects from a [Map] lookup with outer-join semantics.
 *
 * @param lookup Map of string keys; source pattern values are used as keys.
 * @return A [PatternMapperFn] that maps a source pattern to an outer-join pick result.
 *
 * ```KlangScript(Playable)
 * "<a!2 [a,b] b>".apply(pickOut({a: "bd(3,8)", b: "sd sd"})).s()
 * ```
 */
fun pickOut(lookup: Map<String, Any>): PatternMapperFn = { p -> p.pickOut(lookup) }

/**
 * Chains a pickOut onto this [PatternMapperFn]; outer-join semantics, clamped indices.
 */
@KlangScript.Function
fun PatternMapperFn.pickOut(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pickOut(*args, callInfo = callInfo) }

/**
 * Chains a pickOut from a [List] lookup onto this [PatternMapperFn]; outer-join, clamped indices.
 */
fun PatternMapperFn.pickOut(lookup: List<PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickOut(lookup) }

/**
 * Chains a pickOut from a [Map] lookup onto this [PatternMapperFn]; outer-join, string keys.
 */
fun PatternMapperFn.pickOut(lookup: Map<String, Any>): PatternMapperFn =
    this.chain { p -> p.pickOut(lookup) }

// -- pickmodOut() -----------------------------------------------------------------------------------------------------

private fun applyPickmodOut(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>, baseLocation: SourceLocation?): SprudelPattern {
    if (args.isEmpty()) return pattern
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    return dispatchPickOuter(lookup, pattern, modulo = true, baseLocation)
}

/**
 * Like [pickOut] but wraps out-of-bounds indices with modulo arithmetic.
 *
 * Outer-join semantics (onset from selector) combined with modulo index wrapping.
 * Indices wrap cyclically; negative indices are handled correctly.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map for key-based lookup.
 * @return A pattern with onset timing from this pattern; indices wrap cyclically.
 *
 * ```KlangScript(Playable)
 * seq("<0 1 2!2 3>").pickmodOut("g a", "e f", "f g f g" , "g c d").note()
 * ```
 *
 * ```KlangScript(Playable)
 * seq("<0 1 [2,0]>").pickmodOut("bd sd", "cp cp", "hh hh").s()
 * ```
 *
 * @category structural
 * @tags pickmodOut, pickOut, pickmod, modulo, select, index, outer
 */
@KlangScript.Function
fun SprudelPattern.pickmodOut(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPickmodOut(this, args.toList().asSprudelDslArgs(callInfo), callInfo?.receiverLocation)

/**
 * Like [pickOut] but wraps indices with modulo — [List] lookup using this pattern's event values.
 *
 * @param lookup List of items; indices wrap cyclically with modulo.
 * @return A pattern with onset timing from this pattern; selected items are outer-joined.
 *
 * ```KlangScript(Playable)
 * seq("<0 1 2!2 3>").pickmodOut(["g a", "e f", "f g f g" , "g c d"]).note()
 * ```
 */
fun SprudelPattern.pickmodOut(lookup: List<PatternLike>): SprudelPattern =
    applyPickmodOut(this, listOf(lookup).asSprudelDslArgs(), null)

/**
 * Like [pickOut] but wraps indices with modulo — [Map] lookup using this pattern's event values as keys.
 *
 * @param lookup Map of string keys; unmatched keys produce no output.
 * @return A pattern with onset timing from this pattern; selected items are outer-joined.
 *
 * ```KlangScript(Playable)
 * seq("<a!2 [a,b] b>").pickmodOut({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
fun SprudelPattern.pickmodOut(lookup: Map<String, Any>): SprudelPattern =
    applyPickmodOut(this, listOf(lookup).asSprudelDslArgs(), null)

/**
 * Like [pickOut] but wraps indices with modulo — this string is parsed as a mini-notation index pattern.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A pattern with onset timing from this string's parsed values; indices wrap cyclically.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".pickmodOut("bd", "sd", "hh").s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickmodOut, pickOut, pickmod, modulo, select, index, outer
 */
@KlangScript.Function
fun String.pickmodOut(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pickmodOut(*args, callInfo = callInfo)

/**
 * Like [pickOut] but wraps indices with modulo — [List] lookup, this string as index pattern.
 *
 * @param lookup List of items; indices wrap cyclically with modulo.
 * @return A pattern with onset timing from this string; selected items are outer-joined.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".pickmodOut(["bd", "sd", "hh"]).s().fast(2)
 * ```
 */
fun String.pickmodOut(lookup: List<PatternLike>): SprudelPattern =
    this.toVoiceValuePattern().pickmodOut(lookup)

/**
 * Like [pickOut] but wraps indices with modulo — [Map] lookup, this string as key pattern.
 *
 * @param lookup Map of string keys; unmatched keys produce no output.
 * @return A pattern with onset timing from this string; selected items are outer-joined.
 *
 * ```KlangScript(Playable)
 * "<a!2 [a,b] b>".pickmodOut({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
fun String.pickmodOut(lookup: Map<String, Any>): SprudelPattern =
    this.toVoiceValuePattern().pickmodOut(lookup)

/**
 * Returns a [PatternMapperFn] that selects from a lookup with outer-join semantics and modulo indices.
 *
 * Like [pickOut] but wraps indices cyclically. Apply the returned mapper using `.apply()`.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A [PatternMapperFn] that maps a source pattern to an outer-join modulo-pick result.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".apply(pickmodOut("bd", "sd", "hh")).s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickmodOut, pickOut, pickmod, modulo, select, index, outer
 */
@KlangScript.Function
fun pickmodOut(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pickmodOut(*args, callInfo = callInfo) }

/**
 * Returns a [PatternMapperFn] that selects from a [List] lookup with outer-join and modulo indices.
 *
 * @param lookup List of items; source pattern values are used as integer indices (modulo-wrapped).
 * @return A [PatternMapperFn] that maps a source pattern to an outer-join modulo-pick result.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 1>".apply(pickmodOut(["bd", "sd", "hh"])).s().fast(2)
 * ```
 */
fun pickmodOut(lookup: List<PatternLike>): PatternMapperFn = { p -> p.pickmodOut(lookup) }

/**
 * Returns a [PatternMapperFn] that selects from a [Map] lookup with outer-join semantics.
 *
 * @param lookup Map of string keys; source pattern values are used as keys.
 * @return A [PatternMapperFn] that maps a source pattern to an outer-join map-pick result.
 *
 * ```KlangScript(Playable)
 * "<a!2 [a,b] b>".apply(pickmodOut({a: "bd(3,8)", b: "sd sd"})).s()
 * ```
 */
fun pickmodOut(lookup: Map<String, Any>): PatternMapperFn = { p -> p.pickmodOut(lookup) }

/**
 * Chains a pickmodOut onto this [PatternMapperFn]; outer-join semantics, modulo indices.
 */
@KlangScript.Function
fun PatternMapperFn.pickmodOut(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pickmodOut(*args, callInfo = callInfo) }

/**
 * Chains a pickmodOut from a [List] lookup onto this [PatternMapperFn]; outer-join, modulo indices.
 */
fun PatternMapperFn.pickmodOut(lookup: List<PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickmodOut(lookup) }

/**
 * Chains a pickmodOut from a [Map] lookup onto this [PatternMapperFn]; outer-join, string keys.
 */
fun PatternMapperFn.pickmodOut(lookup: Map<String, Any>): PatternMapperFn =
    this.chain { p -> p.pickmodOut(lookup) }
