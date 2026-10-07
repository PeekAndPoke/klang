/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LineOffsetsSpec : StringSpec({

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
})
