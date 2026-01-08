package io.peekandpoke.klang.strudel.math

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.absoluteValue

/**
 * Rational number representation for exact time arithmetic.
 * Eliminates floating-point drift in pattern timing calculations.
 *
 * @param num Numerator
 * @param den Denominator (zero represents NaN)
 */
@Serializable(with = RationalSerializer::class)
data class Rational(val num: Long, val den: Long) : Comparable<Rational> {

    /** Returns true if this rational represents NaN (denominator is zero) */
    val isNaN: Boolean get() = den == 0L

    companion object {
        /** Zero as a rational number */
        val ZERO = Rational(0, 1)

        /** One as a rational number */
        val ONE = Rational(1, 1)

        /** NaN as a rational number (denominator is zero) */
        val NaN = Rational(0, 0)

        /** Create a rational from an integer */
        operator fun invoke(value: Int): Rational = Rational(value.toLong(), 1)

        /** Create a rational from a long */
        operator fun invoke(value: Long): Rational = Rational(value, 1)

        /**
         * Approximate a rational from a double.
         * Uses continued fractions algorithm with a maximum denominator to avoid overflow.
         */
        operator fun invoke(value: Double, maxDenominator: Long = 1_000_000): Rational {
            if (value.isNaN()) return NaN
            if (value.isInfinite()) return NaN

            if (value == 0.0) return ZERO

            val sign = if (value < 0.0) -1L else 1L
            val absValue = value.absoluteValue

            // Simple cases
            val intPart = absValue.toLong()
            if (absValue == intPart.toDouble()) {
                return Rational(sign * intPart, 1)
            }

            // Continued fractions algorithm
            var h0 = 0L
            var h1 = 1L
            var k0 = 1L
            var k1 = 0L

            var x = absValue
            var maxIterations = 20

            while (maxIterations > 0) {
                maxIterations--

                val a = x.toLong()
                val h = a * h1 + h0
                val k = a * k1 + k0

                if (k > maxDenominator) break

                h0 = h1
                h1 = h
                k0 = k1
                k1 = k

                val remainder = x - a
                if (remainder < 1e-10) break

                x = 1.0 / remainder
            }

            return Rational(sign * h1, k1).simplified()
        }

        /**
         * Explicit alias for creating Rational from Double for clarity.
         * Same as invoke(Double).
         */
        fun fromDouble(value: Double, maxDenominator: Long = 1_000_000): Rational =
            invoke(value, maxDenominator)

        /**
         * Explicit alias for creating Rational from Double for clarity.
         *
         * TODO: write tests
         */
        fun Number.toRational(maxDenominator: Long = 1_000_000): Rational =
            Rational(this.toDouble(), maxDenominator)

        /** Greatest common divisor */
        private fun gcd(a: Long, b: Long): Long {
            var x = a.absoluteValue
            var y = b.absoluteValue
            while (y != 0L) {
                val temp = y
                y = x % y
                x = temp
            }
            return x
        }
    }

    // Arithmetic operators

    operator fun plus(other: Rational): Rational {
        if (this.isNaN || other.isNaN) return NaN
        val n = num * other.den + other.num * den
        val d = den * other.den
        return Rational(n, d).simplified()
    }

    operator fun minus(other: Rational): Rational {
        if (this.isNaN || other.isNaN) return NaN
        val n = num * other.den - other.num * den
        val d = den * other.den
        return Rational(n, d).simplified()
    }

    operator fun times(other: Rational): Rational {
        if (this.isNaN || other.isNaN) return NaN
        return Rational(num * other.num, den * other.den).simplified()
    }

    operator fun div(other: Rational): Rational {
        if (this.isNaN || other.isNaN) return NaN
        if (other.num == 0L) return NaN  // Division by zero returns NaN
        return Rational(num * other.den, den * other.num).simplified()
    }

    operator fun rem(other: Rational): Rational {
        if (this.isNaN || other.isNaN) return NaN
        if (other.num == 0L) return NaN
        // a mod b = a - floor(a/b) * b
        val quotient = (this / other).floor()
        return this - (quotient * other)
    }

    /**
     * TODO: write tests for this
     */
    operator fun rem(other: Number): Rational = rem(other.toRational())

    operator fun unaryMinus(): Rational {
        if (this.isNaN) return NaN
        return Rational(-num, den)
    }

    // Double interoperability operators

    operator fun times(other: Double): Rational {
        if (isNaN || other.isNaN()) return NaN
        return this * Rational(other)
    }

    operator fun div(other: Double): Rational {
        if (isNaN || other.isNaN()) return NaN
        if (other == 0.0) return NaN
        return this / Rational(other)
    }

    operator fun plus(other: Double): Rational {
        if (isNaN || other.isNaN()) return NaN
        return this + Rational(other)
    }

    operator fun minus(other: Double): Rational {
        if (isNaN || other.isNaN()) return NaN
        return this - Rational(other)
    }

    // Comparison

    override operator fun compareTo(other: Rational): Int {
        // NaN is not comparable, but we need to return something
        if (this.isNaN || other.isNaN) return 0
        // Compare by cross-multiplication to avoid overflow
        val left = num * other.den
        val right = other.num * den
        return left.compareTo(right)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Rational) return false

        // Compare simplified forms
        val a = this.simplified()
        val b = other.simplified()
        return a.num == b.num && a.den == b.den
    }

    override fun hashCode(): Int {
        val simplified = this.simplified()
        return 31 * simplified.num.hashCode() + simplified.den.hashCode()
    }

    // Conversion methods

    fun toDouble(): Double = if (isNaN) Double.NaN else num.toDouble() / den.toDouble()

    fun toFloat(): Float = if (isNaN) Float.NaN else num.toFloat() / den.toFloat()

    fun toLong(): Long = if (isNaN) 0L else num / den

    fun toInt(): Int = toLong().toInt()

    // Utility methods

    /** Returns the absolute value */
    fun abs(): Rational {
        if (isNaN) return NaN
        return if (num < 0) Rational(-num, den) else this
    }

    /** Returns a simplified (reduced) form of this rational */
    fun simplified(): Rational {
        if (isNaN) return NaN
        if (num == 0L) return ZERO

        val g = gcd(num, den)
        val newNum = num / g
        val newDen = den / g

        // Normalize: keep sign in numerator, denominator always positive
        return if (newDen < 0) {
            Rational(-newNum, -newDen)
        } else {
            Rational(newNum, newDen)
        }
    }

    /** Returns the largest integer less than or equal to this rational */
    fun floor(): Rational {
        if (isNaN) return NaN
        val wholePart = if (num >= 0) {
            num / den
        } else {
            // For negative numbers, floor rounds toward negative infinity
            (num - den + 1) / den
        }
        return Rational(wholePart, 1)
    }

    /** Returns the smallest integer greater than or equal to this rational */
    fun ceil(): Rational {
        if (isNaN) return NaN
        val wholePart = if (num >= 0) {
            // For positive numbers, ceil rounds up
            (num + den - 1) / den
        } else {
            num / den
        }
        return Rational(wholePart, 1)
    }

    /** Returns the fractional part (always between 0 and 1) */
    fun frac(): Rational {
        if (isNaN) return NaN
        return this - floor()
    }

    override fun toString(): String {
        return if (isNaN) {
            "NaN"
        } else if (den == 1L) {
            num.toString()
        } else {
            "$num/$den"
        }
    }
}

/**
 * Custom serializer for Rational numbers.
 * Serializes as "num/den" string format.
 */
object RationalSerializer : KSerializer<Rational> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Rational", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Rational) {
        encoder.encodeString("${value.num}/${value.den}")
    }

    override fun deserialize(decoder: Decoder): Rational {
        val str = decoder.decodeString()
        val parts = str.split("/")
        require(parts.size == 2) { "Invalid Rational format: $str" }
        return Rational(parts[0].toLong(), parts[1].toLong())
    }
}
