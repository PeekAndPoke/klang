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
