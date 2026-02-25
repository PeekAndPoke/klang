@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.strudel.lang.addons.*
import io.peekandpoke.klang.strudel.lang.docs.registerStrudelDocs
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.strudel.pattern.AtomicPattern
import kotlin.jvm.JvmName
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Registers all Strudel DSL functions by accessing the init properties of all lang_*.kt files.
 * This forces the initialization of all delegate properties, which in turn registers them in StrudelRegistry.
 */
fun initStrudelLang() {
    // Access all init properties to force initialization

    // Register core strudel functions
    strudelLangArithmeticInit = true
    strudelLangConditionalInit = true
    strudelLangContinuousInit = true
    strudelLangDynamicsInit = true
    strudelLangEffectsInit = true
    strudelLangFiltersInit = true
    strudelLangHelpersInit = true
    strudelLangMiscInit = true
    strudelLangSampleInit = true
    strudelLangPatternPickingInit = true
    strudelLangRandomInit = true
    strudelLangStructuralInit = true
    strudelLangSynthesisInit = true
    strudelLangTempoInit = true
    strudelLangTonalInit = true
    strudelLangVowelInit = true

    // register strudel addon functions, that are not part of the original strudel impl
    strudelLangArithmeticAddonsInit = true
    strudelLangContinuousAddonsInit = true
    strudelLangFiltersAddonsInit = true
    strudelLangOscAddonsInit = true
    strudelLangStructuralAddonsInit = true
    strudelLangTempoAddonsInit = true

    // Register DSL documentation
    registerStrudelDocs()
}

@DslMarker
annotation class StrudelDsl

/**
 * Type alias for pattern-like values that can be converted to patterns.
 * Accepts:
 * - [StrudelPattern]
 * - [String]
 * - [Number]
 * - [Boolean]
 * - and other types that can be converted to patterns.
 */
typealias PatternLike = Any

/**
 * Type alias for pattern transformation functions.
 * Takes a StrudelPattern as input and returns a modified StrudelPattern.
 */
typealias PatternMapperFn = (source: StrudelPattern) -> StrudelPattern

/**
 * Type alias for voice data transformation functions.
 * Takes a StrudelVoiceData as input and returns a modified StrudelVoiceData.
 */
typealias VoiceModifierFn = StrudelVoiceData.(Any?) -> StrudelVoiceData

/**
 * Type alias for voice data merging functions.
 * Takes two StrudelVoiceData as input and returns a merged StrudelVoiceData.
 */
typealias VoiceMergerFn = (source: StrudelVoiceData, control: StrudelVoiceData) -> StrudelVoiceData

/**
 * Type alias for a top level DSL function definitions.
 */
typealias StrudelDslTopLevelFn<T> = (args: List<StrudelDslArg<Any?>>, callInfo: CallInfo?) -> T

/**
 * Type alias for DSL extension function definitions.
 * Takes a receiver type R, a list of StrudelDslArg arguments, and a CallInfo, and returns a StrudelPattern.
 */
typealias StrudelDslPatternExtFn<R> =
            (recv: R, args: List<StrudelDslArg<Any?>>, callInfo: CallInfo?) -> StrudelPattern

/**
 * Type alias for DSL extension function definitions.
 * Takes a receiver type R, a list of StrudelDslArg arguments, and a CallInfo, and returns a StrudelPattern.
 */
typealias StrudelDslPatternMapperExtFn<R> =
            (recv: R, args: List<StrudelDslArg<Any?>>, callInfo: CallInfo?) -> PatternMapperFn

/** Creates a DSL object that is registered in the StrudelRegistry. */
fun <T : Any> dslObject(handler: () -> T) = DslObjectProvider(handler)

/** Creates a top level DSL function that returns a StrudelPattern. */
fun dslPatternFunction(handler: StrudelDslTopLevelFn<StrudelPattern>) = DslPatternCreatorFunctionProvider(handler)

/** Creates a DSL extension method on StrudelPattern that returns a StrudelPattern. */
fun dslPatternExtension(handler: StrudelDslPatternExtFn<StrudelPattern>) = DslPatternExtensionProvider(handler)

/** Creates a DSL extension method on String that returns a StrudelPattern. */
fun dslStringExtension(handler: StrudelDslPatternExtFn<StrudelPattern>) = DslStringExtensionProvider(handler)

/** Create a top level DSL function that returns a PatternMapper. */
fun dslPatternMapper(handler: StrudelDslTopLevelFn<PatternMapperFn>) = DslPatternMapperFunctionProvider(handler)

/** Create am extension function on a PatternMapper. */
fun dslPatternMapperExtension(handler: StrudelDslPatternMapperExtFn<PatternMapperFn>) =
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
        StrudelRegistry.symbols[name] = instance

        return ReadOnlyProperty { _, _ -> instance }
    }
}

/**
 * Provider that registers the function name and creates bound delegates.
 */
class DslPatternCreatorFunctionProvider(private val handler: StrudelDslTopLevelFn<StrudelPattern>) {

    class Fn(val handler: StrudelDslTopLevelFn<StrudelPattern>) {
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

    operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): ReadOnlyProperty<Any?, Fn> {
        val name = prop.name.trimStart('_')
        val func = Fn(handler)

        // Register in the evaluator registry - convert RuntimeValue to Any? for the handler
        StrudelRegistry.patternCreationFunctions[name] = { args, callInfo ->
            func.invoke(args = args, callInfo = callInfo)
        }

        return ReadOnlyProperty { _, _ -> func }
    }
}

/**
 * Provider that registers the method name and creates bound delegates.
 */
class DslPatternExtensionProvider(private val handler: StrudelDslPatternExtFn<StrudelPattern>) {

    /**
     * A method bound to a specific [pattern] instance.
     * When invoked, it applies the handler to the bound pattern and arguments.
     */
    class Fn(
        val pattern: StrudelPattern,
        val handler: StrudelDslPatternExtFn<StrudelPattern>,
    ) {
        operator fun invoke() = invoke(args = emptyList())

        @JvmName("invokeBlock")
        operator fun invoke(block: PatternMapperFn) =
            invoke(args = listOf(block).asStrudelDslArgs())

        @JvmName("invokeBlock")
        operator fun invoke(p1: Any, block: PatternMapperFn) =
            invoke(args = listOf(p1, block).asStrudelDslArgs())

        @JvmName("invokeBlocksVararg")
        operator fun invoke(vararg block: PatternMapperFn) =
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

    operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): ReadOnlyProperty<StrudelPattern, Fn> {
        val name = prop.name.trimStart('_')

        // Register in the evaluator registry
        StrudelRegistry.patternExtensionMethods[name] = { recv, args, callInfo ->
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
    private val handler: StrudelDslPatternExtFn<StrudelPattern>,
) {
    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>,
    ): ReadOnlyProperty<String, DslPatternExtensionProvider.Fn> {
        val name = prop.name.trimStart('_')

        // Register in the evaluator registry
        StrudelRegistry.stringExtensionMethods[name] = { recv, args, callInfo ->
            val pattern = parse(str = recv, baseLocation = callInfo?.receiverLocation)
            handler(pattern, args, callInfo)
        }

        // Return a delegate that creates a BOUND method when accessed
        return ReadOnlyProperty { string, _ ->
            val pattern = parse(str = string, baseLocation = null)
            DslPatternExtensionProvider.Fn(pattern, handler)
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
 * Provider that registers the function name and creates bound delegates.
 */
class DslPatternMapperFunctionProvider(private val handler: StrudelDslTopLevelFn<PatternMapperFn>) {

    class Fn(val handler: StrudelDslTopLevelFn<PatternMapperFn>) {
        operator fun invoke(): PatternMapperFn = invoke(args = emptyList())

        @JvmName("invokeFunction")
        operator fun invoke(block: (Double) -> Double): PatternMapperFn =
            invoke(args = listOf(block).asStrudelDslArgs())

        @JvmName("invokeVararg")
        operator fun invoke(vararg args: Any?): PatternMapperFn = invoke(args = args.toList().asStrudelDslArgs())

        @JvmName("invokeArray")
        operator fun invoke(args: Array<Any?>): PatternMapperFn = invoke(args = args.toList().asStrudelDslArgs())

        @JvmName("invokeList")
        operator fun invoke(args: List<Any?>): PatternMapperFn = invoke(args = args.asStrudelDslArgs())

        operator fun invoke(args: List<StrudelDslArg<Any?>>, callInfo: CallInfo? = null): PatternMapperFn {
            return handler(args, callInfo)
        }
    }

    operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): ReadOnlyProperty<Any?, Fn> {
        val name = prop.name.trimStart('_')
        val func = Fn(handler)

        // Register in the evaluator registry - convert RuntimeValue to Any? for the handler
        StrudelRegistry.patternMapperFunctions[name] = { args, callInfo ->
            func.invoke(args = args, callInfo = callInfo)
        }

        return ReadOnlyProperty { _, _ -> func }
    }
}


/**
 * Provider that registers the function name and creates bound delegates.
 */
class DslPatternMapperExtensionFunctionProvider(private val handler: StrudelDslPatternMapperExtFn<PatternMapperFn>) {

    class Fn(
        val mapper: PatternMapperFn,
        val handler: StrudelDslPatternMapperExtFn<PatternMapperFn>,
    ) {
        operator fun invoke(): PatternMapperFn = invoke(args = emptyList())

        @JvmName("invokeFunction")
        operator fun invoke(block: (Double) -> Double): PatternMapperFn =
            invoke(args = listOf(block).asStrudelDslArgs())

        @JvmName("invokeVararg")
        operator fun invoke(vararg args: Any?): PatternMapperFn = invoke(args = args.toList().asStrudelDslArgs())

        @JvmName("invokeArray")
        operator fun invoke(args: Array<Any?>): PatternMapperFn = invoke(args = args.toList().asStrudelDslArgs())

        @JvmName("invokeList")
        operator fun invoke(args: List<Any?>): PatternMapperFn = invoke(args = args.asStrudelDslArgs())

        operator fun invoke(args: List<StrudelDslArg<Any?>>, callInfo: CallInfo? = null): PatternMapperFn {
            return handler(mapper, args, callInfo)
        }
    }

    operator fun provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>,
    ): ReadOnlyProperty<PatternMapperFn, Fn> {
        val name = prop.name.trimStart('_')

        // Register in the evaluator registry - convert RuntimeValue to Any? for the handler
        StrudelRegistry.patternMapperExtensionMethods[name] = { recv, args, callInfo ->
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

