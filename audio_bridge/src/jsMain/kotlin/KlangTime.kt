package io.peekandpoke.klang.audio_bridge

import kotlin.js.Date

/**
 * JS implementation with context detection:
 * - Main thread: uses performance.now()
 * - AudioWorklet: uses frame-based timing
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class KlangTime private constructor(
    private val impl: TimeSource,
) {
    private interface TimeSource {
        fun now(): Double
    }

    /**
     * Main thread implementation using Date.now() + performance.now() for precision
     */
    private class MainThreadTimeSource : TimeSource {
        private val baseTimeMs = Date.now()
        private val perfStart = js("performance.now()") as Double

        override fun now(): Double {
            val perfNow = js("performance.now()") as Double
            return baseTimeMs + (perfNow - perfStart)
        }
    }

    /**
     * AudioWorklet implementation using Date.now() + frame count for precision
     */
    private class AudioWorkletTimeSource(
        private val sampleRate: Double,
    ) : TimeSource {
        private val baseTimeMs = Date.now()
        private var startFrame: Long = 0
        var currentFrame: Long = 0
            set(value) {
                if (field == 0L && value > 0L) {
                    startFrame = value // Capture first non-zero frame as start
                }
                field = value
            }

        override fun now(): Double {
            val elapsedMs = ((currentFrame - startFrame) / sampleRate) * 1000.0
            return baseTimeMs + elapsedMs
        }
    }

    actual fun internalMsNow(): Double = impl.now()

    /**
     * For AudioWorklet context: update the current frame count
     */
    fun updateCurrentFrame(frame: Long) {
        (impl as? AudioWorkletTimeSource)?.let {
            it.currentFrame = frame
        }
    }

    actual companion object {
        actual fun create(): KlangTime {
            val isAudioWorklet = js(
                """
                typeof AudioWorkletProcessor !== 'undefined' &&
                typeof registerProcessor === 'function'
            """
            ) as Boolean

            return if (isAudioWorklet) {
                val sampleRate = js("sampleRate") as Double
                KlangTime(AudioWorkletTimeSource(sampleRate))
            } else {
                KlangTime(MainThreadTimeSource())
            }
        }
    }
}
