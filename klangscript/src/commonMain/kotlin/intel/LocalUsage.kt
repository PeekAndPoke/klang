/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

/**
 * One place a local is used ([AnalyzedAst.usagesAt]): the word's offsets in the editor's text, its line (counted
 * from 1) and that line's code, its indentation dropped.
 *
 * @param start Offset where the word starts.
 * @param end   Offset where it ends (exclusive).
 * @param line  The line it stands on, counted from 1.
 * @param code  The line's code, its indentation dropped.
 */
data class LocalUsage(
    val start: Int,
    val end: Int,
    val line: Int,
    val code: String,
)
