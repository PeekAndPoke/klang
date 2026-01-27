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
import io.peekandpoke.klang.strudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
import kotlin.jvm.JvmName
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// --- Init Property ---

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangHelpersInit = false

// --- Type Aliases ---

typealias VoiceDataModifier = StrudelVoiceData.(Any?) -> StrudelVoiceData

typealias VoiceDataMerger = (source: StrudelVoiceData, control: StrudelVoiceData) -> StrudelVoiceData

typealias StrudelDslFn = (args: List<StrudelDslArg<Any?>>, callInfo: CallInfo?) -> StrudelPattern

typealias StrudelDslExtFn<R> = (recv: R, args: List<StrudelDslArg<Any?>>, callInfo: CallInfo?) -> StrudelPattern

// --- Registry ---

object StrudelRegistry {
    val symbols = mutableMapOf<String, Any>()
    val functions = mutableMapOf<String, StrudelDslFn>()
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

    val argDbl = argVal.asDoubleOrNull()

    if (argDbl != null) {
        return ControlValueProvider.Static(
            value = StrudelVoiceValue.Num(argDbl),
            location = arg?.location
        )
    }

    if (argVal is Rational) {
        return ControlValueProvider.Static(
            value = StrudelVoiceValue.Num(argVal.toDouble()),
            location = arg?.location
        )
    }

    val pattern = when (argVal) {
        is StrudelPattern -> argVal

        else -> parseMiniNotation(arg) { text, loc ->
            AtomicPattern(
                data = StrudelVoiceData.empty.defaultModifier(text),
                sourceLocations = loc
            )
        }
    }

    return ControlValueProvider.Pattern(pattern)
}

/** Default modifier for patterns that populates VoiceData.value */
val defaultModifier: VoiceDataModifier = {
    copy(value = it?.asVoiceValue())
}

// --- Value Conversion Helpers ---

/** Safely convert any value to a double or null */
internal fun Any.asDoubleOrNull(): Double? = when (this) {
    is Number -> this.toDouble()
    is String -> this.toDoubleOrNull()
    else -> null
}

/** Safely convert any value to an int or null */
internal fun Any.asIntOrNull(): Int? = when (this) {
    is Number -> this.toInt()
    is String -> this.toDoubleOrNull()?.toInt()
    else -> null
}

/** Safely convert any value to a long or null */
internal fun Any.asLongOrNull(): Long? = when (this) {
    is Number -> this.toLong()
    is String -> this.toDoubleOrNull()?.toLong()
    else -> null
}

// --- High Level Helpers ---

/** Creates a voice modifier */
fun voiceModifier(modify: VoiceDataModifier): VoiceDataModifier = modify

/**
 * Creates a DSL function that returns a StrudelPattern.
 */
fun dslFunction(handler: StrudelDslFn) = DslFunctionProvider(handler)

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

// --- Argument to Pattern Helpers ---

/**
 * Applies a modification to the pattern using the provided arguments.
 * Arguments are interpreted as a control pattern.
 */
fun StrudelPattern.applyControlFromParams(
    args: List<StrudelDslArg<Any?>>,
    modify: VoiceDataModifier,
    combine: VoiceDataMerger,
): StrudelPattern {
    if (args.isEmpty()) return this

    val control = args.toPattern(modify)

    val mapper: (StrudelVoiceData) -> StrudelVoiceData = { data ->
        val value = data.value
        if (value != null) data.modify(value) else data
    }

    return this.applyControl(control, mapper, combine)
}

/**
 * Specifically for numerical parameters where the control pattern might be a continuous pattern.
 * It checks both the specific field and the generic 'value' field.
 *
 * @param args The arguments passed to the function (e.g. pan("0.5") or pan(sine)).
 * @param modify The modifier to create a StrudelVoiceData from a single argument (string/number).
 * @param getValue Function to extract the specific Double value from the control StrudelVoiceData.
 * @param setValue Function to apply the Double value to the source StrudelVoiceData.
 *                 The first param is the Double value.
 *                 The second param is the full control StrudelVoiceData (useful for merging extra fields like resonance).
 */
fun StrudelPattern.applyNumericalParam(
    args: List<StrudelDslArg<Any?>>,
    modify: VoiceDataModifier,
    getValue: StrudelVoiceData.() -> Double?,
    setValue: StrudelVoiceData.(value: Double, control: StrudelVoiceData) -> StrudelVoiceData,
): StrudelPattern {
    if (args.isEmpty()) return this

    val control = args.toPattern(modify)

    return this.applyControl(
        control = control,
        mapper = { it },
        combiner = { src, ctrl ->
            val num = ctrl.getValue() ?: ctrl.value?.asDouble
            if (num != null) src.setValue(num, ctrl) else src
        }
    )
}

/**
 * Converts a list of arguments into a single StrudelPattern.
 * - Single Pattern arg -> returns it.
 * - Single String/Number -> parses to AtomicPattern using [modify].
 * - Multiple args -> returns a SequencePattern of the parsed items.
 */
fun List<StrudelDslArg<Any?>>.toPattern(modify: VoiceDataModifier): StrudelPattern {
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
    modify: VoiceDataModifier,
): List<StrudelPattern> {
    val atomFactory = { text: String, sourceLocations: SourceLocationChain? ->
        AtomicPattern(
            data = StrudelVoiceData.empty.modify(text),
            sourceLocations = sourceLocations,
        )
    }

    return this.flatMap { dslArg ->
        val loc = dslArg.location

        when (val arg = dslArg.value) {
            // -- Plain values from Kotlin DSL - no location information -----------------------------------------------
            is String -> listOf(
                parseMiniNotation(input = arg, baseLocation = loc, atomFactory = atomFactory)
            )

            is Number -> listOf(atomFactory(arg.toString(), loc?.asChain()))

            is Boolean -> listOf(atomFactory(arg.toString(), loc?.asChain()))

            is StrudelPattern -> listOf(arg)

            is List<*> -> arg.map {
                when (it) {
                    is StrudelDslArg<*> -> it
                    else -> StrudelDslArg(value = it, location = loc)
                }
            }.toListOfPatterns(modify)

            // -- empty or null ----------------------------------------------------------------------------------------
            null -> emptyList()
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
    mapper: (StrudelVoiceData) -> StrudelVoiceData,
    combiner: VoiceDataMerger,
): StrudelPattern = ControlPattern(
    source = this,
    control = control,
    mapper = mapper,
    combiner = combiner,
)

// --- Pattern Operation Helpers ---

/**
 * Helper for applying binary operations to patterns.
 */
internal fun applyBinaryOp(
    source: StrudelPattern,
    args: List<StrudelDslArg<Any?>>,
    op: (StrudelVoiceValue, StrudelVoiceValue) -> StrudelVoiceValue?,
): StrudelPattern {
    // We use defaultModifier for args because we just want the 'value'
    val controlPattern = args.toPattern(defaultModifier)

    return ControlPattern(
        source = source,
        control = controlPattern,
        mapper = { it }, // No mapping needed
        combiner = { srcData, ctrlData ->
            val amount = ctrlData.value
            val srcValue = srcData.value

            if (amount == null || srcValue == null) {
                srcData
            } else {
                val newValue = op(srcValue, amount)
                srcData.copy(value = newValue)
            }
        }
    )
}

/**
 * Helper for applying unary operations to patterns.
 */
internal fun applyUnaryOp(
    source: StrudelPattern,
    op: (StrudelVoiceValue) -> StrudelVoiceValue?,
): StrudelPattern {
    // Unary ops (like log2) apply directly to the source values without a control pattern
    return source.reinterpretVoice { srcData ->
        val srcValue = srcData.value

        if (srcValue == null) {
            srcData
        } else {
            val newValue = op(srcValue)
            srcData.copy(value = newValue)
        }
    }
}

// --- Generic Function Delegate (stack, arrange, etc.) ---

class DslFunction(val handler: StrudelDslFn) {
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

// --- Generic Method Delegate (fast, slow, etc.) ---

/**
 * A method bound to a specific [pattern] instance.
 * When invoked, it applies the handler to the bound pattern and arguments.
 */
class DslPatternMethod(
    val pattern: StrudelPattern,
    val handler: StrudelDslExtFn<StrudelPattern>,
) {
    operator fun invoke() = invoke(args = emptyList())

    @JvmName("invokeBlock")
    operator fun invoke(block: (StrudelPattern) -> StrudelPattern) =
        invoke(args = listOf(block).asStrudelDslArgs())

    @JvmName("invokeBlock")
    operator fun invoke(p1: Any, block: (StrudelPattern) -> StrudelPattern) =
        invoke(args = listOf(p1, block).asStrudelDslArgs())

    @JvmName("invokeBlocksVararg")
    operator fun invoke(vararg block: (StrudelPattern) -> StrudelPattern) =
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
class DslFunctionProvider(
    private val handler: StrudelDslFn,
) {
    operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): ReadOnlyProperty<Any?, DslFunction> {
        val name = prop.name
        val func = DslFunction(handler)

        // Register in the evaluator registry - convert RuntimeValue to Any? for the handler
        StrudelRegistry.functions[name] = { args, callInfo ->
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
    ): ReadOnlyProperty<StrudelPattern, DslPatternMethod> {
        val name = prop.name

        // Register in the evaluator registry
        StrudelRegistry.patternExtensionMethods[name] = { recv, args, callInfo ->
            handler(recv, args, callInfo)
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
    private val handler: StrudelDslExtFn<StrudelPattern>,
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>,
    ): ReadOnlyProperty<String, DslPatternMethod> {
        val name = prop.name

        // Register in the evaluator registry
        StrudelRegistry.stringExtensionMethods[name] = { recv, args, callInfo ->
            val pattern = parse(str = recv, baseLocation = callInfo?.receiverLocation)
            handler(pattern, args, callInfo)
        }

        // Return a delegate that creates a BOUND method when accessed
        return ReadOnlyProperty { string, _ ->
            val pattern = parse(str = string, baseLocation = null)
            DslPatternMethod(pattern, handler)
        }
    }

    private fun parse(str: String, baseLocation: SourceLocation?): StrudelPattern {
        return parseMiniNotation(input = str, baseLocation = baseLocation) { text, loc ->
            AtomicPattern(
                data = StrudelVoiceData.empty.defaultModifier(text),
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
        val name = prop.name
        val instance = handler()

        // Register in the evaluator registry as a function that returns the instance
        StrudelRegistry.symbols[name] = instance

        return ReadOnlyProperty { _, _ -> instance }
    }
}
