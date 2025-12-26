package io.peekandpoke.klang.audio_bridge.infra

/**
 * Interface for dispatching (sending) events.
 */
interface KlangEventDispatcher<T> {
    /**
     * Tries to push an item into the buffer.
     * Returns true if successful, false if the buffer is full.
     */
    fun dispatch(event: T): Boolean

    /**
     * Clears the buffer.
     */
    fun clear()
}
