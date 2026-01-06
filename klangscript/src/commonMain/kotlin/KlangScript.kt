package io.peekandpoke.klang.script

import com.github.h0tk3y.betterParse.parser.ParseException
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.runtime.*
import kotlin.reflect.KClass

/**
 * Main facade for the KlangScript engine
 *
 * This is the primary entry point for using KlangScript. It provides a simple
 * API for executing scripts and registering native functions.
 *
 * Basic usage:
 * ```kotlin
 * val engine = KlangScript()
 *
 * // Register functions
 * engine.registerFunction1("print") { value ->
 *     println(value.toDisplayString())
 *     NullValue
 * }
 *
 * // Execute scripts
 * engine.execute("""
 *     print("Hello from KlangScript!")
 * """)
 * ```
 *
 * The engine handles:
 * - Parsing source code into AST
 * - Managing the interpreter and environment
 * - Providing convenient function registration helpers
 */
class KlangScript : LibraryLoader {
    /** Registry of available libraries (library name -> library object) */
    private val libraries = mutableMapOf<String, KlangScriptLibrary>()

    /** Registry of native types (KClass -> type metadata) */
    @PublishedApi
    internal val nativeTypes = mutableMapOf<KClass<*>, NativeTypeInfo>()

    /** Registry of extension methods (KClass -> method name -> ExtensionMethod) */
    @PublishedApi
    internal val extensionMethods = mutableMapOf<KClass<*>, MutableMap<String, ExtensionMethod>>()

    /** The interpreter that executes parsed programs */
    private val interpreter = Interpreter(libraryLoader = this)

    /** The global environment where functions and variables are stored */
    private val environment = interpreter.getEnvironment()

    /**
     * Register a native function that can be called from scripts
     *
     * The function receives a list of runtime values as arguments and
     * must return a runtime value.
     *
     * @param name The function name as it will appear in scripts
     * @param function The Kotlin function implementation
     *
     * Example:
     * ```kotlin
     * engine.registerFunction("add") { args ->
     *     val sum = args.sumOf { (it as NumberValue).value }
     *     NumberValue(sum)
     * }
     * ```
     *
     * Then in scripts: `add(1, 2, 3)` returns 6
     */
    fun registerFunction(name: String, function: (List<RuntimeValue>) -> RuntimeValue) {
        environment.define(name, NativeFunctionValue(name, function))
    }

    /**
     * Execute a KlangScript program
     *
     * This method:
     * 1. Parses the source code into an AST
     * 2. Executes the AST using the interpreter
     * 3. Returns the value of the last statement
     *
     * @param source The KlangScript source code to execute
     * @return The runtime value of the last statement
     * @throws ParseException if the source contains syntax errors
     * @throws RuntimeException if execution fails (e.g., undefined variable)
     *
     * Example:
     * ```kotlin
     * val result = engine.execute("""
     *     print("first")
     *     print("second")
     *     42
     * """)
     * // result is NumberValue(42.0)
     * ```
     */
    fun execute(source: String, sourceName: String? = null): RuntimeValue {
        val program = KlangScriptParser.parse(source, sourceName)
        return interpreter.execute(program)
    }

    /**
     * Helper to register a function that takes no arguments
     *
     * Validates argument count and throws if incorrect.
     *
     * @param name The function name
     * @param function The Kotlin function (no parameters)
     *
     * Example:
     * ```kotlin
     * engine.registerFunction0("getTime") {
     *     NumberValue(System.currentTimeMillis().toDouble())
     * }
     * ```
     */
    fun registerFunction0(name: String, function: () -> RuntimeValue) {
        registerFunction(name) { args ->
            if (args.isNotEmpty()) {
                throw io.peekandpoke.klang.script.runtime.ArgumentError(
                    name,
                    "Expected 0 arguments, got ${args.size}",
                    expected = 0,
                    actual = args.size
                )
            }
            function()
        }
    }

    /**
     * Helper to register a function that takes exactly one argument
     *
     * Validates argument count and throws if incorrect.
     *
     * @param name The function name
     * @param function The Kotlin function (one parameter)
     *
     * Example:
     * ```kotlin
     * engine.registerFunction1("print") { value ->
     *     println(value.toDisplayString())
     *     NullValue
     * }
     * ```
     */
    fun registerFunction1(name: String, function: (RuntimeValue) -> RuntimeValue) {
        registerFunction(name) { args ->
            if (args.size != 1) {
                throw io.peekandpoke.klang.script.runtime.ArgumentError(
                    name,
                    "Expected 1 argument, got ${args.size}",
                    expected = 1,
                    actual = args.size
                )
            }
            function(args[0])
        }
    }

    /**
     * Register a native function that returns a native Kotlin object
     *
     * Similar to registerFunction1, but automatically converts Kotlin parameter types
     * and wraps native return values.
     *
     * @param TParam The Kotlin parameter type
     * @param TReturn The Kotlin return type (will be auto-wrapped)
     * @param name The function name
     * @param function The function implementation
     *
     * Example:
     * ```kotlin
     * engine.registerFunction1<String, StrudelPattern>("note") { pattern ->
     *     StrudelPattern(pattern)  // Returns native object, auto-wrapped
     * }
     * ```
     */
    inline fun <reified TParam : Any, reified TReturn : Any> registerNativeFunction(
        name: String,
        noinline function: (TParam) -> TReturn,
    ) {
        registerFunction(name) { args ->
            if (args.size != 1) {
                throw ArgumentError(
                    name,
                    "Expected 1 argument, got ${args.size}",
                    expected = 1,
                    actual = args.size
                )
            }
            val param = convertParameter<TParam>(args[0])
            val result = function(param)
            wrapAsRuntimeValue(result)
        }
    }

    /**
     * Register a library object that can be imported in scripts
     *
     * Libraries can contain both KlangScript code and native Kotlin registrations
     * (functions, types, extension methods). They provide a clean way to bundle
     * related functionality into reusable packages.
     *
     * @param library The library to register
     *
     * Example:
     * ```kotlin
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
     * engine.registerLibrary(strudelLib)
     * ```
     *
     * Then in scripts:
     * ```javascript
     * import * from "strudel"
     * note("a b c").sound("saw")
     * ```
     */
    fun registerLibrary(library: KlangScriptLibrary) {
        libraries[library.name] = library
    }

    /**
     * Register a library from source code only (backward compatibility)
     *
     * This is a convenience method for registering libraries that contain only
     * KlangScript code without native registrations.
     *
     * @param name The library name (without .klang extension)
     * @param sourceCode The KlangScript source code for the library
     *
     * Example:
     * ```kotlin
     * engine.registerLibrary("math", """
     *     let sqrt = (x) => {
     *         // Native implementation would go here
     *     }
     *     let pi = 3.14159
     * """)
     * ```
     *
     * Then in scripts:
     * ```javascript
     * import * from "math"
     * sqrt(16)  // Available after import
     * ```
     */
    fun registerLibrary(name: String, sourceCode: String) {
        libraries[name] = KlangScriptLibrary.builder(name).source(sourceCode).build()
    }

    /**
     * Load library source code by name (LibraryLoader interface implementation)
     *
     * This is called by the interpreter when executing import statements.
     * It applies any native registrations and returns the library source code.
     *
     * @param name The library name
     * @return The library source code
     * @throws RuntimeException if the library is not found
     */
    override fun loadLibrary(name: String): String {
        val library =
            libraries[name] ?: throw io.peekandpoke.klang.script.runtime.ImportError(name, "Library not found")

        // Apply native registrations before returning source code
        library.applyNativeRegistrations(this)

        // Return source code (or empty string if library has no source)
        return library.getSourceCode() ?: ""
    }

    /**
     * Get access to the underlying interpreter
     *
     * This is primarily used for testing to access the environment directly.
     * In production code, prefer using the public API methods.
     *
     * @return The interpreter instance
     */
    fun getInterpreter(): Interpreter = interpreter

    /**
     * Get a variable from the global environment
     *
     * This is primarily used for testing to inspect variables.
     * In production code, prefer using the execute() method.
     *
     * @param name The variable name
     * @return The runtime value
     * @throws RuntimeException if the variable is not defined
     */
    fun getVariable(name: String): RuntimeValue = environment.get(name)

    // ===== Native Kotlin Interop =====

    /**
     * Register a native Kotlin type
     *
     * Optional - types are auto-registered on first extension method registration.
     * Useful for explicit type registration and documentation.
     *
     * @param T The Kotlin type to register
     *
     * Example:
     * ```kotlin
     * engine.registerNativeType<StrudelPattern>()
     * ```
     */
    inline fun <reified T : Any> registerNativeType() {
        val kClass = T::class
        val qualifiedName = kClass.simpleName ?: "Unknown"  // Use simpleName for multiplatform compatibility
        nativeTypes[kClass] = NativeTypeInfo(kClass, qualifiedName)
    }

    /**
     * Register an extension method with no parameters (just receiver)
     *
     * @param TReceiver The receiver type (the object the method is called on)
     * @param TReturn The return type
     * @param methodName The name of the method
     * @param method The method implementation
     *
     * Example:
     * ```kotlin
     * engine.registerExtensionMethod0<StrudelPattern, StrudelPattern>("reverse") { receiver ->
     *     receiver.reverse()
     * }
     * ```
     */
    inline fun <reified TReceiver : Any, reified TReturn : Any> registerExtensionMethod0(
        methodName: String,
        noinline method: (TReceiver) -> TReturn,
    ) {
        val receiverClass = TReceiver::class
        val qualifiedName = receiverClass.simpleName ?: "Unknown"  // Use simpleName for multiplatform compatibility

        // Auto-register type if not already registered
        if (receiverClass !in nativeTypes) {
            nativeTypes[receiverClass] = NativeTypeInfo(receiverClass, qualifiedName)
        }

        // Create extension method wrapper
        val extensionMethod = ExtensionMethod(
            methodName = methodName,
            receiverClass = receiverClass,
            invoker = { receiver, args ->
                if (args.isNotEmpty()) {
                    throw TypeError(
                        "Method '$methodName' expects 0 arguments, got ${args.size}",
                        operation = "method call"
                    )
                }
                @Suppress("UNCHECKED_CAST")
                val result = method(receiver as TReceiver)
                wrapAsRuntimeValue(result)
            }
        )

        // Register extension method
        extensionMethods
            .getOrPut(receiverClass) { mutableMapOf() }[methodName] = extensionMethod
    }

    /**
     * Register an extension method with one parameter
     *
     * @param TReceiver The receiver type (the object the method is called on)
     * @param TParam The parameter type
     * @param TReturn The return type
     * @param methodName The name of the method
     * @param method The method implementation
     *
     * Example:
     * ```kotlin
     * engine.registerExtensionMethod1<StrudelPattern, String, StrudelPattern>("sound") { receiver, soundName ->
     *     receiver.sound(soundName)
     * }
     * ```
     */
    inline fun <reified TReceiver : Any, reified TParam : Any, reified TReturn : Any> registerExtensionMethod1(
        methodName: String,
        noinline method: (TReceiver, TParam) -> TReturn,
    ) {
        val receiverClass = TReceiver::class
        val qualifiedName = receiverClass.simpleName ?: "Unknown"  // Use simpleName for multiplatform compatibility

        // Auto-register type if not already registered
        if (receiverClass !in nativeTypes) {
            nativeTypes[receiverClass] = NativeTypeInfo(receiverClass, qualifiedName)
        }

        // Create extension method wrapper
        val extensionMethod = ExtensionMethod(
            methodName = methodName,
            receiverClass = receiverClass,
            invoker = { receiver, args ->
                if (args.size != 1) {
                    throw TypeError(
                        "Method '$methodName' expects 1 argument, got ${args.size}",
                        operation = "method call"
                    )
                }
                @Suppress("UNCHECKED_CAST")
                val param = convertParameter<TParam>(args[0])
                val result = method(receiver as TReceiver, param)
                wrapAsRuntimeValue(result)
            }
        )

        // Register extension method
        extensionMethods
            .getOrPut(receiverClass) { mutableMapOf() }[methodName] = extensionMethod
    }

    /**
     * Register an extension method with two parameters
     *
     * @param TReceiver The receiver type (the object the method is called on)
     * @param TParam1 The first parameter type
     * @param TParam2 The second parameter type
     * @param TReturn The return type
     * @param methodName The name of the method
     * @param method The method implementation
     *
     * Example:
     * ```kotlin
     * engine.registerExtensionMethod2<StrudelPattern, String, Double, StrudelPattern>("soundWithGain") { receiver, sound, gain ->
     *     receiver.soundWithGain(sound, gain)
     * }
     * ```
     */
    inline fun <reified TReceiver : Any, reified TParam1 : Any, reified TParam2 : Any, reified TReturn : Any> registerExtensionMethod2(
        methodName: String,
        noinline method: (TReceiver, TParam1, TParam2) -> TReturn,
    ) {
        val receiverClass = TReceiver::class
        val qualifiedName = receiverClass.simpleName ?: "Unknown"  // Use simpleName for multiplatform compatibility

        // Auto-register type if not already registered
        if (receiverClass !in nativeTypes) {
            nativeTypes[receiverClass] = NativeTypeInfo(receiverClass, qualifiedName)
        }

        // Create extension method wrapper
        val extensionMethod = ExtensionMethod(
            methodName = methodName,
            receiverClass = receiverClass,
            invoker = { receiver, args ->
                if (args.size != 2) {
                    throw TypeError(
                        "Method '$methodName' expects 2 arguments, got ${args.size}",
                        operation = "method call"
                    )
                }
                @Suppress("UNCHECKED_CAST")
                val param1 = convertParameter<TParam1>(args[0])
                val param2 = convertParameter<TParam2>(args[1])
                val result = method(receiver as TReceiver, param1, param2)
                wrapAsRuntimeValue(result)
            }
        )

        // Register extension method
        extensionMethods
            .getOrPut(receiverClass) { mutableMapOf() }[methodName] = extensionMethod
    }

    /**
     * Get an extension method for a native type
     *
     * Used by the interpreter to lookup methods during member access evaluation.
     *
     * @param kClass The Kotlin class to lookup methods for
     * @param methodName The method name
     * @return The extension method, or null if not found
     */
    fun getExtensionMethod(kClass: KClass<*>, methodName: String): ExtensionMethod? {
        return extensionMethods[kClass]?.get(methodName)
    }

    /**
     * Get all registered extension method names for a native type
     *
     * Used for error messages to suggest available methods.
     *
     * @param kClass The Kotlin class to get methods for
     * @return List of method names
     */
    fun getExtensionMethodNames(kClass: KClass<*>): List<String> {
        return extensionMethods[kClass]?.keys?.toList() ?: emptyList()
    }

    companion object {
        /**
         * Create a new KlangScript builder
         *
         * The builder pattern provides a clean API for configuring the engine
         * before it becomes immutable.
         *
         * @return A new builder for configuring KlangScript
         *
         * Example:
         * ```kotlin
         * val engine = KlangScript.builder()
         *     .registerLibrary(strudelLib)
         *     .registerFunction("print") { ... }
         *     .build()
         * ```
         */
        fun builder(): Builder = Builder()
    }

    /**
     * Builder for creating KlangScript engine instances
     *
     * The builder collects all registrations (libraries, functions, types, methods)
     * and applies them when build() is called, producing an immutable engine.
     */
    class Builder {
        val engine = KlangScript()

        /**
         * Register a library with the engine
         *
         * @param library The library to register
         * @return This builder for method chaining
         */
        fun registerLibrary(library: KlangScriptLibrary): Builder {
            engine.registerLibrary(library)
            return this
        }

        /**
         * Register a library from source code (backward compatibility)
         *
         * @param name The library name
         * @param sourceCode The KlangScript source code
         * @return This builder for method chaining
         */
        fun registerLibrary(name: String, sourceCode: String): Builder {
            engine.registerLibrary(name, sourceCode)
            return this
        }

        /**
         * Register a native function
         *
         * @param name The function name
         * @param function The function implementation
         * @return This builder for method chaining
         */
        fun registerFunction(name: String, function: (List<RuntimeValue>) -> RuntimeValue): Builder {
            engine.registerFunction(name, function)
            return this
        }

        /**
         * Register a function with no parameters
         *
         * @param name The function name
         * @param function The function implementation
         * @return This builder for method chaining
         */
        fun registerFunction0(name: String, function: () -> RuntimeValue): Builder {
            engine.registerFunction0(name, function)
            return this
        }

        /**
         * Register a function with one parameter
         *
         * @param name The function name
         * @param function The function implementation
         * @return This builder for method chaining
         */
        fun registerFunction1(name: String, function: (RuntimeValue) -> RuntimeValue): Builder {
            engine.registerFunction1(name, function)
            return this
        }

        /**
         * Register a native function that returns a native Kotlin object
         *
         * @param TParam The parameter type
         * @param TReturn The return type
         * @param name The function name
         * @param function The function implementation
         * @return This builder for method chaining
         */
        inline fun <reified TParam : Any, reified TReturn : Any> registerNativeFunction(
            name: String,
            noinline function: (TParam) -> TReturn,
        ): Builder {
            engine.registerNativeFunction(name, function)
            return this
        }

        /**
         * Register a native Kotlin type
         *
         * @param T The type to register
         * @return This builder for method chaining
         */
        inline fun <reified T : Any> registerNativeType(): Builder {
            engine.registerNativeType<T>()
            return this
        }

        /**
         * Register an extension method with no parameters
         *
         * @param TReceiver The receiver type
         * @param TReturn The return type
         * @param methodName The method name
         * @param method The method implementation
         * @return This builder for method chaining
         */
        inline fun <reified TReceiver : Any, reified TReturn : Any> registerExtensionMethod0(
            methodName: String,
            noinline method: (TReceiver) -> TReturn,
        ): Builder {
            engine.registerExtensionMethod0(methodName, method)
            return this
        }

        /**
         * Register an extension method with one parameter
         *
         * @param TReceiver The receiver type
         * @param TParam The parameter type
         * @param TReturn The return type
         * @param methodName The method name
         * @param method The method implementation
         * @return This builder for method chaining
         */
        inline fun <reified TReceiver : Any, reified TParam : Any, reified TReturn : Any> registerExtensionMethod1(
            methodName: String,
            noinline method: (TReceiver, TParam) -> TReturn,
        ): Builder {
            engine.registerExtensionMethod1(methodName, method)
            return this
        }

        /**
         * Register an extension method with two parameters
         *
         * @param TReceiver The receiver type
         * @param TParam1 The first parameter type
         * @param TParam2 The second parameter type
         * @param TReturn The return type
         * @param methodName The method name
         * @param method The method implementation
         * @return This builder for method chaining
         */
        inline fun <reified TReceiver : Any, reified TParam1 : Any, reified TParam2 : Any, reified TReturn : Any> registerExtensionMethod2(
            methodName: String,
            noinline method: (TReceiver, TParam1, TParam2) -> TReturn,
        ): Builder {
            engine.registerExtensionMethod2(methodName, method)
            return this
        }

        /**
         * Build the configured KlangScript engine
         *
         * After build(), the engine is ready to execute scripts.
         * All registrations have been applied.
         *
         * @return The configured KlangScript engine
         */
        fun build(): KlangScript {
            return engine
        }
    }
}
