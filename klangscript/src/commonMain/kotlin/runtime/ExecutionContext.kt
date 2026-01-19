package io.peekandpoke.klang.script.runtime

import io.peekandpoke.klang.script.ast.SourceLocation

/**
 * Execution context for a KlangScript execution.
 *
 * Carries metadata about the current execution that native functions may need.
 * Each call to KlangScriptEngine.execute() creates a new ExecutionContext that
 * is passed through the interpreter and made available to native functions.
 *
 * This enables:
 * - Source location tracking for error reporting and code highlighting
 * - Multiple concurrent executions without interference (no global state)
 * - Future extensions like notebook cell tracking
 *
 * @param sourceName The name of the source file or module being executed (e.g., "main.klang", "user-script")
 * @param currentLocation The current call site location, updated during execution (mutable)
 */
data class ExecutionContext(
    /** Source file or module name */
    val sourceName: String?,
    /** Current call site location (updated during execution) */
    var currentLocation: SourceLocation? = null,
) {
    // Future extensions:
    // val notebookCell: String? = null,
    // val editorId: String? = null,
    // val debugMode: Boolean = false,
}