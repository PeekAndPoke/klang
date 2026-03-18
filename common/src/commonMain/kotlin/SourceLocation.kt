package io.peekandpoke.klang.common

/**
 * Source location information for error reporting.
 *
 * Tracks the position of a code element in the source file using start and end positions.
 * This naturally handles both single-line and multiline code spans.
 *
 * @param source Source file or library name (e.g., "main.klang", "math.klang", or null for main script)
 * @param startLine Starting line number (1-based)
 * @param startColumn Starting column number (1-based)
 * @param endLine Ending line number (1-based)
 * @param endColumn Ending column number (1-based, exclusive - points to character after the last character)
 */
data class SourceLocation(
    val source: String?,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
) {
    /** Returns true when the code location is valid (spans at least one character) */
    fun isValid(): Boolean {
        return startLine != endLine || startColumn != endColumn
    }

    /** Wrap this location in a single-element [SourceLocationChain]. */
    fun asChain() = SourceLocationChain.single(this)

    override fun toString(): String {
        val range = if (startLine == endLine) {
            "$startLine:$startColumn-$endColumn"
        } else {
            "$startLine:$startColumn-$endLine:$endColumn"
        }

        return if (source != null) {
            "$source:$range"
        } else {
            range
        }
    }
}

/** Marker interface for anything that carries a [SourceLocation]. */
interface SourceLocationAware {
    val location: SourceLocation?
}
