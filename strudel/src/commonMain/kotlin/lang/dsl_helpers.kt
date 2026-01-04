package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.parser.MiniNotationParser
import kotlin.jvm.JvmName
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// --- Registry ---

object StrudelRegistry {
    val functions = mutableMapOf<String, (List<Any>) -> Any>()
    val methods = mutableMapOf<String, (Any, List<Any>) -> Any>()
}

// --- Type Aliases ---

typealias VoiceDataModifier<T> = VoiceData.(T) -> VoiceData
typealias VoiceDataMerger = (source: VoiceData, control: VoiceData) -> VoiceData

fun <T> voiceModifier(modify: VoiceDataModifier<T>): VoiceDataModifier<T> = modify

// --- Generic Function Delegate (stack, arrange, etc.) ---

class DslFunctionProvider<In>(
    private val handler: (List<Any>) -> StrudelPattern,
) {
    operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): ReadOnlyProperty<Any?, DslFunction<In>> {
        val name = prop.name
        val func = DslFunction<In>(handler)

        // Register in the evaluator registry
        StrudelRegistry.functions[name] = { args -> func.invokeUntyped(args) }

        return ReadOnlyProperty { _, _ -> func }
    }
}

/**
 * Creates a DSL function that returns a StrudelPattern.
 * [In] is the type used for varargs in Kotlin (e.g. StrudelPattern or Any).
 */
fun <In> dslFunction(handler: (List<Any>) -> StrudelPattern) = DslFunctionProvider<In>(handler)

class DslFunction<In>(val handler: (List<Any>) -> StrudelPattern) {
    // Typed for Kotlin usage
    @Suppress("UNCHECKED_CAST")
    operator fun invoke(vararg args: In): StrudelPattern = handler(args.toList() as List<Any>)

    // Internal usage
    fun invokeUntyped(args: List<Any>): StrudelPattern = handler(args)
}

// --- Generic Method Delegate (fast, slow, etc.) ---

/**
 * Provider that registers the method name and creates bound delegates.
 */
class DslMethodProvider<In>(
    private val handler: (StrudelPattern, List<Any>) -> StrudelPattern,
) {
    operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): ReadOnlyProperty<StrudelPattern, DslMethod<In>> {
        val name = prop.name

        // Register in the evaluator registry
        StrudelRegistry.methods[name] = { recv, args ->
            handler(recv as StrudelPattern, args)
        }

        // Return a delegate that creates a BOUND method when accessed
        return ReadOnlyProperty { pattern, _ ->
            DslMethod(pattern, handler)
        }
    }
}

fun <In> dslMethod(handler: (StrudelPattern, List<Any>) -> StrudelPattern) = DslMethodProvider<In>(handler)

/**
 * A method bound to a specific [pattern] instance.
 * When invoked, it applies the handler to the bound pattern and arguments.
 */
class DslMethod<In>(
    val pattern: StrudelPattern,
    val handler: (StrudelPattern, List<Any>) -> StrudelPattern,
) {
    // Typed invocation for Kotlin usage: p.slow(2)
    // The pattern is already bound, so we only pass args.
    operator fun invoke(vararg args: In): StrudelPattern {
        @Suppress("UNCHECKED_CAST")
        return handler(pattern, args.toList() as List<Any>)
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
        StrudelRegistry.methods[name] = { receiver, args ->
            val p = receiver as StrudelPattern
            val modifier = DslPatternModifier(p, modify, fromStr, toStr, combine)
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

// --- Implementations ---

class DslPatternCreator<T>(
    val modify: VoiceDataModifier<T>,
    val fromStr: (String) -> T,
) {
    operator fun invoke(mini: String): StrudelPattern =
        MiniNotationParser(input = mini) {
            AtomicPattern(
                VoiceData.empty.modify(
                    fromStr(it),
                )
            )
        }.parse()
}

class DslPatternModifier<T>(
    val pattern: StrudelPattern,
    val modify: VoiceDataModifier<T>,
    val fromStr: (String) -> T,
    val toStr: (T) -> String,
    val combine: VoiceDataMerger,
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
        return pattern.applyControl(control, combine)
    }
}

/**
 * Applies a control pattern to the current pattern.
 * The structure comes from [this] pattern.
 * The values are taken from [control] pattern sampled at each event.
 */
@StrudelDsl
private fun StrudelPattern.applyControl(
    control: StrudelPattern,
    combiner: VoiceDataMerger,
): StrudelPattern = ControlPattern(this, control, combiner)
