package io.peekandpoke.klang.tones.core

/**
 * Repeats a string n times.
 *
 * @param s The string to repeat.
 * @param n The number of times to repeat.
 * @return The repeated string.
 */
fun fillStr(s: String, n: Int): String {
    return s.repeat(kotlin.math.abs(n))
}
