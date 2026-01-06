package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.script.KlangScriptLibrary
import io.peekandpoke.klang.script.klangScriptLibrary
import io.peekandpoke.klang.script.runtime.*
import io.peekandpoke.klang.script.stdlib.KlangStdLib.ConsoleObject.printImpl
import kotlin.math.*

/**
 * KlangScript Standard Library
 *
 * Provides commonly-used built-in functions for KlangScript programs.
 * This library follows JavaScript conventions, providing a `Math` object with methods.
 *
 * **Categories**:
 * - **I/O Functions**: `print()`, `console_log()` (global functions)
 * - **Math Object**: `Math.sqrt()`, `Math.abs()`, `Math.min()`, etc. (matches JavaScript API)
 * - **String Functions**: `length()`, `toUpperCase()`, `toLowerCase()` (global functions)
 *
 * **Usage**:
 * ```kotlin
 * val engine = klangScript {
 *     registerLibrary(KlangStdLib.create())
 * }
 *
 * engine.execute("""
 *     import * from "stdlib"
 *
 *     print("Hello, World!")
 *     let x = Math.sqrt(16)
 *     print("Square root of 16 is:", x)
 *     print("Max:", Math.max(5, 10))
 * """)
 * ```
 *
 * **Design Philosophy**:
 * - Follows JavaScript conventions where possible (Math object, console_log)
 * - Type-safe parameter conversion with helpful error messages
 * - Demonstrates native object with extension methods
 * - Minimal dependencies (only Kotlin stdlib)
 */
object KlangStdLib {

    /**
     * Output handler for print functions
     *
     * By default, prints to stdout. Can be overridden for testing or custom output handling.
     */
    var outputHandler: (String) -> Unit = { println(it) }

    /**
     * Math object - singleton for holding math operations (like JavaScript's Math)
     */
    object MathObject {
        override fun toString(): String = "[Math object]"

        fun KlangScriptLibrary.Builder.register() {
            // Math object methods - Single parameter
            registerExtensionMethod1<MathObject, Double, Double>("sqrt") { _, x ->
                sqrt(x)
            }
            registerExtensionMethod1<MathObject, Double, Double>("abs") { _, x ->
                abs(x)
            }
            registerExtensionMethod1<MathObject, Double, Double>("floor") { _, x ->
                floor(x)
            }
            registerExtensionMethod1<MathObject, Double, Double>("ceil") { _, x ->
                ceil(x)
            }
            registerExtensionMethod1<MathObject, Double, Double>("round") { _, x ->
                round(x)
            }
            registerExtensionMethod1<MathObject, Double, Double>("sin") { _, x ->
                sin(x)
            }
            registerExtensionMethod1<MathObject, Double, Double>("cos") { _, x ->
                cos(x)
            }
            registerExtensionMethod1<MathObject, Double, Double>("tan") { _, x ->
                tan(x)
            }

            // Math object methods - Two parameters
            registerExtensionMethod2<MathObject, Double, Double, Double>("min") { _, a, b ->
                min(a, b)
            }
            registerExtensionMethod2<MathObject, Double, Double, Double>("max") { _, a, b ->
                max(a, b)
            }
            registerExtensionMethod2<MathObject, Double, Double, Double>("pow") { _, base, exponent ->
                base.pow(exponent)
            }
        }
    }

    /**
     * Console object - singleton for holding console functions (like JavaScript's console)
     */
    object ConsoleObject {
        override fun toString(): String = "[Console object]"

        fun KlangScriptLibrary.Builder.register() {
            registerExtensionMethod1<ConsoleObject, String, Unit>("log") { _, args ->
                outputHandler(args)
            }
        }

        /**
         * Implementation of print() and console_log()
         *
         * Prints all arguments separated by spaces
         */
        fun printImpl(args: List<RuntimeValue>): RuntimeValue {
            val output = args.joinToString(" ") { it.toDisplayString() }
            outputHandler(output)
            return NullValue
        }
    }

    /**
     * Create the standard library
     *
     * @return A KlangScriptLibrary instance containing all standard functions
     */
    fun create(): KlangScriptLibrary {
        return klangScriptLibrary("stdlib") {
            source(
                """
                const console = __createConsoleObject()
                const Math = __createMathObject()

                export {
                    console,
                    Math 
                }
                """.trimIndent()
            )

            // Register console functions
            registerFunction("__createConsoleObject") { NativeObjectValue(value = ConsoleObject) }
            with(ConsoleObject) { register() }

            // Register Math functions
            registerFunction("__createMathObject") { NativeObjectValue(value = MathObject) }
            with(MathObject) { register() }

            // Output functions
            registerFunction("print") { args ->
                printImpl(args)
            }


            // String Functions (kept as global functions)
            registerFunction("length") { args ->
                requireExactly(args, 1, "length")
                val str = toString(args[0], "length")
                NumberValue(str.length.toDouble())
            }
            registerFunction("toUpperCase") { args ->
                requireExactly(args, 1, "toUpperCase")
                val str = toString(args[0], "toUpperCase")
                StringValue(str.uppercase())
            }
            registerFunction("toLowerCase") { args ->
                requireExactly(args, 1, "toLowerCase")
                val str = toString(args[0], "toLowerCase")
                StringValue(str.lowercase())
            }
        }
    }

    // ===== Helper Functions =====

    /**
     * Require exactly N arguments
     */
    private fun requireExactly(args: List<RuntimeValue>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw IllegalArgumentException(
                "$functionName() expects exactly $expected argument(s), got ${args.size}"
            )
        }
    }

    /**
     * Convert RuntimeValue to String
     */
    private fun toString(value: RuntimeValue, functionName: String): String {
        return when (value) {
            is StringValue -> value.value
            else -> throw IllegalArgumentException(
                "$functionName() expects a string, got ${value.toDisplayString()}"
            )
        }
    }
}
