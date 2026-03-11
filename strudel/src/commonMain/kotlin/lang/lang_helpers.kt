@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.script.ast.SourceLocationChain
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.pattern.*

// --- Init Property ---

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangHelpersInit = false

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

object StrudelRegistry {
    val symbols = mutableMapOf<String, Any>()
    val patternCreationFunctions = mutableMapOf<String, StrudelDslTopLevelFn<StrudelPattern>>()
    val patternExtensionMethods = mutableMapOf<String, StrudelDslPatternExtFn<StrudelPattern>>()
    val stringExtensionMethods = mutableMapOf<String, StrudelDslPatternExtFn<String>>()
    val patternMapperFunctions = mutableMapOf<String, StrudelDslTopLevelFn<PatternMapperFn>>()
    val patternMapperExtensionMethods = mutableMapOf<String, StrudelDslPatternMapperExtFn<PatternMapperFn>>()
}

data class StrudelDslArg<out T>(
    val value: T,
    val location: SourceLocation?,
) {
    companion object {
        fun <T> of(value: T): StrudelDslArg<T> = StrudelDslArg(value = value, location = null)

        fun Any?.asStrudelDslArg(location: SourceLocation? = null): StrudelDslArg<Any?> {
            return StrudelDslArg(value = this, location = location)
        }

        fun List<Any?>.asStrudelDslArgs(callInfo: CallInfo? = null): List<StrudelDslArg<Any?>> {
            return mapIndexed { index, arg ->
                when (arg) {
                    is StrudelDslArg<*> -> arg
                    else -> arg.asStrudelDslArg(location = callInfo?.paramLocations?.getOrNull(index))
                }
            }
        }
    }
}

fun <T> StrudelDslArg<T>?.asControlValueProvider(default: StrudelVoiceValue): ControlValueProvider {
    val arg = this
    val argVal = arg?.value ?: return ControlValueProvider.Static(default)
    val argRat = argVal.asRationalOrNull()?.asVoiceValue()

    if (argRat != null) {
        return ControlValueProvider.Static(value = argRat, location = arg.location)
    }

    val pattern = when (argVal) {
        is StrudelPattern -> argVal

        else -> parseMiniNotation(arg) { text, loc ->
            AtomicPattern(
                data = StrudelVoiceData.empty.voiceValueModifier(text),
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
    is StrudelVoiceValue -> this.asRational
    else -> null
}

/** Safely convert any value to a double or null */
internal fun Any.asDoubleOrNull(): Double? = when (this) {
    is Rational -> this.toDouble()
    is Number -> this.toDouble()
    is String -> this.toDoubleOrNull()
    is StrudelVoiceValue -> this.asDouble
    else -> null
}

/** Safely convert any value to an int or null */
internal fun Any.asIntOrNull(): Int? = when (this) {
    is Rational -> this.toInt()
    is Number -> this.toInt()
    is String -> this.toDoubleOrNull()?.toInt()
    is StrudelVoiceValue -> this.asInt
    else -> null
}

/** Safely convert any value to a long or null */
internal fun Any.asLongOrNull(): Long? = when (this) {
    is Rational -> this.toLong()
    is Number -> this.toLong()
    is String -> this.toDoubleOrNull()?.toLong()
    is StrudelVoiceValue -> this.asInt?.toLong()
    else -> null
}

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
fun PatternMapperFn.chain(arg: StrudelDslArg<Any?>?): PatternMapperFn = chain(arg.toPatternMapper())

/** Creates a pattern mapper */
fun patternMapper(mapper: Any?): PatternMapperFn? {
    return when (mapper) {
        // If we have a pattern we simple return it
        is StrudelPattern -> { _ -> mapper }

        // Is it already a mapper function?
        is Function1<*, *> -> {
            { input ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    val result: Any? = (mapper as? PatternMapperFn)?.invoke(input)

                    (result as? StrudelPattern) ?: input
                } catch (e: Exception) {
                    println("Error while invoking pattern mapper: $mapper: \n${e.stackTraceToString()}")
                    input
                }
            }
        }

        else -> null
    }
}


fun List<StrudelDslArg<Any?>>.parseWeightedArgs(): List<Pair<Double, StrudelPattern>> {
    val args = this

    return args.mapNotNull { arg ->
        when (val argVal = arg.value) {
            // Case: pattern (defaults to 1 cycle)
            is StrudelPattern -> 1.0 to argVal

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
                    is StrudelPattern -> patVal
                    else -> parseMiniNotation(patVal.toString()) { text, _ ->
                        AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
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
fun StrudelDslArg<Any?>?.toPatternMapper(): PatternMapperFn? = patternMapper(this?.value)

/**
 * Extracts choice arguments for choose* functions.
 * If args is a single List, unwraps it. Otherwise returns args as-is.
 */
fun List<StrudelDslArg<Any?>>.extractChoiceArgs(): List<StrudelDslArg<Any?>> {
    return if (size == 1 && get(0).value is List<*>) {
        @Suppress("UNCHECKED_CAST")
        (get(0).value as List<Any?>).asStrudelDslArgs()
    } else {
        this
    }
}

/**
 * Extracts weighted pairs from arguments for weighted choice functions.
 * Expects arguments in the format: [[item1, weight1], [item2, weight2], ...]
 * Returns a pair of (items, weights) lists.
 */
fun List<StrudelDslArg<Any?>>.extractWeightedPairs(): Pair<List<StrudelDslArg<Any?>>, List<StrudelDslArg<Any?>>> {
    val items = mutableListOf<StrudelDslArg<Any?>>()
    val weights = mutableListOf<StrudelDslArg<Any?>>()

    val inputs = if (
        size == 1 && get(0).value is List<*> && (get(0).value as List<*>).all { it is List<*> }
    ) {
        @Suppress("UNCHECKED_CAST")
        (get(0).value as List<Any?>).asStrudelDslArgs()
    } else {
        this
    }

    inputs.forEach { item ->
        if (item.value is List<*> && item.value.size >= 2) {
            val list = item.value
            items.add(StrudelDslArg(list[0], null))
            weights.add(StrudelDslArg(list[1], null))
        }
    }
    return items to weights
}

/**
 * Converts a single argument into a StrudelPattern.
 */
fun StrudelDslArg<Any?>.toPattern(modify: VoiceModifierFn = voiceValueModifier): StrudelPattern =
    listOf(this).toPattern(modify)

/**
 * Converts a list of arguments into a single StrudelPattern.
 * - Single Pattern arg -> returns it.
 * - Single String/Number -> parses to AtomicPattern using [modify].
 * - Multiple args -> returns a SequencePattern of the parsed items.
 */
fun List<StrudelDslArg<Any?>>.toPattern(modify: VoiceModifierFn = voiceValueModifier): StrudelPattern {
    val patterns = this.toListOfPatterns(modify)

    return when {
        patterns.isEmpty() -> EmptyPattern
        patterns.size == 1 -> patterns.first()
        else -> SequencePattern(patterns)
    }
}

/**
 * Recursively flattens arguments into a list of StrudelPatterns.
 */
internal fun List<StrudelDslArg<Any?>>.toListOfPatterns(
    modify: VoiceModifierFn = voiceValueModifier,
): List<StrudelPattern> {
    // Generate a unique source ID for all patterns created from this call
    // This ensures all events from expressions like sound("bd hh") share the same source ID
    // Use first available source location for stable IDs across code re-evaluation
    val firstLocation = this.firstOrNull()?.location
    val sourceId = generateSourceId(firstLocation)

    val atomFactory = { text: Any?, sourceLocations: SourceLocationChain? ->
        AtomicPattern(
            data = StrudelVoiceData.empty.modify(text).copy(patternId = sourceId),
            sourceLocations = sourceLocations,
        )
    }

    return this.mapNotNull { dslArg ->
        val loc = dslArg.location
        val locChain = loc?.asChain()

        when (val arg = dslArg.value) {
            is StrudelPattern -> arg

            is StrudelPatternEvent -> AtomicPattern(arg.data, locChain)

            // -- Plain values from Kotlin DSL - no location information -----------------------------------------------
            is String -> parseMiniNotation(input = arg, baseLocation = loc, atomFactory = atomFactory)

            is Rational -> atomFactory(arg, locChain)

            is Number -> atomFactory(arg, locChain)

            is Boolean -> atomFactory(arg, locChain)

            is List<*> -> {
                val innerPatterns = arg.map {
                    when (it) {
                        is StrudelDslArg<*> -> it
                        else -> StrudelDslArg(value = it, location = loc)
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
fun StrudelPattern.applyControl(
    control: StrudelPattern,
    mapper: (StrudelVoiceData) -> StrudelVoiceData,
    combiner: VoiceMergerFn,
): StrudelPattern = ControlPattern(
    source = this,
    control = control,
    mapper = mapper,
    combiner = combiner,
)
