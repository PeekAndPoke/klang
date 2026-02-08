package io.peekandpoke.klang.audio_engine

/**
 * Simple, synchronous pub/sub signal bus for playback lifecycle signals.
 *
 * Listeners are invoked synchronously on the thread that emits the signal.
 * For UI updates, the subscriber is responsible for dispatching to the appropriate thread/dispatcher.
 *
 * Thread-safety: Listeners list is copied-on-read to avoid ConcurrentModificationException.
 */
class KlangPlaybackSignals {
    private val listeners = mutableListOf<(KlangPlaybackSignal) -> Unit>()

    /**
     * Subscribe to all signals. Returns an unsubscribe function.
     */
    fun subscribe(listener: (KlangPlaybackSignal) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    /**
     * Subscribe to signals of a specific type. Returns an unsubscribe function.
     */
    inline fun <reified T : KlangPlaybackSignal> on(crossinline listener: (T) -> Unit): () -> Unit {
        return subscribe { signal ->
            if (signal is T) listener(signal)
        }
    }

    /**
     * Emit a signal to all subscribers.
     */
    fun emit(signal: KlangPlaybackSignal) {
        // Copy to avoid ConcurrentModificationException if a listener unsubscribes during iteration
        val snapshot = listeners.toList()
        for (listener in snapshot) {
            listener(signal)
        }
    }

    /**
     * Remove all listeners.
     */
    fun clear() {
        listeners.clear()
    }
}
