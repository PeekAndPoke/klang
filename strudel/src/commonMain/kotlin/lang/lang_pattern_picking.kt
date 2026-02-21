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

internal val _pick by dslFunction { args, _ ->
    if (args.size < 2) return@dslFunction silence

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
    dispatchPick(lookup, pat, modulo = false, lookupLocation)
}

internal val StrudelPattern._pick by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p

    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    val lookupLocation = args.firstOrNull()?.location

    dispatchPick(lookup, p, modulo = false, lookupLocation)
}

internal val String._pick by dslStringExtension { p, args, callInfo -> p._pick(args, callInfo) }

/**
 * Selects patterns from a list or map using an index pattern, clamping out-of-bounds indices.
 *
 * When the first argument is a list or map, the second argument is used as the index pattern.
 * Otherwise all arguments except the last are treated as the lookup list, and the last
 * argument is the index pattern. Out-of-bounds indices are clamped.
 *
 * ```KlangScript
 * pick("bd", "sd", "hh", n("0 1 2 1")).s()    // varargs: last arg is index pattern
 * ```
 *
 * ```KlangScript
 * pick(["c3", "e3", "g3"], n("0 1 2")).note()  // list lookup, index pattern as second arg
 * ```
 *
 * @category structural
 * @tags pick, select, index, lookup
 */
@StrudelDsl
fun pick(vararg args: PatternLike): StrudelPattern = _pick(args.toList().asStrudelDslArgs())

/**
 * Selects patterns from a list or map using the values of this pattern as indices.
 *
 * Each event's numeric value is used as a zero-based index into the lookup. Out-of-bounds
 * indices are clamped to the nearest valid position. The lookup can be a list (integer
 * indices) or a map (string keys). The selected pattern's timing structure is preserved.
 *
 * ```KlangScript
 * "0 1 2 1".pick("bd", "sd", "hh").s()              // index selects drum each event
 * ```
 *
 * ```KlangScript
 * n("0 1 2 0").pick(note("c3"), note("e3"), note("g3"))  // pick chord tones by index
 * ```
 *
 * @category structural
 * @tags pick, select, index, lookup
 */
@StrudelDsl
fun StrudelPattern.pick(vararg args: PatternLike): StrudelPattern = this._pick(args.toList().asStrudelDslArgs())

/** Selects patterns from a list using this string as the index pattern, clamping out-of-bounds. */
@StrudelDsl
fun String.pick(vararg args: PatternLike): StrudelPattern = this._pick(args.toList().asStrudelDslArgs())

/** Basic pick with List lookup (clamp indices) */
fun pick(lookup: List<Any>, pat: StrudelPattern): StrudelPattern =
    applyPickInner(lookup, pat, modulo = false)

/** Basic pick with Map lookup (clamp indices) */
fun pick(lookup: Map<String, Any>, pat: StrudelPattern): StrudelPattern =
    applyPickInner(lookup, pat, modulo = false)

// -- pickmod() --------------------------------------------------------------------------------------------------------

internal val _pickmod by dslFunction { args, _ ->
    if (args.size < 2) return@dslFunction silence

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
    dispatchPick(lookup, pat, modulo = true, lookupLocation)
}

internal val StrudelPattern._pickmod by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p

    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    val lookupLocation = args.firstOrNull()?.location

    dispatchPick(lookup, p, modulo = true, lookupLocation)
}

internal val String._pickmod by dslStringExtension { p, args, callInfo -> p._pickmod(args, callInfo) }

/**
 * Like [pick] but wraps out-of-bounds indices with modulo.
 *
 * When the first argument is a list or map, the second argument is used as the index pattern.
 * Otherwise all arguments except the last form the lookup list, and the last is the index
 * pattern. Indices wrap cyclically; see [pick] for clamped behaviour.
 *
 * ```KlangScript
 * pickmod("bd", "sd", "hh", n("0 1 2 3 4")).s()  // 3→0, 4→1 wrapping
 * ```
 *
 * ```KlangScript
 * pickmod(["c3", "e3", "g3"], n("0 4 8")).note()  // all indices wrap to valid range
 * ```
 *
 * @category structural
 * @tags pickmod, pick, modulo, wrap, index, lookup
 */
@StrudelDsl
fun pickmod(vararg args: PatternLike): StrudelPattern = _pickmod(args.toList().asStrudelDslArgs())

/**
 * Like [pick] but wraps out-of-bounds indices with modulo arithmetic.
 *
 * Each event's numeric value is used as a zero-based index into the lookup; indices beyond
 * the list length wrap cyclically. Negative indices are also handled correctly.
 *
 * ```KlangScript
 * "0 1 2 3 4".pickmod("bd", "sd", "hh").s()    // index 3→0, 4→1 (wrap modulo 3)
 * ```
 *
 * ```KlangScript
 * n("0 1 -1 4").pickmod(note("c3"), note("e3"), note("g3"))  // negatives wrap too
 * ```
 *
 * @category structural
 * @tags pickmod, pick, modulo, wrap, index, lookup
 */
@StrudelDsl
fun StrudelPattern.pickmod(vararg args: PatternLike): StrudelPattern = this._pickmod(args.toList().asStrudelDslArgs())

/** Selects patterns from a list using this string as the index pattern, wrapping with modulo. */
@StrudelDsl
fun String.pickmod(vararg args: PatternLike): StrudelPattern = this._pickmod(args.toList().asStrudelDslArgs())

/** Pick with modulo wrapping - List lookup */
fun pickmod(lookup: List<Any>, pat: StrudelPattern): StrudelPattern =
    applyPickInner(lookup, pat, modulo = true)

/** Pick with modulo wrapping - Map lookup */
fun pickmod(lookup: Map<String, Any>, pat: StrudelPattern): StrudelPattern =
    applyPickInner(lookup, pat, modulo = true)

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

internal val _pickOut by dslFunction { args, _ ->
    if (args.size < 2) return@dslFunction silence
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
    dispatchPickOuter(lookup, pat, modulo = false, lookupLocation)
}

internal val StrudelPattern._pickOut by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p

    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    val lookupLocation = args.firstOrNull()?.location

    dispatchPickOuter(lookup, p, modulo = false, lookupLocation)
}

internal val String._pickOut by dslStringExtension { p, args, callInfo -> p._pickOut(args, callInfo) }

/**
 * Like [pick] but uses outer-join semantics; clamped indices.
 *
 * Onsets are determined by the index pattern, not the selected pattern. When the first
 * argument is a list or map, the second is the index pattern; otherwise the last argument
 * is the index pattern and the rest form the lookup.
 *
 * ```KlangScript
 * pickOut("bd sd", "hh rim", n("0 1 0 1")).s()   // outer structure from index pattern
 * ```
 *
 * ```KlangScript
 * pickOut(["c3 e3", "g3 b3"], n("0 1")).note()   // list lookup, outer-join mode
 * ```
 *
 * @category structural
 * @tags pickOut, pick, select, index, outer, outerJoin
 */
@StrudelDsl
fun pickOut(vararg args: PatternLike): StrudelPattern = _pickOut(args.toList().asStrudelDslArgs())

/**
 * Like [pick] but uses outer-join semantics: onset structure is determined by the selector.
 *
 * Whereas [pick] uses inner-join (onset from the *selected* pattern), `pickOut` forces all
 * resulting events to share the onset of the selecting event. Useful when you want events
 * to fire exactly where the selector fires, regardless of the selected pattern's rhythm.
 * Indices are clamped.
 *
 * ```KlangScript
 * "0 1 2".pickOut("bd sd", "rim hh", "cp").s()       // outer structure from selector
 * ```
 *
 * ```KlangScript
 * n("0 1").pickOut(note("c3 e3"), note("g3 b3"))      // onset always follows selector
 * ```
 *
 * @category structural
 * @tags pickOut, pick, select, index, outer, outerJoin
 */
@StrudelDsl
fun StrudelPattern.pickOut(vararg args: PatternLike): StrudelPattern = this._pickOut(args.toList().asStrudelDslArgs())

/** Selects patterns by index (clamped) using this string as the selector; outer-join semantics. */
@StrudelDsl
fun String.pickOut(vararg args: PatternLike): StrudelPattern = this._pickOut(args.toList().asStrudelDslArgs())

// -- pickmodOut() -----------------------------------------------------------------------------------------------------

internal val _pickmodOut by dslFunction { args, _ ->
    if (args.size < 2) return@dslFunction silence
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
    dispatchPickOuter(lookup, pat, modulo = true, lookupLocation)
}

internal val StrudelPattern._pickmodOut by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p

    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    val lookupLocation = args.firstOrNull()?.location

    dispatchPickOuter(lookup, p, modulo = true, lookupLocation)
}

internal val String._pickmodOut by dslStringExtension { p, args, callInfo -> p._pickmodOut(args, callInfo) }

/**
 * Like [pickOut] but wraps indices with modulo; outer-join semantics.
 *
 * Onsets are driven by the index pattern; selected pattern indices wrap cyclically.
 * When the first argument is a list or map, the second is the index pattern.
 *
 * ```KlangScript
 * pickmodOut("bd", "sd", "hh", n("0 1 2 3 4")).s()  // 3→0, 4→1; outer onset
 * ```
 *
 * ```KlangScript
 * pickmodOut(["c3", "e3", "g3"], n("0 5")).note()    // 5→2 mod 3; outer onset
 * ```
 *
 * @category structural
 * @tags pickmodOut, pickOut, pickmod, modulo, select, index, outer
 */
@StrudelDsl
fun pickmodOut(vararg args: PatternLike): StrudelPattern = _pickmodOut(args.toList().asStrudelDslArgs())

/**
 * Like [pickOut] but wraps out-of-bounds indices with modulo arithmetic.
 *
 * Combines modulo index wrapping with outer-join merging; see [pickOut] and [pickmod]
 * for details of each feature. Onsets are determined by the selector pattern.
 *
 * ```KlangScript
 * "0 1 2 3".pickmodOut("bd sd", "hh", "cp").s()          // index 3 wraps; outer onset
 * ```
 *
 * ```KlangScript
 * n("0 1 4").pickmodOut(note("c3"), note("e3"), note("g3"))  // 4→1 mod 3; outer onset
 * ```
 *
 * @category structural
 * @tags pickmodOut, pickOut, pickmod, modulo, select, index, outer
 */
@StrudelDsl
fun StrudelPattern.pickmodOut(vararg args: PatternLike): StrudelPattern =
    this._pickmodOut(args.toList().asStrudelDslArgs())

/** Selects patterns by index (modulo-wrapped) using this string as the selector; outer-join. */
@StrudelDsl
fun String.pickmodOut(vararg args: PatternLike): StrudelPattern = this._pickmodOut(args.toList().asStrudelDslArgs())

// -- inhabit() --------------------------------------------------------------------------------------------------------

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

internal val _inhabit by dslFunction { args, _ ->
    if (args.size < 2) return@dslFunction silence

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

internal val _pickSqueeze by dslFunction { args, callInfo -> _inhabit(args, callInfo) }
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

internal val _inhabitmod by dslFunction { args, _ ->
    if (args.size < 2) return@dslFunction silence

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

internal val _pickmodSqueeze by dslFunction { args, callInfo -> _inhabitmod(args, callInfo) }
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

internal val _squeeze by dslFunction { args, _ ->
    // squeeze(selector, lookup...)
    if (args.size < 2) return@dslFunction silence

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

internal val _pickRestart by dslFunction { args, _ ->
    if (args.size < 2) return@dslFunction silence
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
    dispatchPickRestart(lookup, pat, modulo = false, lookupLocation)
}

internal val StrudelPattern._pickRestart by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    dispatchPickRestart(lookup, p, modulo = false, args.firstOrNull()?.location)
}

internal val String._pickRestart by dslStringExtension { p, args, callInfo -> p._pickRestart(args, callInfo) }

/**
 * Like [pick] but restarts the chosen pattern from its beginning on each new trigger.
 *
 * The last argument is the index pattern; remaining arguments form the lookup list.
 * When the first argument is a list or map, the second is the index pattern.
 * Indices are clamped.
 *
 * ```KlangScript
 * pickRestart("bd sd", "hh rim", n("0 1 0 1")).s()     // restarts on each select
 * ```
 *
 * ```KlangScript
 * pickRestart(["c3 e3 g3", "b3 d4"], n("0 1 0")).note()  // chosen pattern restarts
 * ```
 *
 * @category structural
 * @tags pickRestart, pick, restart, trigger, select, index
 */
@StrudelDsl
fun pickRestart(vararg args: PatternLike): StrudelPattern = _pickRestart(args.toList().asStrudelDslArgs())

/**
 * Like [pick] but restarts the chosen pattern from the beginning when triggered.
 *
 * Each time an event selects a pattern from the lookup, the chosen pattern restarts from its
 * cycle boundary. Indices are clamped to valid bounds.
 *
 * ```KlangScript
 * "0 1 2".pickRestart("bd sd hh", "rim cp", "hh*4").s()       // restarts on each trigger
 * ```
 *
 * ```KlangScript
 * n("<0 1 2>").pickRestart(note("c3 e3"), note("g3"), note("b3"))  // restarts per cycle
 * ```
 *
 * @category structural
 * @tags pickRestart, pick, restart, trigger, select, index
 */
@StrudelDsl
fun StrudelPattern.pickRestart(vararg args: PatternLike): StrudelPattern =
    this._pickRestart(args.toList().asStrudelDslArgs())

/** Like [pick] but restarts selected patterns on trigger; this string provides indices (clamped). */
@StrudelDsl
fun String.pickRestart(vararg args: PatternLike): StrudelPattern = this._pickRestart(args.toList().asStrudelDslArgs())

// -- pickmodRestart() -------------------------------------------------------------------------------------------------

internal val _pickmodRestart by dslFunction { args, _ ->
    if (args.size < 2) return@dslFunction silence
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
    dispatchPickRestart(lookup, pat, modulo = true, lookupLocation)
}

internal val StrudelPattern._pickmodRestart by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    dispatchPickRestart(lookup, p, modulo = true)
}

internal val String._pickmodRestart by dslStringExtension { p, args, callInfo -> p._pickmodRestart(args, callInfo) }

/**
 * Like [pickRestart] but wraps indices with modulo.
 *
 * The last argument is the index pattern (or second when first is a list/map). Selected
 * patterns restart from their beginning on each trigger; indices wrap cyclically.
 *
 * ```KlangScript
 * pickmodRestart("bd", "sd", "hh", n("0 1 2 3")).s()     // 3→0 mod 3; restart
 * ```
 *
 * ```KlangScript
 * pickmodRestart(["c3 e3", "g3 b3"], n("0 5")).note()    // 5→1 mod 2; restart
 * ```
 *
 * @category structural
 * @tags pickmodRestart, pickRestart, modulo, restart, trigger, index
 */
@StrudelDsl
fun pickmodRestart(vararg args: PatternLike): StrudelPattern = _pickmodRestart(args.toList().asStrudelDslArgs())

/**
 * Like [pickRestart] but wraps out-of-bounds indices with modulo arithmetic.
 *
 * Selects patterns using modulo-wrapped indices and restarts the chosen pattern from its
 * beginning whenever it is triggered.
 *
 * ```KlangScript
 * "0 1 2 3".pickmodRestart("bd sd", "hh rim", "cp").s()       // 3→0 mod 3; restarts
 * ```
 *
 * ```KlangScript
 * n("0 5").pickmodRestart(note("c3"), note("e3"), note("g3"))  // 5→2 mod 3; restart
 * ```
 *
 * @category structural
 * @tags pickmodRestart, pickRestart, modulo, restart, trigger, index
 */
@StrudelDsl
fun StrudelPattern.pickmodRestart(vararg args: PatternLike): StrudelPattern =
    this._pickmodRestart(args.toList().asStrudelDslArgs())

/** Like [pickmod] but restarts selected patterns on trigger; this string provides indices (modulo). */
@StrudelDsl
fun String.pickmodRestart(vararg args: PatternLike): StrudelPattern =
    this._pickmodRestart(args.toList().asStrudelDslArgs())

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

internal val _pickReset by dslFunction { args, _ ->
    if (args.size < 2) return@dslFunction silence
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
    dispatchPickReset(lookup, pat, modulo = false, lookupLocation)
}

internal val StrudelPattern._pickReset by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    dispatchPickReset(lookup, p, modulo = false, args.firstOrNull()?.location)
}

internal val String._pickReset by dslStringExtension { p, args, callInfo -> p._pickReset(args, callInfo) }

/**
 * Like [pick] but resets the chosen pattern to its initial state on each new trigger.
 *
 * The last argument is the index pattern (or second when first is a list/map). Selected
 * patterns are reset to their initial phase on each trigger. Indices are clamped.
 *
 * ```KlangScript
 * pickReset("bd sd", "hh rim", n("0 1 0 1")).s()     // resets on each select
 * ```
 *
 * ```KlangScript
 * pickReset(["c3 e3 g3", "b3 d4"], n("0 1 0")).note()  // chosen pattern resets
 * ```
 *
 * @category structural
 * @tags pickReset, pick, reset, trigger, select, index
 */
@StrudelDsl
fun pickReset(vararg args: PatternLike): StrudelPattern = _pickReset(args.toList().asStrudelDslArgs())

/**
 * Like [pick] but resets the chosen pattern to its initial state when triggered.
 *
 * Similar to [pickRestart] but uses "reset" semantics: the selected pattern is reset to its
 * initial phase on each trigger event. Indices are clamped to valid bounds.
 *
 * ```KlangScript
 * "0 1 2".pickReset("bd sd hh", "rim cp", "hh*4").s()         // resets on each trigger
 * ```
 *
 * ```KlangScript
 * n("<0 1 2>").pickReset(note("c3 e3"), note("g3"), note("b3"))  // resets per cycle
 * ```
 *
 * @category structural
 * @tags pickReset, pick, reset, trigger, select, index
 */
@StrudelDsl
fun StrudelPattern.pickReset(vararg args: PatternLike): StrudelPattern =
    this._pickReset(args.toList().asStrudelDslArgs())

/** Like [pick] but resets selected patterns on trigger; this string provides indices (clamped). */
@StrudelDsl
fun String.pickReset(vararg args: PatternLike): StrudelPattern = this._pickReset(args.toList().asStrudelDslArgs())

// -- pickmodReset() ---------------------------------------------------------------------------------------------------

internal val _pickmodReset by dslFunction { args, _ ->
    if (args.size < 2) return@dslFunction silence
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
    dispatchPickReset(lookup, pat, modulo = true, lookupLocation)
}

internal val StrudelPattern._pickmodReset by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    dispatchPickReset(lookup, p, modulo = true)
}

internal val String._pickmodReset by dslStringExtension { p, args, callInfo -> p._pickmodReset(args, callInfo) }

/**
 * Like [pickReset] but wraps indices with modulo.
 *
 * The last argument is the index pattern (or second when first is a list/map). Selected
 * patterns are reset to their initial phase on each trigger; indices wrap cyclically.
 *
 * ```KlangScript
 * pickmodReset("bd", "sd", "hh", n("0 1 2 3")).s()     // 3→0 mod 3; reset
 * ```
 *
 * ```KlangScript
 * pickmodReset(["c3 e3", "g3 b3"], n("0 5")).note()    // 5→1 mod 2; reset
 * ```
 *
 * @category structural
 * @tags pickmodReset, pickReset, modulo, reset, trigger, index
 */
@StrudelDsl
fun pickmodReset(vararg args: PatternLike): StrudelPattern = _pickmodReset(args.toList().asStrudelDslArgs())

/**
 * Like [pickReset] but wraps out-of-bounds indices with modulo arithmetic.
 *
 * Selects patterns using modulo-wrapped indices and resets the chosen pattern to its initial
 * state whenever it is triggered.
 *
 * ```KlangScript
 * "0 1 2 3".pickmodReset("bd sd", "hh rim", "cp").s()       // 3→0 mod 3; resets
 * ```
 *
 * ```KlangScript
 * n("0 5").pickmodReset(note("c3"), note("e3"), note("g3"))  // 5→2 mod 3; reset
 * ```
 *
 * @category structural
 * @tags pickmodReset, pickReset, modulo, reset, trigger, index
 */
@StrudelDsl
fun StrudelPattern.pickmodReset(vararg args: PatternLike): StrudelPattern =
    this._pickmodReset(args.toList().asStrudelDslArgs())

/** Like [pickmod] but resets selected patterns on trigger; this string provides indices (modulo). */
@StrudelDsl
fun String.pickmodReset(vararg args: PatternLike): StrudelPattern = this._pickmodReset(args.toList().asStrudelDslArgs())

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

internal val _pickF by dslFunction { args, _ ->
    if (args.size < 3) return@dslFunction silence
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

internal val _pickmodF by dslFunction { args, _ ->
    if (args.size < 3) return@dslFunction silence
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
