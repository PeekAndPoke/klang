/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.KlangPattern

/**
 * Interface for cycle-based pattern playback.
 *
 * Extends [KlangPlayback] with tempo control, pattern updates, and cycle-based scheduling.
 * Implementations include continuous (live coding) and one-shot (preview) modes.
 */
interface KlangCyclicPlayback : KlangPlayback {
    /**
     * Update the pattern being played.
     */
    fun updatePattern(pattern: KlangPattern)

    /**
     * Update the RPM (revolutions per minute = tempo).
     * RPM = CPS * 60, e.g. RPM 30 = 0.5 cycles per second.
     *
     * Values below 1.0 are clamped by the implementation.
     */
    fun updateRpm(rpm: Double)

    /**
     * Re-emits VoicesScheduled signals for all events currently in the lookahead window.
     * Does not touch the audio backend — use this to refresh UI highlights instantly.
     */
    fun reemitVoiceSignals()

    /**
     * The seconds of this playback heard so far: where the backend reported it was, less the output latency, from the
     * playback's start (KLANG-00399). Stands still while the backend does. Null before the playback has started.
     */
    fun heardSec(): Double?

    /**
     * Moves the playback's output gain to [gain] over [rampSec] seconds (KLANG-00406 step 5): a song faded under a code
     * example, a preview fading in. Kept while stopped and sent again at the next start.
     *
     * The ramp runs from when the backend gets it, not from the first sound: a fade-in asked for right after [start]
     * runs while the playback still waits for the engine and loads its samples, and may be over before a note sounds.
     * Ask for it once the playback has begun.
     */
    fun setGain(gain: Double, rampSec: Double)

    /** The gain last asked for by [setGain], where its ramp goes or stands; 1 until then. */
    val gain: Double

    /**
     * Start playback with the given options.
     */
    fun start(options: Options = Options())

    /**
     * Configuration options for cyclic playback.
     */
    data class Options(
        /** Number of cycles to look ahead when scheduling events */
        val lookaheadCycles: Double = 2.0,
        /** RPM (revolutions per minute = tempo). RPM 30 = 0.5 CPS. Values below 1.0 are clamped. */
        val rpm: Double = 30.0,
        /** Initial cycles prefetch, so that the audio starts flawlessly. Auto-calculated if null. */
        val prefetchCycles: Int? = null,
    )
}
