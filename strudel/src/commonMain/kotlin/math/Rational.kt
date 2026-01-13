package io.peekandpoke.klang.strudel.math

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Rational number representation using numerator/denominator arithmetic.
 *
 * This implementation stores fractions as a pair of [Long] values (numerator and denominator),
 * automatically simplified using GCD after each operation for exact arithmetic.
 *
 * Inspired by Fraction.js, this provides precise rational arithmetic without floating-point
 * precision loss.
 */
@ConsistentCopyVisibility
@Serializable(with = RationalSerializer::class)
data class Rational private constructor(
    val numerator: Long,
    val denominator: Long,
) : Comparable<Rational> {

    companion object {
        /** Zero as a rational number */
        val ZERO = Rational(0L, 1L)

        /** One as a rational number */
        val ONE = Rational(1L, 1L)

        /** Minus One as a rational number */
        val MINUS_ONE = Rational(-1L, 1L)

        /** NaN as a rational number */
        val NaN = Rational(0L, 0L)

        /**
         * Computes the greatest common divisor using the Euclidean algorithm.
         * Works directly with negative numbers (returns potentially negative GCD),
         * avoiding the need for abs(Long.MIN_VALUE).
         */
        private fun gcd(a: Long, b: Long): Long {
            var x = a
            var y = b
            while (y != 0L) {
                val temp = y
                y = x % y
                x = temp
            }
            return x
        }

        /**
         * Creates a simplified rational number from numerator and denominator.
         * Automatically reduces the fraction and normalizes the sign.
         */
        private fun create(num: Long, den: Long): Rational {
            if (den == 0L) return NaN
            if (num == 0L) return ZERO

            // Optimization for identity
            if (den == 1L) return Rational(num, 1L)

            // Simplify using GCD.
            // Note: We don't use abs() here because abs(Long.MIN_VALUE) is negative.
            // The Euclidean algorithm handles signs correctly for reduction purposes.
            val g = gcd(num, den)
            var sNum = num / g
            var sDen = den / g

            // Normalize sign: denominator must be positive
            if (sDen < 0) {
                // If sDen is MIN_VALUE, we cannot negate it (overflow).
                // This implies the rational number cannot be represented with a positive denominator in Long.
                // e.g. 1 / Long.MIN_VALUE is not representable as X/Y with Y > 0 and X, Y in Long.
                if (sDen == Long.MIN_VALUE) return NaN

                sDen = -sDen

                // If sNum is MIN_VALUE, we cannot negate it (overflow).
                if (sNum == Long.MIN_VALUE) return NaN

                sNum = -sNum
            }

            return Rational(sNum, sDen)
        }

        /** Creates a Rational from a [Long] */
        operator fun invoke(value: Long): Rational = Rational(value, 1L)

        /** Creates a Rational from an [Int] */
        operator fun invoke(value: Int): Rational = invoke(value.toLong())

        /**
         * Creates a Rational from a [Double] using the Continued Fraction algorithm.
         * This finds the best rational approximation that fits within [Long.MAX_VALUE].
         */
        operator fun invoke(value: Double): Rational {
            if (value.isNaN() || value.isInfinite()) return NaN
            if (value == 0.0) return ZERO

            // Bounds check: if the value exceeds Long range, we can't represent it
            if (value > Long.MAX_VALUE || value < Long.MIN_VALUE) return NaN

            // Handle exact integers quickly
            val longVal = value.toLong()
            if (value == longVal.toDouble()) {
                return Rational(longVal, 1L)
            }

            val sign = if (value < 0) -1L else 1L
            val absValue = abs(value)

            // Maximum denominator we can support (safe margin below Long.MAX_VALUE)
            val maxDenominator = Long.MAX_VALUE / 2

            var p0 = 0L;
            var q0 = 1L
            var p1 = 1L;
            var q1 = 0L
            var x = absValue

            // Continued fraction expansion
            while (true) {
                val a = x.toLong()
                val rem = x - a

                // Safety checks for overflow before computing next convergents
                if (a > 0 && q1 > maxDenominator / a) break

                val q2 = a * q1 + q0
                if (q2 > maxDenominator || q2 < 0) break

                if (a > 0 && p1 > (Long.MAX_VALUE / 2) / a) break
                val p2 = a * p1 + p0
                if (p2 < 0) break

                // Update convergents
                p0 = p1; q0 = q1
                p1 = p2; q1 = q2

                // Break if we are close enough (epsilon)
                if (rem < 1e-10) break

                x = 1.0 / rem
            }

            return create(sign * p1, q1)
        }

        /** Extension to convert any [Number] to a Rational */
        fun Number.toRational(): Rational = when (this) {
            is Long -> Rational(this)
            is Int -> Rational(this.toLong())
            is Float -> Rational(this.toDouble())
            is Double -> Rational(this)
            else -> Rational(this.toDouble())
        }
    }

    /** Returns true if this value represents NaN */
    val isNaN: Boolean get() = denominator == 0L

    // --- Arithmetic Operators ---

    /** Adds two rational numbers: a/b + c/d */
    operator fun plus(other: Rational): Rational {
        if (isNaN || other.isNaN) return NaN
        if (this == ZERO) return other
        if (other == ZERO) return this

        // Optimization: Use LCD to keep numbers smaller
        val g = gcd(denominator, other.denominator)
        val den = denominator / g

        val otherDenDivG = other.denominator / g

        val num1 = numerator * otherDenDivG
        val num2 = other.numerator * den
        val newNum = num1 + num2
        val newDen = denominator * otherDenDivG

        return create(newNum, newDen)
    }

    /** Subtracts another rational number from this one */
    operator fun minus(other: Rational): Rational = plus(-other)

    /** Multiplies two rational numbers: (a/b) * (c/d) */
    operator fun times(other: Rational): Rational {
        if (isNaN || other.isNaN) return NaN

        // Simplify before multiply to avoid overflow
        // We can cancel gcd(a, d) and gcd(c, b)
        // Use abs() for GCD checks to allow cancellation of signs properly, but fallback for MIN_VALUE
        val g1 = gcd(if (numerator == Long.MIN_VALUE) numerator else abs(numerator), other.denominator)
        val g2 = gcd(if (other.numerator == Long.MIN_VALUE) other.numerator else abs(other.numerator), denominator)

        val num1 = numerator / g1
        val den1 = denominator / g2
        val num2 = other.numerator / g2
        val den2 = other.denominator / g1

        return create(num1 * num2, den1 * den2)
    }

    /** Divides this rational by another: (a/b) / (c/d) = (a/b) * (d/c) */
    operator fun div(other: Rational): Rational {
        if (isNaN || other.isNaN || other.numerator == 0L) return NaN

        val g1 = gcd(
            if (numerator == Long.MIN_VALUE) numerator else abs(numerator),
            if (other.numerator == Long.MIN_VALUE) other.numerator else abs(other.numerator)
        )
        val g2 = gcd(denominator, other.denominator)

        val num1 = numerator / g1
        val den1 = denominator / g2
        val num2 = other.denominator / g2
        val den2 = other.numerator / g1

        return create(num1 * num2, den1 * den2)
    }

    /** Computes the remainder of division between two rational numbers */
    operator fun rem(other: Rational): Rational {
        if (isNaN || other.isNaN || other.numerator == 0L) return NaN
        // a/b % c/d = a/b - floor(a/b / c/d) * c/d
        val quotient = (this / other).floor()
        return this - (quotient * other)
    }

    /** Negates the rational number */
    operator fun unaryMinus(): Rational = if (isNaN) NaN else create(-numerator, denominator)

    // --- Number Interoperability ---

    operator fun plus(other: Number): Rational = this + other.toRational()
    operator fun minus(other: Number): Rational = this - other.toRational()
    operator fun times(other: Number): Rational = this * other.toRational()
    operator fun div(other: Number): Rational = this / other.toRational()
    operator fun rem(other: Number): Rational = this % other.toRational()

    // --- Comparison ---

    override operator fun compareTo(other: Rational): Int {
        if (isNaN && other.isNaN) return 0
        if (isNaN) return 1
        if (other.isNaN) return -1

        // Optimization for equal values
        if (numerator == other.numerator && denominator == other.denominator) return 0

        // Compare signs
        val thisSign = numerator.compareTo(0L)
        val otherSign = other.numerator.compareTo(0L)

        if (thisSign != otherSign) {
            return thisSign.compareTo(otherSign)
        }

        // If both are zero (should be handled by equality check, but safety)
        if (thisSign == 0) return 0

        // Compare absolute values safely to avoid overflow
        // If both are positive: compare(abs(this), abs(other))
        // If both are negative: compare(abs(other), abs(this)) i.e. reverse logic
        val n1 = kotlin.math.abs(numerator)
        val d1 = denominator
        val n2 = kotlin.math.abs(other.numerator)
        val d2 = other.denominator

        val absCompare = compareAbs(n1, d1, n2, d2)

        return if (thisSign > 0) absCompare else -absCompare
    }

    // --- Conversions ---

    /** Converts the rational number to a [Double] */
    fun toDouble(): Double = if (isNaN) Double.NaN else numerator.toDouble() / denominator.toDouble()

    /** Converts to [Long], truncating the fractional part */
    fun toLong(): Long = if (isNaN) 0L else numerator / denominator

    /** Converts to [Int], truncating the fractional part */
    fun toInt(): Int = toLong().toInt()

    // --- Utilities ---

    /** Returns the absolute value of this rational */
    fun abs(): Rational {
        if (isNaN) return NaN
        if (numerator == Long.MIN_VALUE) return NaN
        return if (numerator < 0) Rational(-numerator, denominator) else this
    }

    /** Returns the largest integer (as Rational) less than or equal to this value */
    fun floor(): Rational {
        if (isNaN) return NaN
        val quot = numerator / denominator
        val rem = numerator % denominator

        // Correct floor for negative numbers with remainder
        val result = if (rem != 0L && numerator < 0) {
            quot - 1
        } else {
            quot
        }
        return Rational(result, 1L)
    }

    /** Returns the smallest integer (as Rational) greater than or equal to this value */
    fun ceil(): Rational {
        if (isNaN) return NaN
        val f = floor()
        return if (this == f) f else f + ONE
    }

    /** Returns the fractional part of the number (the remainder after floor) */
    fun frac(): Rational {
        if (isNaN) return NaN
        return this - floor()
    }

    override fun toString(): String = if (isNaN) "NaN" else toDouble().toString()

    /**
     * Compares n1/d1 with n2/d2 avoiding overflow using continued fraction expansion logic.
     * Assumes all inputs are non-negative.
     */
    /**
     * Compares n1/d1 with n2/d2 avoiding overflow using continued fraction expansion logic.
     * Assumes all inputs are non-negative.
     */
    private fun compareAbs(n1: Long, d1: Long, n2: Long, d2: Long): Int {
        var a = n1
        var b = d1
        var c = n2
        var d = d2

        while (true) {
            // Compare integer parts
            val q1 = a / b
            val q2 = c / d

            if (q1 != q2) {
                return q1.compareTo(q2)
            }

            // Integer parts are equal, compare remainders
            val r1 = a % b
            val r2 = c % d

            if (r1 == 0L) {
                // a/b is exactly q1. c/d is q1 + r2/d.
                // If c/d has remainder, it is larger.
                return if (r2 == 0L) 0 else -1
            }
            if (r2 == 0L) {
                // c/d is exactly q1. a/b is q1 + r1/b.
                // a/b is larger.
                return 1
            }

            // Compare fractional parts: r1/b vs r2/d
            // Equivalent to comparing reciprocals: d/r2 vs b/r1 (swapping sides)
            // Next iteration compares: d/r2 vs b/r1
            // a = d2 (old d)
            // b = r2
            // c = d1 (old b)
            // d = r1
            val nextA = d
            a = d
            d = r1
            c = b
            b = r2
        }
    }
}

