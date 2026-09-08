/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceValue
import io.peekandpoke.klang.sprudel._appLeft
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice

/**
 * Helper for the binary arithmetic, comparison and bitwise operators: `source op control`, written
 * into the value register of every source event.
 *
 * **Structure from the SOURCE, values from both** ([_appLeft], Strudel's default `add`). It used to
 * be an inner join: structure, `weight` and `numSteps` from the CONTROL (the wholes were the
 * source's even then), and a continuous control queried over a cycle emits one event valued at the
 * cycle start, so `seq("1 1 1").mul(sine)` gave every note the same number while
 * `.pan(sine.range(0, 1))` swept (`docs/tasks/sprudel-arithmetic-continuous-controls.md`, 2026-09-07).
 *
 * Now a continuous control is read at every onset without a `seg()`, and `weight`/`numSteps` come
 * from the source. A busier control still splits a source event into fragments under the source's
 * whole, on purpose: arithmetic results are mostly READ, not played (`"<0.9>".mul("[1.3 0.99!7]")` is
 * a clip map that `.clip(...)` samples once per note), and a point query has to find the control
 * value that was live at that point. See [_appLeft] for why onset sampling ([_outerJoin]) would
 * flatten such a map to its first value.
 *
 * A continuous SOURCE has no structure of its own (one event per query arc), so
 * `note(saw.range(48, 60).add("0 12"))` plays one note per cycle; `seg()` the source first.
 *
 * A source span that meets no control event (a rest in the control) is dropped, as before and as
 * in Strudel; so is a control event without a value. A source event without a value passes through
 * (with its own copy of the voice data, one per fragment).
 */
internal fun applyArithmetic(
    source: SprudelPattern,
    args: List<SprudelDslArg<Any?>>,
    op: (SprudelVoiceValue, SprudelVoiceValue) -> SprudelVoiceValue?,
): SprudelPattern {
    val control = args.getOrNull(0)?.toPattern() ?: return source

    return source._appLeft(control) { event, controlEvent ->
        val controlVal = controlEvent.data.value ?: return@_appLeft null
        val sourceVal = event.data.value ?: return@_appLeft event.copy(data = event.data.clone())
        event.copy(data = event.data.copy(value = op(sourceVal, controlVal)))
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
@KlangScript.Function
fun String.add(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).add(amount, callInfo)

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
@KlangScript.Function
fun PatternMapperFn.add(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.add(amount, callInfo) }

/**
 * First step of a mapper chain on a field accessor: `freq.add(...)` reads the field, then adds [amount].
 *
 * ```KlangScript(Playable)
 * note("c e g a").bpf(freq.add(50))
 * ```
 *
 * @param amount The operand. May be a number, string mini-notation, or a [SprudelPattern].
 */
@KlangScript.Function
fun PatternMapperProvider.add(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    mapper().add(amount, callInfo)

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
@KlangScript.Function
fun String.sub(amount: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sub(amount, callInfo)

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
@KlangScript.Function
fun PatternMapperFn.sub(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sub(amount, callInfo) }

/**
 * First step of a mapper chain on a field accessor: `freq.sub(...)` reads the field, then subtracts [amount].
 *
 * ```KlangScript(Playable)
 * note("c e g a").bpf(freq.sub(50))
 * ```
 *
 * @param amount The operand. May be a number, string mini-notation, or a [SprudelPattern].
 */
@KlangScript.Function
fun PatternMapperProvider.sub(amount: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    mapper().sub(amount, callInfo)

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
@KlangScript.Function
fun String.mul(factor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).mul(factor, callInfo)

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
@KlangScript.Function
fun PatternMapperFn.mul(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.mul(factor, callInfo) }

/**
 * First step of a mapper chain on a field accessor: `freq.mul(...)` reads the field, then multiplies by [factor].
 *
 * ```KlangScript(Playable)
 * note("c e g a").bpf(freq.mul(2))
 * ```
 *
 * @param factor The operand. May be a number, string mini-notation, or a [SprudelPattern].
 */
@KlangScript.Function
fun PatternMapperProvider.mul(factor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    mapper().mul(factor, callInfo)

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
@KlangScript.Function
fun String.div(divisor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).div(divisor, callInfo)

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
@KlangScript.Function
fun PatternMapperFn.div(divisor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.div(divisor, callInfo) }

/**
 * First step of a mapper chain on a field accessor: `freq.div(...)` reads the field, then divides by [divisor].
 *
 * ```KlangScript(Playable)
 * note("c e g a").bpf(freq.div(2))
 * ```
 *
 * @param divisor The operand. May be a number, string mini-notation, or a [SprudelPattern].
 */
@KlangScript.Function
fun PatternMapperProvider.div(divisor: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    mapper().div(divisor, callInfo)

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
@KlangScript.Function
fun String.mod(divisor: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).mod(divisor, callInfo)

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
@KlangScript.Function
fun String.pow(exponent: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pow(exponent, callInfo)

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
@KlangScript.Function
fun PatternMapperFn.pow(exponent: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pow(exponent, callInfo) }

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
@KlangScript.Function
fun SprudelPattern.log2(@Suppress("unused") callInfo: CallInfo? = null): SprudelPattern =
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
@KlangScript.Function
fun String.log2(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).log2(callInfo)

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
@KlangScript.Function
fun PatternMapperFn.log2(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.log2(callInfo) }
