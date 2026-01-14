package io.peekandpoke.klang.audio_bridge

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.pow

/**
 * A value that can be either a number or a string.
 * Serializes directly to a JSON number or string.
 */
@Serializable(with = VoiceValueSerializer::class)
sealed interface VoiceValue {
    val asBoolean: Boolean
    val asString: String
    val asDouble: Double?
    val asInt: Int?

    fun isTruthy(): Boolean {
        // If it can be interpreted as a number, use numerical truthiness (non-zero)
        val d = asDouble
        if (d != null) {
            return d != 0.0
        }
        // Otherwise use string truthiness (not blank, not "false")
        // Note: "0" or "0.0" is handled by the numeric check above as they parse to 0.0
        return asString.isNotBlank() && asString != "false"
    }

    operator fun plus(other: VoiceValue?): VoiceValue? {
        val d1 = asDouble
        val d2 = other?.asDouble

        // Numeric addition if both are numbers
        if (d1 != null && d2 != null) {
            return Num(d1 + d2)
        }

        // String concatenation otherwise
        val s2 = other?.asString ?: return null
        return Text(asString + s2)
    }

    operator fun minus(other: VoiceValue?): VoiceValue? {
        val d1 = asDouble ?: return null
        val d2 = other?.asDouble ?: return null
        return Num(d1 - d2)
    }

    operator fun times(other: VoiceValue?): VoiceValue? {
        val d1 = asDouble ?: return null
        val d2 = other?.asDouble ?: return null
        return Num(d1 * d2)
    }

    operator fun div(other: VoiceValue?): VoiceValue? {
        val d1 = asDouble ?: return null
        val d2 = other?.asDouble ?: return null
        if (d2 == 0.0) return null // or Num(Double.POSITIVE_INFINITY)?
        return Num(d1 / d2)
    }

    operator fun rem(other: VoiceValue?): VoiceValue? {
        val d1 = asDouble ?: return null
        val d2 = other?.asDouble ?: return null
        if (d2 == 0.0) return null
        return Num(d1 % d2)
    }

    infix fun pow(other: VoiceValue?): VoiceValue? {
        val d1 = asDouble ?: return null
        val d2 = other?.asDouble ?: return null
        return Num(d1.pow(d2))
    }

    infix fun band(other: VoiceValue?): VoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num((i1 and i2).toDouble())
    }

    infix fun bor(other: VoiceValue?): VoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num((i1 or i2).toDouble())
    }

    infix fun bxor(other: VoiceValue?): VoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num((i1 xor i2).toDouble())
    }

    infix fun shl(other: VoiceValue?): VoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num((i1 shl i2).toDouble())
    }

    infix fun shr(other: VoiceValue?): VoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num((i1 shr i2).toDouble())
    }

    fun log2(): VoiceValue? {
        val d = asDouble ?: return null
        return Num(kotlin.math.log2(d))
    }

    infix fun lt(other: VoiceValue?): VoiceValue? {
        val d1 = asDouble ?: return null
        val d2 = other?.asDouble ?: return null
        return Num(if (d1 < d2) 1.0 else 0.0)
    }

    infix fun gt(other: VoiceValue?): VoiceValue? {
        val d1 = asDouble ?: return null
        val d2 = other?.asDouble ?: return null
        return Num(if (d1 > d2) 1.0 else 0.0)
    }

    infix fun lte(other: VoiceValue?): VoiceValue? {
        val d1 = asDouble ?: return null
        val d2 = other?.asDouble ?: return null
        return Num(if (d1 <= d2) 1.0 else 0.0)
    }

    infix fun gte(other: VoiceValue?): VoiceValue? {
        val d1 = asDouble ?: return null
        val d2 = other?.asDouble ?: return null
        return Num(if (d1 >= d2) 1.0 else 0.0)
    }

    infix fun eq(other: VoiceValue?): VoiceValue? {
        // We try double comparison first
        val d1 = asDouble
        val d2 = other?.asDouble
        if (d1 != null && d2 != null) {
            return Num(if (d1 == d2) 1.0 else 0.0)
        }
        // Fallback to string comparison
        return Num(if (asString == other?.asString) 1.0 else 0.0)
    }

    infix fun eqt(other: VoiceValue?): VoiceValue? {
        val t1 = isTruthy()
        val t2 = other?.isTruthy() ?: false
        return Num(if (t1 == t2) 1.0 else 0.0)
    }

    infix fun net(other: VoiceValue?): VoiceValue? {
        val t1 = isTruthy()
        val t2 = other?.isTruthy() ?: false
        return Num(if (t1 != t2) 1.0 else 0.0)
    }

    infix fun ne(other: VoiceValue?): VoiceValue? {
        // reuse eq logic
        val isEqual = eq(other)?.asDouble == 1.0
        return Num(if (!isEqual) 1.0 else 0.0)
    }

    // Logical AND: if 'this' is truthy, return 'other', else return 'this'
    infix fun and(other: VoiceValue?): VoiceValue? {
        return if (isTruthy()) other else this
    }

    // Logical OR: if 'this' is truthy, return 'this', else return 'other'
    infix fun or(other: VoiceValue?): VoiceValue? {
        return if (isTruthy()) this else other
    }

    data class Num(val value: Double) : VoiceValue {
        override val asBoolean get() = isTruthy()
        override val asString get() = value.toString()
        override val asDouble get() = value
        override val asInt get() = value.toInt()
        override fun toString() = asString
    }

    data class Text(val value: String) : VoiceValue {
        override val asBoolean get() = isTruthy()
        override val asString get() = value
        override val asDouble get() = value.toDoubleOrNull()
        override val asInt get() = value.toDoubleOrNull()?.toInt()
        override fun toString() = asString
    }

    companion object {
        fun Double.asVoiceValue() = Num(this)

        fun String.asVoiceValue() = Text(this)

        fun Any.asVoiceValue(): VoiceValue? = from(this)

        fun from(value: Any?): VoiceValue? = when (value) {
            is VoiceValue -> value
            is Number -> Num(value.toDouble())
            is String -> Text(value)
            is Boolean -> Text(value.toString())
            else -> null
        }
    }
}

object VoiceValueSerializer : KSerializer<VoiceValue> {
    // We use a Contextual descriptor or a specialized one.
    // Since it can be Double or String, simpler to treat as a generic primitive structure
    // but declaring it as CONTEXTUAL allows flexible handling.
    // However, to make it work with JsonPrimitive, we often use specific kinds.
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("VoiceValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: VoiceValue) {
        when (value) {
            is VoiceValue.Num -> encoder.encodeDouble(value.value)
            is VoiceValue.Text -> encoder.encodeString(value.value)
        }
    }

    override fun deserialize(decoder: Decoder): VoiceValue {
        // Optimization for JSON: inspect the element type
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            return if (element is JsonPrimitive) {
                if (element.isString) {
                    VoiceValue.Text(element.content)
                } else {
                    // It's a number or boolean
                    val d = element.doubleOrNull
                    if (d != null) VoiceValue.Num(d) else VoiceValue.Text(element.content)
                }
            } else {
                VoiceValue.Text(element.toString())
            }
        }

        // Fallback for generic decoders (like Properties or CBOR):
        // We attempt to decode as string, then parse.
        // This assumes the underlying format can provide the value as a string.
        try {
            val str = decoder.decodeString()
            val d = str.toDoubleOrNull()
            return if (d != null) VoiceValue.Num(d) else VoiceValue.Text(str)
        } catch (e: Exception) {
            // If string decoding fails, maybe it was encoded as a number?
            try {
                val d = decoder.decodeDouble()
                return VoiceValue.Num(d)
            } catch (_: Exception) {
                throw e // Giving up
            }
        }
    }
}
