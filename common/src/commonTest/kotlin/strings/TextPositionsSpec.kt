/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.strings

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.common.SourceLocation

/**
 * The text-position helpers, one home since the utils home pass (Q15). The `buildLineOffsets` and `lineColToOffset`
 * rows moved here from klangscript's `LineOffsetsSpec`.
 */
class TextPositionsSpec : StringSpec({

    /** A zero-width location without a source name. */
    fun at(line: Int, column: Int) = SourceLocation(source = null, startLine = line, startColumn = column, endLine = line, endColumn = column)

    "buildLineOffsets: one entry per line, each where the line starts" {
        buildLineOffsets("ab\ncd").toList() shouldBe listOf(0, 3)
        buildLineOffsets("a\n\nbc\n").toList() shouldBe listOf(0, 2, 3, 6)
    }

    "buildLineOffsets: a text without a line break is one line, the empty text too" {
        buildLineOffsets("abc").toList() shouldBe listOf(0)
        buildLineOffsets("").toList() shouldBe listOf(0)
    }

    "buildLineOffsets: only \\n breaks a line, a \\r belongs to the line before it" {
        buildLineOffsets("a\r\nb").toList() shouldBe listOf(0, 3)
    }

    "lineColToOffset: 1-based line and column to the 0-based offset" {
        val offsets = buildLineOffsets("let a\nlet bb\nx")

        lineColToOffset(lineOffsets = offsets, line = 1, column = 1) shouldBe 0
        lineColToOffset(lineOffsets = offsets, line = 1, column = 5) shouldBe 4
        lineColToOffset(lineOffsets = offsets, line = 2, column = 1) shouldBe 6
        lineColToOffset(lineOffsets = offsets, line = 2, column = 5) shouldBe 10
        lineColToOffset(lineOffsets = offsets, line = 3, column = 1) shouldBe 13
    }

    "lineColToOffset: a line outside the text gives null" {
        val offsets = buildLineOffsets("a\nb")

        lineColToOffset(lineOffsets = offsets, line = 0, column = 1) shouldBe null
        lineColToOffset(lineOffsets = offsets, line = 3, column = 1) shouldBe null
    }

    "lineColToOffset: the column is not checked against the line's length" {
        val offsets = buildLineOffsets("ab\ncd")

        // column 5 of line 1 is past "ab", and lands on line 2
        lineColToOffset(lineOffsets = offsets, line = 1, column = 5) shouldBe 4
    }

    "lineStartOffset: where the 1-based line starts, the same answer as the table" {
        val text = "a\n\nbc\nd"

        text.lineStartOffset(1) shouldBe 0
        text.lineStartOffset(2) shouldBe 2
        text.lineStartOffset(3) shouldBe 3
        text.lineStartOffset(4) shouldBe 6

        (1..4).map { text.lineStartOffset(it) } shouldBe buildLineOffsets(text).toList()
    }

    "lineStartOffset: a line the text does not have gives null, line 0 and below included" {
        "a\nb".lineStartOffset(3) shouldBe null
        "a\nb".lineStartOffset(0) shouldBe null
        "a\nb".lineStartOffset(-1) shouldBe null
        "".lineStartOffset(1) shouldBe 0
    }

    "clampedLineOffset: a column inside the line is its offset" {
        // the line "bcd" spans 4 until 7 in "xyz\nbcd\n"
        clampedLineOffset(lineStart = 4, lineEnd = 7, column = 1) shouldBe 4
        clampedLineOffset(lineStart = 4, lineEnd = 7, column = 3) shouldBe 6
    }

    "clampedLineOffset: a column before the line or past its end is clamped to the line, never to a neighbour" {
        clampedLineOffset(lineStart = 4, lineEnd = 7, column = 0) shouldBe 4
        clampedLineOffset(lineStart = 4, lineEnd = 7, column = -5) shouldBe 4
        clampedLineOffset(lineStart = 4, lineEnd = 7, column = 4) shouldBe 7
        clampedLineOffset(lineStart = 4, lineEnd = 7, column = 99) shouldBe 7
    }

    "clampedLineOffset: a wild column past the end does not overflow the sum" {
        clampedLineOffset(lineStart = 4, lineEnd = 7, column = Int.MAX_VALUE) shouldBe 7
    }

    "offsetToSourceLocation: the 1-based line and column of an offset, a zero-width location" {
        val text = "let a\nlet bb\nx"

        offsetToSourceLocation(source = text, offset = 0) shouldBe at(line = 1, column = 1)
        offsetToSourceLocation(source = text, offset = 4) shouldBe at(line = 1, column = 5)
        offsetToSourceLocation(source = text, offset = 6) shouldBe at(line = 2, column = 1)
        offsetToSourceLocation(source = text, offset = 13) shouldBe at(line = 3, column = 1)
    }

    "offsetToSourceLocation: an offset past the end is the position after the last character" {
        offsetToSourceLocation(source = "ab\nc", offset = 99) shouldBe at(line = 2, column = 2)
    }

    "offsetToSourceLocation is the inverse of lineColToOffset on every offset of a text" {
        val text = "a\n\nbc\nd"
        val offsets = buildLineOffsets(text)

        for (offset in 0..text.length) {
            val loc = offsetToSourceLocation(source = text, offset = offset)

            lineColToOffset(lineOffsets = offsets, line = loc.startLine, column = loc.startColumn) shouldBe offset
        }
    }
})
