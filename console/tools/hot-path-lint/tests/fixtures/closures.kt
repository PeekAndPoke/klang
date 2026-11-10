// Fixture for hot_path_lint: fun-interface, sam-ignitor, captured-var.

package fixtures

fun interface Gen { // expect: fun-interface
    fun next(): Double
}

interface Node {
    fun generate(buffer: DoubleArray, length: Int)
}

class Mapped(private val f: (Double) -> Double) : Node {
    override fun generate(buffer: DoubleArray, length: Int) {
        for (i in 0 until length) {
            buffer[i] = f(buffer[i])
        }
    }
}

fun mapSamples(f: (Double) -> Double): Node = Mapped(f)

inline fun twice(block: () -> Unit) {
    block()
    block()
}

// The SAM constructor of a fun interface, with a captured var: both rules fire on its line.
fun counter(): Gen {
    var n = 0.0

    return Gen { n += 1.0; n } // expect: sam-ignitor, captured-var
}

// An anonymous object that reads a var of its factory: an ObjectRef per access on Kotlin/JS.
fun lastValue(): Node {
    var last = 0.0

    return object : Node { // expect: captured-var
        override fun generate(buffer: DoubleArray, length: Int) {
            for (i in 0 until length) {
                buffer[i] = last
                last = buffer[i]
            }
        }
    }
}

// A lambda handed to a function that stores it.
fun accumulate(): Node {
    var sum = 0.0

    return mapSamples { x -> sum += x; sum } // expect: captured-var
}

// Not findings: a lambda to an inline function, a val, a field of an anonymous object, a local fun of a build.
fun notCaptured(): Node {
    var built = 0
    val gain = 0.5

    twice { built++ }

    fun helper(): Int = built + 1

    built = helper()

    return object : Node {
        private var phase = 0.0

        override fun generate(buffer: DoubleArray, length: Int) {
            for (i in 0 until length) {
                phase += gain
                buffer[i] = phase
            }
        }
    }
}
