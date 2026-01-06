package io.peekandpoke.klang.script

import io.peekandpoke.klang.script.runtime.NativeFunctionValue
import io.peekandpoke.klang.script.runtime.RuntimeValue

/**
 * Test utilities for KlangScript tests
 *
 * Common helper functions used across multiple test files.
 */

/**
 * Helper extension for KlangScript to register variables in the environment
 *
 * This is useful for testing scenarios where you need to set up
 * pre-existing variables before executing a script.
 *
 * Example:
 * ```kotlin
 * val script = KlangScript()
 * script.registerVariable("x", NumberValue(42.0))
 * script.execute("x + 1") // Returns 43.0
 * ```
 */
fun KlangScript.registerVariable(name: String, value: RuntimeValue) {
    getInterpreter().getEnvironment().define(name, value)
}

/**
 * Helper extension to create native functions for testing
 *
 * Creates a NativeFunctionValue that can be stored in objects or
 * used in test scenarios.
 *
 * Example:
 * ```kotlin
 * val script = KlangScript()
 * val func = script.createNativeFunction("double") { args ->
 *     val num = (args[0] as NumberValue).value
 *     NumberValue(num * 2)
 * }
 * ```
 */
fun KlangScript.createNativeFunction(
    name: String,
    function: (List<RuntimeValue>) -> RuntimeValue,
): NativeFunctionValue {
    return NativeFunctionValue(name, function)
}
