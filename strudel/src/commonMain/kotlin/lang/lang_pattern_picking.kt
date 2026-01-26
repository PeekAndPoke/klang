@file:Suppress("DuplicatedCode", "unused")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.pattern.AtomicPattern
import io.peekandpoke.klang.strudel.pattern.PickInnerPattern
import io.peekandpoke.klang.strudel.pattern.PickSqueezePattern
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
 */
private fun reifyLookup(lookup: Any): Map<Any, StrudelPattern>? {
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
            else -> {
                // Wrap any value (including strings) in atomic pattern with default modifier
                // This converts strings to note values, numbers to values, etc.
                AtomicPattern(StrudelVoiceData.empty.defaultModifier(value))
            }
        }
    }
}

/**
 * Safely extracts an integer index from an event value.
 * Handles various numeric types and returns null for invalid values.
 */
private fun extractIndex(value: Any?, modulo: Boolean, len: Int): Int? {
    if (value == null || len == 0) return null

    // Handle various numeric types - DO NOT try to cast ControlPattern or other patterns
    val numericValue: Double = when (value) {
        is StrudelVoiceValue -> value.asDouble ?: return null
        is Int -> value.toDouble()
        is Long -> value.toDouble()
        is Double -> value
        is Float -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: return null
        // Don't handle patterns or complex types - return null
        else -> return null
    }

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
 * Safely extracts a string key from an event value.
 */
private fun extractKey(value: Any?): String {
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
 */
private fun applyPickInner(
    lookup: Any,
    pat: StrudelPattern,
    modulo: Boolean,
): StrudelPattern {
    // Validate and reify lookup
    val reifiedLookup = reifyLookup(lookup) ?: return silence
    val isList = lookup is List<*>

    // Create key extractor function based on list vs map
    val keyExtractor: (Any?, Boolean, Int) -> Any? = if (isList) {
        { value, mod, len -> extractIndex(value, mod, len) }
    } else {
        { value, _, _ -> extractKey(value) }
    }

    // Create and return the pick pattern
    return PickInnerPattern(
        selector = pat,
        lookup = reifiedLookup,
        modulo = modulo,
        extractKey = keyExtractor
    )
}

/** Helper to dispatch pick calls based on lookup type */
private fun dispatchPick(lookup: Any?, pat: StrudelPattern, modulo: Boolean): StrudelPattern {
    if (lookup == null) return silence

    return when (lookup) {
        is List<*> -> {
            @Suppress("UNCHECKED_CAST")
            applyPickInner(lookup as List<Any>, pat, modulo)
        }

        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            applyPickInner(lookup as Map<String, Any>, pat, modulo)
        }

        else -> silence
    }
}

/** pick() - Pattern extension */
@StrudelDsl
val StrudelPattern.pick by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p
    dispatchPick(args[0].value, p, modulo = false)
}

/** pick() - Standalone function */
@StrudelDsl
val pick by dslFunction { args, _ ->
    if (args.size < 2) return@dslFunction silence
    val lookup = args[0].value
    val pat = listOf(args[1]).toPattern(defaultModifier)
    dispatchPick(lookup, pat, modulo = false)
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
    dispatchPick(args[0].value, p, modulo = true)
}

/** pickmod() - Standalone function */
@StrudelDsl
val pickmod by dslFunction { args, _ ->
    if (args.size < 2) return@dslFunction silence
    val lookup = args[0].value
    val pat = listOf(args[1]).toPattern(defaultModifier)
    dispatchPick(lookup, pat, modulo = true)
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

// -- inhabit() --------------------------------------------------------------------------------------------------------

/**
 * Implementation of inhabit() / pickSqueeze() - selects patterns and squeezes them into the selector event.
 */
private fun applyInhabit(
    lookup: Any,
    pat: StrudelPattern,
    modulo: Boolean,
): StrudelPattern {
    // Validate and reify lookup
    val reifiedLookup = reifyLookup(lookup) ?: return silence
    val isList = lookup is List<*>

    // Create key extractor function based on list vs map
    val keyExtractor: (Any?, Boolean, Int) -> Any? = if (isList) {
        { value, mod, len -> extractIndex(value, mod, len) }
    } else {
        { value, _, _ -> extractKey(value) }
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
private fun dispatchInhabit(lookup: Any?, pat: StrudelPattern, modulo: Boolean): StrudelPattern {
    if (lookup == null) return silence

    return when (lookup) {
        is List<*> -> {
            @Suppress("UNCHECKED_CAST")
            applyInhabit(lookup as List<Any>, pat, modulo)
        }

        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            applyInhabit(lookup as Map<String, Any>, pat, modulo)
        }

        else -> silence
    }
}

/** inhabit() - Pattern extension */
@StrudelDsl
val StrudelPattern.inhabit by dslPatternExtension { p, args, _ ->
    if (args.isEmpty()) return@dslPatternExtension p
    dispatchInhabit(args[0].value, p, modulo = false)
}

/** inhabit() - Standalone function */
@StrudelDsl
val inhabit by dslFunction { args, _ ->
    if (args.size < 2) return@dslFunction silence
    val lookup = args[0].value
    val pat = listOf(args[1]).toPattern(defaultModifier)
    dispatchInhabit(lookup, pat, modulo = false)
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
    dispatchInhabit(args[0].value, p, modulo = true)
}

/** inhabitmod() - Standalone function */
@StrudelDsl
val inhabitmod by dslFunction { args, _ ->
    if (args.size < 2) return@dslFunction silence
    val lookup = args[0].value
    val pat = listOf(args[1]).toPattern(defaultModifier)
    dispatchInhabit(lookup, pat, modulo = true)
}

/** inhabitmod() - String extension */
@StrudelDsl
val String.inhabitmod by dslStringExtension { p, args, callInfo ->
    p.inhabitmod(args, callInfo)
}

/** alias pickmodSqueeze */
@StrudelDsl
val StrudelPattern.pickmodSqueeze by dslPatternExtension { p, args, callInfo -> p.inhabitmod(args, callInfo) }

/** alias pickmodSqueeze */
@StrudelDsl
val pickmodSqueeze by dslFunction { args, callInfo -> inhabitmod(args, callInfo) }

/** alias pickmodSqueeze */
@StrudelDsl
val String.pickmodSqueeze by dslStringExtension { p, args, callInfo -> p.inhabitmod(args, callInfo) }
