package io.peekandpoke.klang.script

import io.peekandpoke.klang.script.runtime.RuntimeValue

/**
 * Builder for creating reusable KlangScript libraries
 *
 * A library can contain:
 * - KlangScript source code (functions, objects, values)
 * - Native Kotlin function registrations
 * - Native Kotlin type registrations
 * - Native Kotlin extension method registrations
 *
 * Libraries are self-contained packages that can be registered with a KlangScript engine
 * and imported by user scripts. They provide a clean way to bundle related functionality.
 *
 * **Design Philosophy:**
 * - Fluent builder API for library definition
 * - Native registrations are applied when library is first imported
 * - Script-level exports control what's accessible to importers
 * - Native types are registered globally (matches engine architecture)
 *
 * **Example Usage:**
 * ```kotlin
 * // Library author creates a library
 * val strudelLib = KlangScriptLibrary("strudel")
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
 *
 * // Register library with engine
 * engine.registerLibrary(strudelLib)
 *
 * // Use in scripts
 * engine.execute("""
 *     import * from "strudel"
 *     note("a b c").sound("saw")
 * """)
 * ```
 *
 * @param name The library name (used in import statements)
 */
class KlangScriptLibrary(val name: String) {
    /** The KlangScript source code for this library (optional) */
    private var sourceCode: String? = null

    /** List of native type registration functions to apply when library is loaded */
    @PublishedApi
    internal val nativeTypeRegistrations = mutableListOf<(KlangScript) -> Unit>()

    /** List of extension method registration functions to apply when library is loaded */
    @PublishedApi
    internal val extensionMethodRegistrations = mutableListOf<(KlangScript) -> Unit>()

    /** List of native function registration functions to apply when library is loaded */
    @PublishedApi
    internal val functionRegistrations = mutableListOf<(KlangScript) -> Unit>()

    /** Track if native registrations have been applied to prevent duplicate registration */
    private var nativeRegistrationsApplied = false

    /**
     * Set the KlangScript source code for this library
     *
     * The source code can define functions, objects, and values that will be available
     * when the library is imported. Use export statements to control what's accessible.
     *
     * @param code The KlangScript source code
     * @return This library for method chaining
     *
     * Example:
     * ```kotlin
     * library.source("""
     *     let helper = (x) => x * 2
     *     let publicFunc = (x) => helper(x) + 1
     *     export { publicFunc }
     * """)
     * ```
     */
    fun source(code: String): KlangScriptLibrary {
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
     * @return This library for method chaining
     *
     * Example:
     * ```kotlin
     * library.registerNativeType<StrudelPattern>()
     * ```
     */
    inline fun <reified T : Any> registerNativeType(): KlangScriptLibrary {
        nativeTypeRegistrations.add { engine ->
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
     * @return This library for method chaining
     *
     * Example:
     * ```kotlin
     * library.registerNativeFunction<String, StrudelPattern>("note") { pattern ->
     *     StrudelPattern(pattern)
     * }
     * ```
     */
    inline fun <reified TParam : Any, reified TReturn : Any> registerNativeFunction(
        name: String,
        noinline function: (TParam) -> TReturn,
    ): KlangScriptLibrary {
        functionRegistrations.add { engine ->
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
     * @return This library for method chaining
     *
     * Example:
     * ```kotlin
     * library.registerFunction("sum") { args ->
     *     val sum = args.sumOf { (it as NumberValue).value }
     *     NumberValue(sum)
     * }
     * ```
     */
    fun registerFunction(
        name: String,
        function: (List<RuntimeValue>) -> RuntimeValue,
    ): KlangScriptLibrary {
        functionRegistrations.add { engine ->
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
     * @return This library for method chaining
     *
     * Example:
     * ```kotlin
     * library.registerExtensionMethod0<StrudelPattern, StrudelPattern>("reverse") { receiver ->
     *     receiver.reverse()
     * }
     * ```
     */
    inline fun <reified TReceiver : Any, reified TReturn : Any> registerExtensionMethod0(
        methodName: String,
        noinline method: (TReceiver) -> TReturn,
    ): KlangScriptLibrary {
        extensionMethodRegistrations.add { engine ->
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
     * @return This library for method chaining
     *
     * Example:
     * ```kotlin
     * library.registerExtensionMethod1<StrudelPattern, String, StrudelPattern>("sound") { receiver, soundName ->
     *     receiver.sound(soundName)
     * }
     * ```
     */
    inline fun <reified TReceiver : Any, reified TParam : Any, reified TReturn : Any> registerExtensionMethod1(
        methodName: String,
        noinline method: (TReceiver, TParam) -> TReturn,
    ): KlangScriptLibrary {
        extensionMethodRegistrations.add { engine ->
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
     * @return This library for method chaining
     *
     * Example:
     * ```kotlin
     * library.registerExtensionMethod2<StrudelPattern, Double, Double, StrudelPattern>("pan") { receiver, left, right ->
     *     receiver.pan(left, right)
     * }
     * ```
     */
    inline fun <reified TReceiver : Any, reified TParam1 : Any, reified TParam2 : Any, reified TReturn : Any> registerExtensionMethod2(
        methodName: String,
        noinline method: (TReceiver, TParam1, TParam2) -> TReturn,
    ): KlangScriptLibrary {
        extensionMethodRegistrations.add { engine ->
            engine.registerExtensionMethod2(methodName, method)
        }
        return this
    }

    /**
     * Apply all native registrations to the given engine
     *
     * This is called internally when the library is first imported.
     * Ensures registrations are only applied once per engine.
     *
     * @param engine The KlangScript engine to register with
     */
    internal fun applyNativeRegistrations(engine: KlangScript) {
        // Apply registrations only once
        if (nativeRegistrationsApplied) {
            return
        }

        // Register native types
        nativeTypeRegistrations.forEach { it(engine) }

        // Register extension methods
        extensionMethodRegistrations.forEach { it(engine) }

        // Register native functions
        functionRegistrations.forEach { it(engine) }

        nativeRegistrationsApplied = true
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
     * @return True if this library has any native type, extension method, or function registrations
     */
    internal fun hasNativeRegistrations(): Boolean {
        return nativeTypeRegistrations.isNotEmpty() ||
                extensionMethodRegistrations.isNotEmpty() ||
                functionRegistrations.isNotEmpty()
    }
}
