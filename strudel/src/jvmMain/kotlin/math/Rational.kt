package io.peekandpoke.klang.strudel.math

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sign

/**
 * JVM actual: Long-based numerator/denominator.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@Serializable(with = RationalStringSerializer::class)
actual class Rational private constructor(
    actual val numerator: Long,
    actual val denominator: Long,
) : Comparable<Rational> {

    actual companion object {
        actual val ZERO = Rational(0L, 1L)
        actual val QUARTER = Rational(1L, 4L)
        actual val HALF = Rational(1L, 2L)
        actual val ONE = Rational(1L, 1L)
        actual val TWO = Rational(2L, 1L)
        actual val MINUS_ONE = Rational(-1L, 1L)
        actual val POSITIVE_INFINITY = Rational(1L, 0L)
        actual val NEGATIVE_INFINITY = Rational(-1L, 0L)
        actual val NaN = Rational(0L, 0L)

        actual operator fun invoke(value: Long): Rational = Rational(value, 1L)

        actual operator fun invoke(value: Int): Rational = Rational(value.toLong(), 1L)

        actual operator fun invoke(value: Double): Rational {
            if (value.isNaN()) return NaN
            if (value.isInfinite()) return if (value > 0) POSITIVE_INFINITY else NEGATIVE_INFINITY
            if (value == 0.0) return ZERO

            val (n, d) = doubleToFraction(abs(value))
            val sign = sign(value).toLong()
            return Rational(sign * n, d)
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
            if (denominator == 0L) {
                return if (numerator == 0L) NaN else Rational(sign(numerator.toDouble()).toLong(), 0L)
            }
            if (numerator == 0L) return ZERO

            val common = gcd(numerator, denominator)
            val n = numerator / common
            val d = denominator / common

            return if (d < 0) Rational(-n, -d) else Rational(n, d)
        }

        actual fun Number.toRational(): Rational = invoke(this.toDouble())

        actual fun List<Rational>.sum(): Rational = fold(ZERO) { acc, r -> acc + r }

        private fun doubleToFraction(
            value: Double,
            epsilon: Double = 1.0E-10,
            maxDenominator: Long = 1_000_000_000L,
        ): Pair<Long, Long> {
            var n0 = 0L
            var d0 = 1L
            var n1 = 1L
            var d1 = 0L
            var v = value
            var a: Long
            var count = 0

            while (count < 100) {
                a = floor(v).toLong()

                val n2 = n0 + a * n1
                val d2 = d0 + a * d1

                if (d2 > maxDenominator) break

                n0 = n1; n1 = n2
                d0 = d1; d1 = d2

                val currentVal = n1.toDouble() / d1.toDouble()
                if (abs(value - currentVal) < epsilon) break

                if (abs(v - a) < 1e-15) break

                v = 1.0 / (v - a)
                count++
            }
            return n1 to d1
        }

        private fun gcd(a: Long, b: Long): Long {
            var x = abs(a)
            var y = abs(b)
            while (y != 0L) {
                val t = y
                y = x % y
                x = t
            }
            return x
        }
    }

    actual val isNaN: Boolean get() = denominator == 0L && numerator == 0L
    actual val isInfinite: Boolean get() = denominator == 0L && numerator != 0L

    actual operator fun plus(other: Rational): Rational {
        if (isNaN || other.isNaN) return NaN

        if (isInfinite || other.isInfinite) {
            return if (
                isInfinite && other.isInfinite && sign(numerator.toDouble()) != sign(other.numerator.toDouble())
            ) {
                NaN
            } else {
                if (isInfinite) this else other
            }
        }

        val g = gcd(denominator, other.denominator)
        val num = numerator * (other.denominator / g) + other.numerator * (denominator / g)
        val den = denominator * (other.denominator / g)

        return create(num, den)
    }

    actual operator fun minus(other: Rational): Rational {
        if (isNaN || other.isNaN) return NaN
        return this + (-other)
    }

    actual operator fun times(other: Rational): Rational {
        if (isNaN || other.isNaN) return NaN
        if (isInfinite || other.isInfinite) {
            if (this == ZERO || other == ZERO) return NaN
            return if (sign(toDouble()) == sign(other.toDouble())) Rational(1, 0) else Rational(-1, 0)
        }

        return create(numerator * other.numerator, denominator * other.denominator)
    }

    actual operator fun div(other: Rational): Rational {
        if (isNaN || other.isNaN) return NaN
        if (other.numerator == 0L) {
            return if (numerator == 0L) {
                NaN
            } else {
                if (numerator > 0) POSITIVE_INFINITY else NEGATIVE_INFINITY
            }
        }

        return create(numerator * other.denominator, denominator * other.numerator)
    }

    actual operator fun rem(other: Rational): Rational {
        if (isNaN || other.isNaN || other.numerator == 0L) return NaN
        val div = this / other
        val trunc = div.toLong().toRational()
        return this - (other * trunc)
    }

    actual operator fun unaryMinus(): Rational {
        if (isNaN) return NaN
        return Rational(-numerator, denominator)
    }

    actual operator fun plus(other: Number): Rational = this + other.toRational()
    actual operator fun minus(other: Number): Rational = this - other.toRational()
    actual operator fun times(other: Number): Rational = this * other.toRational()
    actual operator fun div(other: Number): Rational = this / other.toRational()
    actual operator fun rem(other: Number): Rational = this % other.toRational()

    actual override operator fun compareTo(other: Rational): Int {
        if (isNaN && other.isNaN) return 0
        if (isNaN) return 1
        if (other.isNaN) return -1

        val thisSign = sign(numerator.toDouble()).toInt()
        val otherSign = sign(other.numerator.toDouble()).toInt()

        if (thisSign != otherSign) return thisSign - otherSign

        val diff = (numerator.toDouble() * other.denominator) - (other.numerator.toDouble() * denominator)
        return sign(diff).toInt()
    }

    actual fun toDouble(): Double {
        if (isNaN) return Double.NaN
        if (isInfinite) return if (numerator > 0) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
        return numerator.toDouble() / denominator.toDouble()
    }

    actual fun toLong(): Long = if (denominator == 0L) 0L else numerator / denominator

    actual fun toInt(): Int = toLong().toInt()

    actual fun toFractionString(): String {
        if (isNaN) return "NaN"
        if (isInfinite) return if (numerator > 0) "Infinity" else "-Infinity"
        return "$numerator/$denominator"
    }

    actual fun abs(): Rational {
        if (isNaN) return NaN
        return if (numerator < 0) Rational(-numerator, denominator) else this
    }

    actual fun floor(): Rational {
        if (isNaN) return NaN
        if (isInfinite) return this

        val res = numerator / denominator
        val exact = numerator % denominator == 0L
        return if (numerator >= 0 || exact) Rational(res, 1) else Rational(res - 1, 1)
    }

    actual fun ceil(): Rational {
        if (isNaN) return NaN
        if (isInfinite) return this

        val res = numerator / denominator
        val exact = numerator % denominator == 0L
        return if (numerator <= 0 || exact) Rational(res, 1) else Rational(res + 1, 1)
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

        return if (numerator < 0) -roundedAbs else roundedAbs
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Rational) return false
        return numerator == other.numerator && denominator == other.denominator
    }

    override fun hashCode(): Int {
        var result = numerator.hashCode()
        result = 31 * result + denominator.hashCode()
        return result
    }

    override fun toString(): String {
        if (isNaN) return "NaN"
        if (isInfinite) return if (numerator > 0) "Infinity" else "-Infinity"
        if (denominator == 1L) return numerator.toString()
        return "$numerator/$denominator"
    }
}
