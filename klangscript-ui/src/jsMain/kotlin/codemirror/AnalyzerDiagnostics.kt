/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.codemirror.ext.Text
import io.peekandpoke.klang.script.intel.AnalyzerDiagnostic
import io.peekandpoke.klang.script.intel.DiagnosticSeverity

/**
 * Absolute CodeMirror document offsets for a single analyzer diagnostic.
 *
 * [from] is inclusive, [to] is exclusive, both are valid positions in the document that was
 * measured.
 */
internal data class DiagnosticOffsets(val from: Int, val to: Int)

/**
 * The line structure of the document a linter run is measured against.
 *
 * An interface (and not the CodeMirror `Text` itself) so the offset arithmetic stays plain
 * Kotlin and can be reasoned about without an editor.
 */
internal interface LinterDocument {
    /** Number of lines. Always at least 1: an empty document still has one empty line. */
    val lineCount: Int

    /** Total character length of the document. */
    val length: Int

    /** Absolute offset of the first character of the 1-based [line]. */
    fun lineStart(line: Int): Int

    /** Absolute offset just past the last character of the 1-based [line], before the line break. */
    fun lineEnd(line: Int): Int
}

/** [LinterDocument] backed by the live CodeMirror document. */
internal class CodeMirrorLinterDocument(private val doc: Text) : LinterDocument {
    override val lineCount: Int get() = doc.lines
    override val length: Int get() = doc.length
    override fun lineStart(line: Int): Int = doc.line(line).from
    override fun lineEnd(line: Int): Int = doc.line(line).to
}

/**
 * Maps an analyzer severity onto the CodeMirror severity strings.
 *
 * `HINT` folds into `"info"`: the CodeMirror `Diagnostic` binding documents three severities,
 * and no checker emits `HINT` today.
 */
internal fun DiagnosticSeverity.toCodeMirrorSeverity(): String = when (this) {
    DiagnosticSeverity.ERROR -> "error"
    DiagnosticSeverity.WARNING -> "warning"
    DiagnosticSeverity.INFO -> "info"
    DiagnosticSeverity.HINT -> "info"
}

/**
 * Converts the 1-based line/column range of an [AnalyzerDiagnostic] into absolute document
 * offsets, or returns `null` when the diagnostic cannot be placed at all.
 *
 * The analysis behind a diagnostic can describe a document that no longer exists: it is
 * debounced, and `EditorDocContext` deliberately keeps the last good AST when a parse fails.
 * So every coordinate is clamped instead of trusted, and nothing here throws: a linter source
 * that throws takes the whole editor down with it.
 *
 * Handled cases:
 *  - a start line past the end of the document: dropped, since any other line would underline
 *    unrelated code
 *  - an end line past the end of the document: clamped to the last line
 *  - a column before or past the end of its line: clamped to the line bounds
 *  - an inverted range (end before start): collapsed onto the start
 *  - a zero-length range: widened by one character inside its own line so that it draws
 */
internal fun AnalyzerDiagnostic.toOffsets(doc: LinterDocument): DiagnosticOffsets? {
    val lineCount = doc.lineCount
    val docLength = doc.length

    if (lineCount < 1 || docLength < 0) {
        return null
    }

    if (startLine < 1 || startLine > lineCount) {
        return null
    }

    val from = offsetOf(doc, startLine, startColumn)

    // The analyzer's end column is 1-based and exclusive, so the same arithmetic gives the
    // exclusive end offset.
    val to = when {
        endLine < startLine -> from
        endLine > lineCount -> doc.lineEnd(lineCount)
        else -> offsetOf(doc, endLine, endColumn)
    }

    val safeFrom = from.coerceIn(0, docLength)
    val safeTo = to.coerceIn(safeFrom, docLength)

    if (safeTo > safeFrom) {
        return DiagnosticOffsets(safeFrom, safeTo)
    }

    // A zero-length range draws no squiggle. Widen it by one character within the start line,
    // preferring the character to the right, so it never bleeds into a neighbouring line.
    return when {
        safeFrom < doc.lineEnd(startLine) -> DiagnosticOffsets(safeFrom, safeFrom + 1)
        safeFrom > doc.lineStart(startLine) -> DiagnosticOffsets(safeFrom - 1, safeFrom)
        else -> DiagnosticOffsets(safeFrom, safeFrom)
    }
}

/**
 * Absolute offset of the 1-based [column] on the 1-based [line], clamped to that line.
 *
 * The comparison is done on the column offset rather than on the sum, so a wild column value
 * cannot overflow the addition.
 */
private fun offsetOf(doc: LinterDocument, line: Int, column: Int): Int {
    val start = doc.lineStart(line)
    val end = doc.lineEnd(line)
    val offsetInLine = column - 1

    return when {
        offsetInLine <= 0 -> start
        offsetInLine >= end - start -> end
        else -> start + offsetInLine
    }
}
