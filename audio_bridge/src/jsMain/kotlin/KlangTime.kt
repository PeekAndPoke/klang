/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import kotlin.js.Date

/**
 * JS implementation: `Date.now()` at creation plus the `performance.now()` elapsed since, wherever the scope has
 * `performance` (the main thread, and an AudioWorklet that has it); else, in a worklet without it, `Date.now()`.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class KlangTime private constructor(
    private val impl: TimeSource,
) {
    private interface TimeSource {
        fun now(): Double
    }

    /**
     * Date.now() at creation + the performance.now() elapsed since: monotonic, it never jumps with the wall clock
     */
    private class PerformanceTimeSource : TimeSource {
        private val baseTimeMs = Date.now()
        private val perfStart = js("performance.now()") as Double

        override fun now(): Double {
            val perfNow = js("performance.now()") as Double
            return baseTimeMs + (perfNow - perfStart)
        }
    }

    /**
     * For an AudioWorklet whose scope has no `performance` (the render timer's reason, `renderTimerMs`): the wall clock,
     * `Date.now()`. Its one reader is the worklet's start-up, which
     * takes the backend's epoch from it once; from there the worklet's timeline runs on the frames it renders
     * (`RenderClock`), never on this clock.
     *
     * Until 2026-11-11 (KLANG-00433 Decide 7, audit L9-14) it counted frames: the worklet set its frame every block
     * (`updateCurrentFrame`), nothing read it after the start-up, and it stood still from block 0 to block 1.
     */
    private class WallClockTimeSource : TimeSource {
        override fun now(): Double = Date.now()
    }

    actual fun internalMsNow(): Double = impl.now()

    actual companion object {
        actual fun create(): KlangTime {
            // An AudioWorkletGlobalScope need not have `performance`; every other scope here has it
            val hasPerformance = js("typeof performance !== 'undefined' && typeof performance.now === 'function'") as Boolean

            return if (hasPerformance) {
                KlangTime(PerformanceTimeSource())
            } else {
                KlangTime(WallClockTimeSource())
            }
        }
    }
}
