package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.RationalStringSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.math.pow

/**
 * A value that can be either a number or a string.
 * Serializes directly to a JSON number or string.
 */
@Serializable(with = StrudelVoiceValueSerializer::class)
sealed interface StrudelVoiceValue {
    val asBoolean: Boolean
    val asString: String
    val asDouble: Double?
    val asInt: Int?
    val asRational: Rational?

    fun isTruthy(): Boolean

    operator fun plus(other: StrudelVoiceValue?): StrudelVoiceValue? {
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

    operator fun minus(other: StrudelVoiceValue?): StrudelVoiceValue? {
        val r1 = asRational ?: return null
        val r2 = other?.asRational ?: return null
        return Num(r1 - r2)
    }

    operator fun times(other: StrudelVoiceValue?): StrudelVoiceValue? {
        val r1 = asRational ?: return null
        val r2 = other?.asRational ?: return null
        return Num(r1 * r2)
    }

    operator fun div(other: StrudelVoiceValue?): StrudelVoiceValue? {
        val r1 = asRational ?: return null
        val r2 = other?.asRational ?: return null
        if (r2 == Rational.ZERO) return null
        return Num(r1 / r2)
    }

    operator fun rem(other: StrudelVoiceValue?): StrudelVoiceValue? {
        val r1 = asRational ?: return null
        val r2 = other?.asRational ?: return null
        if (r2 == Rational.ZERO) return null
        return Num(r1 % r2)
    }

    infix fun pow(other: StrudelVoiceValue?): StrudelVoiceValue? {
        val d1 = asDouble ?: return null
        val d2 = other?.asDouble ?: return null
        return Num(Rational(d1.pow(d2)))
    }

    infix fun band(other: StrudelVoiceValue?): StrudelVoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num(Rational(i1 and i2))
    }

    infix fun bor(other: StrudelVoiceValue?): StrudelVoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num(Rational(i1 or i2))
    }

    infix fun bxor(other: StrudelVoiceValue?): StrudelVoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num(Rational(i1 xor i2))
    }

    infix fun shl(other: StrudelVoiceValue?): StrudelVoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num(Rational(i1 shl i2))
    }

    infix fun shr(other: StrudelVoiceValue?): StrudelVoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num(Rational(i1 shr i2))
    }

    fun log2(): StrudelVoiceValue? {
        val d = asDouble ?: return null
        return Num(Rational(kotlin.math.log2(d)))
    }

    infix fun lt(other: StrudelVoiceValue?): StrudelVoiceValue? {
        val r1 = asRational ?: return null
        val r2 = other?.asRational ?: return null
        return Num(if (r1 < r2) Rational.ONE else Rational.ZERO)
    }

    infix fun gt(other: StrudelVoiceValue?): StrudelVoiceValue? {
        val r1 = asRational ?: return null
        val r2 = other?.asRational ?: return null
        return Num(if (r1 > r2) Rational.ONE else Rational.ZERO)
    }

    infix fun lte(other: StrudelVoiceValue?): StrudelVoiceValue? {
        val r1 = asRational ?: return null
        val r2 = other?.asRational ?: return null
        return Num(if (r1 <= r2) Rational.ONE else Rational.ZERO)
    }

    infix fun gte(other: StrudelVoiceValue?): StrudelVoiceValue? {
        val r1 = asRational ?: return null
        val r2 = other?.asRational ?: return null
        return Num(if (r1 >= r2) Rational.ONE else Rational.ZERO)
    }

    infix fun eq(other: StrudelVoiceValue?): StrudelVoiceValue? {
        // We try rational comparison first
        val r1 = asRational
        val r2 = other?.asRational
        if (r1 != null && r2 != null) {
            return Num(if (r1 == r2) Rational.ONE else Rational.ZERO)
        }
        // Fallback to string comparison
        return Num(if (asString == other?.asString) Rational.ONE else Rational.ZERO)
    }

    infix fun eqt(other: StrudelVoiceValue?): StrudelVoiceValue? {
        val t1 = isTruthy()
        val t2 = other?.isTruthy() ?: false
        return Num(if (t1 == t2) Rational.ONE else Rational.ZERO)
    }

    infix fun net(other: StrudelVoiceValue?): StrudelVoiceValue? {
        val t1 = isTruthy()
        val t2 = other?.isTruthy() ?: false
        return Num(if (t1 != t2) Rational.ONE else Rational.ZERO)
    }

    infix fun ne(other: StrudelVoiceValue?): StrudelVoiceValue? {
        // reuse eq logic
        val isEqual = eq(other)?.asRational == Rational.ONE
        return Num(if (!isEqual) Rational.ONE else Rational.ZERO)
    }

    // Logical AND: if 'this' is truthy, return 'other', else return 'this'
    infix fun and(other: StrudelVoiceValue?): StrudelVoiceValue? {
        return if (isTruthy()) other else this
    }

    // Logical OR: if 'this' is truthy, return 'this', else return 'other'
    infix fun or(other: StrudelVoiceValue?): StrudelVoiceValue? {
        return if (isTruthy()) this else other
    }

    data class Num(val value: Rational) : StrudelVoiceValue {
        override val asBoolean get() = isTruthy()
        override val asString get() = value.toDouble().toString()
        override val asDouble get() = value.toDouble()
        override val asInt get() = value.toInt()
        override val asRational get() = value
        override fun isTruthy() = value != Rational.ZERO
        override fun toString() = asString
    }

    data class Text(val value: String) : StrudelVoiceValue {
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

    data class Bool(val value: Boolean) : StrudelVoiceValue {
        override val asBoolean get() = value
        override val asString get() = value.toString()
        override val asDouble get() = if (value) 1.0 else 0.0
        override val asInt get() = if (value) 1 else 0
        override val asRational get() = if (value) Rational.ONE else Rational.ZERO
        override fun isTruthy() = value
        override fun toString() = asString
    }

    data class Seq(val value: List<StrudelVoiceValue>) : StrudelVoiceValue {
        override val asBoolean get() = value.isNotEmpty()
        override val asString get() = value.joinToString(", ") { it.asString }
        override val asDouble get() = value.firstOrNull()?.asDouble
        override val asInt get() = value.firstOrNull()?.asInt
        override val asRational get() = value.firstOrNull()?.asRational
        override fun isTruthy() = value.isNotEmpty()
        override fun toString() = "[${asString}]"
    }

    data class Pattern(val pattern: StrudelPattern) : StrudelVoiceValue {
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

        fun Any.asVoiceValue(): StrudelVoiceValue? = of(this)

        fun of(value: Any?): StrudelVoiceValue? = when (value) {
            is StrudelVoiceValue -> value
            is Rational -> Num(value)
            is Number -> Num(Rational(value.toDouble()))
            is String -> Text(value)
            is Boolean -> Bool(value)
            is StrudelPattern -> Pattern(value)
            else -> null
        }
    }
}

object StrudelVoiceValueSerializer : KSerializer<StrudelVoiceValue> {
    // We use a Contextual descriptor or a specialized one.
    // Since it can be Double or String, simpler to treat as a generic primitive structure
    // but declaring it as CONTEXTUAL allows flexible handling.
    // However, to make it work with JsonPrimitive, we often use specific kinds.
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("StrudelVoiceValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: StrudelVoiceValue) {
        when (value) {
            is StrudelVoiceValue.Num -> {
                // Use string serialization for Rational (e.g., "2/3" or "-5/8")
                encoder.encodeSerializableValue(RationalStringSerializer, value.value)
            }
            is StrudelVoiceValue.Text -> encoder.encodeString(value.value)
            is StrudelVoiceValue.Bool -> encoder.encodeBoolean(value.value)
            is StrudelVoiceValue.Seq -> encoder.encodeSerializableValue(
                ListSerializer(StrudelVoiceValueSerializer),
                value.value
            )
            is StrudelVoiceValue.Pattern -> encoder.encodeString("<pattern>")
        }
    }

    override fun deserialize(decoder: Decoder): StrudelVoiceValue {
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
                                        return StrudelVoiceValue.Num(Rational.NaN)
                                    }
                                    return StrudelVoiceValue.Num(Rational(num / den))
                                }
                            }
                            // Malformed fraction - treat as text
                        }
                        // Otherwise it's just text
                        StrudelVoiceValue.Text(content)
                    }
                    element.content == "true" || element.content == "false" ->
                        StrudelVoiceValue.Bool(element.content.toBoolean())

                    else -> {
                        // It's a number
                        val d = element.doubleOrNull
                        if (d != null) StrudelVoiceValue.Num(Rational(d)) else StrudelVoiceValue.Text(element.content)
                    }
                }
            } else if (element is JsonArray) {
                // It's an array
                val items = element.map {
                    Json.decodeFromJsonElement(StrudelVoiceValueSerializer, it)
                }
                StrudelVoiceValue.Seq(items)
            } else {
                StrudelVoiceValue.Text(element.toString())
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
                            return StrudelVoiceValue.Num(Rational.NaN)
                        }
                        return StrudelVoiceValue.Num(Rational(num / den))
                    }
                }
                // Malformed fraction - treat as text
            }
            // Try parsing as a number
            val d = str.toDoubleOrNull()
            return if (d != null) StrudelVoiceValue.Num(Rational(d)) else StrudelVoiceValue.Text(str)
        } catch (e: Exception) {
            // If string decoding fails, maybe it was encoded as a number?
            try {
                val d = decoder.decodeDouble()
                return StrudelVoiceValue.Num(Rational(d))
            } catch (_: Exception) {
                // Maybe it's a boolean?
                try {
                    val b = decoder.decodeBoolean()
                    return StrudelVoiceValue.Bool(b)
                } catch (_: Exception) {
                    throw e // Giving up
                }
            }
        }
    }
}
