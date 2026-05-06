@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.common.SourceLocationChain
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.SprudelVoiceValue
import io.peekandpoke.klang.sprudel._bind
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern
import io.peekandpoke.klang.sprudel.pattern.BindPattern
import io.peekandpoke.klang.sprudel.pattern.MapPattern
import io.peekandpoke.klang.sprudel.pattern.PickResetPattern
import io.peekandpoke.klang.sprudel.pattern.PickRestartPattern
import io.peekandpoke.klang.sprudel.pattern.PickSqueezePattern
import kotlin.math.floor
// =====================================================================================================================
// Helper Functions
// =====================================================================================================================

/**
 * Converts a lookup parameter (List or Map) to a map of reified patterns.
 * Returns null if the lookup is invalid or empty.
 *
 * @param lookup The lookup data (List or Map)
 * @param baseLocation Source location to use for parsed patterns (for error reporting)
 */
private fun reifyLookup(lookup: Any, baseLocation: SourceLocation? = null): Map<Any, SprudelPattern>? {
    val lookupMap: Map<Any, Any> = when (lookup) {
        is List<*> -> {
            // Convert list to map with integer keys
            lookup.withIndex().associate { (idx, value) -> idx to (value ?: return null) }
        }

        is Map<*, *> -> {
            // Use map as-is, converting keys to strings
            lookup.mapKeys { (k, _) -> k?.toString() ?: return null }
                .mapValues { (_, v) -> v ?: return null }
        }

        else -> return null
    }

    if (lookupMap.isEmpty()) return null

    // Convert all values to patterns
    return lookupMap.mapValues { (_, value) ->
        when (value) {
            is SprudelPattern -> value
            is String -> {
                // Parse strings as mini-notation patterns with source location
                val atomFactory = { text: String, sourceLocations: SourceLocationChain? ->
                    AtomicPattern(
                        data = SprudelVoiceData.empty.voiceValueModifier(text),
                        sourceLocations = sourceLocations,
                    )
                }
                parseMiniNotation(input = value, baseLocation = baseLocation, atomFactory = atomFactory)
            }

            else -> {
                // Wrap other values (numbers, etc.) in atomic pattern with default modifier
                AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(value))
            }
        }
    }
}

/**
 * Safely extracts an integer index from an event data.
 */
private fun extractIndex(data: SprudelVoiceData, modulo: Boolean, len: Int): Int? {
    if (len == 0) return null

    // Try value, then note (as number?), then soundIndex
    val value = data.value ?: data.note ?: data.soundIndex ?: return null

    // Handle various numeric types
    val numericValue: Double = value.asDoubleOrNull() ?: return null

    val idx = floor(numericValue).toInt()

    return if (modulo) {
        // Modulo wrapping (handle negatives properly)
        ((idx % len) + len) % len
    } else {
        // Clamp to valid range
        idx.coerceIn(0, len - 1)
    }
}

/**
 * Safely extracts a string key from an event data.
 */
private fun extractKey(data: SprudelVoiceData): String {
    val value = data.value ?: data.note ?: data.soundIndex

    return when (value) {
        null -> ""
        is SprudelVoiceValue -> value.asString
        is String -> value
        is Number -> value.toString()
        else -> value.toString()
    }
}

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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
fun pick(lookup: Map<String, PatternLike>): PatternMapperFn = { p -> p.pick(lookup) }

/**
 * Chains a pick onto this [PatternMapperFn]; the chained result picks from the given lookup.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pick(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pick(*args, callInfo = callInfo) }

/**
 * Chains a pick from a [List] lookup onto this [PatternMapperFn]; indices are clamped.
 */
@SprudelDsl
fun PatternMapperFn.pick(lookup: List<PatternLike>): PatternMapperFn =
    this.chain { p -> p.pick(lookup) }

/**
 * Chains a pick from a [Map] lookup onto this [PatternMapperFn]; string keys are used.
 */
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
fun pickmod(lookup: Map<String, PatternLike>): PatternMapperFn = { p -> p.pickmod(lookup) }

/**
 * Chains a modulo-pick onto this [PatternMapperFn]; indices wrap cyclically.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pickmod(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pickmod(*args, callInfo = callInfo) }

/**
 * Chains a modulo-pick from a [List] lookup onto this [PatternMapperFn].
 */
@SprudelDsl
fun PatternMapperFn.pickmod(lookup: List<PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickmod(lookup) }

/**
 * Chains a modulo-pick from a [Map] lookup onto this [PatternMapperFn].
 */
@SprudelDsl
fun PatternMapperFn.pickmod(lookup: Map<String, PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickmod(lookup) }

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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
fun pickOut(lookup: Map<String, Any>): PatternMapperFn = { p -> p.pickOut(lookup) }

/**
 * Chains a pickOut onto this [PatternMapperFn]; outer-join semantics, clamped indices.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pickOut(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pickOut(*args, callInfo = callInfo) }

/**
 * Chains a pickOut from a [List] lookup onto this [PatternMapperFn]; outer-join, clamped indices.
 */
@SprudelDsl
fun PatternMapperFn.pickOut(lookup: List<PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickOut(lookup) }

/**
 * Chains a pickOut from a [Map] lookup onto this [PatternMapperFn]; outer-join, string keys.
 */
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
fun pickmodOut(lookup: Map<String, Any>): PatternMapperFn = { p -> p.pickmodOut(lookup) }

/**
 * Chains a pickmodOut onto this [PatternMapperFn]; outer-join semantics, modulo indices.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pickmodOut(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pickmodOut(*args, callInfo = callInfo) }

/**
 * Chains a pickmodOut from a [List] lookup onto this [PatternMapperFn]; outer-join, modulo indices.
 */
@SprudelDsl
fun PatternMapperFn.pickmodOut(lookup: List<PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickmodOut(lookup) }

/**
 * Chains a pickmodOut from a [Map] lookup onto this [PatternMapperFn]; outer-join, string keys.
 */
@SprudelDsl
fun PatternMapperFn.pickmodOut(lookup: Map<String, Any>): PatternMapperFn =
    this.chain { p -> p.pickmodOut(lookup) }

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
 * inhabit("bd sd hh", "rim cp", n("0 1 2 0"))   // picked pattern fills event duration
 * ```
 *
 * ```KlangScript(Playable)
 * inhabit(["c3 e3", "g3 b3"], n("0 1 0"))        // each chosen pattern is squeezed in
 * ```
 *
 * @alias pickSqueeze
 * @category structural
 * @tags inhabit, pickSqueeze, squeeze, select, index, lookup
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.inhabit(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyInhabitExtension(this, args.toList().asSprudelDslArgs(callInfo), callInfo?.receiverLocation)

/** Selects patterns from a [List] lookup by clamped index, squeezing each into this pattern's event timespans. */
@SprudelDsl
fun SprudelPattern.inhabit(lookup: List<Any>): SprudelPattern =
    applyInhabitExtension(this, listOf(lookup).asSprudelDslArgs(), null)

/** Selects patterns from a [Map] lookup by string key, squeezing each into this pattern's event timespans. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun String.inhabit(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).inhabit(*args, callInfo = callInfo)

/** Selects patterns from a [List] lookup by clamped index, squeezing each into this string pattern's event timespans. */
@SprudelDsl
fun String.inhabit(lookup: List<Any>): SprudelPattern =
    this.toVoiceValuePattern().inhabit(lookup)

/** Selects patterns from a [Map] lookup by string key, squeezing each into this string pattern's event timespans. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun inhabit(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    applyInhabitTopLevel(args.toList().asSprudelDslArgs(callInfo), modulo = false)

/** Returns a [PatternMapperFn] that selects from a [List] lookup by clamped index and squeezes into event timespans. */
@SprudelDsl
fun inhabit(lookup: List<Any>): PatternMapperFn =
    applyInhabitTopLevel(listOf(lookup).asSprudelDslArgs(), modulo = false)

/** Returns a [PatternMapperFn] that selects from a [Map] lookup by string key and squeezes into event timespans. */
@SprudelDsl
fun inhabit(lookup: Map<String, Any>): PatternMapperFn =
    applyInhabitTopLevel(listOf(lookup).asSprudelDslArgs(), modulo = false)

/** Chains an inhabit onto this [PatternMapperFn]; clamped index, squeeze semantics. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.inhabit(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain(applyInhabitTopLevel(args.toList().asSprudelDslArgs(callInfo), modulo = false))

/** Chains an inhabit from a [List] lookup onto this [PatternMapperFn]; clamped index, squeeze semantics. */
@SprudelDsl
fun PatternMapperFn.inhabit(lookup: List<Any>): PatternMapperFn =
    this.chain(applyInhabitTopLevel(listOf(lookup).asSprudelDslArgs(), modulo = false))

/** Chains an inhabit from a [Map] lookup onto this [PatternMapperFn]; string keys, squeeze semantics. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.pickSqueeze(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.inhabit(*args, callInfo = callInfo)

/** Alias for [inhabit] — [List] lookup, clamped index, squeeze semantics. */
@SprudelDsl
fun SprudelPattern.pickSqueeze(lookup: List<Any>): SprudelPattern = this.inhabit(lookup)

/** Alias for [inhabit] — [Map] lookup, string key, squeeze semantics. */
@SprudelDsl
fun SprudelPattern.pickSqueeze(lookup: Map<String, Any>): SprudelPattern = this.inhabit(lookup)

/**
 * Alias for [inhabit] on a string pattern. Selects patterns from a lookup by index and squeezes into event duration.
 *
 * @alias inhabit
 * @category structural
 * @tags pickSqueeze, inhabit, squeeze, select, index, lookup
 */
@SprudelDsl
@KlangScript.Function
fun String.pickSqueeze(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pickSqueeze(*args, callInfo = callInfo)

/** Alias for [inhabit] — [List] lookup on a string pattern; clamped index, squeeze semantics. */
@SprudelDsl
fun String.pickSqueeze(lookup: List<Any>): SprudelPattern = this.toVoiceValuePattern().pickSqueeze(lookup)

/** Alias for [inhabit] — [Map] lookup on a string pattern; string key, squeeze semantics. */
@SprudelDsl
fun String.pickSqueeze(lookup: Map<String, Any>): SprudelPattern = this.toVoiceValuePattern().pickSqueeze(lookup)

/**
 * Returns a [PatternMapperFn] that is an alias for [inhabit]; selects by clamped index and squeezes into event duration.
 *
 * @alias inhabit
 * @category structural
 * @tags pickSqueeze, inhabit, squeeze, select, index, lookup
 */
@SprudelDsl
@KlangScript.Function
fun pickSqueeze(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    inhabit(*args, callInfo = callInfo)

/** Returns a [PatternMapperFn] — alias for [inhabit] — [List] lookup, clamped index, squeeze semantics. */
@SprudelDsl
fun pickSqueeze(lookup: List<Any>): PatternMapperFn = inhabit(lookup)

/** Returns a [PatternMapperFn] — alias for [inhabit] — [Map] lookup, string key, squeeze semantics. */
@SprudelDsl
fun pickSqueeze(lookup: Map<String, Any>): PatternMapperFn = inhabit(lookup)

/** Chains a pickSqueeze (alias for [inhabit]) onto this [PatternMapperFn]; clamped index, squeeze semantics. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pickSqueeze(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.inhabit(*args, callInfo = callInfo)

/** Chains a pickSqueeze from a [List] lookup onto this [PatternMapperFn]; clamped index, squeeze semantics. */
@SprudelDsl
fun PatternMapperFn.pickSqueeze(lookup: List<Any>): PatternMapperFn = this.inhabit(lookup)

/** Chains a pickSqueeze from a [Map] lookup onto this [PatternMapperFn]; string keys, squeeze semantics. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.inhabitmod(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyInhabitmodExtension(this, args.toList().asSprudelDslArgs(callInfo), callInfo?.receiverLocation)

/** Like [inhabit] with modulo — [List] lookup, modulo-wrapped index, squeezing into this pattern's event timespans. */
@SprudelDsl
fun SprudelPattern.inhabitmod(lookup: List<Any>): SprudelPattern =
    applyInhabitmodExtension(this, listOf(lookup).asSprudelDslArgs(), null)

/** Like [inhabit] with modulo — [Map] lookup, string key, squeezing into this pattern's event timespans. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun String.inhabitmod(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).inhabitmod(*args, callInfo = callInfo)

/** Like [inhabit] with modulo — [List] lookup on a string pattern; modulo-wrapped index, squeeze semantics. */
@SprudelDsl
fun String.inhabitmod(lookup: List<Any>): SprudelPattern =
    this.toVoiceValuePattern().inhabitmod(lookup)

/** Like [inhabit] with modulo — [Map] lookup on a string pattern; string key, squeeze semantics. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun inhabitmod(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    applyInhabitTopLevel(args.toList().asSprudelDslArgs(callInfo), modulo = true)

/** Returns a [PatternMapperFn] like [inhabit] with modulo — [List] lookup, modulo-wrapped index, squeeze semantics. */
@SprudelDsl
fun inhabitmod(lookup: List<Any>): PatternMapperFn =
    applyInhabitTopLevel(listOf(lookup).asSprudelDslArgs(), modulo = true)

/** Returns a [PatternMapperFn] like [inhabit] with modulo — [Map] lookup, string key, squeeze semantics. */
@SprudelDsl
fun inhabitmod(lookup: Map<String, Any>): PatternMapperFn =
    applyInhabitTopLevel(listOf(lookup).asSprudelDslArgs(), modulo = true)

/** Chains an inhabitmod onto this [PatternMapperFn]; modulo-wrapped index, squeeze semantics. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.inhabitmod(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain(applyInhabitTopLevel(args.toList().asSprudelDslArgs(callInfo), modulo = true))

/** Chains an inhabitmod from a [List] lookup onto this [PatternMapperFn]; modulo-wrapped index, squeeze semantics. */
@SprudelDsl
fun PatternMapperFn.inhabitmod(lookup: List<Any>): PatternMapperFn =
    this.chain(applyInhabitTopLevel(listOf(lookup).asSprudelDslArgs(), modulo = true))

/** Chains an inhabitmod from a [Map] lookup onto this [PatternMapperFn]; string keys, squeeze semantics. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.pickmodSqueeze(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.inhabitmod(*args, callInfo = callInfo)

/** Alias for [inhabitmod] — [List] lookup, modulo-wrapped index, squeeze semantics. */
@SprudelDsl
fun SprudelPattern.pickmodSqueeze(lookup: List<Any>): SprudelPattern = this.inhabitmod(lookup)

/** Alias for [inhabitmod] — [Map] lookup, string key, squeeze semantics. */
@SprudelDsl
fun SprudelPattern.pickmodSqueeze(lookup: Map<String, Any>): SprudelPattern = this.inhabitmod(lookup)

/**
 * Alias for [inhabitmod] on a string pattern. Selects patterns by modulo-wrapped index and squeezes into events.
 *
 * @alias inhabitmod
 * @category structural
 * @tags pickmodSqueeze, inhabitmod, modulo, squeeze, select, index, lookup
 */
@SprudelDsl
@KlangScript.Function
fun String.pickmodSqueeze(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pickmodSqueeze(*args, callInfo = callInfo)

/** Alias for [inhabitmod] — [List] lookup on a string pattern; modulo-wrapped index, squeeze semantics. */
@SprudelDsl
fun String.pickmodSqueeze(lookup: List<Any>): SprudelPattern = this.toVoiceValuePattern().pickmodSqueeze(lookup)

/** Alias for [inhabitmod] — [Map] lookup on a string pattern; string key, squeeze semantics. */
@SprudelDsl
fun String.pickmodSqueeze(lookup: Map<String, Any>): SprudelPattern = this.toVoiceValuePattern().pickmodSqueeze(lookup)

/**
 * Returns a [PatternMapperFn] that is an alias for [inhabitmod]; selects by modulo-wrapped index and squeezes.
 *
 * @alias inhabitmod
 * @category structural
 * @tags pickmodSqueeze, inhabitmod, modulo, squeeze, select, index, lookup
 */
@SprudelDsl
@KlangScript.Function
fun pickmodSqueeze(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    inhabitmod(*args, callInfo = callInfo)

/** Returns a [PatternMapperFn] — alias for [inhabitmod] — [List] lookup, modulo-wrapped index, squeeze semantics. */
@SprudelDsl
fun pickmodSqueeze(lookup: List<Any>): PatternMapperFn = inhabitmod(lookup)

/** Returns a [PatternMapperFn] — alias for [inhabitmod] — [Map] lookup, string key, squeeze semantics. */
@SprudelDsl
fun pickmodSqueeze(lookup: Map<String, Any>): PatternMapperFn = inhabitmod(lookup)

/** Chains a pickmodSqueeze (alias for [inhabitmod]) onto this [PatternMapperFn]; modulo-wrapped index, squeeze. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pickmodSqueeze(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.inhabitmod(*args, callInfo = callInfo)

/** Chains a pickmodSqueeze from a [List] lookup onto this [PatternMapperFn]; modulo-wrapped index, squeeze. */
@SprudelDsl
fun PatternMapperFn.pickmodSqueeze(lookup: List<Any>): PatternMapperFn = this.inhabitmod(lookup)

/** Chains a pickmodSqueeze from a [Map] lookup onto this [PatternMapperFn]; string keys, squeeze semantics. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.squeeze(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applySqueeze(this, args.toList().asSprudelDslArgs(callInfo), callInfo?.receiverLocation)

/** Squeezes patterns from a [List] lookup into the timespans of events in this pattern (clamped index). */
@SprudelDsl
fun SprudelPattern.squeeze(lookup: List<Any>): SprudelPattern =
    applySqueeze(this, listOf(lookup).asSprudelDslArgs(), null)

/** Squeezes patterns from a [Map] lookup into the timespans of events in this pattern (string key). */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun String.squeeze(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).squeeze(*args, callInfo = callInfo)

/** Squeezes patterns from a [List] lookup into the timespans of events in this string pattern (clamped). */
@SprudelDsl
fun String.squeeze(lookup: List<Any>): SprudelPattern =
    this.toVoiceValuePattern().squeeze(lookup)

/** Squeezes patterns from a [Map] lookup into the timespans of events in this string pattern (string key). */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun squeeze(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.squeeze(*args, callInfo = callInfo) }

/** Returns a [PatternMapperFn] that selects from a [List] lookup by index and squeezes; clamped. */
@SprudelDsl
fun squeeze(lookup: List<Any>): PatternMapperFn = { p -> p.squeeze(lookup) }

/** Returns a [PatternMapperFn] that selects from a [Map] lookup by string key and squeezes. */
@SprudelDsl
fun squeeze(lookup: Map<String, Any>): PatternMapperFn = { p -> p.squeeze(lookup) }

/** Chains a squeeze onto this [PatternMapperFn]; clamped index, squeeze semantics. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.squeeze(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.squeeze(*args, callInfo = callInfo) }

/** Chains a squeeze from a [List] lookup onto this [PatternMapperFn]; clamped index, squeeze semantics. */
@SprudelDsl
fun PatternMapperFn.squeeze(lookup: List<Any>): PatternMapperFn =
    this.chain { p -> p.squeeze(lookup) }

/** Chains a squeeze from a [Map] lookup onto this [PatternMapperFn]; string keys, squeeze semantics. */
@SprudelDsl
fun PatternMapperFn.squeeze(lookup: Map<String, Any>): PatternMapperFn =
    this.chain { p -> p.squeeze(lookup) }

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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
fun pickRestart(lookup: Map<String, Any>): PatternMapperFn = { p -> p.pickRestart(lookup) }

/**
 * Chains a pickRestart onto this [PatternMapperFn]; clamped indices, restarts on trigger.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pickRestart(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pickRestart(*args, callInfo = callInfo) }

/**
 * Chains a pickRestart from a [List] lookup onto this [PatternMapperFn].
 */
@SprudelDsl
fun PatternMapperFn.pickRestart(lookup: List<PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickRestart(lookup) }

/**
 * Chains a pickRestart from a [Map] lookup onto this [PatternMapperFn].
 */
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
fun pickmodRestart(lookup: Map<String, PatternLike>): PatternMapperFn = { p -> p.pickmodRestart(lookup) }

/**
 * Chains a pickmodRestart onto this [PatternMapperFn]; modulo indices, restarts on trigger.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pickmodRestart(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pickmodRestart(*args, callInfo = callInfo) }

/**
 * Chains a pickmodRestart from a [List] lookup onto this [PatternMapperFn].
 */
@SprudelDsl
fun PatternMapperFn.pickmodRestart(lookup: List<PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickmodRestart(lookup) }

/**
 * Chains a pickmodRestart from a [Map] lookup onto this [PatternMapperFn].
 */
@SprudelDsl
fun PatternMapperFn.pickmodRestart(lookup: Map<String, PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickmodRestart(lookup) }

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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
fun pickReset(lookup: Map<String, PatternLike>): PatternMapperFn = { p -> p.pickReset(lookup) }

/**
 * Chains a pickReset onto this [PatternMapperFn]; clamped indices, resets phase on trigger.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pickReset(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pickReset(*args, callInfo = callInfo) }

/**
 * Chains a pickReset from a [List] lookup onto this [PatternMapperFn].
 */
@SprudelDsl
fun PatternMapperFn.pickReset(lookup: List<PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickReset(lookup) }

/**
 * Chains a pickReset from a [Map] lookup onto this [PatternMapperFn].
 */
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
fun pickmodReset(lookup: Map<String, PatternLike>): PatternMapperFn = { p -> p.pickmodReset(lookup) }

/**
 * Chains a pickmodReset onto this [PatternMapperFn]; modulo indices, resets phase on trigger.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pickmodReset(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pickmodReset(*args, callInfo = callInfo) }

/**
 * Chains a pickmodReset from a [List] lookup onto this [PatternMapperFn].
 */
@SprudelDsl
fun PatternMapperFn.pickmodReset(lookup: List<PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickmodReset(lookup) }

/**
 * Chains a pickmodReset from a [Map] lookup onto this [PatternMapperFn].
 */
@SprudelDsl
fun PatternMapperFn.pickmodReset(lookup: Map<String, PatternLike>): PatternMapperFn =
    this.chain { p -> p.pickmodReset(lookup) }

// -- pickF() ----------------------------------------------------------------------------------------------------------

/**
 * Apply functions from a list based on a pattern of indices.
 * Indices are clamped to the list size.
 *
 * JavaScript: `pat.apply(pick(lookup, funcs))`
 *
 * Example: `s("bd [rim hh]").pickF("<0 1 2>", [rev, jux(rev), fast(2)])`
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
 * s("bd rim hh").pickF("<0 1 2>", [rev, fast(2), jux(rev)])  // fn selected by index
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").pickF(n("0 1"), [slow(2), fast(3)])       // 0→slow(2), 1→fast(3)
 * ```
 *
 * @category structural
 * @tags pickF, pick, apply, transform, function, index
 */
@SprudelDsl
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
 * "bd rim hh".pickF("<0 1 2>", [rev, fast(2), jux(rev)]).s()  // string source, fn by index
 * ```
 *
 * @category structural
 * @tags pickF, pick, apply, transform, function, index
 */
@SprudelDsl
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
 * s("bd rim hh").apply(pickF("<0 1 2>", [rev, fast(2), jux(rev)]))  // via mapper
 * ```
 *
 * @category structural
 * @tags pickF, pick, apply, transform, function, index
 */
@SprudelDsl
@KlangScript.Function
fun pickF(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pickF(*args, callInfo = callInfo) }

/**
 * Chains a pickF onto this [PatternMapperFn]; applies a function selected by clamped index.
 *
 * @param args Index pattern followed by the function list.
 * @return A new [PatternMapperFn] composing this mapper with the function-select operation.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pickF(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pickF(*args, callInfo = callInfo) }

// -- pickmodF() -------------------------------------------------------------------------------------------------------

/**
 * Apply functions from a list based on a pattern of indices.
 * Indices wrap around (modulo) if greater than list size.
 *
 * JavaScript: `pat.apply(pickmod(lookup, funcs))`
 */
private fun applyPickmodF(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val lookupArg = args.getOrNull(0) ?: return pattern
    val funcsArg = args.getOrNull(1) ?: return pattern

    // Get the list of functions
    val funcsList = funcsArg.value as? List<*> ?: return pattern
    val mappers = funcsList.mapNotNull { patternMapper(it) }
    if (mappers.isEmpty()) return pattern

    // JavaScript: pat.apply(pickmod(lookup, funcs))
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun pickmodF(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pickmodF(*args, callInfo = callInfo) }

/**
 * Chains a pickmodF onto this [PatternMapperFn]; applies a function selected by modulo-wrapped index.
 *
 * @param args Index pattern followed by the function list.
 * @return A new [PatternMapperFn] composing this mapper with the modulo function-select operation.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pickmodF(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pickmodF(*args, callInfo = callInfo) }
