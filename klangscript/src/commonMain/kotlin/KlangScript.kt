package io.peekandpoke.klang.script

import com.github.h0tk3y.betterParse.parser.ParseException
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.runtime.Interpreter
import io.peekandpoke.klang.script.runtime.LibraryLoader
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
class KlangScript : LibraryLoader {
    /** Registry of available libraries (library name -> source code) */
    private val libraries = mutableMapOf<String, String>()

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
}
