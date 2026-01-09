package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.parser.MiniNotationParser
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

typealias VoiceDataModifier<T> = VoiceData.(T) -> VoiceData
typealias VoiceDataMerger = (source: VoiceData, control: VoiceData) -> VoiceData

// --- High Level Helpers ---

fun <T> voiceModifier(modify: VoiceDataModifier<T>): VoiceDataModifier<T> = modify

/**
 * Creates a DSL function that returns a StrudelPattern.
 * [In] is the type used for varargs in Kotlin (e.g. StrudelPattern or Any).
 */
fun <In> dslFunction(handler: (List<Any?>) -> StrudelPattern) = DslFunctionProvider<In>(handler)

/**
 * Creates a DSL extension method on StrudelPattern that returns a StrudelPattern.
 */
fun dslMethod(handler: (StrudelPattern, List<Any?>) -> StrudelPattern) = DslMethodProvider(handler)

/**
 * Creates a DSL object that is registered in the StrudelRegistry.
 */
fun <T : StrudelPattern> dslObject(handler: () -> T) = DslObjectProvider(handler)

@JvmName("dslPatternCreatorGeneric")
fun <T> dslPatternCreator(
    modify: VoiceDataModifier<T>,
    fromStr: (String) -> T,
) = DslPatternCreatorProvider(modify, fromStr)

@JvmName("dslPatternCreatorString")
fun dslPatternCreator(
    modify: VoiceDataModifier<String>,
) = dslPatternCreator(modify) { it }

@JvmName("dslPatternCreatorNumber")
fun dslPatternCreator(
    modify: VoiceDataModifier<Number>,
) = dslPatternCreator(modify) { (it.toDoubleOrNull() ?: 0.0) as Number }


@JvmName("dslPatternModifierGeneric")
fun <T> dslPatternModifier(
    modify: VoiceDataModifier<T>,
    combine: VoiceDataMerger,
    fromStr: (String) -> T,
    toStr: (T) -> String,
) = DslPatternModifierProvider(modify, combine, fromStr, toStr)

@JvmName("dslPatternModifierString")
fun dslPatternModifier(modify: VoiceDataModifier<String>, combine: VoiceDataMerger) =
    dslPatternModifier(
        modify = modify,
        fromStr = { it },
        toStr = { it },
        combine = combine
    )

@JvmName("dslPatternModifierNumber")
fun dslPatternModifier(modify: VoiceDataModifier<Number>, combine: VoiceDataMerger) =
    dslPatternModifier(
        modify = modify,
        fromStr = { (it.toDoubleOrNull() ?: 0.0) as Number },
        toStr = { it.toString() },
        combine = combine
    )

// --- Generic Function Delegate (stack, arrange, etc.) ---

class DslFunctionProvider<In>(
    private val handler: (List<Any?>) -> StrudelPattern,
) {
    operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): ReadOnlyProperty<Any?, DslFunction<In>> {
        val name = prop.name
        val func = DslFunction<In>(handler)

        // Register in the evaluator registry
        StrudelRegistry.functions[name] = { args -> func.invokeUntyped(args) }

        return ReadOnlyProperty { _, _ -> func }
    }
}

class DslFunction<In>(val handler: (List<Any?>) -> StrudelPattern) {
    // Typed for Kotlin usage
    @JvmName("invokeVararg")
    operator fun invoke(vararg args: In): StrudelPattern = handler(args.toList())

    @JvmName("invokeArray")
    operator fun invoke(args: Array<In>): StrudelPattern = handler(args.toList())

    @JvmName("invokeList")
    operator fun invoke(args: List<In>): StrudelPattern = handler(args)

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
    // Typed invocation for Kotlin usage: p.slow(2)
    // The pattern is already bound, so we only pass args.
    operator fun invoke(vararg args: Any?): StrudelPattern {
        @Suppress("UNCHECKED_CAST")
        return handler(pattern, args.toList())
    }
}

// --- Creator Delegate (Specialized for MiniNotation) ---

class DslPatternCreatorProvider<T>(
    private val modify: VoiceDataModifier<T>,
    private val fromStr: (String) -> T,
) {
    operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): ReadOnlyProperty<Any?, DslPatternCreator<T>> {
        val name = prop.name
        val creator = DslPatternCreator(modify, fromStr)

        StrudelRegistry.functions[name] = { args ->
            val arg = args.first().toString()
            creator(arg)
        }

        return ReadOnlyProperty { _, _ -> creator }
    }
}

// --- Modifier Delegate (Specialized for VoiceData) ---

class DslPatternModifierProvider<T>(
    private val modify: VoiceDataModifier<T>,
    private val combine: VoiceDataMerger,
    private val fromStr: (String) -> T,
    private val toStr: (T) -> String,
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>,
    ): ReadOnlyProperty<StrudelPattern, DslPatternModifier<T>> {
        val name = prop.name

        // Logic for the registered method
        StrudelRegistry.methods[name] = { pattern, args ->
            val modifier = DslPatternModifier(pattern, modify, fromStr, toStr, combine)
            val arg = args.firstOrNull() ?: error("Method $name requires an argument")

            when (arg) {
                is StrudelPattern -> modifier(arg)
                else -> modifier(arg.toString())
            }
        }

        return ReadOnlyProperty { thisRef, _ ->
            DslPatternModifier(thisRef, modify, fromStr, toStr, combine)
        }
    }
}

// --- Implementations ---

class DslPatternCreator<T>(
    val modify: VoiceDataModifier<T>,
    val fromStr: (String) -> T,
) {
    operator fun invoke(mini: String): StrudelPattern = parseMiniNotation(input = mini) {
        AtomicPattern(VoiceData.empty.modify(fromStr(it)))
    }
}

class DslPatternModifier<T>(
    val pattern: StrudelPattern,
    val modify: VoiceDataModifier<T>,
    val fromStr: (String) -> T,
    val toStr: (T) -> String,
    val combiner: VoiceDataMerger,
) {
    /** Parses mini notation and uses the resulting pattern as a control pattern  */
    operator fun invoke(mini: String): StrudelPattern {
        return invoke(
            control = MiniNotationParser(input = mini) {
                AtomicPattern(
                    VoiceData.empty.modify(
                        fromStr(it),
                    )
                )
            }.parse()
        )
    }

    operator fun invoke(vararg values: T): StrudelPattern {
        return invoke(values.joinToString(" ") { toStr(it) })
    }

    /** Uses a control pattern to modify voice events */
    operator fun invoke(control: StrudelPattern): StrudelPattern {
        // Logic to map the custom "VoiceData.value" into the correct value
        val valueMapper: (VoiceData) -> VoiceData = { data ->
            @Suppress("UNCHECKED_CAST")
            val value = data.value as? T

            if (value != null) {
                data.modify(value)
            } else {
                data
            }
        }

        return pattern.applyControl(
            control = control,
            mapper = valueMapper,
            combiner = combiner,
        )
    }

    /**
     * Applies a control pattern to the current pattern.
     * The structure comes from [this] pattern.
     * The values are taken from [control] pattern sampled at each event.
     */
    @StrudelDsl
    private fun StrudelPattern.applyControl(
        control: StrudelPattern,
        mapper: (VoiceData) -> VoiceData,
        combiner: VoiceDataMerger,
    ): StrudelPattern = ControlPattern(
        source = this,
        control = control,
        mapper = mapper,
        combiner = combiner,
    )
}

// --- Generic Object Delegate (sine, saw, etc.) ---

class DslObjectProvider<T : StrudelPattern>(
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
