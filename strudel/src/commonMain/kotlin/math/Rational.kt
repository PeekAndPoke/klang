package io.peekandpoke.klang.strudel.math

import io.peekandpoke.klang.strudel.math.Rational.Companion.invoke
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline
import kotlin.math.roundToLong

/**
 * Rational number representation using 32.32 Fixed Point arithmetic.
 *
 * This implementation is a `value class`, meaning it is inlined to a primitive [Long] at runtime,
 * resulting in zero heap allocations for arithmetic operations.
 *
 * It uses 32 bits for the fractional part, providing a resolution of ~2.3e-10.
 */
@Serializable(with = RationalSerializer::class)
@JvmInline
value class Rational private constructor(private val bits: Long) : Comparable<Rational> {

    companion object {
        private const val PRECISION_BITS = 32
        private const val MULTIPLIER_D = 4294967296.0 // 2^32 as Double
        private const val MULTIPLIER_L = 1L shl PRECISION_BITS
        private const val FRACTION_MASK = MULTIPLIER_L - 1

        /** Zero as a rational number */
        val ZERO = Rational(0L)

        /** One as a rational number */
        val ONE = Rational(MULTIPLIER_L)

        /** NaN as a rational number, represented by the minimum Long value */
        val NaN = Rational(Long.MIN_VALUE)

        /** Creates a Rational from a [Long] */
        operator fun invoke(value: Long): Rational = Rational(value shl PRECISION_BITS)

        /** Creates a Rational from an [Int] */
        operator fun invoke(value: Int): Rational = invoke(value.toLong())

        /** Creates a Rational from a [Double] by rounding to the nearest fixed-point step */
        operator fun invoke(value: Double): Rational {
            if (value.isNaN() || value.isInfinite()) return NaN
            return Rational((value * MULTIPLIER_D).roundToLong())
        }

        /** Alias for [invoke] to convert a Double to Rational */
        fun fromDouble(value: Double): Rational = invoke(value)

        /** Extension to convert any [Number] to a Rational */
        fun Number.toRational(): Rational = invoke(this.toDouble())
    }

    /** Returns true if this value represents NaN */
    val isNaN: Boolean get() = bits == NaN.bits

    // --- Arithmetic Operators ---

    /** Adds two rational numbers */
    operator fun plus(other: Rational): Rational =
        if (isNaN || other.isNaN) NaN else Rational(bits + other.bits)

    /** Subtracts another rational number from this one */
    operator fun minus(other: Rational): Rational =
        if (isNaN || other.isNaN) NaN else Rational(bits - other.bits)

    /**
     * Multiplies two rational numbers.
     * Uses Double for the intermediate product to prevent 64-bit overflow.
     */
    operator fun times(other: Rational): Rational {
        if (isNaN || other.isNaN) return NaN
        val res = (bits.toDouble() * other.bits.toDouble()) / MULTIPLIER_D
        return Rational(res.toLong())
    }

    /**
     * Divides this rational by another.
     * Uses Double for the intermediate to prevent 64-bit overflow.
     */
    operator fun div(other: Rational): Rational {
        if (isNaN || other.isNaN || other.bits == 0L) return NaN
        val res = (bits.toDouble() / other.bits.toDouble()) * MULTIPLIER_D
        return Rational(res.toLong())
    }

    /** Computes the remainder of division between two rational numbers */
    operator fun rem(other: Rational): Rational {
        if (isNaN || other.isNaN || other.bits == 0L) return NaN
        return Rational(bits % other.bits)
    }

    /** Negates the rational number */
    operator fun unaryMinus(): Rational = if (isNaN) NaN else Rational(-bits)

    // --- Number Interoperability ---

    operator fun plus(other: Number): Rational = this + other.toRational()
    operator fun minus(other: Number): Rational = this - other.toRational()
    operator fun times(other: Number): Rational = this * other.toRational()
    operator fun div(other: Number): Rational = this / other.toRational()
    operator fun rem(other: Number): Rational = this % other.toRational()

    // --- Comparison ---

    override operator fun compareTo(other: Rational): Int = bits.compareTo(other.bits)

    // --- Conversions ---

    /** Converts the fixed-point value back to a [Double] */
    fun toDouble(): Double = if (isNaN) Double.NaN else bits.toDouble() / MULTIPLIER_D

    /** Converts to [Long], truncating the fractional part */
    fun toLong(): Long = if (isNaN) 0L else bits shr PRECISION_BITS

    /** Converts to [Int], truncating the fractional part */
    fun toInt(): Int = toLong().toInt()

    // --- Utilities ---

    /** Returns the absolute value of this rational */
    fun abs(): Rational {
        if (isNaN) return NaN
        return if (bits < 0) Rational(-bits) else this
    }

    /** Returns the largest integer (as Rational) less than or equal to this value */
    fun floor(): Rational {
        if (isNaN) return NaN
        return Rational((bits shr PRECISION_BITS) shl PRECISION_BITS)
    }

    /** Returns the smallest integer (as Rational) greater than or equal to this value */
    fun ceil(): Rational {
        if (isNaN) return NaN
        val f = floor()
        return if (f.bits == bits) f else Rational(f.bits + MULTIPLIER_L)
    }

    /** Returns the fractional part of the number (the remainder after floor) */
    fun frac(): Rational {
        if (isNaN) return NaN
        return Rational(bits and FRACTION_MASK)
    }

    override fun toString(): String = if (isNaN) "NaN" else toDouble().toString()
}

object RationalSerializer : KSerializer<Rational> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Rational", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: Rational) {
        encoder.encodeDouble(value.toDouble())
    }

    override fun deserialize(decoder: Decoder): Rational {
        return Rational(decoder.decodeDouble())
    }
}
