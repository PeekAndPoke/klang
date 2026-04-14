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
