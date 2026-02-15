package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_engine.KlangPlayback

/**
 * Interface for Strudel playback implementations.
 * Provides common API for both continuous and one-shot playback modes.
 */
interface StrudelPlayback : KlangPlayback {

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