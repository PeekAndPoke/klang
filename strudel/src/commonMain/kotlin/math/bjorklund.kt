package io.peekandpoke.klang.strudel.math

import kotlin.math.abs
import kotlin.math.min

/**
 * Calculates the Euclidean rhythm using the Bjorklund algorithm.
 */
fun bjorklund(pulses: Int, steps: Int): List<Int> {
    val k = abs(pulses)
    val n = steps
    if (n <= 0) return emptyList()

    // Calculate base pattern
    val basePattern = if (k >= n) {
        List(n) { 1 }
    } else {
        val ons = k
        val offs = n - k

        val ones = List(ons) { listOf(1) }
        val zeros = List(offs) { listOf(0) }

        val result = recursiveBjorklund(ons, offs, ones, zeros)
        result.first.flatten() + result.second.flatten()
    }

    // Handle negative pulses by inverting the pattern (1 -> 0, 0 -> 1)
    return if (pulses < 0) {
        basePattern.map { 1 - it }
    } else {
        basePattern
    }
}

fun recursiveBjorklund(
    ons: Int,
    offs: Int,
    xs: List<List<Int>>,
    ys: List<List<Int>>,
): Pair<List<List<Int>>, List<List<Int>>> {
    // JS logic: Math.min(ons, offs) <= 1 ? [n, x] ...
    // Note: The JS source uses Math.min(ons, offs) <= 1.
    if (min(ons, offs) <= 1) return xs to ys

    if (ons > offs) {
        val offsCount = offs
        val xsPrefix = xs.take(offsCount)
        val xsSuffix = xs.drop(offsCount)
        val newXs = xsPrefix.zip(ys) { a, b -> a + b }
        val newYs = xsSuffix
        return recursiveBjorklund(offs, ons - offs, newXs, newYs)
    } else {
        val onsCount = ons
        val ysPrefix = ys.take(onsCount)
        val ysSuffix = ys.drop(onsCount)
        val newXs = xs.zip(ysPrefix) { a, b -> a + b }
        val newYs = ysSuffix
        return recursiveBjorklund(ons, offs - ons, newXs, newYs)
    }
}
