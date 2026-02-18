package io.peekandpoke.klang.strudel.lang.parser

import io.peekandpoke.klang.script.ast.SourceLocation

/**
 * Exception thrown when mini-notation parsing fails
 *
 * @param message The error message
 * @param position Position within the mini-notation string (0-based)
 * @param baseLocation The source location of the mini-notation string in the overall code
 */
class MiniNotationParseException(
    message: String,
    val position: Int,
    val baseLocation: SourceLocation?,
) : Exception(buildMessage(message, position, baseLocation)) {

    companion object {
        private fun buildMessage(message: String, position: Int, baseLocation: SourceLocation?): String {
            return if (baseLocation != null) {
                // Calculate actual position in source code
                // For simplicity, assume the mini-notation string is on a single line
                // and position is the character offset within that string
                val actualLine = baseLocation.startLine
                val actualCol = baseLocation.startColumn + position
                "Parse error at $actualLine:$actualCol: $message"
            } else {
                "Parse error at position $position: $message"
            }
        }
    }
}
