package io.peekandpoke.klang.audio_bridge.infra

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class KlangLock {
    actual fun lock() {
        // No-op in single-threaded JS environment
    }

    actual fun unlock() {
        // No-op
    }
}
