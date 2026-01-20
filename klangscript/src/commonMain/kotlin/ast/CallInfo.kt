package io.peekandpoke.klang.script.ast

/**
 * Debug information for function/method calls
 *
 * Provides source location tracking for:
 * - The function call itself
 * - The receiver object (for method calls)
 * - Individual parameter locations (extracted from RuntimeValue instances)
 *
 * Used for live code highlighting and error reporting.
 */
data class CallInfo(
    /** Location of the function call expression */
    val callLocation: SourceLocation?,
    /** Location of the receiver object (for method calls like receiver.method()) */
    val receiverLocation: SourceLocation? = null,
    /** List of parameter locations (from StringValue, NumberValue, etc.) - indices match argument positions */
    val paramLocations: List<SourceLocation?>,
)
