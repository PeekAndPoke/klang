package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.script.KlangScriptLibrary
import io.peekandpoke.klang.script.builder.registerType
import io.peekandpoke.klang.script.builder.registerVarargFunction
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
    var outputHandler: (List<Any?>) -> Unit = {
        println(it.joinToString(", "))
    }

    /**
     * Math object - singleton for holding math operations (like JavaScript's Math)
     */
    object MathObject {
        override fun toString(): String = "[Math object]"

        fun KlangScriptLibrary.Builder.register() {
            registerObject("Math", MathObject) {
                // Math object methods - Single parameter
                registerMethod(name = "sqrt") { x: Double -> sqrt(x) }
                registerMethod(name = "abs") { x: Double -> abs(x) }
                registerMethod(name = "floor") { x: Double -> floor(x) }
                registerMethod(name = "ceil") { x: Double -> ceil(x) }
                registerMethod(name = "round") { x: Double -> round(x) }
                registerMethod(name = "sin") { x: Double -> sin(x) }
                registerMethod(name = "cos") { x: Double -> cos(x) }
                registerMethod(name = "tan") { x: Double -> tan(x) }

                // Math object methods - Two parameters
                registerMethod(name = "min") { a: Double, b: Double -> min(a, b) }
                registerMethod(name = "max") { a: Double, b: Double -> max(a, b) }
                registerMethod(name = "pow") { base: Double, exp: Double -> base.pow(exp) }
            }
        }
    }

    /**
     * Object utility object - singleton for holding Object static methods (like JavaScript's Object)
     */
    object ObjectUtility {
        override fun toString(): String = "[Object utility]"
    }

    /**
     * Console object - singleton for holding console functions (like JavaScript's console)
     */
    object ConsoleObject {
        override fun toString(): String = "[Console object]"

        fun KlangScriptLibrary.Builder.register() {
            registerObject("console", ConsoleObject) {
                registerVarargMethod("log") { args ->
                    outputHandler(args)
                }
            }
        }

        /**
         * Implementation of print() and console_log()
         *
         * Prints all arguments separated by spaces
         */
        fun printImpl(args: List<Any?>): RuntimeValue {
            outputHandler(args)
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
                export {
                    console,
                    Math,
                    Object
                }
                """.trimIndent()
            )

            // Register console functions
            with(ConsoleObject) { register() }

            // Register Math functions
            with(MathObject) { register() }

            // Register Object utility functions
            registerObject("Object", ObjectUtility) {
                // Object methods need RuntimeValue parameters, so we register them manually
            }

            // Object utility methods (need RuntimeValue parameters)
            registerExtensionMethod(ObjectUtility::class, "keys") { _, args ->
                val obj = args[0] as? ObjectValue
                    ?: throw IllegalArgumentException("Object.keys() expects an object argument")
                val keys = obj.properties.keys.map { StringValue(it) }
                ArrayValue(keys.toMutableList())
            }
            registerExtensionMethod(ObjectUtility::class, "values") { _, args ->
                val obj = args[0] as? ObjectValue
                    ?: throw IllegalArgumentException("Object.values() expects an object argument")
                ArrayValue(obj.properties.values.toMutableList())
            }
            registerExtensionMethod(ObjectUtility::class, "entries") { _, args ->
                val obj = args[0] as? ObjectValue
                    ?: throw IllegalArgumentException("Object.entries() expects an object argument")
                val entries = obj.properties.map { (key, value) ->
                    ArrayValue(mutableListOf(StringValue(key), value))
                }
                ArrayValue(entries.toMutableList())
            }

            // Register String extensions
            registerType<StringValue> {
                registerMethod("length") { value.length.toDouble() }
                registerMethod("charAt") { index: Double ->
                    val idx = index.toInt()
                    if (idx in value.indices) value[idx].toString() else ""
                }
                registerMethod("substring") { start: Double, end: Double ->
                    val startIdx = start.toInt().coerceAtLeast(0)
                    val endIdx = end.toInt().coerceAtMost(value.length)
                    value.substring(startIdx, endIdx)
                }
                registerMethod("indexOf") { searchStr: String ->
                    value.indexOf(searchStr).toDouble()
                }
                registerMethod("split") { separator: String ->
                    val parts = value.split(separator).map { StringValue(it) }
                    ArrayValue(parts.toMutableList())
                }
                registerMethod("toUpperCase") { value.uppercase() }
                registerMethod("toLowerCase") { value.lowercase() }
                registerMethod("trim") { value.trim() }
                registerMethod("startsWith") { prefix: String ->
                    value.startsWith(prefix)
                }
                registerMethod("endsWith") { suffix: String ->
                    value.endsWith(suffix)
                }
                registerMethod("replace") { search: String, replacement: String ->
                    value.replace(search, replacement)
                }
                registerMethod("slice") { start: Double, end: Double ->
                    val startIdx = start.toInt().coerceAtLeast(0)
                    val endIdx = end.toInt().coerceAtMost(value.length)
                    value.substring(startIdx, endIdx)
                }
                registerMethod("concat") { other: String ->
                    value + other
                }
                registerMethod("repeat") { count: Double ->
                    value.repeat(count.toInt().coerceAtLeast(0))
                }
            }

            // Register Array extensions
            registerType<ArrayValue> {
                // Property-like methods
                registerMethod("length") { elements.size.toDouble() }

                // Mutating methods
                registerVarargMethod("push") { items: List<RuntimeValue> ->
                    elements.addAll(items)
                    elements.size.toDouble()
                }
                registerMethod("pop") {
                    if (elements.isEmpty()) NullValue else elements.removeLast()
                }
                registerMethod("shift") {
                    if (elements.isEmpty()) NullValue else elements.removeFirst()
                }
                registerVarargMethod("unshift") { items: List<RuntimeValue> ->
                    elements.addAll(0, items)
                    elements.size.toDouble()
                }

                // Non-mutating methods
                registerMethod("slice") { start: Double, end: Double ->
                    val startIdx = start.toInt().coerceAtLeast(0)
                    val endIdx = end.toInt().coerceAtMost(elements.size)
                    ArrayValue(elements.subList(startIdx, endIdx).toMutableList())
                }
                registerMethod("reverse") {
                    ArrayValue(elements.reversed().toMutableList())
                }
            }

            // Array methods that need RuntimeValue parameters (registerFunctionRaw style)
            // These can't use type-safe registerMethod because they need to accept any RuntimeValue
            registerExtensionMethod(ArrayValue::class, "concat") { arr, args ->
                val other = args[0] as? ArrayValue
                    ?: throw IllegalArgumentException("concat() expects an array argument")
                ArrayValue((arr.elements + other.elements).toMutableList())
            }
            registerExtensionMethod(ArrayValue::class, "join") { arr, args ->
                val sep = (args[0] as? StringValue)?.value ?: ", "
                StringValue(arr.elements.joinToString(sep) { it.toDisplayString() })
            }
            registerExtensionMethod(ArrayValue::class, "indexOf") { arr, args ->
                val searchValue = args[0]
                NumberValue(arr.elements.indexOfFirst { it.value == searchValue.value }.toDouble())
            }
            registerExtensionMethod(ArrayValue::class, "includes") { arr, args ->
                val searchValue = args[0]
                BooleanValue(arr.elements.any { it.value == searchValue.value })
            }

            // Note: Higher-order methods (map, filter, forEach, find, some, every)
            // require callback execution which needs interpreter context.
            // These will be implemented in a future update with proper callback support.

            // Output functions
            registerVarargFunction("print") { args ->
                printImpl(args)
            }

            // String Functions (kept as global functions)
            registerFunctionRaw("length") { args ->
                requireExactly(args, 1, "length")
                val str = toString(args[0], "length")
                NumberValue(str.length.toDouble())
            }
            registerFunctionRaw("toUpperCase") { args ->
                requireExactly(args, 1, "toUpperCase")
                val str = toString(args[0], "toUpperCase")
                StringValue(str.uppercase())
            }
            registerFunctionRaw("toLowerCase") { args ->
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
