@file:Suppress("DuplicatedCode", "unused")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.script.ast.SourceLocationChain
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel._bind
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

/** pick() - Pattern extension */
@StrudelDsl
val StrudelPattern.pick by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p

    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    val lookupLocation = args.firstOrNull()?.location

    dispatchPick(lookup, p, modulo = false, lookupLocation)
}

/** pick() - Standalone function */
@StrudelDsl
val pick by dslFunction { args, _ ->
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
        // When using varargs, use location of first arg
        lookupLocation = args.firstOrNull()?.location
        patArg = args.last()
    }

    val pat = listOf(patArg).toPattern(voiceValueModifier)
    dispatchPick(lookup, pat, modulo = false, lookupLocation)
}

/** pick() - String extension */
@StrudelDsl
val String.pick by dslStringExtension { p, args, callInfo ->
    p.pick(args, callInfo)
}

/** Basic pick with List lookup (clamp indices) */
fun pick(lookup: List<Any>, pat: StrudelPattern): StrudelPattern =
    applyPickInner(lookup, pat, modulo = false)

/** Basic pick with Map lookup (clamp indices) */
fun pick(lookup: Map<String, Any>, pat: StrudelPattern): StrudelPattern =
    applyPickInner(lookup, pat, modulo = false)


// -- pickmod() --------------------------------------------------------------------------------------------------------

/** pickmod() - Pattern extension */
@StrudelDsl
val StrudelPattern.pickmod by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p

    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    val lookupLocation = args.firstOrNull()?.location

    dispatchPick(lookup, p, modulo = true, lookupLocation)
}

/** pickmod() - Standalone function */
@StrudelDsl
val pickmod by dslFunction { args, _ ->
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

/** pickmod() - String extension */
@StrudelDsl
val String.pickmod by dslStringExtension { p, args, callInfo ->
    p.pickmod(args, callInfo)
}

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

/** pickOut() - Pattern extension */
@StrudelDsl
val StrudelPattern.pickOut by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p

    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    val lookupLocation = args.firstOrNull()?.location

    dispatchPickOuter(lookup, p, modulo = false, lookupLocation)
}

/** pickOut() - Standalone function */
@StrudelDsl
val pickOut by dslFunction { args, _ ->
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

/** pickOut() - String extension */
@StrudelDsl
val String.pickOut by dslStringExtension { p, args, callInfo ->
    p.pickOut(args, callInfo)
}

// -- pickmodOut() -----------------------------------------------------------------------------------------------------

/** pickmodOut() - Pattern extension */
@StrudelDsl
val StrudelPattern.pickmodOut by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p

    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    val lookupLocation = args.firstOrNull()?.location

    dispatchPickOuter(lookup, p, modulo = true, lookupLocation)
}

/** pickmodOut() - Standalone function */
@StrudelDsl
val pickmodOut by dslFunction { args, _ ->
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

/** pickmodOut() - String extension */
@StrudelDsl
val String.pickmodOut by dslStringExtension { p, args, callInfo ->
    p.pickmodOut(args, callInfo)
}

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

/** inhabit() - Pattern extension */
@StrudelDsl
val StrudelPattern.inhabit by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p

    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }

    dispatchInhabit(lookup, p, modulo = false, args.firstOrNull()?.location)
}

/** inhabit() - Standalone function */
@StrudelDsl
val inhabit by dslFunction { args, _ ->
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

/** inhabit() - String extension */
@StrudelDsl
val String.inhabit by dslStringExtension { p, args, callInfo ->
    p.inhabit(args, callInfo)
}

/** alias pickSqueeze */
@StrudelDsl
val StrudelPattern.pickSqueeze by dslPatternExtension { p, args, callInfo -> p.inhabit(args, callInfo) }

/** alias pickSqueeze */
@StrudelDsl
val pickSqueeze by dslFunction { args, callInfo -> inhabit(args, callInfo) }

/** alias pickSqueeze */
@StrudelDsl
val String.pickSqueeze by dslStringExtension { p, args, callInfo -> p.inhabit(args, callInfo) }

// -- inhabitmod() -----------------------------------------------------------------------------------------------------

/** inhabitmod() - Pattern extension */
@StrudelDsl
val StrudelPattern.inhabitmod by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p

    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }

    dispatchInhabit(lookup, p, modulo = true, args.firstOrNull()?.location)
}

/** inhabitmod() - Standalone function */
@StrudelDsl
val inhabitmod by dslFunction { args, _ ->
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

/** inhabitmod() - String extension */
@StrudelDsl
val String.inhabitmod by dslStringExtension { p, args, callInfo ->
    p.inhabitmod(args, callInfo)
}

/** alias for [inhabitmod] */
@StrudelDsl
val StrudelPattern.pickmodSqueeze by dslPatternExtension { p, args, callInfo -> p.inhabitmod(args, callInfo) }

/** alias for [inhabitmod] */
@StrudelDsl
val pickmodSqueeze by dslFunction { args, callInfo -> inhabitmod(args, callInfo) }

/** alias for [inhabitmod] */
@StrudelDsl
val String.pickmodSqueeze by dslStringExtension { p, args, callInfo -> p.inhabitmod(args, callInfo) }

// -- squeeze() --------------------------------------------------------------------------------------------------------

/**
 * Selects patterns from list `xs` using index pattern `pat`, and squeezes each selected pattern
 * into the duration of the event that selected it.
 *
 * This is effectively `inhabit` with arguments flipped: `squeeze(selector, lookup)`.
 */
@StrudelDsl
val squeeze by dslFunction { args, _ ->
    // squeeze(selector, lookup...)
    if (args.size < 2) return@dslFunction silence

    val selectorArg = args[0]
    // Parse selector if it's a string/value
    val selector = listOf(selectorArg).toPattern(voiceValueModifier)

    val secondArg = args[1]

    val lookup: Any
    val lookupLocation: SourceLocation?

    if (secondArg.value is List<*> || secondArg.value is Map<*, *>) {
        lookup = secondArg.value
        lookupLocation = secondArg.location
    } else {
        // treat rest of args as list
        lookup = args.drop(1).map { it.value }
        lookupLocation = args.getOrNull(1)?.location
    }

    dispatchInhabit(lookup, selector, modulo = false, lookupLocation)
}

/**
 * Pattern method for squeeze.
 * `pat.squeeze(lookup)` -> same as `inhabit(lookup, pat)`
 */
@StrudelDsl
val StrudelPattern.squeeze by dslPatternExtension { p, args, _ ->
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

@StrudelDsl
val String.squeeze by dslStringExtension { p, args, callInfo -> p.squeeze(args, callInfo) }

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

/**
 * Like pick but restarts the chosen pattern when triggered.
 */
@StrudelDsl
val StrudelPattern.pickRestart by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    dispatchPickRestart(lookup, p, modulo = false, args.firstOrNull()?.location)
}

@StrudelDsl
val pickRestart by dslFunction { args, _ ->
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

@StrudelDsl
val String.pickRestart by dslStringExtension { p, args, callInfo -> p.pickRestart(args, callInfo) }

// -- pickmodRestart() -------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.pickmodRestart by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    dispatchPickRestart(lookup, p, modulo = true)
}

@StrudelDsl
val pickmodRestart by dslFunction { args, _ ->
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

@StrudelDsl
val String.pickmodRestart by dslStringExtension { p, args, callInfo -> p.pickmodRestart(args, callInfo) }

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

/**
 * Like pick but resets the chosen pattern when triggered.
 */
@StrudelDsl
val StrudelPattern.pickReset by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    dispatchPickReset(lookup, p, modulo = false, args.firstOrNull()?.location)
}

@StrudelDsl
val pickReset by dslFunction { args, _ ->
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

@StrudelDsl
val String.pickReset by dslStringExtension { p, args, callInfo -> p.pickReset(args, callInfo) }

// -- pickmodReset() ---------------------------------------------------------------------------------------------------

@StrudelDsl
val StrudelPattern.pickmodReset by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p
    val first = args[0].value
    val lookup = if (first is List<*> || first is Map<*, *>) first else args.map { it.value }
    dispatchPickReset(lookup, p, modulo = true)
}

@StrudelDsl
val pickmodReset by dslFunction { args, _ ->
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

@StrudelDsl
val String.pickmodReset by dslStringExtension { p, args, callInfo -> p.pickmodReset(args, callInfo) }

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

/** Apply functions based on index pattern (clamp indices) */
@StrudelDsl
val StrudelPattern.pickF by dslPatternExtension { p, args, _ -> applyPickF(p, args) }

/** Apply functions based on index pattern (clamp indices) */
@StrudelDsl
val pickF by dslFunction { args, _ ->
    if (args.size < 3) return@dslFunction silence
    val lookupArg = args[0]
    val funcsArg = args[1]
    val patArg = args[2]
    val pattern = listOf(patArg).toPattern(voiceValueModifier)
    applyPickF(pattern, listOf(lookupArg, funcsArg))
}

/** Apply functions based on index pattern (clamp indices) */
@StrudelDsl
val String.pickF by dslStringExtension { p, args, callInfo ->
    p.pickF(args, callInfo)
}

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

/** Apply functions based on index pattern (wrap indices) */
@StrudelDsl
val StrudelPattern.pickmodF by dslPatternExtension { p, args, _ ->
    applyPickmodF(p, args)
}

/** Apply functions based on index pattern (wrap indices) */
@StrudelDsl
val pickmodF by dslFunction { args, _ ->
    if (args.size < 3) return@dslFunction silence
    val lookupArg = args[0]
    val funcsArg = args[1]
    val patArg = args[2]
    val pattern = listOf(patArg).toPattern(voiceValueModifier)
    applyPickmodF(pattern, listOf(lookupArg, funcsArg))
}

/** Apply functions based on index pattern (wrap indices) */
@StrudelDsl
val String.pickmodF by dslStringExtension { p, args, callInfo ->
    p.pickmodF(args, callInfo)
}
