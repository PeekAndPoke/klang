@file:Suppress("DuplicatedCode", "unused", "ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.script.ast.SourceLocationChain
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel._bind
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.strudel.pattern.*
import kotlin.math.floor

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangPatternPickingInit = false

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
private fun reifyLookup(lookup: Any, baseLocation: SourceLocation? = null): Map<Any, StrudelPattern>? {
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
            is StrudelPattern -> value
            is String -> {
                // Parse strings as mini-notation patterns with source location
                val atomFactory = { text: String, sourceLocations: SourceLocationChain? ->
                    AtomicPattern(
                        data = StrudelVoiceData.empty.voiceValueModifier(text),
                        sourceLocations = sourceLocations,
                    )
                }
                parseMiniNotation(input = value, baseLocation = baseLocation, atomFactory = atomFactory)
            }

            else -> {
                // Wrap other values (numbers, etc.) in atomic pattern with default modifier
                AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(value))
            }
        }
    }
}

/**
 * Safely extracts an integer index from an event data.
 */
private fun extractIndex(data: StrudelVoiceData, modulo: Boolean, len: Int): Int? {
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
private fun extractKey(data: StrudelVoiceData): String {
    val value = data.value ?: data.note ?: data.soundIndex

    return when (value) {
        null -> ""
        is StrudelVoiceValue -> value.asString
        is String -> value
        is Int, is Long, is Double, is Float -> value.toString()
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
    pat: StrudelPattern,
    modulo: Boolean,
    baseLocation: SourceLocation? = null,
): StrudelPattern {
    // Validate and reify lookup
    val reifiedLookup = reifyLookup(lookup, baseLocation) ?: return silence
    val isList = lookup is List<*>

    // Create key extractor function based on list vs map
    val keyExtractor: (StrudelVoiceData, Boolean, Int) -> Any? = if (isList) {
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
    pat: StrudelPattern,
    modulo: Boolean,
    baseLocation: SourceLocation? = null,
): StrudelPattern {
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

internal val _pick by dslPatternMapper { args, callInfo -> { p -> p._pick(args, callInfo) } }

internal val StrudelPattern._pick by dslPatternExtension { p, args, callInfo ->
    if (args.isEmpty()) return@dslPatternExtension p

    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    val lookupLocation = args.firstOrNull()?.location

    dispatchPick(lookup = lookup, pat = p, modulo = false, baseLocation = callInfo?.receiverLocation)
}

internal val String._pick by dslStringExtension { p, args, callInfo -> p._pick(args, callInfo) }

internal val PatternMapperFn._pick by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_pick(args, callInfo))
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
 * ```KlangScript
 * seq("<0 1 2!2 3>").pick("g a", "e f", "f g f g" , "g c d").note()
 * ```
 *
 * ```KlangScript
 * seq("<0 1 [2,0]>").pick("bd sd", "cp cp", "hh hh").s()
 * ```
 *
 * @category structural
 * @tags pick, select, index, lookup
 */
@StrudelDsl
fun StrudelPattern.pick(vararg args: PatternLike): StrudelPattern = this._pick(args.toList().asStrudelDslArgs())

/**
 * Selects patterns from a [List] lookup using this pattern's event values as zero-based indices.
 *
 * Convenience overload accepting an explicit [List]. Indices are clamped to valid bounds.
 *
 * @param lookup List of items to pick from; index 0 is first, last index is max.
 * @return A pattern playing the item at each event's integer index (clamped).
 *
 * ```KlangScript
 * seq("<0 1 2!2 3>").pick(["g a", "e f", "f g f g" , "g c d"]).note()
 * ```
 */
@StrudelDsl
fun StrudelPattern.pick(lookup: List<PatternLike>): StrudelPattern = this._pick(listOf(lookup).asStrudelDslArgs())

/**
 * Selects patterns from a [Map] lookup using this pattern's event values as string keys.
 *
 * Convenience overload accepting an explicit [Map]. Events whose values have no matching key
 * produce no output.
 *
 * @param lookup Map of string keys to items; each event value is used as a lookup key.
 * @return A pattern playing the item for each matched key; unmatched keys produce silence.
 *
 * ```KlangScript
 * seq("<a!2 [a,b] b>").pick({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
@StrudelDsl
fun StrudelPattern.pick(lookup: Map<String, PatternLike>): StrudelPattern = this._pick(listOf(lookup).asStrudelDslArgs())

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
 * ```KlangScript
 * "<0 1 2 1>".pick("bd", "sd", "hh").s().fast(2)
 * ```
 *
 * @category structural
 * @tags pick, select, index, lookup
 */
@StrudelDsl
fun String.pick(vararg args: PatternLike): StrudelPattern = this._pick(args.toList().asStrudelDslArgs())

/**
 * Selects patterns from a [List] using this string parsed as a mini-notation index pattern.
 *
 * @param lookup List of items to pick from; integer indices, clamped.
 * @return A pattern playing the item at each event's integer index (clamped).
 *
 * ```KlangScript
 * "<0 1 2 1>".pick(["bd", "sd", "hh"]).s().fast(2)
 * ```
 */
@StrudelDsl
fun String.pick(lookup: List<PatternLike>): StrudelPattern = this._pick(listOf(lookup).asStrudelDslArgs())

/**
 * Selects patterns from a [Map] using this string parsed as a mini-notation key pattern.
 *
 * @param lookup Map of string keys to items; each parsed event value is a lookup key.
 * @return A pattern playing the item for each matched key; unmatched keys produce silence.
 *
 * ```KlangScript
 * "<a!2 [a,b] b>".pick({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
@StrudelDsl
fun String.pick(lookup: Map<String, PatternLike>): StrudelPattern = this._pick(listOf(lookup).asStrudelDslArgs())

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
 * ```KlangScript
 * "<0 1 2 1>".apply(pick("bd", "sd", "hh")).s().fast(2)
 * ```
 *
 * @category structural
 * @tags pick, select, index, lookup
 */
@StrudelDsl
fun pick(vararg args: PatternLike): PatternMapperFn = _pick(args.toList().asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a [List] lookup with clamped integer indices.
 *
 * @param lookup List of items; source pattern values are used as zero-based indices (clamped).
 * @return A [PatternMapperFn] that maps a source pattern to a pick result.
 *
 * ```KlangScript
 * "<0 1 2 1>".apply(pick(["bd", "sd", "hh"])).s().fast(2)
 * ```
 */
@StrudelDsl
fun pick(lookup: List<PatternLike>): PatternMapperFn = _pick(listOf(lookup).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a [Map] lookup using string keys.
 *
 * @param lookup Map of string keys to items; source pattern values are used as keys.
 * @return A [PatternMapperFn] that maps a source pattern to a pick result.
 *
 * ```KlangScript
 * "<a!2 [a,b] b>".apply(pick({a: "bd(3,8)", b: "sd sd"})).s()
 * ```
 */
@StrudelDsl
fun pick(lookup: Map<String, PatternLike>): PatternMapperFn = _pick(listOf(lookup).asStrudelDslArgs())

/**
 * Chains a pick onto this [PatternMapperFn]; the chained result picks from the given lookup.
 */
@StrudelDsl
fun PatternMapperFn.pick(vararg args: PatternLike): PatternMapperFn = this._pick(args.toList().asStrudelDslArgs())

/**
 * Chains a pick from a [List] lookup onto this [PatternMapperFn]; indices are clamped.
 */
@StrudelDsl
fun PatternMapperFn.pick(lookup: List<PatternLike>): PatternMapperFn = this._pick(listOf(lookup).asStrudelDslArgs())

/**
 * Chains a pick from a [Map] lookup onto this [PatternMapperFn]; string keys are used.
 */
@StrudelDsl
fun PatternMapperFn.pick(lookup: Map<String, PatternLike>): PatternMapperFn = this._pick(listOf(lookup).asStrudelDslArgs())

// -- pickmod() ----------------------------------------------------------------------------------------------------------------------------

internal val _pickmod by dslPatternMapper { args, callInfo -> { p -> p._pickmod(args, callInfo) } }

internal val StrudelPattern._pickmod by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p

    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    val lookupLocation = args.firstOrNull()?.location

    dispatchPick(lookup, p, modulo = true, lookupLocation)
}

internal val String._pickmod by dslStringExtension { p, args, callInfo -> p._pickmod(args, callInfo) }

internal val PatternMapperFn._pickmod by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_pickmod(args, callInfo))
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
 * ```KlangScript
 * seq("<0 1 2!2 3>").pickmod("g a", "e f", "f g f g" , "g c d").note()
 * ```
 *
 * ```KlangScript
 * seq("<0 1 [2,0]>").pickmod("bd sd", "cp cp", "hh hh").s()
 * ```
 *
 * @category structural
 * @tags pickmod, pick, modulo, wrap, index, lookup
 */
@StrudelDsl
fun StrudelPattern.pickmod(vararg args: PatternLike): StrudelPattern = this._pickmod(args.toList().asStrudelDslArgs())

/**
 * Like [pick] but wraps indices with modulo — [List] lookup using this pattern's event values.
 *
 * @param lookup List of items; indices wrap cyclically with modulo.
 * @return A pattern playing the item at each event's wrapped integer index.
 *
 * ```KlangScript
 * seq("<0 1 2!2 3>").pickmod(["g a", "e f", "f g f g" , "g c d"]).note()
 * ```
 */
@StrudelDsl
fun StrudelPattern.pickmod(lookup: List<PatternLike>): StrudelPattern = this._pickmod(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pick] but wraps indices with modulo — [Map] lookup using this pattern's event values.
 *
 * @param lookup Map of string keys; source pattern event values are used as keys.
 * @return A pattern playing the item for each matched key; unmatched keys produce silence.
 *
 * ```KlangScript
 * seq("<a!2 [a,b] b>").pickmod({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
@StrudelDsl
fun StrudelPattern.pickmod(lookup: Map<String, PatternLike>): StrudelPattern = this._pickmod(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pick] but wraps indices with modulo — this string is parsed as a mini-notation index pattern.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A pattern playing the selected items driven by this string's parsed values (modulo-wrapped).
 *
 * ```KlangScript
 * "<0 1 2 1>".pickmod("bd", "sd", "hh").s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickmod, pick, modulo, wrap, index, lookup
 */
@StrudelDsl
fun String.pickmod(vararg args: PatternLike): StrudelPattern = this._pickmod(args.toList().asStrudelDslArgs())

/**
 * Like [pick] but wraps indices with modulo — [List] lookup, this string as index pattern.
 *
 * @param lookup List of items; this string is parsed as mini-notation to produce integer indices.
 * @return A pattern playing the item at each event's wrapped integer index.
 *
 * ```KlangScript
 * "<0 1 2 1>".pickmod(["bd", "sd", "hh"]).s().fast(2)
 * ```
 */
@StrudelDsl
fun String.pickmod(lookup: List<PatternLike>): StrudelPattern = this._pickmod(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pick] but wraps indices with modulo — [Map] lookup, this string as key pattern.
 *
 * @param lookup Map of string keys; this string is parsed as mini-notation to produce keys.
 * @return A pattern playing the item for each matched key; unmatched keys produce silence.
 *
 * ```KlangScript
 * "<a!2 [a,b] b>".pickmod({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
@StrudelDsl
fun String.pickmod(lookup: Map<String, PatternLike>): StrudelPattern = this._pickmod(listOf(lookup).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a lookup with modulo-wrapped index values.
 *
 * Like [pick] but indices wrap cyclically. Apply the returned mapper to a pattern using `.apply()`.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A [PatternMapperFn] that maps a source pattern to a modulo-pick result.
 *
 * ```KlangScript
 * "<0 1 2 1>".apply(pickmod("bd", "sd", "hh")).s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickmod, pick, modulo, wrap, index, lookup
 */
@StrudelDsl
fun pickmod(vararg args: PatternLike): PatternMapperFn = _pickmod(args.toList().asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a [List] lookup with modulo-wrapped indices.
 *
 * @param lookup List of items; source pattern values are used as integer indices (modulo-wrapped).
 * @return A [PatternMapperFn] that maps a source pattern to a modulo-pick result.
 *
 * ```KlangScript
 * "<0 1 2 1>".apply(pickmod(["bd", "sd", "hh"])).s().fast(2)
 * ```
 */
@StrudelDsl
fun pickmod(lookup: List<PatternLike>): PatternMapperFn = _pickmod(listOf(lookup).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a [Map] lookup using string keys.
 *
 * @param lookup Map of string keys to items; source pattern values are used as keys.
 * @return A [PatternMapperFn] that maps a source pattern to a map-pick result.
 *
 * ```KlangScript
 * "<a!2 [a,b] b>".apply(pickmod({a: "bd(3,8)", b: "sd sd"})).s()
 * ```
 */
@StrudelDsl
fun pickmod(lookup: Map<String, PatternLike>): PatternMapperFn = _pickmod(listOf(lookup).asStrudelDslArgs())

/**
 * Chains a modulo-pick onto this [PatternMapperFn]; indices wrap cyclically.
 */
@StrudelDsl
fun PatternMapperFn.pickmod(vararg args: PatternLike): PatternMapperFn = this._pickmod(args.toList().asStrudelDslArgs())

/**
 * Chains a modulo-pick from a [List] lookup onto this [PatternMapperFn].
 */
@StrudelDsl
fun PatternMapperFn.pickmod(lookup: List<PatternLike>): PatternMapperFn = this._pickmod(listOf(lookup).asStrudelDslArgs())

/**
 * Chains a modulo-pick from a [Map] lookup onto this [PatternMapperFn].
 */
@StrudelDsl
fun PatternMapperFn.pickmod(lookup: Map<String, PatternLike>): PatternMapperFn = this._pickmod(listOf(lookup).asStrudelDslArgs())

// -- pickout() ----------------------------------------------------------------------------------------------------------------------------

private fun applyPickOuter(
    lookup: Any,
    pat: StrudelPattern,
    modulo: Boolean,
    baseLocation: SourceLocation? = null,
): StrudelPattern {
    // Validate and reify lookup
    val reifiedLookup = reifyLookup(lookup, baseLocation) ?: return silence
    val isList = lookup is List<*>

    // Create key extractor
    val keyExtractor: (StrudelVoiceData, Boolean, Int) -> Any? = if (isList) {
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
    pat: StrudelPattern,
    modulo: Boolean,
    baseLocation: SourceLocation? = null,
): StrudelPattern {
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

internal val _pickOut by dslPatternMapper { args, callInfo -> { p -> p._pickOut(args, callInfo) } }

internal val StrudelPattern._pickOut by dslPatternExtension { p, args, callInfo ->
    if (args.isEmpty()) return@dslPatternExtension p

    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }

    dispatchPickOuter(lookup, p, modulo = false, callInfo?.receiverLocation)
}

internal val String._pickOut by dslStringExtension { p, args, callInfo -> p._pickOut(args, callInfo) }

internal val PatternMapperFn._pickOut by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_pickOut(args, callInfo))
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
 * ```KlangScript
 * seq("<0 1 2!2 3>").pickOut("g a", "e f", "f g f g" , "g c d").note()
 * ```
 *
 * ```KlangScript
 * seq("<0 1 [2,0]>").pickOut("bd sd", "cp cp", "hh hh").s()
 * ```
 *
 * @category structural
 * @tags pickOut, pick, select, index, outer, outerJoin
 */
@StrudelDsl
fun StrudelPattern.pickOut(vararg args: PatternLike): StrudelPattern = this._pickOut(args.toList().asStrudelDslArgs())

/**
 * Like [pick] but uses outer-join semantics — [List] lookup using this pattern's event values.
 *
 * @param lookup List of items; indices are clamped to valid bounds.
 * @return A pattern playing the selected items with onset timing from this pattern.
 *
 * ```KlangScript
 * seq("<0 1 2!2 3>").pickOut(["g a", "e f", "f g f g" , "g c d"]).note()
 * ```
 */
@StrudelDsl
fun StrudelPattern.pickOut(lookup: List<PatternLike>): StrudelPattern = this._pickOut(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pick] but uses outer-join semantics — [Map] lookup using this pattern's event values as keys.
 *
 * @param lookup Map of string keys to items; unmatched keys produce no output.
 * @return A pattern playing the selected items with onset timing from this pattern.
 *
 * ```KlangScript
 * seq("<a!2 [a,b] b>").pickOut({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
@StrudelDsl
fun StrudelPattern.pickOut(lookup: Map<String, Any>): StrudelPattern =
    this._pickOut(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pick] but uses outer-join semantics — this string is parsed as a mini-notation index pattern.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A pattern with onset timing from this string's parsed values; selected items are outer-joined.
 *
 * ```KlangScript
 * "<0 1 2 1>".pickOut("bd", "sd", "hh").s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickOut, pick, select, index, outer, outerJoin
 */
@StrudelDsl
fun String.pickOut(vararg args: PatternLike): StrudelPattern = this._pickOut(args.toList().asStrudelDslArgs())

/**
 * Like [pick] but uses outer-join semantics — [List] lookup, this string as index pattern.
 *
 * @param lookup List of items; integer indices, clamped.
 * @return A pattern with onset timing from this string; selected items are outer-joined.
 *
 * ```KlangScript
 * "<0 1 2 1>".pickOut(["bd", "sd", "hh"]).s().fast(2)
 * ```
 */
@StrudelDsl
fun String.pickOut(lookup: List<PatternLike>): StrudelPattern = this._pickOut(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pick] but uses outer-join semantics — [Map] lookup, this string as key pattern.
 *
 * @param lookup Map of string keys; unmatched keys produce no output.
 * @return A pattern with onset timing from this string; selected items are outer-joined.
 *
 * ```KlangScript
 * "<a!2 [a,b] b>".pickOut({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
@StrudelDsl
fun String.pickOut(lookup: Map<String, Any>): StrudelPattern = this._pickOut(listOf(lookup).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a lookup with outer-join semantics, clamped indices.
 *
 * Like [pick] but forces all selected events to fire at the selector's onset. Apply using `.apply()`.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A [PatternMapperFn] that maps a source pattern to an outer-join pick result.
 *
 * ```KlangScript
 * "<0 1 2 1>".apply(pickOut("bd", "sd", "hh")).s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickOut, pick, select, index, outer, outerJoin
 */
@StrudelDsl
fun pickOut(vararg args: PatternLike): PatternMapperFn = _pickOut(args.toList().asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a [List] lookup with outer-join semantics.
 *
 * @param lookup List of items; source pattern values are used as zero-based indices (clamped).
 * @return A [PatternMapperFn] that maps a source pattern to an outer-join pick result.
 *
 * ```KlangScript
 * "<0 1 2 1>".apply(pickOut(["bd", "sd", "hh"])).s().fast(2)
 * ```
 */
@StrudelDsl
fun pickOut(lookup: List<PatternLike>): PatternMapperFn = _pickOut(listOf(lookup).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a [Map] lookup with outer-join semantics.
 *
 * @param lookup Map of string keys; source pattern values are used as keys.
 * @return A [PatternMapperFn] that maps a source pattern to an outer-join pick result.
 *
 * ```KlangScript
 * "<a!2 [a,b] b>".apply(pickOut({a: "bd(3,8)", b: "sd sd"})).s()
 * ```
 */
@StrudelDsl
fun pickOut(lookup: Map<String, Any>): PatternMapperFn = _pickOut(listOf(lookup).asStrudelDslArgs())

/**
 * Chains a pickOut onto this [PatternMapperFn]; outer-join semantics, clamped indices.
 */
@StrudelDsl
fun PatternMapperFn.pickOut(vararg args: PatternLike): PatternMapperFn = this._pickOut(args.toList().asStrudelDslArgs())

/**
 * Chains a pickOut from a [List] lookup onto this [PatternMapperFn]; outer-join, clamped indices.
 */
@StrudelDsl
fun PatternMapperFn.pickOut(lookup: List<PatternLike>): PatternMapperFn = this._pickOut(listOf(lookup).asStrudelDslArgs())

/**
 * Chains a pickOut from a [Map] lookup onto this [PatternMapperFn]; outer-join, string keys.
 */
@StrudelDsl
fun PatternMapperFn.pickOut(lookup: Map<String, Any>): PatternMapperFn = this._pickOut(listOf(lookup).asStrudelDslArgs())

// -- pickmodOut() -----------------------------------------------------------------------------------------------------

internal val _pickmodOut by dslPatternMapper { args, callInfo -> { p -> p._pickmodOut(args, callInfo) } }

internal val StrudelPattern._pickmodOut by dslPatternExtension { p, args, callInfo ->
    if (args.isEmpty()) return@dslPatternExtension p

    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }

    dispatchPickOuter(lookup, p, modulo = true, callInfo?.receiverLocation)
}

internal val String._pickmodOut by dslStringExtension { p, args, callInfo -> p._pickmodOut(args, callInfo) }

internal val PatternMapperFn._pickmodOut by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_pickmodOut(args, callInfo))
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
 * ```KlangScript
 * seq("<0 1 2!2 3>").pickmodOut("g a", "e f", "f g f g" , "g c d").note()
 * ```
 *
 * ```KlangScript
 * seq("<0 1 [2,0]>").pickmodOut("bd sd", "cp cp", "hh hh").s()
 * ```
 *
 * @category structural
 * @tags pickmodOut, pickOut, pickmod, modulo, select, index, outer
 */
@StrudelDsl
fun StrudelPattern.pickmodOut(vararg args: PatternLike): StrudelPattern =
    this._pickmodOut(args.toList().asStrudelDslArgs())

/**
 * Like [pickOut] but wraps indices with modulo — [List] lookup using this pattern's event values.
 *
 * @param lookup List of items; indices wrap cyclically with modulo.
 * @return A pattern with onset timing from this pattern; selected items are outer-joined.
 *
 * ```KlangScript
 * seq("<0 1 2!2 3>").pickmodOut(["g a", "e f", "f g f g" , "g c d"]).note()
 * ```
 */
@StrudelDsl
fun StrudelPattern.pickmodOut(lookup: List<PatternLike>): StrudelPattern =
    this._pickmodOut(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pickOut] but wraps indices with modulo — [Map] lookup using this pattern's event values as keys.
 *
 * @param lookup Map of string keys; unmatched keys produce no output.
 * @return A pattern with onset timing from this pattern; selected items are outer-joined.
 *
 * ```KlangScript
 * seq("<a!2 [a,b] b>").pickmodOut({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
@StrudelDsl
fun StrudelPattern.pickmodOut(lookup: Map<String, Any>): StrudelPattern =
    this._pickmodOut(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pickOut] but wraps indices with modulo — this string is parsed as a mini-notation index pattern.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A pattern with onset timing from this string's parsed values; indices wrap cyclically.
 *
 * ```KlangScript
 * "<0 1 2 1>".pickmodOut("bd", "sd", "hh").s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickmodOut, pickOut, pickmod, modulo, select, index, outer
 */
@StrudelDsl
fun String.pickmodOut(vararg args: PatternLike): StrudelPattern = this._pickmodOut(args.toList().asStrudelDslArgs())

/**
 * Like [pickOut] but wraps indices with modulo — [List] lookup, this string as index pattern.
 *
 * @param lookup List of items; indices wrap cyclically with modulo.
 * @return A pattern with onset timing from this string; selected items are outer-joined.
 *
 * ```KlangScript
 * "<0 1 2 1>".pickmodOut(["bd", "sd", "hh"]).s().fast(2)
 * ```
 */
@StrudelDsl
fun String.pickmodOut(lookup: List<PatternLike>): StrudelPattern = this._pickmodOut(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pickOut] but wraps indices with modulo — [Map] lookup, this string as key pattern.
 *
 * @param lookup Map of string keys; unmatched keys produce no output.
 * @return A pattern with onset timing from this string; selected items are outer-joined.
 *
 * ```KlangScript
 * "<a!2 [a,b] b>".pickmodOut({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
@StrudelDsl
fun String.pickmodOut(lookup: Map<String, Any>): StrudelPattern = this._pickmodOut(listOf(lookup).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a lookup with outer-join semantics and modulo indices.
 *
 * Like [pickOut] but wraps indices cyclically. Apply the returned mapper using `.apply()`.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A [PatternMapperFn] that maps a source pattern to an outer-join modulo-pick result.
 *
 * ```KlangScript
 * "<0 1 2 1>".apply(pickmodOut("bd", "sd", "hh")).s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickmodOut, pickOut, pickmod, modulo, select, index, outer
 */
@StrudelDsl
fun pickmodOut(vararg args: PatternLike): PatternMapperFn = _pickmodOut(args.toList().asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a [List] lookup with outer-join and modulo indices.
 *
 * @param lookup List of items; source pattern values are used as integer indices (modulo-wrapped).
 * @return A [PatternMapperFn] that maps a source pattern to an outer-join modulo-pick result.
 *
 * ```KlangScript
 * "<0 1 2 1>".apply(pickmodOut(["bd", "sd", "hh"])).s().fast(2)
 * ```
 */
@StrudelDsl
fun pickmodOut(lookup: List<PatternLike>): PatternMapperFn = _pickmodOut(listOf(lookup).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a [Map] lookup with outer-join semantics.
 *
 * @param lookup Map of string keys; source pattern values are used as keys.
 * @return A [PatternMapperFn] that maps a source pattern to an outer-join map-pick result.
 *
 * ```KlangScript
 * "<a!2 [a,b] b>".apply(pickmodOut({a: "bd(3,8)", b: "sd sd"})).s()
 * ```
 */
@StrudelDsl
fun pickmodOut(lookup: Map<String, Any>): PatternMapperFn = _pickmodOut(listOf(lookup).asStrudelDslArgs())

/**
 * Chains a pickmodOut onto this [PatternMapperFn]; outer-join semantics, modulo indices.
 */
@StrudelDsl
fun PatternMapperFn.pickmodOut(vararg args: PatternLike): PatternMapperFn =
    this._pickmodOut(args.toList().asStrudelDslArgs())

/**
 * Chains a pickmodOut from a [List] lookup onto this [PatternMapperFn]; outer-join, modulo indices.
 */
@StrudelDsl
fun PatternMapperFn.pickmodOut(lookup: List<PatternLike>): PatternMapperFn = this._pickmodOut(listOf(lookup).asStrudelDslArgs())

/**
 * Chains a pickmodOut from a [Map] lookup onto this [PatternMapperFn]; outer-join, string keys.
 */
@StrudelDsl
fun PatternMapperFn.pickmodOut(lookup: Map<String, Any>): PatternMapperFn = this._pickmodOut(listOf(lookup).asStrudelDslArgs())

// -- inhabit() ----------------------------------------------------------------------------------------------------------------------------

private fun applyInhabit(
    lookup: Any,
    pat: StrudelPattern,
    modulo: Boolean,
    baseLocation: SourceLocation? = null,
): StrudelPattern {
    // Validate and reify lookup
    val reifiedLookup = reifyLookup(lookup, baseLocation) ?: return silence
    val isList = lookup is List<*>

    // Create key extractor function based on list vs map
    val keyExtractor: (StrudelVoiceData, Boolean, Int) -> Any? = if (isList) {
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
    pat: StrudelPattern,
    modulo: Boolean,
    baseLocation: SourceLocation? = null,
): StrudelPattern {
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

internal val _inhabit by dslPatternFunction { args, _ ->
    if (args.size < 2) return@dslPatternFunction silence

    val first = args[0].value
    val lookup: Any
    val patArg: StrudelDslArg<Any?>
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
    dispatchInhabit(lookup, pat, modulo = false, lookupLocation)
}

internal val StrudelPattern._inhabit by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p

    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }

    dispatchInhabit(lookup, p, modulo = false, args.firstOrNull()?.location)
}

internal val String._inhabit by dslStringExtension { p, args, callInfo -> p._inhabit(args, callInfo) }

/**
 * Selects patterns from a list or map by index and squeezes each into the trigger's timespan.
 *
 * The last argument (or the second when the first is a list/map) is the index pattern.
 * Selected patterns are squeezed so their full cycle fits within the selecting event's duration.
 * Indices are clamped to valid bounds.
 *
 * ```KlangScript
 * inhabit("bd sd hh", "rim cp", n("0 1 2 0"))   // picked pattern fills event duration
 * ```
 *
 * ```KlangScript
 * inhabit(["c3 e3", "g3 b3"], n("0 1 0"))        // each chosen pattern is squeezed in
 * ```
 *
 * @alias pickSqueeze
 * @category structural
 * @tags inhabit, pickSqueeze, squeeze, select, index, lookup
 */
@StrudelDsl
fun inhabit(vararg args: PatternLike): StrudelPattern = _inhabit(args.toList().asStrudelDslArgs())

/**
 * Selects patterns from a list by index and squeezes each into the timespan of the trigger.
 *
 * Each event of this pattern picks a pattern from the lookup by index (clamped) and squeezes
 * it so that the full cycle of the chosen pattern fits within the duration of the event.
 * This is equivalent to [squeeze] with arguments reversed.
 *
 * ```KlangScript
 * "0 1 2".inhabit("bd sd hh", "rim cp", "hh*4").s()         // chosen pattern fills event
 * ```
 *
 * ```KlangScript
 * n("0 1").inhabit(note("c3 e3 g3"), note("b3 d4"))          // pattern squeezed per index
 * ```
 *
 * @alias pickSqueeze
 * @category structural
 * @tags inhabit, pickSqueeze, squeeze, select, index, lookup
 */
@StrudelDsl
fun StrudelPattern.inhabit(vararg args: PatternLike): StrudelPattern = this._inhabit(args.toList().asStrudelDslArgs())

/** Selects patterns from a list (clamped index) and squeezes them into event timespans. */
@StrudelDsl
fun String.inhabit(vararg args: PatternLike): StrudelPattern = this._inhabit(args.toList().asStrudelDslArgs())

// -- pickSqueeze() ------------------------------------------------------------------------------------------------------------------------

internal val _pickSqueeze by dslPatternFunction { args, callInfo -> _inhabit(args, callInfo) }
internal val StrudelPattern._pickSqueeze by dslPatternExtension { p, args, callInfo -> p._inhabit(args, callInfo) }
internal val String._pickSqueeze by dslStringExtension { p, args, callInfo -> p._pickSqueeze(args, callInfo) }

/** Alias for [inhabit]. Selects patterns from a list by index and squeezes into event duration. */
@StrudelDsl
fun pickSqueeze(vararg args: PatternLike): StrudelPattern = _pickSqueeze(args.toList().asStrudelDslArgs())

/** Alias for [inhabit] on this pattern. */
@StrudelDsl
fun StrudelPattern.pickSqueeze(vararg args: PatternLike): StrudelPattern =
    this._pickSqueeze(args.toList().asStrudelDslArgs())

/** Alias for [inhabit] on a string pattern. */
@StrudelDsl
fun String.pickSqueeze(vararg args: PatternLike): StrudelPattern = this._pickSqueeze(args.toList().asStrudelDslArgs())

// -- inhabitmod() -----------------------------------------------------------------------------------------------------

internal val _inhabitmod by dslPatternFunction { args, _ ->
    if (args.size < 2) return@dslPatternFunction silence

    val first = args[0].value
    val lookup: Any
    val patArg: StrudelDslArg<Any?>

    if (first is List<*> || first is Map<*, *>) {
        lookup = first
        patArg = args[1]
    } else {
        lookup = args.dropLast(1).map { it.value }
        patArg = args.last()
    }

    val pat = listOf(patArg).toPattern(voiceValueModifier)
    dispatchInhabit(lookup, pat, modulo = true)
}

internal val StrudelPattern._inhabitmod by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p

    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }

    dispatchInhabit(lookup, p, modulo = true, args.firstOrNull()?.location)
}

internal val String._inhabitmod by dslStringExtension { p, args, callInfo -> p._inhabitmod(args, callInfo) }

/**
 * Like [inhabit] but wraps indices with modulo.
 *
 * The last argument (or the second when the first is a list/map) is the index pattern.
 * Selected patterns are squeezed into each triggering event's timespan; indices wrap cyclically.
 *
 * ```KlangScript
 * inhabitmod("bd", "sd", "hh", n("0 1 2 3"))   // 3→0 mod 3; squeezed into event
 * ```
 *
 * ```KlangScript
 * inhabitmod(["c3 e3", "g3 b3"], n("0 5"))      // 5→1 mod 2; squeezed in
 * ```
 *
 * @alias pickmodSqueeze
 * @category structural
 * @tags inhabitmod, pickmodSqueeze, modulo, squeeze, select, index, lookup
 */
@StrudelDsl
fun inhabitmod(vararg args: PatternLike): StrudelPattern = _inhabitmod(args.toList().asStrudelDslArgs())

/**
 * Like [inhabit] but wraps out-of-bounds indices with modulo arithmetic.
 *
 * Selects patterns from the lookup using modulo-wrapped indices and squeezes each selected
 * pattern into the timespan of the event that triggered it. Negative indices are handled.
 *
 * ```KlangScript
 * "0 1 2 3".inhabitmod("bd sd", "hh rim", "cp").s()       // 3→0 mod 3; squeezed
 * ```
 *
 * ```KlangScript
 * n("0 5").inhabitmod(note("c3"), note("e3"), note("g3"))  // 5→2 mod 3; squeezed
 * ```
 *
 * @alias pickmodSqueeze
 * @category structural
 * @tags inhabitmod, pickmodSqueeze, modulo, squeeze, select, index, lookup
 */
@StrudelDsl
fun StrudelPattern.inhabitmod(vararg args: PatternLike): StrudelPattern =
    this._inhabitmod(args.toList().asStrudelDslArgs())

/** Selects patterns from a list (modulo-wrapped index) and squeezes them into event timespans. */
@StrudelDsl
fun String.inhabitmod(vararg args: PatternLike): StrudelPattern = this._inhabitmod(args.toList().asStrudelDslArgs())

internal val _pickmodSqueeze by dslPatternFunction { args, callInfo -> _inhabitmod(args, callInfo) }
internal val StrudelPattern._pickmodSqueeze by dslPatternExtension { p, args, callInfo ->
    p._inhabitmod(
        args,
        callInfo
    )
}
internal val String._pickmodSqueeze by dslStringExtension { p, args, callInfo -> p._pickmodSqueeze(args, callInfo) }

/** Alias for [inhabitmod]. Selects patterns by modulo-wrapped index and squeezes into events. */
@StrudelDsl
fun pickmodSqueeze(vararg args: PatternLike): StrudelPattern = _pickmodSqueeze(args.toList().asStrudelDslArgs())

/** Alias for [inhabitmod] on this pattern. */
@StrudelDsl
fun StrudelPattern.pickmodSqueeze(vararg args: PatternLike): StrudelPattern =
    this._pickmodSqueeze(args.toList().asStrudelDslArgs())

/** Alias for [inhabitmod] on a string pattern. */
@StrudelDsl
fun String.pickmodSqueeze(vararg args: PatternLike): StrudelPattern =
    this._pickmodSqueeze(args.toList().asStrudelDslArgs())

// -- squeeze() --------------------------------------------------------------------------------------------------------

internal val _squeeze by dslPatternFunction { args, _ ->
    // squeeze(selector, lookup...)
    if (args.size < 2) return@dslPatternFunction silence

    val selectorArg = args[0]
    val selector = listOf(selectorArg).toPattern(voiceValueModifier)

    val secondArg = args[1]

    val lookup: Any
    val lookupLocation: SourceLocation?

    if (secondArg.value is List<*> || secondArg.value is Map<*, *>) {
        lookup = secondArg.value
        lookupLocation = secondArg.location
    } else {
        lookup = args.drop(1).map { it.value }
        lookupLocation = args.getOrNull(1)?.location
    }

    dispatchInhabit(lookup, selector, modulo = false, lookupLocation)
}

internal val StrudelPattern._squeeze by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p

    val first = args[0].value
    val lookup: Any

    if (first is List<*> || first is Map<*, *>) {
        lookup = first
    } else {
        lookup = args.map { it.value }
    }

    dispatchInhabit(lookup, p, modulo = false, args.firstOrNull()?.location)
}

internal val String._squeeze by dslStringExtension { p, args, callInfo -> p._squeeze(args, callInfo) }

/**
 * Selects patterns from a list by index and squeezes them into the triggering event's timespan.
 *
 * Like [inhabit] but with arguments in the opposite order: the first argument is the selector
 * (index) pattern and the remaining arguments are the lookup patterns. Indices are clamped.
 *
 * ```KlangScript
 * squeeze(n("0 1 2"), "bd sd hh", "rim cp", "hh*4").s()  // selector first, then lookup
 * ```
 *
 * ```KlangScript
 * squeeze(n("0 1"), note("c3 e3"), note("g3 b3"))         // index → squeezed pattern
 * ```
 *
 * @category structural
 * @tags squeeze, inhabit, pickSqueeze, select, index, lookup
 */
@StrudelDsl
fun squeeze(vararg args: PatternLike): StrudelPattern = _squeeze(args.toList().asStrudelDslArgs())

/**
 * Squeezes patterns from a list into the timespans of events in this pattern (clamped index).
 *
 * The receiver pattern provides the indices; the arguments are the lookup patterns squeezed
 * into each event's duration. Equivalent to calling `inhabit(lookup, this)`.
 *
 * ```KlangScript
 * n("0 1 2").squeeze("bd sd hh", "rim cp", "hh*4").s()  // receiver is index pattern
 * ```
 *
 * ```KlangScript
 * "0 1".squeeze(note("c3 e3"), note("g3 b3"))            // string receiver; squeezes in
 * ```
 *
 * @category structural
 * @tags squeeze, inhabit, select, index, lookup
 */
@StrudelDsl
fun StrudelPattern.squeeze(vararg args: PatternLike): StrudelPattern = this._squeeze(args.toList().asStrudelDslArgs())

/** Squeezes patterns from a list into the timespans of events in this string pattern (clamped). */
@StrudelDsl
fun String.squeeze(vararg args: PatternLike): StrudelPattern = this._squeeze(args.toList().asStrudelDslArgs())

// -- pickRestart() ----------------------------------------------------------------------------------------------------

private fun applyPickRestart(
    lookup: Any,
    pat: StrudelPattern,
    modulo: Boolean,
    baseLocation: SourceLocation? = null,
): StrudelPattern {
    val reifiedLookup = reifyLookup(lookup, baseLocation) ?: return silence
    val isList = lookup is List<*>

    val keyExtractor: (StrudelVoiceData, Boolean, Int) -> Any? = if (isList) {
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
    pat: StrudelPattern,
    modulo: Boolean,
    baseLocation: SourceLocation? = null,
): StrudelPattern {
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

internal val _pickRestart by dslPatternMapper { args, callInfo -> { p -> p._pickRestart(args, callInfo) } }

internal val StrudelPattern._pickRestart by dslPatternExtension { p, args, callInfo ->
    if (args.isEmpty()) return@dslPatternExtension p
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    dispatchPickRestart(lookup, p, modulo = false, callInfo?.receiverLocation)
}

internal val String._pickRestart by dslStringExtension { p, args, callInfo -> p._pickRestart(args, callInfo) }

internal val PatternMapperFn._pickRestart by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_pickRestart(args, callInfo))
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
 * ```KlangScript
 * seq("<0 1 2!2 3>").pickRestart("g a", "e f", "f g f g" , "g c d").note()
 * ```
 *
 * ```KlangScript
 * seq("<0 1 [2,0]>").pickRestart("bd sd", "cp cp", "hh hh").s()
 * ```
 *
 * @category structural
 * @tags pickRestart, pick, restart, trigger, select, index
 */
@StrudelDsl
fun StrudelPattern.pickRestart(vararg args: PatternLike): StrudelPattern =
    this._pickRestart(args.toList().asStrudelDslArgs())

/**
 * Like [pick] but restarts the chosen pattern — [List] lookup using this pattern's event values.
 *
 * @param lookup List of items; integer indices, clamped. Selected item restarts on each trigger.
 * @return A pattern that restarts the selected item on each trigger.
 *
 * ```KlangScript
 * seq("<0 1 2!2 3>").pickRestart(["g a", "e f", "f g f g" , "g c d"]).note()
 * ```
 */
@StrudelDsl
fun StrudelPattern.pickRestart(lookup: List<PatternLike>): StrudelPattern =
    this._pickRestart(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pick] but restarts the chosen pattern — [Map] lookup using this pattern's event values as keys.
 *
 * @param lookup Map of string keys to items; unmatched keys produce no output.
 * @return A pattern that restarts the selected item on each trigger.
 *
 * ```KlangScript
 * seq("<a!2 [a,b] b>").pickRestart({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
@StrudelDsl
fun StrudelPattern.pickRestart(lookup: Map<String, Any>): StrudelPattern =
    this._pickRestart(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pick] but restarts the chosen pattern — this string parsed as a mini-notation index pattern.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A pattern that restarts the selected item on each trigger.
 *
 * ```KlangScript
 * "<0 1 2 1>".pickRestart("bd", "sd", "hh").s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickRestart, pick, restart, trigger, select, index
 */
@StrudelDsl
fun String.pickRestart(vararg args: PatternLike): StrudelPattern = this._pickRestart(args.toList().asStrudelDslArgs())

/**
 * Like [pick] but restarts the chosen pattern — [List] lookup, this string as index pattern.
 *
 * @param lookup List of items; integer indices, clamped.
 * @return A pattern that restarts the selected item on each trigger.
 *
 * ```KlangScript
 * "<0 1 2 1>".pickRestart(["bd", "sd", "hh"]).s().fast(2)
 * ```
 */
@StrudelDsl
fun String.pickRestart(lookup: List<PatternLike>): StrudelPattern = seq(this).pickRestart(lookup)

/**
 * Like [pick] but restarts the chosen pattern — [Map] lookup, this string as key pattern.
 *
 * @param lookup Map of string keys; unmatched keys produce no output.
 * @return A pattern that restarts the selected item on each trigger.
 *
 * ```KlangScript
 * "<a!2 [a,b] b>".pickRestart({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
@StrudelDsl
fun String.pickRestart(lookup: Map<String, Any>): StrudelPattern = seq(this).pickRestart(lookup)

/**
 * Returns a [PatternMapperFn] that selects from a lookup and restarts the chosen pattern on each trigger.
 *
 * Like [pick] but the selected pattern always restarts from its beginning. Apply using `.apply()`.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A [PatternMapperFn] that maps a source pattern to a restart-pick result.
 *
 * ```KlangScript
 * "<0 1 2 1>".apply(pickRestart("bd", "sd", "hh")).s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickRestart, pick, restart, trigger, select, index
 */
@StrudelDsl
fun pickRestart(vararg args: PatternLike): PatternMapperFn = _pickRestart(args.toList().asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a [List] lookup and restarts the chosen pattern.
 *
 * @param lookup List of items; source pattern values are used as zero-based indices (clamped).
 * @return A [PatternMapperFn] that maps a source pattern to a restart-pick result.
 *
 * ```KlangScript
 * "<0 1 2 1>".apply(pickRestart(["bd", "sd", "hh"])).s().fast(2)
 * ```
 */
@StrudelDsl
fun pickRestart(lookup: List<PatternLike>): PatternMapperFn = _pickRestart(listOf(lookup).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a [Map] lookup and restarts the chosen pattern.
 *
 * @param lookup Map of string keys; source pattern values are used as keys.
 * @return A [PatternMapperFn] that maps a source pattern to a restart-pick result.
 *
 * ```KlangScript
 * "<a!2 [a,b] b>".apply(pickRestart({a: "bd(3,8)", b: "sd sd"})).s()
 * ```
 */
@StrudelDsl
fun pickRestart(lookup: Map<String, Any>): PatternMapperFn = _pickRestart(listOf(lookup).asStrudelDslArgs())

/**
 * Chains a pickRestart onto this [PatternMapperFn]; clamped indices, restarts on trigger.
 */
@StrudelDsl
fun PatternMapperFn.pickRestart(vararg args: PatternLike): PatternMapperFn =
    this._pickRestart(args.toList().asStrudelDslArgs())

/**
 * Chains a pickRestart from a [List] lookup onto this [PatternMapperFn].
 */
@StrudelDsl
fun PatternMapperFn.pickRestart(lookup: List<PatternLike>): PatternMapperFn =
    this._pickRestart(listOf(lookup).asStrudelDslArgs())

/**
 * Chains a pickRestart from a [Map] lookup onto this [PatternMapperFn].
 */
@StrudelDsl
fun PatternMapperFn.pickRestart(lookup: Map<String, Any>): PatternMapperFn =
    this._pickRestart(listOf(lookup).asStrudelDslArgs())

// -- pickmodRestart() -------------------------------------------------------------------------------------------------

internal val _pickmodRestart by dslPatternMapper { args, callInfo -> { p -> p._pickmodRestart(args, callInfo) } }

internal val StrudelPattern._pickmodRestart by dslPatternExtension { p, args, callInfo ->
    if (args.isEmpty()) return@dslPatternExtension p
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    dispatchPickRestart(lookup, p, modulo = true, callInfo?.receiverLocation)
}

internal val String._pickmodRestart by dslStringExtension { p, args, callInfo -> p._pickmodRestart(args, callInfo) }

internal val PatternMapperFn._pickmodRestart by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_pickmodRestart(args, callInfo))
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
 * ```KlangScript
 * seq("<0 1 2!2 3>").pickmodRestart("g a", "e f", "f g f g" , "g c d").note()
 * ```
 *
 * ```KlangScript
 * seq("<0 1 [2,0]>").pickmodRestart("bd sd", "cp cp", "hh hh").s()
 * ```
 *
 * @category structural
 * @tags pickmodRestart, pickRestart, modulo, restart, trigger, index
 */
@StrudelDsl
fun StrudelPattern.pickmodRestart(vararg args: PatternLike): StrudelPattern =
    this._pickmodRestart(args.toList().asStrudelDslArgs())

/**
 * Like [pickRestart] but wraps indices with modulo — [List] lookup using this pattern's event values.
 *
 * @param lookup List of items; indices wrap cyclically with modulo.
 * @return A pattern that restarts the selected item on each trigger.
 *
 * ```KlangScript
 * seq("<0 1 2!2 3>").pickmodRestart(["g a", "e f", "f g f g" , "g c d"]).note()
 * ```
 */
@StrudelDsl
fun StrudelPattern.pickmodRestart(lookup: List<PatternLike>): StrudelPattern =
    this._pickmodRestart(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pickRestart] but wraps indices with modulo — [Map] lookup using this pattern's event values as keys.
 *
 * @param lookup Map of string keys; unmatched keys produce no output.
 * @return A pattern that restarts the selected item on each trigger.
 *
 * ```KlangScript
 * seq("<a!2 [a,b] b>").pickmodRestart({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
@StrudelDsl
fun StrudelPattern.pickmodRestart(lookup: Map<String, Any>): StrudelPattern =
    this._pickmodRestart(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pickRestart] but wraps indices with modulo — this string parsed as a mini-notation index pattern.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A pattern that restarts the selected item on each trigger; indices wrap cyclically.
 *
 * ```KlangScript
 * "<0 1 2 1>".pickmodRestart("bd", "sd", "hh").s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickmodRestart, pickRestart, modulo, restart, trigger, index
 */
@StrudelDsl
fun String.pickmodRestart(vararg args: PatternLike): StrudelPattern =
    this._pickmodRestart(args.toList().asStrudelDslArgs())

/**
 * Like [pickRestart] but wraps indices with modulo — [List] lookup, this string as index pattern.
 *
 * @param lookup List of items; indices wrap cyclically.
 * @return A pattern that restarts the selected item on each trigger.
 *
 * ```KlangScript
 * "<0 1 2 1>".pickmodRestart(["bd", "sd", "hh"]).s().fast(2)
 * ```
 */
@StrudelDsl
fun String.pickmodRestart(lookup: List<PatternLike>): StrudelPattern =
    this._pickmodRestart(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pickRestart] but wraps indices with modulo — [Map] lookup, this string as key pattern.
 *
 * @param lookup Map of string keys; unmatched keys produce no output.
 * @return A pattern that restarts the selected item on each trigger.
 *
 * ```KlangScript
 * "<a!2 [a,b] b>".pickmodRestart({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
@StrudelDsl
fun String.pickmodRestart(lookup: Map<String, PatternLike>): StrudelPattern =
    this._pickmodRestart(listOf(lookup).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a lookup with modulo indices and restarts the chosen pattern.
 *
 * Like [pickRestart] but wraps indices cyclically. Apply the returned mapper using `.apply()`.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A [PatternMapperFn] that maps a source pattern to a modulo restart-pick result.
 *
 * ```KlangScript
 * "<0 1 2 1>".apply(pickmodRestart("bd", "sd", "hh")).s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickmodRestart, pickRestart, modulo, restart, trigger, index
 */
@StrudelDsl
fun pickmodRestart(vararg args: PatternLike): PatternMapperFn =
    _pickmodRestart(args.toList().asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a [List] with modulo indices and restarts.
 *
 * @param lookup List of items; source pattern values are used as integer indices (modulo-wrapped).
 * @return A [PatternMapperFn] that maps a source pattern to a modulo restart-pick result.
 *
 * ```KlangScript
 * "<0 1 2 1>".apply(pickmodRestart(["bd", "sd", "hh"])).s().fast(2)
 * ```
 */
@StrudelDsl
fun pickmodRestart(lookup: List<PatternLike>): PatternMapperFn =
    _pickmodRestart(listOf(lookup).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a [Map] lookup and restarts the chosen pattern.
 *
 * @param lookup Map of string keys; source pattern values are used as keys.
 * @return A [PatternMapperFn] that maps a source pattern to a modulo restart-pick result.
 *
 * ```KlangScript
 * "<a!2 [a,b] b>".apply(pickmodRestart({a: "bd(3,8)", b: "sd sd"})).s()
 * ```
 */
@StrudelDsl
fun pickmodRestart(lookup: Map<String, PatternLike>): PatternMapperFn =
    _pickmodRestart(listOf(lookup).asStrudelDslArgs())

/**
 * Chains a pickmodRestart onto this [PatternMapperFn]; modulo indices, restarts on trigger.
 */
@StrudelDsl
fun PatternMapperFn.pickmodRestart(vararg args: PatternLike): PatternMapperFn =
    this._pickmodRestart(args.toList().asStrudelDslArgs())

/**
 * Chains a pickmodRestart from a [List] lookup onto this [PatternMapperFn].
 */
@StrudelDsl
fun PatternMapperFn.pickmodRestart(lookup: List<PatternLike>): PatternMapperFn =
    this._pickmodRestart(listOf(lookup).asStrudelDslArgs())

/**
 * Chains a pickmodRestart from a [Map] lookup onto this [PatternMapperFn].
 */
@StrudelDsl
fun PatternMapperFn.pickmodRestart(lookup: Map<String, PatternLike>): PatternMapperFn =
    this._pickmodRestart(listOf(lookup).asStrudelDslArgs())

// -- pickReset() ------------------------------------------------------------------------------------------------------

private fun applyPickReset(
    lookup: Any,
    pat: StrudelPattern,
    modulo: Boolean,
    baseLocation: SourceLocation? = null,
): StrudelPattern {
    val reifiedLookup = reifyLookup(lookup, baseLocation) ?: return silence
    val isList = lookup is List<*>

    val keyExtractor: (StrudelVoiceData, Boolean, Int) -> Any? = if (isList) {
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
    pat: StrudelPattern,
    modulo: Boolean,
    baseLocation: SourceLocation? = null,
): StrudelPattern {
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

internal val _pickReset by dslPatternMapper { args, callInfo -> { p -> p._pickReset(args, callInfo) } }

internal val StrudelPattern._pickReset by dslPatternExtension { p, args, callInfo ->
    if (args.isEmpty()) return@dslPatternExtension p
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    dispatchPickReset(lookup, p, modulo = false, callInfo?.receiverLocation)
}

internal val String._pickReset by dslStringExtension { p, args, callInfo -> p._pickReset(args, callInfo) }

internal val PatternMapperFn._pickReset by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_pickReset(args, callInfo))
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
 * ```KlangScript
 * seq("<0 1 2!2 3>").pickReset("g a", "e f", "f g f g" , "g c d").note()
 * ```
 *
 * ```KlangScript
 * seq("<0 1 [2,0]>").pickReset("bd sd", "cp cp", "hh hh").s()
 * ```
 *
 * @category structural
 * @tags pickReset, pick, reset, trigger, select, index
 */
@StrudelDsl
fun StrudelPattern.pickReset(vararg args: PatternLike): StrudelPattern =
    this._pickReset(args.toList().asStrudelDslArgs())

/**
 * Like [pick] but resets the chosen pattern — [List] lookup using this pattern's event values.
 *
 * @param lookup List of items; integer indices, clamped. Selected item's phase resets on each trigger.
 * @return A pattern that resets the selected item's phase on each trigger.
 *
 * ```KlangScript
 * seq("<0 1 2!2 3>").pickReset(["g a", "e f", "f g f g" , "g c d"]).note()
 * ```
 */
@StrudelDsl
fun StrudelPattern.pickReset(lookup: List<PatternLike>): StrudelPattern =
    this._pickReset(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pick] but resets the chosen pattern — [Map] lookup using this pattern's event values as keys.
 *
 * @param lookup Map of string keys to items; unmatched keys produce no output.
 * @return A pattern that resets the selected item's phase on each trigger.
 *
 * ```KlangScript
 * seq("<a!2 [a,b] b>").pickReset({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
@StrudelDsl
fun StrudelPattern.pickReset(lookup: Map<String, PatternLike>): StrudelPattern =
    this._pickReset(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pick] but resets the chosen pattern — this string parsed as a mini-notation index pattern.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A pattern that resets the selected item's phase on each trigger.
 *
 * ```KlangScript
 * "<0 1 2 1>".pickReset("bd", "sd", "hh").s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickReset, pick, reset, trigger, select, index
 */
@StrudelDsl
fun String.pickReset(vararg args: PatternLike): StrudelPattern =
    this._pickReset(args.toList().asStrudelDslArgs())

/**
 * Like [pick] but resets the chosen pattern — [List] lookup, this string as index pattern.
 *
 * @param lookup List of items; integer indices, clamped.
 * @return A pattern that resets the selected item's phase on each trigger.
 *
 * ```KlangScript
 * "<0 1 2 1>".pickReset(["bd", "sd", "hh"]).s().fast(2)
 * ```
 */
@StrudelDsl
fun String.pickReset(lookup: List<PatternLike>): StrudelPattern =
    this._pickReset(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pick] but resets the chosen pattern — [Map] lookup, this string as key pattern.
 *
 * @param lookup Map of string keys; unmatched keys produce no output.
 * @return A pattern that resets the selected item's phase on each trigger.
 *
 * ```KlangScript
 * "<a!2 [a,b] b>".pickReset({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
@StrudelDsl
fun String.pickReset(lookup: Map<String, PatternLike>): StrudelPattern =
    this._pickReset(listOf(lookup).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a lookup and resets the chosen pattern's phase on trigger.
 *
 * Like [pick] but the selected pattern's phase is reset on each trigger. Apply using `.apply()`.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A [PatternMapperFn] that maps a source pattern to a reset-pick result.
 *
 * ```KlangScript
 * "<0 1 2 1>".apply(pickReset("bd", "sd", "hh")).s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickReset, pick, reset, trigger, select, index
 */
@StrudelDsl
fun pickReset(vararg args: PatternLike): PatternMapperFn =
    _pickReset(args.toList().asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a [List] lookup and resets the chosen pattern.
 *
 * @param lookup List of items; source pattern values are used as zero-based indices (clamped).
 * @return A [PatternMapperFn] that maps a source pattern to a reset-pick result.
 *
 * ```KlangScript
 * "<0 1 2 1>".apply(pickReset(["bd", "sd", "hh"])).s().fast(2)
 * ```
 */
@StrudelDsl
fun pickReset(lookup: List<PatternLike>): PatternMapperFn =
    _pickReset(listOf(lookup).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a [Map] lookup and resets the chosen pattern.
 *
 * @param lookup Map of string keys; source pattern values are used as keys.
 * @return A [PatternMapperFn] that maps a source pattern to a reset-pick result.
 *
 * ```KlangScript
 * "<a!2 [a,b] b>".apply(pickReset({a: "bd(3,8)", b: "sd sd"})).s()
 * ```
 */
@StrudelDsl
fun pickReset(lookup: Map<String, PatternLike>): PatternMapperFn =
    _pickReset(listOf(lookup).asStrudelDslArgs())

/**
 * Chains a pickReset onto this [PatternMapperFn]; clamped indices, resets phase on trigger.
 */
@StrudelDsl
fun PatternMapperFn.pickReset(vararg args: PatternLike): PatternMapperFn =
    this._pickReset(args.toList().asStrudelDslArgs())

/**
 * Chains a pickReset from a [List] lookup onto this [PatternMapperFn].
 */
@StrudelDsl
fun PatternMapperFn.pickReset(lookup: List<PatternLike>): PatternMapperFn =
    this._pickReset(listOf(lookup).asStrudelDslArgs())

/**
 * Chains a pickReset from a [Map] lookup onto this [PatternMapperFn].
 */
@StrudelDsl
fun PatternMapperFn.pickReset(lookup: Map<String, PatternLike>): PatternMapperFn =
    this._pickReset(listOf(lookup).asStrudelDslArgs())

// -- pickmodReset() ---------------------------------------------------------------------------------------------------

internal val _pickmodReset by dslPatternMapper { args, callInfo -> { p -> p._pickmodReset(args, callInfo) } }

internal val StrudelPattern._pickmodReset by dslPatternExtension { p, args, callInfo ->
    if (args.isEmpty()) return@dslPatternExtension p
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    dispatchPickReset(lookup, p, modulo = true, callInfo?.receiverLocation)
}

internal val String._pickmodReset by dslStringExtension { p, args, callInfo -> p._pickmodReset(args, callInfo) }

internal val PatternMapperFn._pickmodReset by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_pickmodReset(args, callInfo))
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
 * ```KlangScript
 * seq("<0 1 2!2 3>").pickmodReset("g a", "e f", "f g f g" , "g c d").note()
 * ```
 *
 * ```KlangScript
 * seq("<0 1 [2,0]>").pickmodReset("bd sd", "cp cp", "hh hh").s()
 * ```
 *
 * @category structural
 * @tags pickmodReset, pickReset, modulo, reset, trigger, index
 */
@StrudelDsl
fun StrudelPattern.pickmodReset(vararg args: PatternLike): StrudelPattern =
    this._pickmodReset(args.toList().asStrudelDslArgs())

/**
 * Like [pickReset] but wraps indices with modulo — [List] lookup using this pattern's event values.
 *
 * @param lookup List of items; indices wrap cyclically with modulo.
 * @return A pattern that resets the selected item's phase on each trigger.
 *
 * ```KlangScript
 * seq("<0 1 2!2 3>").pickmodReset(["g a", "e f", "f g f g" , "g c d"]).note()
 * ```
 */
@StrudelDsl
fun StrudelPattern.pickmodReset(lookup: List<PatternLike>): StrudelPattern =
    this._pickmodReset(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pickReset] but wraps indices with modulo — [Map] lookup using this pattern's event values as keys.
 *
 * @param lookup Map of string keys; unmatched keys produce no output.
 * @return A pattern that resets the selected item's phase on each trigger.
 *
 * ```KlangScript
 * seq("<a!2 [a,b] b>").pickmodReset({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
@StrudelDsl
fun StrudelPattern.pickmodReset(lookup: Map<String, PatternLike>): StrudelPattern =
    this._pickmodReset(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pickReset] but wraps indices with modulo — this string parsed as a mini-notation index pattern.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A pattern that resets the selected item's phase on each trigger; indices wrap cyclically.
 *
 * ```KlangScript
 * "<0 1 2 1>".pickmodReset("bd", "sd", "hh").s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickmodReset, pickReset, modulo, reset, trigger, index
 */
@StrudelDsl
fun String.pickmodReset(vararg args: PatternLike): StrudelPattern =
    this._pickmodReset(args.toList().asStrudelDslArgs())

/**
 * Like [pickReset] but wraps indices with modulo — [List] lookup, this string as index pattern.
 *
 * @param lookup List of items; indices wrap cyclically.
 * @return A pattern that resets the selected item's phase on each trigger.
 *
 * ```KlangScript
 * "<0 1 2 1>".pickmodReset(["bd", "sd", "hh"]).s().fast(2)
 * ```
 */
@StrudelDsl
fun String.pickmodReset(lookup: List<PatternLike>): StrudelPattern =
    this._pickmodReset(listOf(lookup).asStrudelDslArgs())

/**
 * Like [pickReset] but wraps indices with modulo — [Map] lookup, this string as key pattern.
 *
 * @param lookup Map of string keys; unmatched keys produce no output.
 * @return A pattern that resets the selected item's phase on each trigger.
 *
 * ```KlangScript
 * "<a!2 [a,b] b>".pickmodReset({a: "bd(3,8)", b: "sd sd"}).s()
 * ```
 */
@StrudelDsl
fun String.pickmodReset(lookup: Map<String, PatternLike>): StrudelPattern =
    this._pickmodReset(listOf(lookup).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a lookup with modulo indices and resets the chosen pattern.
 *
 * Like [pickReset] but wraps indices cyclically. Apply the returned mapper using `.apply()`.
 *
 * @param args Lookup items to pick from — strings, patterns, or a single map.
 * @return A [PatternMapperFn] that maps a source pattern to a modulo reset-pick result.
 *
 * ```KlangScript
 * "<0 1 2 1>".apply(pickmodReset("bd", "sd", "hh")).s().fast(2)
 * ```
 *
 * @category structural
 * @tags pickmodReset, pickReset, modulo, reset, trigger, index
 */
@StrudelDsl
fun pickmodReset(vararg args: PatternLike): PatternMapperFn =
    _pickmodReset(args.toList().asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a [List] with modulo indices and resets phase.
 *
 * @param lookup List of items; source pattern values are used as integer indices (modulo-wrapped).
 * @return A [PatternMapperFn] that maps a source pattern to a modulo reset-pick result.
 *
 * ```KlangScript
 * "<0 1 2 1>".apply(pickmodReset(["bd", "sd", "hh"])).s().fast(2)
 * ```
 */
@StrudelDsl
fun pickmodReset(lookup: List<PatternLike>): PatternMapperFn =
    _pickmodReset(listOf(lookup).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects from a [Map] lookup and resets the chosen pattern.
 *
 * @param lookup Map of string keys; source pattern values are used as keys.
 * @return A [PatternMapperFn] that maps a source pattern to a modulo reset-pick result.
 *
 * ```KlangScript
 * "<a!2 [a,b] b>".apply(pickmodReset({a: "bd(3,8)", b: "sd sd"})).s()
 * ```
 */
@StrudelDsl
fun pickmodReset(lookup: Map<String, PatternLike>): PatternMapperFn =
    _pickmodReset(listOf(lookup).asStrudelDslArgs())

/**
 * Chains a pickmodReset onto this [PatternMapperFn]; modulo indices, resets phase on trigger.
 */
@StrudelDsl
fun PatternMapperFn.pickmodReset(vararg args: PatternLike): PatternMapperFn =
    this._pickmodReset(args.toList().asStrudelDslArgs())

/**
 * Chains a pickmodReset from a [List] lookup onto this [PatternMapperFn].
 */
@StrudelDsl
fun PatternMapperFn.pickmodReset(lookup: List<PatternLike>): PatternMapperFn =
    this._pickmodReset(listOf(lookup).asStrudelDslArgs())

/**
 * Chains a pickmodReset from a [Map] lookup onto this [PatternMapperFn].
 */
@StrudelDsl
fun PatternMapperFn.pickmodReset(lookup: Map<String, PatternLike>): PatternMapperFn =
    this._pickmodReset(listOf(lookup).asStrudelDslArgs())

// -- pickF() ----------------------------------------------------------------------------------------------------------

/**
 * Apply functions from a list based on a pattern of indices.
 * Indices are clamped to the list size.
 *
 * JavaScript: `pat.apply(pick(lookup, funcs))`
 *
 * Example: `s("bd [rim hh]").pickF("<0 1 2>", [rev, jux(rev), fast(2)])`
 */
fun applyPickF(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

internal val StrudelPattern._pickF by dslPatternExtension { p, args, _ -> applyPickF(p, args) }

internal val _pickF by dslPatternFunction { args, _ ->
    if (args.size < 3) return@dslPatternFunction silence
    val lookupArg = args[0]
    val funcsArg = args[1]
    val patArg = args[2]
    val pattern = listOf(patArg).toPattern(voiceValueModifier)
    applyPickF(pattern, listOf(lookupArg, funcsArg))
}

internal val String._pickF by dslStringExtension { p, args, callInfo -> p._pickF(args, callInfo) }

/**
 * Applies functions from a list to a pattern, selected by an index pattern (clamped).
 *
 * Arguments: index pattern, function list, source pattern. The index pattern drives which
 * function from the list is applied to the source pattern. Out-of-bounds indices are clamped.
 *
 * ```KlangScript
 * pickF("<0 1 2>", [rev, fast(2), jux(rev)], s("bd rim hh"))  // fn selected by index
 * ```
 *
 * ```KlangScript
 * pickF(n("0 1"), [slow(2), fast(3)], note("c3 e3"))           // 0→slow(2), 1→fast(3)
 * ```
 *
 * @category structural
 * @tags pickF, pick, apply, transform, function, index
 */
@StrudelDsl
fun pickF(vararg args: PatternLike): StrudelPattern = _pickF(args.toList().asStrudelDslArgs())

/**
 * Applies functions from a list to this pattern, selected by an index pattern (clamped).
 *
 * The first argument is the index pattern and the second is a list of pattern-transforming
 * functions. Each index (clamped to bounds) selects a function which is applied to this pattern.
 *
 * ```KlangScript
 * s("bd rim hh").pickF("<0 1 2>", [rev, fast(2), jux(rev)])  // apply fn by index
 * ```
 *
 * ```KlangScript
 * note("c3 e3 g3").pickF(n("0 1"), [slow(2), fast(3)])       // 0→slow(2), 1→fast(3)
 * ```
 *
 * @category structural
 * @tags pickF, pick, apply, transform, function, index
 */
@StrudelDsl
fun StrudelPattern.pickF(vararg args: PatternLike): StrudelPattern = this._pickF(args.toList().asStrudelDslArgs())

/** Applies functions from a list to this string pattern, selected by an index pattern (clamped). */
@StrudelDsl
fun String.pickF(vararg args: PatternLike): StrudelPattern = this._pickF(args.toList().asStrudelDslArgs())

// -- pickmodF() -------------------------------------------------------------------------------------------------------

/**
 * Apply functions from a list based on a pattern of indices.
 * Indices wrap around (modulo) if greater than list size.
 *
 * JavaScript: `pat.apply(pickmod(lookup, funcs))`
 */
fun applyPickmodF(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

internal val StrudelPattern._pickmodF by dslPatternExtension { p, args, _ -> applyPickmodF(p, args) }

internal val _pickmodF by dslPatternFunction { args, _ ->
    if (args.size < 3) return@dslPatternFunction silence
    val lookupArg = args[0]
    val funcsArg = args[1]
    val patArg = args[2]
    val pattern = listOf(patArg).toPattern(voiceValueModifier)
    applyPickmodF(pattern, listOf(lookupArg, funcsArg))
}

internal val String._pickmodF by dslStringExtension { p, args, callInfo -> p._pickmodF(args, callInfo) }

/**
 * Like [pickF] but wraps indices with modulo.
 *
 * Arguments: index pattern, function list, source pattern. The index selects a function from
 * the list with modulo wrapping when out of bounds.
 *
 * ```KlangScript
 * pickmodF("<0 1 2 3>", [rev, fast(2)], s("bd rim hh"))  // 2→0, 3→1 mod 2; apply fn
 * ```
 *
 * ```KlangScript
 * pickmodF(n("0 5"), [slow(2), fast(3)], note("c3 e3"))   // 5→1 mod 2; apply fn
 * ```
 *
 * @category structural
 * @tags pickmodF, pickF, modulo, apply, transform, function, index
 */
@StrudelDsl
fun pickmodF(vararg args: PatternLike): StrudelPattern = _pickmodF(args.toList().asStrudelDslArgs())

/**
 * Like [pickF] but wraps out-of-bounds indices with modulo arithmetic.
 *
 * Applies functions from a list to this pattern. The first argument is the index pattern and
 * the second is the function list. Indices exceeding the list length wrap cyclically.
 *
 * ```KlangScript
 * s("bd rim hh").pickmodF("<0 1 2 3>", [rev, fast(2)])  // 2→0, 3→1 mod 2; apply fn
 * ```
 *
 * ```KlangScript
 * note("c3 e3").pickmodF(n("0 5"), [slow(2), fast(3), jux(rev)])  // 5→2 mod 3; fn applied
 * ```
 *
 * @category structural
 * @tags pickmodF, pickF, modulo, apply, transform, function, index
 */
@StrudelDsl
fun StrudelPattern.pickmodF(vararg args: PatternLike): StrudelPattern = this._pickmodF(args.toList().asStrudelDslArgs())

/** Applies functions from a list to this string pattern, selected by an index pattern (modulo). */
@StrudelDsl
fun String.pickmodF(vararg args: PatternLike): StrudelPattern = this._pickmodF(args.toList().asStrudelDslArgs())
