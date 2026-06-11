package io.peekandpoke.klang.common.infra

/**
 * Thread-safe map with a lock-free read path.
 *
 * Reads go through a `@Volatile` immutable snapshot that is republished on every write.
 * Writes are serialised via an internal [KlangLock]. The trade-off — writes cost O(n)
 * for the snapshot copy, but reads are O(1) and lock-free — fits caches with many
 * reads and few writes (e.g. process-wide name allocations consulted per voice event).
 *
 * The `defaultValue` lambda passed to [getOrPut] runs inside the internal lock, so
 * call-site state mutated by the lambda (e.g. a monotonic counter) is also serialized.
 */
class KlangSnapshotMap<K, V> {

    private val lock = KlangLock()
    private val backing = mutableMapOf<K, V>()

    @kotlin.concurrent.Volatile
    private var snapshot: Map<K, V> = emptyMap()

    /** Number of entries in the current snapshot. */
    val size: Int get() = snapshot.size

    /** Lock-free read. Returns the value for [key] if present in the latest snapshot, else `null`. */
    operator fun get(key: K): V? = snapshot[key]

    /**
     * Return the value for [key] if present; otherwise allocate via [defaultValue],
     * publish a fresh snapshot, and return the new value.
     *
     * [defaultValue] is invoked at most once per [key] across all concurrent callers and
     * runs inside the internal lock — so it is safe to mutate caller-owned state from inside.
     */
    fun getOrPut(key: K, defaultValue: () -> V): V {
        // Fast path: lock-free read of immutable snapshot.
        snapshot[key]?.let { return it }
        // Slow path: synchronized allocation. Re-check inside the lock to cover the race
        // where another thread populated [key] between our snapshot read and the lock.
        return lock.withLock {
            backing[key]?.let { return@withLock it }
            val value = defaultValue()
            backing[key] = value
            snapshot = backing.toMap()
            value
        }
    }

    /** Snapshot copy. Mutations to the backing map after this call do not affect the returned map. */
    fun snapshot(): Map<K, V> = snapshot

    /** Remove all entries. */
    fun clear(): Unit = lock.withLock {
        backing.clear()
        snapshot = emptyMap()
    }
}
