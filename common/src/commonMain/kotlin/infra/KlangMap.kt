/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.infra

/**
 * A map from `String` keys to [V] values with a dedicated implementation per platform: a native JS `Map` on
 * Kotlin/JS, a `LinkedHashMap` on the JVM.
 *
 * **Why it exists.** On Kotlin/JS every `HashMap`/`HashSet` operation with a non-null key goes through the stdlib's
 * generic `hashCode(dynamic)`, whose `else -> throw` arm compiles to a closure that captures the `typeof` result, so
 * V8 allocates a function context on EVERY call: 40 bytes per lookup on node (measured, engine follow-up 2,
 * `docs/tasks/engine-follow-ups.md` item 2). A JS `Map` hashes a string key without allocating, so a lookup here
 * costs nothing. The JVM caches a string's hash and allocates nothing either way; its side is a thin wrapper so that
 * both platforms share one type. The rule is in `audio/ref/performance.md`.
 *
 * **Not a `Map`** (maintainer, 2026-10-11): no `kotlin.collections` supertype, so no caller can walk it, compare it
 * with a stdlib map or hand it to a stdlib function by accident and lose what it is for. It has what its callers
 * need and nothing else; a helper is an extension function where a caller really needs one (the two in this file,
 * [toMap] and [toKlangMap], are the interop for tests and tools, never an engine path).
 *
 * **The contract**, the same on both platforms (`KlangMapSpec` pins it):
 * - Read by key ([get], [contains]); walk with [forEachEntry], which allocates nothing per entry (a JS `Map`'s own
 *   `forEach`). [copy] and [setAll] walk the same way.
 * - Insertion order; overwriting a key keeps its position; [delete] removes a key, and a later [set] of it goes last.
 * - Equality and `hashCode` by content, against another `KlangMap` only (`hashCode` as `Map` defines it: the sum of
 *   `key.hashCode() xor value.hashCode()`); `toString` as Kotlin's own maps print on the platform (`{a=1.0, b=2.5}` on
 *   the JVM, `{a=1, b=2.5}` on JS).
 * - Not thread-safe, and not to be written while [forEachEntry] runs.
 *
 * The values are stored as they come: on JS a `Double` stays the JS number it was, so a read returns it without a
 * box.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class KlangMap<V>() {
    /**
     * An empty map whose JVM table starts at [capacityHint] slots (`LinkedHashMap(capacityHint)`, which still grows
     * once it is 75 percent full); ignored on JS, whose `Map` takes no size. A negative hint counts as 0.
     */
    constructor(capacityHint: Int)

    /** How many keys the map holds. */
    val size: Int

    /** True while the map holds no key. */
    fun isEmpty(): Boolean

    /** The value stored under [key], or null when the key is absent (or a null value is stored). */
    operator fun get(key: String): V?

    /** True when [key] is present (`key in map`). */
    operator fun contains(key: String): Boolean

    /** Stores [value] under [key]; an existing key keeps its position in the iteration order. */
    operator fun set(key: String, value: V)

    /** Stores every entry of [from], in its order ([set] for each). */
    fun setAll(from: KlangMap<V>)

    /** Removes [key] and returns the value it held, or null when it was absent. */
    fun delete(key: String): V?

    /** A new map with the same entries in the same order; writes to either never reach the other. */
    fun copy(): KlangMap<V>

    /** Calls [action] for every entry, in insertion order, allocating nothing per entry. */
    fun forEachEntry(action: (key: String, value: V) -> Unit)

    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int

    override fun toString(): String
}

/** A [KlangMap] holding [entries], in their order (a later duplicate key overwrites in place, as [KlangMap.set]). */
fun <V> klangMapOf(vararg entries: Pair<String, V>): KlangMap<V> = KlangMap<V>().also { map ->
    for (entry in entries) {
        map[entry.first] = entry.second
    }
}

/** A plain `Map` copy of this map's entries, in order: interop for tests and tools, never an engine path. */
fun <V> KlangMap<V>.toMap(): Map<String, V> = LinkedHashMap<String, V>(size).also { out ->
    forEachEntry { key, value -> out[key] = value }
}

/** A [KlangMap] holding this map's entries, in order: interop for tests and tools, never an engine path. */
fun <V> Map<String, V>.toKlangMap(): KlangMap<V> = KlangMap<V>().also { out ->
    for ((key, value) in this) {
        out[key] = value
    }
}
