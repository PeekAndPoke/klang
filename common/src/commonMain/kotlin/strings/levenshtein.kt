/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.strings

/** Levenshtein edit distance to [other]. */
fun String.levenshtein(other: String): Int {
    val m = length
    val n = other.length
    var prev = IntArray(n + 1) { it }
    var curr = IntArray(n + 1)

    for (i in 1..m) {
        curr[0] = i
        for (j in 1..n) {
            val cost = if (this[i - 1] == other[j - 1]) 0 else 1
            curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
        }
        val tmp = prev; prev = curr; curr = tmp
    }
    return prev[n]
}

/**
 * Optimal-string-alignment distance to [other] — Levenshtein plus ADJACENT TRANSPOSITION as a
 * single edit.
 *
 * Use this, not [levenshtein], for "did you mean" suggestions. Swapping two neighbouring letters
 * is the most common typing mistake there is, and plain Levenshtein scores it as two edits, which
 * pushes it past any threshold tight enough to be useful on short names. The motivating case:
 * `ocsp` for `oscp` scores 2 under Levenshtein and 1 here, so only this version can suggest it
 * without also suggesting unrelated names.
 *
 * "Optimal string alignment" is the restricted Damerau variant: it does not allow a substring to
 * be edited more than once, which is fine for name suggestions and keeps the table simple.
 */
fun String.osaDistance(other: String): Int {
    val m = length
    val n = other.length

    if (m == 0) return n
    if (n == 0) return m

    // Three rows are enough for OSA: the transposition case needs to look two rows back.
    var twoBack = IntArray(n + 1)
    var prev = IntArray(n + 1) { it }
    var curr = IntArray(n + 1)

    for (i in 1..m) {
        curr[0] = i
        for (j in 1..n) {
            val cost = if (this[i - 1] == other[j - 1]) 0 else 1
            var best = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)

            // Adjacent transposition: this[i-1] == other[j-2] && this[i-2] == other[j-1]
            if (i > 1 && j > 1 && this[i - 1] == other[j - 2] && this[i - 2] == other[j - 1]) {
                best = minOf(best, twoBack[j - 2] + 1)
            }

            curr[j] = best
        }
        val tmp = twoBack
        twoBack = prev
        prev = curr
        curr = tmp
    }

    return prev[n]
}
