/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.common.math.cents
import io.peekandpoke.klang.common.math.db
import io.peekandpoke.klang.common.math.semitones
import io.peekandpoke.klang.common.math.toDb
import io.peekandpoke.klang.common.math.toSemitones
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.StringValue
import kotlin.math.pow

/**
 * Number type extensions for KlangScript.
 *
 * Methods are registered as extensions on [NumberValue] in KlangScript.
 * The first parameter (`self`) is the receiver, injected automatically by the runtime.
 *
 * The methods that overlap `Math.*` (`pow`, `abs`, `sqrt`, `round`, `floor`, `ceil`, `min`, `max`) are
 * an additional spelling of the same call, not a replacement; the others (`clamp`, `rem`, `mod`, the
 * logarithms, the musical conversions) exist only as methods. A negative receiver needs parentheses,
 * `(-8).abs()`: the bare `-8.abs()` is refused as ambiguous, because Kotlin reads it as `-(8.abs())`.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.TypeExtensions(NumberValue::class)
internal object KlangScriptNumberExtensions {

    /**
     * Returns the string representation of the number.
     *
     * @param self The number
     * @return The string representation
     * @category number
     */
    @KlangScript.Method(name = "toString")
    fun asString(self: NumberValue): StringValue = StringValue(self.toDisplayString())

    // ── Tier 1: everyday patch math ─────────────────────────────────────

    /**
     * Raises the number to the power of exp.
     *
     * The reason this exists: `^` is bitwise XOR, not exponentiation, so `2^(7/12)` is 2 and not a
     * perfect fifth. Write `2.pow(7/12)` (or `2 ** (7/12)`) instead.
     *
     * ```KlangScript(Executable)
     * 2.pow(7/12)  // 1.4983, a perfect fifth
     * ```
     *
     * @param self The base
     * @param exp The exponent
     * @return self raised to exp
     * @category number
     * @tags arithmetic, exponent
     */
    @KlangScript.Method
    fun pow(self: NumberValue, exp: Double): Double = self.value.pow(exp)

    /**
     * Returns the absolute value of the number.
     *
     * ```KlangScript(Executable)
     * (-8).abs()  // 8
     * ```
     *
     * @param self The number
     * @return The absolute value
     * @category number
     * @tags arithmetic
     */
    @KlangScript.Method
    fun abs(self: NumberValue): Double = kotlin.math.abs(self.value)

    /**
     * Returns the square root of the number.
     *
     * ```KlangScript(Executable)
     * 16.sqrt()  // 4
     * ```
     *
     * @param self The number
     * @return The square root
     * @category number
     * @tags arithmetic, calculation
     */
    @KlangScript.Method
    fun sqrt(self: NumberValue): Double = kotlin.math.sqrt(self.value)

    /**
     * Rounds the number to the nearest integer, ties to the even neighbour.
     *
     * `Math.round` uses the same rule, so `2.5.round()` is 2 while `3.5.round()` is 4.
     *
     * ```KlangScript(Executable)
     * 1.7.round()  // 2
     * ```
     *
     * @param self The number
     * @return The rounded value
     * @category number
     * @tags rounding
     */
    @KlangScript.Method
    fun round(self: NumberValue): Double = kotlin.math.round(self.value)

    /**
     * Rounds the number down to the nearest integer.
     *
     * ```KlangScript(Executable)
     * 3.7.floor()  // 3
     * ```
     *
     * @param self The number
     * @return The floored value
     * @category number
     * @tags rounding
     */
    @KlangScript.Method
    fun floor(self: NumberValue): Double = kotlin.math.floor(self.value)

    /**
     * Rounds the number up to the nearest integer.
     *
     * ```KlangScript(Executable)
     * 3.2.ceil()  // 4
     * ```
     *
     * @param self The number
     * @return The ceiling value
     * @category number
     * @tags rounding
     */
    @KlangScript.Method
    fun ceil(self: NumberValue): Double = kotlin.math.ceil(self.value)

    /**
     * Enforces a minimum allowed value: this number, but at least [other].
     *
     * Reads as "take self, at a minimum other". Anything below [other] is raised to it;
     * values at or above [other] pass through unchanged. Same meaning as `min` on a
     * pattern and on a signal. For the smaller *of* two numbers use `Math.min(a, b)`.
     *
     * ```KlangScript(Executable)
     * 7.min(3)  // 7
     * ```
     *
     * @param self The number
     * @param other The minimum allowed value (floor)
     * @return The number, raised to [other] when it was below
     * @category number
     * @tags comparison, clamp, floor
     */
    @KlangScript.Method
    fun min(self: NumberValue, other: Double): Double = self.value.coerceAtLeast(other)

    /**
     * Enforces a maximum allowed value: this number, but at most [other].
     *
     * Reads as "take self, at a maximum other". Anything above [other] is lowered to it;
     * values at or below [other] pass through unchanged. Same meaning as `max` on a
     * pattern and on a signal. For the larger *of* two numbers use `Math.max(a, b)`.
     *
     * ```KlangScript(Executable)
     * 7.max(3)  // 3
     * ```
     *
     * @param self The number
     * @param other The maximum allowed value (cap)
     * @return The number, lowered to [other] when it was above
     * @category number
     * @tags comparison, clamp, cap
     */
    @KlangScript.Method
    fun max(self: NumberValue, other: Double): Double = self.value.coerceAtMost(other)

    /**
     * Clamps the number into the range from lo to hi, both inclusive.
     *
     * ```KlangScript(Executable)
     * 5.clamp(0, 3)  // 3
     * ```
     *
     * @param self The number
     * @param lo The lower bound
     * @param hi The upper bound
     * @return The number, moved into the range
     * @category number
     * @tags comparison, range
     */
    @KlangScript.Method
    fun clamp(self: NumberValue, lo: Double, hi: Double, callInfo: CallInfo? = null): Double {
        if (lo > hi) {
            throw KlangScriptTypeError(
                "clamp: lo ($lo) is greater than hi ($hi)",
                operation = "clamp",
                location = callInfo?.callLocation,
            )
        }

        return self.value.coerceIn(lo, hi)
    }

    /**
     * Returns the remainder of the division by n, with the sign of the dividend.
     *
     * This is the method form of `%`. Its twin is `mod`, which takes the sign of the divisor:
     * `-1.rem(12)` is -1 while `-1.mod(12)` is 11.
     *
     * ```KlangScript(Executable)
     * (-1).rem(12)  // -1
     * ```
     *
     * @param self The dividend
     * @param n The divisor
     * @return The remainder, with the sign of self
     * @category number
     * @tags arithmetic, remainder
     */
    @KlangScript.Method
    fun rem(self: NumberValue, n: Double, callInfo: CallInfo? = null): Double {
        if (n == 0.0) {
            throw KlangScriptTypeError("Modulo by zero", operation = "modulo", location = callInfo?.callLocation)
        }

        return self.value % n
    }

    /**
     * Returns the remainder of the division by n, with the sign of the divisor (floor modulo).
     *
     * Only on numbers: `mod` on a SIGNAL (an oscillator chain) is the engine's `%`, the same as `rem`,
     * and keeps a negative value negative.
     *
     * This is the one no operator can express: a negative offset wraps back into the range, which is
     * what pitch classes and cycle wrapping want. Its twin is `rem`, the method form of `%`, which
     * takes the sign of the dividend: `-1.mod(12)` is 11 while `-1.rem(12)` is -1.
     *
     * ```KlangScript(Executable)
     * (-1).mod(12)  // 11
     * ```
     *
     * @param self The dividend
     * @param n The divisor
     * @return The remainder, with the sign of n
     * @category number
     * @tags arithmetic, remainder
     */
    @KlangScript.Method
    fun mod(self: NumberValue, n: Double, callInfo: CallInfo? = null): Double {
        if (n == 0.0) {
            throw KlangScriptTypeError("Modulo by zero", operation = "modulo", location = callInfo?.callLocation)
        }

        return self.value.mod(n)
    }

    // ── Tier 2: logarithmic ─────────────────────────────────────────────

    /**
     * Returns the base 2 logarithm of the number.
     *
     * ```KlangScript(Executable)
     * 8.log2()  // 3
     * ```
     *
     * @param self The number
     * @return The base 2 logarithm
     * @category number
     * @tags logarithm
     */
    @KlangScript.Method
    fun log2(self: NumberValue): Double = kotlin.math.log2(self.value)

    /**
     * Returns the base 10 logarithm of the number.
     *
     * ```KlangScript(Executable)
     * 1000.log10()  // 3
     * ```
     *
     * @param self The number
     * @return The base 10 logarithm
     * @category number
     * @tags logarithm
     */
    @KlangScript.Method
    fun log10(self: NumberValue): Double = kotlin.math.log10(self.value)

    /**
     * Returns the natural logarithm of the number.
     *
     * ```KlangScript(Executable)
     * 1.ln()  // 0
     * ```
     *
     * @param self The number
     * @return The natural logarithm
     * @category number
     * @tags logarithm
     */
    @KlangScript.Method
    fun ln(self: NumberValue): Double = kotlin.math.ln(self.value)

    /**
     * Returns e raised to the power of the number, the inverse of `ln`.
     *
     * ```KlangScript(Executable)
     * 1.exp()  // 2.7183, the number e
     * ```
     *
     * @param self The exponent
     * @return e raised to self
     * @category number
     * @tags logarithm, exponent
     */
    @KlangScript.Method
    fun exp(self: NumberValue): Double = kotlin.math.exp(self.value)

    /**
     * Returns the sign of the number: -1 below zero, 0 at zero, 1 above zero.
     *
     * ```KlangScript(Executable)
     * (-3).sign()  // -1
     * ```
     *
     * @param self The number
     * @return The sign
     * @category number
     * @tags arithmetic
     */
    @KlangScript.Method
    fun sign(self: NumberValue): Double = kotlin.math.sign(self.value)

    // ── Tier 3: musical ─────────────────────────────────────────────────

    /**
     * Reads the number as semitones and returns the frequency ratio: 2^(self / 12).
     *
     * Use it wherever a detune or a transposition is a ratio, so the interval stays readable.
     * The inverse is `toSemitones`, and `"P5".toRatio()` says the same thing by name.
     *
     * ```KlangScript(Executable)
     * 7.semitones()  // 1.4983, a perfect fifth
     * ```
     *
     * @param self The number of semitones
     * @return The frequency ratio
     * @category number
     * @tags music, pitch
     */
    @KlangScript.Method
    fun semitones(self: NumberValue): Double = self.value.semitones()

    /**
     * Reads the number as cents and returns the frequency ratio: 2^(self / 1200).
     *
     * A cent is a hundredth of a semitone, the unit for fine detuning.
     *
     * ```KlangScript(Executable)
     * 50.cents()  // 1.0293, a quarter tone up
     * ```
     *
     * @param self The number of cents
     * @return The frequency ratio
     * @category number
     * @tags music, pitch
     */
    @KlangScript.Method
    fun cents(self: NumberValue): Double = self.value.cents()

    /**
     * Reads the number as a frequency ratio and returns its size in semitones: 12 * log2(self).
     *
     * The inverse of `semitones`. A ratio of 0 gives -Infinity and a negative ratio gives NaN,
     * exactly as the underlying logarithm does.
     *
     * ```KlangScript(Executable)
     * 1.5.toSemitones()  // 7.0196, a perfect fifth is a hair wider than 7 equal semitones
     * ```
     *
     * @param self The frequency ratio
     * @return The size in semitones
     * @category number
     * @tags music, pitch
     */
    @KlangScript.Method
    fun toSemitones(self: NumberValue): Double = self.value.toSemitones()

    /**
     * Reads the number as decibels and returns the linear gain: 10^(self / 20).
     *
     * Every mixing knob speaks dB and every engine gain is linear, so this is the conversion between
     * the two. The inverse is `toDb`.
     *
     * ```KlangScript(Executable)
     * (-6).db()  // 0.5012, half the amplitude
     * ```
     *
     * @param self The level in decibels
     * @return The linear gain
     * @category number
     * @tags music, gain
     */
    @KlangScript.Method
    fun db(self: NumberValue): Double = self.value.db()

    /**
     * Reads the number as a linear gain and returns the level in decibels: 20 * log10(self).
     *
     * The inverse of `db`. A gain of 0 gives -Infinity (silence) and a negative gain gives NaN,
     * exactly as the underlying logarithm does.
     *
     * ```KlangScript(Executable)
     * 0.5.toDb()  // -6.0206
     * ```
     *
     * @param self The linear gain
     * @return The level in decibels
     * @category number
     * @tags music, gain
     */
    @KlangScript.Method
    fun toDb(self: NumberValue): Double = self.value.toDb()
}
