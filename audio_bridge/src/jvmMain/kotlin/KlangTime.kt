package io.peekandpoke.klang.audio_bridge

/**
 * JVM implementation using System.currentTimeMillis() + System.nanoTime()
 * Seeded with epoch time for synchronization, uses nanoTime for precision
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class KlangTime private constructor() {
    private val baseTimeMs = System.currentTimeMillis().toDouble()
    private val startNanos = System.nanoTime()

    actual fun internalMsNow(): Double {
        val elapsedNanos = System.nanoTime() - startNanos
        return baseTimeMs + (elapsedNanos / 1_000_000.0)
    }

    actual companion object {
        private val instance = KlangTime()

        actual fun create(): KlangTime = instance
    }
}
