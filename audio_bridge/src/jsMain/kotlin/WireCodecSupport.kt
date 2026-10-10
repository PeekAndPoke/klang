/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge.wire

/**
 * Hand-written JS-interop helpers used by the generated wire codec (see `:audio-wire-codec-ksp` and
 * `docs/tasks/worklet-codec-ksp.md`). Kept hand-written because `dynamic`/`js(...)` interop has sharp edges
 * (e.g. `.also {}` on a `dynamic` doesn't bind `it`) — the generator emits straight-line calls to these.
 */

/** Fresh empty JS object. */
fun wireObj(): dynamic = js("({})")

/** Kotlin List → JS array, element-encoded. */
inline fun <T> wireEncodeList(list: List<T>, enc: (T) -> dynamic): dynamic {
    val arr: dynamic = js("([])")
    for (e in list) arr.push(enc(e))
    return arr
}

/** JS array → Kotlin List, element-decoded. */
inline fun <T> wireDecodeList(arr: dynamic, dec: (dynamic) -> T): List<T> {
    val n: Int = arr.length.unsafeCast<Int>()
    val out = ArrayList<T>(n)
    var i = 0
    while (i < n) {
        out.add(dec(arr[i]))
        i++
    }
    return out
}

/** Kotlin Set → JS array, element-encoded. */
inline fun <T> wireEncodeSet(set: Set<T>, enc: (T) -> dynamic): dynamic {
    val arr: dynamic = js("([])")
    for (e in set) arr.push(enc(e))
    return arr
}

/** JS array → Kotlin Set, element-decoded (insertion order preserved via LinkedHashSet). */
inline fun <T> wireDecodeSet(arr: dynamic, dec: (dynamic) -> T): Set<T> {
    val n: Int = arr.length.unsafeCast<Int>()
    val out = LinkedHashSet<T>(n)
    var i = 0
    while (i < n) {
        out.add(dec(arr[i]))
        i++
    }
    return out
}

/** Map<String, Double> → JS object. */
fun wireEncodeStringDoubleMap(m: Map<String, Double>): dynamic {
    val o = wireObj()
    for ((k, v) in m) o[k] = v
    return o
}

/** JS object → Map<String, Double> (insertion order preserved via Object.keys). */
fun wireDecodeStringDoubleMap(o: dynamic): Map<String, Double> {
    val keys = js("Object.keys(o)").unsafeCast<Array<String>>()
    val out = LinkedHashMap<String, Double>(keys.size)
    for (k in keys) out[k] = o[k].unsafeCast<Double>()
    return out
}

// ── Identity across the wire (`@WireShared`) ───────────────────────────────────────────────────────────────────

/** How many root codec calls are running now; the identity table lives while this is above 0. */
@PublishedApi
internal var wireScopeDepth: Int = 0

/**
 * The identity table of the running root call, a JS `Map`, or null until a `@WireShared` value is met. One call runs
 * one direction, so one table serves both: Kotlin instance to JS object while encoding, JS object to Kotlin instance
 * while decoding. A JS `Map` compares keys by identity, never by `equals`.
 */
@PublishedApi
internal var wireSeen: dynamic = null

/**
 * Runs the codec of a `@WireFormat` root: opens the identity table's lifetime at the outermost call and drops the table
 * when it ends, so nothing is remembered from one message to the next.
 */
inline fun <T> wireScoped(block: () -> T): T {
    wireScopeDepth++

    try {
        return block()
    } finally {
        wireScopeDepth--

        if (wireScopeDepth == 0) {
            wireSeen = null
        }
    }
}

/** Encodes a `@WireShared` value once per root call: a second reference to [v] gets the same JS object. */
inline fun wireEncodeShared(v: Any, enc: () -> dynamic): dynamic = wireShared(key = v, make = enc)

/** Decodes a `@WireShared` value once per root call: a second reference to [o] gets the same Kotlin object. */
inline fun <T> wireDecodeShared(o: dynamic, dec: () -> T): T = wireShared(key = o, make = dec).unsafeCast<T>()

@PublishedApi
internal inline fun wireShared(key: Any?, make: () -> Any?): dynamic {
    if (wireScopeDepth == 0) {
        return make()
    }

    if (wireSeen == null) {
        wireSeen = js("new Map()")
    }

    val seen = wireSeen
    val hit = seen.get(key)

    if (jsTypeOf(hit) != "undefined") {
        return hit
    }

    val made = make()
    seen.set(key, made)

    return made
}
