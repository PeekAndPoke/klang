// strudel/src/commonMain/kotlin/lang/lang_conditional.kt
@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel._innerJoin
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.sampleAt

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangConditionalInit = false

// -- firstOf() --------------------------------------------------------------------------------------------------------

fun applyFirstOf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val transform = args.getOrNull(1).toPatternMapper() ?: return source

    return source._innerJoin(args.take(1)) { src, nValue ->
        val n = nValue?.asInt ?: 1
        if (n <= 1) {
            transform(src)
        } else {
            val patterns = ArrayList<StrudelPattern>(n)
            patterns.add(transform(src))
            repeat(n - 1) { patterns.add(src) }
            applySlowcatPrime(patterns)
        }
    }
}

internal val _firstOf by dslPatternMapper { args, callInfo -> { p -> p._firstOf(args, callInfo) } }
internal val StrudelPattern._firstOf by dslPatternExtension { source, args, _ -> applyFirstOf(source, args) }
internal val String._firstOf by dslStringExtension { source, args, callInfo -> source._firstOf(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c3 d3 e3 g3").firstOf(4, x => x.rev())  // reverse every 4th cycle
 * ```
 *
 * ```KlangScript
 * note("c3 d3 e3 g3").firstOf("<2 4>", x => x.fast(2))  // alternating period
 * ```
 * @alias every
 * @category conditional
 * @tags firstOf, every, conditional, cycle, periodic, transform
 */
@StrudelDsl
fun StrudelPattern.firstOf(n: PatternLike, transform: PatternMapperFn): StrudelPattern =
    this._firstOf(listOf(n, transform).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then applies [transform] on the first of every [n] cycles.
 *
 * @param n How many cycles make one period. The transform fires on the first of these.
 * @param transform The function to apply on the first cycle of each period.
 * @return A new pattern that applies [transform] periodically.
 *
 * ```KlangScript
 * "c3 d3 e3 g3".firstOf(4, x => x.rev()).note()  // reverse every 4th cycle
 * ```
 */
@StrudelDsl
fun String.firstOf(n: PatternLike, transform: PatternMapperFn): StrudelPattern =
    this._firstOf(listOf(n, transform).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that applies [transform] on the **first** cycle of every [n] cycles.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 *
 * @param n How many cycles make one period. The transform fires on the first of these.
 * @param transform The function to apply on the first cycle of each period.
 * @return A [PatternMapperFn] that applies [transform] periodically.
 *
 * ```KlangScript
 * note("c3 d3 e3 g3").apply(firstOf(4, x => x.rev()))  // reverse every 4th cycle
 * ```
 *
 * ```KlangScript
 * note("c3 e3 g3").every(3, firstOf(2, x => x.fast(2)))  // nested periodic transforms
 * ```
 * @alias every
 * @category conditional
 * @tags firstOf, every, conditional, cycle, periodic, transform
 */
@StrudelDsl
fun firstOf(n: PatternLike, transform: PatternMapperFn): PatternMapperFn =
    _firstOf(listOf(n, transform).asStrudelDslArgs())

// -- every() ----------------------------------------------------------------------------------------------------------

internal val _every by dslPatternMapper { args, callInfo -> _firstOf(args, callInfo) }
internal val StrudelPattern._every by dslPatternExtension { source, args, callInfo -> source._firstOf(args, callInfo) }
internal val String._every by dslStringExtension { source, args, callInfo -> source._firstOf(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Alias for [firstOf]. Applies [transform] on the **first** cycle of every [n] cycles.
 *
 * @param n How many cycles make one period. The transform fires on the first of these.
 * @param transform The function to apply on the first cycle of each period.
 * @return A new pattern that applies [transform] periodically.
 *
 * ```KlangScript
 * note("c3 d3 e3 g3").every(4, x => x.rev())  // reverse every 4th cycle
 * ```
 *
 * ```KlangScript
 * note("c3 d3 e3 g3").every("<2 4>", x => x.fast(2))  // alternating period
 * ```
 * @alias firstOf
 * @category conditional
 * @tags every, firstOf, conditional, cycle, periodic, transform
 */
@StrudelDsl
fun StrudelPattern.every(n: PatternLike, transform: PatternMapperFn): StrudelPattern =
    this._every(listOf(n, transform).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then applies [transform] on the first of every [n] cycles.
 *
 * @param n How many cycles make one period. The transform fires on the first of these.
 * @param transform The function to apply on the first cycle of each period.
 * @return A new pattern that applies [transform] periodically.
 *
 * ```KlangScript
 * "c3 d3 e3 g3".every(4, x => x.rev()).note()  // reverse every 4th cycle
 * ```
 */
@StrudelDsl
fun String.every(n: PatternLike, transform: PatternMapperFn): StrudelPattern =
    this._every(listOf(n, transform).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that applies [transform] on the **first** cycle of every [n] cycles.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 *
 * @param n How many cycles make one period. The transform fires on the first of these.
 * @param transform The function to apply on the first cycle of each period.
 * @return A [PatternMapperFn] that applies [transform] periodically.
 *
 * ```KlangScript
 * note("c3 d3 e3 g3").apply(every(4, x => x.rev()))  // reverse every 4th cycle
 * ```
 *
 * ```KlangScript
 * note("c3 e3 g3").firstOf(3, every(2, x => x.fast(2)))  // nested periodic transforms
 * ```
 * @alias firstOf
 * @category conditional
 * @tags every, firstOf, conditional, cycle, periodic, transform
 */
@StrudelDsl
fun every(n: PatternLike, transform: PatternMapperFn): PatternMapperFn =
    _every(listOf(n, transform).asStrudelDslArgs())

// -- lastOf() ---------------------------------------------------------------------------------------------------------

private fun applyLastOf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    @Suppress("UNCHECKED_CAST")
    val transform = args.getOrNull(1).toPatternMapper() ?: return source

    return source._innerJoin(args.take(1)) { src, nValue ->
        val n = nValue?.asInt ?: 1
        if (n <= 1) {
            transform(src)
        } else {
            val patterns = ArrayList<StrudelPattern>(n)
            repeat(n - 1) { patterns.add(src) }
            patterns.add(transform(src))
            applySlowcatPrime(patterns)
        }
    }
}

internal val _lastOf by dslPatternMapper { args, callInfo -> { p -> p._lastOf(args, callInfo) } }
internal val StrudelPattern._lastOf by dslPatternExtension { source, args, _ -> applyLastOf(source, args) }
internal val String._lastOf by dslStringExtension { source, args, callInfo -> source._lastOf(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c3 d3 e3 g3").lastOf(4, x => x.rev())  // reverse on 4th of every 4
 * ```
 *
 * ```KlangScript
 * note("c3 d3 e3 g3").lastOf("<2 4>", x => x.fast(2))  // alternating period
 * ```
 * @category conditional
 * @tags lastOf, conditional, cycle, periodic, transform
 */
@StrudelDsl
fun StrudelPattern.lastOf(n: PatternLike, transform: PatternMapperFn): StrudelPattern =
    this._lastOf(listOf(n, transform).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then applies [transform] on the last of every [n] cycles.
 *
 * @param n How many cycles make one period. The transform fires on the last of these.
 * @param transform The function to apply on the last cycle of each period.
 * @return A new pattern that applies [transform] at the end of each period.
 *
 * ```KlangScript
 * "c3 d3 e3 g3".lastOf(4, x => x.rev()).note()  // reverse on the 4th of every 4 cycles
 * ```
 */
@StrudelDsl
fun String.lastOf(n: PatternLike, transform: PatternMapperFn): StrudelPattern =
    this._lastOf(listOf(n, transform).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that applies [transform] on the **last** cycle of every [n] cycles.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 *
 * @param n How many cycles make one period. The transform fires on the last of these.
 * @param transform The function to apply on the last cycle of each period.
 * @return A [PatternMapperFn] that applies [transform] at the end of each period.
 *
 * ```KlangScript
 * note("c3 d3 e3 g3").apply(lastOf(4, x => x.rev()))  // reverse on the 4th of every 4 cycles
 * ```
 *
 * ```KlangScript
 * note("c3 e3 g3").firstOf(3, lastOf(2, x => x.fast(2)))  // nested periodic transforms
 * ```
 * @category conditional
 * @tags lastOf, conditional, cycle, periodic, transform
 */
@StrudelDsl
fun lastOf(n: PatternLike, transform: PatternMapperFn): PatternMapperFn =
    _lastOf(listOf(n, transform).asStrudelDslArgs())

// -- when() -----------------------------------------------------------------------------------------------------------

internal val _when by dslPatternMapper { args, callInfo -> { p -> p._when(args, callInfo) } }
internal val StrudelPattern._when by dslPatternExtension { p, args, _ ->
    val condition = args.getOrNull(0)?.toPattern() ?: return@dslPatternExtension p
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }

    // The true part: events where the condition is sampled as truthy.
    val truePart = object : StrudelPattern by p {
        override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
            return p.queryArcContextual(from, to, ctx).filter { event ->
                // Keep if condition is missing OR if the found event is not truthy
                condition.sampleAt(event.part.begin, ctx)?.data?.isTruthy() ?: false
            }
        }
    }

    // The false part: events where the condition is NOT truthy (falsy OR silent).
    // We implement this as a custom pattern to ensure complete coverage of the source pattern.
    val falsePart = object : StrudelPattern by p {
        override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
            return p.queryArcContextual(from, to, ctx).filter { event ->
                // Keep if condition is missing OR if the found event is not truthy
                condition.sampleAt(event.part.begin, ctx)?.data?.isNotTruthy() ?: true
            }
        }
    }

    // Apply transform only to the 'true' part and stack with the complementary 'false' part
    stack(transform(truePart), falsePart)
}

internal val String._when by dslStringExtension { p, args, callInfo -> p._when(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c d e f").when(pure(1).struct("t ~ t ~"), x => x.add(12))
 * ```
 *
 * ```KlangScript
 * s("bd sd").when("<1 0>", x => x.fast(2))
 * ```
 * @category conditional
 * @tags when, conditional, binary, gate, transform
 */
@StrudelDsl
fun StrudelPattern.`when`(condition: PatternLike, transform: PatternMapperFn): StrudelPattern =
    this._when(listOf(condition, transform).asStrudelDslArgs())

/**
 * Parses this string as a pattern, then applies [transform] whenever [condition] is truthy.
 *
 * @param condition A pattern whose values determine when to apply [transform].
 *   Zero is falsy; any other value is truthy.
 * @param transform The function to apply when [condition] is truthy.
 * @return A new pattern that conditionally applies [transform].
 *
 * ```KlangScript
 * "c d e f".when("1 0 1 0", x => x.add(12)).note()
 * ```
 */
@StrudelDsl
fun String.`when`(condition: PatternLike, transform: PatternMapperFn): StrudelPattern =
    this._when(listOf(condition, transform).asStrudelDslArgs())

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
 * ```KlangScript
 * note("c d e f").apply(when("1 0 1 0", x => x.add(12)))
 * ```
 *
 * ```KlangScript
 * note("c d e f").firstOf(4, when("1 0 1 0", x => x.add(12)))
 * ```
 * @category conditional
 * @tags when, conditional, binary, gate, transform
 */
@StrudelDsl
fun `when`(condition: PatternLike, transform: PatternMapperFn): PatternMapperFn =
    _when(listOf(condition, transform).asStrudelDslArgs())
