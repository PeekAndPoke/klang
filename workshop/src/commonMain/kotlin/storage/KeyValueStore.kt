package io.peekandpoke.klang.workshop.storage

import io.peekandpoke.ultra.streams.Unsubscribe

/**
 * Texts under keys: the layer a [DocumentStorage] keeps its document in. The browser's storage of a site or of a
 * tab, a server, or memory ([MemoryKeyValueStore]).
 */
interface KeyValueStore {
    /** The text kept under [key]; null when there is none or it cannot be read. */
    fun read(key: String): String?

    /** Keeps [text] under [key]; false when it could not be kept. */
    fun write(key: String, text: String): Boolean

    /** Forgets what is kept under [key]. */
    fun remove(key: String)

    /**
     * Tells when what is kept under [key] was changed from elsewhere (another tab, a server), with the new text, null
     * when it was removed; never for this store's own [write]. The way to stop comes back.
     */
    fun watch(key: String, onChange: (text: String?) -> Unit): Unsubscribe
}

/** A store that lives as long as it is held: for a spec, and for an app run without a place to keep things. */
class MemoryKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {

    private val kept = initial.toMutableMap()
    private val watchers = mutableListOf<Pair<String, (String?) -> Unit>>()

    /** Everything kept now. */
    val all: Map<String, String> get() = kept.toMap()

    override fun read(key: String): String? = kept[key]

    override fun write(key: String, text: String): Boolean {
        kept[key] = text

        return true
    }

    override fun remove(key: String) {
        kept.remove(key)
    }

    override fun watch(key: String, onChange: (text: String?) -> Unit): Unsubscribe {
        val watcher = key to onChange

        watchers.add(watcher)

        return { watchers.remove(watcher) }
    }

    /** As another tab or a server would: [text] is kept under [key] (null removes it), and the watchers are told. */
    fun changeFromElsewhere(key: String, text: String?) {
        if (text == null) {
            kept.remove(key)
        } else {
            kept[key] = text
        }

        watchers.filter { it.first == key }.forEach { it.second(text) }
    }
}
