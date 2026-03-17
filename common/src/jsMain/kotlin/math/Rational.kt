package io.peekandpoke.klang.common.math

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/**
 * JS actual: Native BigInt-based numerator/denominator.
 * Avoids Kotlin/JS Long boxing overhead by keeping all arithmetic in BigInt-land.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@Serializable(with = RationalStringSerializer::class)
actual class Rational private constructor(
    /** BigInt numerator */
    private val n: dynamic,
    /** BigInt denominator */
    private val d: dynamic,
    /** Precomputed flags to avoid repeated BigInt comparisons on the hot path */
    actual val isNaN: Boolean,
    actual val isInfinite: Boolean,
) : Comparable<Rational> {

    actual val numerator: Long get() = bigIntToLong(n)
    actual val denominator: Long get() = bigIntToLong(d)

    actual companion object {
        // BigInt constants
        private val BI_ZERO: dynamic = js("BigInt(0)")
        private val BI_ONE: dynamic = js("BigInt(1)")
        private val BI_NEG_ONE: dynamic = js("BigInt(-1)")

        /** Private factory that precomputes isNaN/isInfinite flags */
        private fun of(n: dynamic, d: dynamic): Rational {
            val dIsZero = bigIntEq(d, BI_ZERO)
            return Rational(
                n = n, d = d,
                isNaN = dIsZero && bigIntEq(n, BI_ZERO),
                isInfinite = dIsZero && !bigIntEq(n, BI_ZERO),
            )
        }

        actual val ZERO = of(BI_ZERO, BI_ONE)
        actual val QUARTER = of(BI_ONE, js("BigInt(4)"))
        actual val HALF = of(BI_ONE, js("BigInt(2)"))
        actual val ONE = of(BI_ONE, BI_ONE)
        actual val TWO = of(js("BigInt(2)"), BI_ONE)
        actual val MINUS_ONE = of(BI_NEG_ONE, BI_ONE)
        actual val POSITIVE_INFINITY = of(BI_ONE, BI_ZERO)
        actual val NEGATIVE_INFINITY = of(BI_NEG_ONE, BI_ZERO)
        actual val NaN = of(BI_ZERO, BI_ZERO)

        actual operator fun invoke(value: Long): Rational = of(longToBigInt(value), BI_ONE)

        actual operator fun invoke(value: Int): Rational = of(intToBigInt(value), BI_ONE)

        actual operator fun invoke(value: Double): Rational {
            if (value.isNaN()) return NaN
            if (value.isInfinite()) return if (value > 0) POSITIVE_INFINITY else NEGATIVE_INFINITY
            if (value == 0.0) return ZERO

            val (bn, bd) = doubleToFractionBigInt(abs(value))
            return if (value < 0) of(bigIntNeg(bn), bd) else of(bn, bd)
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

        /** Internal create that stays in BigInt-land */
        private fun createBI(numerator: dynamic, denominator: dynamic): Rational {
            if (bigIntEq(denominator, BI_ZERO)) {
                return if (bigIntEq(numerator, BI_ZERO)) {
                    NaN
                } else {
                    of(if (bigIntGt(numerator, BI_ZERO)) BI_ONE else BI_NEG_ONE, BI_ZERO)
                }
            }
            if (bigIntEq(numerator, BI_ZERO)) return ZERO

            val common = gcd(numerator, denominator)
            val n = bigIntDiv(numerator, common)
            val d = bigIntDiv(denominator, common)

            return if (bigIntLt(d, BI_ZERO)) of(bigIntNeg(n), bigIntNeg(d)) else of(n, d)
        }

        actual fun Number.toRational(): Rational = invoke(this.toDouble())

        actual fun List<Rational>.sum(): Rational = fold(ZERO) { acc, r -> acc + r }

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

        // --- BigInt helper functions ---

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
        private fun bigIntGte(a: dynamic, b: dynamic): Boolean = js("a >= b") as Boolean

        private fun bigIntAbs(v: dynamic): dynamic =
            if (bigIntLt(v, BI_ZERO)) bigIntNeg(v) else v

        private fun bigIntSign(v: dynamic): Int = when {
            bigIntGt(v, BI_ZERO) -> 1
            bigIntLt(v, BI_ZERO) -> -1
            else -> 0
        }
    }

    // --- Arithmetic ---

    actual operator fun plus(other: Rational): Rational {
        if (isNaN || other.isNaN) return NaN

        if (isInfinite || other.isInfinite) {
            return if (isInfinite && other.isInfinite && bigIntSign(n) != bigIntSign(other.n)) {
                NaN
            } else {
                if (isInfinite) this else other
            }
        }

        val g = gcd(d, other.d)
        val num = bigIntAdd(bigIntMul(n, bigIntDiv(other.d, g)), bigIntMul(other.n, bigIntDiv(d, g)))
        val den = bigIntMul(d, bigIntDiv(other.d, g))

        return createBI(num, den)
    }

    actual operator fun minus(other: Rational): Rational {
        if (isNaN || other.isNaN) return NaN
        return this + (-other)
    }

    actual operator fun times(other: Rational): Rational {
        if (isNaN || other.isNaN) return NaN
        if (isInfinite || other.isInfinite) {
            if (this == ZERO || other == ZERO) return NaN
            val thisSign = bigIntSign(n)
            val otherSign = bigIntSign(other.n)
            return if (thisSign == otherSign) POSITIVE_INFINITY else NEGATIVE_INFINITY
        }

        return createBI(bigIntMul(n, other.n), bigIntMul(d, other.d))
    }

    actual operator fun div(other: Rational): Rational {
        if (isNaN || other.isNaN) return NaN
        if (bigIntEq(other.n, BI_ZERO)) {
            return if (bigIntEq(n, BI_ZERO)) {
                NaN
            } else {
                if (bigIntGt(n, BI_ZERO)) POSITIVE_INFINITY else NEGATIVE_INFINITY
            }
        }

        return createBI(bigIntMul(n, other.d), bigIntMul(d, other.n))
    }

    actual operator fun rem(other: Rational): Rational {
        if (isNaN || other.isNaN || bigIntEq(other.n, BI_ZERO)) return NaN
        val div = this / other
        val trunc = createBI(bigIntDiv(div.n, div.d), BI_ONE)
        return this - (other * trunc)
    }

    actual operator fun unaryMinus(): Rational {
        if (isNaN) return NaN
        return of(bigIntNeg(n), d)
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

        val thisSign = bigIntSign(n)
        val otherSign = bigIntSign(other.n)

        if (thisSign != otherSign) return thisSign - otherSign

        // Cross-multiply for exact comparison (BigInt can't overflow)
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
        if (isInfinite) return if (bigIntGt(n, BI_ZERO)) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
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
        if (isInfinite) return if (bigIntGt(n, BI_ZERO)) "Infinity" else "-Infinity"
        return "$n/$d"
    }

    // --- Utilities ---

    actual fun abs(): Rational {
        if (isNaN) return NaN
        return if (bigIntLt(n, BI_ZERO)) of(bigIntNeg(n), d) else this
    }

    actual fun floor(): Rational {
        if (isNaN) return NaN
        if (isInfinite) return this

        val res = bigIntDiv(n, d)
        val exact = bigIntEq(bigIntRem(n, d), BI_ZERO)
        return if (bigIntGte(n, BI_ZERO) || exact) of(res, BI_ONE) else of(bigIntAdd(res, BI_NEG_ONE), BI_ONE)
    }

    actual fun ceil(): Rational {
        if (isNaN) return NaN
        if (isInfinite) return this

        val res = bigIntDiv(n, d)
        val exact = bigIntEq(bigIntRem(n, d), BI_ZERO)
        return if (!bigIntGt(n, BI_ZERO) || exact) of(res, BI_ONE) else of(bigIntAdd(res, BI_ONE), BI_ONE)
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

        return if (bigIntLt(n, BI_ZERO)) -roundedAbs else roundedAbs
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
        if (isInfinite) return if (bigIntGt(n, BI_ZERO)) "Infinity" else "-Infinity"
        if (bigIntEq(d, BI_ONE)) return n.toString()
        return "$n/$d"
    }
}
