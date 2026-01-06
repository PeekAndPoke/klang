package io.peekandpoke.klang.script.runtime

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
sealed class RuntimeValue {
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
data class NumberValue(val value: Double) : RuntimeValue() {
    override fun toDisplayString(): String = value.toString()
}

/**
 * String value
 *
 * Immutable text values.
 *
 * Examples: "hello", 'world'
 */
data class StringValue(val value: String) : RuntimeValue() {
    override fun toDisplayString(): String = value
}

/**
 * Null value
 *
 * Represents the absence of a value.
 * KlangScript has no 'undefined' - only 'null'.
 */
data object NullValue : RuntimeValue() {
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
) : RuntimeValue() {
    override fun toDisplayString(): String = "[native function $name]"
}
