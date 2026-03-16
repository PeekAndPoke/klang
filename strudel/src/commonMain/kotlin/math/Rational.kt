package io.peekandpoke.klang.strudel.math

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Rational number representation using numerator and denominator.
 *
 * Platform implementations:
 * - JVM: Long-based numerator/denominator
 * - JS: Native BigInt-based to avoid Kotlin/JS Long boxing overhead
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "unused")
@Serializable(with = RationalStringSerializer::class)
expect class Rational : Comparable<Rational> {

    companion object {
        val ZERO: Rational
        val QUARTER: Rational
        val HALF: Rational
        val ONE: Rational
        val TWO: Rational
        val MINUS_ONE: Rational
        val POSITIVE_INFINITY: Rational
        val NEGATIVE_INFINITY: Rational
        val NaN: Rational

        operator fun invoke(value: Long): Rational
        operator fun invoke(value: Int): Rational
        operator fun invoke(value: Double): Rational
        fun parse(value: String): Rational
        fun create(numerator: Long, denominator: Long): Rational
        fun Number.toRational(): Rational
        fun List<Rational>.sum(): Rational
    }

    val numerator: Long
    val denominator: Long
    val isNaN: Boolean
    val isInfinite: Boolean

    operator fun plus(other: Rational): Rational
    operator fun minus(other: Rational): Rational
    operator fun times(other: Rational): Rational
    operator fun div(other: Rational): Rational
    operator fun rem(other: Rational): Rational
    operator fun unaryMinus(): Rational

    operator fun plus(other: Number): Rational
    operator fun minus(other: Number): Rational
    operator fun times(other: Number): Rational
    operator fun div(other: Number): Rational
    operator fun rem(other: Number): Rational

    override operator fun compareTo(other: Rational): Int

    fun toDouble(): Double
    fun toLong(): Long
    fun toInt(): Int
    fun toFractionString(): String

    fun abs(): Rational
    fun floor(): Rational
    fun ceil(): Rational
    fun frac(): Rational
    fun round(): Rational
    fun exp(): Rational
    fun pow(exponent: Rational): Rational
    fun pow(exponent: Number): Rational
    fun log(base: Rational): Rational
    fun log(base: Number): Rational
    fun ln(): Rational
    fun log10(): Rational
    fun log2(): Rational
}

/**
 * Serializer that converts Rational to/from string format like "2/3" or "-5/8"
 */
object RationalStringSerializer : KSerializer<Rational> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("RationalString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Rational) {
        encoder.encodeString(value.toFractionString())
    }

    override fun deserialize(decoder: Decoder): Rational {
        return Rational.parse(decoder.decodeString())
    }
}
