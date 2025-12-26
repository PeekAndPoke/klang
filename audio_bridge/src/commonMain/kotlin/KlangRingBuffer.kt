package io.peekandpoke.klang.audio_bridge

/**
 * A simple ring buffer to pass events from the fetcher (Main Thread) to the audio loop (Audio Thread).
 * Uses a fixed capacity.
 */
class KlangRingBuffer<T>(private val capacity: Int) : KlangEventDispatcher<T>, KlangEventReceiver<T> {
    private val buffer = arrayOfNulls<Any?>(capacity)
    private var head = 0 // Write index
    private var tail = 0 // Read index
    private val lock = KlangLock()

    override fun dispatch(event: T): Boolean = lock.withLock {
        val nextHead = (head + 1) % capacity
        if (nextHead == tail) {
            return@withLock false // Buffer full
        }
        buffer[head] = event
        head = nextHead
        return@withLock true
    }

    @Suppress("UNCHECKED_CAST")
    override fun receive(): T? = lock.withLock {
        if (head == tail) {
            return@withLock null // Buffer empty
        }
        val item = buffer[tail] as T
        buffer[tail] = null // Help GC
        tail = (tail + 1) % capacity
        return@withLock item
    }

    override fun clear() = lock.withLock {
        while (head != tail) {
            buffer[tail] = null
            tail = (tail + 1) % capacity
        }
        head = 0
        tail = 0
    }
}
