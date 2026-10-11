/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.infra

import java.util.Collections

/**
 * The JVM [KlangMap]: a `LinkedHashMap` behind one-line forwards. The JVM caches a string's hash, so its lookups
 * allocate nothing already; this class exists so that both platforms share one type (see the `expect` declaration).
 * Equality, `hashCode` and `toString` are the backing map's, so it compares as any `Map` does.
 *
 * Read-only to every reader, as on JS: [keys], [values] and [entries] are the views of one unmodifiable wrapper made
 * at the first view asked for (`Collections.unmodifiableMap` then builds each view once and keeps it), and
 * the `java.util.Map` writers the compiler generates for a read-only Kotlin `Map` (`put`, `remove`, `putAll`,
 * `clear`) throw `UnsupportedOperationException`.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class KlangMap<V> private constructor(
    private val backing: LinkedHashMap<String, V>,
) : Map<String, V> {

    actual constructor() : this(LinkedHashMap())

    actual constructor(capacityHint: Int) : this(LinkedHashMap(capacityHint.coerceAtLeast(0)))

    /**
     * The read-only face of [backing], whose views [keys], [values] and [entries] hand out; made at the first view
     * asked for, so a map nobody walks never allocates it (most maps are only written and read by key).
     */
    private var readOnly: Map<String, V>? = null

    private fun readOnlyView(): Map<String, V> = readOnly ?: Collections.unmodifiableMap(backing).also { readOnly = it }

    actual override val size: Int get() = backing.size

    actual override val keys: Set<String> get() = readOnlyView().keys

    actual override val values: Collection<V> get() = readOnlyView().values

    actual override val entries: Set<Map.Entry<String, V>> get() = readOnlyView().entries

    actual override fun isEmpty(): Boolean = backing.isEmpty()

    actual override fun containsKey(key: String): Boolean = backing.containsKey(key)

    actual override fun containsValue(value: V): Boolean = backing.containsValue(value)

    actual override operator fun get(key: String): V? = backing[key]

    actual operator fun set(key: String, value: V) {
        backing[key] = value
    }

    actual fun setAll(from: Map<String, V>) {
        // From a KlangMap: its backing map directly, not through the read-only views' wrapped entries.
        if (from is KlangMap<*>) {
            @Suppress("UNCHECKED_CAST")
            backing.putAll((from as KlangMap<V>).backing)

            return
        }

        backing.putAll(from)
    }

    actual fun copy(): KlangMap<V> = KlangMap(LinkedHashMap(backing))

    actual fun forEachEntry(action: (key: String, value: V) -> Unit) {
        for ((key, value) in backing) {
            action(key, value)
        }
    }

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other is KlangMap<*> -> backing == other.backing
        else -> backing == other
    }

    override fun hashCode(): Int = backing.hashCode()

    override fun toString(): String = backing.toString()
}
