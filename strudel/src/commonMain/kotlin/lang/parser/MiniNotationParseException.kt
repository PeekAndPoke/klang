package io.peekandpoke.klang.strudel.lang.parser

import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.script.ast.SourceLocationAware

/**
 * Exception thrown when mini-notation parsing fails
 *
 * Implements [SourceLocationAware] so the editor can extract the absolute source location
 * without regex parsing.
 *
 * @param message The error message
 * @param position Position within the mini-notation string (0-based)
 * @param baseLocation The source location of the mini-notation string in the overall code
 */
class MiniNotationParseException(
    message: String,
    val position: Int,
    val baseLocation: SourceLocation?,
) : Exception(buildMessage(message, position, baseLocation)), SourceLocationAware {

    /** Absolute source location computed from [baseLocation] + [position]. */
    override val location: SourceLocation? = baseLocation?.let {
        val actualLine = it.startLine
        val actualCol = it.startColumn + position
        SourceLocation(it.source, actualLine, actualCol, actualLine, actualCol + 1)
    }

    companion object {
        private fun buildMessage(message: String, position: Int, baseLocation: SourceLocation?): String {
            return if (baseLocation != null) {
                val actualLine = baseLocation.startLine
                val actualCol = baseLocation.startColumn + position
                "Parse error at $actualLine:$actualCol: $message"
            } else {
                "Parse error at position $position: $message"
            }
        }
    }
}
