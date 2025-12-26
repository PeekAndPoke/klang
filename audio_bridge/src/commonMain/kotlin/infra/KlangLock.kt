package io.peekandpoke.klang.audio_bridge.infra

expect class KlangLock() {
    fun lock()
    fun unlock()
}

/**
 * Executes the given [block] under this lock.
 */
inline fun <T> KlangLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
