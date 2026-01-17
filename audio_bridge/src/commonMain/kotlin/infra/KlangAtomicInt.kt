package io.peekandpoke.klang.audio_bridge.infra

/**
 * Thread-safe atomic integer using KlangLock for synchronization.
 */
class KlangAtomicInt(initialValue: Int) {
    private val lock = KlangLock()
    private var value: Int = initialValue

    fun get(): Int = lock.withLock { value }

    fun set(newValue: Int) {
        lock.withLock { value = newValue }
    }

    fun incrementAndGet(): Int = lock.withLock {
        value++
        value
    }

    fun decrementAndGet(): Int = lock.withLock {
        value--
        value
    }

    fun getAndIncrement(): Int = lock.withLock {
        val old = value
        value++
        old
    }

    fun getAndDecrement(): Int = lock.withLock {
        val old = value
        value--
        old
    }
}
