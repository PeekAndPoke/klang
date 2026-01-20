package io.peekandpoke.klang.script.ast

/**
 * Debug information for function/method calls
 *
 * Provides source location tracking for:
 * - The function call itself
 * - Individual parameter locations (extracted from RuntimeValue instances)
 *
 * Used for live code highlighting and error reporting.
 */
data class CallInfo(
    /** Location of the function call expression */
    val callLocation: SourceLocation?,
    /** List of parameter locations (from StringValue, NumberValue, etc.) - indices match argument positions */
    val paramLocations: List<SourceLocation?>,
) {
    /**
     * Drop the first N parameters from the call info
     * Useful when doing operations like args.drop(1) to keep locations aligned
     */
    fun dropParams(count: Int): CallInfo = CallInfo(
        callLocation = callLocation,
        paramLocations = paramLocations.drop(count)
    )

    /**
     * Take the first N parameters from the call info
     */
    fun takeParams(count: Int): CallInfo = CallInfo(
        callLocation = callLocation,
        paramLocations = paramLocations.take(count)
    )
}