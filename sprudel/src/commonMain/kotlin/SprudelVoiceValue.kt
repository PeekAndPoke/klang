/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import kotlin.math.log2
import kotlin.math.pow

/**
 * A musical value carried by a voice event — a number, string, boolean, sequence, or sub-pattern.
 *
 * Numbers are stored as [Double]. Musical time is NOT represented here (that is
 * [io.peekandpoke.klang.common.math.CycleTime]); these are *values* (gain, pan, note numbers, filter
 * cutoffs, control amounts), for which Double precision is ample.
 */
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

