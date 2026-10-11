/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.infra

/**
 * The JVM [KlangMap]: a `LinkedHashMap` behind one-line forwards. The JVM caches a string's hash, so its lookups
 * allocate nothing already; this class exists so that both platforms share one type (see the `expect` declaration).
 * Equality, `hashCode` and `toString` are the backing map's.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class KlangMap<V> private constructor(
    private val backing: LinkedHashMap<String, V>,
) {

    actual constructor() : this(LinkedHashMap())

    actual constructor(capacityHint: Int) : this(LinkedHashMap(capacityHint.coerceAtLeast(0)))

    actual val size: Int get() = backing.size

    actual fun isEmpty(): Boolean = backing.isEmpty()

    actual operator fun get(key: String): V? = backing[key]

    actual operator fun contains(key: String): Boolean = backing.containsKey(key)

    actual operator fun set(key: String, value: V) {
        backing[key] = value
    }

    actual fun setAll(from: KlangMap<V>) {
        backing.putAll(from.backing)
    }

    actual fun delete(key: String): V? = backing.remove(key)

    actual fun copy(): KlangMap<V> = KlangMap(LinkedHashMap(backing))

    actual fun forEachEntry(action: (key: String, value: V) -> Unit) {
        for ((key, value) in backing) {
            action(key, value)
        }
    }

    actual override fun equals(other: Any?): Boolean = this === other || (other is KlangMap<*> && backing == other.backing)

    actual override fun hashCode(): Int = backing.hashCode()

    actual override fun toString(): String = backing.toString()
}
