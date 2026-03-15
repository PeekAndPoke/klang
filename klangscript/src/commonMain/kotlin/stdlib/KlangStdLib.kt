package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.script.KlangScriptLibrary
import io.peekandpoke.klang.script.builder.registerType
import io.peekandpoke.klang.script.builder.registerVarargFunction
import io.peekandpoke.klang.script.klangScriptLibrary
import io.peekandpoke.klang.script.runtime.*
import io.peekandpoke.klang.script.stdlib.KlangStdLib.defaultOutputHandler
import kotlin.math.*

/**
 * KlangScript Standard Library
 *
 * Provides commonly-used built-in functions for KlangScript programs.
 *
 * **Usage**:
 * ```kotlin
 * val engine = klangScript {
 *     registerLibrary(KlangStdLib.create())
 * }
 * ```
 */
object KlangStdLib {

    /** Default output handler — prints to stdout. */
    val defaultOutputHandler: (List<Any?>) -> Unit = {
        println(it.joinToString(" "))
    }

    /** Math object — singleton for holding math operations (like JavaScript's Math). */
    object MathObject {
        override fun toString(): String = "[Math object]"

        fun KlangScriptLibrary.Builder.register() {
            registerObject("Math", MathObject) {
                registerMethod(name = "sqrt") { x: Double -> sqrt(x) }
                registerMethod(name = "abs") { x: Double -> abs(x) }
                registerMethod(name = "floor") { x: Double -> floor(x) }
                registerMethod(name = "ceil") { x: Double -> ceil(x) }
                registerMethod(name = "round") { x: Double -> round(x) }
                registerMethod(name = "sin") { x: Double -> sin(x) }
                registerMethod(name = "cos") { x: Double -> cos(x) }
                registerMethod(name = "tan") { x: Double -> tan(x) }
                registerMethod(name = "min") { a: Double, b: Double -> min(a, b) }
                registerMethod(name = "max") { a: Double, b: Double -> max(a, b) }
                registerMethod(name = "pow") { base: Double, exp: Double -> base.pow(exp) }
            }
        }
    }

    /** Object utility — singleton for holding Object static methods. */
    object ObjectUtility {
        override fun toString(): String = "[Object utility]"
    }

    /** Console object — singleton for holding console functions. */
    object ConsoleObject {
        override fun toString(): String = "[Console object]"
    }

    /**
     * Create the standard library.
     *
     * @param outputHandler Handler for `print()` and `console.log()` output.
     *   Defaults to [defaultOutputHandler] (stdout). Override to capture output in a REPL or tests.
     * @return A KlangScriptLibrary instance containing all standard functions
     */
    fun create(
        outputHandler: (List<Any?>) -> Unit = defaultOutputHandler,
    ): KlangScriptLibrary {
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

            // Register console object with log method
            registerObject("console", ConsoleObject) {
                registerVarargMethod("log") { args ->
                    outputHandler(args)
                }
            }

            // Register Math object
            with(MathObject) { register() }

            // Register Object utility methods
            registerObject("Object", ObjectUtility) {}

            registerExtensionMethod(ObjectUtility::class, "keys") { _, args, callLocation ->
                val obj = args[0] as? ObjectValue
                    ?: throw KlangScriptTypeError("Object.keys() expects an object argument", location = callLocation)
                val keys = obj.properties.keys.map { StringValue(it) }
                ArrayValue(keys.toMutableList())
            }
            registerExtensionMethod(ObjectUtility::class, "values") { _, args, callLocation ->
                val obj = args[0] as? ObjectValue
                    ?: throw KlangScriptTypeError("Object.values() expects an object argument", location = callLocation)
                ArrayValue(obj.properties.values.toMutableList())
            }
            registerExtensionMethod(ObjectUtility::class, "entries") { _, args, callLocation ->
                val obj = args[0] as? ObjectValue
                    ?: throw KlangScriptTypeError("Object.entries() expects an object argument", location = callLocation)
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
                    if (idx in value.indices) StringValue(value[idx].toString()) else StringValue("")
                }
                registerMethod("substring") { start: Double, end: Double ->
                    val s = start.toInt().coerceIn(0, value.length)
                    val e = end.toInt().coerceIn(0, value.length)
                    StringValue(value.substring(s, e))
                }
                registerMethod("indexOf") { searchStr: String ->
                    value.indexOf(searchStr).toDouble()
                }
                registerMethod("split") { separator: String ->
                    ArrayValue(value.split(separator).map { StringValue(it) }.toMutableList())
                }
                registerMethod("toUpperCase") { StringValue(value.uppercase()) }
                registerMethod("toLowerCase") { StringValue(value.lowercase()) }
                registerMethod("trim") { StringValue(value.trim()) }
                registerMethod("startsWith") { prefix: String ->
                    BooleanValue(value.startsWith(prefix))
                }
                registerMethod("endsWith") { suffix: String ->
                    BooleanValue(value.endsWith(suffix))
                }
                registerMethod("replace") { search: String, replacement: String ->
                    StringValue(value.replace(search, replacement))
                }
                registerMethod("slice") { start: Double, end: Double ->
                    val s = start.toInt().coerceIn(0, value.length)
                    val e = end.toInt().coerceIn(0, value.length)
                    StringValue(value.substring(s, e))
                }
                registerMethod("concat") { other: String ->
                    StringValue(value + other)
                }
                registerMethod("repeat") { count: Double ->
                    StringValue(value.repeat(count.toInt().coerceAtLeast(0)))
                }
                registerMethod("toString") { StringValue(value) }
            }

            // Register Number extensions
            registerType<NumberValue> {
                registerMethod("toString") { StringValue(toDisplayString()) }
            }

            // Register Boolean extensions
            registerType<BooleanValue> {
                registerMethod("toString") { StringValue(toDisplayString()) }
            }

            // Register Array extensions (Kotlin-style API)
            registerType<ArrayValue> {
                // Properties
                registerMethod("size") { elements.size.toDouble() }
                // Access
                registerMethod("first") { if (elements.isNotEmpty()) elements.first() else NullValue }
                registerMethod("last") { if (elements.isNotEmpty()) elements.last() else NullValue }
                // Mutating
                registerMethod("add") { item: Any -> elements.add(wrapAsRuntimeValue(item)); elements.size.toDouble() }
                registerMethod("removeAt") { index: Double ->
                    val idx = index.toInt()
                    if (idx in elements.indices) elements.removeAt(idx) else NullValue
                }
                registerMethod("removeLast") { if (elements.isNotEmpty()) elements.removeLast() else NullValue }
                registerMethod("removeFirst") { if (elements.isNotEmpty()) elements.removeFirst() else NullValue }
                // Non-mutating (return new arrays)
                registerMethod("reversed") { ArrayValue(elements.reversed().toMutableList()) }
                registerMethod("drop") { n: Double -> ArrayValue(elements.drop(n.toInt()).toMutableList()) }
                registerMethod("take") { n: Double -> ArrayValue(elements.take(n.toInt()).toMutableList()) }
                registerMethod("subList") { start: Double, end: Double ->
                    val s = start.toInt().coerceIn(0, elements.size)
                    val e = end.toInt().coerceIn(0, elements.size)
                    ArrayValue(elements.subList(s, e).toMutableList())
                }
                registerMethod("joinToString") { separator: String ->
                    StringValue(elements.joinToString(separator) { it.toDisplayString() })
                }
                registerMethod("indexOf") { item: Any ->
                    val wrapped = wrapAsRuntimeValue(item)
                    NumberValue(elements.indexOfFirst { it.value == wrapped.value }.toDouble())
                }
                registerMethod("contains") { item: Any ->
                    val wrapped = wrapAsRuntimeValue(item)
                    BooleanValue(elements.any { it.value == wrapped.value })
                }
                registerMethod("isEmpty") { BooleanValue(elements.isEmpty()) }
                registerMethod("isNotEmpty") { BooleanValue(elements.isNotEmpty()) }
            }

            // Output functions — use the captured outputHandler
            registerVarargFunction("print") { args ->
                outputHandler(args)
            }
        }
    }
}
