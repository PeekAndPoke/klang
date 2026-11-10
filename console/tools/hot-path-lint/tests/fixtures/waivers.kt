// Fixture for hot_path_lint: waivers. The test reads this file's findings directly (a waiver line cannot carry an
// `expect:` comment of its own: it would read as the waiver's reason).

package fixtures

class Waived {
    private var grow = DoubleArray(0)

    fun generate(buffer: DoubleArray, length: Int) {
        val a = DoubleArray(length) // hot-path-lint: allow alloc-array a test rig's buffer, never in a song

        // hot-path-lint: allow alloc-array the first block only, measured
        // (a second comment line between the waiver and the code is fine)
        val b = DoubleArray(length)

        // hot-path-lint: allow alloc
        val c = DoubleArray(length)

        // hot-path-lint: allow exception nothing on this line throws
        val d = length + 1

        // hot-path-lint: allow alloc-arrays a misspelt rule
        val e = d + 1

        // hot-path-lint: allow alloc-pair the waiver names the wrong rule
        val f = DoubleArray(length)

        buffer[0] = a[0] + b[0] + c[0] + e + f[0]
    }
}
