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
import io.peekandpoke.klang.sprudel.pattern.PickSqueezePattern

// -- inhabit() ----------------------------------------------------------------------------------------------------------------------------

private fun applyInhabit(
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

    // Create and return the pick squeeze pattern
    return PickSqueezePattern(
        selector = pat,
        lookup = reifiedLookup,
        modulo = modulo,
        extractKey = keyExtractor
    )
}

/** Helper to dispatch inhabit calls based on lookup type */
private fun dispatchInhabit(
    lookup: Any?,
    pat: SprudelPattern,
    modulo: Boolean,
    baseLocation: SourceLocation? = null,
): SprudelPattern {
    if (lookup == null) return silence

    return when (lookup) {
        is List<*> -> {
            @Suppress("UNCHECKED_CAST")
            applyInhabit(lookup as List<Any>, pat, modulo, baseLocation)
        }

        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            applyInhabit(lookup as Map<String, Any>, pat, modulo, baseLocation)
        }

        else -> silence
    }
}

private fun applyInhabitExtension(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>, baseLocation: SourceLocation?): SprudelPattern {
    if (args.isEmpty()) return pattern
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    return dispatchInhabit(lookup, pattern, modulo = false, baseLocation)
}

private fun applyInhabitTopLevel(args: List<SprudelDslArg<Any?>>, modulo: Boolean): PatternMapperFn {
    if (args.size < 2) {
        return { silence }
    }

    val first = args[0].value
    val lookup: Any
    val patArg: SprudelDslArg<Any?>
    val lookupLocation: SourceLocation?

    if (first is List<*> || first is Map<*, *>) {
        lookup = first
        lookupLocation = args[0].location
        patArg = args[1]
    } else {
        lookup = args.dropLast(1).map { it.value }
        lookupLocation = args.firstOrNull()?.location
        patArg = args.last()
    }

    val pat = listOf(patArg).toPattern(voiceValueModifier)
    return { dispatchInhabit(lookup, pat, modulo, lookupLocation) }
}

/**
 * Selects patterns from a list or map by index and squeezes each into the trigger's timespan.
 *
 * The last argument (or the second when the first is a list/map) is the index pattern.
 * Selected patterns are squeezed so their full cycle fits within the selecting event's duration.
 * Indices are clamped to valid bounds.
 *
 * ```KlangScript(Playable)
 * "<0 1>".apply(inhabit("bd sd hh", "rim cp", n("0 1 2 0"))).s()   // picked pattern fills event duration
 * ```
 *
 * ```KlangScript(Playable)
 * "<0 1>".apply(inhabit(["c3 e3", "g3 b3"], n("0 1 0")))          // each chosen pattern is squeezed in
 * ```
 *
 * @alias pickSqueeze
 * @category structural
 * @tags inhabit, pickSqueeze, squeeze, select, index, lookup
 */
@KlangScript.Function
fun SprudelPattern.inhabit(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyInhabitExtension(this, args.toList().asSprudelDslArgs(callInfo), callInfo?.receiverLocation)

/** Selects patterns from a [List] lookup by clamped index, squeezing each into this pattern's event timespans. */
fun SprudelPattern.inhabit(lookup: List<Any>): SprudelPattern =
    applyInhabitExtension(this, listOf(lookup).asSprudelDslArgs(), null)

/** Selects patterns from a [Map] lookup by string key, squeezing each into this pattern's event timespans. */
fun SprudelPattern.inhabit(lookup: Map<String, Any>): SprudelPattern =
    applyInhabitExtension(this, listOf(lookup).asSprudelDslArgs(), null)

/**
 * Selects patterns from a list by index and squeezes each into the timespan of the trigger.
 *
 * Each event of this string pattern picks a pattern from the lookup by index (clamped) and
 * squeezes it so that the full cycle of the chosen pattern fits within the duration of the event.
 * This is equivalent to [squeeze] with arguments reversed.
 *
 * ```KlangScript(Playable)
 * "0 1 2".inhabit("bd sd hh", "rim cp", "hh*4").s()         // chosen pattern fills event
 * ```
 *
 * ```KlangScript(Playable)
 * "0 1".inhabit(note("c3 e3 g3"), note("b3 d4"))             // pattern squeezed per index
 * ```
 *
 * @alias pickSqueeze
 * @category structural
 * @tags inhabit, pickSqueeze, squeeze, select, index, lookup
 */
@KlangScript.Function
fun String.inhabit(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).inhabit(*args, callInfo = callInfo)

/** Selects patterns from a [List] lookup by clamped index, squeezing each into this string pattern's event timespans. */
fun String.inhabit(lookup: List<Any>): SprudelPattern =
    this.toVoiceValuePattern().inhabit(lookup)

/** Selects patterns from a [Map] lookup by string key, squeezing each into this string pattern's event timespans. */
fun String.inhabit(lookup: Map<String, Any>): SprudelPattern =
    this.toVoiceValuePattern().inhabit(lookup)

/**
 * Returns a [PatternMapperFn] that selects patterns from a lookup by index and squeezes them into event timespans.
 *
 * Like [inhabit] but as a top-level mapper: the last argument is the selector pattern and the
 * preceding arguments form the lookup. Indices are clamped to valid bounds.
 *
 * ```KlangScript(Playable)
 * "<0 1>".apply(inhabit("bd sd hh", "rim cp", n("0 1 2 0"))).s()
 * ```
 *
 * @alias pickSqueeze
 * @category structural
 * @tags inhabit, pickSqueeze, squeeze, select, index, lookup
 */
@KlangScript.Function
fun inhabit(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    applyInhabitTopLevel(args.toList().asSprudelDslArgs(callInfo), modulo = false)

/** Returns a [PatternMapperFn] that selects from a [List] lookup by clamped index and squeezes into event timespans. */
fun inhabit(lookup: List<Any>): PatternMapperFn =
    applyInhabitTopLevel(listOf(lookup).asSprudelDslArgs(), modulo = false)

/** Returns a [PatternMapperFn] that selects from a [Map] lookup by string key and squeezes into event timespans. */
fun inhabit(lookup: Map<String, Any>): PatternMapperFn =
    applyInhabitTopLevel(listOf(lookup).asSprudelDslArgs(), modulo = false)

/** Chains an inhabit onto this [PatternMapperFn]; clamped index, squeeze semantics. */
@KlangScript.Function
fun PatternMapperFn.inhabit(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain(applyInhabitTopLevel(args.toList().asSprudelDslArgs(callInfo), modulo = false))

/** Chains an inhabit from a [List] lookup onto this [PatternMapperFn]; clamped index, squeeze semantics. */
fun PatternMapperFn.inhabit(lookup: List<Any>): PatternMapperFn =
    this.chain(applyInhabitTopLevel(listOf(lookup).asSprudelDslArgs(), modulo = false))

/** Chains an inhabit from a [Map] lookup onto this [PatternMapperFn]; string keys, squeeze semantics. */
fun PatternMapperFn.inhabit(lookup: Map<String, Any>): PatternMapperFn =
    this.chain(applyInhabitTopLevel(listOf(lookup).asSprudelDslArgs(), modulo = false))

// -- pickSqueeze() ------------------------------------------------------------------------------------------------------------------------

/**
 * Alias for [inhabit]. Selects patterns from a list by index and squeezes into event duration.
 *
 * @alias inhabit
 * @category structural
 * @tags pickSqueeze, inhabit, squeeze, select, index, lookup
 */
@KlangScript.Function
fun SprudelPattern.pickSqueeze(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.inhabit(*args, callInfo = callInfo)

/** Alias for [inhabit] — [List] lookup, clamped index, squeeze semantics. */
fun SprudelPattern.pickSqueeze(lookup: List<Any>): SprudelPattern = this.inhabit(lookup)

/** Alias for [inhabit] — [Map] lookup, string key, squeeze semantics. */
fun SprudelPattern.pickSqueeze(lookup: Map<String, Any>): SprudelPattern = this.inhabit(lookup)

/**
 * Alias for [inhabit] on a string pattern. Selects patterns from a lookup by index and squeezes into event duration.
 *
 * @alias inhabit
 * @category structural
 * @tags pickSqueeze, inhabit, squeeze, select, index, lookup
 */
@KlangScript.Function
fun String.pickSqueeze(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pickSqueeze(*args, callInfo = callInfo)

/** Alias for [inhabit] — [List] lookup on a string pattern; clamped index, squeeze semantics. */
fun String.pickSqueeze(lookup: List<Any>): SprudelPattern = this.toVoiceValuePattern().pickSqueeze(lookup)

/** Alias for [inhabit] — [Map] lookup on a string pattern; string key, squeeze semantics. */
fun String.pickSqueeze(lookup: Map<String, Any>): SprudelPattern = this.toVoiceValuePattern().pickSqueeze(lookup)

/**
 * Returns a [PatternMapperFn] that is an alias for [inhabit]; selects by clamped index and squeezes into event duration.
 *
 * @alias inhabit
 * @category structural
 * @tags pickSqueeze, inhabit, squeeze, select, index, lookup
 */
@KlangScript.Function
fun pickSqueeze(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    inhabit(*args, callInfo = callInfo)

/** Returns a [PatternMapperFn] — alias for [inhabit] — [List] lookup, clamped index, squeeze semantics. */
fun pickSqueeze(lookup: List<Any>): PatternMapperFn = inhabit(lookup)

/** Returns a [PatternMapperFn] — alias for [inhabit] — [Map] lookup, string key, squeeze semantics. */
fun pickSqueeze(lookup: Map<String, Any>): PatternMapperFn = inhabit(lookup)

/** Chains a pickSqueeze (alias for [inhabit]) onto this [PatternMapperFn]; clamped index, squeeze semantics. */
@KlangScript.Function
fun PatternMapperFn.pickSqueeze(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.inhabit(*args, callInfo = callInfo)

/** Chains a pickSqueeze from a [List] lookup onto this [PatternMapperFn]; clamped index, squeeze semantics. */
fun PatternMapperFn.pickSqueeze(lookup: List<Any>): PatternMapperFn = this.inhabit(lookup)

/** Chains a pickSqueeze from a [Map] lookup onto this [PatternMapperFn]; string keys, squeeze semantics. */
fun PatternMapperFn.pickSqueeze(lookup: Map<String, Any>): PatternMapperFn = this.inhabit(lookup)

// -- inhabitmod() -----------------------------------------------------------------------------------------------------

private fun applyInhabitmodExtension(
    pattern: SprudelPattern,
    args: List<SprudelDslArg<Any?>>,
    baseLocation: SourceLocation?
): SprudelPattern {
    if (args.isEmpty()) return pattern
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    return dispatchInhabit(lookup, pattern, modulo = true, baseLocation)
}

/**
 * Like [inhabit] but wraps out-of-bounds indices with modulo arithmetic.
 *
 * Selects patterns from the lookup using modulo-wrapped indices and squeezes each selected
 * pattern into the timespan of the event that triggered it. Negative indices are handled.
 *
 * ```KlangScript(Playable)
 * "0 1 2 3".inhabitmod("bd sd", "hh rim", "cp").s()       // 3→0 mod 3; squeezed
 * ```
 *
 * ```KlangScript(Playable)
 * n("0 5").inhabitmod(note("c3"), note("e3"), note("g3"))  // 5→2 mod 3; squeezed
 * ```
 *
 * @alias pickmodSqueeze
 * @category structural
 * @tags inhabitmod, pickmodSqueeze, modulo, squeeze, select, index, lookup
 */
@KlangScript.Function
fun SprudelPattern.inhabitmod(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyInhabitmodExtension(this, args.toList().asSprudelDslArgs(callInfo), callInfo?.receiverLocation)

/** Like [inhabit] with modulo — [List] lookup, modulo-wrapped index, squeezing into this pattern's event timespans. */
fun SprudelPattern.inhabitmod(lookup: List<Any>): SprudelPattern =
    applyInhabitmodExtension(this, listOf(lookup).asSprudelDslArgs(), null)

/** Like [inhabit] with modulo — [Map] lookup, string key, squeezing into this pattern's event timespans. */
fun SprudelPattern.inhabitmod(lookup: Map<String, Any>): SprudelPattern =
    applyInhabitmodExtension(this, listOf(lookup).asSprudelDslArgs(), null)

/**
 * Like [inhabit] but wraps out-of-bounds indices with modulo arithmetic — string receiver.
 *
 * This string is parsed as mini-notation to produce indices that select patterns from the
 * lookup with modulo wrapping. Selected patterns are squeezed into each event's timespan.
 *
 * ```KlangScript(Playable)
 * "0 1 2 3".inhabitmod("bd sd", "hh rim", "cp").s()       // 3→0 mod 3; squeezed
 * ```
 *
 * ```KlangScript(Playable)
 * "0 5".inhabitmod(note("c3"), note("e3"), note("g3"))     // 5→2 mod 3; squeezed
 * ```
 *
 * @alias pickmodSqueeze
 * @category structural
 * @tags inhabitmod, pickmodSqueeze, modulo, squeeze, select, index, lookup
 */
@KlangScript.Function
fun String.inhabitmod(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).inhabitmod(*args, callInfo = callInfo)

/** Like [inhabit] with modulo — [List] lookup on a string pattern; modulo-wrapped index, squeeze semantics. */
fun String.inhabitmod(lookup: List<Any>): SprudelPattern =
    this.toVoiceValuePattern().inhabitmod(lookup)

/** Like [inhabit] with modulo — [Map] lookup on a string pattern; string key, squeeze semantics. */
fun String.inhabitmod(lookup: Map<String, Any>): SprudelPattern =
    this.toVoiceValuePattern().inhabitmod(lookup)

/**
 * Returns a [PatternMapperFn] that is like [inhabit] but wraps indices with modulo.
 *
 * The last argument is the selector pattern and the preceding arguments form the lookup.
 * Indices wrap cyclically; negative indices are handled correctly.
 *
 * ```KlangScript(Playable)
 * "<0 1 2 3>".apply(inhabitmod("bd", "sd", "hh")).s()   // 3→0 mod 3; squeezed into event
 * ```
 *
 * @alias pickmodSqueeze
 * @category structural
 * @tags inhabitmod, pickmodSqueeze, modulo, squeeze, select, index, lookup
 */
@KlangScript.Function
fun inhabitmod(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    applyInhabitTopLevel(args.toList().asSprudelDslArgs(callInfo), modulo = true)

/** Returns a [PatternMapperFn] like [inhabit] with modulo — [List] lookup, modulo-wrapped index, squeeze semantics. */
fun inhabitmod(lookup: List<Any>): PatternMapperFn =
    applyInhabitTopLevel(listOf(lookup).asSprudelDslArgs(), modulo = true)

/** Returns a [PatternMapperFn] like [inhabit] with modulo — [Map] lookup, string key, squeeze semantics. */
fun inhabitmod(lookup: Map<String, Any>): PatternMapperFn =
    applyInhabitTopLevel(listOf(lookup).asSprudelDslArgs(), modulo = true)

/** Chains an inhabitmod onto this [PatternMapperFn]; modulo-wrapped index, squeeze semantics. */
@KlangScript.Function
fun PatternMapperFn.inhabitmod(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain(applyInhabitTopLevel(args.toList().asSprudelDslArgs(callInfo), modulo = true))

/** Chains an inhabitmod from a [List] lookup onto this [PatternMapperFn]; modulo-wrapped index, squeeze semantics. */
fun PatternMapperFn.inhabitmod(lookup: List<Any>): PatternMapperFn =
    this.chain(applyInhabitTopLevel(listOf(lookup).asSprudelDslArgs(), modulo = true))

/** Chains an inhabitmod from a [Map] lookup onto this [PatternMapperFn]; string keys, squeeze semantics. */
fun PatternMapperFn.inhabitmod(lookup: Map<String, Any>): PatternMapperFn =
    this.chain(applyInhabitTopLevel(listOf(lookup).asSprudelDslArgs(), modulo = true))

// -- pickmodSqueeze() -------------------------------------------------------------------------------------------------

/**
 * Alias for [inhabitmod]. Selects patterns by modulo-wrapped index and squeezes into events.
 *
 * @alias inhabitmod
 * @category structural
 * @tags pickmodSqueeze, inhabitmod, modulo, squeeze, select, index, lookup
 */
@KlangScript.Function
fun SprudelPattern.pickmodSqueeze(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.inhabitmod(*args, callInfo = callInfo)

/** Alias for [inhabitmod] — [List] lookup, modulo-wrapped index, squeeze semantics. */
fun SprudelPattern.pickmodSqueeze(lookup: List<Any>): SprudelPattern = this.inhabitmod(lookup)

/** Alias for [inhabitmod] — [Map] lookup, string key, squeeze semantics. */
fun SprudelPattern.pickmodSqueeze(lookup: Map<String, Any>): SprudelPattern = this.inhabitmod(lookup)

/**
 * Alias for [inhabitmod] on a string pattern. Selects patterns by modulo-wrapped index and squeezes into events.
 *
 * @alias inhabitmod
 * @category structural
 * @tags pickmodSqueeze, inhabitmod, modulo, squeeze, select, index, lookup
 */
@KlangScript.Function
fun String.pickmodSqueeze(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pickmodSqueeze(*args, callInfo = callInfo)

/** Alias for [inhabitmod] — [List] lookup on a string pattern; modulo-wrapped index, squeeze semantics. */
fun String.pickmodSqueeze(lookup: List<Any>): SprudelPattern = this.toVoiceValuePattern().pickmodSqueeze(lookup)

/** Alias for [inhabitmod] — [Map] lookup on a string pattern; string key, squeeze semantics. */
fun String.pickmodSqueeze(lookup: Map<String, Any>): SprudelPattern = this.toVoiceValuePattern().pickmodSqueeze(lookup)

/**
 * Returns a [PatternMapperFn] that is an alias for [inhabitmod]; selects by modulo-wrapped index and squeezes.
 *
 * @alias inhabitmod
 * @category structural
 * @tags pickmodSqueeze, inhabitmod, modulo, squeeze, select, index, lookup
 */
@KlangScript.Function
fun pickmodSqueeze(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    inhabitmod(*args, callInfo = callInfo)

/** Returns a [PatternMapperFn] — alias for [inhabitmod] — [List] lookup, modulo-wrapped index, squeeze semantics. */
fun pickmodSqueeze(lookup: List<Any>): PatternMapperFn = inhabitmod(lookup)

/** Returns a [PatternMapperFn] — alias for [inhabitmod] — [Map] lookup, string key, squeeze semantics. */
fun pickmodSqueeze(lookup: Map<String, Any>): PatternMapperFn = inhabitmod(lookup)

/** Chains a pickmodSqueeze (alias for [inhabitmod]) onto this [PatternMapperFn]; modulo-wrapped index, squeeze. */
@KlangScript.Function
fun PatternMapperFn.pickmodSqueeze(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.inhabitmod(*args, callInfo = callInfo)

/** Chains a pickmodSqueeze from a [List] lookup onto this [PatternMapperFn]; modulo-wrapped index, squeeze. */
fun PatternMapperFn.pickmodSqueeze(lookup: List<Any>): PatternMapperFn = this.inhabitmod(lookup)

/** Chains a pickmodSqueeze from a [Map] lookup onto this [PatternMapperFn]; string keys, squeeze semantics. */
fun PatternMapperFn.pickmodSqueeze(lookup: Map<String, Any>): PatternMapperFn = this.inhabitmod(lookup)

// -- squeeze() --------------------------------------------------------------------------------------------------------

private fun applySqueeze(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>, baseLocation: SourceLocation?): SprudelPattern {
    if (args.isEmpty()) return pattern
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    return dispatchInhabit(lookup, pattern, modulo = false, baseLocation)
}

/**
 * Squeezes patterns from a list into the timespans of events in this pattern (clamped index).
 *
 * The receiver pattern provides the indices; the arguments are the lookup patterns squeezed
 * into each event's duration. Equivalent to calling `inhabit(lookup, this)`.
 *
 * ```KlangScript(Playable)
 * n("0 1 2").squeeze("bd sd hh", "rim cp", "hh*4").s()  // receiver is index pattern
 * ```
 *
 * ```KlangScript(Playable)
 * "0 1".squeeze(note("c3 e3"), note("g3 b3"))            // string receiver; squeezes in
 * ```
 *
 * @category structural
 * @tags squeeze, inhabit, select, index, lookup
 */
@KlangScript.Function
fun SprudelPattern.squeeze(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applySqueeze(this, args.toList().asSprudelDslArgs(callInfo), callInfo?.receiverLocation)

/** Squeezes patterns from a [List] lookup into the timespans of events in this pattern (clamped index). */
fun SprudelPattern.squeeze(lookup: List<Any>): SprudelPattern =
    applySqueeze(this, listOf(lookup).asSprudelDslArgs(), null)

/** Squeezes patterns from a [Map] lookup into the timespans of events in this pattern (string key). */
fun SprudelPattern.squeeze(lookup: Map<String, Any>): SprudelPattern =
    applySqueeze(this, listOf(lookup).asSprudelDslArgs(), null)

/**
 * Squeezes patterns from a list into the timespans of events in this string pattern (clamped).
 *
 * This string is parsed as mini-notation to produce index values. Those values select from the
 * lookup with clamped out-of-bounds handling. Selected patterns are squeezed into each event's timespan.
 *
 * ```KlangScript(Playable)
 * "0 1".squeeze("bd sd hh", "rim cp").s()   // string receiver as index pattern
 * ```
 *
 * @category structural
 * @tags squeeze, inhabit, select, index, lookup
 */
@KlangScript.Function
fun String.squeeze(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).squeeze(*args, callInfo = callInfo)

/** Squeezes patterns from a [List] lookup into the timespans of events in this string pattern (clamped). */
fun String.squeeze(lookup: List<Any>): SprudelPattern =
    this.toVoiceValuePattern().squeeze(lookup)

/** Squeezes patterns from a [Map] lookup into the timespans of events in this string pattern (string key). */
fun String.squeeze(lookup: Map<String, Any>): SprudelPattern =
    this.toVoiceValuePattern().squeeze(lookup)

/**
 * Selects patterns from a list by index and squeezes them into the triggering event's timespan.
 *
 * Like [inhabit] but with arguments in the opposite order: the first argument is the selector
 * (index) pattern and the remaining arguments are the lookup patterns. Indices are clamped.
 *
 * ```KlangScript(Playable)
 * "<0 1>".apply(squeeze("bd sd hh", "rim cp")).s()   // selector first, then lookup
 * ```
 *
 * ```KlangScript(Playable)
 * "<0 1>".apply(squeeze(note("c3 e3"), note("g3 b3")))  // index → squeezed pattern
 * ```
 *
 * @category structural
 * @tags squeeze, inhabit, pickSqueeze, select, index, lookup
 */
@KlangScript.Function
fun squeeze(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.squeeze(*args, callInfo = callInfo) }

/** Returns a [PatternMapperFn] that selects from a [List] lookup by index and squeezes; clamped. */
fun squeeze(lookup: List<Any>): PatternMapperFn = { p -> p.squeeze(lookup) }

/** Returns a [PatternMapperFn] that selects from a [Map] lookup by string key and squeezes. */
fun squeeze(lookup: Map<String, Any>): PatternMapperFn = { p -> p.squeeze(lookup) }

/** Chains a squeeze onto this [PatternMapperFn]; clamped index, squeeze semantics. */
@KlangScript.Function
fun PatternMapperFn.squeeze(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.squeeze(*args, callInfo = callInfo) }

/** Chains a squeeze from a [List] lookup onto this [PatternMapperFn]; clamped index, squeeze semantics. */
fun PatternMapperFn.squeeze(lookup: List<Any>): PatternMapperFn =
    this.chain { p -> p.squeeze(lookup) }

/** Chains a squeeze from a [Map] lookup onto this [PatternMapperFn]; string keys, squeeze semantics. */
fun PatternMapperFn.squeeze(lookup: Map<String, Any>): PatternMapperFn =
    this.chain { p -> p.squeeze(lookup) }
