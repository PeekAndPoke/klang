package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.klang.codemirror.CodeMirrorComp
import io.peekandpoke.klang.codemirror.EditorError

/**
 * Parses a Throwable into an EditorError, extracting line/column information from the exception message.
 */
fun mapToEditorError(e: Throwable): EditorError {
    val message = e.message ?: "Unknown error"

    // Try pattern: "at line:col:" (e.g., "Parse error at 14:3: Expected expression")
    val atLineColRegex = Regex("at\\s+(\\d+):(\\d+)", RegexOption.IGNORE_CASE)
    val atLineColMatch = atLineColRegex.find(message)

    val line: Int
    val col: Int

    if (atLineColMatch != null) {
        line = atLineColMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
        col = atLineColMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
    } else {
        // Fallback to separate line and column patterns
        val lineRegex = Regex("line[:\\s]+(\\d+)", RegexOption.IGNORE_CASE)
        val columnRegex = Regex("column[:\\s]+(\\d+)", RegexOption.IGNORE_CASE)

        line = lineRegex.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        col = columnRegex.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
    }

    // Extract a cleaner message (without the location prefix)
    val cleanMessage = message
        .replace(Regex("Parse error at \\d+:\\d+[:\\s]*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("at line \\d+(, column \\d+)?[:\\s]*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("line \\d+[:\\s]*", RegexOption.IGNORE_CASE), "")
        .trim()
        .takeIf { it.isNotEmpty() } ?: message

    return EditorError(
        message = "Line $line: $cleanMessage",
        line = line,
        col = col,
        len = 1
    )
}

/**
 * Clears editor errors, runs [block], and on failure maps the exception to an EditorError.
 */
suspend fun withEditorErrorHandling(
    editorRef: ComponentRef.Tracker<CodeMirrorComp>,
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
