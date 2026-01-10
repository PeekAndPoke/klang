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

/**
 * A value that can be either a number or a string.
 * Serializes directly to a JSON number or string.
 */
@Serializable(with = VoiceValueSerializer::class)
sealed interface VoiceValue {
    val asString: String
    val asDouble: Double?
    val asInt: Int?

    operator fun plus(amount: Double): VoiceValue? {
        val current = asDouble ?: return null
        return Num(current + amount)
    }

    operator fun plus(other: VoiceValue?): VoiceValue? {
        val current = asDouble ?: return null
        val amount = other?.asDouble ?: return null
        return Num(current + amount)
    }

    data class Num(val value: Double) : VoiceValue {
        override val asString get() = value.toString()
        override val asDouble get() = value
        override val asInt get() = value.toInt()
        override fun toString() = asString
    }

    data class Text(val value: String) : VoiceValue {
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
