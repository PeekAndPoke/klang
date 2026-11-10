package io.peekandpoke.klang.workshop.storage

import io.peekandpoke.ultra.streams.StreamHandler
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.StreamStorage
import io.peekandpoke.ultra.streams.Unsubscribe

/** A [StreamStorage] that tells when its value was changed from elsewhere: another tab, a server. */
interface WatchedStreamStorage<T> : StreamStorage<T> {
    /** Calls [onChange] with the value whenever it was changed from elsewhere; the way to stop comes back. */
    fun watch(onChange: (T) -> Unit): Unsubscribe
}

/**
 * Keeps this stream's value in [storage]: what the storage holds is shown at once, every value published is written,
 * and a [WatchedStreamStorage]'s changes from elsewhere are shown without being written back.
 *
 * Ultra's `persistInLocalStorage` is this with one storage built in; here the storage is the caller's, so it can be
 * the browser's, a tab's, a server's, or several in layers.
 */
fun <T> StreamSource<T>.persist(storage: StreamStorage<T>): PersistedStreamSource<T> =
    PersistedStreamSource(storage = storage, wrapped = this)

/** A [StreamSource] kept in a [StreamStorage] ([persist]). */
class PersistedStreamSource<T>(
    private val storage: StreamStorage<T>,
    private val wrapped: StreamSource<T>,
) : StreamSource<T> {

    private val unwatch: Unsubscribe

    init {
        try {
            storage.read()?.let { wrapped(it) }
        } catch (_: Throwable) {
            // The storage could not be read: the stream keeps the value it has
        }

        unwatch = (storage as? WatchedStreamStorage<T>)?.watch { wrapped(it) } ?: {}
    }

    override val initialValue: T get() = wrapped.initialValue

    override val subscriptions: Set<StreamHandler<T>> get() = wrapped.subscriptions

    override fun removeAllSubscriptions() {
        wrapped.removeAllSubscriptions()
    }

    override fun invoke(): T = wrapped()

    /** Written first, then shown: a listener that publishes again writes after this value, never before it. */
    override fun invoke(next: T) {
        storage.write(next)
        wrapped(next)
    }

    override fun subscribeToStream(sub: (T) -> Unit): Unsubscribe = wrapped.subscribeToStream(sub)

    /** Stops following the storage's changes from elsewhere. */
    fun close() {
        unwatch()
    }
}
