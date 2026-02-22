package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.script.ast.SourceLocationChain
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.pattern.*
import kotlin.jvm.JvmName
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

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
    val patternMapperFunctions = mutableMapOf<String, StrudelDslTopLevelFn<PatternMapper>>()
    val patternExtensionMethods = mutableMapOf<String, StrudelDslExtFn<StrudelPattern>>()
    val stringExtensionMethods = mutableMapOf<String, StrudelDslExtFn<String>>()
}

data class StrudelDslArg<out T>(
    val value: T,
    val location: SourceLocation?,
) {
    companion object {
        fun <T> of(value: T): StrudelDslArg<T> = StrudelDslArg(value = value, location = null)

        fun List<Any?>.asStrudelDslArgs(callInfo: CallInfo? = null): List<StrudelDslArg<Any?>> {
            return mapIndexed { index, arg ->
                when (arg) {
                    is StrudelDslArg<*> -> arg
                    else -> StrudelDslArg(value = arg, location = callInfo?.paramLocations?.getOrNull(index))
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
fun voiceModifier(modify: VoiceModifier): VoiceModifier = modify

/** Creates a pattern mapper */
fun patternMapper(mapper: Any?): PatternMapper? {
    return when (mapper) {
        // Is it a provider function? ... for example when we receive a function reference back from KlangScript
        is Function0<*> -> {
            patternMapper(mapper())
        }

        // Is it already a mapper function?
        is Function1<*, *> -> {
            { input ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    val result: Any? = (mapper as? PatternMapper)?.invoke(input)

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

/**
 * Creates a top level DSL function that returns a StrudelPattern.
 */
fun dslPatternFunction(handler: StrudelDslTopLevelFn<StrudelPattern>) = DslPatternCreatorFunctionProvider(handler)

/**
 * Create a top level DSL function that returns a PatternMapper.
 */
fun dslPatternMapper(handler: StrudelDslTopLevelFn<PatternMapper>) = DslPatternMapperFunctionProvider(handler)

/**
 * Creates a DSL extension method on StrudelPattern that returns a StrudelPattern.
 */
fun dslPatternExtension(handler: StrudelDslExtFn<StrudelPattern>) = DslPatternExtensionProvider(handler)

/**
 * Creates a DSL extension method on String that returns a StrudelPattern.
 */
fun dslStringExtension(handler: StrudelDslExtFn<StrudelPattern>) = DslStringExtensionProvider(handler)

/**
 * Creates a DSL object that is registered in the StrudelRegistry.
 */
fun <T : Any> dslObject(handler: () -> T) = DslObjectProvider(handler)

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
 * Safely converts a single argument into a [PatternMapper].
 */
fun StrudelDslArg<Any?>?.toPatternMapper(): PatternMapper? = patternMapper(this?.value)

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
fun StrudelDslArg<Any?>.toPattern(modify: VoiceModifier = voiceValueModifier): StrudelPattern =
    listOf(this).toPattern(modify)

/**
 * Converts a list of arguments into a single StrudelPattern.
 * - Single Pattern arg -> returns it.
 * - Single String/Number -> parses to AtomicPattern using [modify].
 * - Multiple args -> returns a SequencePattern of the parsed items.
 */
fun List<StrudelDslArg<Any?>>.toPattern(modify: VoiceModifier = voiceValueModifier): StrudelPattern {
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
    modify: VoiceModifier = voiceValueModifier,
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
    combiner: VoiceMerger,
): StrudelPattern = ControlPattern(
    source = this,
    control = control,
    mapper = mapper,
    combiner = combiner,
)

// --- Generic Function Delegate (stack, arrange, etc.) ---

class DslTopLevelPatternCreatorFunction(val handler: StrudelDslTopLevelFn<StrudelPattern>) {
    operator fun invoke() = invoke(args = emptyList())

    @JvmName("invokeFunction")
    operator fun invoke(block: (Double) -> Double) = invoke(args = listOf(block).asStrudelDslArgs())

    @JvmName("invokeVararg")
    operator fun invoke(vararg args: Any?): StrudelPattern = invoke(args = args.toList().asStrudelDslArgs())

    @JvmName("invokeArray")
    operator fun invoke(args: Array<Any?>): StrudelPattern = invoke(args = args.toList().asStrudelDslArgs())

    @JvmName("invokeList")
    operator fun invoke(args: List<Any?>): StrudelPattern = invoke(args = args.asStrudelDslArgs())

    operator fun invoke(args: List<StrudelDslArg<Any?>>, callInfo: CallInfo? = null): StrudelPattern {
        return handler(args, callInfo)
    }
}

class DslTopLevelPatternMapperFunction(val handler: StrudelDslTopLevelFn<PatternMapper>) {
    /** Returns the actual functional mapper */
    val asMapper: PatternMapper
        get() = { p1: StrudelPattern ->
            p1.apply(handler(emptyList(), null))
        }

    operator fun invoke(): PatternMapper = invoke(args = emptyList())

    @JvmName("invokeFunction")
    operator fun invoke(block: (Double) -> Double): PatternMapper = invoke(args = listOf(block).asStrudelDslArgs())

    @JvmName("invokeVararg")
    operator fun invoke(vararg args: Any?): PatternMapper = invoke(args = args.toList().asStrudelDslArgs())

    @JvmName("invokeArray")
    operator fun invoke(args: Array<Any?>): PatternMapper = invoke(args = args.toList().asStrudelDslArgs())

    @JvmName("invokeList")
    operator fun invoke(args: List<Any?>): PatternMapper = invoke(args = args.asStrudelDslArgs())

    operator fun invoke(args: List<StrudelDslArg<Any?>>, callInfo: CallInfo? = null): PatternMapper {
        return handler(args, callInfo)
    }
}


// --- Generic Method Delegate (fast, slow, etc.) ---

/**
 * A method bound to a specific [pattern] instance.
 * When invoked, it applies the handler to the bound pattern and arguments.
 */
class DslPatternExtensionMethod(
    val pattern: StrudelPattern,
    val handler: StrudelDslExtFn<StrudelPattern>,
) {
    operator fun invoke() = invoke(args = emptyList())

    @JvmName("invokeBlock")
    operator fun invoke(block: PatternMapper) =
        invoke(args = listOf(block).asStrudelDslArgs())

    @JvmName("invokeBlock")
    operator fun invoke(p1: Any, block: PatternMapper) =
        invoke(args = listOf(p1, block).asStrudelDslArgs())

    @JvmName("invokeBlocksVararg")
    operator fun invoke(vararg block: PatternMapper) =
        invoke(args = block.toList().asStrudelDslArgs())

    @JvmName("invokeVararg")
    operator fun invoke(vararg args: Any?): StrudelPattern =
        invoke(args = args.toList().asStrudelDslArgs())

    @JvmName("invokeArray")
    operator fun invoke(args: Array<Any?>): StrudelPattern =
        invoke(args = args.toList().asStrudelDslArgs())

    @JvmName("invokeList")
    operator fun invoke(args: List<Any?>): StrudelPattern =
        invoke(args = args.asStrudelDslArgs())

    operator fun invoke(args: List<StrudelDslArg<Any?>>, callInfo: CallInfo? = null): StrudelPattern =
        handler(pattern, args, callInfo)
}

/**
 * Provider that registers the function name and creates bound delegates.
 */
class DslPatternCreatorFunctionProvider(
    private val handler: StrudelDslTopLevelFn<StrudelPattern>,
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>,
    ): ReadOnlyProperty<Any?, DslTopLevelPatternCreatorFunction> {
        val name = prop.name.trimStart('_')
        val func = DslTopLevelPatternCreatorFunction(handler)

        // Register in the evaluator registry - convert RuntimeValue to Any? for the handler
        StrudelRegistry.patternCreationFunctions[name] = { args, callInfo ->
            func.invoke(args = args, callInfo = callInfo)
        }

        return ReadOnlyProperty { _, _ -> func }
    }
}

/**
 * Provider that registers the function name and creates bound delegates.
 */
class DslPatternMapperFunctionProvider(
    private val handler: StrudelDslTopLevelFn<PatternMapper>,
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>,
    ): ReadOnlyProperty<Any?, DslTopLevelPatternMapperFunction> {
        val name = prop.name.trimStart('_')
        val func = DslTopLevelPatternMapperFunction(handler)

        // Register in the evaluator registry - convert RuntimeValue to Any? for the handler
        StrudelRegistry.patternMapperFunctions[name] = { args, callInfo ->
            func.invoke(args = args, callInfo = callInfo)
        }

        return ReadOnlyProperty { _, _ -> func }
    }
}


/**
 * Provider that registers the method name and creates bound delegates.
 */
class DslPatternExtensionProvider(
    private val handler: StrudelDslExtFn<StrudelPattern>,
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>,
    ): ReadOnlyProperty<StrudelPattern, DslPatternExtensionMethod> {
        val name = prop.name.trimStart('_')

        // Register in the evaluator registry
        StrudelRegistry.patternExtensionMethods[name] = { recv, args, callInfo ->
            handler(recv, args, callInfo)
        }

        // Return a delegate that creates a BOUND method when accessed
        return ReadOnlyProperty { pattern, _ ->
            DslPatternExtensionMethod(pattern, handler)
        }
    }
}

/**
 * Provider that registers the method name and creates bound delegates for Strings.
 */
class DslStringExtensionProvider(
    private val handler: StrudelDslExtFn<StrudelPattern>,
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>,
    ): ReadOnlyProperty<String, DslPatternExtensionMethod> {
        val name = prop.name.trimStart('_')

        // Register in the evaluator registry
        StrudelRegistry.stringExtensionMethods[name] = { recv, args, callInfo ->
            val pattern = parse(str = recv, baseLocation = callInfo?.receiverLocation)
            handler(pattern, args, callInfo)
        }

        // Return a delegate that creates a BOUND method when accessed
        return ReadOnlyProperty { string, _ ->
            val pattern = parse(str = string, baseLocation = null)
            DslPatternExtensionMethod(pattern, handler)
        }
    }

    private fun parse(str: String, baseLocation: SourceLocation?): StrudelPattern {
        return parseMiniNotation(input = str, baseLocation = baseLocation) { text, loc ->
            AtomicPattern(
                data = StrudelVoiceData.empty.voiceValueModifier(text),
                sourceLocations = loc,
            )
        }
    }
}

/**
 * Provider that registers the method name and creates bound delegates.
 */
class DslObjectProvider<T : Any>(
    private val handler: () -> T,
) {
    operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): ReadOnlyProperty<Any?, T> {
        val name = prop.name.trimStart('_')
        val instance = handler()

        // Register in the evaluator registry as a function that returns the instance
        StrudelRegistry.symbols[name] = instance

        return ReadOnlyProperty { _, _ -> instance }
    }
}
