package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_engine.KlangPlayback
import io.peekandpoke.klang.audio_engine.KlangPlaybackContext
import io.peekandpoke.klang.strudel.StrudelPlayback.Companion.continuous
import io.peekandpoke.klang.strudel.StrudelPlayback.Companion.oneShot

/**
 * Interface for Strudel playback implementations.
 * Provides common API for both continuous and one-shot playback modes.
 *
 * Use [continuous] or [oneShot] factory methods to create instances.
 */
interface StrudelPlayback : KlangPlayback {

    companion object {
        /**
         * Create a continuous playback that runs indefinitely until explicitly stopped.
         * This is the default mode for live coding.
         */
        fun continuous(
            playbackId: String,
            pattern: StrudelPattern,
            context: KlangPlaybackContext,
        ): StrudelPlayback = ContinuousStrudelPlayback(
            playbackId = playbackId,
            pattern = pattern,
            context = context,
        )

        /**
         * Create a one-shot playback that stops automatically after [cycles] cycles.
         * Useful for sample previews, auditioning, and other finite-length playback.
         */
        fun oneShot(
            playbackId: String,
            pattern: StrudelPattern,
            context: KlangPlaybackContext,
            cycles: Int = 1,
        ): StrudelPlayback = OneShotStrudelPlayback(
            playbackId = playbackId,
            pattern = pattern,
            context = context,
            cyclesToPlay = cycles,
        )
    }

    /**
     * Configuration options for Strudel playback.
     */
    data class Options(
        /** Number of cycles to look ahead when scheduling events */
        val lookaheadCycles: Double = 2.0,
        /** Cycles per second (tempo) */
        val cyclesPerSecond: Double = 0.5,
        /** Initial cycles prefetch, so that the audio starts flawlessly. Auto-calculated if null. */
        val prefetchCycles: Int? = null,
    )

    /**
     * Update the pattern being played
     */
    fun updatePattern(pattern: StrudelPattern)

    /**
     * Update the cycles per second (tempo)
     */
    fun updateCyclesPerSecond(cps: Double)

    /**
     * Start playback with the given options
     */
    fun start(options: Options = Options())
}
