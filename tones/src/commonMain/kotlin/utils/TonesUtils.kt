package io.peekandpoke.klang.tones.utils

import kotlin.math.abs

/**
 * General utility functions for the tones library.
 */
object TonesUtils {
    /**
     * Repeats a string n times.
     *
     * @param s The string to repeat.
     * @param n The number of times to repeat.
     * @return The repeated string.
     */
    fun fillStr(s: String, n: Int): String {
        return s.repeat(abs(n))
    }
}
