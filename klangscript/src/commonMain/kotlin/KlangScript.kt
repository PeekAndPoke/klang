package io.peekandpoke.klang.script

import com.github.h0tk3y.betterParse.parser.ParseException
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.runtime.Interpreter
import io.peekandpoke.klang.script.runtime.NativeFunctionValue
import io.peekandpoke.klang.script.runtime.RuntimeValue

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
class KlangScript {
    /** The interpreter that executes parsed programs */
    private val interpreter = Interpreter()

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
    fun execute(source: String): RuntimeValue {
        val program = KlangScriptParser.parse(source)
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
                throw RuntimeException("Function $name expects 0 arguments, got ${args.size}")
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
                throw RuntimeException("Function $name expects 1 argument, got ${args.size}")
            }
            function(args[0])
        }
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
}
