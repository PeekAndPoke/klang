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
 * `docs/tasks/engine-follow-ups.md` item 2; about 20 in Chrome, inferred). A JS `Map` hashes a string key without
 * allocating, so a lookup here costs nothing. The JVM caches a string's hash and allocates nothing either way; its
 * side is a thin delegate so that both platforms share one type. The rule is in `audio/ref/performance.md`.
 *
 * **The contract**, the same on both platforms (`KlangMapSpec` pins it):
 * - A read-only [Map] to every reader, with three writers of its own ([set], [setAll], [copy]). Not a `MutableMap`:
 *   its mutable views are what a JS `Map` cannot honestly back, and nobody needs them. No reader can write through
 *   it: a cast to a mutable type fails or its write throws, the views and their entries included.
 * - Insertion order for iteration; overwriting a key keeps its position, as Kotlin's `LinkedHashMap` does.
 * - Content equality with any [Map] in both directions, and `hashCode` as [Map] defines it (the sum of
 *   `key.hashCode() xor value.hashCode()` over the entries), so it can stand in for a `LinkedHashMap` anywhere a
 *   `Map` is compared.
 * - `toString` prints as Kotlin's own maps print on the platform: `{a=1.0, b=2.5}` on the JVM, `{a=1, b=2.5}` on JS.
 * - Not thread-safe, and not to be written while it is being iterated: on JS a live iterator does not see
 *   a key added after its last `next()`; on the JVM it throws `ConcurrentModificationException`.
 *
 * **Read it by key; walk it with [forEachEntry].** A lookup allocates nothing, but iterating [entries], [keys] or
 * [values] on JS allocates per entry (the JS iterator's results and one entry object: about 110 bytes per entry on
 * node, where a `LinkedHashMap` walk is free once optimized). [copy], [setAll] from a `KlangMap`, [forEachEntry] and
 * the wire encoder walk without that.
 *
 * The values are stored as they come: on JS a `Double` stays the JS number it was, so a read returns it without a
 * box.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class KlangMap<V>() : Map<String, V> {
    /**
     * An empty map whose JVM table starts at [capacityHint] slots (`LinkedHashMap(capacityHint)`, which still grows
     * once it is 75 percent full); ignored on JS, whose `Map` takes no size. A negative hint counts as 0.
     */
    constructor(capacityHint: Int)

    override val size: Int
    override val keys: Set<String>
    override val values: Collection<V>
    override val entries: Set<Map.Entry<String, V>>

    override fun isEmpty(): Boolean

    override fun containsKey(key: String): Boolean

    override fun containsValue(value: V): Boolean

    override operator fun get(key: String): V?

    /** Stores [value] under [key]; an existing key keeps its position in the iteration order. */
    operator fun set(key: String, value: V)

    /** Stores every entry of [from], in its iteration order ([set] for each). */
    fun setAll(from: Map<String, V>)

    /** A new map with the same entries in the same order; writes to either never reach the other. */
    fun copy(): KlangMap<V>

    /**
     * Calls [action] for every entry, in insertion order: the walk that allocates nothing per entry (on JS the JS
     * `Map`'s own `forEach`, where iterating [entries] makes objects per entry). Not to be written during it.
     */
    fun forEachEntry(action: (key: String, value: V) -> Unit)
}

/** A [KlangMap] holding [entries], in their order (a later duplicate key overwrites in place, as [KlangMap.set]). */
fun <V> klangMapOf(vararg entries: Pair<String, V>): KlangMap<V> = KlangMap<V>().also { map ->
    for (entry in entries) {
        map[entry.first] = entry.second
    }
}

/** A [KlangMap] holding the entries of [from], in its iteration order. */
fun <V> klangMapOf(from: Map<String, V>): KlangMap<V> = KlangMap<V>().also { it.setAll(from) }
