package io.peekandpoke.klang.strudel.math

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object Rational2Serializer : KSerializer<Rational2> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Rational2", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Rational2) {
        if (value.isNaN) {
            encoder.encodeString("NaN")
        } else {
            encoder.encodeString("${value.numerator}/${value.denominator}")
        }
    }

    override fun deserialize(decoder: Decoder): Rational2 {
        val string = decoder.decodeString()
        if (string == "NaN") return Rational2.NaN

        val parts = string.split('/')
        if (parts.size != 2) return Rational2.NaN

        return try {
            val num = parts[0].toLong()
            val den = parts[1].toLong()
            // Construct safely via division which triggers simplification
            Rational2(num) / Rational2(den)
        } catch (_: Exception) {
            Rational2.NaN
        }
    }
}
