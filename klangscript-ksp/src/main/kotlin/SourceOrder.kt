/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.ksp

/**
 * Where an annotated declaration sits in its module's source: the file's path, the line, and the declaration's
 * name as the last tie-breaker (two annotated declarations on one line).
 *
 * The processor sorts every symbol list it collects by this key ([sortedBySource]) before it generates anything,
 * and the rendered registration blocks once more before it cuts them into chunks. KSP hands the symbols over in
 * the order the file system lists the source directory, and that order is not the same on two machines (ext4
 * orders a directory by a hash with a per-file-system seed, APFS by name). Sorted, the registration order, the
 * chunk boundaries and numbers, the order of a docs symbol's variants and the `ktN` numbering of the docs' type
 * table are the same on every machine. The registration order is the source order across objects, type
 * extensions, functions and constants alike, so the blocks of one area come out together and each area needs
 * only as many chunk functions as its size asks for (`docs/tasks/reduce-js-bundle-size.md`, 2026-10-07).
 *
 * [filePath] is compared with `/` as the separator, so a Windows path sorts like the same path anywhere else.
 */
internal data class SourcePosition(val filePath: String, val line: Int, val name: String) : Comparable<SourcePosition> {

    override fun compareTo(other: SourcePosition): Int = ORDER.compare(this, other)

    private companion object {
        val ORDER: Comparator<SourcePosition> = compareBy<SourcePosition>(
            { it.filePath.replace('\\', '/') },
            { it.line },
            { it.name },
        )
    }
}

/** This list in source order: by [SourcePosition] (file path, line, name), whatever order it arrived in. */
internal fun <T> List<T>.sortedBySource(position: (T) -> SourcePosition): List<T> = sortedBy(position)
