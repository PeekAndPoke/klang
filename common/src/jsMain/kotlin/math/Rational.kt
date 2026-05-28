package io.peekandpoke.klang.common.math

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

// =============================================================================
// File-private BigInt constants and helpers.
// Kept at top level (not in Companion) so accesses on the hot path don't pay
// the `Companion.getInstance()` accessor cost in compiled Kotlin/JS.
// =============================================================================

private val BI_ZERO: dynamic = js("BigInt(0)")
private val BI_ONE: dynamic = js("BigInt(1)")
private val BI_NEG_ONE: dynamic = js("BigInt(-1)")

private fun intToBigInt(v: Int): dynamic = js("BigInt(v)")
private fun longToBigInt(v: Long): dynamic = js("BigInt(v.toString())")
private fun doubleToBigInt(v: Double): dynamic = js("BigInt(Math.trunc(v))")
private fun bigIntToInt(v: dynamic): Int = (js("Number(v)") as Double).toInt()
private fun bigIntToLong(v: dynamic): Long = v.toString().toLong()
private fun bigIntToDouble(v: dynamic): Double = js("Number(v)") as Double

private fun bigIntAdd(a: dynamic, b: dynamic): dynamic = js("a + b")
private fun bigIntMul(a: dynamic, b: dynamic): dynamic = js("a * b")
private fun bigIntDiv(a: dynamic, b: dynamic): dynamic = js("a / b")
private fun bigIntRem(a: dynamic, b: dynamic): dynamic = js("a % b")
private fun bigIntNeg(a: dynamic): dynamic = js("(-a)")

private fun bigIntEq(a: dynamic, b: dynamic): Boolean = js("a === b") as Boolean
private fun bigIntGt(a: dynamic, b: dynamic): Boolean = js("a > b") as Boolean
private fun bigIntLt(a: dynamic, b: dynamic): Boolean = js("a < b") as Boolean

private fun bigIntAbs(v: dynamic): dynamic =
    if (bigIntLt(v, BI_ZERO)) bigIntNeg(v) else v

private fun bigIntSign(v: dynamic): Int = when {
    bigIntGt(v, BI_ZERO) -> 1
    bigIntLt(v, BI_ZERO) -> -1
    else -> 0
}

private fun gcd(a: dynamic, b: dynamic): dynamic {
    var x = bigIntAbs(a)
    var y = bigIntAbs(b)
    while (!bigIntEq(y, BI_ZERO)) {
        val t = y
        y = bigIntRem(x, y)
        x = t
    }
    return x
}

private fun doubleToFractionBigInt(
    value: Double,
    epsilon: Double = 1.0E-10,
    maxDenominator: dynamic = js("BigInt(1000000000)"),
): Pair<dynamic, dynamic> {
    var n0: dynamic = BI_ZERO
    var d0: dynamic = BI_ONE
    var n1: dynamic = BI_ONE
    var d1: dynamic = BI_ZERO
    var v = value
    var count = 0

    while (count < 100) {
        val aDouble = floor(v)
        val a: dynamic = doubleToBigInt(aDouble)

        val n2 = bigIntAdd(n0, bigIntMul(a, n1))
        val d2 = bigIntAdd(d0, bigIntMul(a, d1))

        if (bigIntGt(d2, maxDenominator)) break

        n0 = n1; n1 = n2
        d0 = d1; d1 = d2

        val currentVal = bigIntToDouble(n1) / bigIntToDouble(d1)
        if (abs(value - currentVal) < epsilon) break

        if (abs(v - aDouble) < 1e-15) break

        v = 1.0 / (v - aDouble)
        count++
    }
    return Pair(n1, d1)
}

// Factory: computes sign by inspecting the numerator. Callers that already know the sign
// (most arithmetic paths) should use `ofKnownSign` instead.
private fun of(n: dynamic, d: dynamic): Rational {
    val dIsZero = bigIntEq(d, BI_ZERO)
    return Rational.create(
        n = n, d = d,
        isNaN = dIsZero && bigIntEq(n, BI_ZERO),
        isInfinite = dIsZero && !bigIntEq(n, BI_ZERO),
        sgn = bigIntSign(n),
    )
}

// Fast factory: caller asserts the sign. Skips the `bigIntSign(n)` call that `of()` would do.
// Invariant: sgn must match `bigIntSign(n)`, and `d` must be non-negative.
private fun ofKnownSign(n: dynamic, d: dynamic, sgn: Int): Rational {
    val dIsZero = bigIntEq(d, BI_ZERO)
    return Rational.create(
        n = n, d = d,
        isNaN = dIsZero && sgn == 0,
        isInfinite = dIsZero && sgn != 0,
        sgn = sgn,
    )
}

// Fastest factory: caller guarantees a finite value (denominator strictly positive) AND the sign.
// Skips the `bigIntEq(d, BI_ZERO)` NaN/Infinity probe. Use only when the denominator cannot be zero
// (e.g. results of arithmetic where both inputs were finite).
private fun ofFinite(n: dynamic, d: dynamic, sgn: Int): Rational =
    Rational.create(n = n, d = d, isNaN = false, isInfinite = false, sgn = sgn)

private fun createBI(numerator: dynamic, denominator: dynamic): Rational {
    if (bigIntEq(denominator, BI_ZERO)) {
        if (bigIntEq(numerator, BI_ZERO)) return Rational.NaN
        val numIsPos = bigIntGt(numerator, BI_ZERO)
        return ofKnownSign(
            if (numIsPos) BI_ONE else BI_NEG_ONE,
            BI_ZERO,
            if (numIsPos) 1 else -1,
        )
    }
    if (bigIntEq(numerator, BI_ZERO)) return Rational.ZERO

    val common = gcd(numerator, denominator)
    val n = bigIntDiv(numerator, common)
    val d = bigIntDiv(denominator, common)

    // numerator and denominator are both non-zero here. Compute signs from the originals;
    // common is positive (gcd always is), so dividing preserves sign. Result is finite.
    val numPos = bigIntGt(numerator, BI_ZERO)
    val denPos = bigIntGt(denominator, BI_ZERO)
    val finalSgn = if (numPos == denPos) 1 else -1

    return if (denPos) {
        ofFinite(n, d, finalSgn)
    } else {
        ofFinite(bigIntNeg(n), bigIntNeg(d), finalSgn)
    }
}

/**
 * JS actual: Native BigInt-based numerator/denominator.
 * Avoids Kotlin/JS Long boxing overhead by keeping all arithmetic in BigInt-land.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@Serializable(with = RationalStringSerializer::class)
actual class Rational private constructor(
    /** BigInt numerator */
    private val n: dynamic,
    /** BigInt denominator (always non-negative; sign normalized onto numerator). */
    private val d: dynamic,
    /** Precomputed flags to avoid repeated BigInt comparisons on the hot path */
    actual val isNaN: Boolean,
    actual val isInfinite: Boolean,
    /** Precomputed sign: -1, 0, +1. Equals sign(n) given the d-non-negative invariant. */
    private val sgn: Int,
) : Comparable<Rational> {

    actual val numerator: Long get() = bigIntToLong(n)
    actual val denominator: Long get() = bigIntToLong(d)

    actual companion object {

        /** Internal hook so top-level factories can construct the class. */
        internal fun create(n: dynamic, d: dynamic, isNaN: Boolean, isInfinite: Boolean, sgn: Int): Rational =
            Rational(n, d, isNaN, isInfinite, sgn)

        actual val ZERO = ofKnownSign(BI_ZERO, BI_ONE, 0)
        actual val QUARTER = ofKnownSign(BI_ONE, js("BigInt(4)"), 1)
        actual val HALF = ofKnownSign(BI_ONE, js("BigInt(2)"), 1)
        actual val ONE = ofKnownSign(BI_ONE, BI_ONE, 1)
        actual val TWO = ofKnownSign(js("BigInt(2)"), BI_ONE, 1)
        actual val MINUS_ONE = ofKnownSign(BI_NEG_ONE, BI_ONE, -1)
        actual val POSITIVE_INFINITY = ofKnownSign(BI_ONE, BI_ZERO, 1)
        actual val NEGATIVE_INFINITY = ofKnownSign(BI_NEG_ONE, BI_ZERO, -1)
        actual val NaN = ofKnownSign(BI_ZERO, BI_ZERO, 0)

        actual operator fun invoke(value: Long): Rational {
            val sgn = when {
                value > 0L -> 1
                value < 0L -> -1
                else -> 0
            }
            return ofKnownSign(longToBigInt(value), BI_ONE, sgn)
        }

        actual operator fun invoke(value: Int): Rational {
            val sgn = when {
                value > 0 -> 1
                value < 0 -> -1
                else -> 0
            }
            return ofKnownSign(intToBigInt(value), BI_ONE, sgn)
        }

        actual operator fun invoke(value: Double): Rational {
            if (value.isNaN()) return NaN
            if (value.isInfinite()) return if (value > 0) POSITIVE_INFINITY else NEGATIVE_INFINITY
            if (value == 0.0) return ZERO

            val (bn, bd) = doubleToFractionBigInt(abs(value))
            return if (value < 0) ofKnownSign(bigIntNeg(bn), bd, -1) else ofKnownSign(bn, bd, 1)
        }

        actual fun parse(value: String): Rational {
            val str = value.trim()
            if (str == "NaN") return NaN
            if (str == "Infinity" || str == "+Infinity") return POSITIVE_INFINITY
            if (str == "-Infinity") return NEGATIVE_INFINITY

            if ("/" in str) {
                val parts = str.split("/")
                if (parts.size == 2) {
                    val num = parts[0].trim().toDoubleOrNull()
                    val den = parts[1].trim().toDoubleOrNull()

                    if (num != null && den != null) {
                        if (den == 0.0) return NaN
                        return Rational(num) / Rational(den)
                    }
                }
                return NaN
            }

            val d = str.toDoubleOrNull()
            return if (d != null) Rational(d) else NaN
        }

        actual fun create(numerator: Long, denominator: Long): Rational {
            return createBI(longToBigInt(numerator), longToBigInt(denominator))
        }

        actual fun Number.toRational(): Rational = invoke(this.toDouble())

        actual fun List<Rational>.sum(): Rational = fold(ZERO) { acc, r -> acc + r }
    }

    // --- Arithmetic ---

    actual operator fun plus(other: Rational): Rational {
        if (isNaN || other.isNaN) return NaN

        if (isInfinite || other.isInfinite) {
            return if (isInfinite && other.isInfinite && sgn != other.sgn) {
                NaN
            } else {
                if (isInfinite) this else other
            }
        }

        val g = gcd(d, other.d)

        // Coprime denominators (g == 1) — includes every "add an integer/cycle offset" case.
        // Proof: gcd(num, den) = gcd(num, g) = gcd(num, 1) = 1, so the result is already reduced.
        // Skip the divisions and the second gcd entirely. Common on the hot path.
        if (bigIntEq(g, BI_ONE)) {
            val num = bigIntAdd(bigIntMul(n, other.d), bigIntMul(other.n, d))
            val den = bigIntMul(d, other.d)
            val resultSgn = if (sgn == other.sgn) sgn else bigIntSign(num)
            return ofFinite(num, den, resultSgn)
        }

        val otherDOverG = bigIntDiv(other.d, g)
        val dOverG = bigIntDiv(d, g)
        val num = bigIntAdd(bigIntMul(n, otherDOverG), bigIntMul(other.n, dOverG))
        val den = bigIntMul(d, otherDOverG)

        // Smart reduction: inputs are reduced, so gcd(num, den) = gcd(num, g) (much smaller).
        val g2 = gcd(num, g)
        // Result sign matches both inputs when they share a sign; otherwise probe `num`.
        val resultSgn = if (sgn == other.sgn) sgn else bigIntSign(num)
        return if (bigIntEq(g2, BI_ONE)) {
            ofFinite(num, den, resultSgn)
        } else {
            ofFinite(bigIntDiv(num, g2), bigIntDiv(den, g2), resultSgn)
        }
    }

    actual operator fun minus(other: Rational): Rational {
        if (isNaN || other.isNaN) return NaN
        return this + (-other)
    }

    actual operator fun times(other: Rational): Rational {
        if (isNaN || other.isNaN) return NaN

        if (isInfinite || other.isInfinite) {
            if (sgn == 0 || other.sgn == 0) return NaN
            return if (sgn == other.sgn) POSITIVE_INFINITY else NEGATIVE_INFINITY
        }

        return createBI(bigIntMul(n, other.n), bigIntMul(d, other.d))
    }

    actual operator fun div(other: Rational): Rational {
        if (isNaN || other.isNaN) return NaN

        if (other.sgn == 0) {
            return if (sgn == 0) NaN else if (sgn > 0) POSITIVE_INFINITY else NEGATIVE_INFINITY
        }

        return createBI(bigIntMul(n, other.d), bigIntMul(d, other.n))
    }

    actual operator fun rem(other: Rational): Rational {
        if (isNaN || other.isNaN || other.sgn == 0) return NaN
        val div = this / other
        val trunc = createBI(bigIntDiv(div.n, div.d), BI_ONE)
        return this - (other * trunc)
    }

    actual operator fun unaryMinus(): Rational {
        if (isNaN) return NaN
        return ofKnownSign(bigIntNeg(n), d, -sgn)
    }

    actual operator fun plus(other: Number): Rational = this + other.toRational()
    actual operator fun minus(other: Number): Rational = this - other.toRational()
    actual operator fun times(other: Number): Rational = this * other.toRational()
    actual operator fun div(other: Number): Rational = this / other.toRational()
    actual operator fun rem(other: Number): Rational = this % other.toRational()

    // --- Comparison ---

    actual override operator fun compareTo(other: Rational): Int {
        if (isNaN && other.isNaN) return 0
        if (isNaN) return 1
        if (other.isNaN) return -1

        if (sgn != other.sgn) return sgn - other.sgn

        // Same sign — cross-multiply for exact comparison (BigInt can't overflow)
        val lhs = bigIntMul(n, other.d)
        val rhs = bigIntMul(other.n, d)

        return when {
            bigIntGt(lhs, rhs) -> 1
            bigIntLt(lhs, rhs) -> -1
            else -> 0
        }
    }

    // --- Conversions ---

    actual fun toDouble(): Double {
        if (isNaN) return Double.NaN

        if (isInfinite) {
            return if (sgn > 0) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
        }

        return bigIntToDouble(n) / bigIntToDouble(d)
    }

    actual fun toLong(): Long {
        if (isNaN || isInfinite) return 0L
        return bigIntToLong(bigIntDiv(n, d))
    }

    actual fun toInt(): Int {
        if (isNaN || isInfinite) return 0
        return bigIntToInt(bigIntDiv(n, d))
    }

    actual fun toFractionString(): String {
        if (isNaN) return "NaN"
        if (isInfinite) return if (sgn > 0) "Infinity" else "-Infinity"

        return "$n/$d"
    }

    // --- Utilities ---

    actual fun abs(): Rational {
        if (isNaN) return NaN

        return if (sgn < 0) ofKnownSign(bigIntNeg(n), d, 1) else this
    }

    actual fun floor(): Rational {
        if (isNaN) return NaN
        if (isInfinite) return this

        val res = bigIntDiv(n, d)
        val exact = bigIntEq(bigIntRem(n, d), BI_ZERO)

        return if (sgn >= 0 || exact) {
            of(res, BI_ONE)
        } else {
            of(bigIntAdd(res, BI_NEG_ONE), BI_ONE)
        }
    }

    actual fun ceil(): Rational {
        if (isNaN) return NaN
        if (isInfinite) return this

        val res = bigIntDiv(n, d)
        val exact = bigIntEq(bigIntRem(n, d), BI_ZERO)

        return if (sgn <= 0 || exact) {
            of(res, BI_ONE)
        } else {
            of(bigIntAdd(res, BI_ONE), BI_ONE)
        }
    }

    actual fun frac(): Rational {
        if (isNaN) return NaN
        return this - floor()
    }

    actual fun round(): Rational {
        if (isNaN) return NaN
        if (isInfinite) return this

        val abs = abs()
        val floor = abs.floor()
        val frac = abs - floor

        val roundedAbs = if (frac >= HALF) floor + ONE else floor

        return if (sgn < 0) -roundedAbs else roundedAbs
    }

    actual fun exp(): Rational {
        if (isNaN) return NaN
        val result = kotlin.math.exp(toDouble())

        if (result.isNaN() || result.isInfinite()) return NaN

        return Rational(result)
    }

    actual fun pow(exponent: Rational): Rational {
        val result = toDouble().pow(exponent.toDouble())
        if (result.isNaN() || result.isInfinite()) return NaN

        return Rational(result)
    }

    actual fun pow(exponent: Number): Rational = pow(exponent.toRational())

    actual fun log(base: Rational): Rational {
        val result = kotlin.math.log(toDouble(), base.toDouble())
        if (result.isNaN() || result.isInfinite()) return NaN
        return Rational(result)
    }

    actual fun log(base: Number): Rational = log(base.toRational())

    actual fun ln(): Rational {
        val result = kotlin.math.ln(toDouble())
        if (result.isNaN() || result.isInfinite()) return NaN
        return Rational(result)
    }

    actual fun log10(): Rational {
        val result = kotlin.math.log10(toDouble())
        if (result.isNaN() || result.isInfinite()) return NaN
        return Rational(result)
    }

    actual fun log2(): Rational {
        val result = kotlin.math.log2(toDouble())
        if (result.isNaN() || result.isInfinite()) return NaN
        return Rational(result)
    }

    // --- Object overrides ---

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Rational) return false
        return bigIntEq(n, other.n) && bigIntEq(d, other.d)
    }

    override fun hashCode(): Int {
        // Use string-based hash since BigInt has no native hashCode
        var result = n.toString().hashCode()
        result = 31 * result + d.toString().hashCode()
        return result
    }

    override fun toString(): String {
        if (isNaN) return "NaN"
        if (isInfinite) return if (sgn > 0) "Infinity" else "-Infinity"
        if (bigIntEq(d, BI_ONE)) return n.toString()
        return "$n/$d"
    }
}
