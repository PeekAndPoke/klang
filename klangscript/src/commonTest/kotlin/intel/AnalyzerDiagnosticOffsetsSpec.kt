/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The clamp table of [AnalyzerDiagnostic.toOffsets].
 *
 * The conversion never throws and never trusts a coordinate, because the analysis behind a
 * diagnostic is allowed to describe a document that no longer exists: it is debounced, and the
 * editor keeps the last good AST when a parse fails. Every row of that table is a decision about
 * how much of the wrong document a stale diagnostic may underline, so each one is pinned here.
 */
class AnalyzerDiagnosticOffsetsSpec : StringSpec({

    /**
     * A [LinterDocument] over a plain string, splitting on `\n` only.
     *
     * Line ends sit ON the line break, matching CodeMirror: `lineEnd` is the offset just past the
     * last character of the line, before the break.
     */
    class FakeDocument(private val text: String) : LinterDocument {
        private val lineStarts: List<Int> = buildList {
            add(0)
            text.forEachIndexed { index, char -> if (char == '\n') add(index + 1) }
        }

        override val lineCount: Int get() = lineStarts.size
        override val length: Int get() = text.length
        override fun lineStart(line: Int): Int = lineStarts[line - 1]
        override fun lineEnd(line: Int): Int =
            if (line < lineStarts.size) lineStarts[line] - 1 else text.length
    }

    fun diagnostic(
        startLine: Int,
        startColumn: Int,
        endLine: Int,
        endColumn: Int,
    ) = AnalyzerDiagnostic(
        message = "something is off",
        severity = DiagnosticSeverity.WARNING,
        startLine = startLine,
        startColumn = startColumn,
        endLine = endLine,
        endColumn = endColumn,
    )

    // "one" [0, 3), break at 3, "two" [4, 7), break at 7, "three" [8, 13). Length 13, 3 lines.
    val doc = FakeDocument("one\ntwo\nthree")

    "a normal single-line range converts to the offsets it names" {
        diagnostic(2, 1, 2, 4).toOffsets(doc) shouldBe DiagnosticOffsets(4, 7)
    }

    "a multi-line range ending at column 1 stops at the start of its end line" {
        diagnostic(1, 1, 3, 1).toOffsets(doc) shouldBe DiagnosticOffsets(0, 8)
    }

    "a start line past the end of the document is dropped" {
        diagnostic(4, 1, 4, 2).toOffsets(doc) shouldBe null
        diagnostic(99, 1, 99, 2).toOffsets(doc) shouldBe null
    }

    "a start line below 1 is dropped" {
        diagnostic(0, 1, 1, 2).toOffsets(doc) shouldBe null
    }

    "an end line past the end of the document is clamped to the end of the START line" {
        // Not to the end of the document: the same stale analysis that put the end line out of
        // range would otherwise underline every line below it.
        diagnostic(2, 1, 99, 1).toOffsets(doc) shouldBe DiagnosticOffsets(4, 7)
    }

    "a column past the end of its line is clamped to that line" {
        diagnostic(1, 2, 1, 99).toOffsets(doc) shouldBe DiagnosticOffsets(1, 3)
    }

    "a column below 1 is clamped to the start of its line" {
        diagnostic(2, 0, 2, 3).toOffsets(doc) shouldBe DiagnosticOffsets(4, 6)
        diagnostic(2, -5, 2, 3).toOffsets(doc) shouldBe DiagnosticOffsets(4, 6)
    }

    "an inverted range collapses onto its start, then widens so it still draws" {
        diagnostic(3, 3, 1, 1).toOffsets(doc) shouldBe DiagnosticOffsets(10, 11)
    }

    "an inverted range on one line collapses onto its start too" {
        diagnostic(2, 3, 2, 1).toOffsets(doc) shouldBe DiagnosticOffsets(6, 7)
    }

    "a zero-length range widens to the right, inside its own line" {
        diagnostic(2, 1, 2, 1).toOffsets(doc) shouldBe DiagnosticOffsets(4, 5)
    }

    "a zero-length range at the end of a line widens to the LEFT, never over the line break" {
        // Offset 3 is the line break. Widening right would underline it and reach into line 2.
        diagnostic(1, 4, 1, 4).toOffsets(doc) shouldBe DiagnosticOffsets(2, 3)
    }

    "a zero-length range on an empty line stays empty, since there is nothing to widen onto" {
        val withEmptyLine = FakeDocument("a\n\nb")

        diagnostic(2, 1, 2, 1).toOffsets(withEmptyLine) shouldBe DiagnosticOffsets(2, 2)
    }

    "an empty document places a diagnostic at offset 0" {
        diagnostic(1, 1, 1, 1).toOffsets(FakeDocument("")) shouldBe DiagnosticOffsets(0, 0)
    }

    "severities map onto the three CodeMirror severity strings" {
        DiagnosticSeverity.ERROR.toCodeMirrorSeverity() shouldBe "error"
        DiagnosticSeverity.WARNING.toCodeMirrorSeverity() shouldBe "warning"
        DiagnosticSeverity.INFO.toCodeMirrorSeverity() shouldBe "info"
        DiagnosticSeverity.HINT.toCodeMirrorSeverity() shouldBe "info"
    }
})
