package io.peekandpoke.klang.audio_bridge.infra

import java.util.concurrent.locks.ReentrantLock

actual class KlangLock {
    private val lock = ReentrantLock()

    actual fun lock() {
        lock.lock()
    }

    actual fun unlock() {
        lock.unlock()
    }
}
