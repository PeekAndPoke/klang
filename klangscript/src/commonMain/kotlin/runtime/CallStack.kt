package io.peekandpoke.klang.script.runtime

import io.peekandpoke.klang.script.ast.SourceLocation

/**
 * Represents a single frame in the call stack
 *
 * Tracks information about a function call for debugging and error reporting.
 * Used to build JavaScript-style stack traces.
 *
 * @property functionName The name of the function being called (or "<anonymous>" for arrow functions)
 * @property location The source location where this function was called from
 */
data class CallStackFrame(
    val functionName: String,
    val location: SourceLocation?,
) {
    /**
     * Format this stack frame as a string for display in stack traces
     *
     * Format: "  at functionName (source:line:column)"
     *
     * Examples:
     * - "  at add (math.klang:5:12)"
     * - "  at <anonymous> (script.klang:10:5)"
     * - "  at calculate (line 15, column 3)"
     * - "  at process"
     */
    fun format(): String {
        return if (location != null) {
            "  at $functionName ($location)"
        } else {
            "  at $functionName"
        }
    }
}

/**
 * Manages the call stack for the interpreter
 *
 * Tracks function calls during execution to provide meaningful stack traces
 * when errors occur. Implements stack overflow protection.
 *
 * Example usage:
 * ```kotlin
 * val callStack = CallStack()
 * callStack.push("add", location)
 * try {
 *     // ... execute function
 * } finally {
 *     callStack.pop()
 * }
 * ```
 */
class CallStack(
    private val maxDepth: Int = 1000,
) {
    private val frames = mutableListOf<CallStackFrame>()

    /**
     * Push a new frame onto the call stack
     *
     * @param functionName The name of the function being called
     * @param location The source location where the call occurred
     * @throws StackOverflowError if stack depth exceeds maxDepth
     */
    fun push(functionName: String, location: SourceLocation?) {
        if (frames.size >= maxDepth) {
            throw StackOverflowError(
                "Stack overflow: maximum call depth of $maxDepth exceeded. " +
                        "Possible infinite recursion in function '$functionName'"
            )
        }
        frames.add(CallStackFrame(functionName, location))
    }

    /**
     * Pop the most recent frame from the call stack
     *
     * Should always be called in a finally block to ensure stack consistency
     * even when exceptions occur.
     */
    fun pop() {
        if (frames.isNotEmpty()) {
            frames.removeLast()
        }
    }

    /**
     * Get a snapshot of the current call stack
     *
     * Returns a copy of the frames list so modifications to the original
     * stack don't affect the snapshot.
     *
     * @return List of stack frames, most recent call first
     */
    fun getFrames(): List<CallStackFrame> {
        return frames.toList().reversed() // Most recent first
    }

    /**
     * Get the current stack depth
     */
    fun depth(): Int = frames.size

    /**
     * Clear the entire call stack
     *
     * Useful for resetting between script executions.
     */
    fun clear() {
        frames.clear()
    }

    /**
     * Format the entire call stack as a multi-line string
     *
     * Format matches JavaScript/Java style:
     * ```
     *   at functionName (source:line:column)
     *   at anotherFunction (source:line:column)
     *   at <top-level> (source:line:column)
     * ```
     */
    fun format(): String {
        return frames.reversed().joinToString("\n") { it.format() }
    }
}
