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
