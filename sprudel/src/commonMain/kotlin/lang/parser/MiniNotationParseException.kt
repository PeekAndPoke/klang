package io.peekandpoke.klang.sprudel.lang.parser

import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.common.SourceLocationAware

/**
 * Exception thrown when mini-notation parsing fails
 *
 * Implements [SourceLocationAware] so the editor can extract the absolute source location
 * without regex parsing.
 *
 * @param message The error message
 * @param startPosition Start position within the mini-notation string (0-based), e.g. the opening bracket
 * @param endPosition End position within the mini-notation string (0-based), where the error was detected
 * @param baseLocation The source location of the mini-notation string in the overall code
 */
class MiniNotationParseException(
    message: String,
    val startPosition: Int,
    val endPosition: Int = startPosition,
    val baseLocation: SourceLocation?,
) : Exception(buildMessage(message, endPosition, baseLocation)), SourceLocationAware {

    /** Absolute source location computed from [baseLocation] + positions, offset by 1 for the opening quote. */
    override val location: SourceLocation? = baseLocation?.let {
        val quoteOffset = 1  // skip the opening quote character
        val startCol = it.startColumn + quoteOffset + startPosition
        val endCol = it.startColumn + quoteOffset + endPosition
        SourceLocation(it.source, it.startLine, startCol, it.startLine, endCol)
    }

    companion object {
        private fun buildMessage(message: String, position: Int, baseLocation: SourceLocation?): String {
            return if (baseLocation != null) {
                val actualLine = baseLocation.startLine
                val actualCol = baseLocation.startColumn + 1 + position  // +1 for opening quote
                "Parse error at $actualLine:$actualCol: $message"
            } else {
                "Parse error at position $position: $message"
            }
        }
    }
}
