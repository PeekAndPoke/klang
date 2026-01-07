package io.peekandpoke.klang.script.runtime

import io.peekandpoke.klang.script.ast.Expression
import io.peekandpoke.klang.script.getUniqueClassName
import kotlin.reflect.KClass

/**
 * Runtime value types for KlangScript
 *
 * These classes represent values during script execution.
 * Every expression evaluation produces a RuntimeValue.
 *
 * The type system is kept simple intentionally:
 * - All numbers are doubles (like JavaScript)
 * - Only null (no undefined)
 * - Functions are first-class values
 */

/**
 * Base class for all runtime values
 *
 * All values in the KlangScript runtime inherit from this sealed class.
 * This allows exhaustive when expressions and type-safe pattern matching.
 */
sealed interface RuntimeValue {
    val value: Any?

    /**
     * Convert this value to a human-readable string
     * Used for debugging and console output
     */
    abstract fun toDisplayString(): String
}

/**
 * Numeric value
 *
 * All numbers in KlangScript are stored as doubles, matching JavaScript semantics.
 * This simplifies the type system and avoids int/float conversion issues.
 *
 * Examples: 42, 3.14, -0.5
 */
data class NumberValue(override val value: Double) : RuntimeValue {
    override fun toDisplayString(): String = value.toString()
}

/**
 * String value
 *
 * Immutable text values.
 *
 * Examples: "hello", 'world'
 */
data class StringValue(override val value: String) : RuntimeValue {
    override fun toDisplayString(): String = value
}

/**
 * Boolean value
 *
 * Represents true or false values.
 * Used for logical operations and conditionals.
 *
 * Examples: true, false
 */
data class BooleanValue(override val value: Boolean) : RuntimeValue {
    override fun toDisplayString(): String = value.toString()
}

/**
 * Null value
 *
 * Represents the absence of a value.
 * KlangScript has no 'undefined' - only 'null'.
 */
data object NullValue : RuntimeValue {
    override val value: Nothing? = null

    override fun toDisplayString(): String = "null"
}

/**
 * Native function value
 *
 * Represents a function implemented in Kotlin that can be called from scripts.
 * These are registered with the engine and become available in the script environment.
 *
 * The function receives a list of runtime values as arguments and returns a runtime value.
 *
 * Example registration:
 * ```kotlin
 * engine.registerFunction("print") { args ->
 *     println(args[0].toDisplayString())
 *     NullValue
 * }
 * ```
 */
data class NativeFunctionValue(
    val name: String,
    val function: (List<RuntimeValue>) -> RuntimeValue,
) : RuntimeValue {
    override val value = null

    override fun toDisplayString(): String = "[native function $name]"
}

/**
 * Script function value (arrow functions)
 *
 * Represents a function defined in KlangScript code using arrow function syntax.
 * Script functions are first-class values that capture their lexical environment (closures).
 *
 * **Closure semantics:**
 * When a function is created, it captures the environment where it was defined.
 * This allows the function to access variables from outer scopes even after
 * those scopes have exited.
 *
 * Example:
 * ```javascript
 * let x = 10
 * let addX = y => x + y
 * addX(5)  // Returns 15, accessing captured 'x'
 * ```
 *
 * **Usage in callbacks:**
 * ```javascript
 * note("a b c").superImpose(x => x.detune(0.5))
 * ```
 *
 * @param parameters List of parameter names
 * @param body Expression to evaluate when function is called
 * @param closureEnv The environment captured at function definition time
 */
data class FunctionValue(
    val parameters: List<String>,
    val body: Expression,
    val closureEnv: Environment,
) : RuntimeValue {
    override val value = null

    override fun toDisplayString(): String = "[function(${parameters.joinToString(", ")})]"
}

/**
 * Object value
 *
 * Represents an object with properties that can be accessed via member access (dot notation).
 * Properties are stored in a mutable map, allowing dynamic property assignment.
 *
 * This is essential for method chaining patterns where functions return objects
 * that have further methods:
 *
 * ```
 * note("c d e f")     // Returns ObjectValue
 *   .gain(0.5)        // Accesses "gain" property (a function), calls it, returns ObjectValue
 *   .pan("0 1")       // Accesses "pan" property (a function), calls it, returns ObjectValue
 * ```
 *
 * Key design decisions:
 * - Properties can be any RuntimeValue (functions, numbers, strings, other objects)
 * - Mutable map allows runtime property modification
 * - Property access returns NullValue for missing properties (like JavaScript)
 *
 * Example usage:
 * ```kotlin
 * val obj = ObjectValue(mutableMapOf(
 *     "name" to StringValue("Alice"),
 *     "age" to NumberValue(30.0),
 *     "greet" to NativeFunctionValue("greet") { ... }
 * ))
 * ```
 */
data class ObjectValue(
    val properties: MutableMap<String, RuntimeValue> = mutableMapOf(),
) : RuntimeValue {
    override val value: Map<String, Any?> by lazy {
        properties.mapValues { it.value.value }
    }

    override fun toDisplayString(): String = "[object]"

    /**
     * Get a property by name
     * Returns NullValue if the property doesn't exist
     */
    fun getProperty(name: String): RuntimeValue {
        return properties[name] ?: NullValue
    }

    /**
     * Set a property by name
     */
    fun setProperty(name: String, value: RuntimeValue) {
        properties[name] = value
    }
}

/**
 * Native Kotlin object value
 *
 * Wraps a native Kotlin object to make it accessible from KlangScript.
 * The object's methods are registered separately as extension methods in the interpreter.
 *
 * @property kClass The Kotlin class of the wrapped object (used for registry lookup)
 * @property qualifiedName The fully qualified class name (for display and debugging)
 * @property value The actual Kotlin object instance
 *
 * Example:
 * ```kotlin
 * // Kotlin side:
 * class StrudelPattern(val pattern: String) {
 *     fun sound(name: String): StrudelPattern = ...
 * }
 *
 * // Wrapped as:
 * NativeObjectValue(
 *     kClass = StrudelPattern::class,
 *     qualifiedName = "com.example.StrudelPattern",
 *     value = StrudelPattern("a b c d")
 * )
 * ```
 */
data class NativeObjectValue<T : Any>(
    val kClass: KClass<out T>,
    val qualifiedName: String = kClass.getUniqueClassName(),
    override val value: T,
) : RuntimeValue {
    companion object {
        fun <T : Any> fromValue(value: T): NativeObjectValue<T> =
            NativeObjectValue(kClass = value::class, value = value)
    }

    override fun toDisplayString(): String = value.toString()
}

/**
 * Array value
 *
 * Represents a JavaScript-style array (ordered collection) in KlangScript.
 * Arrays can contain any mix of runtime value types and can be nested.
 *
 * **Key characteristics:**
 * - Zero-based indexing: first element at index 0
 * - Mutable: elements can be added, removed, or changed
 * - Dynamic sizing: arrays grow/shrink as needed
 * - Mixed types: `[1, "hello", true, null, { a: 1 }]` is valid
 * - Nested arrays: `[[1, 2], [3, 4]]` is valid
 *
 * **Usage patterns:**
 * ```javascript
 * // Creating arrays
 * let numbers = [1, 2, 3]
 * let mixed = [42, "hello", true, null]
 * let nested = [[1, 2], [3, 4]]
 *
 * // Arrays as arguments
 * print([1, 2, 3])
 *
 * // Arrays in objects
 * let data = { items: [1, 2, 3], names: ["a", "b"] }
 * ```
 *
 * **Future enhancements:**
 * - Index access: `arr[0]`, `arr[1]`
 * - Array methods: `push()`, `pop()`, `map()`, `filter()`, etc.
 * - Length property: `arr.length`
 *
 * @property elements Mutable list of runtime values (allows dynamic array operations)
 *
 * Example:
 * ```kotlin
 * // Simple array
 * ArrayValue(mutableListOf(NumberValue(1.0), NumberValue(2.0), NumberValue(3.0)))
 *
 * // Mixed types
 * ArrayValue(mutableListOf(
 *     NumberValue(42.0),
 *     StringValue("hello"),
 *     BooleanValue(true),
 *     NullValue
 * ))
 *
 * // Nested arrays
 * ArrayValue(mutableListOf(
 *     ArrayValue(mutableListOf(NumberValue(1.0), NumberValue(2.0))),
 *     ArrayValue(mutableListOf(NumberValue(3.0), NumberValue(4.0)))
 * ))
 * ```
 */
data class ArrayValue(
    val elements: MutableList<RuntimeValue>,
) : RuntimeValue {
    override val value by lazy {
        elements.map { it.value }
    }

    override fun toDisplayString(): String {
        return "[${elements.joinToString(", ") { it.toDisplayString() }}]"
    }
}

/**
 * Bound native method
 *
 * Represents an extension method bound to a specific native object receiver.
 * Created when accessing a method on a NativeObjectValue (e.g., `pattern.sound`).
 * Can be called like a function, which invokes the method with the bound receiver.
 *
 * @property methodName The name of the method
 * @property receiver The native object this method is bound to
 * @property invoker Function that invokes the extension method with arguments
 *
 * Example:
 * ```kotlin
 * // When evaluating: pattern.sound
 * // Returns:
 * BoundNativeMethod(
 *     methodName = "sound",
 *     receiver = NativeObjectValue(...),
 *     invoker = { args -> /* calls sound method */ }
 * )
 * ```
 */
data class BoundNativeMethod(
    val methodName: String,
    val receiver: NativeObjectValue<*>,
    val invoker: (List<RuntimeValue>) -> RuntimeValue,
) : RuntimeValue {
    override val value = null

    override fun toDisplayString(): String = "[bound method $methodName on ${receiver.qualifiedName}]"
}
