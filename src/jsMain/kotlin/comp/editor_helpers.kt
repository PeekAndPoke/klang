package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.klang.common.SourceLocationAware
import io.peekandpoke.klang.script.runtime.KlangScriptError
import io.peekandpoke.klang.ui.codemirror.EditorError
import io.peekandpoke.klang.ui.codemirror.KlangScriptEditorComp

/**
 * Maps a Throwable into an EditorError for display in the CodeMirror editor.
 *
 * If the error is a [KlangScriptError] with a [SourceLocation], the location is used directly.
 * Otherwise, falls back to regex parsing of the error message.
 */
fun mapToEditorError(e: Throwable): EditorError {
    // Use structured location if available (KlangScriptError, MiniNotationParseException, etc.)
    if (e is SourceLocationAware) {
        val loc = e.location
        if (loc != null) {
            val len = if (loc.startLine == loc.endLine) {
                (loc.endColumn - loc.startColumn).coerceAtLeast(1)
            } else {
                1
            }
            val prefix = if (e is KlangScriptError) "${e.errorType.name}: " else ""
            return EditorError(
                message = "$prefix${e.message}",
                line = loc.startLine,
                col = loc.startColumn,
                len = len,
            )
        }
    }

    // KlangScriptError without location
    if (e is KlangScriptError) {
        return EditorError(
            message = "${e.errorType.name}: ${e.message}",
            line = 1,
            col = 1,
            len = 1,
        )
    }

    // Fallback: parse location from error message (for non-KlangScript exceptions)
    val message = e.message ?: "Unknown error"

    val atLineColRegex = Regex("at\\s+(\\d+):(\\d+)", RegexOption.IGNORE_CASE)
    val atLineColMatch = atLineColRegex.find(message)

    val line: Int
    val col: Int

    if (atLineColMatch != null) {
        line = atLineColMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
        col = atLineColMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
    } else {
        line = 1
        col = 1
    }

    return EditorError(
        message = message,
        line = line,
        col = col,
        len = 1,
    )
}

/**
 * Clears editor errors, runs [block], and on failure maps the exception to an EditorError.
 */
suspend fun withEditorErrorHandling(
    editorRef: ComponentRef.Tracker<KlangScriptEditorComp>,
    block: suspend () -> Unit,
) {
    editorRef { it.setErrors(emptyList()) }
    try {
        block()
    } catch (e: Throwable) {
        console.error("Editor error:", e)
        editorRef { it.setErrors(listOf(mapToEditorError(e))) }
    }
}
