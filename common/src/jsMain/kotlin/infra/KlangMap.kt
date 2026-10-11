/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.infra

/**
 * The Kotlin/JS [KlangMap]: a native JS `Map` behind [AbstractMap], which supplies equality, `hashCode`,
 * `toString`, `containsValue` and the `keys` and `values` views from [entries].
 *
 * [get] and [containsKey] are one call into the JS `Map` each, which hashes a string key without allocating (the
 * reason this class exists, see the `expect` declaration). A value is stored and returned as the JS value it is, so
 * a `Double` read is the number that was stored, never a fresh box.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class KlangMap<V> actual constructor() : AbstractMap<String, V>() {

    /** A JS `Map` takes no size, so the hint is ignored. */
    @Suppress("UNUSED_PARAMETER")
    actual constructor(capacityHint: Int) : this()

    /** The native JS `Map`, string keys to values. */
    private var native: dynamic = js("new Map()")

    actual override val size: Int get() = native.size.unsafeCast<Int>()

    actual override val keys: Set<String> get() = super.keys

    actual override val values: Collection<V> get() = super.values

    actual override fun isEmpty(): Boolean = size == 0

    actual override fun containsKey(key: String): Boolean = native.has(key).unsafeCast<Boolean>()

    actual override fun containsValue(value: V): Boolean = super.containsValue(value)

    actual override operator fun get(key: String): V? {
        val value = native.get(key)

        // A JS `Map` answers `undefined` for an absent key; a stored null comes back as null.
        return if (value === undefined) null else value.unsafeCast<V>()
    }

    actual override val entries: Set<Map.Entry<String, V>> get() = EntrySet()

    actual operator fun set(key: String, value: V) {
        // A JS `Map` keeps an existing key's position on `set`, as Kotlin's `LinkedHashMap` does.
        native.set(key, value)
    }

    actual fun setAll(from: Map<String, V>) {
        // From a KlangMap: the JS `Map`'s own walk, no iterator results and no entry objects.
        if (from is KlangMap<*>) {
            val target = native

            from.native.forEach { value: dynamic, key: dynamic -> target.set(key, value) }

            return
        }

        for ((key, value) in from) {
            native.set(key, value)
        }
    }

    actual fun copy(): KlangMap<V> {
        // Through `setAll`'s native walk: `new Map(source)` goes through the iterator protocol, a `[key, value]`
        // array per entry (measured: 1,385 bytes for 6 entries against 592).
        val result = KlangMap<V>()

        result.setAll(this)

        return result
    }

    actual fun forEachEntry(action: (key: String, value: V) -> Unit) {
        native.forEach { value: dynamic, key: dynamic -> action(key.unsafeCast<String>(), value.unsafeCast<V>()) }
    }

    /** The entries, a live read-only view in insertion order. */
    private inner class EntrySet : AbstractSet<Map.Entry<String, V>>() {
        override val size: Int get() = this@KlangMap.size

        override fun iterator(): Iterator<Map.Entry<String, V>> = EntryIterator(native.entries())
    }

    /** Walks a JS `Map` entries iterator, one [Entry] per step. */
    private class EntryIterator<V>(private val source: dynamic) : Iterator<Map.Entry<String, V>> {
        private var step: dynamic = source.next()

        override fun hasNext(): Boolean = !step.done.unsafeCast<Boolean>()

        override fun next(): Map.Entry<String, V> {
            if (!hasNext()) {
                throw NoSuchElementException()
            }

            val pair = step.value

            step = source.next()

            return Entry(key = pair[0].unsafeCast<String>(), value = pair[1].unsafeCast<V>())
        }
    }

    /** One entry, with [Map.Entry]'s equality and `hashCode` (key xor value). */
    private class Entry<V>(override val key: String, override val value: V) : Map.Entry<String, V> {
        override fun equals(other: Any?): Boolean =
            other is Map.Entry<*, *> && key == other.key && value == other.value

        override fun hashCode(): Int = key.hashCode() xor (value?.hashCode() ?: 0)

        override fun toString(): String = "$key=$value"
    }
}
