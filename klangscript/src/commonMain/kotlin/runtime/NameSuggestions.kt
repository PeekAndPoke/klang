/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.runtime

import io.peekandpoke.klang.common.strings.osaDistance

/**
 * Builds the "Did you mean ...?" tail for a name that was not found.
 *
 * A misspelled name is the single most common authoring mistake, and the cost of a bad message
 * is real: `.ocsp` for `.oscp` (one adjacent transposition) cost a full debugging session,
 * because the error dumped all ~400 available method names unranked and the right one was
 * invisible in the middle of it.
 *
 * Ranks with [osaDistance], NOT plain Levenshtein: a transposition is ONE edit there and two
 * under Levenshtein, and at the tight thresholds short names need, the plain version cannot
 * suggest `oscp` for `ocsp` at all. That was measured, not assumed — the first version of this
 * helper used Levenshtein and failed the very case it was written for.
 *
 * Returns an empty string when nothing is close enough, so callers can append it unconditionally.
 */
fun suggestNames(
    typed: String,
    available: Collection<String>,
    /**
     * Maximum edit distance to accept. Scaled to the typed name's length: a 3-edit "suggestion"
     * for a 4-letter name is noise, and offering `mul` for `abs` helps nobody.
     */
    maxDistance: Int = when {
        typed.length <= 4 -> 1
        typed.length <= 8 -> 2
        else -> 3
    },
    limit: Int = 3,
): String {
    if (typed.isEmpty() || available.isEmpty()) {
        return ""
    }

    val ranked = available
        .asSequence()
        .map { it to typed.lowercase().osaDistance(it.lowercase()) }
        .filter { it.second <= maxDistance }
        // Distance first, then shorter names, then alphabetical — so the order is stable and
        // does not depend on the iteration order of whatever set was passed in.
        .sortedWith(compareBy({ it.second }, { it.first.length }, { it.first }))
        .take(limit)
        .map { it.first }
        .toList()

    return when {
        ranked.isEmpty() -> ""
        else -> " Did you mean ${ranked.joinToString(" or ") { "'$it'" }}?"
    }
}

/**
 * Formats the full list of available names for an error message, truncated.
 *
 * The untruncated list for a sprudel pattern is over 400 names and roughly 4 KB, which buries
 * the suggestion that matters and floods the console. Callers should put [suggestNames] first.
 */
fun formatAvailableNames(available: Collection<String>, limit: Int = 40): String {
    if (available.isEmpty()) {
        return ""
    }

    val sorted = available.sorted()
    val shown = sorted.take(limit).joinToString(", ")

    return when {
        sorted.size <= limit -> " Available: $shown."
        else -> " Available (${sorted.size} total, showing $limit): $shown, ..."
    }
}
