package io.peekandpoke.klang.script.parser

/**
 * Exception thrown when parsing fails
 *
 * This replaces the better-parse ParseException to remove the dependency.
 */
class ParseException(
    val errorResult: ErrorResult,
) : Exception(errorResult.toString())

/**
 * Error result containing parse error details
 *
 * This replaces the better-parse ErrorResult to remove the dependency.
 */
data class ErrorResult(
    val message: String,
    val column: Int,
    val line: Int,
) {
    override fun toString(): String = "Parse error at $line:$column: $message"
}
