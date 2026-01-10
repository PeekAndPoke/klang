package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.strudel.pattern.AtomicPattern
import io.peekandpoke.klang.strudel.pattern.ControlPattern
import kotlin.jvm.JvmName
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// --- Registry ---

object StrudelRegistry {
    val symbols = mutableMapOf<String, Any>()
    val functions = mutableMapOf<String, (List<Any?>) -> StrudelPattern>()
    val methods = mutableMapOf<String, (StrudelPattern, List<Any?>) -> StrudelPattern>()
}

// --- Type Aliases ---

typealias VoiceDataModifier = VoiceData.(Any?) -> VoiceData
typealias VoiceDataMerger = (source: VoiceData, control: VoiceData) -> VoiceData

// --- High Level Helpers ---

fun voiceModifier(modify: VoiceDataModifier): VoiceDataModifier = modify

/**
 * Creates a DSL function that returns a StrudelPattern.
 */
fun dslFunction(handler: (List<Any?>) -> StrudelPattern) = DslFunctionProvider(handler)

/**
 * Creates a DSL extension method on StrudelPattern that returns a StrudelPattern.
 */
fun dslMethod(handler: (StrudelPattern, List<Any?>) -> StrudelPattern) = DslMethodProvider(handler)

/**
 * Creates a DSL object that is registered in the StrudelRegistry.
 */
fun <T : Any> dslObject(handler: () -> T) = DslObjectProvider(handler)

@JvmName("dslPatternCreatorGeneric")
fun dslPatternCreator(
    modify: VoiceDataModifier,
) = DslPatternCreatorProvider(modify)


@JvmName("dslPatternModifierGeneric")
fun dslPatternModifier(
    modify: VoiceDataModifier,
    combine: VoiceDataMerger,
) = DslPatternModifierProvider(modify, combine)

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

class DslFunctionProvider(
    private val handler: (List<Any?>) -> StrudelPattern,
) {
    operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): ReadOnlyProperty<Any?, DslFunction> {
        val name = prop.name
        val func = DslFunction(handler)

        // Register in the evaluator registry
        StrudelRegistry.functions[name] = { args -> func.invokeUntyped(args) }

        return ReadOnlyProperty { _, _ -> func }
    }
}

class DslFunction(val handler: (List<Any?>) -> StrudelPattern) {
    // Typed for Kotlin usage
    @JvmName("invokeVararg")
    operator fun invoke(vararg args: Any?): StrudelPattern = handler(args.toList())

    @JvmName("invokeArray")
    operator fun invoke(args: Array<Any?>): StrudelPattern = handler(args.toList())

    @JvmName("invokeList")
    operator fun invoke(args: List<Any?>): StrudelPattern = handler(args)

    // Internal usage
    internal fun invokeUntyped(args: List<Any?>): StrudelPattern = handler(args)
}

// --- Generic Method Delegate (fast, slow, etc.) ---

/**
 * Provider that registers the method name and creates bound delegates.
 */
class DslMethodProvider(
    private val handler: (StrudelPattern, List<Any?>) -> StrudelPattern,
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>,
    ): ReadOnlyProperty<StrudelPattern, DslMethod> {
        val name = prop.name

        // Register in the evaluator registry
        StrudelRegistry.methods[name] = { recv, args ->
            handler(recv, args)
        }

        // Return a delegate that creates a BOUND method when accessed
        return ReadOnlyProperty { pattern, _ ->
            DslMethod(pattern, handler)
        }
    }
}


/**
 * A method bound to a specific [pattern] instance.
 * When invoked, it applies the handler to the bound pattern and arguments.
 */
class DslMethod(
    val pattern: StrudelPattern,
    val handler: (StrudelPattern, List<Any?>) -> StrudelPattern,
) {
    operator fun invoke(): StrudelPattern {
        return handler(pattern, emptyList())
    }

    operator fun invoke(block: (StrudelPattern) -> StrudelPattern): StrudelPattern {
        return handler(pattern, listOf(block))
    }

    // Typed invocation for Kotlin usage: p.slow(2)
    // The pattern is already bound, so we only pass args.
    operator fun invoke(vararg args: Any?): StrudelPattern {
        return handler(pattern, args.toList())
    }

    operator fun invoke(vararg block: (StrudelPattern) -> StrudelPattern): StrudelPattern {
        return handler(pattern, block.toList())
    }
}

// --- Creator Delegate (Specialized for MiniNotation) ---

class DslPatternCreatorProvider(
    private val modify: VoiceDataModifier,
) {
    operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): ReadOnlyProperty<Any?, DslPatternCreator> {
        val name = prop.name
        val creator = DslPatternCreator(modify)

        StrudelRegistry.functions[name] = { args ->
            // TODO: pass varargs to creator ... let it decide
            val arg = args.first().toString()
            creator(arg)
        }

        return ReadOnlyProperty { _, _ -> creator }
    }
}

// --- Modifier Delegate (Specialized for VoiceData) ---

class DslPatternModifierProvider(
    private val modify: VoiceDataModifier,
    private val combine: VoiceDataMerger,
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>,
    ): ReadOnlyProperty<StrudelPattern, DslPatternModifier> {
        val name = prop.name

        // Logic for the registered method
        StrudelRegistry.methods[name] = { pattern, args ->
            println("Script called method $name with args: ${args}")

            val modifier = DslPatternModifier(pattern, modify, combine)
            modifier(*args.toTypedArray())
        }

        return ReadOnlyProperty { thisRef, _ ->
            DslPatternModifier(thisRef, modify, combine)
        }
    }
}

// --- Implementations ---

class DslPatternCreator(
    val modify: VoiceDataModifier,
) {
    operator fun invoke(mini: String): StrudelPattern = parseMiniNotation(input = mini) {
        AtomicPattern(VoiceData.empty.modify(it))
    }
}

class DslPatternModifier(
    val pattern: StrudelPattern,
    val modify: VoiceDataModifier,
    val combiner: VoiceDataMerger,
) {
    operator fun invoke(vararg values: Any?): StrudelPattern {
        return when (values.size) {
            // TODO: reinterpret
            0 -> pattern
            1 if (values[0] is StrudelPattern) -> useControl(values[0] as StrudelPattern)
            else -> useControl(
                useMini(
                    values[0]?.toString() ?: ""
                )
            )
        }
    }

    /** Parses mini notation and uses the resulting pattern as a control pattern  */
    private fun useMini(mini: String): StrudelPattern {
        return useControl(
            control = parseMiniNotation(input = mini) {
                AtomicPattern(VoiceData.empty.modify(it))
            }
        )
    }

    /** Uses a control pattern to modify voice events */
    private fun useControl(control: StrudelPattern): StrudelPattern {
        // Logic to map the custom "VoiceData.value" into the correct value
        val mapper: (VoiceData) -> VoiceData = { data ->
            val value = data.value

            if (value != null) {
                data.modify(value)
            } else {
                data
            }
        }

        return pattern.applyControl(
            control = control,
            mapper = mapper,
            combiner = combiner,
        )
    }
}

// --- Generic Object Delegate (sine, saw, etc.) ---

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
