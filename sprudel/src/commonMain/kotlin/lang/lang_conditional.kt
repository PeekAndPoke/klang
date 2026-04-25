@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel._innerJoin
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.sampleAt

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangConditionalInit = false

// -- firstOf() --------------------------------------------------------------------------------------------------------

private fun applyFirstOf(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val transform = args.getOrNull(1).toPatternMapper() ?: return source

    return source._innerJoin(args.take(1)) { src, nValue ->
        val n = nValue?.asInt ?: 1
        if (n <= 1) {
            transform(src)
        } else {
            val patterns = ArrayList<SprudelPattern>(n)
            patterns.add(transform(src))
            repeat(n - 1) { patterns.add(src) }
            applySlowcatPrime(patterns)
        }
    }
}

/**
 * Applies [transform] on the **first** cycle of every [n] cycles; all other cycles play unchanged.
 *
 * The pattern rotates through [n] cycles: the transformed version plays on cycle 1, then the
 * original plays on cycles 2 through [n], then the sequence repeats.
 *
 * @param n How many cycles make one period. The transform fires on the first of these.
 * @param transform The function to apply on the first cycle of each period.
 * @return A new pattern that applies [transform] periodically.
 *
 * ```KlangScript(Playable)
 * note("c3 d3 e3 g3").firstOf(4, x => x.rev())  // reverse every 4th cycle
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 d3 e3 g3").firstOf("<2 4>", x => x.fast(2))  // alternating period
 * ```
 * @alias every
 * @category conditional
 * @tags firstOf, every, conditional, cycle, periodic, transform
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.firstOf(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyFirstOf(this, listOf(n, transform).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern, then applies [transform] on the first of every [n] cycles.
 *
 * @param n How many cycles make one period. The transform fires on the first of these.
 * @param transform The function to apply on the first cycle of each period.
 * @return A new pattern that applies [transform] periodically.
 *
 * ```KlangScript(Playable)
 * "c3 d3 e3 g3".firstOf(4, x => x.rev()).note()  // reverse every 4th cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.firstOf(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).firstOf(n, transform, callInfo)

/**
 * Returns a [PatternMapperFn] that applies [transform] on the **first** cycle of every [n] cycles.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 *
 * @param n How many cycles make one period. The transform fires on the first of these.
 * @param transform The function to apply on the first cycle of each period.
 * @return A [PatternMapperFn] that applies [transform] periodically.
 *
 * ```KlangScript(Playable)
 * note("c3 d3 e3 g3").apply(firstOf(4, x => x.rev()))  // reverse every 4th cycle
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").every(3, firstOf(2, x => x.fast(2)))  // nested periodic transforms
 * ```
 * @alias every
 * @category conditional
 * @tags firstOf, every, conditional, cycle, periodic, transform
 */
@SprudelDsl
@KlangScript.Function
fun firstOf(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.firstOf(n, transform, callInfo) }

/**
 * Chains a periodic transform onto this [PatternMapperFn], applying [transform] on the first of every [n] cycles.
 *
 * ```KlangScript(Playable)
 * note("a").apply(lastOf(3, x => x.note("b")).firstOf(2, x => x.note("c")))
 * ```
 *
 * @param n How many cycles make one period. The transform fires on the first of these.
 * @param transform The function to apply on the first cycle of each period.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.firstOf(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.firstOf(n, transform, callInfo) }

// -- every() ----------------------------------------------------------------------------------------------------------

/**
 * Alias for [firstOf]. Applies [transform] on the **first** cycle of every [n] cycles.
 *
 * @param n How many cycles make one period. The transform fires on the first of these.
 * @param transform The function to apply on the first cycle of each period.
 * @return A new pattern that applies [transform] periodically.
 *
 * ```KlangScript(Playable)
 * note("c3 d3 e3 g3").every(4, x => x.rev())  // reverse every 4th cycle
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 d3 e3 g3").every("<2 4>", x => x.fast(2))  // alternating period
 * ```
 * @alias firstOf
 * @category conditional
 * @tags every, firstOf, conditional, cycle, periodic, transform
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.every(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.firstOf(n, transform, callInfo)

/**
 * Parses this string as a pattern, then applies [transform] on the first of every [n] cycles.
 *
 * @param n How many cycles make one period. The transform fires on the first of these.
 * @param transform The function to apply on the first cycle of each period.
 * @return A new pattern that applies [transform] periodically.
 *
 * ```KlangScript(Playable)
 * "c3 d3 e3 g3".every(4, x => x.rev()).note()  // reverse every 4th cycle
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.every(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).every(n, transform, callInfo)

/**
 * Returns a [PatternMapperFn] that applies [transform] on the **first** cycle of every [n] cycles.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 *
 * @param n How many cycles make one period. The transform fires on the first of these.
 * @param transform The function to apply on the first cycle of each period.
 * @return A [PatternMapperFn] that applies [transform] periodically.
 *
 * ```KlangScript(Playable)
 * note("c3 d3 e3 g3").apply(every(4, x => x.rev()))  // reverse every 4th cycle
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").firstOf(3, every(2, x => x.fast(2)))  // nested periodic transforms
 * ```
 * @alias firstOf
 * @category conditional
 * @tags every, firstOf, conditional, cycle, periodic, transform
 */
@SprudelDsl
@KlangScript.Function
fun every(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    firstOf(n, transform, callInfo)

/**
 * Chains a periodic transform onto this [PatternMapperFn], applying [transform] on the first of every [n] cycles.
 *
 * ```KlangScript(Playable)
 * note("c3 d3").apply(lastOf(4, x => x.rev()).every(2, x => x.fast(2)))  // alternate two transforms
 * ```
 *
 * @param n How many cycles make one period. The transform fires on the first of these.
 * @param transform The function to apply on the first cycle of each period.
 * @alias firstOf
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.every(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.firstOf(n, transform, callInfo)

// -- lastOf() ---------------------------------------------------------------------------------------------------------

private fun applyLastOf(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val transform = args.getOrNull(1).toPatternMapper() ?: return source

    return source._innerJoin(args.take(1)) { src, nValue ->
        val n = nValue?.asInt ?: 1
        if (n <= 1) {
            transform(src)
        } else {
            val patterns = ArrayList<SprudelPattern>(n)
            repeat(n - 1) { patterns.add(src) }
            patterns.add(transform(src))
            applySlowcatPrime(patterns)
        }
    }
}

/**
 * Applies [transform] on the **last** cycle of every [n] cycles; all other cycles play unchanged.
 *
 * The pattern rotates through [n] cycles: the original plays on cycles 1 through n−1, then the
 * transformed version plays on cycle [n], then the sequence repeats.
 *
 * @param n How many cycles make one period. The transform fires on the last of these.
 * @param transform The function to apply on the last cycle of each period.
 * @return A new pattern that applies [transform] at the end of each period.
 *
 * ```KlangScript(Playable)
 * note("c3 d3 e3 g3").lastOf(4, x => x.rev())  // reverse on 4th of every 4
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 d3 e3 g3").lastOf("<2 4>", x => x.fast(2))  // alternating period
 * ```
 * @category conditional
 * @tags lastOf, conditional, cycle, periodic, transform
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.lastOf(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyLastOf(this, listOf(n, transform).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern, then applies [transform] on the last of every [n] cycles.
 *
 * @param n How many cycles make one period. The transform fires on the last of these.
 * @param transform The function to apply on the last cycle of each period.
 * @return A new pattern that applies [transform] at the end of each period.
 *
 * ```KlangScript(Playable)
 * "c3 d3 e3 g3".lastOf(4, x => x.rev()).note()  // reverse on the 4th of every 4 cycles
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.lastOf(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lastOf(n, transform, callInfo)

/**
 * Returns a [PatternMapperFn] that applies [transform] on the **last** cycle of every [n] cycles.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 *
 * @param n How many cycles make one period. The transform fires on the last of these.
 * @param transform The function to apply on the last cycle of each period.
 * @return A [PatternMapperFn] that applies [transform] at the end of each period.
 *
 * ```KlangScript(Playable)
 * note("c3 d3 e3 g3").apply(lastOf(4, x => x.rev()))  // reverse on the 4th of every 4 cycles
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").firstOf(3, lastOf(2, x => x.fast(2)))  // nested periodic transforms
 * ```
 * @category conditional
 * @tags lastOf, conditional, cycle, periodic, transform
 */
@SprudelDsl
@KlangScript.Function
fun lastOf(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.lastOf(n, transform, callInfo) }

/**
 * Chains a periodic transform onto this [PatternMapperFn], applying [transform] on the last of every [n] cycles.
 *
 * ```KlangScript(Playable)
 * note("c3 d3").apply(firstOf(4, x => x.rev()).lastOf(2, x => x.fast(2)))  // alternate two transforms
 * ```
 *
 * @param n How many cycles make one period. The transform fires on the last of these.
 * @param transform The function to apply on the last cycle of each period.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.lastOf(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.lastOf(n, transform, callInfo) }

// -- when() -----------------------------------------------------------------------------------------------------------

private fun applyWhen(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val condition = args.getOrNull(0)?.toPattern() ?: return p
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }

    // The true part: events where the condition is sampled as truthy.
    val truePart = object : SprudelPattern by p {
        override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent> {
            return p.queryArcContextual(from, to, ctx).filter { event ->
                // Keep if condition is missing OR if the found event is not truthy
                condition.sampleAt(event.part.begin, ctx)?.data?.isTruthy() ?: false
            }
        }
    }

    // The false part: events where the condition is NOT truthy (falsy OR silent).
    // We implement this as a custom pattern to ensure complete coverage of the source pattern.
    val falsePart = object : SprudelPattern by p {
        override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent> {
            return p.queryArcContextual(from, to, ctx).filter { event ->
                // Keep if condition is missing OR if the found event is not truthy
                condition.sampleAt(event.part.begin, ctx)?.data?.isNotTruthy() ?: true
            }
        }
    }

    // Apply transform only to the 'true' part and stack with the complementary 'false' part
    return stack(transform(truePart), falsePart)
}

/**
 * Conditionally applies [transform] to every event whose position in the cycle falls within a
 * truthy region of [condition].
 *
 * The [condition] pattern is sampled at each event's time. If the sampled value is truthy
 * (non-zero), [transform] is applied; otherwise the event plays unchanged.
 *
 * A common idiom is to use binary patterns (`0`/`1`) as the condition — for example a pattern
 * created with [struct] or a slow alternation like `pure(1).slowcat(pure(0))`.
 *
 * @param condition A pattern whose values determine when to apply [transform].
 * @param transform The function to apply when [condition] is truthy.
 * @return A new pattern that conditionally applies [transform].
 *
 * ```KlangScript(Playable)
 * note("c d e f").when(pure(1).struct("t ~ t ~"), x => x.add(12))
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").when("<1 0>", x => x.fast(2))
 * ```
 * @category conditional
 * @tags when, conditional, binary, gate, transform
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.`when`(condition: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyWhen(this, listOf(condition, transform).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern, then applies [transform] whenever [condition] is truthy.
 *
 * @param condition A pattern whose values determine when to apply [transform].
 *   Zero is falsy; any other value is truthy.
 * @param transform The function to apply when [condition] is truthy.
 * @return A new pattern that conditionally applies [transform].
 *
 * ```KlangScript(Playable)
 * "c d e f".when("1 0 1 0", x => x.add(12)).note()
 * ```
 */
@SprudelDsl
@KlangScript.Function
fun String.`when`(condition: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).`when`(condition, transform, callInfo)

/**
 * Returns a [PatternMapperFn] that conditionally applies [transform] to events where [condition] is truthy.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 *
 * @param condition A pattern whose values determine when to apply [transform].
 *   Zero is falsy; any other value is truthy.
 * @param transform The function to apply when [condition] is truthy.
 * @return A [PatternMapperFn] that conditionally applies [transform].
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(when("1 0 1 0", x => x.add(12)))
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").firstOf(4, when("1 0 1 0", x => x.add(12)))
 * ```
 * @category conditional
 * @tags when, conditional, binary, gate, transform
 */
@SprudelDsl
@KlangScript.Function
fun `when`(condition: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.`when`(condition, transform, callInfo) }

/**
 * Chains a conditional transform onto this [PatternMapperFn], applying [transform] whenever [condition] is truthy.
 *
 * ```KlangScript(Playable)
 * note("c3 d3").apply(firstOf(4, x => x.rev()).when("1 0", x => x.add(12)))
 * ```
 *
 * @param condition A pattern whose values determine when to apply [transform].
 * @param transform The function to apply when [condition] is truthy.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.`when`(condition: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.`when`(condition, transform, callInfo) }
