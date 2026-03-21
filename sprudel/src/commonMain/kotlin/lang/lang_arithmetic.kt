@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.StrudelVoiceValue
import io.peekandpoke.klang.sprudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel._innerJoin
import io.peekandpoke.klang.sprudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.sprudel.mapEvents
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice

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

internal val StrudelPattern._add by dslPatternExtension { p, args, _ -> applyArithmetic(p, args) { a, b -> a + b } }
internal val String._add by dslStringExtension { p, args, callInfo -> p._add(args, callInfo) }
internal val _add by dslPatternMapper { args, callInfo -> { p -> p._add(args, callInfo) } }
internal val PatternMapperFn._add by dslPatternMapperExtension { m, args, callInfo -> m.chain(_add(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Adds [amount] to every numeric value in the pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Supports control patterns: pass a mini-notation string or another
 * [StrudelPattern] as [amount] to modulate the offset per cycle or event.
 *
 * ```KlangScript
 * seq("0 2").add(5).scale("c3:major").n()  // n values become 5 and 7
 * ```
 *
 * ```KlangScript
 * seq("0 2").add("<0 12>").scale("c3:major").n()  // add 0 or 12 alternately each cycle
 * ```
 *
 * @param amount The value to add. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern with each numeric value increased by [amount].
 * @category arithmetic
 * @tags add, arithmetic, math, offset
 */
@StrudelDsl
fun StrudelPattern.add(amount: PatternLike): StrudelPattern = this._add(listOf(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then adds [amount] to every numeric value.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "0 2".add(5).scale("c3:major").n()  // n values become 5 and 7
 * ```
 *
 * @param amount The value to add. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.add(amount: PatternLike): StrudelPattern = this._add(listOf(amount).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that adds [amount] to every numeric value in a pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the addition to an existing pattern.
 *
 * ```KlangScript
 * seq("0 2").apply(add(5)).scale("c3:major").n()  // n values become 5 and 7
 * ```
 *
 * @param amount The value to add. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun add(amount: PatternLike): PatternMapperFn = _add(listOf(amount).asStrudelDslArgs())

/**
 * Chains a PatternMapperFn to this pattern, adding [amount] to every numeric value in the result.
 *
 * ```KlangScript
 * seq("10 20").apply(mul(2).add(3)).scale("c1:major").n()  // (10*2)+3=23, (20*2)+3=43
 * ```
 *
 * @param amount The value to subtract. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.add(amount: PatternLike): PatternMapperFn = _add(listOf(amount).asStrudelDslArgs())

// -- sub() ------------------------------------------------------------------------------------------------------------

internal val _sub by dslPatternMapper { args, callInfo -> { p -> p._sub(args, callInfo) } }
internal val StrudelPattern._sub by dslPatternExtension { p, args, _ -> applyArithmetic(p, args) { a, b -> a - b } }
internal val String._sub by dslStringExtension { p, args, callInfo -> p._sub(args, callInfo) }
internal val PatternMapperFn._sub by dslPatternMapperExtension { m, args, callInfo -> m.chain(_sub(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Subtracts [amount] from every numeric value in the pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Supports control patterns: pass a mini-notation string or another
 * [StrudelPattern] as [amount] to modulate the offset per cycle or event.
 *
 * ```KlangScript
 * seq("10 20").sub(5).scale("c3:major").n()  // n values become 5 and 15
 * ```
 *
 * ```KlangScript
 * seq("10").sub("<0 5>").scale("c3:major").n()  // subtract 0 or 5 alternately each cycle
 * ```
 *
 * @param amount The value to subtract. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern with each numeric value decreased by [amount].
 * @category arithmetic
 * @tags sub, subtract, arithmetic, math, offset
 */
@StrudelDsl
fun StrudelPattern.sub(amount: PatternLike): StrudelPattern = this._sub(listOf(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then subtracts [amount] from every numeric value.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "10 20".sub(5).scale("c3:major").n()  // n values become 5 and 15
 * ```
 *
 * @param amount The value to subtract. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.sub(amount: PatternLike): StrudelPattern = this._sub(listOf(amount).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that subtracts [amount] from every numeric value in a pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the subtraction to an existing pattern.
 *
 * ```KlangScript
 * seq("10 20").apply(sub(5)).scale("c3:major").n()  // n values become 5 and 15
 * ```
 *
 * @param amount The value to subtract. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun sub(amount: PatternLike): PatternMapperFn = _sub(listOf(amount).asStrudelDslArgs())

/**
 * Chains a subtraction onto this [PatternMapperFn], subtracting [amount] from every numeric value in the result.
 *
 * ```KlangScript
 * seq("10 20").apply(mul(2).sub(3)).scale("c1:major").n()  // (10*2)-3=17, (20*2)-3=37
 * ```
 *
 * @param amount The value to subtract. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.sub(amount: PatternLike): PatternMapperFn = _sub(listOf(amount).asStrudelDslArgs())

// -- mul() ------------------------------------------------------------------------------------------------------------

internal val _mul by dslPatternMapper { args, callInfo -> { p -> p._mul(args, callInfo) } }
internal val StrudelPattern._mul by dslPatternExtension { p, args, _ -> applyArithmetic(p, args) { a, b -> a * b } }
internal val String._mul by dslStringExtension { p, args, callInfo -> p._mul(args, callInfo) }
internal val PatternMapperFn._mul by dslPatternMapperExtension { m, args, callInfo -> m.chain(_mul(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Multiplies every numeric value in the pattern by [factor].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Supports control patterns: pass a mini-notation string or another
 * [StrudelPattern] as [factor] to modulate the scale per cycle or event.
 *
 * ```KlangScript
 * seq("2 3").mul(4).scale("c3:major").n()  // values become 8 and 12
 * ```
 *
 * ```KlangScript
 * seq("1 2").mul("<1 2>").scale("c3:major").n()  // double every other cycle
 * ```
 *
 * @param factor The multiplier. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern with each numeric value multiplied by [factor].
 * @category arithmetic
 * @tags mul, multiply, arithmetic, math, scale
 */
@StrudelDsl
fun StrudelPattern.mul(factor: PatternLike): StrudelPattern = this._mul(listOf(factor).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then multiplies every numeric value by [factor].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "2 3".mul(4).scale("c3:major").n()  // values become 8 and 12
 * ```
 *
 * @param factor The multiplier. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.mul(factor: PatternLike): StrudelPattern = this._mul(listOf(factor).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that multiplies every numeric value in a pattern by [factor].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the multiplication to an existing pattern.
 *
 * ```KlangScript
 * seq("2 3").apply(mul(4)).scale("c3:major").n()  // values become 8 and 12
 * ```
 *
 * @param factor The multiplier. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun mul(factor: PatternLike): PatternMapperFn = _mul(listOf(factor).asStrudelDslArgs())

/**
 * Chains a multiplication onto this [PatternMapperFn], multiplying every numeric value by [factor].
 *
 * ```KlangScript
 * seq("1 2").apply(add(1).mul(3)).scale("c2:major").n()  // (1+1)*3=6, (2+1)*3=9
 * ```
 *
 * @param factor The multiplier. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.mul(factor: PatternLike): PatternMapperFn = _mul(listOf(factor).asStrudelDslArgs())

// -- div() ------------------------------------------------------------------------------------------------------------

internal val _div by dslPatternMapper { args, callInfo -> { p -> p._div(args, callInfo) } }
internal val StrudelPattern._div by dslPatternExtension { p, args, _ -> applyArithmetic(p, args) { a, b -> a / b } }
internal val String._div by dslStringExtension { p, args, callInfo -> p._div(args, callInfo) }
internal val PatternMapperFn._div by dslPatternMapperExtension { m, args, callInfo -> m.chain(_div(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Divides every numeric value in the pattern by [divisor].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Supports control patterns: pass a mini-notation string or another
 * [StrudelPattern] as [divisor] to modulate the division per cycle or event.
 *
 * ```KlangScript
 * seq("10 20").div(2).scale("c3:major").n()  // values become 5 and 10
 * ```
 *
 * ```KlangScript
 * seq("10 20").div("<1 2>").scale("c3:major").n()  // halve every other cycle
 * ```
 *
 * @param divisor The divisor. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern with each numeric value divided by [divisor].
 * @category arithmetic
 * @tags div, divide, arithmetic, math, scale
 */
@StrudelDsl
fun StrudelPattern.div(divisor: PatternLike): StrudelPattern = this._div(listOf(divisor).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then divides every numeric value by [divisor].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "10 20".div(2).scale("c3:major").n()  // values become 5 and 10
 * ```
 *
 * @param divisor The divisor. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.div(divisor: PatternLike): StrudelPattern = this._div(listOf(divisor).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that divides every numeric value in a pattern by [divisor].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the division to an existing pattern.
 *
 * ```KlangScript
 * seq("10 20").apply(div(2)).scale("c3:major").n()  // values become 5 and 10
 * ```
 *
 * @param divisor The divisor. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun div(divisor: PatternLike): PatternMapperFn = _div(listOf(divisor).asStrudelDslArgs())

/**
 * Chains a division onto this [PatternMapperFn], dividing every numeric value by [divisor].
 *
 * ```KlangScript
 * seq("10 20").apply(mul(2).div(4)).scale("c2:major").n()  // (10*2)/4=5, (20*2)/4=10
 * ```
 *
 * @param divisor The divisor. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.div(divisor: PatternLike): PatternMapperFn = _div(listOf(divisor).asStrudelDslArgs())

// -- mod() ------------------------------------------------------------------------------------------------------------

internal val _mod by dslPatternMapper { args, callInfo -> { p -> p._mod(args, callInfo) } }
internal val StrudelPattern._mod by dslPatternExtension { p, args, _ -> applyArithmetic(p, args) { a, b -> a % b } }
internal val String._mod by dslStringExtension { p, args, callInfo -> p._mod(args, callInfo) }
internal val PatternMapperFn._mod by dslPatternMapperExtension { m, args, callInfo -> m.chain(_mod(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies modulo [divisor] to every numeric value in the pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Useful for wrapping note indices, step counters, or any cyclic numeric range.
 * Division by zero is safe — events with a zero divisor are silenced. Supports control patterns:
 * pass a mini-notation string or another [StrudelPattern] as [divisor].
 *
 * ```KlangScript
 * seq("10 11").mod(3).scale("c3:major").n()  // values become 1 and 2
 * ```
 *
 * ```KlangScript
 * seq("0 1 2 3 4 5 6 7").mod(4).scale("c3:major").n()  // wraps at 4: 0 1 2 3 0 1 2 3
 * ```
 *
 * @param divisor The modulus. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern where each value is replaced by `value % divisor`.
 * @category arithmetic
 * @tags mod, modulo, arithmetic, math, wrap
 */
@StrudelDsl
fun StrudelPattern.mod(divisor: PatternLike): StrudelPattern = this._mod(listOf(divisor).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then applies modulo [divisor] to every numeric value.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Division by zero is safe — events with a zero divisor are silenced.
 *
 * ```KlangScript
 * "10 11".mod(3).scale("c3:major").n()  // values become 1 and 2
 * ```
 *
 * @param divisor The modulus. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.mod(divisor: PatternLike): StrudelPattern = this._mod(listOf(divisor).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that applies modulo [divisor] to every numeric value in a pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Division by zero is safe — events with a zero divisor are silenced.
 * Use with [StrudelPattern.apply] to apply the modulo to an existing pattern.
 *
 * ```KlangScript
 * seq("0 1 2 3 4 5 6 7").apply(mod(4)).scale("c3:major").n()  // wraps at 4: 0 1 2 3 0 1 2 3
 * ```
 *
 * @param divisor The modulus. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun mod(divisor: PatternLike): PatternMapperFn = _mod(listOf(divisor).asStrudelDslArgs())

/**
 * Chains a modulo operation onto this [PatternMapperFn], applying modulo [divisor] to every numeric value.
 *
 * ```KlangScript
 * seq("10 11").apply(add(1).mod(4)).scale("c3:major").n()  // (10+1)%4=3, (11+1)%4=0
 * ```
 *
 * @param divisor The modulus. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.mod(divisor: PatternLike): PatternMapperFn = _mod(listOf(divisor).asStrudelDslArgs())

// -- pow() ------------------------------------------------------------------------------------------------------------

internal val _pow by dslPatternMapper { args, callInfo -> { p -> p._pow(args, callInfo) } }
internal val StrudelPattern._pow by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a pow b }
}
internal val String._pow by dslStringExtension { p, args, callInfo -> p._pow(args, callInfo) }
internal val PatternMapperFn._pow by dslPatternMapperExtension { m, args, callInfo -> m.chain(_pow(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Raises every numeric value in the pattern to the power of [exponent].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Supports control patterns: pass a mini-notation string or another
 * [StrudelPattern] as [exponent] to modulate the exponent per cycle or event.
 *
 * ```KlangScript
 * seq("2 3").pow(3).scale("c3:major").n()  // values become 8 (2³) and 27 (3³)
 * ```
 *
 * ```KlangScript
 * seq("2").pow("<1 2 3>").scale("c3:major").n()  // 2, 4, 8 over three cycles
 * ```
 *
 * @param exponent The exponent. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern where each value is replaced by `value ^ exponent`.
 * @category arithmetic
 * @tags pow, power, exponent, arithmetic, math
 */
@StrudelDsl
fun StrudelPattern.pow(exponent: PatternLike): StrudelPattern = this._pow(listOf(exponent).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then raises every numeric value to the power of [exponent].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "2 3".pow(3).scale("c3:major").n()  // values become 8 (2³) and 27 (3³)
 * ```
 *
 * @param exponent The exponent. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.pow(exponent: PatternLike): StrudelPattern = this._pow(listOf(exponent).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that raises every numeric value in a pattern to the power of [exponent].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the exponentiation to an existing pattern.
 *
 * ```KlangScript
 * seq("2 3").apply(pow(3)).scale("c3:major").n()  // values become 8 (2³) and 27 (3³)
 * ```
 *
 * @param exponent The exponent. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun pow(exponent: PatternLike): PatternMapperFn = _pow(listOf(exponent).asStrudelDslArgs())

/**
 * Chains an exponentiation onto this [PatternMapperFn], raising every numeric value to [exponent].
 *
 * ```KlangScript
 * seq("2 3").apply(add(1).pow(2)).scale("c2:major").n()  // (2+1)^2=9, (3+1)^2=16
 * ```
 *
 * @param exponent The exponent. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.pow(exponent: PatternLike): PatternMapperFn = _pow(listOf(exponent).asStrudelDslArgs())

// -- band() (Bitwise AND) ---------------------------------------------------------------------------------------------

internal val _band by dslPatternMapper { args, callInfo -> { p -> p._band(args, callInfo) } }
internal val StrudelPattern._band by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a band b }
}
internal val String._band by dslStringExtension { p, args, callInfo -> p._band(args, callInfo) }
internal val PatternMapperFn._band by dslPatternMapperExtension { m, args, callInfo -> m.chain(_band(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies bitwise AND of [mask] to every integer value in the pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Values are truncated to integers before the operation.
 *
 * ```KlangScript
 * "12 15".band(10).scale("c3:major").n()  // 12&10=8, 15&10=10
 * ```
 *
 * ```KlangScript
 * "127".band("<15 63>").scale("c3:major").n()  // mask low or high nibble alternately
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern where each value is replaced by `value & mask`.
 * @category arithmetic
 * @tags band, bitwise, and, arithmetic, binary
 */
@StrudelDsl
fun StrudelPattern.band(mask: PatternLike): StrudelPattern = this._band(listOf(mask).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then applies bitwise AND with [mask] to every integer value.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "12 15".band(10).scale("c3:major").n()  // 12&10=8, 15&10=10
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.band(mask: PatternLike): StrudelPattern = this._band(listOf(mask).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that applies bitwise AND of [mask] to every integer value in a pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the mask to an existing pattern.
 *
 * ```KlangScript
 * seq("12 15").apply(band(10)).scale("c3:major").n()  // 12&10=8, 15&10=10
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun band(mask: PatternLike): PatternMapperFn = _band(listOf(mask).asStrudelDslArgs())

/**
 * Chains a bitwise AND onto this [PatternMapperFn], applying [mask] to every integer value.
 *
 * ```KlangScript
 * seq("12 15").apply(add(3).band(10)).scale("c2:major").n()  // (12+3)&10=10, (15+3)&10=2
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.band(mask: PatternLike): PatternMapperFn = _band(listOf(mask).asStrudelDslArgs())

// -- bor() (Bitwise OR) -----------------------------------------------------------------------------------------------

internal val _bor by dslPatternMapper { args, callInfo -> { p -> p._bor(args, callInfo) } }
internal val StrudelPattern._bor by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a bor b }
}
internal val String._bor by dslStringExtension { p, args, callInfo -> p._bor(args, callInfo) }
internal val PatternMapperFn._bor by dslPatternMapperExtension { m, args, callInfo -> m.chain(_bor(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies bitwise OR of [mask] to every integer value in the pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Values are truncated to integers before the operation.
 *
 * ```KlangScript
 * "8 4".bor(2).scale("c3:major").n()  // 8|2=10, 4|2=6
 * ```
 *
 * ```KlangScript
 * "0".bor("<1 2 4 8>").scale("c3:major").n()  // set individual bits each cycle
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern where each value is replaced by `value | mask`.
 * @category arithmetic
 * @tags bor, bitwise, or, arithmetic, binary
 */
@StrudelDsl
fun StrudelPattern.bor(mask: PatternLike): StrudelPattern = this._bor(listOf(mask).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then applies bitwise OR with [mask] to every integer value.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "8 4".bor(2).scale("c3:major").n()  // 8|2=10, 4|2=6
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.bor(mask: PatternLike): StrudelPattern = this._bor(listOf(mask).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that applies bitwise OR of [mask] to every integer value in a pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the mask to an existing pattern.
 *
 * ```KlangScript
 * seq("8 4").apply(bor(2)).scale("c3:major").n()  // 8|2=10, 4|2=6
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun bor(mask: PatternLike): PatternMapperFn = _bor(listOf(mask).asStrudelDslArgs())

/**
 * Chains a bitwise OR onto this [PatternMapperFn], applying [mask] to every integer value.
 *
 * ```KlangScript
 * seq("8 4").apply(add(1).bor(2)).scale("c2:major").n()  // (8+1)|2=11, (4+1)|2=7
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.bor(mask: PatternLike): PatternMapperFn = _bor(listOf(mask).asStrudelDslArgs())

// -- bxor() (Bitwise XOR) ---------------------------------------------------------------------------------------------

internal val _bxor by dslPatternMapper { args, callInfo -> { p -> p._bxor(args, callInfo) } }
internal val StrudelPattern._bxor by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a bxor b }
}
internal val String._bxor by dslStringExtension { p, args, callInfo -> p._bxor(args, callInfo) }
internal val PatternMapperFn._bxor by dslPatternMapperExtension { m, args, callInfo -> m.chain(_bxor(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies bitwise XOR of [mask] to every integer value in the pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Values are truncated to integers before the operation. XOR is useful for
 * toggling specific bits.
 *
 * ```KlangScript
 * "12 10".bxor(6).scale("c3:major").n()  // 12^6=10, 10^6=12
 * ```
 *
 * ```KlangScript
 * "5".bxor("<3 5>").scale("c3:major").n()  // toggle bits each cycle
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern where each value is replaced by `value ^ mask`.
 * @category arithmetic
 * @tags bxor, bitwise, xor, arithmetic, binary
 */
@StrudelDsl
fun StrudelPattern.bxor(mask: PatternLike): StrudelPattern = this._bxor(listOf(mask).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then applies bitwise XOR with [mask] to every integer value.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "12 10".bxor(6).scale("c3:major").n()  // 12^6=10, 10^6=12
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.bxor(mask: PatternLike): StrudelPattern = this._bxor(listOf(mask).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that applies bitwise XOR of [mask] to every integer value in a pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the mask to an existing pattern.
 *
 * ```KlangScript
 * seq("12 10").apply(bxor(6)).scale("c3:major").n()  // 12^6=10, 10^6=12
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun bxor(mask: PatternLike): PatternMapperFn = _bxor(listOf(mask).asStrudelDslArgs())

/**
 * Chains a bitwise XOR onto this [PatternMapperFn], applying [mask] to every integer value.
 *
 * ```KlangScript
 * seq("12 10").apply(add(2).bxor(6)).scale("c2:major").n()  // (12+2)^6=8, (10+2)^6=10
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.bxor(mask: PatternLike): PatternMapperFn = _bxor(listOf(mask).asStrudelDslArgs())

// -- blshift() (Bitwise Left Shift) -----------------------------------------------------------------------------------

internal val _blshift by dslPatternMapper { args, callInfo -> { p -> p._blshift(args, callInfo) } }
internal val StrudelPattern._blshift by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a shl b }
}
internal val String._blshift by dslStringExtension { p, args, callInfo -> p._blshift(args, callInfo) }
internal val PatternMapperFn._blshift by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(
        _blshift(
            args,
            callInfo
        )
    )
}

// ===== USER-FACING OVERLOADS =====

/**
 * Shifts every integer value in the pattern left by [bits] bits (equivalent to multiplying by 2ⁿ).
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Values are truncated to integers before the operation.
 *
 * ```KlangScript
 * "1 2".blshift(2).scale("c3:major").n()  // 1<<2=4, 2<<2=8
 * ```
 *
 * ```KlangScript
 * "1".blshift("<0 1 2 3>").scale("c3:major").n()  // 1, 2, 4, 8 over four cycles
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern where each value is replaced by `value << bits`.
 * @category arithmetic
 * @tags blshift, bitwise, shift, left shift, arithmetic, binary
 */
@StrudelDsl
fun StrudelPattern.blshift(bits: PatternLike): StrudelPattern = this._blshift(listOf(bits).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then shifts every integer value left by [bits] bits.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "1 2".blshift(2).scale("c3:major").n()  // 1<<2=4, 2<<2=8
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.blshift(bits: PatternLike): StrudelPattern = this._blshift(listOf(bits).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that shifts every integer value in a pattern left by [bits] bits.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the shift to an existing pattern.
 *
 * ```KlangScript
 * seq("1 2").apply(blshift(2)).scale("c3:major").n()  // 1<<2=4, 2<<2=8
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation,
 *   or a [StrudelPattern].
 */
@StrudelDsl
fun blshift(bits: PatternLike): PatternMapperFn = _blshift(listOf(bits).asStrudelDslArgs())

/**
 * Chains a bitwise left-shift onto this [PatternMapperFn], shifting every integer value left by [bits] bits.
 *
 * ```KlangScript
 * seq("1 2").apply(add(1).blshift(2)).scale("c2:major").n()  // (1+1)<<2=8, (2+1)<<2=12
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.blshift(bits: PatternLike): PatternMapperFn = _blshift(listOf(bits).asStrudelDslArgs())

// -- brshift() (Bitwise Right Shift) ----------------------------------------------------------------------------------

internal val _brshift by dslPatternMapper { args, callInfo -> { p -> p._brshift(args, callInfo) } }
internal val StrudelPattern._brshift by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a shr b }
}
internal val String._brshift by dslStringExtension { p, args, callInfo -> p._brshift(args, callInfo) }
internal val PatternMapperFn._brshift by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(
        _brshift(
            args,
            callInfo
        )
    )
}

// ===== USER-FACING OVERLOADS =====

/**
 * Shifts every integer value in the pattern right by [bits] bits (equivalent to integer-dividing by 2ⁿ).
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Values are truncated to integers before the operation.
 *
 * ```KlangScript
 * "8 12".brshift(2).scale("c3:major").n()  // 8>>2=2, 12>>2=3
 * ```
 *
 * ```KlangScript
 * "16".brshift("<0 1 2 3>").scale("c3:major").n()  // 16, 8, 4, 2 over four cycles
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern where each value is replaced by `value >> bits`.
 * @category arithmetic
 * @tags brshift, bitwise, shift, right shift, arithmetic, binary
 */
@StrudelDsl
fun StrudelPattern.brshift(bits: PatternLike): StrudelPattern = this._brshift(listOf(bits).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then shifts every integer value right by [bits] bits.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "8 12".brshift(2).scale("c3:major").n()  // 8>>2=2, 12>>2=3
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.brshift(bits: PatternLike): StrudelPattern = this._brshift(listOf(bits).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that shifts every integer value in a pattern right by [bits] bits.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the shift to an existing pattern.
 *
 * ```KlangScript
 * seq("8 12").apply(brshift(2)).scale("c3:major").n()  // 8>>2=2, 12>>2=3
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun brshift(bits: PatternLike): PatternMapperFn = _brshift(listOf(bits).asStrudelDslArgs())

/**
 * Chains a bitwise right-shift onto this [PatternMapperFn], shifting every integer value right by [bits] bits.
 *
 * ```KlangScript
 * seq("8 16").apply(mul(2).brshift(3)).scale("c3:major").n()  // (8*2)>>3=2, (16*2)>>3=4
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.brshift(bits: PatternLike): PatternMapperFn = _brshift(listOf(bits).asStrudelDslArgs())

// -- log2() -----------------------------------------------------------------------------------------------------------

internal val _log2 by dslPatternMapper { args, callInfo -> { p -> p._log2(args, callInfo) } }
internal val StrudelPattern._log2 by dslPatternExtension { p, _, _ -> applyUnaryOp(p) { it.log2() } }
internal val String._log2 by dslStringExtension { p, args, callInfo -> p._log2(args, callInfo) }
internal val PatternMapperFn._log2 by dslPatternMapperExtension { m, _, _ -> m.chain(_log2(emptyList())) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies log base 2 to every numeric value in the pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Useful for converting exponential frequency ratios to linear semitone or
 * octave values.
 *
 * ```KlangScript
 * "8 16".log2().scale("c3:major").n()  // log₂(8)=3, log₂(16)=4
 * ```
 *
 * ```KlangScript
 * "1 2 4 8".log2().scale("c3:major").n()  // 0, 1, 2, 3
 * ```
 *
 * @return A new pattern where each value is replaced by `log₂(value)`.
 * @category arithmetic
 * @tags log2, logarithm, arithmetic, math
 */
@StrudelDsl
fun StrudelPattern.log2(): StrudelPattern = this._log2()

/**
 * Parses this string as a pattern, then applies log base 2 to every numeric value.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "1 2 4 8".log2().scale("c3:major").n()  // 0, 1, 2, 3
 * ```
 */
@StrudelDsl
fun String.log2(): StrudelPattern = this._log2()

/**
 * Creates a [PatternMapperFn] that applies log base 2 to every numeric value in a pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the transform to an existing pattern.
 *
 * ```KlangScript
 * seq("1 2 4 8").apply(log2()).scale("c3:major").n()  // 0, 1, 2, 3
 * ```
 */
@StrudelDsl
fun log2(): PatternMapperFn = _log2(emptyList())

/**
 * Chains a log₂ operation onto this [PatternMapperFn], applying log base 2 to every numeric value.
 *
 * ```KlangScript
 * seq("2 4").apply(mul(4).log2()).scale("c3:major").n()  // log2(2*4)=log2(8)=3, log2(4*4)=log2(16)=4
 * ```
 */
@StrudelDsl
fun PatternMapperFn.log2(): PatternMapperFn = _log2(emptyList())

// -- lt() (Less Than) -------------------------------------------------------------------------------------------------

internal val _lt by dslPatternMapper { args, callInfo -> { p -> p._lt(args, callInfo) } }
internal val StrudelPattern._lt by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a lt b }
}
internal val String._lt by dslStringExtension { p, args, callInfo -> p._lt(args, callInfo) }
internal val PatternMapperFn._lt by dslPatternMapperExtension { m, args, callInfo -> m.chain(_lt(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Compares every value in the pattern to [threshold], replacing each with `1` (true) if less
 * than [threshold] or `0` (false) otherwise.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Supports control patterns: pass a mini-notation string or another
 * [StrudelPattern] as [threshold] to modulate the threshold per cycle or event.
 *
 * ```KlangScript
 * seq("5 10").lt(8).scale("c3:major").n()  // 5<8 → 1, 10<8 → 0
 * ```
 *
 * ```KlangScript
 * seq("5 10").lt("<8 6>").scale("c3:major").n()  // threshold changes each cycle
 * ```
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags lt, less than, comparison, arithmetic
 */
@StrudelDsl
fun StrudelPattern.lt(threshold: PatternLike): StrudelPattern = this._lt(listOf(threshold).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then compares every value to [threshold] using less-than.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "5 10".lt(8).scale("c3:major").n()  // 5<8 → 1, 10<8 → 0
 * ```
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.lt(threshold: PatternLike): StrudelPattern = this._lt(listOf(threshold).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that compares every value in a pattern to [threshold], replacing
 * each with `1` (true) if less than [threshold] or `0` (false) otherwise.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the comparison to an existing pattern.
 *
 * ```KlangScript
 * seq("5 10").apply(lt(8)).scale("c3:major").n()  // 5<8 → 1, 10<8 → 0
 * ```
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun lt(threshold: PatternLike): PatternMapperFn = _lt(listOf(threshold).asStrudelDslArgs())

/**
 * Chains a less-than comparison onto this [PatternMapperFn], replacing each value with `1` if less
 * than [threshold] or `0` otherwise.
 *
 * ```KlangScript
 * seq("5 10").apply(add(3).lt(9)).scale("c3:major").n()  // (5+3)<9=1, (10+3)<9=0
 * ```
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.lt(threshold: PatternLike): PatternMapperFn = _lt(listOf(threshold).asStrudelDslArgs())

// -- gt() (Greater Than) ----------------------------------------------------------------------------------------------

internal val _gt by dslPatternMapper { args, callInfo -> { p -> p._gt(args, callInfo) } }
internal val StrudelPattern._gt by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a gt b }
}
internal val String._gt by dslStringExtension { p, args, callInfo -> p._gt(args, callInfo) }
internal val PatternMapperFn._gt by dslPatternMapperExtension { m, args, callInfo -> m.chain(_gt(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Compares every value in the pattern to [threshold], replacing each with `1` (true) if greater
 * than [threshold] or `0` (false) otherwise.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Supports control patterns: pass a mini-notation string or another
 * [StrudelPattern] as [threshold] to modulate the threshold per cycle or event.
 *
 * ```KlangScript
 * seq("5 10").gt(8).scale("c3:major").n()  // 5>8 → 0, 10>8 → 1
 * ```
 *
 * ```KlangScript
 * seq("5 10").gt("<8 6>").scale("c3:major").n()  // threshold changes each cycle
 * ```
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags gt, greater than, comparison, arithmetic
 */
@StrudelDsl
fun StrudelPattern.gt(threshold: PatternLike): StrudelPattern = this._gt(listOf(threshold).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then compares every value to [threshold] using greater-than.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "5 10".gt(8).scale("c3:major").n()  // 5>8 → 0, 10>8 → 1
 * ```
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.gt(threshold: PatternLike): StrudelPattern = this._gt(listOf(threshold).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that compares every value in a pattern to [threshold], replacing
 * each with `1` (true) if greater than [threshold] or `0` (false) otherwise.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the comparison to an existing pattern.
 *
 * ```KlangScript
 * seq("5 10").apply(gt(8)).scale("c3:major").n()  // 5>8 → 0, 10>8 → 1
 * ```
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun gt(threshold: PatternLike): PatternMapperFn = _gt(listOf(threshold).asStrudelDslArgs())

/**
 * Chains a greater-than comparison onto this [PatternMapperFn], replacing each value with `1` if greater
 * than [threshold] or `0` otherwise.
 *
 * ```KlangScript
 * seq("5 10").apply(add(3).gt(9)).scale("c3:major").n()  // (5+3)>9=0, (10+3)>9=1
 * ```
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.gt(threshold: PatternLike): PatternMapperFn = _gt(listOf(threshold).asStrudelDslArgs())

// -- lte() (Less Than or Equal) ---------------------------------------------------------------------------------------

internal val _lte by dslPatternMapper { args, callInfo -> { p -> p._lte(args, callInfo) } }
internal val StrudelPattern._lte by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a lte b }
}
internal val String._lte by dslStringExtension { p, args, callInfo -> p._lte(args, callInfo) }
internal val PatternMapperFn._lte by dslPatternMapperExtension { m, args, callInfo -> m.chain(_lte(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Compares every value in the pattern to [threshold], replacing each with `1` (true) if less
 * than or equal to [threshold] or `0` (false) otherwise.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Supports control patterns: pass a mini-notation string or another
 * [StrudelPattern] as [threshold] to modulate the threshold per cycle or event.
 *
 * ```KlangScript
 * seq("5 8 10").lte(8).scale("c3:major").n()  // 5<=8 → 1, 8<=8 → 1, 10<=8 → 0
 * ```
 *
 * ```KlangScript
 * seq("5 8 10").lte("<8 6>").scale("c3:major").n()  // threshold changes each cycle
 * ```
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags lte, less than or equal, comparison, arithmetic
 */
@StrudelDsl
fun StrudelPattern.lte(threshold: PatternLike): StrudelPattern = this._lte(listOf(threshold).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then compares every value to [threshold]
 * using less-than-or-equal.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "5 8 10".lte(8).scale("c3:major").n()  // 5<=8 → 1, 8<=8 → 1, 10<=8 → 0
 * ```
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.lte(threshold: PatternLike): StrudelPattern = this._lte(listOf(threshold).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that compares every value in a pattern to [threshold], replacing
 * each with `1` (true) if less than or equal to [threshold] or `0` (false) otherwise.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the comparison to an existing pattern.
 *
 * ```KlangScript
 * seq("5 8 10").apply(lte(8)).scale("c3:major").n()  // 5<=8 → 1, 8<=8 → 1, 10<=8 → 0
 * ```
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun lte(threshold: PatternLike): PatternMapperFn = _lte(listOf(threshold).asStrudelDslArgs())

/**
 * Chains a less-than-or-equal comparison onto this [PatternMapperFn], replacing each value with `1` if
 * less than or equal to [threshold] or `0` otherwise.
 *
 * ```KlangScript
 * seq("5 10").apply(add(3).lte(11)).scale("c3:major").n()  // (5+3)<=11=1, (10+3)<=11=0
 * ```
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.lte(threshold: PatternLike): PatternMapperFn = _lte(listOf(threshold).asStrudelDslArgs())

// -- gte() (Greater Than or Equal) ------------------------------------------------------------------------------------

internal val _gte by dslPatternMapper { args, callInfo -> { p -> p._gte(args, callInfo) } }
internal val StrudelPattern._gte by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a gte b }
}
internal val String._gte by dslStringExtension { p, args, callInfo -> p._gte(args, callInfo) }
internal val PatternMapperFn._gte by dslPatternMapperExtension { m, args, callInfo -> m.chain(_gte(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Compares every value in the pattern to [threshold], replacing each with `1` (true) if greater
 * than or equal to [threshold] or `0` (false) otherwise.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Supports control patterns: pass a mini-notation string or another
 * [StrudelPattern] as [threshold] to modulate the threshold per cycle or event.
 *
 * ```KlangScript
 * seq("5 8 10").gte(8).scale("c3:major").n()  // 5>=8 → 0, 8>=8 → 1, 10>=8 → 1
 * ```
 *
 * ```KlangScript
 * seq("5 8 10").gte("<8 6>").scale("c3:major").n()  // threshold changes each cycle
 * ```
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags gte, greater than or equal, comparison, arithmetic
 */
@StrudelDsl
fun StrudelPattern.gte(threshold: PatternLike): StrudelPattern = this._gte(listOf(threshold).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then compares every value to [threshold]
 * using greater-than-or-equal.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "5 8 10".gte(8).scale("c3:major").n()  // 5>=8 → 0, 8>=8 → 1, 10>=8 → 1
 * ```
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.gte(threshold: PatternLike): StrudelPattern = this._gte(listOf(threshold).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that compares every value in a pattern to [threshold], replacing
 * each with `1` (true) if greater than or equal to [threshold] or `0` (false) otherwise.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the comparison to an existing pattern.
 *
 * ```KlangScript
 * seq("5 8 10").apply(gte(8)).scale("c3:major").n()  // 5>=8 → 0, 8>=8 → 1, 10>=8 → 1
 * ```
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun gte(threshold: PatternLike): PatternMapperFn = _gte(listOf(threshold).asStrudelDslArgs())

/**
 * Chains a greater-than-or-equal comparison onto this [PatternMapperFn], replacing each value with `1` if
 * greater than or equal to [threshold] or `0` otherwise.
 *
 * ```KlangScript
 * seq("5 10").apply(add(3).gte(11)).scale("c3:major").n()  // (5+3)>=11=0, (10+3)>=11=1
 * ```
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.gte(threshold: PatternLike): PatternMapperFn = _gte(listOf(threshold).asStrudelDslArgs())

// -- eq() (Equal) -----------------------------------------------------------------------------------------------------

internal val _eq by dslPatternMapper { args, callInfo -> { p -> p._eq(args, callInfo) } }
internal val StrudelPattern._eq by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a eq b }
}
internal val String._eq by dslStringExtension { p, args, callInfo -> p._eq(args, callInfo) }
internal val PatternMapperFn._eq by dslPatternMapperExtension { m, args, callInfo -> m.chain(_eq(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Compares every value in the pattern to [other] for strict equality, replacing each with
 * `1` (true) if equal or `0` (false) otherwise.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Supports control patterns: pass a mini-notation string or another
 * [StrudelPattern] as [other] to vary the comparison target per cycle or event.
 *
 * ```KlangScript
 * seq("5 8").eq(8).scale("c3:major").n()  // 5==8 → 0, 8==8 → 1
 * ```
 *
 * ```KlangScript
 * seq("0 1 2 3").eq("<0 1>").scale("c3:major").n()  // equality target alternates each cycle
 * ```
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags eq, equal, equality, comparison, arithmetic
 */
@StrudelDsl
fun StrudelPattern.eq(other: PatternLike): StrudelPattern = this._eq(listOf(other).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then tests every value for strict equality with [other].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "5 8".eq(8).scale("c3:major").n()  // 5==8 → 0, 8==8 → 1
 * ```
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.eq(other: PatternLike): StrudelPattern = this._eq(listOf(other).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that tests every value in a pattern for strict equality with [other],
 * replacing each with `1` (true) if equal or `0` (false) otherwise.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the comparison to an existing pattern.
 *
 * ```KlangScript
 * seq("5 8").apply(eq(8)).scale("c3:major").n()  // 5==8 → 0, 8==8 → 1
 * ```
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun eq(other: PatternLike): PatternMapperFn = _eq(listOf(other).asStrudelDslArgs())

/**
 * Chains a strict-equality test onto this [PatternMapperFn], replacing each value with `1` if equal
 * to [other] or `0` otherwise.
 *
 * ```KlangScript
 * seq("5 8").apply(add(3).eq(11)).scale("c3:major").n()  // (5+3)=8==11=0, (8+3)=11==11=1
 * ```
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.eq(other: PatternLike): PatternMapperFn = _eq(listOf(other).asStrudelDslArgs())

// -- eqt() (Truthiness Equal) -----------------------------------------------------------------------------------------

internal val _eqt by dslPatternMapper { args, callInfo -> { p -> p._eqt(args, callInfo) } }
internal val StrudelPattern._eqt by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a eqt b }
}
internal val String._eqt by dslStringExtension { p, args, callInfo -> p._eqt(args, callInfo) }
internal val PatternMapperFn._eqt by dslPatternMapperExtension { m, args, callInfo -> m.chain(_eqt(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Compares the truthiness of every value in the pattern to the truthiness of [other], replacing
 * each with `1` (true) if both share the same truthiness, or `0` (false) otherwise.
 *
 * A value is falsy if it is zero; otherwise it is truthy. Only the raw event `value` is
 * affected — `note`, `soundIndex`, and all other voice properties remain unchanged.
 *
 * ```KlangScript
 * seq("0 5").eqt(0).scale("c3:major").n()  // 0~=0 → 1 (both falsy), 5~=0 → 0
 * ```
 *
 * ```KlangScript
 * seq("0 5").eqt(3).scale("c3:major").n()  // 0~=3 → 0, 5~=3 → 1 (both truthy)
 * ```
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags eqt, truthiness, equal, comparison, arithmetic
 */
@StrudelDsl
fun StrudelPattern.eqt(other: PatternLike): StrudelPattern = this._eqt(listOf(other).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then tests every value for truthiness equality with [other].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. A value is falsy if it is zero; otherwise it is truthy.
 *
 * ```KlangScript
 * "0 5".eqt(0).scale("c3:major").n()  // 0~=0 → 1 (both falsy), 5~=0 → 0
 * ```
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.eqt(other: PatternLike): StrudelPattern = this._eqt(listOf(other).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that compares the truthiness of every value in a pattern to [other],
 * replacing each with `1` (true) if both share the same truthiness, or `0` (false) otherwise.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the comparison to an existing pattern.
 *
 * ```KlangScript
 * seq("0 5").apply(eqt(3)).scale("c3:major").n()  // 0~=3 → 0, 5~=3 → 1 (both truthy)
 * ```
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun eqt(other: PatternLike): PatternMapperFn = _eqt(listOf(other).asStrudelDslArgs())

/**
 * Chains a truthiness-equality test onto this [PatternMapperFn], replacing each value with `1` if it shares
 * the same truthiness as [other] or `0` otherwise.
 *
 * ```KlangScript
 * seq("0 5").apply(mul(3).eqt(0)).scale("c3:major").n()  // (0*3)=0~=0=1 (both falsy), (5*3)=15~=0=0
 * ```
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.eqt(other: PatternLike): PatternMapperFn = _eqt(listOf(other).asStrudelDslArgs())

// -- ne() (Not Equal) -------------------------------------------------------------------------------------------------

internal val _ne by dslPatternMapper { args, callInfo -> { p -> p._ne(args, callInfo) } }
internal val StrudelPattern._ne by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a ne b }
}
internal val String._ne by dslStringExtension { p, args, callInfo -> p._ne(args, callInfo) }
internal val PatternMapperFn._ne by dslPatternMapperExtension { m, args, callInfo -> m.chain(_ne(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Compares every value in the pattern to [other] for strict inequality, replacing each with
 * `1` (true) if not equal or `0` (false) otherwise.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Supports control patterns: pass a mini-notation string or another
 * [StrudelPattern] as [other] to vary the comparison target per cycle or event.
 *
 * ```KlangScript
 * seq("5 8").ne(8).scale("c3:major").n()  // 5!=8 → 1, 8!=8 → 0
 * ```
 *
 * ```KlangScript
 * seq("0 1 2 3").ne("<0 1>").scale("c3:major").n()  // target alternates each cycle
 * ```
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags ne, not equal, inequality, comparison, arithmetic
 */
@StrudelDsl
fun StrudelPattern.ne(other: PatternLike): StrudelPattern = this._ne(listOf(other).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then tests every value for strict inequality with [other].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "5 8".ne(8).scale("c3:major").n()  // 5!=8 → 1, 8!=8 → 0
 * ```
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.ne(other: PatternLike): StrudelPattern = this._ne(listOf(other).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that tests every value in a pattern for strict inequality with [other],
 * replacing each with `1` (true) if not equal or `0` (false) otherwise.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the comparison to an existing pattern.
 *
 * ```KlangScript
 * seq("5 8").apply(ne(8)).scale("c3:major").n()  // 5!=8 → 1, 8!=8 → 0
 * ```
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun ne(other: PatternLike): PatternMapperFn = _ne(listOf(other).asStrudelDslArgs())

/**
 * Chains a strict-inequality test onto this [PatternMapperFn], replacing each value with `1` if not equal
 * to [other] or `0` otherwise.
 *
 * ```KlangScript
 * seq("5 8").apply(add(3).ne(11)).scale("c3:major").n()  // (5+3)=8!=11=1, (8+3)=11!=11=0
 * ```
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.ne(other: PatternLike): PatternMapperFn = _ne(listOf(other).asStrudelDslArgs())

// -- net() (Truthiness Not Equal) -------------------------------------------------------------------------------------

internal val _net by dslPatternMapper { args, callInfo -> { p -> p._net(args, callInfo) } }
internal val StrudelPattern._net by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a net b }
}
internal val String._net by dslStringExtension { p, args, callInfo -> p._net(args, callInfo) }
internal val PatternMapperFn._net by dslPatternMapperExtension { m, args, callInfo -> m.chain(_net(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Compares the truthiness of every value in the pattern to the truthiness of [other], replacing
 * each with `1` (true) if their truthiness differs, or `0` (false) otherwise.
 *
 * A value is falsy if it is zero; otherwise it is truthy. Only the raw event `value` is
 * affected — `note`, `soundIndex`, and all other voice properties remain unchanged.
 *
 * ```KlangScript
 * seq("0 5").net(0).scale("c3:major").n()  // 0~!=0 → 0 (both falsy), 5~!=0 → 1
 * ```
 *
 * ```KlangScript
 * seq("0 5").net(3).scale("c3:major").n()  // 0~!=3 → 1, 5~!=3 → 0 (both truthy)
 * ```
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags net, truthiness, not equal, inequality, comparison, arithmetic
 */
@StrudelDsl
fun StrudelPattern.net(other: PatternLike): StrudelPattern = this._net(listOf(other).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then tests every value for truthiness inequality with [other].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. A value is falsy if it is zero; otherwise it is truthy.
 *
 * ```KlangScript
 * "0 5".net(0).scale("c3:major").n()  // 0~!=0 → 0 (both falsy), 5~!=0 → 1
 * ```
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.net(other: PatternLike): StrudelPattern = this._net(listOf(other).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that compares the truthiness of every value in a pattern to [other],
 * replacing each with `1` (true) if their truthiness differs, or `0` (false) otherwise.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the comparison to an existing pattern.
 *
 * ```KlangScript
 * seq("0 5").apply(net(0)).scale("c3:major").n()  // 0~!=0 → 0 (both falsy), 5~!=0 → 1
 * ```
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun net(other: PatternLike): PatternMapperFn = _net(listOf(other).asStrudelDslArgs())

/**
 * Chains a truthiness-inequality test onto this [PatternMapperFn], replacing each value with `1` if it has
 * different truthiness than [other] or `0` otherwise.
 *
 * ```KlangScript
 * seq("0 5").apply(mul(3).net(0)).scale("c3:major").n()  // (0*3)=0~!=0=0 (both falsy), (5*3)=15~!=0=1
 * ```
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.net(other: PatternLike): PatternMapperFn = _net(listOf(other).asStrudelDslArgs())

// -- and() (Logical AND) ----------------------------------------------------------------------------------------------

internal val _and by dslPatternMapper { args, callInfo -> { p -> p._and(args, callInfo) } }
internal val StrudelPattern._and by dslPatternExtension { source, args, _ ->
    applyArithmetic(source, args) { a, b -> a and b }
}
internal val String._and by dslStringExtension { p, args, callInfo -> p._and(args, callInfo) }
internal val PatternMapperFn._and by dslPatternMapperExtension { m, args, callInfo -> m.chain(_and(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies logical AND between every value in the pattern and [other].
 *
 * Returns [other] when the source value is truthy (non-zero), or `0` when it is falsy (zero).
 * This mirrors JavaScript's `&&` short-circuit behaviour. Only the raw event `value` is
 * affected — `note`, `soundIndex`, and all other voice properties remain unchanged.
 *
 * ```KlangScript
 * seq("0 5").and(10).scale("c3:major").n()  // 0&&10 → 0, 5&&10 → 10
 * ```
 *
 * ```KlangScript
 * seq("5").and("<0 10>").scale("c3:major").n()  // gate on/off each cycle
 * ```
 *
 * @param other The right-hand operand. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern where each value is `value && other`.
 * @category arithmetic
 * @tags and, logical, boolean, arithmetic
 */
@StrudelDsl
fun StrudelPattern.and(other: PatternLike): StrudelPattern = this._and(listOf(other).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then applies logical AND with [other] to every value.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "0 5".and(10).scale("c3:major").n()  // 0&&10 → 0, 5&&10 → 10
 * ```
 *
 * @param other The right-hand operand. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.and(other: PatternLike): StrudelPattern = this._and(listOf(other).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that applies logical AND between every value in a pattern and [other].
 *
 * Returns [other] when the source value is truthy (non-zero), or `0` when it is falsy (zero).
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the gate to an existing pattern.
 *
 * ```KlangScript
 * seq("0 5").apply(and(10)).scale("c3:major").n()  // 0&&10 → 0, 5&&10 → 10
 * ```
 *
 * @param other The right-hand operand. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun and(other: PatternLike): PatternMapperFn = _and(listOf(other).asStrudelDslArgs())

/**
 * Chains a logical AND onto this [PatternMapperFn]. Returns [other] when the value is truthy, or `0` when falsy.
 *
 * ```KlangScript
 * seq("1 5").apply(sub(1).and(7)).scale("c3:major").n()  // (1-1)=0&&7=0, (5-1)=4&&7=7
 * ```
 *
 * @param other The right-hand operand. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.and(other: PatternLike): PatternMapperFn = _and(listOf(other).asStrudelDslArgs())

// -- or() (Logical OR) ------------------------------------------------------------------------------------------------

internal val _or by dslPatternMapper { args, callInfo -> { p -> p._or(args, callInfo) } }
internal val StrudelPattern._or by dslPatternExtension { p, args, _ ->
    applyArithmetic(p, args) { a, b -> a or b }
}
internal val String._or by dslStringExtension { p, args, callInfo -> p._or(args, callInfo) }
internal val PatternMapperFn._or by dslPatternMapperExtension { m, args, callInfo -> m.chain(_or(args, callInfo)) }

// ===== USER-FACING OVERLOADS =====

/**
 * Applies logical OR between every value in the pattern and [other].
 *
 * Returns the source value when it is truthy (non-zero), or [other] when it is falsy (zero).
 * This mirrors JavaScript's `||` short-circuit behaviour. Only the raw event `value` is
 * affected — `note`, `soundIndex`, and all other voice properties remain unchanged.
 *
 * ```KlangScript
 * seq("0 5").or(10).scale("c3:major").n()  // 0||10 → 10, 5||10 → 5
 * ```
 *
 * ```KlangScript
 * seq("0 5").or("<1 2>").scale("c3:major").n()  // fallback alternates each cycle
 * ```
 *
 * @param other The right-hand operand. May be a number, string mini-notation, or a [StrudelPattern].
 * @return A new pattern where each value is `value || other`.
 * @category arithmetic
 * @tags or, logical, boolean, arithmetic
 */
@StrudelDsl
fun StrudelPattern.or(other: PatternLike): StrudelPattern = this._or(listOf(other).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then applies logical OR with [other] to every value.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "0 5".or(10).scale("c3:major").n()  // 0||10 → 10, 5||10 → 5
 * ```
 *
 * @param other The right-hand operand. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun String.or(other: PatternLike): StrudelPattern = this._or(listOf(other).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that applies logical OR between every value in a pattern and [other].
 *
 * Returns the source value when it is truthy (non-zero), or [other] when it is falsy (zero).
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply the fallback to an existing pattern.
 *
 * ```KlangScript
 * seq("0 5").apply(or(10)).scale("c3:major").n()  // 0||10 → 10, 5||10 → 5
 * ```
 *
 * @param other The right-hand operand. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun or(other: PatternLike): PatternMapperFn = _or(listOf(other).asStrudelDslArgs())

/**
 * Chains a logical OR onto this [PatternMapperFn]. Returns the source value when truthy, or [other] when falsy.
 *
 * ```KlangScript
 * seq("0 5").apply(mul(1).or(7)).scale("c3:major").n()  // (0*1)=0||7=7, (5*1)=5||7=5
 * ```
 *
 * @param other The right-hand operand. May be a number, string mini-notation, or a [StrudelPattern].
 */
@StrudelDsl
fun PatternMapperFn.or(other: PatternLike): PatternMapperFn = _or(listOf(other).asStrudelDslArgs())

// -- round() ----------------------------------------------------------------------------------------------------------

internal val _round by dslPatternMapper { _, callInfo -> { p -> p._round(emptyList(), callInfo) } }
internal val StrudelPattern._round by dslPatternExtension { p, _, _ ->
    applyUnaryOp(p) { v -> v.asRational?.round()?.asVoiceValue() ?: v }
}
internal val String._round by dslStringExtension { p, _, _ -> p._round() }
internal val PatternMapperFn._round by dslPatternMapperExtension { m, _, _ -> m.chain(_round(emptyList())) }

// ===== USER-FACING OVERLOADS =====

/**
 * Rounds every numeric value in the pattern to the nearest integer.
 *
 * Halfway values (e.g. 2.5) round up. Non-numeric values are passed through unchanged.
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "2.4 2.5 2.6".round().scale("c3:major").n()  // 2, 3, 3
 * ```
 *
 * ```KlangScript
 * "0.1 0.9".round().scale("c3:major").n()  // 0, 1
 * ```
 *
 * @return A new pattern with each value rounded to the nearest integer.
 * @category arithmetic
 * @tags round, rounding, arithmetic, math
 */
@StrudelDsl
fun StrudelPattern.round(): StrudelPattern = this._round()

/**
 * Parses this string as a pattern, then rounds every numeric value to the nearest integer.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "2.4 2.5 2.6".round().scale("c3:major").n()  // 2, 3, 3
 * ```
 */
@StrudelDsl
fun String.round(): StrudelPattern = this._round()

/**
 * Creates a [PatternMapperFn] that rounds every numeric value in a pattern to the nearest integer.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply rounding to an existing pattern.
 *
 * ```KlangScript
 * seq("2.4 2.5 2.6").apply(round()).scale("c3:major").n()  // 2, 3, 3
 * ```
 */
@StrudelDsl
fun round(): PatternMapperFn = _round(emptyList())

/**
 * Chains a rounding operation onto this [PatternMapperFn], rounding every numeric value to the nearest integer.
 *
 * ```KlangScript
 * seq("2.1 3.7").apply(mul(2).round()).scale("c3:major").n()  // round(2.1*2)=round(4.2)=4, round(3.7*2)=round(7.4)=7
 * ```
 */
@StrudelDsl
fun PatternMapperFn.round(): PatternMapperFn = _round(emptyList())

// -- floor() ----------------------------------------------------------------------------------------------------------

internal val _floor by dslPatternMapper { _, callInfo -> { p -> p._floor(emptyList(), callInfo) } }
internal val StrudelPattern._floor by dslPatternExtension { p, _, _ ->
    applyUnaryOp(p) { v -> v.asRational?.floor()?.asVoiceValue() ?: v }
}
internal val String._floor by dslStringExtension { p, _, _ -> p._floor() }
internal val PatternMapperFn._floor by dslPatternMapperExtension { m, _, _ -> m.chain(_floor(emptyList())) }

// ===== USER-FACING OVERLOADS =====

/**
 * Floors every numeric value in the pattern to the largest integer less than or equal to the value.
 *
 * For negative numbers this rounds away from zero: `floor(-2.1) = -3`.
 * Non-numeric values are passed through unchanged. Only the raw event `value` is affected —
 * `note`, `soundIndex`, and all other voice properties remain unchanged.
 *
 * ```KlangScript
 * "2.1 2.9".floor().scale("c3:major").n()  // 2, 2
 * ```
 *
 * ```KlangScript
 * "-2.1 -2.9".floor().scale("c3:major").n()  // -3, -3
 * ```
 *
 * @return A new pattern with each value floored to an integer.
 * @category arithmetic
 * @tags floor, rounding, arithmetic, math
 */
@StrudelDsl
fun StrudelPattern.floor(): StrudelPattern = this._floor()

/**
 * Parses this string as a pattern, then floors every numeric value to an integer.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "2.1 2.9".floor().scale("c3:major").n()  // 2, 2
 * ```
 */
@StrudelDsl
fun String.floor(): StrudelPattern = this._floor()

/**
 * Creates a [PatternMapperFn] that floors every numeric value in a pattern to an integer.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply flooring to an existing pattern.
 *
 * ```KlangScript
 * seq("2.1 2.9").apply(floor()).scale("c3:major").n()  // 2, 2
 * ```
 */
@StrudelDsl
fun floor(): PatternMapperFn = _floor(emptyList())

/**
 * Chains a floor operation onto this [PatternMapperFn], flooring every numeric value to an integer.
 *
 * ```KlangScript
 * seq("2.1 3.9").apply(mul(2).floor()).scale("c3:major").n()  // floor(2.1*2)=floor(4.2)=4, floor(3.9*2)=floor(7.8)=7
 * ```
 */
@StrudelDsl
fun PatternMapperFn.floor(): PatternMapperFn = _floor(emptyList())

// -- ceil() -----------------------------------------------------------------------------------------------------------

internal val _ceil by dslPatternMapper { _, callInfo -> { p -> p._ceil(emptyList(), callInfo) } }
internal val StrudelPattern._ceil by dslPatternExtension { p, _, _ ->
    applyUnaryOp(p) { v -> v.asRational?.ceil()?.asVoiceValue() ?: v }
}
internal val String._ceil by dslStringExtension { p, _, _ -> p._ceil() }
internal val PatternMapperFn._ceil by dslPatternMapperExtension { m, _, _ -> m.chain(_ceil(emptyList())) }

// ===== USER-FACING OVERLOADS =====

/**
 * Ceils every numeric value in the pattern to the smallest integer greater than or equal to the value.
 *
 * For negative numbers this rounds toward zero: `ceil(-2.9) = -2`.
 * Non-numeric values are passed through unchanged. Only the raw event `value` is affected —
 * `note`, `soundIndex`, and all other voice properties remain unchanged.
 *
 * ```KlangScript
 * "2.1 2.9".ceil().scale("c3:major").n()  // 3, 3
 * ```
 *
 * ```KlangScript
 * "-2.9 -2.1".ceil().scale("c3:major").n()  // -2, -2
 * ```
 *
 * @return A new pattern with each value ceiled to an integer.
 * @category arithmetic
 * @tags ceil, ceiling, rounding, arithmetic, math
 */
@StrudelDsl
fun StrudelPattern.ceil(): StrudelPattern = this._ceil()

/**
 * Parses this string as a pattern, then ceils every numeric value to an integer.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript
 * "2.1 2.9".ceil().scale("c3:major").n()  // 3, 3
 * ```
 */
@StrudelDsl
fun String.ceil(): StrudelPattern = this._ceil()

/**
 * Creates a [PatternMapperFn] that ceils every numeric value in a pattern to an integer.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [StrudelPattern.apply] to apply ceiling to an existing pattern.
 *
 * ```KlangScript
 * seq("2.1 2.9").apply(ceil()).scale("c3:major").n()  // 3, 3
 * ```
 */
@StrudelDsl
fun ceil(): PatternMapperFn = _ceil(emptyList())

/**
 * Chains a ceiling operation onto this [PatternMapperFn], ceiling every numeric value to an integer.
 *
 * ```KlangScript
 * seq("2.1 3.9").apply(mul(2).ceil()).scale("c3:major").n()  // ceil(2.1*2)=ceil(4.2)=5, ceil(3.9*2)=ceil(7.8)=8
 * ```
 */
@StrudelDsl
fun PatternMapperFn.ceil(): PatternMapperFn = _ceil(emptyList())
