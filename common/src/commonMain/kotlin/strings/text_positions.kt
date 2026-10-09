/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.strings

import io.peekandpoke.klang.common.SourceLocation

// The one home of the text-position helpers (utils home pass, maintainer 2026-10-09, Q15). Lines and columns are
// 1-based, offsets 0-based, and only `\n` breaks a line: a `\r` before it counts as the last character of its line.
// The four keep the semantics their callers had: a table for many lookups, a scan without allocation for one, a
// clamped position, and the reverse direction.

/**
 * The 0-based offset at which each line of [source] starts, line 1 first: `"ab\ncd"` gives `[0, 3]`. Build it once
 * for many lookups ([lineColToOffset]); for a single line, [lineStartOffset] scans without allocating.
 */
fun buildLineOffsets(source: String): IntArray {
    val offsets = mutableListOf(0)

    for (i in source.indices) {
        if (source[i] == '\n') {
            offsets.add(i + 1)
        }
    }

    return offsets.toIntArray()
}

/**
 * The 0-based offset of the 1-based [line] and [column], read from [lineOffsets] (see [buildLineOffsets]);
 * `null` for a line outside the table.
 *
 * The column is not checked against the line's length, so a column past the end lands on a later line.
 */
fun lineColToOffset(lineOffsets: IntArray, line: Int, column: Int): Int? {
    val lineIdx = line - 1

    if (lineIdx < 0 || lineIdx >= lineOffsets.size) {
        return null
    }

    return lineOffsets[lineIdx] + (column - 1)
}

/**
 * The 0-based offset at which the 1-based [line] of this text starts, or `null` when the text has no such line.
 * One scan up to that line, nothing allocated.
 */
fun String.lineStartOffset(line: Int): Int? {
    if (line == 1) {
        return 0
    }

    var current = 1

    for (i in indices) {
        if (this[i] == '\n') {
            current++

            if (current == line) {
                return i + 1
            }
        }
    }

    return null
}

/**
 * The offset of the 1-based [column] on a line that spans [lineStart] (its first character) to [lineEnd] (just past
 * its last, before the line break), clamped to the line: a column before the line gives [lineStart], one past its
 * end gives [lineEnd]. The comparison is done on the column, not on the sum, so a wild column past the end cannot
 * overflow the sum. `Int.MIN_VALUE` itself overflows `column - 1` and gives [lineEnd]; no parser location is that.
 */
fun clampedLineOffset(lineStart: Int, lineEnd: Int, column: Int): Int {
    val offsetInLine = column - 1

    return when {
        offsetInLine <= 0 -> lineStart
        offsetInLine >= lineEnd - lineStart -> lineEnd
        else -> lineStart + offsetInLine
    }
}

/**
 * The 1-based line and column of the 0-based [offset] in [source], as a zero-width [SourceLocation] without a
 * source name. An offset past the end of the text is the position just after its last character.
 */
fun offsetToSourceLocation(source: String, offset: Int): SourceLocation {
    var line = 1
    var column = 1

    for (i in 0 until offset.coerceAtMost(source.length)) {
        if (source[i] == '\n') {
            line++
            column = 1
        } else {
            column++
        }
    }

    return SourceLocation(source = null, startLine = line, startColumn = column, endLine = line, endColumn = column)
}
