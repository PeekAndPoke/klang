/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._innerJoin
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.SwingPattern

// -- inside() ---------------------------------------------------------------------------------------------------------

private fun applyInside(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) return pattern

    val factorArg = args[0]
    val func = args[1].toPatternMapper() ?: return pattern

    // Use apply functions directly to avoid double-wrapping SprudelDslArg
    val slowed = applySlow(pattern, listOf(factorArg))
    val transformed = func(slowed)

    return applyFast(transformed, listOf(factorArg))
}

/**
 * Applies a transformation inside a zoomed-in view of the cycle.
 *
 * Slows the pattern by `factor`, applies `transform`, then speeds it back up to the original
 * tempo. The net effect is that `transform` sees a pattern spread over `factor` cycles,
 * allowing operations like `rev()` to work across a larger musical phrase while the result
 * still fits in one cycle.
 *
 * @param factor The zoom-in amount. The pattern is slowed by this factor before the transform, then sped back up.
 * @param transform Transformation to apply while the pattern is spread over `factor` cycles.
 *
 * ```KlangScript(Playable)
 * note("0 1 2 3").inside(4, x => x.rev())       // reverse across 4-cycle span, then compress back
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").inside(2, x => x.slow(2))    // double-slow inside = no net change in tempo
 * ```
 *
 * @category tempo
 * @tags inside, transform, zoom, slow, fast
 */
@KlangScript.Function
fun SprudelPattern.inside(factor: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyInside(this, listOf(factor, transform).asSprudelDslArgs(callInfo))

/** Applies a transformation inside a zoomed-in view of the cycle. */
@KlangScript.Function
fun String.inside(factor: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).inside(factor, transform, callInfo)

/** Returns a [PatternMapperFn] that applies a transformation inside a zoomed-in view of the cycle. */
@KlangScript.Function
fun inside(factor: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.inside(factor, transform, callInfo) }

/** Chains an inside operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.inside(factor: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.inside(factor, transform, callInfo) }

// -- outside() --------------------------------------------------------------------------------------------------------

private fun applyOutside(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) {
        return pattern
    }

    // TODO: support control pattern for "factor"
    val factor = args[0].value?.asDoubleOrNull() ?: 1.0

    val func = args[1].toPatternMapper() ?: return pattern

    val sped = applyFast(pattern, listOf(SprudelDslArg.of(factor)))
    val transformed = func(sped)

    return applySlow(transformed, listOf(SprudelDslArg.of(factor)))
}

/**
 * Applies a transformation outside the current cycle, across a wider temporal context.
 *
 * Speeds the pattern by `factor`, applies `transform`, then slows it back down. The net effect
 * is that `transform` sees only `1/factor` of the original pattern per cycle, allowing
 * operations like `rev()` to work on a globally coarser time scale.
 *
 * @param factor The zoom-out amount. The pattern is sped up by this factor before the transform, then slowed back down.
 * @param transform Transformation to apply while the pattern covers only `1/factor` of the original cycle.
 *
 * ```KlangScript(Playable)
 * note("0 1 2 3").outside(4, x => x.rev())      // reverse on 1/4 speed, then speed back up
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").outside(2, x => x.fast(2))   // double-fast outside = no net change in tempo
 * ```
 *
 * @category tempo
 * @tags outside, transform, zoom, fast, slow
 */
@KlangScript.Function
fun SprudelPattern.outside(factor: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyOutside(this, listOf(factor, transform).asSprudelDslArgs(callInfo))

/** Applies a transformation outside the current cycle on this string pattern. */
@KlangScript.Function
fun String.outside(factor: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).outside(factor, transform, callInfo)

/** Returns a [PatternMapperFn] that applies a transformation outside the current cycle. */
@KlangScript.Function
fun outside(factor: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.outside(factor, transform, callInfo) }

/** Chains an outside operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.outside(factor: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.outside(factor, transform, callInfo) }

// -- swingBy() --------------------------------------------------------------------------------------------------------

private fun applySwingBy(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return pattern._innerJoin(args) { source, swing, n ->
        val swingValue = swing?.asDouble ?: return@_innerJoin silence
        val nVal = n?.asDouble ?: 1.0

        SwingPattern(source = source, swing = swingValue, n = nVal)
    }
}

/**
 * Creates a swing or shuffle rhythm by adjusting event timing and duration within subdivisions.
 *
 * Divides each cycle into `n` subdivisions. Within each subdivision, events are split into
 * two groups — first half gets `(1 + swing) / 2` of the slot duration, second half gets
 * `(1 - swing) / 2`. This creates a natural rhythmic feel without overlapping events.
 *
 * - Positive swing (e.g. 1/3): "long-short" pattern — classic jazz swing
 * - Negative swing (e.g. -1/3): "short-long" pattern — reverse swing
 * - Zero swing: equal durations (no effect)
 *
 * @param swing Swing amount in the range -1 to 1.
 * @param n Number of subdivisions per cycle.
 *
 * ```KlangScript(Playable)
 * s("hh*8").swingBy(1/3, 4)               // classic swing on hi-hats, 4 subdivisions
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").swingBy(-0.25, 2)       // reverse swing on notes, 2 subdivisions
 * ```
 *
 * @category tempo
 * @tags swingBy, swing, shuffle, rhythm, timing, groove
 */
@KlangScript.Function
fun SprudelPattern.swingBy(swing: PatternLike, n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applySwingBy(this, listOf(swing, n).asSprudelDslArgs(callInfo))

/** Creates a swing rhythm with custom amount; see `swingBy` for details. */
@KlangScript.Function
fun String.swingBy(swing: PatternLike, n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).swingBy(swing, n, callInfo)

/** Returns a [PatternMapperFn] that creates a swing or shuffle rhythm. */
@KlangScript.Function
fun swingBy(swing: PatternLike, n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.swingBy(swing, n, callInfo) }

/** Chains a swingBy operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.swingBy(swing: PatternLike, n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.swingBy(swing, n, callInfo) }

// -- swing() ----------------------------------------------------------------------------------------------------------

private fun applySwing(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.isEmpty()) return pattern
    val nArg = args.getOrNull(0)
    // swing(n) = swingBy(1/3, n)
    return applySwingBy(pattern, listOf(SprudelDslArg.of(1.0 / 3.0), nArg ?: SprudelDslArg.of(1.0)))
}

/**
 * Shorthand for `swingBy(1/3, n)` — classic jazz swing feel.
 *
 * Applies a 1/3 swing amount across `n` subdivisions per cycle. Equivalent to
 * `swingBy(1/3, n)`, creating the characteristic "long-short" groove.
 *
 * @param n Number of subdivisions per cycle.
 *
 * ```KlangScript(Playable)
 * s("hh*8").swing(4)                  // classic swing on hi-hats, 4 subdivisions
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f g a b c").swing(2)    // swing feel on a melody, 2 subdivisions
 * ```
 *
 * @category tempo
 * @tags swing, swingBy, shuffle, rhythm, timing, groove
 */
@KlangScript.Function
fun SprudelPattern.swing(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applySwing(this, listOf(n).asSprudelDslArgs(callInfo))

/** Shorthand for `swingBy(1/3, n)` — classic jazz swing feel. */
@KlangScript.Function
fun String.swing(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).swing(n, callInfo)

/** Returns a [PatternMapperFn] that applies classic jazz swing feel. */
@KlangScript.Function
fun swing(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.swing(n, callInfo) }

/** Chains a swing operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.swing(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.swing(n, callInfo) }
