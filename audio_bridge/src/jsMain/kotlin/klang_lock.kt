package io.peekandpoke.klang.audio_bridge

actual class KlangLock {
    actual fun lock() {
        // No-op in single-threaded JS environment
    }

    actual fun unlock() {
        // No-op
    }
}
