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
     * AudioWorklet implementation using Date.now() + frame count for precision.
     *
     * Frame counters use Int instead of Long: Long is boxed in Kotlin/JS (emulated via
     * a wrapper object), causing heap allocation on every arithmetic operation on the audio thread.
     * Int maps directly to a JS number. At 48kHz, Int overflows after ~12.4 hours.
     */
    private class AudioWorkletTimeSource(
        private val sampleRate: Double,
    ) : TimeSource {
        private val baseTimeMs = Date.now()
        private var startFrame: Int = 0
        var currentFrame: Int = 0
            set(value) {
                if (field == 0 && value > 0) {
                    startFrame = value  // Capture first non-zero frame as start
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
    fun updateCurrentFrame(frame: Int) {
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
