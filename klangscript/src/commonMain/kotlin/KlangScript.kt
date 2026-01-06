package io.peekandpoke.klang.script

import com.github.h0tk3y.betterParse.parser.ParseException
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.runtime.*
import kotlin.reflect.KClass

/**
 * Shorthand for using the [KlangScript.Builder]
 */
fun klangScript(builder: KlangScript.Builder.() -> Unit = {}) =
    KlangScript.Builder().apply(builder).build()

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
class KlangScript private constructor(
    libraries: Map<String, KlangScriptLibrary>,
    nativeTypes: Map<KClass<*>, NativeTypeInfo>,
    extensionMethods: Map<KClass<*>, Map<String, ExtensionMethod>>,
    functionRegistrations: List<Pair<String, (List<RuntimeValue>) -> RuntimeValue>>,
) : LibraryLoader {
    /** Registry of available libraries (library name -> library object) */
    private val libraries = libraries.toMutableMap()

    /** Registry of native types (KClass -> type metadata) */
    @PublishedApi
    internal val nativeTypes = nativeTypes.toMutableMap()

    /** Registry of extension methods (KClass -> method name -> ExtensionMethod) */
    @PublishedApi
    internal val extensionMethods = extensionMethods.mapValues { it.value.toMutableMap() }.toMutableMap()

    /** The interpreter that executes parsed programs */
    private val interpreter = Interpreter(libraryLoader = this)

    /** The global environment where functions and variables are stored */
    @PublishedApi
    internal val environment = interpreter.getEnvironment().apply {
        // Apply all function registrations
        functionRegistrations.forEach { (name, function) ->
            define(name, NativeFunctionValue(name, function))
        }
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
     * Internal method for libraries to register native functions
     * Used by KlangScriptLibrary when applying native registrations
     */
    @PublishedApi
    internal inline fun <reified TParam : Any, reified TReturn : Any> registerNativeFunction(
        name: String,
        noinline function: (TParam) -> TReturn,
    ) {
        val wrappedFunction: (List<RuntimeValue>) -> RuntimeValue = { args ->
            if (args.size != 1) {
                throw ArgumentError(
                    name,
                    "Expected 1 argument, got ${args.size}",
                    expected = 1,
                    actual = args.size
                )
            }
            val param = args[0].convertToKotlin<TParam>()
            val result = function(param)
            wrapAsRuntimeValue(result)
        }
        environment.define(name, NativeFunctionValue(name, wrappedFunction))
    }

    /**
     * Internal method for libraries to register functions
     * Used by KlangScriptLibrary when applying native registrations
     */
    @PublishedApi
    internal fun registerFunction(name: String, function: (List<RuntimeValue>) -> RuntimeValue) {
        environment.define(name, NativeFunctionValue(name, function))
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
            libraries[name] ?: throw ImportError(name, "Library not found")

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
        val qualifiedName = kClass.getUniqueClassName()
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
        val qualifiedName = receiverClass.getUniqueClassName()

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
        val qualifiedName = receiverClass.getUniqueClassName()

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
                val param = args[0].convertToKotlin<TParam>()
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
        val qualifiedName = receiverClass.getUniqueClassName()

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
                val param1 = args[0].convertToKotlin<TParam1>()
                val param2 = args[1].convertToKotlin<TParam2>()
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
        @PublishedApi
        internal val libraries = mutableMapOf<String, KlangScriptLibrary>()

        @PublishedApi
        internal val nativeTypes = mutableMapOf<KClass<*>, NativeTypeInfo>()

        @PublishedApi
        internal val extensionMethods = mutableMapOf<KClass<*>, MutableMap<String, ExtensionMethod>>()

        @PublishedApi
        internal val functionRegistrations = mutableListOf<Pair<String, (List<RuntimeValue>) -> RuntimeValue>>()

        /**
         * Register a library with the engine
         *
         * @param library The library to register
         * @return This builder for method chaining
         */
        fun registerLibrary(library: KlangScriptLibrary): Builder {
            libraries[library.name] = library
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
            libraries[name] = KlangScriptLibrary.builder(name).source(sourceCode).build()
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
            functionRegistrations.add(name to function)
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
            registerFunction(name) { args ->
                if (args.isNotEmpty()) {
                    throw ArgumentError(
                        name,
                        "Expected 0 arguments, got ${args.size}",
                        expected = 0,
                        actual = args.size
                    )
                }
                function()
            }
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
            registerFunction(name) { args ->
                if (args.size != 1) {
                    throw ArgumentError(
                        name,
                        "Expected 1 argument, got ${args.size}",
                        expected = 1,
                        actual = args.size
                    )
                }
                function(args[0])
            }
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
            registerFunction(name) { args ->
                if (args.size != 1) {
                    throw ArgumentError(
                        name,
                        "Expected 1 argument, got ${args.size}",
                        expected = 1,
                        actual = args.size
                    )
                }
                val param = args[0].convertToKotlin<TParam>()
                val result = function(param)
                wrapAsRuntimeValue(result)
            }
            return this
        }

        /**
         * Register a native Kotlin type
         *
         * @param T The type to register
         * @return This builder for method chaining
         */
        inline fun <reified T : Any> registerNativeType(): Builder {
            val kClass = T::class
            val qualifiedName = kClass.getUniqueClassName()
            nativeTypes[kClass] = NativeTypeInfo(kClass, qualifiedName)
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
            val receiverClass = TReceiver::class
            val qualifiedName = receiverClass.getUniqueClassName()

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
            val receiverClass = TReceiver::class
            val qualifiedName = receiverClass.getUniqueClassName()

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
                    val param = args[0].convertToKotlin<TParam>()
                    val result = method(receiver as TReceiver, param)
                    wrapAsRuntimeValue(result)
                }
            )

            // Register extension method
            extensionMethods
                .getOrPut(receiverClass) { mutableMapOf() }[methodName] = extensionMethod
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
            val receiverClass = TReceiver::class
            val qualifiedName = receiverClass.getUniqueClassName()

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
                    val param1 = args[0].convertToKotlin<TParam1>()
                    val param2 = args[1].convertToKotlin<TParam2>()
                    val result = method(receiver as TReceiver, param1, param2)
                    wrapAsRuntimeValue(result)
                }
            )

            // Register extension method
            extensionMethods
                .getOrPut(receiverClass) { mutableMapOf() }[methodName] = extensionMethod
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
            return KlangScript(
                libraries = libraries,
                nativeTypes = nativeTypes,
                extensionMethods = extensionMethods,
                functionRegistrations = functionRegistrations
            )
        }
    }
}
