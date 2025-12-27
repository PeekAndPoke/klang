package io.peekandpoke.klang.audio_bridge.infra

/**
 * Interface for receiving events.
 */
interface KlangMessageReceiver<T> {
    /**
     * Tries to pop an item from the buffer.
     * Returns the item or null if the buffer is empty.
     */
    fun receive(): T?
}
