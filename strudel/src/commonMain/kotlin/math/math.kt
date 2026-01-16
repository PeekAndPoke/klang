package io.peekandpoke.klang.strudel.math

import kotlin.math.abs

/**
 * Calculates the greatest common divisor (GCD) of two integers.
 */
fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

/**
 * Calculates the least common multiple (LCM) of two integers.
 */
fun lcm(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else abs(a * b) / gcd(a, b)

/**
 * Calculates the least common multiple (LCM) of a list of integers.
 */
fun lcm(numbers: List<Int>): Int = numbers.fold(1) { acc, i -> lcm(acc, i) }
