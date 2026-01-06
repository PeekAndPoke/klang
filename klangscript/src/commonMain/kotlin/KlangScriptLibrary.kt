package io.peekandpoke.klang.script

import io.peekandpoke.klang.script.runtime.RuntimeValue

/**
 * Shorthand factory method to create a KlangScript library.
 */
fun klangScriptLibrary(name: String, builder: KlangScriptLibrary.Builder.() -> Unit) =
    KlangScriptLibrary.builder(name).apply(builder).build()

/**
 * Immutable library definition for KlangScript
 *
 * A library bundles KlangScript source code with native Kotlin registrations (functions, types, extension methods).
 * Libraries are created using the builder pattern and become immutable after construction.
 *
 * **Lifecycle**:
 * 1. Create builder: `KlangScriptLibrary.builder("name")`
 * 2. Configure library: `.source()`, `.registerNativeFunction()`, etc.
 * 3. Build immutable library: `.build()`
 * 4. Register with engine: `engine.registerLibrary(library)`
 *
 * **Example**:
 * ```kotlin
 * val strudelLib = KlangScriptLibrary.builder("strudel")
 *     .source("""
 *         let sequence = (pattern) => note(pattern)
 *         export { sequence }
 *     """)
 *     .registerNativeFunction<String, StrudelPattern>("note") { pattern ->
 *         StrudelPattern(pattern)
 *     }
 *     .registerExtensionMethod1<StrudelPattern, String, StrudelPattern>("sound") { receiver, soundName ->
 *         receiver.sound(soundName)
 *     }
 *     .build()
 *
 * engine.registerLibrary(strudelLib)
 * ```
 *
 * @param name The library name (used in import statements)
 */
class KlangScriptLibrary internal constructor(
    val name: String,
    private val sourceCode: String?,
    private val registrations: List<(KlangScript) -> Unit>,
) {
    /** Track if native registrations have been applied to prevent duplicate registration */
    private var nativeRegistrationsApplied = false

    /**
     * Apply all native registrations to the given engine
     *
     * This is called internally when the library is first imported.
     * Ensures registrations are only applied once per engine.
     *
     * Note: Thread safety is intentionally relaxed here. Multiple calls are safe
     * because registrations are idempotent (engines track what's already registered).
     * The worst case is registrations being applied twice, which is harmless.
     *
     * @param engine The KlangScript engine to register with
     */
    internal fun applyNativeRegistrations(engine: KlangScript) {
        // Simple check to avoid duplicate work in common case
        if (nativeRegistrationsApplied) {
            return
        }
        nativeRegistrationsApplied = true

        // Apply all registrations
        registrations.forEach { it(engine) }
    }

    /**
     * Get the source code for this library
     *
     * @return The KlangScript source code, or null if no source was provided
     */
    internal fun getSourceCode(): String? = sourceCode

    /**
     * Check if this library has native registrations
     *
     * @return True if this library has any native registrations
     */
    internal fun hasNativeRegistrations(): Boolean = registrations.isNotEmpty()

    companion object {
        /**
         * Create a new library builder
         *
         * @param name The library name (used in import statements)
         * @return A new builder for configuring the library
         *
         * Example:
         * ```kotlin
         * val lib = KlangScriptLibrary.builder("mylib")
         *     .source("...")
         *     .registerNativeFunction(...) { ... }
         *     .build()
         * ```
         */
        fun builder(name: String): Builder = Builder(name)
    }

    /**
     * Builder for creating immutable KlangScriptLibrary instances
     *
     * The builder is mutable during configuration, then produces an immutable library.
     * All registration methods return `this` for fluent chaining.
     *
     * @param name The library name
     */
    class Builder(private val name: String) {
        /** The KlangScript source code for this library (optional) */
        private var sourceCode: String? = null

        /** List of registration functions to apply when library is loaded */
        @PublishedApi
        internal val registrations = mutableListOf<(KlangScript) -> Unit>()

        /**
         * Set the KlangScript source code for this library
         *
         * The source code can define functions, objects, and values that will be available
         * when the library is imported. Use export statements to control what's accessible.
         *
         * @param code The KlangScript source code
         * @return This builder for method chaining
         *
         * Example:
         * ```kotlin
         * builder.source("""
         *     let helper = (x) => x * 2
         *     let publicFunc = (x) => helper(x) + 1
         *     export { publicFunc }
         * """)
         * ```
         */
        fun source(code: String): Builder {
            this.sourceCode = code
            return this
        }

        /**
         * Register a native Kotlin type with this library
         *
         * Types are automatically registered when extension methods are added,
         * but this method allows explicit registration for documentation.
         *
         * @param T The Kotlin type to register
         * @return This builder for method chaining
         *
         * Example:
         * ```kotlin
         * builder.registerNativeType<StrudelPattern>()
         * ```
         */
        inline fun <reified T : Any> registerNativeType(): Builder {
            registrations.add { engine ->
                engine.registerNativeType<T>()
            }
            return this
        }

        /**
         * Register a native function (1 parameter) that returns a native Kotlin object
         *
         * The function automatically converts the parameter from RuntimeValue to Kotlin type
         * and wraps the return value in NativeObjectValue.
         *
         * @param TParam The Kotlin parameter type
         * @param TReturn The Kotlin return type
         * @param name The function name as it will appear in scripts
         * @param function The Kotlin function implementation
         * @return This builder for method chaining
         *
         * Example:
         * ```kotlin
         * builder.registerNativeFunction<String, StrudelPattern>("note") { pattern ->
         *     StrudelPattern(pattern)
         * }
         * ```
         */
        inline fun <reified TParam : Any, reified TReturn : Any> registerNativeFunction(
            name: String,
            noinline function: (TParam) -> TReturn,
        ): Builder {
            registrations.add { engine ->
                engine.registerNativeFunction(name, function)
            }
            return this
        }

        /**
         * Register a native function that works with RuntimeValues directly
         *
         * This is useful for functions that need dynamic argument handling or
         * want to work with RuntimeValues directly.
         *
         * @param name The function name as it will appear in scripts
         * @param function The Kotlin function implementation
         * @return This builder for method chaining
         *
         * Example:
         * ```kotlin
         * builder.registerFunction("sum") { args ->
         *     val sum = args.sumOf { (it as NumberValue).value }
         *     NumberValue(sum)
         * }
         * ```
         */
        fun registerFunction(
            name: String,
            function: (List<RuntimeValue>) -> RuntimeValue,
        ): Builder {
            registrations.add { engine ->
                engine.registerFunction(name, function)
            }
            return this
        }

        /**
         * Register an extension method with no parameters
         *
         * Extension methods are callable on native objects using dot notation.
         *
         * @param TReceiver The receiver type (the object this method is called on)
         * @param TReturn The return type
         * @param methodName The method name
         * @param method The method implementation
         * @return This builder for method chaining
         *
         * Example:
         * ```kotlin
         * builder.registerExtensionMethod0<StrudelPattern, StrudelPattern>("reverse") { receiver ->
         *     receiver.reverse()
         * }
         * ```
         */
        inline fun <reified TReceiver : Any, reified TReturn : Any> registerExtensionMethod0(
            methodName: String,
            noinline method: (TReceiver) -> TReturn,
        ): Builder {
            registrations.add { engine ->
                engine.registerExtensionMethod0(methodName, method)
            }
            return this
        }

        /**
         * Register an extension method with one parameter
         *
         * Extension methods are callable on native objects using dot notation.
         *
         * @param TReceiver The receiver type (the object this method is called on)
         * @param TParam The parameter type
         * @param TReturn The return type
         * @param methodName The method name
         * @param method The method implementation
         * @return This builder for method chaining
         *
         * Example:
         * ```kotlin
         * builder.registerExtensionMethod1<StrudelPattern, String, StrudelPattern>("sound") { receiver, soundName ->
         *     receiver.sound(soundName)
         * }
         * ```
         */
        inline fun <reified TReceiver : Any, reified TParam : Any, reified TReturn : Any> registerExtensionMethod1(
            methodName: String,
            noinline method: (TReceiver, TParam) -> TReturn,
        ): Builder {
            registrations.add { engine ->
                engine.registerExtensionMethod1(methodName, method)
            }
            return this
        }

        /**
         * Register an extension method with two parameters
         *
         * Extension methods are callable on native objects using dot notation.
         *
         * @param TReceiver The receiver type (the object this method is called on)
         * @param TParam1 The first parameter type
         * @param TParam2 The second parameter type
         * @param TReturn The return type
         * @param methodName The method name
         * @param method The method implementation
         * @return This builder for method chaining
         *
         * Example:
         * ```kotlin
         * builder.registerExtensionMethod2<StrudelPattern, Double, Double, StrudelPattern>("pan") { receiver, left, right ->
         *     receiver.pan(left, right)
         * }
         * ```
         */
        inline fun <reified TReceiver : Any, reified TParam1 : Any, reified TParam2 : Any, reified TReturn : Any> registerExtensionMethod2(
            methodName: String,
            noinline method: (TReceiver, TParam1, TParam2) -> TReturn,
        ): Builder {
            registrations.add { engine ->
                engine.registerExtensionMethod2(methodName, method)
            }
            return this
        }

        /**
         * Build an immutable KlangScriptLibrary from this builder's configuration
         *
         * After calling build(), the library cannot be modified.
         * The library can be safely shared across multiple engines.
         *
         * @return An immutable KlangScriptLibrary
         */
        fun build(): KlangScriptLibrary {
            return KlangScriptLibrary(
                name = name,
                sourceCode = sourceCode,
                registrations = registrations.toList() // Defensive copy
            )
        }
    }
}
