package io.peekandpoke.klang.sprudel

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.log2
import kotlin.math.pow

/**
 * A musical value carried by a voice event — a number, string, boolean, sequence, or sub-pattern.
 *
 * Numbers are stored as [Double]. Musical time is NOT represented here (that is
 * [io.peekandpoke.klang.common.math.CycleTime]); these are *values* (gain, pan, note numbers, filter
 * cutoffs, control amounts), for which Double precision is ample.
 */
@Serializable(with = SprudelVoiceValueSerializer::class)
sealed interface SprudelVoiceValue {
    val asBoolean: Boolean
    val asString: String
    val asDouble: Double?
    val asInt: Int?

    fun isTruthy(): Boolean

    operator fun plus(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val a = asDouble
        val b = other?.asDouble

        // Numeric addition if both are numbers
        if (a != null && b != null) {
            return Num(a + b)
        }

        // String concatenation otherwise
        val s2 = other?.asString ?: return null
        return Text(asString + s2)
    }

    operator fun minus(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val a = asDouble ?: return null
        val b = other?.asDouble ?: return null
        return Num(a - b)
    }

    operator fun times(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val a = asDouble ?: return null
        val b = other?.asDouble ?: return null
        return Num(a * b)
    }

    operator fun div(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val a = asDouble ?: return null
        val b = other?.asDouble ?: return null
        if (b == 0.0) return null
        return Num(a / b)
    }

    operator fun rem(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val a = asDouble ?: return null
        val b = other?.asDouble ?: return null
        if (b == 0.0) return null
        return Num(a % b)
    }

    infix fun pow(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val a = asDouble ?: return null
        val b = other?.asDouble ?: return null
        return Num(a.pow(b))
    }

    infix fun band(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num((i1 and i2).toDouble())
    }

    infix fun bor(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num((i1 or i2).toDouble())
    }

    infix fun bxor(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num((i1 xor i2).toDouble())
    }

    infix fun shl(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num((i1 shl i2).toDouble())
    }

    infix fun shr(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val i1 = asInt ?: return null
        val i2 = other?.asInt ?: return null
        return Num((i1 shr i2).toDouble())
    }

    fun log2(): SprudelVoiceValue? {
        val d = asDouble ?: return null
        return Num(log2(d))
    }

    infix fun lt(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val a = asDouble ?: return null
        val b = other?.asDouble ?: return null
        return Num(if (a < b) 1.0 else 0.0)
    }

    infix fun gt(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val a = asDouble ?: return null
        val b = other?.asDouble ?: return null
        return Num(if (a > b) 1.0 else 0.0)
    }

    infix fun lte(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val a = asDouble ?: return null
        val b = other?.asDouble ?: return null
        return Num(if (a <= b) 1.0 else 0.0)
    }

    infix fun gte(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val a = asDouble ?: return null
        val b = other?.asDouble ?: return null
        return Num(if (a >= b) 1.0 else 0.0)
    }

    infix fun eq(other: SprudelVoiceValue?): SprudelVoiceValue? {
        // We try numeric comparison first
        val a = asDouble
        val b = other?.asDouble
        if (a != null && b != null) {
            return Num(if (a == b) 1.0 else 0.0)
        }
        // Fallback to string comparison
        return Num(if (asString == other?.asString) 1.0 else 0.0)
    }

    infix fun eqt(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val t1 = isTruthy()
        val t2 = other?.isTruthy() ?: false
        return Num(if (t1 == t2) 1.0 else 0.0)
    }

    infix fun net(other: SprudelVoiceValue?): SprudelVoiceValue? {
        val t1 = isTruthy()
        val t2 = other?.isTruthy() ?: false
        return Num(if (t1 != t2) 1.0 else 0.0)
    }

    infix fun ne(other: SprudelVoiceValue?): SprudelVoiceValue? {
        // reuse eq logic
        val isEqual = eq(other)?.asDouble == 1.0
        return Num(if (!isEqual) 1.0 else 0.0)
    }

    // Logical AND: if 'this' is truthy, return 'other', else return 'this'
    infix fun and(other: SprudelVoiceValue?): SprudelVoiceValue? {
        return if (isTruthy()) other else this
    }

    // Logical OR: if 'this' is truthy, return 'this', else return 'other'
    infix fun or(other: SprudelVoiceValue?): SprudelVoiceValue? {
        return if (isTruthy()) this else other
    }

    data class Num(val value: Double) : SprudelVoiceValue {
        override val asBoolean get() = isTruthy()
        override val asString get() = value.toString()
        override val asDouble get() = value
        override val asInt get() = value.toInt()
        override fun isTruthy() = value != 0.0
        override fun toString() = asString
    }

    data class Text(val value: String) : SprudelVoiceValue {
        override val asBoolean get() = isTruthy()
        override val asString get() = value
        override val asDouble get() = value.toDoubleOrNull()
        override val asInt get() = value.toDoubleOrNull()?.toInt()
        override fun isTruthy(): Boolean {
            // If it can be interpreted as a number, use numerical truthiness (non-zero)
            val d = asDouble
            if (d != null) {
                return d != 0.0
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
        override fun isTruthy() = value
        override fun toString() = asString
    }

    data class Seq(val value: List<SprudelVoiceValue>) : SprudelVoiceValue {
        override val asBoolean get() = value.isNotEmpty()
        override val asString get() = value.joinToString(", ") { it.asString }
        override val asDouble get() = value.firstOrNull()?.asDouble
        override val asInt get() = value.firstOrNull()?.asInt
        override fun isTruthy() = value.isNotEmpty()
        override fun toString() = "[$asString]"
    }

    data class Pattern(val pattern: SprudelPattern) : SprudelVoiceValue {
        override val asBoolean get() = true
        override val asString get() = "<pattern>"
        override val asDouble get() = null
        override val asInt get() = null
        override fun isTruthy() = true
        override fun toString() = asString
    }

    companion object {
        fun Double.asVoiceValue() = Num(this)

        fun Number.asVoiceValue() = Num(toDouble())

        fun String.asVoiceValue() = Text(this)

        fun Boolean.asVoiceValue() = Bool(this)

        fun Any.asVoiceValue(): SprudelVoiceValue? = of(this)

        fun of(value: Any?): SprudelVoiceValue? = when (value) {
            is SprudelVoiceValue -> value
            is Number -> Num(value.toDouble())
            is String -> Text(value)
            is Boolean -> Bool(value)
            is SprudelPattern -> Pattern(value)
            else -> null
        }
    }
}

object SprudelVoiceValueSerializer : KSerializer<SprudelVoiceValue> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SprudelVoiceValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SprudelVoiceValue) {
        when (value) {
            is SprudelVoiceValue.Num -> encoder.encodeDouble(value.value)
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
                        // Legacy fraction form "a/b" (older persisted patterns) → parse to Double.
                        parseFraction(content)?.let { return SprudelVoiceValue.Num(it) }
                        // Otherwise it's just text
                        SprudelVoiceValue.Text(content)
                    }

                    element.content == "true" || element.content == "false" ->
                        SprudelVoiceValue.Bool(element.content.toBoolean())

                    else -> {
                        val d = element.doubleOrNull
                        if (d != null) SprudelVoiceValue.Num(d) else SprudelVoiceValue.Text(element.content)
                    }
                }
            } else if (element is JsonArray) {
                val items = element.map {
                    Json.decodeFromJsonElement(SprudelVoiceValueSerializer, it)
                }
                SprudelVoiceValue.Seq(items)
            } else {
                SprudelVoiceValue.Text(element.toString())
            }
        }

        // Fallback for generic decoders (Properties / CBOR)
        try {
            val str = decoder.decodeString()
            parseFraction(str)?.let { return SprudelVoiceValue.Num(it) }
            val d = str.toDoubleOrNull()
            return if (d != null) SprudelVoiceValue.Num(d) else SprudelVoiceValue.Text(str)
        } catch (e: Exception) {
            try {
                return SprudelVoiceValue.Num(decoder.decodeDouble())
            } catch (_: Exception) {
                try {
                    return SprudelVoiceValue.Bool(decoder.decodeBoolean())
                } catch (_: Exception) {
                    throw e
                }
            }
        }
    }

    /** Parses a legacy `"a/b"` fraction string to a Double, or null if not a fraction. */
    private fun parseFraction(content: String): Double? {
        if ("/" !in content) return null
        val parts = content.split("/")
        if (parts.size != 2) return null
        val num = parts[0].trim().toDoubleOrNull() ?: return null
        val den = parts[1].trim().toDoubleOrNull() ?: return null
        return if (den == 0.0) Double.NaN else num / den
    }
}
