@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.common.SourceLocationChain
import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.SprudelVoiceValue
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.sprudel.pattern.*

// --- Init Property ---

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangHelpersInit = false

// --- Source ID Generation ---

private var sourceIdCounter = 0

/**
 * Generates a unique source ID for tracking which audio source events came from.
 * Uses source location hash for stable IDs across code re-evaluation (live coding).
 * Falls back to counter-based IDs when no source location is available.
 *
 * Used to track solo/mute state per pattern expression (e.g., sound("bd hh")).
 */
internal fun generateSourceId(sourceLocation: SourceLocation? = null): String {

    return if (sourceLocation != null) {
        val hash = "${sourceLocation.startLine}:${sourceLocation.startColumn}".hashCode()
        // Use hash of source location for stable ID across re-evaluation
        "loc_$hash"
    } else {
        // Fallback to counter for patterns without source locations
        "id_${++sourceIdCounter}"
    }
}

// --- Registry ---

object SprudelRegistry {
    val symbols = mutableMapOf<String, Any>()
    val patternCreationFunctions = mutableMapOf<String, SprudelDslTopLevelFn<SprudelPattern>>()
    val patternExtensionMethods = mutableMapOf<String, SprudelDslPatternExtFn<SprudelPattern>>()
    val stringExtensionMethods = mutableMapOf<String, SprudelDslPatternExtFn<String>>()
    val patternMapperFunctions = mutableMapOf<String, SprudelDslTopLevelFn<PatternMapperFn>>()
    val patternMapperExtensionMethods = mutableMapOf<String, SprudelDslPatternMapperExtFn<PatternMapperFn>>()
}

data class SprudelDslArg<out T>(
    val value: T,
    val location: SourceLocation?,
) {
    companion object {
        fun <T> of(value: T): SprudelDslArg<T> = SprudelDslArg(value = value, location = null)

        fun Any?.asSprudelDslArg(location: SourceLocation? = null): SprudelDslArg<Any?> {
            return SprudelDslArg(value = this, location = location)
        }

        fun List<Any?>.asSprudelDslArgs(callInfo: CallInfo? = null): List<SprudelDslArg<Any?>> {
            return mapIndexed { index, arg ->
                when (arg) {
                    is SprudelDslArg<*> -> arg
                    else -> arg.asSprudelDslArg(location = callInfo?.paramLocations?.getOrNull(index))
                }
            }
        }
    }
}

fun <T> SprudelDslArg<T>?.asControlValueProvider(default: SprudelVoiceValue): ControlValueProvider {
    val arg = this
    val argVal = arg?.value ?: return ControlValueProvider.Static(default)
    val argRat = argVal.asRationalOrNull()?.asVoiceValue()

    if (argRat != null) {
        return ControlValueProvider.Static(value = argRat, location = arg.location)
    }

    val pattern = when (argVal) {
        is SprudelPattern -> argVal

        else -> parseMiniNotation(arg) { text, loc ->
            AtomicPattern(
                data = SprudelVoiceData.empty.voiceValueModifier(text),
                sourceLocations = loc
            )
        }
    }

    return ControlValueProvider.Pattern(pattern)
}

/** Default modifier for patterns that populates VoiceData.value */
val voiceValueModifier = voiceModifier {
    val result = (it?.asRationalOrNull() ?: it)?.asVoiceValue()

    copy(value = result)
}

// --- Value Conversion Helpers ---

internal fun Any.asRationalOrNull(): Rational? = when (this) {
    is Rational -> this
    is Number -> Rational(this.toDouble())
    is String -> this.toDoubleOrNull()?.let { Rational(it) }
    is SprudelVoiceValue -> this.asRational
    else -> null
}

/** Safely convert any value to a double or null */
internal fun Any.asDoubleOrNull(): Double? = when (this) {
    is Rational -> this.toDouble()
    is Number -> this.toDouble()
    is String -> this.toDoubleOrNull()
    is SprudelVoiceValue -> this.asDouble
    else -> null
}

/** Safely convert any value to an int or null */
internal fun Any.asIntOrNull(): Int? = when (this) {
    is Rational -> this.toInt()
    is Number -> this.toInt()
    is String -> this.toDoubleOrNull()?.toInt()
    is SprudelVoiceValue -> this.asInt
    else -> null
}

/** Safely convert any value to a long or null */

// --- High Level Helpers ---

/** Creates a voice modifier */
fun voiceModifier(modify: VoiceModifierFn): VoiceModifierFn = modify

/** Chains two pattern mappers together, returning a new mapper */
fun PatternMapperFn.chain(next: PatternMapperFn?): PatternMapperFn {
    if (next == null) return this

    return { input ->
        try {
            val result = this.invoke(input)
            next.invoke(result)
        } catch (e: Exception) {
            println("Error while chaining pattern mappers: $this -> $next: \n${e.stackTraceToString()}")
            input
        }
    }
}

/** Converts a dsl arg into a pattern mapper, and chains it with the current mapper */
fun PatternMapperFn.chain(arg: SprudelDslArg<Any?>?): PatternMapperFn = chain(arg.toPatternMapper())

/** Creates a pattern mapper */
fun patternMapper(mapper: Any?): PatternMapperFn? {
    return when (mapper) {
        // If we have a pattern we simple return it
        is SprudelPattern -> { _ -> mapper }

        // Is it already a mapper function?
        is Function1<*, *> -> {
            { input ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    val result: Any? = (mapper as? PatternMapperFn)?.invoke(input)

                    (result as? SprudelPattern) ?: input
                } catch (e: Exception) {
                    println("Error while invoking pattern mapper: $mapper: \n${e.stackTraceToString()}")
                    input
                }
            }
        }

        else -> null
    }
}

fun List<SprudelDslArg<Any?>>.parseWeightedArgs(): List<Pair<Double, SprudelPattern>> {
    val args = this

    return args.mapNotNull { arg ->
        when (val argVal = arg.value) {
            // Case: pattern (defaults to 1 cycle)
            is SprudelPattern -> 1.0 to argVal

            // Case: [duration, pattern] or [pattern]
            is List<*> -> {
                if (argVal.isEmpty()) return@mapNotNull null

                var dur = 1.0
                var patVal: Any?

                // Check for [number, pattern] format
                if (argVal.size >= 2 && argVal[0] is Number) {
                    dur = (argVal[0] as Number).toDouble()
                    patVal = argVal[1]
                } else {
                    // Fallback to [pattern] (defaults to duration 1.0)
                    patVal = argVal[0]
                }

                // Filter out non-positive durations
                if (dur <= 0.0) return@mapNotNull null

                val pat = when (patVal) {
                    is SprudelPattern -> patVal
                    else -> parseMiniNotation(patVal.toString()) { text, _ ->
                        AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
                    }
                }

                dur to pat
            }
            // Unknown type
            else -> null
        }
    }
}

/**
 * Safely converts a single argument into a [PatternMapperFn].
 */
fun SprudelDslArg<Any?>?.toPatternMapper(): PatternMapperFn? = patternMapper(this?.value)

/**
 * Extracts choice arguments for choose* functions.
 * If args is a single List, unwraps it. Otherwise returns args as-is.
 */
fun List<SprudelDslArg<Any?>>.extractChoiceArgs(): List<SprudelDslArg<Any?>> {
    return if (size == 1 && get(0).value is List<*>) {
        @Suppress("UNCHECKED_CAST")
        (get(0).value as List<Any?>).asSprudelDslArgs()
    } else {
        this
    }
}

/**
 * Extracts weighted pairs from arguments for weighted choice functions.
 * Expects arguments in the format: [[item1, weight1], [item2, weight2], ...]
 * Returns a pair of (items, weights) lists.
 */
fun List<SprudelDslArg<Any?>>.extractWeightedPairs(): Pair<List<SprudelDslArg<Any?>>, List<SprudelDslArg<Any?>>> {
    val items = mutableListOf<SprudelDslArg<Any?>>()
    val weights = mutableListOf<SprudelDslArg<Any?>>()

    val inputs = if (
        size == 1 && get(0).value is List<*> && (get(0).value as List<*>).all { it is List<*> }
    ) {
        @Suppress("UNCHECKED_CAST")
        (get(0).value as List<Any?>).asSprudelDslArgs()
    } else {
        this
    }

    inputs.forEach { item ->
        if (item.value is List<*> && item.value.size >= 2) {
            val list = item.value
            items.add(SprudelDslArg(list[0], null))
            weights.add(SprudelDslArg(list[1], null))
        }
    }
    return items to weights
}

/**
 * Converts a single argument into a SprudelPattern.
 */
fun SprudelDslArg<Any?>.toPattern(modify: VoiceModifierFn = voiceValueModifier): SprudelPattern =
    listOf(this).toPattern(modify)

/**
 * Converts a list of arguments into a single SprudelPattern.
 * - Single Pattern arg -> returns it.
 * - Single String/Number -> parses to AtomicPattern using [modify].
 * - Multiple args -> returns a SequencePattern of the parsed items.
 */
fun List<SprudelDslArg<Any?>>.toPattern(modify: VoiceModifierFn = voiceValueModifier): SprudelPattern {
    val patterns = this.toListOfPatterns(modify)

    return when {
        patterns.isEmpty() -> EmptyPattern
        patterns.size == 1 -> patterns.first()
        else -> SequencePattern(patterns)
    }
}

/**
 * Recursively flattens arguments into a list of SprudelPatterns.
 */
internal fun List<SprudelDslArg<Any?>>.toListOfPatterns(
    modify: VoiceModifierFn = voiceValueModifier,
): List<SprudelPattern> {
    // Generate a unique source ID for all patterns created from this call
    // This ensures all events from expressions like sound("bd hh") share the same source ID
    // Use first available source location for stable IDs across code re-evaluation
    val firstLocation = this.firstOrNull()?.location
    val sourceId = generateSourceId(firstLocation)

    val atomFactory = { text: Any?, sourceLocations: SourceLocationChain? ->
        AtomicPattern(
            data = SprudelVoiceData.empty.modify(text).copy(patternId = sourceId),
            sourceLocations = sourceLocations,
        )
    }

    return this.mapNotNull { dslArg ->
        val loc = dslArg.location
        val locChain = loc?.asChain()

        when (val arg = dslArg.value) {
            is SprudelPattern -> arg

            is SprudelPatternEvent -> AtomicPattern(arg.data, locChain)

            // -- Plain values from Kotlin DSL - no location information -----------------------------------------------
            is String -> parseMiniNotation(input = arg, baseLocation = loc, atomFactory = atomFactory)

            is Rational -> atomFactory(arg, locChain)

            is Number -> atomFactory(arg, locChain)

            is Boolean -> atomFactory(arg, locChain)

            is List<*> -> {
                val innerPatterns = arg.map {
                    when (it) {
                        is SprudelDslArg<*> -> it
                        else -> SprudelDslArg(value = it, location = loc)
                    }
                }.toListOfPatterns(modify)

                SequencePattern.create(innerPatterns)
            }

            // -- empty or null ----------------------------------------------------------------------------------------
            null -> null
            else -> null
        }
    }
}

/**
 * Applies a control pattern to the current pattern.
 * The structure comes from [this] pattern.
 * The values are taken from [control] pattern sampled at each event.
 */
fun SprudelPattern.applyControl(
    control: SprudelPattern,
    mapper: (SprudelVoiceData) -> SprudelVoiceData,
    combiner: VoiceMergerFn,
): SprudelPattern = ControlPattern(
    source = this,
    control = control,
    mapper = mapper,
    combiner = combiner,
)
