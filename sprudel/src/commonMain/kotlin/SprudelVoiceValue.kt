package io.peekandpoke.klang.sprudel

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.common.math.RationalStringSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * A value that can be either a number or a string.
 * Serializes directly to a JSON number or string.
 */
@Serializable(with = SprudelVoiceValueSerializer::class)
sealed interface SprudelVoiceValue {
    val asBoolean: Boolean
    val asString: String
    val asDouble: Double?
    val asInt: Int?
    val asRational: Rational?

    fun isTruthy(): Boolean

    operator fun plus(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val r1 = asRational
        val r2 = other?.asRational

        // Numeric addition if both are numbers
        if (r1 != null && r2 != null) {
            return Num(r1 + r2)
        }

        // String concatenation otherwise
        val s2 = other?.asString ?: return null
        return Text(asString + s2)
    }

    operator fun minus(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val r1 = asRational ?: return null
        val r2 = other?.asRational ?: return null
        return Num(r1 - r2)
    }

    operator fun times(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val r1 = asRational ?: return null
        val r2 = other?.asRational ?: return null
        return Num(r1 * r2)
    }

    operator fun div(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val r1 = asRational ?: return null
        val r2 = other?.asRational ?: return null
        if (r2 == Rational.ZERO) return null
        return Num(r1 / r2)
    }

    operator fun rem(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val r1 = asRational ?: return null
        val r2 = other?.asRational ?: return null
        if (r2 == Rational.ZERO) return null
        return Num(r1 % r2)
    }

    infix fun pow(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val d1 = asRational ?: return null
        val d2 = other?.asRational ?: return null
        return Num(d1.pow(d2))
    }

    infix fun band(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num(Rational(i1 and i2))
    }

    infix fun bor(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num(Rational(i1 or i2))
    }

    infix fun bxor(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num(Rational(i1 xor i2))
    }

    infix fun shl(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num(Rational(i1 shl i2))
    }

    infix fun shr(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num(Rational(i1 shr i2))
    }

    fun log2(): SprudelVoiceValue? {
        val d = asRational ?: return null
        return Num(d.log2())
    }

    infix fun lt(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val r1 = asRational ?: return null
        val r2 = other?.asRational ?: return null
        return Num(if (r1 < r2) Rational.ONE else Rational.ZERO)
    }

    infix fun gt(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val r1 = asRational ?: return null
        val r2 = other?.asRational ?: return null
        return Num(if (r1 > r2) Rational.ONE else Rational.ZERO)
    }

    infix fun lte(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val r1 = asRational ?: return null
        val r2 = other?.asRational ?: return null
        return Num(if (r1 <= r2) Rational.ONE else Rational.ZERO)
    }

    infix fun gte(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val r1 = asRational ?: return null
        val r2 = other?.asRational ?: return null
        return Num(if (r1 >= r2) Rational.ONE else Rational.ZERO)
    }

    infix fun eq(other: SprudelVoiceValue?): SprudelVoiceValue? {
        // We try rational comparison first
        val r1 = asRational
        val r2 = other?.asRational
        if (r1 != null && r2 != null) {
            return Num(if (r1 == r2) Rational.ONE else Rational.ZERO)
        }
        // Fallback to string comparison
        return Num(if (asString == other?.asString) Rational.ONE else Rational.ZERO)
    }

    infix fun eqt(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val t1 = isTruthy()
        val t2 = other?.isTruthy() ?: false
        return Num(if (t1 == t2) Rational.ONE else Rational.ZERO)
    }

    infix fun net(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val t1 = isTruthy()
        val t2 = other?.isTruthy() ?: false
        return Num(if (t1 != t2) Rational.ONE else Rational.ZERO)
    }

    infix fun ne(other: SprudelVoiceValue?): SprudelVoiceValue? {
        // reuse eq logic
        val isEqual = eq(other)?.asRational == Rational.ONE
        return Num(if (!isEqual) Rational.ONE else Rational.ZERO)
    }

    // Logical AND: if 'this' is truthy, return 'other', else return 'this'
    infix fun and(other: SprudelVoiceValue?): SprudelVoiceValue? {
        return if (isTruthy()) other else this
    }

    // Logical OR: if 'this' is truthy, return 'this', else return 'other'
    infix fun or(other: SprudelVoiceValue?): SprudelVoiceValue? {
        return if (isTruthy()) this else other
    }

    data class Num(val value: Rational) : SprudelVoiceValue {
        override val asBoolean get() = isTruthy()
        override val asString get() = value.toDouble().toString()
        override val asDouble get() = value.toDouble()
        override val asInt get() = value.toInt()
        override val asRational get() = value
        override fun isTruthy() = value != Rational.ZERO
        override fun toString() = asString
    }

    data class Text(val value: String) : SprudelVoiceValue {
        override val asBoolean get() = isTruthy()
        override val asString get() = value
        override val asDouble get() = value.toDoubleOrNull()
        override val asInt get() = value.toDoubleOrNull()?.toInt()
        override val asRational get() = value.toDoubleOrNull()?.let { Rational(it) }
        override fun isTruthy(): Boolean {
            // If it can be interpreted as a number, use numerical truthiness (non-zero)
            val r = asRational
            if (r != null) {
                return r != Rational.ZERO
            }
            // Otherwise use string truthiness (not blank, not "false")
            return value.isNotBlank() && value != "false"
        }
        override fun toString() = asString
    }

    data class Bool(val value: Boolean) : SprudelVoiceValue {
        override val asBoolean get() = value
        override val asString get() = value.toString()
        override val asDouble get() = if (value) 1.0 else 0.0
        override val asInt get() = if (value) 1 else 0
        override val asRational get() = if (value) Rational.ONE else Rational.ZERO
        override fun isTruthy() = value
        override fun toString() = asString
    }

    data class Seq(val value: List<SprudelVoiceValue>) : SprudelVoiceValue {
        override val asBoolean get() = value.isNotEmpty()
        override val asString get() = value.joinToString(", ") { it.asString }
        override val asDouble get() = value.firstOrNull()?.asDouble
        override val asInt get() = value.firstOrNull()?.asInt
        override val asRational get() = value.firstOrNull()?.asRational
        override fun isTruthy() = value.isNotEmpty()
        override fun toString() = "[$asString]"
    }

    data class Pattern(val pattern: SprudelPattern) : SprudelVoiceValue {
        override val asBoolean get() = true
        override val asString get() = "<pattern>"
        override val asDouble get() = null
        override val asInt get() = null
        override val asRational get() = null
        override fun isTruthy() = true
        override fun toString() = asString
    }

    companion object {
        fun Double.asVoiceValue() = Num(Rational(this))

        fun Number.asVoiceValue() = Num(Rational(toDouble()))

        fun Rational.asVoiceValue() = Num(this)

        fun String.asVoiceValue() = Text(this)

        fun Boolean.asVoiceValue() = Bool(this)

        fun Any.asVoiceValue(): SprudelVoiceValue? = of(this)

        fun of(value: Any?): SprudelVoiceValue? = when (value) {
            is SprudelVoiceValue -> value
            is Rational -> Num(value)
            is Number -> Num(Rational(value.toDouble()))
            is String -> Text(value)
            is Boolean -> Bool(value)
            is SprudelPattern -> Pattern(value)
            else -> null
        }
    }
}

object SprudelVoiceValueSerializer : KSerializer<SprudelVoiceValue> {
    // We use a Contextual descriptor or a specialized one.
    // Since it can be Double or String, simpler to treat as a generic primitive structure
    // but declaring it as CONTEXTUAL allows flexible handling.
    // However, to make it work with JsonPrimitive, we often use specific kinds.
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SprudelVoiceValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SprudelVoiceValue) {
        when (value) {
            is SprudelVoiceValue.Num -> {
                // Use string serialization for Rational (e.g., "2/3" or "-5/8")
                encoder.encodeSerializableValue(RationalStringSerializer, value.value)
            }

            is SprudelVoiceValue.Text -> encoder.encodeString(value.value)
            is SprudelVoiceValue.Bool -> encoder.encodeBoolean(value.value)
            is SprudelVoiceValue.Seq -> encoder.encodeSerializableValue(
                ListSerializer(SprudelVoiceValueSerializer),
                value.value
            )

            is SprudelVoiceValue.Pattern -> encoder.encodeString("<pattern>")
        }
    }

    override fun deserialize(decoder: Decoder): SprudelVoiceValue {
        // Optimization for JSON: inspect the element type
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            return if (element is JsonPrimitive) {
                when {
                    element.isString -> {
                        val content = element.content
                        // Try to parse as a Rational first (handles "2/3" format)
                        // Check if it looks like a fraction
                        if ("/" in content) {
                            val parts = content.split("/")
                            if (parts.size == 2) {
                                val num = parts[0].trim().toDoubleOrNull()
                                val den = parts[1].trim().toDoubleOrNull()
                                if (num != null && den != null) {
                                    // Division by zero or malformed results in NaN
                                    if (den == 0.0) {
                                        return SprudelVoiceValue.Num(Rational.NaN)
                                    }
                                    return SprudelVoiceValue.Num(Rational(num / den))
                                }
                            }
                            // Malformed fraction - treat as text
                        }
                        // Otherwise it's just text
                        SprudelVoiceValue.Text(content)
                    }
                    element.content == "true" || element.content == "false" ->
                        SprudelVoiceValue.Bool(element.content.toBoolean())

                    else -> {
                        // It's a number
                        val d = element.doubleOrNull
                        if (d != null) SprudelVoiceValue.Num(Rational(d)) else SprudelVoiceValue.Text(element.content)
                    }
                }
            } else if (element is JsonArray) {
                // It's an array
                val items = element.map {
                    Json.decodeFromJsonElement(SprudelVoiceValueSerializer, it)
                }
                SprudelVoiceValue.Seq(items)
            } else {
                SprudelVoiceValue.Text(element.toString())
            }
        }

        // Fallback for generic decoders (like Properties or CBOR):
        // Try to decode as string first (supports fraction format like "2/3")
        try {
            val str = decoder.decodeString()
            // Check if it's a fraction format
            if ("/" in str) {
                val parts = str.split("/")
                if (parts.size == 2) {
                    val num = parts[0].trim().toDoubleOrNull()
                    val den = parts[1].trim().toDoubleOrNull()
                    if (num != null && den != null) {
                        // Division by zero results in NaN
                        if (den == 0.0) {
                            return SprudelVoiceValue.Num(Rational.NaN)
                        }
                        return SprudelVoiceValue.Num(Rational(num / den))
                    }
                }
                // Malformed fraction - treat as text
            }
            // Try parsing as a number
            val d = str.toDoubleOrNull()
            return if (d != null) SprudelVoiceValue.Num(Rational(d)) else SprudelVoiceValue.Text(str)
        } catch (e: Exception) {
            // If string decoding fails, maybe it was encoded as a number?
            try {
                val d = decoder.decodeDouble()
                return SprudelVoiceValue.Num(Rational(d))
            } catch (_: Exception) {
                // Maybe it's a boolean?
                try {
                    val b = decoder.decodeBoolean()
                    return SprudelVoiceValue.Bool(b)
                } catch (_: Exception) {
                    throw e // Giving up
                }
            }
        }
    }
}
