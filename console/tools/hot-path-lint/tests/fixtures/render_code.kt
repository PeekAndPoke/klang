// Fixture for hot_path_lint: the render-code rules. `generate` and `process` are roots, `helper` is hot because
// `generate` calls it, `reset` is cold by name and `describe` is never called from render code.

package fixtures

typealias Buf = DoubleArray

data class Hit(val at: Int, val level: Double)

class Holder(val value: Double)

class Voice {
    private var scratch = DoubleArray(0)
    private var cache: Holder? = null
    private var history = DoubleArray(4)
    private val names = HashMap<String, Int>()
    private var mode = Mode.A

    enum class Mode { A, B }

    fun generate(buffer: DoubleArray, length: Int, tag: String?) {
        val tmp = DoubleArray(length) // expect: alloc-array
        val view: Buf = Buf(length) // expect: alloc-array
        val hits = mutableListOf<Hit>() // expect: alloc-collection
        val hit = Hit(at = 0, level = 1.0) // expect: alloc-object
        val pair = 1 to 2 // expect: alloc-pair
        val label = "voice $length" // expect: alloc-string
        val joined = "v" + length // expect: alloc-string
        val lookup = names.getOrPut("a") { 1 } // expect: map-op
        val maybe: Double? = cache?.value // expect: nullable-num
        val again = hit.copy(level = 0.5) // expect: alloc-object
        val listener = { x: Double -> x * 2.0 } // expect: alloc-lambda
        val ref = ::helper // expect: alloc-object
        val first = tag!!.length // expect: not-null-assert

        require(length >= 0) // expect: exception

        if (length > 4096) {
            throw IllegalStateException("too long") // expect: exception
        }

        history.copyInto(scratch) // expect: alloc-array
        helper(buffer, length)

        if (length == 0) {
            reset() // cold by name: its allocation is not render code
        }

        for (i in 0 until length) {
            buffer[i] = when (mode) { // expect: when-in-loop
                Mode.A -> buffer[i] * 0.5
                Mode.B -> buffer[i] * 0.25
            }
        }

        for (i in 0 until length) {
            buffer[i] = when (mode) {
                Mode.A -> buffer[i]
                else -> 0.0
            }
        }

        // Not findings: resize on shape change, a cache filled on a miss, a field store behind a guard.
        if (scratch.size < length) {
            scratch = DoubleArray(length)
        }

        val held = cache ?: Holder(1.0).also { cache = it }
        val to = length
        val from = if (to > 0) to else 0

        consume(tmp, view, hits, pair, label, joined, lookup, maybe, again, listener, ref, first, held, from)
    }

    private fun helper(buffer: DoubleArray, length: Int) {
        val sorted = buffer.sorted() // expect: alloc-collection
        val gate = if (length > 0) 1.0 else Double.NaN // expect: nan-merge
        val modes = Mode.values() // expect: alloc-array
        val peak = maxOf(buffer[0], buffer[1], buffer[2], buffer[3]) // expect: alloc-array
        val small = maxOf(buffer[0], buffer[1], buffer[2])
        val count = names.keys.size + Mode.entries[0].ordinal

        for (key in names.keys) { // expect: map-op
            consume(key)
        }

        for ((name, index) in names) { // expect: map-op
            consume(name, index)
        }

        consume(sorted, gate, modes, peak, small, count)
    }

    fun process(buffer: DoubleArray) {
        try {
            helper(buffer, buffer.size)
        } catch (e: Exception) { // expect: exception
            buffer.fill(0.0)
        }
    }

    // A hot function's signature: a nullable number crosses as a box on the JVM.
    fun processLevel(level: Double?): Double = level ?: 0.0 // expect: nullable-num

    fun reset() {
        val fresh = DoubleArray(16)
        scratch = fresh
    }

    fun describe(): String = "voice ${names.size}"

    private fun consume(vararg xs: Any?) {}
}
