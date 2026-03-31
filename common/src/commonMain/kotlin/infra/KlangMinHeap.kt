package io.peekandpoke.klang.common.infra

class KlangMinHeap<T>(private val less: (T, T) -> Boolean) {
    private val data = ArrayList<T>()

    fun clear() = data.clear()
    fun peek(): T? = data.firstOrNull()

    /** Number of elements currently in the heap (debugging/metrics). */
    fun size(): Int = data.size

    fun push(x: T) {
        data.add(x)
        siftUp(data.lastIndex)
    }

    fun pop(): T? {
        if (data.isEmpty()) return null
        val root = data[0]
        val last = data.removeAt(data.lastIndex)
        if (data.isNotEmpty()) {
            data[0] = last
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
                data[writeIdx++] = data[readIdx]
            }
        }
        // Trim removed elements from the end
        while (data.size > writeIdx) {
            data.removeLast()
        }
        // Restore heap property using Floyd's algorithm — O(n)
        heapify()
    }

    private fun siftUp(i0: Int) {
        var i = i0
        while (i > 0) {
            val p = (i - 1) / 2
            if (!less(data[i], data[p])) break
            val tmp = data[i]
            data[i] = data[p]
            data[p] = tmp
            i = p
        }
    }

    private fun siftDown(i0: Int) {
        var i = i0
        while (true) {
            val l = i * 2 + 1
            val r = i * 2 + 2
            var m = i

            if (l < data.size && less(data[l], data[m])) m = l
            if (r < data.size && less(data[r], data[m])) m = r
            if (m == i) break

            val tmp = data[i]
            data[i] = data[m]
            data[m] = tmp
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
