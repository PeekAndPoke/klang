package io.peekandpoke.klang.common.infra

/**
 * Interface for dispatching (sending) events.
 */
interface KlangMessageSender<T> {
    /**
     * Tries to push a message into the buffer.
     */
    fun send(msg: T): Boolean

    /**
     * Clears the buffer.
     */
    fun clear()
}
