@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceValue
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel._innerJoin
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.mapEvents
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangArithmeticInit = false

// Helper for arithmetic operations that modify the 'value' field
internal fun applyArithmetic(
    source: SprudelPattern,
    args: List<SprudelDslArg<Any?>>,
    op: (SprudelVoiceValue, SprudelVoiceValue) -> SprudelVoiceValue?,
): SprudelPattern {
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
internal fun applyUnaryOp(
    source: SprudelPattern,
    op: (SprudelVoiceValue) -> SprudelVoiceValue?,
): SprudelPattern {
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

/**
 * Adds [amount] to every numeric value in the pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Supports control patterns: pass a mini-notation string or another
 * [SprudelPattern] as [amount] to modulate the offset per cycle or event.
 *
 * ```KlangScript(Playable)
 * seq("0 2").add(5).scale("c3:major").n()  // n values become 5 and 7
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0 2").add("<0 12>").scale("c3:major").n()  // add 0 or 12 alternately each cycle
 * ```
 *
 * @param amount The value to add. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern with each numeric value increased by [amount].
 * @category arithmetic
 * @tags add, arithmetic, math, offset
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.add(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(amount).asSprudelDslArgs(callInfo)) { a, b -> a + b }

/**
 * Parses this string as a pattern, then adds [amount] to every numeric value.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript(Playable)
 * "0 2".add(5).scale("c3:major").n()  // n values become 5 and 7
 * ```
 *
 * @param amount The value to add. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun String.add(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().add(amount, callInfo)

/**
 * Creates a [PatternMapperFn] that adds [amount] to every numeric value in a pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [SprudelPattern.apply] to apply the addition to an existing pattern.
 *
 * ```KlangScript(Playable)
 * seq("0 2").apply(add(5)).scale("c3:major").n()  // n values become 5 and 7
 * ```
 *
 * @param amount The value to add. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun add(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.add(amount, callInfo) }

/**
 * Chains a PatternMapperFn to this pattern, adding [amount] to every numeric value in the result.
 *
 * ```KlangScript(Playable)
 * seq("10 20").apply(mul(2).add(3)).scale("c1:major").n()  // (10*2)+3=23, (20*2)+3=43
 * ```
 *
 * @param amount The value to subtract. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.add(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.add(amount, callInfo) }

// -- sub() ------------------------------------------------------------------------------------------------------------

/**
 * Subtracts [amount] from every numeric value in the pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Supports control patterns: pass a mini-notation string or another
 * [SprudelPattern] as [amount] to modulate the offset per cycle or event.
 *
 * ```KlangScript(Playable)
 * seq("10 20").sub(5).scale("c3:major").n()  // n values become 5 and 15
 * ```
 *
 * ```KlangScript(Playable)
 * seq("10").sub("<0 5>").scale("c3:major").n()  // subtract 0 or 5 alternately each cycle
 * ```
 *
 * @param amount The value to subtract. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern with each numeric value decreased by [amount].
 * @category arithmetic
 * @tags sub, subtract, arithmetic, math, offset
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.sub(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(amount).asSprudelDslArgs(callInfo)) { a, b -> a - b }

/**
 * Parses this string as a pattern, then subtracts [amount] from every numeric value.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript(Playable)
 * "10 20".sub(5).scale("c3:major").n()  // n values become 5 and 15
 * ```
 *
 * @param amount The value to subtract. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun String.sub(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().sub(amount, callInfo)

/**
 * Creates a [PatternMapperFn] that subtracts [amount] from every numeric value in a pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [SprudelPattern.apply] to apply the subtraction to an existing pattern.
 *
 * ```KlangScript(Playable)
 * seq("10 20").apply(sub(5)).scale("c3:major").n()  // n values become 5 and 15
 * ```
 *
 * @param amount The value to subtract. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun sub(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sub(amount, callInfo) }

/**
 * Chains a subtraction onto this [PatternMapperFn], subtracting [amount] from every numeric value in the result.
 *
 * ```KlangScript(Playable)
 * seq("10 20").apply(mul(2).sub(3)).scale("c1:major").n()  // (10*2)-3=17, (20*2)-3=37
 * ```
 *
 * @param amount The value to subtract. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sub(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sub(amount, callInfo) }

// -- mul() ------------------------------------------------------------------------------------------------------------

/**
 * Multiplies every numeric value in the pattern by [factor].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Supports control patterns: pass a mini-notation string or another
 * [SprudelPattern] as [factor] to modulate the scale per cycle or event.
 *
 * ```KlangScript(Playable)
 * seq("2 3").mul(4).scale("c3:major").n()  // values become 8 and 12
 * ```
 *
 * ```KlangScript(Playable)
 * seq("1 2").mul("<1 2>").scale("c3:major").n()  // double every other cycle
 * ```
 *
 * @param factor The multiplier. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern with each numeric value multiplied by [factor].
 * @category arithmetic
 * @tags mul, multiply, arithmetic, math, scale
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.mul(factor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(factor).asSprudelDslArgs(callInfo)) { a, b -> a * b }

/**
 * Parses this string as a pattern, then multiplies every numeric value by [factor].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript(Playable)
 * "2 3".mul(4).scale("c3:major").n()  // values become 8 and 12
 * ```
 *
 * @param factor The multiplier. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun String.mul(factor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().mul(factor, callInfo)

/**
 * Creates a [PatternMapperFn] that multiplies every numeric value in a pattern by [factor].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [SprudelPattern.apply] to apply the multiplication to an existing pattern.
 *
 * ```KlangScript(Playable)
 * seq("2 3").apply(mul(4)).scale("c3:major").n()  // values become 8 and 12
 * ```
 *
 * @param factor The multiplier. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun mul(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.mul(factor, callInfo) }

/**
 * Chains a multiplication onto this [PatternMapperFn], multiplying every numeric value by [factor].
 *
 * ```KlangScript(Playable)
 * seq("1 2").apply(add(1).mul(3)).scale("c2:major").n()  // (1+1)*3=6, (2+1)*3=9
 * ```
 *
 * @param factor The multiplier. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.mul(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.mul(factor, callInfo) }

// -- div() ------------------------------------------------------------------------------------------------------------

/**
 * Divides every numeric value in the pattern by [divisor].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Supports control patterns: pass a mini-notation string or another
 * [SprudelPattern] as [divisor] to modulate the division per cycle or event.
 *
 * ```KlangScript(Playable)
 * seq("10 20").div(2).scale("c3:major").n()  // values become 5 and 10
 * ```
 *
 * ```KlangScript(Playable)
 * seq("10 20").div("<1 2>").scale("c3:major").n()  // halve every other cycle
 * ```
 *
 * @param divisor The divisor. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern with each numeric value divided by [divisor].
 * @category arithmetic
 * @tags div, divide, arithmetic, math, scale
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.div(divisor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(divisor).asSprudelDslArgs(callInfo)) { a, b -> a / b }

/**
 * Parses this string as a pattern, then divides every numeric value by [divisor].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript(Playable)
 * "10 20".div(2).scale("c3:major").n()  // values become 5 and 10
 * ```
 *
 * @param divisor The divisor. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun String.div(divisor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().div(divisor, callInfo)

/**
 * Creates a [PatternMapperFn] that divides every numeric value in a pattern by [divisor].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [SprudelPattern.apply] to apply the division to an existing pattern.
 *
 * ```KlangScript(Playable)
 * seq("10 20").apply(div(2)).scale("c3:major").n()  // values become 5 and 10
 * ```
 *
 * @param divisor The divisor. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun div(divisor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.div(divisor, callInfo) }

/**
 * Chains a division onto this [PatternMapperFn], dividing every numeric value by [divisor].
 *
 * ```KlangScript(Playable)
 * seq("10 20").apply(mul(2).div(4)).scale("c2:major").n()  // (10*2)/4=5, (20*2)/4=10
 * ```
 *
 * @param divisor The divisor. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.div(divisor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.div(divisor, callInfo) }

// -- mod() ------------------------------------------------------------------------------------------------------------

/**
 * Applies modulo [divisor] to every numeric value in the pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Useful for wrapping note indices, step counters, or any cyclic numeric range.
 * Division by zero is safe — events with a zero divisor are silenced. Supports control patterns:
 * pass a mini-notation string or another [SprudelPattern] as [divisor].
 *
 * ```KlangScript(Playable)
 * seq("10 11").mod(3).scale("c3:major").n()  // values become 1 and 2
 * ```
 *
 * ```KlangScript(Playable)
 * seq("0 1 2 3 4 5 6 7").mod(4).scale("c3:major").n()  // wraps at 4: 0 1 2 3 0 1 2 3
 * ```
 *
 * @param divisor The modulus. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern where each value is replaced by `value % divisor`.
 * @category arithmetic
 * @tags mod, modulo, arithmetic, math, wrap
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.mod(divisor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(divisor).asSprudelDslArgs(callInfo)) { a, b -> a % b }

/**
 * Parses this string as a pattern, then applies modulo [divisor] to every numeric value.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Division by zero is safe — events with a zero divisor are silenced.
 *
 * ```KlangScript(Playable)
 * "10 11".mod(3).scale("c3:major").n()  // values become 1 and 2
 * ```
 *
 * @param divisor The modulus. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun String.mod(divisor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().mod(divisor, callInfo)

/**
 * Creates a [PatternMapperFn] that applies modulo [divisor] to every numeric value in a pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Division by zero is safe — events with a zero divisor are silenced.
 * Use with [SprudelPattern.apply] to apply the modulo to an existing pattern.
 *
 * ```KlangScript(Playable)
 * seq("0 1 2 3 4 5 6 7").apply(mod(4)).scale("c3:major").n()  // wraps at 4: 0 1 2 3 0 1 2 3
 * ```
 *
 * @param divisor The modulus. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun mod(divisor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.mod(divisor, callInfo) }

/**
 * Chains a modulo operation onto this [PatternMapperFn], applying modulo [divisor] to every numeric value.
 *
 * ```KlangScript(Playable)
 * seq("10 11").apply(add(1).mod(4)).scale("c3:major").n()  // (10+1)%4=3, (11+1)%4=0
 * ```
 *
 * @param divisor The modulus. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.mod(divisor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.mod(divisor, callInfo) }

// -- pow() ------------------------------------------------------------------------------------------------------------

/**
 * Raises every numeric value in the pattern to the power of [exponent].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Supports control patterns: pass a mini-notation string or another
 * [SprudelPattern] as [exponent] to modulate the exponent per cycle or event.
 *
 * ```KlangScript(Playable)
 * seq("2 3").pow(3).scale("c3:major").n()  // values become 8 (2³) and 27 (3³)
 * ```
 *
 * ```KlangScript(Playable)
 * seq("2").pow("<1 2 3>").scale("c3:major").n()  // 2, 4, 8 over three cycles
 * ```
 *
 * @param exponent The exponent. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern where each value is replaced by `value ^ exponent`.
 * @category arithmetic
 * @tags pow, power, exponent, arithmetic, math
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.pow(exponent: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(exponent).asSprudelDslArgs(callInfo)) { a, b -> a pow b }

/**
 * Parses this string as a pattern, then raises every numeric value to the power of [exponent].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript(Playable)
 * "2 3".pow(3).scale("c3:major").n()  // values become 8 (2³) and 27 (3³)
 * ```
 *
 * @param exponent The exponent. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun String.pow(exponent: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().pow(exponent, callInfo)

/**
 * Creates a [PatternMapperFn] that raises every numeric value in a pattern to the power of [exponent].
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [SprudelPattern.apply] to apply the exponentiation to an existing pattern.
 *
 * ```KlangScript(Playable)
 * seq("2 3").apply(pow(3)).scale("c3:major").n()  // values become 8 (2³) and 27 (3³)
 * ```
 *
 * @param exponent The exponent. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun pow(exponent: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pow(exponent, callInfo) }

/**
 * Chains an exponentiation onto this [PatternMapperFn], raising every numeric value to [exponent].
 *
 * ```KlangScript(Playable)
 * seq("2 3").apply(add(1).pow(2)).scale("c2:major").n()  // (2+1)^2=9, (3+1)^2=16
 * ```
 *
 * @param exponent The exponent. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.pow(exponent: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pow(exponent, callInfo) }

// -- band() (Bitwise AND) ---------------------------------------------------------------------------------------------

/**
 * Applies bitwise AND of [mask] to every integer value in the pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Values are truncated to integers before the operation.
 *
 * ```KlangScript(Playable)
 * "12 15".band(10).scale("c3:major").n()  // 12&10=8, 15&10=10
 * ```
 *
 * ```KlangScript(Playable)
 * "127".band("<15 63>").scale("c3:major").n()  // mask low or high nibble alternately
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern where each value is replaced by `value & mask`.
 * @category arithmetic
 * @tags band, bitwise, and, arithmetic, binary
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.band(mask: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(mask).asSprudelDslArgs(callInfo)) { a, b -> a band b }

/**
 * Parses this string as a pattern, then applies bitwise AND with [mask] to every integer value.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript(Playable)
 * "12 15".band(10).scale("c3:major").n()  // 12&10=8, 15&10=10
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun String.band(mask: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().band(mask, callInfo)

/**
 * Creates a [PatternMapperFn] that applies bitwise AND of [mask] to every integer value in a pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [SprudelPattern.apply] to apply the mask to an existing pattern.
 *
 * ```KlangScript(Playable)
 * seq("12 15").apply(band(10)).scale("c3:major").n()  // 12&10=8, 15&10=10
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun band(mask: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.band(mask, callInfo) }

/**
 * Chains a bitwise AND onto this [PatternMapperFn], applying [mask] to every integer value.
 *
 * ```KlangScript(Playable)
 * seq("12 15").apply(add(3).band(10)).scale("c2:major").n()  // (12+3)&10=10, (15+3)&10=2
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.band(mask: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.band(mask, callInfo) }

// -- bor() (Bitwise OR) -----------------------------------------------------------------------------------------------

/**
 * Applies bitwise OR of [mask] to every integer value in the pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Values are truncated to integers before the operation.
 *
 * ```KlangScript(Playable)
 * "8 4".bor(2).scale("c3:major").n()  // 8|2=10, 4|2=6
 * ```
 *
 * ```KlangScript(Playable)
 * "0".bor("<1 2 4 8>").scale("c3:major").n()  // set individual bits each cycle
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern where each value is replaced by `value | mask`.
 * @category arithmetic
 * @tags bor, bitwise, or, arithmetic, binary
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.bor(mask: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(mask).asSprudelDslArgs(callInfo)) { a, b -> a bor b }

/**
 * Parses this string as a pattern, then applies bitwise OR with [mask] to every integer value.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript(Playable)
 * "8 4".bor(2).scale("c3:major").n()  // 8|2=10, 4|2=6
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun String.bor(mask: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().bor(mask, callInfo)

/**
 * Creates a [PatternMapperFn] that applies bitwise OR of [mask] to every integer value in a pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [SprudelPattern.apply] to apply the mask to an existing pattern.
 *
 * ```KlangScript(Playable)
 * seq("8 4").apply(bor(2)).scale("c3:major").n()  // 8|2=10, 4|2=6
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun bor(mask: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bor(mask, callInfo) }

/**
 * Chains a bitwise OR onto this [PatternMapperFn], applying [mask] to every integer value.
 *
 * ```KlangScript(Playable)
 * seq("8 4").apply(add(1).bor(2)).scale("c2:major").n()  // (8+1)|2=11, (4+1)|2=7
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bor(mask: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bor(mask, callInfo) }

// -- bxor() (Bitwise XOR) ---------------------------------------------------------------------------------------------

/**
 * Applies bitwise XOR of [mask] to every integer value in the pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Values are truncated to integers before the operation. XOR is useful for
 * toggling specific bits.
 *
 * ```KlangScript(Playable)
 * "12 10".bxor(6).scale("c3:major").n()  // 12^6=10, 10^6=12
 * ```
 *
 * ```KlangScript(Playable)
 * "5".bxor("<3 5>").scale("c3:major").n()  // toggle bits each cycle
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern where each value is replaced by `value ^ mask`.
 * @category arithmetic
 * @tags bxor, bitwise, xor, arithmetic, binary
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.bxor(mask: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(mask).asSprudelDslArgs(callInfo)) { a, b -> a bxor b }

/**
 * Parses this string as a pattern, then applies bitwise XOR with [mask] to every integer value.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript(Playable)
 * "12 10".bxor(6).scale("c3:major").n()  // 12^6=10, 10^6=12
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun String.bxor(mask: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().bxor(mask, callInfo)

/**
 * Creates a [PatternMapperFn] that applies bitwise XOR of [mask] to every integer value in a pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [SprudelPattern.apply] to apply the mask to an existing pattern.
 *
 * ```KlangScript(Playable)
 * seq("12 10").apply(bxor(6)).scale("c3:major").n()  // 12^6=10, 10^6=12
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun bxor(mask: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bxor(mask, callInfo) }

/**
 * Chains a bitwise XOR onto this [PatternMapperFn], applying [mask] to every integer value.
 *
 * ```KlangScript(Playable)
 * seq("12 10").apply(add(2).bxor(6)).scale("c2:major").n()  // (12+2)^6=8, (10+2)^6=10
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bxor(mask: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bxor(mask, callInfo) }

// -- blshift() (Bitwise Left Shift) -----------------------------------------------------------------------------------

/**
 * Shifts every integer value in the pattern left by [bits] bits (equivalent to multiplying by 2^n).
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Values are truncated to integers before the operation.
 *
 * ```KlangScript(Playable)
 * "1 2".blshift(2).scale("c3:major").n()  // 1<<2=4, 2<<2=8
 * ```
 *
 * ```KlangScript(Playable)
 * "1".blshift("<0 1 2 3>").scale("c3:major").n()  // 1, 2, 4, 8 over four cycles
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern where each value is replaced by `value << bits`.
 * @category arithmetic
 * @tags blshift, bitwise, shift, left shift, arithmetic, binary
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.blshift(bits: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(bits).asSprudelDslArgs(callInfo)) { a, b -> a shl b }

/**
 * Parses this string as a pattern, then shifts every integer value left by [bits] bits.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript(Playable)
 * "1 2".blshift(2).scale("c3:major").n()  // 1<<2=4, 2<<2=8
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun String.blshift(bits: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().blshift(bits, callInfo)

/**
 * Creates a [PatternMapperFn] that shifts every integer value in a pattern left by [bits] bits.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [SprudelPattern.apply] to apply the shift to an existing pattern.
 *
 * ```KlangScript(Playable)
 * seq("1 2").apply(blshift(2)).scale("c3:major").n()  // 1<<2=4, 2<<2=8
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation,
 *   or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun blshift(bits: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.blshift(bits, callInfo) }

/**
 * Chains a bitwise left-shift onto this [PatternMapperFn], shifting every integer value left by [bits] bits.
 *
 * ```KlangScript(Playable)
 * seq("1 2").apply(add(1).blshift(2)).scale("c2:major").n()  // (1+1)<<2=8, (2+1)<<2=12
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.blshift(bits: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.blshift(bits, callInfo) }

// -- brshift() (Bitwise Right Shift) ----------------------------------------------------------------------------------

/**
 * Shifts every integer value in the pattern right by [bits] bits (equivalent to integer-dividing by 2^n).
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Values are truncated to integers before the operation.
 *
 * ```KlangScript(Playable)
 * "8 12".brshift(2).scale("c3:major").n()  // 8>>2=2, 12>>2=3
 * ```
 *
 * ```KlangScript(Playable)
 * "16".brshift("<0 1 2 3>").scale("c3:major").n()  // 16, 8, 4, 2 over four cycles
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern where each value is replaced by `value >> bits`.
 * @category arithmetic
 * @tags brshift, bitwise, shift, right shift, arithmetic, binary
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.brshift(bits: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(bits).asSprudelDslArgs(callInfo)) { a, b -> a shr b }

/**
 * Parses this string as a pattern, then shifts every integer value right by [bits] bits.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript(Playable)
 * "8 12".brshift(2).scale("c3:major").n()  // 8>>2=2, 12>>2=3
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun String.brshift(bits: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().brshift(bits, callInfo)

/**
 * Creates a [PatternMapperFn] that shifts every integer value in a pattern right by [bits] bits.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [SprudelPattern.apply] to apply the shift to an existing pattern.
 *
 * ```KlangScript(Playable)
 * seq("8 12").apply(brshift(2)).scale("c3:major").n()  // 8>>2=2, 12>>2=3
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun brshift(bits: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.brshift(bits, callInfo) }

/**
 * Chains a bitwise right-shift onto this [PatternMapperFn], shifting every integer value right by [bits] bits.
 *
 * ```KlangScript(Playable)
 * seq("8 16").apply(mul(2).brshift(3)).scale("c3:major").n()  // (8*2)>>3=2, (16*2)>>3=4
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [SprudelPattern].
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.brshift(bits: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.brshift(bits, callInfo) }

// -- log2() -----------------------------------------------------------------------------------------------------------

/**
 * Applies log base 2 to every numeric value in the pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Useful for converting exponential frequency ratios to linear semitone or
 * octave values.
 *
 * ```KlangScript(Playable)
 * "8 16".log2().scale("c3:major").n()  // log2(8)=3, log2(16)=4
 * ```
 *
 * ```KlangScript(Playable)
 * "1 2 4 8".log2().scale("c3:major").n()  // 0, 1, 2, 3
 * ```
 *
 * @return A new pattern where each value is replaced by `log2(value)`.
 * @category arithmetic
 * @tags log2, logarithm, arithmetic, math
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.log2(callInfo: CallInfo? = null): SprudelPattern =
    applyUnaryOp(this) { it.log2() }

/**
 * Parses this string as a pattern, then applies log base 2 to every numeric value.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript(Playable)
 * "1 2 4 8".log2().scale("c3:major").n()  // 0, 1, 2, 3
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.log2(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().log2(callInfo)

/**
 * Creates a [PatternMapperFn] that applies log base 2 to every numeric value in a pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [SprudelPattern.apply] to apply the transform to an existing pattern.
 *
 * ```KlangScript(Playable)
 * seq("1 2 4 8").apply(log2()).scale("c3:major").n()  // 0, 1, 2, 3
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun log2(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.log2(callInfo) }

/**
 * Chains a log2 operation onto this [PatternMapperFn], applying log base 2 to every numeric value.
 *
 * ```KlangScript(Playable)
 * seq("2 4").apply(mul(4).log2()).scale("c3:major").n()  // log2(2*4)=log2(8)=3, log2(4*4)=log2(16)=4
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.log2(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.log2(callInfo) }

// -- lt() (Less Than) -------------------------------------------------------------------------------------------------

/**
 * Compares every value in the pattern to [threshold], replacing each with `1` (true) if less
 * than [threshold] or `0` (false) otherwise.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Supports control patterns: pass a mini-notation string or another
 * [SprudelPattern] as [threshold] to modulate the threshold per cycle or event.
 *
 * ```KlangScript(Playable)
 * seq("5 10").lt(8).scale("c3:major").n()  // 5<8 -> 1, 10<8 -> 0
 * ```
 *
 * ```KlangScript(Playable)
 * seq("5 10").lt("<8 6>").scale("c3:major").n()  // threshold changes each cycle
 * ```
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags lt, less than, comparison, arithmetic
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.lt(threshold: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(threshold).asSprudelDslArgs(callInfo)) { a, b -> a lt b }

@SprudelDsl
@KlangScript.Function
fun String.lt(threshold: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().lt(threshold, callInfo)

@SprudelDsl
@KlangScript.Function
fun lt(threshold: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.lt(threshold, callInfo) }

@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.lt(threshold: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.lt(threshold, callInfo) }

// -- gt() (Greater Than) ----------------------------------------------------------------------------------------------

/**
 * Compares every value in the pattern to [threshold], replacing each with `1` (true) if greater
 * than [threshold] or `0` (false) otherwise.
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags gt, greater than, comparison, arithmetic
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.gt(threshold: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(threshold).asSprudelDslArgs(callInfo)) { a, b -> a gt b }

@SprudelDsl
@KlangScript.Function
fun String.gt(threshold: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().gt(threshold, callInfo)

@SprudelDsl
@KlangScript.Function
fun gt(threshold: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.gt(threshold, callInfo) }

@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.gt(threshold: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.gt(threshold, callInfo) }

// -- lte() (Less Than or Equal) ---------------------------------------------------------------------------------------

/**
 * Compares every value in the pattern to [threshold], replacing each with `1` (true) if less
 * than or equal to [threshold] or `0` (false) otherwise.
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags lte, less than or equal, comparison, arithmetic
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.lte(threshold: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(threshold).asSprudelDslArgs(callInfo)) { a, b -> a lte b }

@SprudelDsl
@KlangScript.Function
fun String.lte(threshold: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().lte(threshold, callInfo)

@SprudelDsl
@KlangScript.Function
fun lte(threshold: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.lte(threshold, callInfo) }

@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.lte(threshold: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.lte(threshold, callInfo) }

// -- gte() (Greater Than or Equal) ------------------------------------------------------------------------------------

/**
 * Compares every value in the pattern to [threshold], replacing each with `1` (true) if greater
 * than or equal to [threshold] or `0` (false) otherwise.
 *
 * @param threshold The value to compare against. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags gte, greater than or equal, comparison, arithmetic
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.gte(threshold: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(threshold).asSprudelDslArgs(callInfo)) { a, b -> a gte b }

@SprudelDsl
@KlangScript.Function
fun String.gte(threshold: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().gte(threshold, callInfo)

@SprudelDsl
@KlangScript.Function
fun gte(threshold: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.gte(threshold, callInfo) }

@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.gte(threshold: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.gte(threshold, callInfo) }

// -- eq() (Equal) -----------------------------------------------------------------------------------------------------

/**
 * Compares every value in the pattern to [other] for strict equality, replacing each with
 * `1` (true) if equal or `0` (false) otherwise.
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags eq, equal, equality, comparison, arithmetic
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.eq(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(other).asSprudelDslArgs(callInfo)) { a, b -> a eq b }

@SprudelDsl
@KlangScript.Function
fun String.eq(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().eq(other, callInfo)

@SprudelDsl
@KlangScript.Function
fun eq(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.eq(other, callInfo) }

@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.eq(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.eq(other, callInfo) }

// -- eqt() (Truthiness Equal) -----------------------------------------------------------------------------------------

/**
 * Compares the truthiness of every value in the pattern to the truthiness of [other], replacing
 * each with `1` (true) if both share the same truthiness, or `0` (false) otherwise.
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags eqt, truthiness, equal, comparison, arithmetic
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.eqt(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(other).asSprudelDslArgs(callInfo)) { a, b -> a eqt b }

@SprudelDsl
@KlangScript.Function
fun String.eqt(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().eqt(other, callInfo)

@SprudelDsl
@KlangScript.Function
fun eqt(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.eqt(other, callInfo) }

@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.eqt(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.eqt(other, callInfo) }

// -- ne() (Not Equal) -------------------------------------------------------------------------------------------------

/**
 * Compares every value in the pattern to [other] for strict inequality, replacing each with
 * `1` (true) if not equal or `0` (false) otherwise.
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags ne, not equal, inequality, comparison, arithmetic
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.ne(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(other).asSprudelDslArgs(callInfo)) { a, b -> a ne b }

@SprudelDsl
@KlangScript.Function
fun String.ne(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().ne(other, callInfo)

@SprudelDsl
@KlangScript.Function
fun ne(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.ne(other, callInfo) }

@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.ne(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.ne(other, callInfo) }

// -- net() (Truthiness Not Equal) -------------------------------------------------------------------------------------

/**
 * Compares the truthiness of every value in the pattern to the truthiness of [other], replacing
 * each with `1` (true) if their truthiness differs, or `0` (false) otherwise.
 *
 * @param other The value to compare against. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern of `0`/`1` values.
 * @category arithmetic
 * @tags net, truthiness, not equal, inequality, comparison, arithmetic
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.net(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(other).asSprudelDslArgs(callInfo)) { a, b -> a net b }

@SprudelDsl
@KlangScript.Function
fun String.net(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().net(other, callInfo)

@SprudelDsl
@KlangScript.Function
fun net(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.net(other, callInfo) }

@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.net(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.net(other, callInfo) }

// -- and() (Logical AND) ----------------------------------------------------------------------------------------------

/**
 * Applies logical AND between every value in the pattern and [other].
 *
 * @param other The right-hand operand. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern where each value is `value && other`.
 * @category arithmetic
 * @tags and, logical, boolean, arithmetic
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.and(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(other).asSprudelDslArgs(callInfo)) { a, b -> a and b }

@SprudelDsl
@KlangScript.Function
fun String.and(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().and(other, callInfo)

@SprudelDsl
@KlangScript.Function
fun and(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.and(other, callInfo) }

@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.and(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.and(other, callInfo) }

// -- or() (Logical OR) ------------------------------------------------------------------------------------------------

/**
 * Applies logical OR between every value in the pattern and [other].
 *
 * @param other The right-hand operand. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern where each value is `value || other`.
 * @category arithmetic
 * @tags or, logical, boolean, arithmetic
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.or(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(other).asSprudelDslArgs(callInfo)) { a, b -> a or b }

@SprudelDsl
@KlangScript.Function
fun String.or(other: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().or(other, callInfo)

@SprudelDsl
@KlangScript.Function
fun or(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.or(other, callInfo) }

@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.or(other: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.or(other, callInfo) }

// -- round() ----------------------------------------------------------------------------------------------------------

/**
 * Rounds every numeric value in the pattern to the nearest integer.
 *
 * @return A new pattern with each value rounded to the nearest integer.
 * @category arithmetic
 * @tags round, rounding, arithmetic, math
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.round(callInfo: CallInfo? = null): SprudelPattern =
    applyUnaryOp(this) { v -> v.asRational?.round()?.asVoiceValue() ?: v }

@SprudelDsl
@KlangScript.Function
fun String.round(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().round(callInfo)

@SprudelDsl
@KlangScript.Function
fun round(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.round(callInfo) }

@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.round(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.round(callInfo) }

// -- floor() ----------------------------------------------------------------------------------------------------------

/**
 * Floors every numeric value in the pattern to the largest integer less than or equal to the value.
 *
 * @return A new pattern with each value floored to an integer.
 * @category arithmetic
 * @tags floor, rounding, arithmetic, math
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.floor(callInfo: CallInfo? = null): SprudelPattern =
    applyUnaryOp(this) { v -> v.asRational?.floor()?.asVoiceValue() ?: v }

@SprudelDsl
@KlangScript.Function
fun String.floor(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().floor(callInfo)

@SprudelDsl
@KlangScript.Function
fun floor(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.floor(callInfo) }

@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.floor(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.floor(callInfo) }

// -- ceil() -----------------------------------------------------------------------------------------------------------

/**
 * Ceils every numeric value in the pattern to the smallest integer greater than or equal to the value.
 *
 * @return A new pattern with each value ceiled to an integer.
 * @category arithmetic
 * @tags ceil, ceiling, rounding, arithmetic, math
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.ceil(callInfo: CallInfo? = null): SprudelPattern =
    applyUnaryOp(this) { v -> v.asRational?.ceil()?.asVoiceValue() ?: v }

@SprudelDsl
@KlangScript.Function
fun String.ceil(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().ceil(callInfo)

@SprudelDsl
@KlangScript.Function
fun ceil(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.ceil(callInfo) }

@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.ceil(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.ceil(callInfo) }
