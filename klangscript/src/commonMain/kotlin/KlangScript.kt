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
    /** Registry of available libraries (library name -> source code) */
    private val libraries = mutableMapOf<String, String>()

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
     * Register a library that can be imported in scripts
     *
     * Libraries are KlangScript source code that define functions, objects,
     * and values. They can be imported using: `import * from "libraryName"`
     *
     * **Design philosophy:**
     * Libraries are not hard-coded - they are KlangScript files registered
     * by the host application. This keeps the language core minimal and allows
     * complete flexibility in what libraries provide.
     *
     * @param name The library name (without .klang extension)
     * @param sourceCode The KlangScript source code for the library
     *
     * Example:
     * ```kotlin
     * engine.registerLibrary("math", """
     *     let sqrt = (x) => {
     *         // Native implementation would go here
     *         // For now, just a placeholder
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
        libraries[name] = sourceCode
    }

    /**
     * Load library source code by name (LibraryLoader interface implementation)
     *
     * This is called by the interpreter when executing import statements.
     *
     * @param name The library name
     * @return The library source code
     * @throws RuntimeException if the library is not found
     */
    override fun loadLibrary(name: String): String {
        return libraries[name] ?: throw io.peekandpoke.klang.script.runtime.ImportError(name, "Library not found")
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
}
