package io.peekandpoke.klang.strudel.math

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

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
