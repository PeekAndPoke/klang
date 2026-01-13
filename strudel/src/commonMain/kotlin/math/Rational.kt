package io.peekandpoke.klang.strudel.math

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
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

        /** NaN as a rational number */
        val NaN = Rational(0L, 0L)

        /**
         * Computes the greatest common divisor.
         * Handles Long.MIN_VALUE by treating it as Long.MAX_VALUE for simplicity,
         * as strict absolute value logic fails on MIN_VALUE.
         */
        private fun gcd(a: Long, b: Long): Long {
            var x = if (a == Long.MIN_VALUE) Long.MAX_VALUE else abs(a)
            var y = if (b == Long.MIN_VALUE) Long.MAX_VALUE else abs(b)

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

            // Check for MIN_VALUE to avoid abs() issues
            if (den == Long.MIN_VALUE || num == Long.MIN_VALUE) {
                return NaN
            }

            // Normalize sign: denominator is always positive
            val sign = if ((num < 0) xor (den < 0)) -1L else 1L
            val absNum = abs(num)
            val absDen = abs(den)

            val g = gcd(absNum, absDen)

            return Rational(sign * (absNum / g), absDen / g)
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

            // Handle exact integers quickly
            val longVal = value.toLong()
            if (value == longVal.toDouble()) {
                return Rational(longVal, 1L)
            }

            val sign = if (value < 0) -1L else 1L
            val absValue = abs(value)

            // Maximum denominator we can support (safe margin below Long.MAX_VALUE)
            val maxDenominator = Long.MAX_VALUE / 2

            var p0 = 0L
            var q0 = 1L
            var p1 = 1L
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
                if (rem < 1e-9) break

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
        val g1 = gcd(abs(numerator), other.denominator)
        val g2Abs = gcd(abs(other.numerator), denominator)

        val num1 = numerator / g1
        val den1 = denominator / g2Abs
        val num2 = other.numerator / g2Abs
        val den2 = other.denominator / g1

        return create(num1 * num2, den1 * den2)
    }

    /** Divides this rational by another: (a/b) / (c/d) = (a/b) * (d/c) */
    operator fun div(other: Rational): Rational {
        if (isNaN || other.isNaN || other.numerator == 0L) return NaN

        val g1 = gcd(abs(numerator), abs(other.numerator))
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
    operator fun unaryMinus(): Rational = if (isNaN) NaN else Rational(-numerator, denominator)

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

        // Use subtraction to compare safely
        val diff = this - other
        if (diff.isNaN) return 0
        return diff.numerator.compareTo(0L)
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
}

object RationalSerializer : KSerializer<Rational> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Rational", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Rational) {
        if (value.isNaN) {
            encoder.encodeString("NaN")
        } else {
            encoder.encodeString("${value.numerator}/${value.denominator}")
        }
    }

    override fun deserialize(decoder: Decoder): Rational {
        val string = decoder.decodeString()
        if (string == "NaN") return Rational.NaN

        val parts = string.split('/')
        if (parts.size != 2) return Rational.NaN

        return try {
            val num = parts[0].toLong()
            val den = parts[1].toLong()
            // Construct safely via division which triggers simplification
            Rational(num) / Rational(den)
        } catch (_: Exception) {
            Rational.NaN
        }
    }
}
