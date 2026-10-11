/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.infra

/**
 * The Kotlin/JS [KlangMap]: a native JS `Map`. [get], [contains] and [set] are one call into it each, which hashes a
 * string key without allocating (the reason this class exists, see the `expect` declaration); [forEachEntry], [copy]
 * and [setAll] walk with the JS `Map`'s own `forEach`, no iterator results and no entry objects. A value is stored and
 * returned as the JS value it is, so a `Double` read is the number that was stored, never a fresh box.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class KlangMap<V> actual constructor() {

    /** A JS `Map` takes no size, so the hint is ignored. */
    @Suppress("UNUSED_PARAMETER")
    actual constructor(capacityHint: Int) : this()

    /** The native JS `Map`, string keys to values. */
    private val native: dynamic = js("new Map()")

    actual val size: Int get() = native.size.unsafeCast<Int>()

    actual fun isEmpty(): Boolean = size == 0

    actual operator fun get(key: String): V? {
        val value = native.get(key)

        // A JS `Map` answers `undefined` for an absent key; a stored null comes back as null.
        return if (value === undefined) null else value.unsafeCast<V>()
    }

    actual operator fun contains(key: String): Boolean = native.has(key).unsafeCast<Boolean>()

    actual operator fun set(key: String, value: V) {
        // A JS `Map` keeps an existing key's position on `set`, as Kotlin's `LinkedHashMap` does.
        native.set(key, value)
    }

    actual fun setAll(from: KlangMap<V>) {
        val target = native

        from.native.forEach { value: dynamic, key: dynamic -> target.set(key, value) }
    }

    actual fun delete(key: String): V? {
        val value = get(key)

        native.delete(key)

        return value
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

    actual override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is KlangMap<*> || other.size != size) {
            return false
        }

        var same = true

        forEachEntry { key, value ->
            if (same && !(other.contains(key) && other[key] == value)) {
                same = false
            }
        }

        return same
    }

    actual override fun hashCode(): Int {
        var hash = 0

        forEachEntry { key, value -> hash += key.hashCode() xor (value?.hashCode() ?: 0) }

        return hash
    }

    actual override fun toString(): String {
        val out = StringBuilder("{")

        forEachEntry { key, value ->
            if (out.length > 1) {
                out.append(", ")
            }

            out.append(key).append('=').append(value)
        }

        return out.append('}').toString()
    }
}
