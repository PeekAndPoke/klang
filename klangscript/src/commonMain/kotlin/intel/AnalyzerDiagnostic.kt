/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

/**
 * Severity levels for analyzer diagnostics.
 */
enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO,
    HINT,
}

/**
 * A diagnostic produced by static analysis of a KlangScript program.
 *
 * Line and column values are 1-based, matching [io.peekandpoke.klang.common.SourceLocation] conventions.
 */
data class AnalyzerDiagnostic(
    val message: String,
    val severity: DiagnosticSeverity,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
)
