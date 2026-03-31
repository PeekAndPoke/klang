package io.peekandpoke.klang.common.math

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

/**
 * Returns true if this integer is a power of two (1, 2, 4, 8, 16, ...).
 *
 * How it works: A power of two has exactly one bit set in binary (e.g., 8 = 0b1000).
 * Subtracting 1 flips that bit and sets all lower bits (e.g., 7 = 0b0111).
 * AND-ing the two gives zero only for powers of two: 0b1000 & 0b0111 = 0b0000.
 *
 * Why not use log2: `(log2(x.toDouble()) % 1.0) == 0.0` fails for certain integers
 * due to floating-point precision (e.g., log2(8.0) could be 2.9999999999999996).
 */
fun Int.isPowerOfTwo(): Boolean = this > 0 && (this and (this - 1)) == 0
