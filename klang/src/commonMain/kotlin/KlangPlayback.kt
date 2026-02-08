package io.peekandpoke.klang.audio_engine

/**
 * Interface for playback implementations.
 * Each music source (Strudel, MIDI, etc.) implements this interface with its own logic.
 */
interface KlangPlayback {
    /**
     * Signal bus for subscribing to playback lifecycle signals.
     */
    val signals: KlangPlaybackSignals

    /**
     * Start playback
     */
    fun start()

    /**
     * Stop playback
     */
    fun stop()
}
