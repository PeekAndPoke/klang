/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.infra

/**
 * A min-heap ordered by [less], and STABLE: of two elements neither of which is less, the one pushed first comes out
 * first (KLANG-00399, step 4). The voice scheduler's queue is one: voices that start at the same moment (a chord, a
 * stack) are promoted in the order they were scheduled, whatever pops came between, so a live playback and an offline
 * render of it, which push the same voices in the same order but pop at other moments, deal the voices' random seeds
 * and cut groups alike. Before, ties came out in an order that depended on when they were pushed.
 *
 * Each element carries its push number in a parallel [DoubleArray] (a `Double`: no boxing on Kotlin/JS, exact far
 * beyond any count of pushes), grown by doubling; nothing is allocated per push otherwise.
 */
class KlangMinHeap<T>(private val less: (T, T) -> Boolean) {
    private val data = ArrayList<T>()
    private var order = DoubleArray(16)
    private var pushed = 0.0

    fun clear() = data.clear()
    fun peek(): T? = data.firstOrNull()

    /** Number of elements currently in the heap (debugging/metrics). */
    fun size(): Int = data.size

    fun push(x: T) {
        data.add(x)

        if (data.size > order.size) {
            order = order.copyOf(order.size * 2)
        }

        order[data.lastIndex] = pushed
        pushed += 1.0
        siftUp(data.lastIndex)
    }

    fun pop(): T? {
        if (data.isEmpty()) {
            return null
        }

        val root = data[0]
        val lastIndex = data.lastIndex
        val last = data.removeAt(lastIndex)

        if (data.isNotEmpty()) {
            data[0] = last
            order[0] = order[lastIndex]
            siftDown(0)
        }

        return root
    }

    /**
     * Removes all elements matching the predicate and rebuilds the heap.
     *
     * Uses in-place compaction (no intermediate list allocation) followed by Floyd's heapify O(n).
     * This is important when called from the audio thread where heap allocations must be avoided.
     */
    fun removeWhen(predicate: (T) -> Boolean) {
        var writeIdx = 0
        for (readIdx in data.indices) {
            if (!predicate(data[readIdx])) {
                if (writeIdx != readIdx) {
                    data[writeIdx] = data[readIdx]
                    order[writeIdx] = order[readIdx]
                }
                writeIdx++
            }
        }
        // Nothing removed — skip trim and heapify
        if (writeIdx == data.size) return
        // Trim removed elements from the end
        while (data.size > writeIdx) {
            data.removeLast()
        }
        // Restore heap property using Floyd's algorithm — O(n)
        heapify()
    }

    /** Whether the element at [first] comes out before the one at [second]: less, or as little and pushed first. */
    private fun before(first: Int, second: Int): Boolean {
        val a = data[first]
        val b = data[second]

        return less(a, b) || (!less(b, a) && order[first] < order[second])
    }

    private fun swap(i: Int, j: Int) {
        val tmp = data[i]
        data[i] = data[j]
        data[j] = tmp

        val tmpOrder = order[i]
        order[i] = order[j]
        order[j] = tmpOrder
    }

    private fun siftUp(i0: Int) {
        var i = i0
        while (i > 0) {
            val p = (i - 1) / 2

            if (!before(first = i, second = p)) {
                break
            }

            swap(i, p)
            i = p
        }
    }

    private fun siftDown(i0: Int) {
        var i = i0
        while (true) {
            val l = i * 2 + 1
            val r = i * 2 + 2
            var m = i

            if (l < data.size && before(first = l, second = m)) {
                m = l
            }

            if (r < data.size && before(first = r, second = m)) {
                m = r
            }

            if (m == i) {
                break
            }

            swap(i, m)
            i = m
        }
    }

    /**
     * Floyd's heapify: sift down from the last parent node to the root.
     * Restores the heap property in O(n) time after bulk modifications.
     */
    private fun heapify() {
        for (i in (data.size / 2 - 1) downTo 0) {
            siftDown(i)
        }
    }
}
