package io.peekandpoke.klang.tones.utils

import kotlin.math.floor
import kotlin.random.Random

/**
 * Utility functions for working with lists and ranges.
 *
 * Port of js original from @tonaljs/collection
 */
object TonesArray {
    /**
     * Creates a numeric range.
     *
     * @param from The start of the range.
     * @param to The end of the range.
     * @return A list of numbers from [from] to [to].
     */
    fun range(from: Int, to: Int): List<Int> {
        return if (from <= to) {
            (from..to).toList()
        } else {
            (from downTo to).toList()
        }
    }

    /**
     * Rotates a list a number of times.
     *
     * @param times The number of rotations.
     * @param list The list to rotate.
     * @return The rotated list.
     */
    fun <T> rotate(times: Int, list: List<T>): List<T> {
        if (list.isEmpty()) return list
        val len = list.size
        val n = ((times % len) + len) % len
        return list.drop(n) + list.take(n)
    }

    /**
     * Return a copy of the list with null values removed.
     * In Kotlin, we can use filterNotNull for this.
     *
     * @param list The list to compact.
     * @return A list with null values removed.
     */
    fun <T : Any> compact(list: List<T?>): List<T> {
        return list.filterNotNull()
    }

    /**
     * Randomizes the order of the specified list.
     *
     * @param list The list to shuffle.
     * @param rnd A random number generator (0.0 to 1.0).
     * @return The shuffled list.
     */
    fun <T> shuffle(list: List<T>, rnd: () -> Double = { Random.nextDouble() }): List<T> {
        val mutable = list.toMutableList()
        var m = mutable.size
        // Fisher-Yates shuffle algorithm
        while (m > 0) {
            val i = floor(rnd() * m--).toInt()
            val t = mutable[m]
            mutable[m] = mutable[i]
            mutable[i] = t
        }
        return mutable
    }

    /**
     * Get all permutations of a list.
     *
     * @param list The list.
     * @return A list of all permutations.
     */
    fun <T> permutations(list: List<T>): List<List<T>> {
        if (list.isEmpty()) {
            return listOf(emptyList())
        }
        val head = list[0]
        val tail = list.drop(1)

        // Recursively find permutations of the tail and insert head at every possible position
        return permutations(tail).flatMap { perm ->
            (0..perm.size).map { i ->
                val newPerm = perm.toMutableList()
                newPerm.add(i, head)
                newPerm
            }
        }
    }
}
