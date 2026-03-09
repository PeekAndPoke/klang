package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * Interface for playback implementations.
 * Each music source (Strudel, MIDI, etc.) implements this interface with its own logic.
 */
interface KlangPlayback {
    /**
     * Unique identifier for this playback.
     */
    val playbackId: String

    /**
     * Subscribe to playback signals. Returns an unsubscribe function.
     *
     * All subscriptions are automatically cleaned up when the playback stops.
     */
    fun onSignal(listener: (KlangPlaybackSignal) -> Unit): () -> Unit

    /**
     * Called by the player to deliver feedback messages to this playback.
     * Only messages with matching playbackId will be delivered.
     */
    fun handleFeedback(feedback: KlangCommLink.Feedback)

    /**
     * Start playback
     */
    fun start()

    /**
     * Stop playback
     */
    fun stop()
}
