/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

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
     *
     * Frames here are **Double**, not Int. This counter tracks the worklet's absolute timeline and
     * grows for the life of the audio thread; as an Int it overflowed after ~12.4 h at 48 kHz and
     * the resulting clock went backwards, silently. Double is a native JS number, exact for integers
     * to 2^53 (~5,950 years at 48 kHz), and cannot drift here — only subtraction and division by a
     * constant are applied. See `RenderClock.cursorFrame` in audio_be for the full reasoning.
     */
    private class AudioWorkletTimeSource(
        private val sampleRate: Double,
    ) : TimeSource {
        private val baseTimeMs = Date.now()
        private var startFrame: Double = 0.0
        var currentFrame: Double = 0.0
            set(value) {
                if (field == 0.0 && value > 0.0) {
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
    fun updateCurrentFrame(frame: Double) {
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
