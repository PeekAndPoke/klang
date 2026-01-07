package io.peekandpoke.klang.script

import com.github.h0tk3y.betterParse.parser.ParseException
import io.peekandpoke.klang.script.builder.KlangScriptExtension
import io.peekandpoke.klang.script.builder.KlangScriptExtensionBuilder
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.runtime.Interpreter
import io.peekandpoke.klang.script.runtime.LibraryLoader
import io.peekandpoke.klang.script.runtime.NativeExtensionMethod
import io.peekandpoke.klang.script.runtime.RuntimeValue
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
class KlangScriptEngine private constructor(
    native: KlangScriptExtension,
) : LibraryLoader {
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

    /** The interpreter that executes parsed programs */
    val interpreter = Interpreter(engine = this)

    /** The global environment where functions and variables are stored */
    val environment = interpreter.getEnvironment().apply {
        register(native)
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
        return environment.loadLibrary(name)
    }

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

    /**
     * Get an extension method for a native type
     *
     * Used by the interpreter to lookup methods during member access evaluation.
     *
     * @param kClass The Kotlin class to lookup methods for
     * @param methodName The method name
     * @return The extension method, or null if not found
     */
    fun getNativeExtensionMethod(kClass: KClass<*>, methodName: String): NativeExtensionMethod? {
        return environment.getExtensionMethod(kClass, methodName)
    }

    /**
     * Get all registered extension method names for a native type
     *
     * Used for error messages to suggest available methods.
     *
     * @param kClass The Kotlin class to get methods for
     * @return List of method names
     */
    fun getExtensionMethodNames(kClass: KClass<*>): Set<String> {
        return environment.getExtensionMethodNames(kClass)
    }

    /**
     * Builder for creating KlangScript engine instances
     *
     * The builder collects all registrations (libraries, functions, types, methods)
     * and applies them when build() is called, producing an immutable engine.
     */
    class Builder(
        private val registry: KlangScriptExtensionBuilder = KlangScriptExtensionBuilder(),
    ) : KlangScriptExtensionBuilder by registry {

        /**
         * Build the configured KlangScript engine
         *
         * After build(), the engine is ready to execute scripts.
         * All registrations have been applied.
         *
         * @return The configured KlangScript engine
         */
        fun build(): KlangScriptEngine {
            return KlangScriptEngine(
                native = registry.buildNativeRegistry(),
            )
        }
    }
}
