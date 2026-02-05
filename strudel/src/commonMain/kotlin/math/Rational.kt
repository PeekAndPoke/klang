package io.peekandpoke.klang.strudel.math

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sign

/**
 * Rational number representation using numerator and denominator.
 *
 * This implementation replaces the Fixed Point 32.32 arithmetic with exact rational arithmetic,
 * similar to Fraction.js. It stores values as [numerator] and [denominator].
 */
@ConsistentCopyVisibility
@Serializable(with = RationalStringSerializer::class)
data class Rational private constructor(val numerator: Long, val denominator: Long) : Comparable<Rational> {

    companion object {
        /** Zero as a rational number */
        val ZERO = Rational(0L, 1L)

        /** Half as a rational number */
        val HALF = Rational(1L, 2L)

        /** One as a rational number */
        val ONE = Rational(1L, 1L)

        /** TWO as a rational number */
        val TWO = Rational(2L, 1L)

        /** Negative one as a rational number */
        val MINUS_ONE = Rational(-1L, 1L)

        /** Positive Infinity as a rational number */
        val POSITIVE_INFINITY = Rational(1L, 0L)

        /** Negative Infinity as a rational number */
        val NEGATIVE_INFINITY = Rational(-1L, 0L)

        /** NaN as a rational number */
        val NaN = Rational(0L, 0L)

        /**
         * Creates a Rational from a [Long].
         * Matches existing API where Rational(1L) creates the number 1.
         */
        operator fun invoke(value: Long): Rational = Rational(value, 1L)

        /** Creates a Rational from an [Int] */
        operator fun invoke(value: Int): Rational = Rational(value.toLong(), 1L)

        /**
         * Creates a Rational from a [Double] using the continued fraction algorithm.
         */
        operator fun invoke(value: Double): Rational {
            if (value.isNaN()) return NaN
            if (value.isInfinite()) return if (value > 0) POSITIVE_INFINITY else NEGATIVE_INFINITY
            if (value == 0.0) return ZERO

            val (n, d) = doubleToFraction(abs(value))
            val sign = sign(value).toLong()
            return Rational(sign * n, d)
        }

        /**
         * Parses a string into a Rational.
         * Supports formats:
         * - "numerator/denominator" (e.g. "2/3", "-5/8", "5/1")
         * - "integer" (e.g. "5", "-10") - treats as 5/1
         * - "decimal" (e.g. "0.5", "1.25") - converts using continued fraction approximation
         * - "NaN", "Infinity", "-Infinity"
         */
        fun parse(value: String): Rational {
            val str = value.trim()
            if (str == "NaN") return NaN
            if (str == "Infinity" || str == "+Infinity") return POSITIVE_INFINITY
            if (str == "-Infinity") return NEGATIVE_INFINITY

            // Handle fraction format "numerator/denominator"
            if ("/" in str) {
                val parts = str.split("/")
                if (parts.size == 2) {
                    val num = parts[0].trim().toDoubleOrNull()
                    val den = parts[1].trim().toDoubleOrNull()

                    if (num != null && den != null) {
                        if (den == 0.0) return NaN
                        // Use Rational division to handle potential decimals in fraction parts
                        return Rational(num) / Rational(den)
                    }
                }
                return NaN
            }

            // Fallback: try parsing as plain number
            val d = str.toDoubleOrNull()
            return if (d != null) Rational(d) else NaN
        }

        /** Creates a Rational from a numerator and denominator, normalizing the result. */
        fun create(numerator: Long, denominator: Long): Rational {
            if (denominator == 0L) {
                return if (numerator == 0L) NaN else Rational(sign(numerator.toDouble()).toLong(), 0L)
            }
            if (numerator == 0L) return ZERO

            val common = gcd(numerator, denominator)
            val n = numerator / common
            val d = denominator / common

            return if (d < 0) Rational(-n, -d) else Rational(n, d)
        }

        /** Extension to convert any [Number] to a Rational */
        fun Number.toRational(): Rational = invoke(this.toDouble())

        fun List<Rational>.sum(): Rational = fold(ZERO) { acc, r -> acc + r }

        /**
         * Converts a double to a fraction (numerator, denominator) using the continued fraction algorithm.
         * This approximates the double with a rational number.
         */
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

                // Safe addition check could be added here, but Long is usually sufficient for typical patterns
                val n2 = n0 + a * n1
                val d2 = d0 + a * d1

                if (d2 > maxDenominator) break

                n0 = n1; n1 = n2
                d0 = d1; d1 = d2

                val currentVal = n1.toDouble() / d1.toDouble()
                if (abs(value - currentVal) < epsilon) break

                if (abs(v - a) < 1e-15) break // Exact integer part reached

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

    /** Returns true if this value represents NaN */
    val isNaN: Boolean get() = denominator == 0L && numerator == 0L

    /** Returns true if this value represents Infinity */
    val isInfinite: Boolean get() = denominator == 0L && numerator != 0L

    // --- Arithmetic Operators ---

    /** Adds two rational numbers */
    operator fun plus(other: Rational): Rational {
        if (isNaN || other.isNaN) return NaN
        if (isInfinite || other.isInfinite) return if (isInfinite && other.isInfinite && sign(numerator.toDouble()) != sign(
                other.numerator.toDouble()
            )
        ) NaN else if (isInfinite) this else other

        // (a/b) + (c/d) = (ad + bc) / bd
        val g = gcd(denominator, other.denominator)
        val num = numerator * (other.denominator / g) + other.numerator * (denominator / g)
        val den = denominator * (other.denominator / g)

        return create(num, den)
    }

    /** Subtracts another rational number from this one */
    operator fun minus(other: Rational): Rational {
        if (isNaN || other.isNaN) return NaN
        return this + (-other)
    }

    /** Multiplies two rational numbers */
    operator fun times(other: Rational): Rational {
        if (isNaN || other.isNaN) return NaN
        if (isInfinite || other.isInfinite) {
            if (this == ZERO || other == ZERO) return NaN
            return if (sign(toDouble()) == sign(other.toDouble())) Rational(1, 0) else Rational(-1, 0)
        }

        return create(numerator * other.numerator, denominator * other.denominator)
    }

    /** Divides this rational by another */
    operator fun div(other: Rational): Rational {
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

    /** Computes the remainder of division between two rational numbers */
    operator fun rem(other: Rational): Rational {
        if (isNaN || other.isNaN || other.numerator == 0L) return NaN
        // a % b = a - b * trunc(a / b)
        val div = this / other
        val trunc = div.toLong().toRational()
        return this - (other * trunc)
    }

    /** Negates the rational number */
    operator fun unaryMinus(): Rational {
        if (isNaN) return NaN
        return Rational(-numerator, denominator)
    }

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

        val thisSign = sign(numerator.toDouble()).toInt()
        val otherSign = sign(other.numerator.toDouble()).toInt()

        if (thisSign != otherSign) return thisSign - otherSign

        // Use Double for comparison to prevent overflow, typically sufficient
        val diff = (numerator.toDouble() * other.denominator) - (other.numerator.toDouble() * denominator)
        return sign(diff).toInt()
    }

    // --- Conversions ---

    /** Converts to [Double] */
    fun toDouble(): Double {
        if (isNaN) return Double.NaN
        if (isInfinite) return if (numerator > 0) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
        return numerator.toDouble() / denominator.toDouble()
    }

    /** Converts to [Long], truncating the fractional part */
    fun toLong(): Long = if (denominator == 0L) 0L else numerator / denominator

    /** Converts to [Int], truncating the fractional part */
    fun toInt(): Int = toLong().toInt()

    /**
     * Converts to a fraction string like "2/3".
     * Unlike [toString], this always includes the denominator (e.g. "5/1").
     * This matches the format expected by Strudel's serialization tests.
     */
    fun toFractionString(): String {
        if (isNaN) return "NaN"
        if (isInfinite) return if (numerator > 0) "Infinity" else "-Infinity"
        return "$numerator/$denominator"
    }

    // --- Utilities ---

    /** Returns the absolute value of this rational */
    fun abs(): Rational {
        if (isNaN) return NaN
        return if (numerator < 0) Rational(-numerator, denominator) else this
    }

    /** Returns the largest integer (as Rational) less than or equal to this value */
    fun floor(): Rational {
        if (isNaN) return NaN
        if (isInfinite) return this

        val res = numerator / denominator
        val exact = numerator % denominator == 0L
        return if (numerator >= 0 || exact) Rational(res, 1) else Rational(res - 1, 1)
    }

    /** Returns the smallest integer (as Rational) greater than or equal to this value */
    fun ceil(): Rational {
        if (isNaN) return NaN
        if (isInfinite) return this

        val res = numerator / denominator
        val exact = numerator % denominator == 0L
        return if (numerator <= 0 || exact) Rational(res, 1) else Rational(res + 1, 1)
    }

    /** Returns the fractional part of the number */
    fun frac(): Rational {
        if (isNaN) return NaN
        return this - floor()
    }

    /**
     * Rounds to the nearest integer (as Rational).
     * Uses "round half away from zero" rule.
     */
    fun round(): Rational {
        if (isNaN) return NaN
        if (isInfinite) return this

        val abs = abs()
        val floor = abs.floor()
        val frac = abs - floor

        val roundedAbs = if (frac >= HALF) floor + ONE else floor

        return if (numerator < 0) -roundedAbs else roundedAbs
    }

    /**
     * Computes Euler's number `e` raised to the power of this value.
     */
    fun exp(): Rational {
        if (isNaN) return NaN
        val result = kotlin.math.exp(toDouble())
        if (result.isNaN() || result.isInfinite()) return NaN
        return Rational(result)
    }

    /**
     * Raises this rational to the power of another rational.
     */
    infix fun pow(exponent: Rational): Rational {
        if (isNaN || exponent.isNaN) return NaN
        val res = toDouble().pow(exponent.toDouble())
        return Rational(res)
    }

    override fun toString(): String {
        if (isNaN) return "NaN"
        if (isInfinite) return if (numerator > 0) "Infinity" else "-Infinity"
        if (denominator == 1L) return numerator.toString()
        return "$numerator/$denominator"
    }
}

/**
 * Serializer that converts Rational to/from string format like "2/3" or "-5/8"
 */
object RationalStringSerializer : KSerializer<Rational> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("RationalString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Rational) {
        // Use the explicit fraction string format (e.g. "5/1")
        encoder.encodeString(value.toFractionString())
    }

    override fun deserialize(decoder: Decoder): Rational {
        return Rational.parse(decoder.decodeString())
    }
}
