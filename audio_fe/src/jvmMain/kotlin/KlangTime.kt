package io.peekandpoke.klang.audio_fe

/**
 * JVM implementation using System.nanoTime for high precision
 */
actual object KlangTime {
    private val startTimeNanos = System.nanoTime()

    actual fun nowMs(): Double {
        val elapsedNanos = System.nanoTime() - startTimeNanos
        return elapsedNanos / 1_000_000.0
    }
}
