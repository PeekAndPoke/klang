package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.strudel.pattern.AtomicPattern
import io.peekandpoke.klang.strudel.pattern.ControlPattern
import io.peekandpoke.klang.strudel.pattern.EmptyPattern
import io.peekandpoke.klang.strudel.pattern.SequencePattern
import kotlin.jvm.JvmName
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// --- Registry ---

object StrudelRegistry {
    val symbols = mutableMapOf<String, Any>()
    val functions = mutableMapOf<String, (List<Any?>) -> StrudelPattern>()
    val patternExtensionMethods = mutableMapOf<String, (StrudelPattern, List<Any?>) -> StrudelPattern>()
    val stringExtensionMethods = mutableMapOf<String, (String, List<Any?>) -> StrudelPattern>()
}

// --- Type Aliases ---

typealias VoiceDataModifier = VoiceData.(Any?) -> VoiceData
typealias VoiceDataMerger = (source: VoiceData, control: VoiceData) -> VoiceData

// --- High Level Helpers ---

/** Creates a voice modifier */
fun voiceModifier(modify: VoiceDataModifier): VoiceDataModifier = modify

/**
 * Creates a DSL function that returns a StrudelPattern.
 */
fun dslFunction(handler: (List<Any?>) -> StrudelPattern) =
    DslFunctionProvider(handler)

/**
 * Creates a DSL extension method on StrudelPattern that returns a StrudelPattern.
 */
fun dslPatternExtension(handler: (StrudelPattern, List<Any?>) -> StrudelPattern) =
    DslPatternExtensionProvider(handler)

/**
 * Creates a DSL extension method on String that returns a StrudelPattern.
 */
fun dslStringExtension(handler: (StrudelPattern, List<Any?>) -> StrudelPattern) =
    DslStringExtensionProvider(handler)

/**
 * Creates a DSL object that is registered in the StrudelRegistry.
 */
fun <T : Any> dslObject(handler: () -> T) = DslObjectProvider(handler)

// --- Argument to Pattern Helpers ---

/**
 * Applies a modification to the pattern using the provided arguments.
 * Arguments are interpreted as a control pattern.
 */
fun StrudelPattern.applyParam(
    args: List<Any?>,
    modify: VoiceDataModifier,
    combine: VoiceDataMerger,
): StrudelPattern {
    if (args.isEmpty()) return this

    val control = args.toPattern(modify)

    val mapper: (VoiceData) -> VoiceData = { data ->
        val value = data.value
        if (value != null) data.modify(value) else data
    }

    return this.applyControl(control, mapper, combine)
}

/**
 * Converts a list of arguments into a single StrudelPattern.
 * - Single Pattern arg -> returns it.
 * - Single String/Number -> parses to AtomicPattern using [modify].
 * - Multiple args -> returns a SequencePattern of the parsed items.
 */
fun List<Any?>.toPattern(modify: VoiceDataModifier): StrudelPattern {
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
internal fun List<Any?>.toListOfPatterns(
    modify: VoiceDataModifier,
): List<StrudelPattern> {
    val atomFactory = { it: String ->
        AtomicPattern(VoiceData.empty.modify(it))
    }

    return this.flatMap { arg ->
        when (arg) {
            is String -> listOf(parseMiniNotation(arg, atomFactory))
            is Number -> listOf(atomFactory(arg.toString()))
            is StrudelPattern -> listOf(arg)
            is List<*> -> arg.toListOfPatterns(modify)
            else -> emptyList()
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
    mapper: (VoiceData) -> VoiceData,
    combiner: VoiceDataMerger,
): StrudelPattern = ControlPattern(
    source = this,
    control = control,
    mapper = mapper,
    combiner = combiner,
)

// --- Generic Function Delegate (stack, arrange, etc.) ---

class DslFunction(val handler: (List<Any?>) -> StrudelPattern) {
    operator fun invoke() = handler(emptyList())

    @JvmName("invokeBlock")
    operator fun invoke(block: (StrudelPattern) -> StrudelPattern) = handler(listOf(block))

    @JvmName("invokeBlocksVararg")
    operator fun invoke(vararg block: (StrudelPattern) -> StrudelPattern) = handler(block.toList())

    @JvmName("invokeVararg")
    operator fun invoke(vararg args: Any?): StrudelPattern = handler(args.toList())

    @JvmName("invokeArray")
    operator fun invoke(args: Array<Any?>): StrudelPattern = handler(args.toList())

    @JvmName("invokeList")
    operator fun invoke(args: List<Any?>): StrudelPattern = handler(args)
}

// --- Generic Method Delegate (fast, slow, etc.) ---


/**
 * A method bound to a specific [pattern] instance.
 * When invoked, it applies the handler to the bound pattern and arguments.
 */
class DslPatternMethod(
    val pattern: StrudelPattern,
    val handler: (StrudelPattern, List<Any?>) -> StrudelPattern,
) {
    operator fun invoke() = handler(pattern, emptyList())

    @JvmName("invokeBlock")
    operator fun invoke(block: (StrudelPattern) -> StrudelPattern) = handler(pattern, listOf(block))

    @JvmName("invokeBlocksVararg")
    operator fun invoke(vararg block: (StrudelPattern) -> StrudelPattern) = handler(pattern, block.toList())

    @JvmName("invokeVararg")
    operator fun invoke(vararg args: Any?): StrudelPattern = handler(pattern, args.toList())

    @JvmName("invokeArray")
    operator fun invoke(args: Array<Any?>): StrudelPattern = handler(pattern, args.toList())

    @JvmName("invokeList")
    operator fun invoke(args: List<Any?>): StrudelPattern = handler(pattern, args)
}

/**
 * Provider that registers the function name and creates bound delegates.
 */
class DslFunctionProvider(
    private val handler: (List<Any?>) -> StrudelPattern,
) {
    operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): ReadOnlyProperty<Any?, DslFunction> {
        val name = prop.name
        val func = DslFunction(handler)

        // Register in the evaluator registry
        StrudelRegistry.functions[name] = { args -> func.invoke(args) }

        return ReadOnlyProperty { _, _ -> func }
    }
}

/**
 * Provider that registers the method name and creates bound delegates.
 */
class DslPatternExtensionProvider(
    private val handler: (StrudelPattern, List<Any?>) -> StrudelPattern,
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>,
    ): ReadOnlyProperty<StrudelPattern, DslPatternMethod> {
        val name = prop.name

        // Register in the evaluator registry
        StrudelRegistry.patternExtensionMethods[name] = { recv, args ->
            handler(recv, args)
        }

        // Return a delegate that creates a BOUND method when accessed
        return ReadOnlyProperty { pattern, _ ->
            DslPatternMethod(pattern, handler)
        }
    }
}

/**
 * Provider that registers the method name and creates bound delegates for Strings.
 */
class DslStringExtensionProvider(
    private val handler: (StrudelPattern, List<Any?>) -> StrudelPattern,
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>,
    ): ReadOnlyProperty<String, DslPatternMethod> {
        val name = prop.name

        // Register in the evaluator registry
        StrudelRegistry.stringExtensionMethods[name] = { recv, args ->
            val pattern = parse(recv)
            handler(pattern, args)
        }

        // Return a delegate that creates a BOUND method when accessed
        return ReadOnlyProperty { string, _ ->
            val pattern = parse(string)
            DslPatternMethod(pattern, handler)
        }
    }

    private fun parse(str: String): StrudelPattern {
        return parseMiniNotation(str) {
            AtomicPattern(
                VoiceData.empty.defaultModifier(it)
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
        val name = prop.name
        val instance = handler()

        // Register in the evaluator registry as a function that returns the instance
        StrudelRegistry.symbols[name] = instance

        return ReadOnlyProperty { _, _ -> instance }
    }
}
