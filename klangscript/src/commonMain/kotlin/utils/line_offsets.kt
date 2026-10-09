/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.utils

/**
 * The 0-based offset at which each line of [source] starts, line 1 first: `"ab\ncd"` gives `[0, 3]`.
 *
 * Only `\n` breaks a line; a `\r` before it counts as the last character of its line.
 */
internal fun buildLineOffsets(source: String): IntArray {
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
internal fun lineColToOffset(lineOffsets: IntArray, line: Int, column: Int): Int? {
    val lineIdx = line - 1

    if (lineIdx < 0 || lineIdx >= lineOffsets.size) {
        return null
    }

    return lineOffsets[lineIdx] + (column - 1)
}
