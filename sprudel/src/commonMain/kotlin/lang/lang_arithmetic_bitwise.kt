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
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

// -- bitAnd() (Bitwise AND) ---------------------------------------------------------------------------------------------

/**
 * Applies bitwise AND of [mask] to every integer value in the pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Values are truncated to integers before the operation.
 *
 * ```KlangScript(Playable)
 * "12 15".bitAnd(10).scale("c3:major").n()  // 12&10=8, 15&10=10
 * ```
 *
 * ```KlangScript(Playable)
 * "127".bitAnd("<15 63>").scale("c3:major").n()  // mask low or high nibble alternately
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern where each value is replaced by `value & mask`.
 * @category arithmetic
 * @tags bitAnd, bitwise, and, arithmetic, binary
 */
@KlangScript.Function
fun SprudelPattern.bitAnd(mask: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(mask).asSprudelDslArgs(callInfo)) { a, b -> a bitAnd b }

/**
 * Parses this string as a pattern, then applies bitwise AND with [mask] to every integer value.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript(Playable)
 * "12 15".bitAnd(10).scale("c3:major").n()  // 12&10=8, 15&10=10
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 */
@KlangScript.Function
fun String.bitAnd(mask: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bitAnd(mask, callInfo)

/**
 * Creates a [PatternMapperFn] that applies bitwise AND of [mask] to every integer value in a pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [SprudelPattern.apply] to apply the mask to an existing pattern.
 *
 * ```KlangScript(Playable)
 * seq("12 15").apply(bitAnd(10)).scale("c3:major").n()  // 12&10=8, 15&10=10
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 */
@KlangScript.Function
fun bitAnd(mask: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bitAnd(mask, callInfo) }

/**
 * Chains a bitwise AND onto this [PatternMapperFn], applying [mask] to every integer value.
 *
 * ```KlangScript(Playable)
 * seq("12 15").apply(add(3).bitAnd(10)).scale("c2:major").n()  // (12+3)&10=10, (15+3)&10=2
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 */
@KlangScript.Function
fun PatternMapperFn.bitAnd(mask: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bitAnd(mask, callInfo) }

// -- bitOr() (Bitwise OR) -----------------------------------------------------------------------------------------------

/**
 * Applies bitwise OR of [mask] to every integer value in the pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Values are truncated to integers before the operation.
 *
 * ```KlangScript(Playable)
 * "8 4".bitOr(2).scale("c3:major").n()  // 8|2=10, 4|2=6
 * ```
 *
 * ```KlangScript(Playable)
 * "0".bitOr("<1 2 4 8>").scale("c3:major").n()  // set individual bits each cycle
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern where each value is replaced by `value | mask`.
 * @category arithmetic
 * @tags bitOr, bitwise, or, arithmetic, binary
 */
@KlangScript.Function
fun SprudelPattern.bitOr(mask: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(mask).asSprudelDslArgs(callInfo)) { a, b -> a bitOr b }

/**
 * Parses this string as a pattern, then applies bitwise OR with [mask] to every integer value.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript(Playable)
 * "8 4".bitOr(2).scale("c3:major").n()  // 8|2=10, 4|2=6
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 */
@KlangScript.Function
fun String.bitOr(mask: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bitOr(mask, callInfo)

/**
 * Creates a [PatternMapperFn] that applies bitwise OR of [mask] to every integer value in a pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [SprudelPattern.apply] to apply the mask to an existing pattern.
 *
 * ```KlangScript(Playable)
 * seq("8 4").apply(bitOr(2)).scale("c3:major").n()  // 8|2=10, 4|2=6
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 */
@KlangScript.Function
fun bitOr(mask: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bitOr(mask, callInfo) }

/**
 * Chains a bitwise OR onto this [PatternMapperFn], applying [mask] to every integer value.
 *
 * ```KlangScript(Playable)
 * seq("8 4").apply(add(1).bitOr(2)).scale("c2:major").n()  // (8+1)|2=11, (4+1)|2=7
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 */
@KlangScript.Function
fun PatternMapperFn.bitOr(mask: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bitOr(mask, callInfo) }

// -- bitXor() (Bitwise XOR) ---------------------------------------------------------------------------------------------

/**
 * Applies bitwise XOR of [mask] to every integer value in the pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Values are truncated to integers before the operation. XOR is useful for
 * toggling specific bits.
 *
 * ```KlangScript(Playable)
 * "12 10".bitXor(6).scale("c3:major").n()  // 12^6=10, 10^6=12
 * ```
 *
 * ```KlangScript(Playable)
 * "5".bitXor("<3 5>").scale("c3:major").n()  // toggle bits each cycle
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern where each value is replaced by `value ^ mask`.
 * @category arithmetic
 * @tags bitXor, bitwise, xor, arithmetic, binary
 */
@KlangScript.Function
fun SprudelPattern.bitXor(mask: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(mask).asSprudelDslArgs(callInfo)) { a, b -> a bitXor b }

/**
 * Parses this string as a pattern, then applies bitwise XOR with [mask] to every integer value.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript(Playable)
 * "12 10".bitXor(6).scale("c3:major").n()  // 12^6=10, 10^6=12
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 */
@KlangScript.Function
fun String.bitXor(mask: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bitXor(mask, callInfo)

/**
 * Creates a [PatternMapperFn] that applies bitwise XOR of [mask] to every integer value in a pattern.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [SprudelPattern.apply] to apply the mask to an existing pattern.
 *
 * ```KlangScript(Playable)
 * seq("12 10").apply(bitXor(6)).scale("c3:major").n()  // 12^6=10, 10^6=12
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 */
@KlangScript.Function
fun bitXor(mask: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bitXor(mask, callInfo) }

/**
 * Chains a bitwise XOR onto this [PatternMapperFn], applying [mask] to every integer value.
 *
 * ```KlangScript(Playable)
 * seq("12 10").apply(add(2).bitXor(6)).scale("c2:major").n()  // (12+2)^6=8, (10+2)^6=10
 * ```
 *
 * @param mask The bitmask. May be a number, string mini-notation, or a [SprudelPattern].
 */
@KlangScript.Function
fun PatternMapperFn.bitXor(mask: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bitXor(mask, callInfo) }

// -- bitShl() (Bitwise Left Shift) -----------------------------------------------------------------------------------

/**
 * Shifts every integer value in the pattern left by [bits] bits (equivalent to multiplying by 2^n).
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Values are truncated to integers before the operation.
 *
 * ```KlangScript(Playable)
 * "1 2".bitShl(2).scale("c3:major").n()  // 1<<2=4, 2<<2=8
 * ```
 *
 * ```KlangScript(Playable)
 * "1".bitShl("<0 1 2 3>").scale("c3:major").n()  // 1, 2, 4, 8 over four cycles
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern where each value is replaced by `value << bits`.
 * @category arithmetic
 * @tags bitShl, bitwise, shift, left shift, arithmetic, binary
 */
@KlangScript.Function
fun SprudelPattern.bitShl(bits: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(bits).asSprudelDslArgs(callInfo)) { a, b -> a shl b }

/**
 * Parses this string as a pattern, then shifts every integer value left by [bits] bits.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript(Playable)
 * "1 2".bitShl(2).scale("c3:major").n()  // 1<<2=4, 2<<2=8
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [SprudelPattern].
 */
@KlangScript.Function
fun String.bitShl(bits: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bitShl(bits, callInfo)

/**
 * Creates a [PatternMapperFn] that shifts every integer value in a pattern left by [bits] bits.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [SprudelPattern.apply] to apply the shift to an existing pattern.
 *
 * ```KlangScript(Playable)
 * seq("1 2").apply(bitShl(2)).scale("c3:major").n()  // 1<<2=4, 2<<2=8
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation,
 *   or a [SprudelPattern].
 */
@KlangScript.Function
fun bitShl(bits: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bitShl(bits, callInfo) }

/**
 * Chains a bitwise left-shift onto this [PatternMapperFn], shifting every integer value left by [bits] bits.
 *
 * ```KlangScript(Playable)
 * seq("1 2").apply(add(1).bitShl(2)).scale("c2:major").n()  // (1+1)<<2=8, (2+1)<<2=12
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [SprudelPattern].
 */
@KlangScript.Function
fun PatternMapperFn.bitShl(bits: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bitShl(bits, callInfo) }

// -- bitShr() (Bitwise Right Shift) ----------------------------------------------------------------------------------

/**
 * Shifts every integer value in the pattern right by [bits] bits (equivalent to integer-dividing by 2^n).
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and all other voice properties
 * remain unchanged. Values are truncated to integers before the operation.
 *
 * ```KlangScript(Playable)
 * "8 12".bitShr(2).scale("c3:major").n()  // 8>>2=2, 12>>2=3
 * ```
 *
 * ```KlangScript(Playable)
 * "16".bitShr("<0 1 2 3>").scale("c3:major").n()  // 16, 8, 4, 2 over four cycles
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [SprudelPattern].
 * @return A new pattern where each value is replaced by `value >> bits`.
 * @category arithmetic
 * @tags bitShr, bitwise, shift, right shift, arithmetic, binary
 */
@KlangScript.Function
fun SprudelPattern.bitShr(bits: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyArithmetic(this, listOfNotNull(bits).asSprudelDslArgs(callInfo)) { a, b -> a shr b }

/**
 * Parses this string as a pattern, then shifts every integer value right by [bits] bits.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged.
 *
 * ```KlangScript(Playable)
 * "8 12".bitShr(2).scale("c3:major").n()  // 8>>2=2, 12>>2=3
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [SprudelPattern].
 */
@KlangScript.Function
fun String.bitShr(bits: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bitShr(bits, callInfo)

/**
 * Creates a [PatternMapperFn] that shifts every integer value in a pattern right by [bits] bits.
 *
 * Only the raw event `value` is affected — `note`, `soundIndex`, and other voice properties
 * remain unchanged. Use with [SprudelPattern.apply] to apply the shift to an existing pattern.
 *
 * ```KlangScript(Playable)
 * seq("8 12").apply(bitShr(2)).scale("c3:major").n()  // 8>>2=2, 12>>2=3
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [SprudelPattern].
 */
@KlangScript.Function
fun bitShr(bits: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bitShr(bits, callInfo) }

/**
 * Chains a bitwise right-shift onto this [PatternMapperFn], shifting every integer value right by [bits] bits.
 *
 * ```KlangScript(Playable)
 * seq("8 16").apply(mul(2).bitShr(3)).scale("c3:major").n()  // (8*2)>>3=2, (16*2)>>3=4
 * ```
 *
 * @param bits The number of bit positions to shift. May be a number, string mini-notation, or a [SprudelPattern].
 */
@KlangScript.Function
fun PatternMapperFn.bitShr(bits: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bitShr(bits, callInfo) }
