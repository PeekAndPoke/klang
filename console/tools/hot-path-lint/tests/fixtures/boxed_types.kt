// Fixture for hot_path_lint: boxed-type. Each `expect:` names the rules that must fire on its line, and no other
// line may fire. A Long in this comment, "a Long in a string" and 'x' inside a comment are not code.

package fixtures

class Clock(private val startNanos: Long) { // expect: boxed-type
    private val codes: CharArray = CharArray(4) // expect: boxed-type

    fun elapsed(now: Double): Double = now - startNanos.toDouble()

    fun big(): Int = (1L shl 40).toInt() // expect: boxed-type

    fun cast(x: Any): Int = (x as Short).toInt() // expect: boxed-type

    fun bytes(n: Int): Int = n.toByte().toInt() // expect: boxed-type

    fun separator(): Int = ':'.code // expect: boxed-type

    fun generic(xs: List<ULong>): Int = xs.size // expect: boxed-type

    fun limit(): Double = Long.MAX_VALUE.toDouble() // expect: boxed-type

    fun label(): String = "Long and Char in a string are text"

    // A name that only contains a banned word is no type: LongRange, isShort, Charge.
    fun isShortNote(longest: Int, charge: Int): Boolean = longest < charge
}
