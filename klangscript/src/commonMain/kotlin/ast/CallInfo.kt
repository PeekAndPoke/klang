/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.ast

import io.peekandpoke.klang.common.SourceLocation

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
) {
    /**
     * A view of this call for the single original argument at [index], for a multi-param DSL
     * function that chains one argument into a single-param sibling call. The location lands at
     * [targetIndex] in the resulting list, matching the sibling's own argument position
     * (e.g. `oscparam(key, value)` reads its value location at index 1).
     */
    fun forParam(index: Int, targetIndex: Int = 0): CallInfo = copy(
        paramLocations = List(targetIndex) { null } + listOf(paramLocations.getOrNull(index)),
    )
}
