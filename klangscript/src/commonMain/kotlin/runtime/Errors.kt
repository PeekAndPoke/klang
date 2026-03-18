package io.peekandpoke.klang.script.runtime

import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.common.SourceLocationAware
import io.peekandpoke.klang.script.ast.AstNode

// ── Error Type Enum ──────────────────────────────────────────────────────────

/** Identifies the category of a KlangScript error. */
enum class KlangScriptErrorType {
    SyntaxError,
    TypeError,
    ReferenceError,
    ArgumentError,
    ImportError,
    AssignmentError,
    StackOverflowError,
    InternalError,
}

// ── Sealed Interface ─────────────────────────────────────────────────────────

/**
 * Base interface for all KlangScript errors (parse and runtime).
 *
 * Enables unified handling: `catch (e: Exception) { if (e is KlangScriptError) ... }`.
 * The sealed sub-hierarchies [KlangScriptParseError] and [KlangScriptRuntimeError]
 * separate concerns cleanly.
 */
sealed interface KlangScriptError : SourceLocationAware {
    val errorType: KlangScriptErrorType
    override val location: SourceLocation?

    /** Format the error for display (type, location, message, stack trace). */
    fun format(): String
}

// ── Parse Errors ─────────────────────────────────────────────────────────────

/**
 * Base class for parse-time errors.
 *
 * Parse errors occur before execution — no call stack or AST node is available.
 */
sealed class KlangScriptParseError(
    message: String,
    override val errorType: KlangScriptErrorType,
    override val location: SourceLocation? = null,
) : Exception(message), KlangScriptError {

    override fun format(): String {
        return if (location != null) {
            "${errorType.name} at $location: $message"
        } else {
            "${errorType.name}: $message"
        }
    }
}

/** Syntax error — invalid token, unterminated string, unexpected character, etc. */
class KlangScriptSyntaxError(
    message: String,
    location: SourceLocation? = null,
) : KlangScriptParseError(message, KlangScriptErrorType.SyntaxError, location)

// ── Runtime Errors ───────────────────────────────────────────────────────────

/**
 * Base class for runtime errors.
 *
 * Runtime errors carry an optional [astNode] (the AST node that triggered the error)
 * and a [callStackTrace] (the interpreter call stack at the time of the error).
 */
sealed class KlangScriptRuntimeError(
    message: String,
    override val errorType: KlangScriptErrorType,
    override val location: SourceLocation? = null,
    val astNode: AstNode? = null,
    val callStackTrace: List<CallStackFrame> = emptyList(),
    cause: Throwable? = null,
) : RuntimeException(message, cause), KlangScriptError {

    override fun format(): String {
        val header = if (location != null) {
            "${errorType.name} at $location: $message"
        } else {
            "${errorType.name}: $message"
        }

        return if (callStackTrace.isNotEmpty()) {
            header + "\n" + callStackTrace.joinToString("\n") { it.format() }
        } else {
            header
        }
    }
}

/**
 * TypeError — type mismatch or invalid operation.
 *
 * Examples: calling a non-function (`5()`), adding incompatible types, property access on non-object.
 */
class KlangScriptTypeError(
    message: String,
    val operation: String? = null,
    location: SourceLocation? = null,
    astNode: AstNode? = null,
    callStackTrace: List<CallStackFrame> = emptyList(),
) : KlangScriptRuntimeError(message, KlangScriptErrorType.TypeError, location, astNode, callStackTrace) {

    override fun format(): String {
        val prefix = if (location != null) "${errorType.name} at $location" else errorType.name
        val header = if (operation != null) {
            "$prefix in $operation: $message"
        } else {
            "$prefix: $message"
        }

        return if (callStackTrace.isNotEmpty()) {
            header + "\n" + callStackTrace.joinToString("\n") { it.format() }
        } else {
            header
        }
    }
}

/**
 * ReferenceError — variable or symbol not found.
 *
 * Examples: accessing undefined variable, accessing non-exported symbol.
 */
class KlangScriptReferenceError(
    val symbolName: String,
    message: String = "Undefined symbol: $symbolName",
    location: SourceLocation? = null,
    astNode: AstNode? = null,
    callStackTrace: List<CallStackFrame> = emptyList(),
) : KlangScriptRuntimeError(message, KlangScriptErrorType.ReferenceError, location, astNode, callStackTrace)

/**
 * ArgumentError — wrong number or type of arguments.
 *
 * Examples: wrong argument count, wrong argument type.
 */
class KlangScriptArgumentError(
    val functionName: String,
    message: String,
    val expected: Int? = null,
    val actual: Int? = null,
    location: SourceLocation? = null,
    astNode: AstNode? = null,
    callStackTrace: List<CallStackFrame> = emptyList(),
) : KlangScriptRuntimeError(message, KlangScriptErrorType.ArgumentError, location, astNode, callStackTrace) {

    override fun format(): String {
        val prefix = if (location != null) "${errorType.name} at $location" else errorType.name
        val header = if (expected != null && actual != null) {
            "$prefix in $functionName: Expected $expected arguments, got $actual"
        } else {
            "$prefix in $functionName: $message"
        }

        return if (callStackTrace.isNotEmpty()) {
            header + "\n" + callStackTrace.joinToString("\n") { it.format() }
        } else {
            header
        }
    }
}

/**
 * ImportError — library import failure.
 *
 * Examples: library not found, non-exported symbol, namespace conflict.
 */
class KlangScriptImportError(
    val libraryName: String?,
    message: String,
    location: SourceLocation? = null,
    astNode: AstNode? = null,
    callStackTrace: List<CallStackFrame> = emptyList(),
) : KlangScriptRuntimeError(message, KlangScriptErrorType.ImportError, location, astNode, callStackTrace) {

    override fun format(): String {
        val prefix = if (location != null) "${errorType.name} at $location" else errorType.name
        val header = if (libraryName != null) {
            "$prefix in library '$libraryName': $message"
        } else {
            "$prefix: $message"
        }

        return if (callStackTrace.isNotEmpty()) {
            header + "\n" + callStackTrace.joinToString("\n") { it.format() }
        } else {
            header
        }
    }
}

/**
 * AssignmentError — invalid assignment operation.
 *
 * Examples: reassigning a const variable.
 */
class KlangScriptAssignmentError(
    val variableName: String?,
    message: String,
    location: SourceLocation? = null,
    astNode: AstNode? = null,
    callStackTrace: List<CallStackFrame> = emptyList(),
) : KlangScriptRuntimeError(message, KlangScriptErrorType.AssignmentError, location, astNode, callStackTrace) {

    override fun format(): String {
        val prefix = if (location != null) "${errorType.name} at $location" else errorType.name
        val header = if (variableName != null) {
            "$prefix for variable '$variableName': $message"
        } else {
            "$prefix: $message"
        }

        return if (callStackTrace.isNotEmpty()) {
            header + "\n" + callStackTrace.joinToString("\n") { it.format() }
        } else {
            header
        }
    }
}

/**
 * StackOverflowError — call stack exceeded maximum depth.
 *
 * Examples: infinite recursion, deeply nested calls.
 */
class KlangScriptStackOverflowError(
    message: String,
    location: SourceLocation? = null,
    astNode: AstNode? = null,
    callStackTrace: List<CallStackFrame> = emptyList(),
) : KlangScriptRuntimeError(message, KlangScriptErrorType.StackOverflowError, location, astNode, callStackTrace)

/**
 * InternalError — unexpected failure in a native function call.
 *
 * Wraps exceptions thrown by Kotlin native functions with context about
 * which function was called and what arguments were passed.
 */
class KlangScriptInternalError(
    message: String,
    cause: Throwable? = null,
    location: SourceLocation? = null,
    astNode: AstNode? = null,
    callStackTrace: List<CallStackFrame> = emptyList(),
) : KlangScriptRuntimeError(
    message = message,
    errorType = KlangScriptErrorType.InternalError,
    location = location,
    astNode = astNode,
    callStackTrace = callStackTrace,
    cause = cause,
)

// ── Control Flow Exceptions (NOT errors) ─────────────────────────────────────

/**
 * ReturnException — control flow for return statements.
 * NOT an error. Carries the return value from the function.
 */
internal class ReturnException(
    val value: RuntimeValue,
) : Exception("Return statement")

/**
 * BreakException — control flow for break statements.
 * NOT an error.
 */
internal class BreakException : Exception("Break statement")

/**
 * ContinueException — control flow for continue statements.
 * NOT an error.
 */
internal class ContinueException : Exception("Continue statement")
