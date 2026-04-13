@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.addons.sprudelLangArithmeticAddonsInit
import io.peekandpoke.klang.sprudel.lang.addons.sprudelLangContinuousAddonsInit
import io.peekandpoke.klang.sprudel.lang.addons.sprudelLangDynamicsAddonsInit
import io.peekandpoke.klang.sprudel.lang.addons.sprudelLangEffectsAddonsInit
import io.peekandpoke.klang.sprudel.lang.addons.sprudelLangFiltersAddonsInit
import io.peekandpoke.klang.sprudel.lang.addons.sprudelLangOscAddonsInit
import io.peekandpoke.klang.sprudel.lang.addons.sprudelLangSndAddonsInit
import io.peekandpoke.klang.sprudel.lang.addons.sprudelLangStructuralAddonsInit
import io.peekandpoke.klang.sprudel.lang.addons.sprudelLangTempoAddonsInit
import io.peekandpoke.klang.sprudel.lang.docs.registerSprudelDocs
import io.peekandpoke.klang.sprudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern
import kotlin.jvm.JvmName
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Registers all Sprudel DSL functions by accessing the init properties of all lang_*.kt files.
 * This forces the initialization of all delegate properties, which in turn registers them in SprudelRegistry.
 */
fun initSprudelDsl() {
    // Access all init properties to force initialization

    // Register core sprudel functions
    sprudelLangArithmeticInit = true
    sprudelLangConditionalInit = true
    sprudelLangContinuousInit = true
    sprudelLangDynamicsInit = true
    sprudelLangEffectsInit = true
    sprudelLangEngineInit = true
    sprudelLangEuclidInit = true
    sprudelLangFiltersInit = true
    sprudelLangHelpersInit = true
    sprudelLangMiscInit = true
    sprudelLangSampleInit = true
    sprudelLangPatternPickingInit = true
    sprudelLangRandomInit = true
    sprudelLangStructuralInit = true
    sprudelLangSynthesisInit = true
    sprudelLangTempoInit = true
    sprudelLangTonalInit = true
    sprudelLangVowelInit = true

    // register sprudel addon functions, that are not part of the original strudel impl
    sprudelLangArithmeticAddonsInit = true
    sprudelLangContinuousAddonsInit = true
    sprudelLangDynamicsAddonsInit = true
    sprudelLangEffectsAddonsInit = true
    sprudelLangFiltersAddonsInit = true
    sprudelLangOscAddonsInit = true
    sprudelLangSndAddonsInit = true
    sprudelLangStructuralAddonsInit = true
    sprudelLangTempoAddonsInit = true

    // Register DSL documentation
    registerSprudelDocs()
}

@DslMarker
annotation class SprudelDsl

/**
 * Type alias for pattern-like values that can be converted to patterns.
 * Accepts:
 * - [SprudelPattern]
 * - [String]
 * - [Number]
 * - [Boolean]
 * - and other types that can be converted to patterns.
 */
typealias PatternLike = Any

/**
 * Type alias for pattern transformation functions.
 * Takes a SprudelPattern as input and returns a modified SprudelPattern.
 */
typealias PatternMapperFn = (source: SprudelPattern) -> SprudelPattern

/**
 * Type alias for voice data transformation functions.
 * Takes a SprudelVoiceData as input and returns a modified SprudelVoiceData.
 */
typealias VoiceModifierFn = SprudelVoiceData.(Any?) -> SprudelVoiceData

/**
 * Type alias for voice data merging functions.
 * Takes two SprudelVoiceData as input and returns a merged SprudelVoiceData.
 */
typealias VoiceMergerFn = (source: SprudelVoiceData, control: SprudelVoiceData) -> SprudelVoiceData

/**
 * Type alias for a top level DSL function definitions.
 */
typealias SprudelDslTopLevelFn<T> = (args: List<SprudelDslArg<Any?>>, callInfo: CallInfo?) -> T

/**
 * Type alias for DSL extension function definitions.
 * Takes a receiver type R, a list of SprudelDslArg arguments, and a CallInfo, and returns a SprudelPattern.
 */
typealias SprudelDslPatternExtFn<R> =
            (recv: R, args: List<SprudelDslArg<Any?>>, callInfo: CallInfo?) -> SprudelPattern

/**
 * Type alias for DSL extension function definitions.
 * Takes a receiver type R, a list of SprudelDslArg arguments, and a CallInfo, and returns a SprudelPattern.
 */
typealias SprudelDslPatternMapperExtFn<R> =
            (recv: R, args: List<SprudelDslArg<Any?>>, callInfo: CallInfo?) -> PatternMapperFn

/** Creates a DSL object that is registered in the SprudelRegistry. */
fun <T : Any> dslObject(handler: () -> T) = DslObjectProvider(handler)

/** Creates a top level DSL function that returns a SprudelPattern. */
fun dslPatternFunction(handler: SprudelDslTopLevelFn<SprudelPattern>) = DslPatternCreatorFunctionProvider(handler)

/** Creates a DSL extension method on SprudelPattern that returns a SprudelPattern. */
fun dslPatternExtension(handler: SprudelDslPatternExtFn<SprudelPattern>) = DslPatternExtensionProvider(handler)

/** Creates a DSL extension method on String that returns a SprudelPattern. */
fun dslStringExtension(handler: SprudelDslPatternExtFn<SprudelPattern>) = DslStringExtensionProvider(handler)

/** Create a top level DSL function that returns a PatternMapper. */
fun dslPatternMapper(handler: SprudelDslTopLevelFn<PatternMapperFn>) = DslPatternMapperFunctionProvider(handler)

/** Create am extension function on a PatternMapper. */
fun dslPatternMapperExtension(handler: SprudelDslPatternMapperExtFn<PatternMapperFn>) =
    DslPatternMapperExtensionFunctionProvider(handler)

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
        SprudelRegistry.symbols[name] = instance

        return ReadOnlyProperty { _, _ -> instance }
    }
}

/**
 * Provider that registers the function name and creates bound delegates.
 */
class DslPatternCreatorFunctionProvider(private val handler: SprudelDslTopLevelFn<SprudelPattern>) {

    class Fn(val handler: SprudelDslTopLevelFn<SprudelPattern>) {
        operator fun invoke() = invoke(args = emptyList())

        @JvmName("invokeFunction")
        operator fun invoke(block: (Double) -> Double) = invoke(args = listOf(block).asSprudelDslArgs())

        @JvmName("invokeVararg")
        operator fun invoke(vararg args: Any?): SprudelPattern = invoke(args = args.toList().asSprudelDslArgs())

        @JvmName("invokeArray")
        operator fun invoke(args: Array<Any?>): SprudelPattern = invoke(args = args.toList().asSprudelDslArgs())

        @JvmName("invokeList")
        operator fun invoke(args: List<Any?>): SprudelPattern = invoke(args = args.asSprudelDslArgs())

        operator fun invoke(args: List<SprudelDslArg<Any?>>, callInfo: CallInfo? = null): SprudelPattern {
            return handler(args, callInfo)
        }
    }

    operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): ReadOnlyProperty<Any?, Fn> {
        val name = prop.name.trimStart('_')
        val func = Fn(handler)

        // Register in the evaluator registry - convert RuntimeValue to Any? for the handler
        SprudelRegistry.patternCreationFunctions[name] = { args, callInfo ->
            func.invoke(args = args, callInfo = callInfo)
        }

        return ReadOnlyProperty { _, _ -> func }
    }
}

/**
 * Provider that registers the method name and creates bound delegates.
 */
class DslPatternExtensionProvider(private val handler: SprudelDslPatternExtFn<SprudelPattern>) {

    /**
     * A method bound to a specific [pattern] instance.
     * When invoked, it applies the handler to the bound pattern and arguments.
     */
    class Fn(
        val pattern: SprudelPattern,
        val handler: SprudelDslPatternExtFn<SprudelPattern>,
    ) {
        operator fun invoke() = invoke(args = emptyList())

        @JvmName("invokeBlock")
        operator fun invoke(block: PatternMapperFn) =
            invoke(args = listOf(block).asSprudelDslArgs())

        @JvmName("invokeBlock")
        operator fun invoke(p1: Any, block: PatternMapperFn) =
            invoke(args = listOf(p1, block).asSprudelDslArgs())

        @JvmName("invokeBlocksVararg")
        operator fun invoke(vararg block: PatternMapperFn) =
            invoke(args = block.toList().asSprudelDslArgs())

        @JvmName("invokeVararg")
        operator fun invoke(vararg args: Any?): SprudelPattern =
            invoke(args = args.toList().asSprudelDslArgs())

        @JvmName("invokeArray")
        operator fun invoke(args: Array<Any?>): SprudelPattern =
            invoke(args = args.toList().asSprudelDslArgs())

        @JvmName("invokeList")
        operator fun invoke(args: List<Any?>): SprudelPattern =
            invoke(args = args.asSprudelDslArgs())

        operator fun invoke(args: List<SprudelDslArg<Any?>>, callInfo: CallInfo? = null): SprudelPattern =
            handler(pattern, args, callInfo)
    }

    operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): ReadOnlyProperty<SprudelPattern, Fn> {
        val name = prop.name.trimStart('_')

        // Register in the evaluator registry
        SprudelRegistry.patternExtensionMethods[name] = { recv, args, callInfo ->
            handler(recv, args, callInfo)
        }

        // Return a delegate that creates a BOUND method when accessed
        return ReadOnlyProperty { pattern, _ ->
            Fn(pattern, handler)
        }
    }
}

/**
 * Provider that registers the method name and creates bound delegates for Strings.
 */
class DslStringExtensionProvider(
    private val handler: SprudelDslPatternExtFn<SprudelPattern>,
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>,
    ): ReadOnlyProperty<String, DslPatternExtensionProvider.Fn> {
        val name = prop.name.trimStart('_')

        // Register in the evaluator registry
        SprudelRegistry.stringExtensionMethods[name] = { recv, args, callInfo ->
            val pattern = parse(str = recv, baseLocation = callInfo?.receiverLocation)
            handler(pattern, args, callInfo)
        }

        // Return a delegate that creates a BOUND method when accessed
        return ReadOnlyProperty { string, _ ->
            val pattern = parse(str = string, baseLocation = null)
            DslPatternExtensionProvider.Fn(pattern, handler)
        }
    }

    private fun parse(str: String, baseLocation: SourceLocation?): SprudelPattern {
        return parseMiniNotation(input = str, baseLocation = baseLocation) { text, loc ->
            AtomicPattern(
                data = SprudelVoiceData.empty.voiceValueModifier(text),
                sourceLocations = loc,
            )
        }
    }
}

/**
 * Provider that registers the function name and creates bound delegates.
 */
class DslPatternMapperFunctionProvider(private val handler: SprudelDslTopLevelFn<PatternMapperFn>) {

    class Fn(val handler: SprudelDslTopLevelFn<PatternMapperFn>) {
        operator fun invoke(): PatternMapperFn = invoke(args = emptyList())

        @JvmName("invokeFunction")
        operator fun invoke(block: (Double) -> Double): PatternMapperFn =
            invoke(args = listOf(block).asSprudelDslArgs())

        @JvmName("invokeVararg")
        operator fun invoke(vararg args: Any?): PatternMapperFn = invoke(args = args.toList().asSprudelDslArgs())

        @JvmName("invokeArray")
        operator fun invoke(args: Array<Any?>): PatternMapperFn = invoke(args = args.toList().asSprudelDslArgs())

        @JvmName("invokeList")
        operator fun invoke(args: List<Any?>): PatternMapperFn = invoke(args = args.asSprudelDslArgs())

        operator fun invoke(args: List<SprudelDslArg<Any?>>, callInfo: CallInfo? = null): PatternMapperFn {
            return handler(args, callInfo)
        }
    }

    operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): ReadOnlyProperty<Any?, Fn> {
        val name = prop.name.trimStart('_')
        val func = Fn(handler)

        // Register in the evaluator registry - convert RuntimeValue to Any? for the handler
        SprudelRegistry.patternMapperFunctions[name] = { args, callInfo ->
            func.invoke(args = args, callInfo = callInfo)
        }

        return ReadOnlyProperty { _, _ -> func }
    }
}

/**
 * Provider that registers the function name and creates bound delegates.
 */
class DslPatternMapperExtensionFunctionProvider(private val handler: SprudelDslPatternMapperExtFn<PatternMapperFn>) {

    class Fn(
        val mapper: PatternMapperFn,
        val handler: SprudelDslPatternMapperExtFn<PatternMapperFn>,
    ) {
        operator fun invoke(): PatternMapperFn = invoke(args = emptyList())

        @JvmName("invokeFunction")
        operator fun invoke(block: (Double) -> Double): PatternMapperFn =
            invoke(args = listOf(block).asSprudelDslArgs())

        @JvmName("invokeVararg")
        operator fun invoke(vararg args: Any?): PatternMapperFn = invoke(args = args.toList().asSprudelDslArgs())

        @JvmName("invokeArray")
        operator fun invoke(args: Array<Any?>): PatternMapperFn = invoke(args = args.toList().asSprudelDslArgs())

        @JvmName("invokeList")
        operator fun invoke(args: List<Any?>): PatternMapperFn = invoke(args = args.asSprudelDslArgs())

        operator fun invoke(args: List<SprudelDslArg<Any?>>, callInfo: CallInfo? = null): PatternMapperFn {
            return handler(mapper, args, callInfo)
        }
    }

    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>,
    ): ReadOnlyProperty<PatternMapperFn, Fn> {
        val name = prop.name.trimStart('_')

        // Register in the evaluator registry - convert RuntimeValue to Any? for the handler
        SprudelRegistry.patternMapperExtensionMethods[name] = { recv, args, callInfo ->
            handler(recv, args, callInfo)
        }

        return ReadOnlyProperty { mapper, _ ->
            Fn(
                mapper = mapper,
                handler = handler,
            )
        }
    }
}
