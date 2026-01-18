package io.peekandpoke.klang.audio_bridge

/**
 * JVM implementation using System.nanoTime for high precision
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object KlangTime {
    private val startTimeNanos = System.nanoTime()

    actual fun nowMs(): Double {
        val elapsedNanos = System.nanoTime() - startTimeNanos
        return elapsedNanos / 1_000_000.0
    }
}
