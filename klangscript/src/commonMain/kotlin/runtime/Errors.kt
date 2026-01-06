package io.peekandpoke.klang.script.runtime

/**
 * Base class for all KlangScript runtime errors
 *
 * Provides structured error information including error type, message,
 * and optional source location information for debugging.
 */
sealed class KlangScriptError(
    message: String,
    val errorType: String,
) : RuntimeException(message) {

    /**
     * Format the error message for display
     *
     * Returns a formatted error message including the error type and message.
     * Subclasses can override to add additional context.
     */
    open fun format(): String = "$errorType: $message"
}

/**
 * ReferenceError - Variable or symbol not found
 *
 * Thrown when trying to access a variable that doesn't exist in any scope.
 * Similar to JavaScript's ReferenceError.
 *
 * Examples:
 * - Accessing undefined variable: `foo` when foo doesn't exist
 * - Accessing non-exported symbol from library
 */
class ReferenceError(
    val symbolName: String,
    message: String = "Undefined variable: $symbolName",
) : KlangScriptError(message, "ReferenceError")

/**
 * TypeError - Type mismatch or invalid operation
 *
 * Thrown when an operation is performed on incompatible types.
 * Similar to JavaScript's TypeError.
 *
 * Examples:
 * - Calling a non-function: `5()`
 * - Adding incompatible types: `"hello" + null`
 * - Accessing property on non-object: `5.foo`
 */
class TypeError(
    message: String,
    val operation: String? = null,
) : KlangScriptError(message, "TypeError") {

    override fun format(): String {
        return if (operation != null) {
            "$errorType in $operation: $message"
        } else {
            super.format()
        }
    }
}

/**
 * ArgumentError - Wrong number or type of arguments
 *
 * Thrown when a function is called with incorrect arguments.
 *
 * Examples:
 * - Wrong argument count: `add(1)` when add expects 2 arguments
 * - Wrong argument type: `parseInt("hello")`
 */
class ArgumentError(
    val functionName: String,
    message: String,
    val expected: Int? = null,
    val actual: Int? = null,
) : KlangScriptError(message, "ArgumentError") {

    override fun format(): String {
        return if (expected != null && actual != null) {
            "$errorType in $functionName: Expected $expected arguments, got $actual"
        } else {
            "$errorType in $functionName: $message"
        }
    }
}

/**
 * ImportError - Library import failure
 *
 * Thrown when importing a library fails for various reasons.
 *
 * Examples:
 * - Library not found: `import * from "missing"`
 * - Importing non-exported symbol: `import { foo } from "lib"` when foo not exported
 * - Invalid import syntax
 */
class ImportError(
    val libraryName: String?,
    message: String,
) : KlangScriptError(message, "ImportError") {

    override fun format(): String {
        return if (libraryName != null) {
            "$errorType in library '$libraryName': $message"
        } else {
            super.format()
        }
    }
}

/**
 * AssignmentError - Invalid assignment operation
 *
 * Thrown when trying to reassign a const variable or perform invalid assignments.
 *
 * Examples:
 * - Reassigning const: `const x = 1; x = 2`
 */
class AssignmentError(
    val variableName: String?,
    message: String,
) : KlangScriptError(message, "AssignmentError") {

    override fun format(): String {
        return if (variableName != null) {
            "$errorType for variable '$variableName': $message"
        } else {
            super.format()
        }
    }
}
