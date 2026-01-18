package io.peekandpoke.klang.audio_engine

/**
 * Interface for playback implementations.
 * Each music source (Strudel, MIDI, etc.) implements this interface with its own logic.
 */
interface KlangPlayback {
    /**
     * Start playback
     */
    fun start()

    /**
     * Stop playback
     */
    fun stop()
}