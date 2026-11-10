package io.peekandpoke.klang.workshop.storage

import io.peekandpoke.ultra.streams.Unsubscribe
import kotlinx.browser.window
import org.w3c.dom.Storage
import org.w3c.dom.StorageEvent
import org.w3c.dom.events.Event

/**
 * A browser's [Storage] as a [KeyValueStore]: `localStorage` (the site's, shared by its tabs, which are told of each
 * other's changes) or `sessionStorage` (one tab's: it lives through a reload and is no other tab's). A failure (no
 * storage, the quota) is told to [onProblem] and the app goes on.
 */
class BrowserKeyValueStore(
    private val storage: Storage,
    private val onProblem: (message: String, cause: Throwable?) -> Unit = { _, _ -> },
) : KeyValueStore {

    companion object {
        /**
         * The browser's [storage] as a store, or memory where the browser gives none: with the site's data blocked,
         * already asking for `localStorage` throws. The app then runs and keeps nothing beyond this visit.
         */
        fun orMemory(onProblem: (message: String, cause: Throwable?) -> Unit = { _, _ -> }, storage: () -> Storage): KeyValueStore = try {
            // Some browsers give null where the storage is switched off, others throw
            BrowserKeyValueStore(storage = storage().unsafeCast<Storage?>() ?: error("No storage"), onProblem = onProblem)
        } catch (e: Throwable) {
            onProblem("The browser gives no storage: nothing is kept beyond this visit", e)
            MemoryKeyValueStore()
        }
    }

    override fun read(key: String): String? = try {
        storage.getItem(key)
    } catch (e: Throwable) {
        onProblem("Could not read $key", e)
        null
    }

    override fun write(key: String, text: String): Boolean = try {
        storage.setItem(key, text)
        true
    } catch (e: Throwable) {
        onProblem("Could not keep $key", e)
        false
    }

    override fun remove(key: String) {
        try {
            storage.removeItem(key)
        } catch (e: Throwable) {
            onProblem("Could not forget $key", e)
        }
    }

    /** The browser's `storage` event: for `localStorage`, fired in every other tab of the site when one writes, never in
     * the writer; a tab's `sessionStorage` has no other writer. */
    override fun watch(key: String, onChange: (text: String?) -> Unit): Unsubscribe {
        val listener: (Event) -> Unit = { event ->
            val change = event as? StorageEvent

            if (change != null && change.storageArea === storage && change.key == key) {
                onChange(change.newValue)
            }
        }

        window.addEventListener("storage", listener)

        return { window.removeEventListener("storage", listener) }
    }
}
