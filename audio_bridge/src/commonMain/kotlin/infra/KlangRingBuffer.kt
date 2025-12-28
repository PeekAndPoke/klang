package io.peekandpoke.klang.audio_bridge.infra

/**
 * A simple ring buffer to pass events from the fetcher (Main Thread) to the audio loop (Audio Thread).
 * Uses a fixed capacity.
 */
class KlangRingBuffer<T>(private val capacity: Int) : KlangMessageSender<T>, KlangMessageReceiver<T> {
    private val buffer = arrayOfNulls<Any?>(capacity)
    private var head = 0 // Write index
    private var tail = 0 // Read index
    private val lock = KlangLock()

    override fun send(msg: T): Boolean = lock.withLock {
        val nextHead = (head + 1) % capacity
        if (nextHead == tail) {
            return@withLock false // Buffer full
        }
        buffer[head] = msg
        head = nextHead
        return@withLock true
    }

    override fun receive(): T? = lock.withLock {
        if (head == tail) {
            return@withLock null // Buffer empty
        }
        @Suppress("UNCHECKED_CAST")
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
