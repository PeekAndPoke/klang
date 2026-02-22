@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceValue
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel._innerJoin
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.strudel.mapEvents
import io.peekandpoke.klang.strudel.pattern.ReinterpretPattern.Companion.reinterpretVoice

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangArithmeticInit = false

// Helper for arithmetic operations that modify the 'value' field
fun applyArithmetic(
    source: StrudelPattern,
    args: List<StrudelDslArg<Any?>>,
    op: (StrudelVoiceValue, StrudelVoiceValue) -> StrudelVoiceValue?,
): StrudelPattern {
    return source._innerJoin(args) { src, controlValue ->
        val controlVal = controlValue ?: return@_innerJoin silence

        // Apply the operation to each event in the source pattern
        src.mapEvents { event ->
            val sourceVal = event.data.value ?: return@mapEvents event
            val newVal = op(sourceVal, controlVal)
            event.copy(data = event.data.copy(value = newVal))
        }
    }
}

/**
 * Helper for applying unary operations to patterns.
 */
fun applyUnaryOp(
    source: StrudelPattern,
    op: (StrudelVoiceValue) -> StrudelVoiceValue?,
): StrudelPattern {
    // Unary ops (like log2) apply directly to the source values without a control pattern
    return source.reinterpretVoice { srcData ->
        val srcValue = srcData.value

        if (srcValue == null) {
            srcData
        } else {
            val newValue = op(srcValue)
            srcData.copy(value = newValue)
        }
    }
}

// -- add() ------------------------------------------------------------------------------------------------------------

internal val _add by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._add by dslPatternExtension { p, args, _ -> applyArithmetic(p, args) { a, b -> a + b } }
internal val String._add by dslStringExtension { p, args, callInfo -> p._add(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Adds [amount] to every numeric value in the pattern.
 *
 * Supports control patterns: pass a mini-notation string or another [StrudelPattern] as [amount]
 * to modulate the offset per cycle or event.
 *
 * @param amount The value to add. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern with each numeric value increased by [amount].
 *
 * Examples:
 *
 * ```KlangScript
 * seq("0 2").add(5).scale("c3:major").n()  // n values become 5 and 7
 * ```
 *
 * ```KlangScript
 * seq("0 2").add("<0 12>").scale("c3:major").n()  // add 0 or 12 alternately each cycle
 * ```
 *
 * @category arithmetic
 * @tags add, arithmetic, math, offset
 */
@StrudelDsl
fun StrudelPattern.add(amount: PatternLike): StrudelPattern = this._add(listOf(amount).asStrudelDslArgs())

/** Parses this string as a pattern, then adds [amount] to every numeric value. */
@StrudelDsl
fun String.add(amount: PatternLike): StrudelPattern = this._add(listOf(amount).asStrudelDslArgs())

/** Top-level [add] — always returns silence (use the extension form instead). */
@StrudelDsl
fun add(amount: PatternLike): StrudelPattern = _add(listOf(amount).asStrudelDslArgs())

// -- sub() ------------------------------------------------------------------------------------------------------------

internal val _sub by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._sub by dslPatternExtension { p, args, _ -> applyArithmetic(p, args) { a, b -> a - b } }
internal val String._sub by dslStringExtension { p, args, callInfo -> p._sub(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Subtracts [amount] from every numeric value in the pattern.
 *
 * Supports control patterns: pass a mini-notation string or another [StrudelPattern] as [amount]
 * to modulate the offset per cycle or event.
 *
 * @param amount The value to subtract. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern with each numeric value decreased by [amount].
 *
 * ```KlangScript
 * seq("10 20").sub(5).scale("c3:major").n()  // n values become 5 and 15
 * ```
 *
 * ```KlangScript
 * seq("10").sub("<0 5>").scale("c3:major").n()  // subtract 0 or 5 alternately each cycle
 * ```
 * @category arithmetic
 * @tags sub, subtract, arithmetic, math, offset
 */
@StrudelDsl
fun StrudelPattern.sub(amount: PatternLike): StrudelPattern = this._sub(listOf(amount).asStrudelDslArgs())

/** Parses this string as a pattern, then subtracts [amount] from every numeric value. */
@StrudelDsl
fun String.sub(amount: PatternLike): StrudelPattern = this._sub(listOf(amount).asStrudelDslArgs())

/** Top-level [sub] — always returns silence (use the extension form instead). */
@StrudelDsl
fun sub(amount: PatternLike): StrudelPattern = _sub(listOf(amount).asStrudelDslArgs())

// -- mul() ------------------------------------------------------------------------------------------------------------

internal val _mul by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._mul by dslPatternExtension { p, args, _ -> applyArithmetic(p, args) { a, b -> a * b } }
internal val String._mul by dslStringExtension { p, args, callInfo -> p._mul(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Multiplies every numeric value in the pattern by [factor].
 *
 * Supports control patterns: pass a mini-notation string or another [StrudelPattern] as [factor]
 * to modulate the scale per cycle or event.
 *
 * @param factor The multiplier. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern with each numeric value multiplied by [factor].
 *
 * ```KlangScript
 * seq("2 3").mul(4).scale("c3:major").n()  // values become 8 and 12
 * ```
 *
 * ```KlangScript
 * seq("1 2").mul("<1 2>").scale("c3:major").n()  // double every other cycle
 * ```
 * @category arithmetic
 * @tags mul, multiply, arithmetic, math, scale
 */
@StrudelDsl
fun StrudelPattern.mul(factor: PatternLike): StrudelPattern = this._mul(listOf(factor).asStrudelDslArgs())

/** Parses this string as a pattern, then multiplies every numeric value by [factor]. */
@StrudelDsl
fun String.mul(factor: PatternLike): StrudelPattern = this._mul(listOf(factor).asStrudelDslArgs())

/** Top-level [mul] — always returns silence (use the extension form instead). */
@StrudelDsl
fun mul(factor: PatternLike): StrudelPattern = _mul(listOf(factor).asStrudelDslArgs())

// -- div() ------------------------------------------------------------------------------------------------------------

internal val _div by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._div by dslPatternExtension { p, args, _ -> applyArithmetic(p, args) { a, b -> a / b } }
internal val String._div by dslStringExtension { p, args, callInfo -> p._div(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Divides every numeric value in the pattern by [divisor].
 *
 * Supports control patterns: pass a mini-notation string or another [StrudelPattern] as [divisor]
 * to modulate the division per cycle or event.
 *
 * @param divisor The divisor. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern with each numeric value divided by [divisor].
 *
 * ```KlangScript
 * seq("10 20").div(2).scale("c3:major").n()  // values become 5 and 10
 * ```
 *
 * ```KlangScript
 * seq("10 20").div("<1 2>").scale("c3:major").n()  // halve every other cycle
 * ```
 * @category arithmetic
 * @tags div, divide, arithmetic, math, scale
 */
@StrudelDsl
fun StrudelPattern.div(divisor: PatternLike): StrudelPattern = this._div(listOf(divisor).asStrudelDslArgs())

/** Parses this string as a pattern, then divides every numeric value by [divisor]. */
@StrudelDsl
fun String.div(divisor: PatternLike): StrudelPattern = this._div(listOf(divisor).asStrudelDslArgs())

/** Top-level [div] — always returns silence (use the extension form instead). */
@StrudelDsl
fun div(divisor: PatternLike): StrudelPattern = _div(listOf(divisor).asStrudelDslArgs())

// -- mod() ------------------------------------------------------------------------------------------------------------

internal val _mod by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._mod by dslPatternExtension { p, args, _ -> applyArithmetic(p, args) { a, b -> a % b } }
internal val String._mod by dslStringExtension { p, args, callInfo -> p._mod(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies modulo [divisor] to every numeric value in the pattern.
 *
 * Useful for wrapping note indices, step counters, or any cyclic numeric range.
 * Supports control patterns: pass a mini-notation string or another [StrudelPattern] as [divisor].
 *
 * @param divisor The modulus. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern where each value is replaced by `value % divisor`.
 *
 * ```KlangScript
 * seq("10 11").mod(3).scale("c3:major").n()  // values become 1 and 2
 * ```
 *
 * ```KlangScript
 * seq("0 1 2 3 4 5 6 7").mod(4).scale("c3:major").n()  // wraps at 4: 0 1 2 3 0 1 2 3
 * ```
 * @category arithmetic
 * @tags mod, modulo, arithmetic, math, wrap
 */
@StrudelDsl
fun StrudelPattern.mod(divisor: PatternLike): StrudelPattern = this._mod(listOf(divisor).asStrudelDslArgs())

/** Parses this string as a pattern, then applies modulo [divisor] to every numeric value. */
@StrudelDsl
fun String.mod(divisor: PatternLike): StrudelPattern = this._mod(listOf(divisor).asStrudelDslArgs())

/** Top-level [mod] — always returns silence (use the extension form instead). */
@StrudelDsl
fun mod(divisor: PatternLike): StrudelPattern = _mod(listOf(divisor).asStrudelDslArgs())

// -- pow() ------------------------------------------------------------------------------------------------------------

internal val _pow by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._pow by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a pow b }
}
internal val String._pow by dslStringExtension { p, args, callInfo -> p._pow(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Raises every numeric value in the pattern to the power of [exponent].
 *
 * Supports control patterns: pass a mini-notation string or another [StrudelPattern] as [exponent].
 *
 * @param exponent The exponent. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern where each value is replaced by `value ^ exponent`.
 *
 * ```KlangScript
 * seq("2 3").pow(3).scale("c3:major").n()  // values become 8 (2³) and 27 (3³)
 * ```
 *
 * ```KlangScript
 * seq("2").pow("<1 2 3>").scale("c3:major").n()  // 2, 4, 8 over three cycles
 * ```
 * @category arithmetic
 * @tags pow, power, exponent, arithmetic, math
 */
@StrudelDsl
fun StrudelPattern.pow(exponent: PatternLike): StrudelPattern = this._pow(listOf(exponent).asStrudelDslArgs())

/** Parses this string as a pattern, then raises every numeric value to the power of [exponent]. */
@StrudelDsl
fun String.pow(exponent: PatternLike): StrudelPattern = this._pow(listOf(exponent).asStrudelDslArgs())

/** Top-level [pow] — always returns silence (use the extension form instead). */
@StrudelDsl
fun pow(exponent: PatternLike): StrudelPattern = _pow(listOf(exponent).asStrudelDslArgs())

// -- band() (Bitwise AND) ---------------------------------------------------------------------------------------------

internal val _band by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._band by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a band b }
}
internal val String._band by dslStringExtension { p, args, callInfo -> p._band(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies bitwise AND of [mask] to every integer value in the pattern.
 *
 * Truncates values to integers before the operation.
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern where each value is replaced by `value & mask`.
 *
 * ```KlangScript
 * "12 15".band(10).scale("c3:major").n()  // 12&10=8, 15&10=10
 * ```
 *
 * ```KlangScript
 * "255".band("<15 240>").scale("c3:major").n()  // mask low or high nibble alternately
 * ```
 * @category arithmetic
 * @tags band, bitwise, and, arithmetic, binary
 */
@StrudelDsl
fun StrudelPattern.band(mask: PatternLike): StrudelPattern = this._band(listOf(mask).asStrudelDslArgs())

/** Parses this string as a pattern, then applies bitwise AND with [mask] to every integer value. */
@StrudelDsl
fun String.band(mask: PatternLike): StrudelPattern = this._band(listOf(mask).asStrudelDslArgs())

/** Top-level [band] — always returns silence (use the extension form instead). */
@StrudelDsl
fun band(mask: PatternLike): StrudelPattern = _band(listOf(mask).asStrudelDslArgs())

// -- bor() (Bitwise OR) -----------------------------------------------------------------------------------------------

internal val _bor by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._bor by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a bor b }
}
internal val String._bor by dslStringExtension { p, args, callInfo -> p._bor(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies bitwise OR of [mask] to every integer value in the pattern.
 *
 * Truncates values to integers before the operation.
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern where each value is replaced by `value | mask`.
 *
 * ```KlangScript
 * "8 4".bor(2).scale("c3:major").n()  // 8|2=10, 4|2=6
 * ```
 *
 * ```KlangScript
 * "0".bor("<1 2 4 8>").scale("c3:major").n()  // set individual bits each cycle
 * ```
 * @category arithmetic
 * @tags bor, bitwise, or, arithmetic, binary
 */
@StrudelDsl
fun StrudelPattern.bor(mask: PatternLike): StrudelPattern = this._bor(listOf(mask).asStrudelDslArgs())

/** Parses this string as a pattern, then applies bitwise OR with [mask] to every integer value. */
@StrudelDsl
fun String.bor(mask: PatternLike): StrudelPattern = this._bor(listOf(mask).asStrudelDslArgs())

/** Top-level [bor] — always returns silence (use the extension form instead). */
@StrudelDsl
fun bor(mask: PatternLike): StrudelPattern = _bor(listOf(mask).asStrudelDslArgs())

// -- bxor() (Bitwise XOR) ---------------------------------------------------------------------------------------------

internal val _bxor by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._bxor by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a bxor b }
}
internal val String._bxor by dslStringExtension { p, args, callInfo -> p._bxor(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies bitwise XOR of [mask] to every integer value in the pattern.
 *
 * Truncates values to integers before the operation. XOR is useful for toggling specific bits.
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern where each value is replaced by `value ^ mask`.
 *
 * ```KlangScript
 * "12 10".bxor(6).scale("c3:major").n()  // 12^6=10, 10^6=12
 * ```
 *
 * ```KlangScript
 * "5".bxor("<3 5>").scale("c3:major").n()  // toggle bits each cycle
 * ```
 * @category arithmetic
 * @tags bxor, bitwise, xor, arithmetic, binary
 */
@StrudelDsl
fun StrudelPattern.bxor(mask: PatternLike): StrudelPattern = this._bxor(listOf(mask).asStrudelDslArgs())

/** Parses this string as a pattern, then applies bitwise XOR with [mask] to every integer value. */
@StrudelDsl
fun String.bxor(mask: PatternLike): StrudelPattern = this._bxor(listOf(mask).asStrudelDslArgs())

/** Top-level [bxor] — always returns silence (use the extension form instead). */
@StrudelDsl
fun bxor(mask: PatternLike): StrudelPattern = _bxor(listOf(mask).asStrudelDslArgs())

// -- blshift() (Bitwise Left Shift) -----------------------------------------------------------------------------------

internal val _blshift by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._blshift by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a shl b }
}
internal val String._blshift by dslStringExtension { p, args, callInfo -> p._blshift(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Shifts every integer value in the pattern left by [bits] bits (equivalent to multiplying by 2ⁿ).
 *
 * Truncates values to integers before the operation.
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation,
 *   or a [StrudelPattern].
 * @return A new pattern where each value is replaced by `value << bits`.
 *
 * ```KlangScript
 * "1 2".blshift(2).scale("c3:major").n()  // 1<<2=4, 2<<2=8
 * ```
 *
 * ```KlangScript
 * "1".blshift("<0 1 2 3>").scale("c3:major").n()  // 1, 2, 4, 8 over four cycles
 * ```
 * @category arithmetic
 * @tags blshift, bitwise, shift, left shift, arithmetic, binary
 */
@StrudelDsl
fun StrudelPattern.blshift(bits: PatternLike): StrudelPattern = this._blshift(listOf(bits).asStrudelDslArgs())

/** Parses this string as a pattern, then shifts every integer value left by [bits] bits. */
@StrudelDsl
fun String.blshift(bits: PatternLike): StrudelPattern = this._blshift(listOf(bits).asStrudelDslArgs())

/** Top-level [blshift] — always returns silence (use the extension form instead). */
@StrudelDsl
fun blshift(bits: PatternLike): StrudelPattern = _blshift(listOf(bits).asStrudelDslArgs())

// -- brshift() (Bitwise Right Shift) ----------------------------------------------------------------------------------

internal val _brshift by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._brshift by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a shr b }
}
internal val String._brshift by dslStringExtension { p, args, callInfo -> p._brshift(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Shifts every integer value in the pattern right by [bits] bits (equivalent to integer-dividing by 2ⁿ).
 *
 * Truncates values to integers before the operation.
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation,
 *   or a [StrudelPattern].
 * @return A new pattern where each value is replaced by `value >> bits`.
 *
 * ```KlangScript
 * "8 12".brshift(2).scale("c3:major").n()  // 8>>2=2, 12>>2=3
 * ```
 *
 * ```KlangScript
 * "16".brshift("<0 1 2 3>").scale("c3:major").n()  // 16, 8, 4, 2 over four cycles
 * ```
 * @category arithmetic
 * @tags brshift, bitwise, shift, right shift, arithmetic, binary
 */
@StrudelDsl
fun StrudelPattern.brshift(bits: PatternLike): StrudelPattern = this._brshift(listOf(bits).asStrudelDslArgs())

/** Parses this string as a pattern, then shifts every integer value right by [bits] bits. */
@StrudelDsl
fun String.brshift(bits: PatternLike): StrudelPattern = this._brshift(listOf(bits).asStrudelDslArgs())

/** Top-level [brshift] — always returns silence (use the extension form instead). */
@StrudelDsl
fun brshift(bits: PatternLike): StrudelPattern = _brshift(listOf(bits).asStrudelDslArgs())

// -- log2() -----------------------------------------------------------------------------------------------------------

internal val _log2 by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._log2 by dslPatternExtension { p, _, _ -> applyUnaryOp(p) { it.log2() } }
internal val String._log2 by dslStringExtension { p, args, callInfo -> p._log2(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies log base 2 to every numeric value in the pattern.
 *
 * Useful for converting exponential frequency ratios to linear semitone or octave values.
 *
 * @return A new pattern where each value is replaced by `log₂(value)`.
 *
 * ```KlangScript
 * "8 16".log2().scale("c3:major").n()  // log₂(8)=3, log₂(16)=4
 * ```
 *
 * ```KlangScript
 * "1 2 4 8".log2().scale("c3:major").n()  // 0, 1, 2, 3
 * ```
 * @category arithmetic
 * @tags log2, logarithm, arithmetic, math
 */
@StrudelDsl
fun StrudelPattern.log2(): StrudelPattern = this._log2()

/** Parses this string as a pattern, then applies log base 2 to every numeric value. */
@StrudelDsl
fun String.log2(): StrudelPattern = this._log2()

/** Top-level [log2] — always returns silence (use the extension form instead). */
@StrudelDsl
fun log2(): StrudelPattern = _log2(emptyList())

// -- lt() (Less Than) -------------------------------------------------------------------------------------------------

internal val _lt by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._lt by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a lt b }
}
internal val String._lt by dslStringExtension { p, args, callInfo -> p._lt(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Compares every value in the pattern to [threshold], replacing each with `1` (true) if less than
 * [threshold] or `0` (false) otherwise.
 *
 * @param threshold The value to compare against. May be a number, string mini-notation,
 *   or a [StrudelPattern].
 * @return A new pattern of `0`/`1` values.
 *
 * ```KlangScript
 * "5 10".lt(8).scale("c3:major").n()  // 5<8 → 1, 10<8 → 0
 * ```
 *
 * ```KlangScript
 * "5 10".lt("<8 6>").scale("c3:major").n()  // threshold changes each cycle
 * ```
 * @category arithmetic
 * @tags lt, less than, comparison, arithmetic
 */
@StrudelDsl
fun StrudelPattern.lt(threshold: PatternLike): StrudelPattern = this._lt(listOf(threshold).asStrudelDslArgs())

/** Parses this string as a pattern, then compares every value to [threshold] using less-than. */
@StrudelDsl
fun String.lt(threshold: PatternLike): StrudelPattern = this._lt(listOf(threshold).asStrudelDslArgs())

/** Top-level [lt] — always returns silence (use the extension form instead). */
@StrudelDsl
fun lt(threshold: PatternLike): StrudelPattern = _lt(listOf(threshold).asStrudelDslArgs())

// -- gt() (Greater Than) ----------------------------------------------------------------------------------------------

internal val _gt by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._gt by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a gt b }
}
internal val String._gt by dslStringExtension { p, args, callInfo -> p._gt(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Compares every value in the pattern to [threshold], replacing each with `1` (true) if greater
 * than [threshold] or `0` (false) otherwise.
 *
 * @param threshold The value to compare against. May be a number, string mini-notation,
 *   or a [StrudelPattern].
 * @return A new pattern of `0`/`1` values.
 *
 * ```KlangScript
 * "5 10".gt(8).scale("c3:major").n()  // 5>8 → 0, 10>8 → 1
 * ```
 *
 * ```KlangScript
 * "5 10".gt("<8 6>").scale("c3:major").n()  // threshold changes each cycle
 * ```
 * @category arithmetic
 * @tags gt, greater than, comparison, arithmetic
 */
@StrudelDsl
fun StrudelPattern.gt(threshold: PatternLike): StrudelPattern = this._gt(listOf(threshold).asStrudelDslArgs())

/** Parses this string as a pattern, then compares every value to [threshold] using greater-than. */
@StrudelDsl
fun String.gt(threshold: PatternLike): StrudelPattern = this._gt(listOf(threshold).asStrudelDslArgs())

/** Top-level [gt] — always returns silence (use the extension form instead). */
@StrudelDsl
fun gt(threshold: PatternLike): StrudelPattern = _gt(listOf(threshold).asStrudelDslArgs())

// -- lte() (Less Than or Equal) ---------------------------------------------------------------------------------------

internal val _lte by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._lte by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a lte b }
}
internal val String._lte by dslStringExtension { p, args, callInfo -> p._lte(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Compares every value in the pattern to [threshold], replacing each with `1` (true) if less than
 * or equal to [threshold] or `0` (false) otherwise.
 *
 * @param threshold The value to compare against. May be a number, string mini-notation,
 *   or a [StrudelPattern].
 * @return A new pattern of `0`/`1` values.
 *
 * ```KlangScript
 * "5 8 10".lte(8).scale("c3:major").n()  // 5<=8 → 1, 8<=8 → 1, 10<=8 → 0
 * ```
 *
 * ```KlangScript
 * "5 8 10".lte("<8 6>").scale("c3:major").n()  // threshold changes each cycle
 * ```
 * @category arithmetic
 * @tags lte, less than or equal, comparison, arithmetic
 */
@StrudelDsl
fun StrudelPattern.lte(threshold: PatternLike): StrudelPattern = this._lte(listOf(threshold).asStrudelDslArgs())

/** Parses this string as a pattern, then compares every value to [threshold] using less-than-or-equal. */
@StrudelDsl
fun String.lte(threshold: PatternLike): StrudelPattern = this._lte(listOf(threshold).asStrudelDslArgs())

/** Top-level [lte] — always returns silence (use the extension form instead). */
@StrudelDsl
fun lte(threshold: PatternLike): StrudelPattern = _lte(listOf(threshold).asStrudelDslArgs())

// -- gte() (Greater Than or Equal) ------------------------------------------------------------------------------------

internal val _gte by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._gte by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a gte b }
}
internal val String._gte by dslStringExtension { p, args, callInfo -> p._gte(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Compares every value in the pattern to [threshold], replacing each with `1` (true) if greater
 * than or equal to [threshold] or `0` (false) otherwise.
 *
 * @param threshold The value to compare against. May be a number, string mini-notation,
 *   or a [StrudelPattern].
 * @return A new pattern of `0`/`1` values.
 *
 * ```KlangScript
 * "5 8 10".gte(8).scale("c3:major").n()  // 5>=8 → 0, 8>=8 → 1, 10>=8 → 1
 * ```
 *
 * ```KlangScript
 * "5 8 10".gte("<8 6>").scale("c3:major").n()  // threshold changes each cycle
 * ```
 * @category arithmetic
 * @tags gte, greater than or equal, comparison, arithmetic
 */
@StrudelDsl
fun StrudelPattern.gte(threshold: PatternLike): StrudelPattern = this._gte(listOf(threshold).asStrudelDslArgs())

/** Parses this string as a pattern, then compares every value to [threshold] using greater-than-or-equal. */
@StrudelDsl
fun String.gte(threshold: PatternLike): StrudelPattern = this._gte(listOf(threshold).asStrudelDslArgs())

/** Top-level [gte] — always returns silence (use the extension form instead). */
@StrudelDsl
fun gte(threshold: PatternLike): StrudelPattern = _gte(listOf(threshold).asStrudelDslArgs())

// -- eq() (Equal) -----------------------------------------------------------------------------------------------------

internal val _eq by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._eq by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a eq b }
}
internal val String._eq by dslStringExtension { p, args, callInfo -> p._eq(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Compares every value in the pattern to [other] for strict equality, replacing each with
 * `1` (true) if equal or `0` (false) otherwise.
 *
 * @param other The value to compare against. May be a number, string mini-notation,
 *   or a [StrudelPattern].
 * @return A new pattern of `0`/`1` values.
 *
 * ```KlangScript
 * "5 8".eq(8).scale("c3:major").n()  // 5==8 → 0, 8==8 → 1
 * ```
 *
 * ```KlangScript
 * "0 1 2 3".eq("<0 1>").scale("c3:major").n()  // equality alternates between 0 and 1 each cycle
 * ```
 * @category arithmetic
 * @tags eq, equal, equality, comparison, arithmetic
 */
@StrudelDsl
fun StrudelPattern.eq(other: PatternLike): StrudelPattern = this._eq(listOf(other).asStrudelDslArgs())

/** Parses this string as a pattern, then tests every value for strict equality with [other]. */
@StrudelDsl
fun String.eq(other: PatternLike): StrudelPattern = this._eq(listOf(other).asStrudelDslArgs())

/** Top-level [eq] — always returns silence (use the extension form instead). */
@StrudelDsl
fun eq(other: PatternLike): StrudelPattern = _eq(listOf(other).asStrudelDslArgs())

// -- eqt() (Truthiness Equal) -----------------------------------------------------------------------------------------

internal val _eqt by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._eqt by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a eqt b }
}
internal val String._eqt by dslStringExtension { p, args, callInfo -> p._eqt(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Compares the truthiness of every value in the pattern to the truthiness of [other], replacing
 * each with `1` (true) if both are truthy or both are falsy, or `0` (false) otherwise.
 *
 * A value is falsy if it is zero; otherwise it is truthy.
 *
 * @param other The value to compare against. May be a number, string mini-notation,
 *   or a [StrudelPattern].
 * @return A new pattern of `0`/`1` values.
 *
 * ```KlangScript
 * "0 5".eqt(0).scale("c3:major").n()  // 0~=0 → 1, 5~=0 → 0
 * ```
 *
 * ```KlangScript
 * "0 5".eqt(3).scale("c3:major").n()  // 0~=3 → 0, 5~=3 → 1 (both truthy)
 * ```
 * @category arithmetic
 * @tags eqt, truthiness, equal, comparison, arithmetic
 */
@StrudelDsl
fun StrudelPattern.eqt(other: PatternLike): StrudelPattern = this._eqt(listOf(other).asStrudelDslArgs())

/** Parses this string as a pattern, then tests every value for truthiness equality with [other]. */
@StrudelDsl
fun String.eqt(other: PatternLike): StrudelPattern = this._eqt(listOf(other).asStrudelDslArgs())

/** Top-level [eqt] — always returns silence (use the extension form instead). */
@StrudelDsl
fun eqt(other: PatternLike): StrudelPattern = _eqt(listOf(other).asStrudelDslArgs())

// -- ne() (Not Equal) -------------------------------------------------------------------------------------------------

internal val _ne by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._ne by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a ne b }
}
internal val String._ne by dslStringExtension { p, args, callInfo -> p._ne(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Compares every value in the pattern to [other] for strict inequality, replacing each with
 * `1` (true) if not equal or `0` (false) otherwise.
 *
 * @param other The value to compare against. May be a number, string mini-notation,
 *   or a [StrudelPattern].
 * @return A new pattern of `0`/`1` values.
 *
 * ```KlangScript
 * "5 8".ne(8).scale("c3:major").n()  // 5!=8 → 1, 8!=8 → 0
 * ```
 *
 * ```KlangScript
 * "0 1 2 3".ne("<0 1>").scale("c3:major").n()  // inequality alternates between 0 and 1 each cycle
 * ```
 * @category arithmetic
 * @tags ne, not equal, inequality, comparison, arithmetic
 */
@StrudelDsl
fun StrudelPattern.ne(other: PatternLike): StrudelPattern = this._ne(listOf(other).asStrudelDslArgs())

/** Parses this string as a pattern, then tests every value for strict inequality with [other]. */
@StrudelDsl
fun String.ne(other: PatternLike): StrudelPattern = this._ne(listOf(other).asStrudelDslArgs())

/** Top-level [ne] — always returns silence (use the extension form instead). */
@StrudelDsl
fun ne(other: PatternLike): StrudelPattern = _ne(listOf(other).asStrudelDslArgs())

// -- net() (Truthiness Not Equal) -------------------------------------------------------------------------------------

internal val _net by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._net by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a net b }
}
internal val String._net by dslStringExtension { p, args, callInfo -> p._net(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Compares the truthiness of every value in the pattern to the truthiness of [other], replacing
 * each with `1` (true) if their truthiness differs, or `0` (false) otherwise.
 *
 * A value is falsy if it is zero; otherwise it is truthy.
 *
 * @param other The value to compare against. May be a number, string mini-notation,
 *   or a [StrudelPattern].
 * @return A new pattern of `0`/`1` values.
 *
 * ```KlangScript
 * "0 5".net(0).scale("c3:major").n()  // 0~!=0 → 0, 5~!=0 → 1
 * ```
 *
 * ```KlangScript
 * "0 5".net(3).scale("c3:major").n()  // 0~!=3 → 1, 5~!=3 → 0 (both truthy)
 * ```
 * @category arithmetic
 * @tags net, truthiness, not equal, inequality, comparison, arithmetic
 */
@StrudelDsl
fun StrudelPattern.net(other: PatternLike): StrudelPattern = this._net(listOf(other).asStrudelDslArgs())

/** Parses this string as a pattern, then tests every value for truthiness inequality with [other]. */
@StrudelDsl
fun String.net(other: PatternLike): StrudelPattern = this._net(listOf(other).asStrudelDslArgs())

/** Top-level [net] — always returns silence (use the extension form instead). */
@StrudelDsl
fun net(other: PatternLike): StrudelPattern = _net(listOf(other).asStrudelDslArgs())

// -- and() (Logical AND) ----------------------------------------------------------------------------------------------

internal val _and by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._and by dslPatternExtension { source, args, _ ->
    applyArithmetic(source, args) { a, b -> a and b }
}
internal val String._and by dslStringExtension { p, args, callInfo -> p._and(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies logical AND between every value in the pattern and [other].
 *
 * Returns [other] when the source value is truthy (non-zero), or `0` when it is falsy (zero).
 * This mirrors JavaScript's `&&` short-circuit behaviour.
 *
 * @param other The right-hand operand. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern where each value is `value && other`.
 *
 * ```KlangScript
 * "0 5".and(10).scale("c3:major").n()  // 0&&10 → 0, 5&&10 → 10
 * ```
 *
 * ```KlangScript
 * "5".and("<0 10>").scale("c3:major").n()  // gate on/off each cycle
 * ```
 * @category arithmetic
 * @tags and, logical, boolean, arithmetic
 */
@StrudelDsl
fun StrudelPattern.and(other: PatternLike): StrudelPattern = this._and(listOf(other).asStrudelDslArgs())

/** Parses this string as a pattern, then applies logical AND with [other] to every value. */
@StrudelDsl
fun String.and(other: PatternLike): StrudelPattern = this._and(listOf(other).asStrudelDslArgs())

/** Top-level [and] — always returns silence (use the extension form instead). */
@StrudelDsl
fun and(other: PatternLike): StrudelPattern = _and(listOf(other).asStrudelDslArgs())

// -- or() (Logical OR) ------------------------------------------------------------------------------------------------

internal val _or by dslPatternFunction { _, _ -> silence }
internal val StrudelPattern._or by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a or b }
}
internal val String._or by dslStringExtension { p, args, callInfo -> p._or(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies logical OR between every value in the pattern and [other].
 *
 * Returns the source value when it is truthy (non-zero), or [other] when it is falsy (zero).
 * This mirrors JavaScript's `||` short-circuit behaviour.
 *
 * @param other The right-hand operand. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern where each value is `value || other`.
 *
 * ```KlangScript
 * "0 5".or(10).scale("c3:major").n()  // 0||10 → 10, 5||10 → 5
 * ```
 *
 * ```KlangScript
 * "0 5".or("<1 2>").scale("c3:major").n()  // fallback alternates each cycle
 * ```
 * @category arithmetic
 * @tags or, logical, boolean, arithmetic
 */
@StrudelDsl
fun StrudelPattern.or(other: PatternLike): StrudelPattern = this._or(listOf(other).asStrudelDslArgs())

/** Parses this string as a pattern, then applies logical OR with [other] to every value. */
@StrudelDsl
fun String.or(other: PatternLike): StrudelPattern = this._or(listOf(other).asStrudelDslArgs())

/** Top-level [or] — always returns silence (use the extension form instead). */
@StrudelDsl
fun or(other: PatternLike): StrudelPattern = _or(listOf(other).asStrudelDslArgs())

// -- round() ----------------------------------------------------------------------------------------------------------

internal val StrudelPattern._round by dslPatternExtension { p, _, _ ->
    applyUnaryOp(p) { v -> v.asRational?.round()?.asVoiceValue() ?: v }
}
internal val String._round by dslStringExtension { p, _, _ -> p._round() }

// ===== USER-FACING OVERLOADS =====

/**
 * Rounds every numeric value in the pattern to the nearest integer.
 *
 * Halfway values (e.g. 2.5) round up. Non-numeric values are passed through unchanged.
 *
 * @return A new pattern with each value rounded to the nearest integer.
 *
 * ```KlangScript
 * "2.4 2.5 2.6".round().scale("c3:major").n()  // 2, 3, 3
 * ```
 *
 * ```KlangScript
 * "0.1 0.9".round().scale("c3:major").n()  // 0, 1
 * ```
 * @category arithmetic
 * @tags round, rounding, arithmetic, math
 */
@StrudelDsl
fun StrudelPattern.round(): StrudelPattern = this._round()

/** Parses this string as a pattern, then rounds every numeric value to the nearest integer. */
@StrudelDsl
fun String.round(): StrudelPattern = this._round()

// -- floor() ----------------------------------------------------------------------------------------------------------

internal val StrudelPattern._floor by dslPatternExtension { p, _, _ ->
    applyUnaryOp(p) { v -> v.asRational?.floor()?.asVoiceValue() ?: v }
}
internal val String._floor by dslStringExtension { p, _, _ -> p._floor() }

// ===== USER-FACING OVERLOADS =====

/**
 * Floors every numeric value in the pattern to the largest integer less than or equal to the value.
 *
 * For negative numbers this rounds away from zero: `floor(-2.1) = -3`.
 * Non-numeric values are passed through unchanged.
 *
 * @return A new pattern with each value floored to an integer.
 *
 * ```KlangScript
 * "2.1 2.9".floor().scale("c3:major").n()  // 2, 2
 * ```
 *
 * ```KlangScript
 * "-2.1 -2.9".floor().scale("c3:major").n()  // -3, -3
 * ```
 * @category arithmetic
 * @tags floor, rounding, arithmetic, math
 */
@StrudelDsl
fun StrudelPattern.floor(): StrudelPattern = this._floor()

/** Parses this string as a pattern, then floors every numeric value to an integer. */
@StrudelDsl
fun String.floor(): StrudelPattern = this._floor()

// -- ceil() -----------------------------------------------------------------------------------------------------------

internal val StrudelPattern._ceil by dslPatternExtension { p, _, _ ->
    applyUnaryOp(p) { v -> v.asRational?.ceil()?.asVoiceValue() ?: v }
}
internal val String._ceil by dslStringExtension { p, _, _ -> p._ceil() }

// ===== USER-FACING OVERLOADS =====

/**
 * Ceils every numeric value in the pattern to the smallest integer greater than or equal to the value.
 *
 * For negative numbers this rounds toward zero: `ceil(-2.9) = -2`.
 * Non-numeric values are passed through unchanged.
 *
 * @return A new pattern with each value ceiled to an integer.
 *
 * ```KlangScript
 * "2.1 2.9".ceil().scale("c3:major").n()  // 3, 3
 * ```
 *
 * ```KlangScript
 * "-2.9 -2.1".ceil().scale("c3:major").n()  // -2, -2
 * ```
 * @category arithmetic
 * @tags ceil, ceiling, rounding, arithmetic, math
 */
@StrudelDsl
fun StrudelPattern.ceil(): StrudelPattern = this._ceil()

/** Parses this string as a pattern, then ceils every numeric value to an integer. */
@StrudelDsl
fun String.ceil(): StrudelPattern = this._ceil()
