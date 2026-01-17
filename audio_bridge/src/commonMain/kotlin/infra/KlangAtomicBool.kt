package io.peekandpoke.klang.audio_bridge.infra

/**
 * Thread-safe atomic boolean using KlangLock for synchronization.
 */
class KlangAtomicBool(initialValue: Boolean) {
    private val lock = KlangLock()
    private var value: Boolean = initialValue

    fun get(): Boolean = lock.withLock { value }

    fun set(newValue: Boolean) {
        lock.withLock { value = newValue }
    }

    /**
     * Atomically sets the value to the given updated value if the current value equals the expected value.
     * Returns true if successful.
     */
    fun compareAndSet(expect: Boolean, update: Boolean): Boolean = lock.withLock {
        if (value == expect) {
            value = update
            true
        } else {
            false
        }
    }

    /**
     * Atomically sets to the given value and returns the old value.
     */
    fun getAndSet(newValue: Boolean): Boolean = lock.withLock {
        val old = value
        value = newValue
        old
    }
}
