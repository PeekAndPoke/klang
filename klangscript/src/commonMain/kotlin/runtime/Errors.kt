package io.peekandpoke.klang.script.runtime

import io.peekandpoke.klang.script.ast.SourceLocation

/**
 * Base class for all KlangScript runtime errors
 *
 * Provides structured error information including error type, message,
 * optional source location, and call stack trace for debugging.
 */
sealed class KlangScriptError(
    message: String,
    val errorType: String,
    val location: SourceLocation? = null,
    val stackTrace: List<CallStackFrame> = emptyList(),
) : RuntimeException(message) {

    /**
     * Format the error message for display
     *
     * Returns a formatted error message including the error type, message,
     * and stack trace (if available).
     * Subclasses can override to add additional context.
     */
    open fun format(): String {
        val header = if (location != null) {
            "$errorType at $location: $message"
        } else {
            "$errorType: $message"
        }

        return if (stackTrace.isNotEmpty()) {
            header + "\n" + stackTrace.joinToString("\n") { it.format() }
        } else {
            header
        }
    }
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
    location: SourceLocation? = null,
    stackTrace: List<CallStackFrame> = emptyList(),
) : KlangScriptError(message, "ReferenceError", location, stackTrace)

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
    location: SourceLocation? = null,
    stackTrace: List<CallStackFrame> = emptyList(),
) : KlangScriptError(message, "TypeError", location, stackTrace) {

    override fun format(): String {
        val prefix = if (location != null) "$errorType at $location" else errorType
        val header = if (operation != null) {
            "$prefix in $operation: $message"
        } else if (location != null) {
            "$prefix: $message"
        } else {
            "$errorType: $message"
        }

        return if (stackTrace.isNotEmpty()) {
            header + "\n" + stackTrace.joinToString("\n") { it.format() }
        } else {
            header
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
    location: SourceLocation? = null,
    stackTrace: List<CallStackFrame> = emptyList(),
) : KlangScriptError(message, "ArgumentError", location, stackTrace) {

    override fun format(): String {
        val prefix = if (location != null) "$errorType at $location" else errorType
        val header = if (expected != null && actual != null) {
            "$prefix in $functionName: Expected $expected arguments, got $actual"
        } else {
            "$prefix in $functionName: $message"
        }

        return if (stackTrace.isNotEmpty()) {
            header + "\n" + stackTrace.joinToString("\n") { it.format() }
        } else {
            header
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
    location: SourceLocation? = null,
    stackTrace: List<CallStackFrame> = emptyList(),
) : KlangScriptError(message, "ImportError", location, stackTrace) {

    override fun format(): String {
        val prefix = if (location != null) "$errorType at $location" else errorType
        val header = if (libraryName != null) {
            "$prefix in library '$libraryName': $message"
        } else if (location != null) {
            "$prefix: $message"
        } else {
            "$errorType: $message"
        }

        return if (stackTrace.isNotEmpty()) {
            header + "\n" + stackTrace.joinToString("\n") { it.format() }
        } else {
            header
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
    location: SourceLocation? = null,
    stackTrace: List<CallStackFrame> = emptyList(),
) : KlangScriptError(message, "AssignmentError", location, stackTrace) {

    override fun format(): String {
        val prefix = if (location != null) "$errorType at $location" else errorType
        val header = if (variableName != null) {
            "$prefix for variable '$variableName': $message"
        } else if (location != null) {
            "$prefix: $message"
        } else {
            "$errorType: $message"
        }

        return if (stackTrace.isNotEmpty()) {
            header + "\n" + stackTrace.joinToString("\n") { it.format() }
        } else {
            header
        }
    }
}

/**
 * StackOverflowError - Call stack exceeded maximum depth
 *
 * Thrown when the call stack grows beyond the configured limit (default 1000 frames).
 * This prevents infinite recursion from consuming all memory.
 *
 * Examples:
 * - Infinite recursion: `let f = () => f(); f()`
 * - Deeply nested calls exceeding limit
 */
class StackOverflowError(
    message: String,
    location: SourceLocation? = null,
    stackTrace: List<CallStackFrame> = emptyList(),
) : KlangScriptError(message, "StackOverflowError", location, stackTrace)
