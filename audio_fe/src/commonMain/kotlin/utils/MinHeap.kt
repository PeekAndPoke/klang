package io.peekandpoke.klang.audio_fe.utils

class MinHeap<T>(private val less: (T, T) -> Boolean) {
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
}
