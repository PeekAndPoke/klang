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
 * with the map (`Collections.unmodifiableMap` builds each view once and keeps it, so a read allocates nothing), and
 * the `java.util.Map` writers the compiler generates for a read-only Kotlin `Map` (`put`, `remove`, `putAll`,
 * `clear`) throw `UnsupportedOperationException`.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class KlangMap<V> private constructor(
    private val backing: LinkedHashMap<String, V>,
) : Map<String, V> {

    actual constructor() : this(LinkedHashMap())

    /** The read-only face of [backing], whose views [keys], [values] and [entries] hand out. */
    private val readOnly: Map<String, V> = Collections.unmodifiableMap(backing)

    actual override val size: Int get() = backing.size

    actual override val keys: Set<String> get() = readOnly.keys

    actual override val values: Collection<V> get() = readOnly.values

    actual override val entries: Set<Map.Entry<String, V>> get() = readOnly.entries

    actual override fun isEmpty(): Boolean = backing.isEmpty()

    actual override fun containsKey(key: String): Boolean = backing.containsKey(key)

    actual override fun containsValue(value: V): Boolean = backing.containsValue(value)

    actual override operator fun get(key: String): V? = backing[key]

    actual operator fun set(key: String, value: V) {
        backing[key] = value
    }

    actual fun setAll(from: Map<String, V>) {
        backing.putAll(from)
    }

    actual fun copy(): KlangMap<V> = KlangMap(LinkedHashMap(backing))

    override fun equals(other: Any?): Boolean = this === other || backing == other

    override fun hashCode(): Int = backing.hashCode()

    override fun toString(): String = backing.toString()
}
