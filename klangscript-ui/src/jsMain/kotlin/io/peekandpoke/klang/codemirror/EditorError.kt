package io.peekandpoke.klang.codemirror

/**
 * Represents an error in the code editor
 *
 * @param message The error message to display
 * @param line 1-based line number where the error occurred
 * @param col 1-based column number where the error starts
 * @param len Length of the error range in characters (default: 1)
 */
data class EditorError(
    val message: String,
    val line: Int,
    val col: Int,
    val len: Int = 1,
)
