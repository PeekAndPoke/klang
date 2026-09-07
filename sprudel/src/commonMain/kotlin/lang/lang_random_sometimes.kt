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

// -- sometimesBy() ----------------------------------------------------------------------------------------------------

/**
 * Randomly applies the given function by the given probability.
 */
private fun applySometimesBy(
    pattern: SprudelPattern,
    args: List<SprudelDslArg<Any?>>,
    seedByCycle: Boolean = false,
): SprudelPattern {
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }

    // Use 'when' with comparison operators for cleaner implementation
    // Apply transform when random < probability
    return pattern._innerJoin(args.take(1)) { src, probValue ->
        val x = probValue?.asDouble ?: 0.5

        // Choose rand or randCycle based on seedByCycle
        val randomPattern = if (seedByCycle) randCycle else rand

        // Apply transform when random < x, otherwise keep original
        src.`when`(randomPattern.lt(x), transform)
    }
}

/**
 * Applies `transform` to each event independently with the given probability.
 *
 * `sometimesBy(p, fn)` randomly decides per-event: with probability `p` the event is
 * transformed by `fn`; otherwise it plays unmodified. For cycle-level decisions use [someCyclesBy].
 *
 * @param prob Probability in [0, 1] that the transform is applied to each event.
 * @param mapper The transformation to apply probabilistically.
 * @return A pattern with `mapper` applied to each event at the given probability.
 *
 * ```KlangScript(Playable)
 * s("hh*8").sometimesBy(0.4, x => x.speed(0.5))   // 40% of hits at half speed
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").sometimesBy(0.5, x => x.transpose(12)) // 50% of notes an octave higher
 * ```
 *
 * @category random
 * @tags sometimesBy, random, probability, conditional, transform
 */
@KlangScript.Function
fun SprudelPattern.sometimesBy(prob: PatternLike, callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    applySometimesBy(this, listOf(prob, mapper).asSprudelDslArgs(callInfo))

/** Applies `transform` to each event independently with the given probability. */
@KlangScript.Function
fun String.sometimesBy(prob: PatternLike, callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sometimesBy(prob, callInfo, mapper)

/**
 * Returns a [PatternMapperFn] that applies `mapper` to each event at the given probability.
 *
 * @param prob Probability in [0, 1] that the transform is applied to each event.
 * @param mapper The transformation to apply probabilistically.
 * @return A [PatternMapperFn] that applies the mapper at the given probability.
 *
 * ```KlangScript(Playable)
 * s("hh*8").apply(sometimesBy(0.4, x => x.speed(0.5)))   // via mapper
 * ```
 *
 * @category random
 * @tags sometimesBy, random, probability, conditional, transform
 */
@KlangScript.Function
fun sometimesBy(prob: PatternLike, callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.sometimesBy(prob, callInfo, mapper) }

/** Chains a sometimesBy onto this [PatternMapperFn]; applies inner mapper at the given probability. */
@KlangScript.Function
fun PatternMapperFn.sometimesBy(prob: PatternLike, callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    this.chain { p -> p.sometimesBy(prob, callInfo, mapper) }

// -- sometimes() ------------------------------------------------------------------------------------------------------

private fun applySometimes(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.5
    return pattern.`when`(rand.lt(x), transform)
}

/**
 * Applies `transform` with a 50% chance per event. Shorthand for `sometimesBy(0.5, fn)`.
 *
 * @param mapper The transformation to apply 50% of the time.
 * @return A pattern with `mapper` applied to roughly half the events.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").sometimes(x => x.speed(2))  // half the hits at double speed
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").sometimes(x => x.transpose(7))     // 50% of notes shifted a fifth up
 * ```
 *
 * @category random
 * @tags sometimes, random, probability, conditional, transform
 */
@KlangScript.Function
fun SprudelPattern.sometimes(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    applySometimes(this, listOf(mapper).asSprudelDslArgs(callInfo))

/** Applies `transform` with a 50% chance per event. Shorthand for `sometimesBy(0.5, fn)`. */
@KlangScript.Function
fun String.sometimes(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sometimes(callInfo, mapper)

/** Returns a [PatternMapperFn] that applies `mapper` with 50% probability per event. */
@KlangScript.Function
fun sometimes(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.sometimes(callInfo, mapper) }

/** Chains a sometimes (50% per event) onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.sometimes(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    this.chain { p -> p.sometimes(callInfo, mapper) }

// -- often() ----------------------------------------------------------------------------------------------------------

private fun applyOften(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.75
    return pattern.`when`(rand.lt(x), transform)
}

/**
 * Applies `transform` with 75% probability per event. Shorthand for `sometimesBy(0.75, fn)`.
 *
 * @param mapper The transformation to apply 75% of the time.
 * @return A pattern with `mapper` applied to ~75% of events.
 *
 * ```KlangScript(Playable)
 * s("hh*8").often(x => x.gain(0.5))    // 75% of hi-hats at half gain
 * ```
 *
 * @category random
 * @tags often, random, probability, conditional, transform
 */
@KlangScript.Function
fun SprudelPattern.often(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    applyOften(this, listOf(mapper).asSprudelDslArgs(callInfo))

/** Applies `transform` with 75% probability per event. Shorthand for `sometimesBy(0.75, fn)`. */
@KlangScript.Function
fun String.often(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).often(callInfo, mapper)

/** Returns a [PatternMapperFn] that applies `mapper` with 75% probability per event. */
@KlangScript.Function
fun often(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.often(callInfo, mapper) }

/** Chains an often (75% per event) onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.often(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    this.chain { p -> p.often(callInfo, mapper) }

// -- rarely() ---------------------------------------------------------------------------------------------------------

private fun applyRarely(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.25
    return pattern.`when`(rand.lt(x), transform)
}

/**
 * Applies `transform` with 25% probability per event. Shorthand for `sometimesBy(0.25, fn)`.
 *
 * @param mapper The transformation to apply 25% of the time.
 * @return A pattern with `mapper` applied to ~25% of events.
 *
 * ```KlangScript(Playable)
 * note("c d e f").rarely(x => x.transpose(12))   // only 1 in 4 notes shifted an octave
 * ```
 *
 * @category random
 * @tags rarely, random, probability, conditional, transform
 */
@KlangScript.Function
fun SprudelPattern.rarely(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    applyRarely(this, listOf(mapper).asSprudelDslArgs(callInfo))

/** Applies `transform` with 25% probability per event. Shorthand for `sometimesBy(0.25, fn)`. */
@KlangScript.Function
fun String.rarely(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).rarely(callInfo, mapper)

/** Returns a [PatternMapperFn] that applies `mapper` with 25% probability per event. */
@KlangScript.Function
fun rarely(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.rarely(callInfo, mapper) }

/** Chains a rarely (25% per event) onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.rarely(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    this.chain { p -> p.rarely(callInfo, mapper) }

// -- almostNever() ----------------------------------------------------------------------------------------------------

private fun applyAlmostNever(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.1
    return pattern.`when`(rand.lt(x), transform)
}

/**
 * Applies `transform` with 10% probability per event. Shorthand for `sometimesBy(0.1, fn)`.
 *
 * @param mapper The transformation to apply 10% of the time.
 * @return A pattern with `mapper` applied to ~10% of events.
 *
 * ```KlangScript(Playable)
 * s("bd hh sd oh").almostNever(x => x.speed(0.5))   // very rarely at half sample speed
 * ```
 *
 * @category random
 * @tags almostNever, random, probability, conditional, rare
 */
@KlangScript.Function
fun SprudelPattern.almostNever(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    applyAlmostNever(this, listOf(mapper).asSprudelDslArgs(callInfo))

/** Applies `transform` with 10% probability per event. Shorthand for `sometimesBy(0.1, fn)`. */
@KlangScript.Function
fun String.almostNever(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).almostNever(callInfo, mapper)

/** Returns a [PatternMapperFn] that applies `mapper` with 10% probability per event. */
@KlangScript.Function
fun almostNever(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.almostNever(callInfo, mapper) }

/** Chains an almostNever (10% per event) onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.almostNever(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    this.chain { p -> p.almostNever(callInfo, mapper) }

// -- almostAlways() ---------------------------------------------------------------------------------------------------

private fun applyAlmostAlways(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.9
    return pattern.`when`(rand.lt(x), transform)
}

/**
 * Applies `transform` with 90% probability per event. Shorthand for `sometimesBy(0.9, fn)`.
 *
 * @param mapper The transformation to apply 90% of the time.
 * @return A pattern with `mapper` applied to ~90% of events.
 *
 * ```KlangScript(Playable)
 * s("hh*8").almostAlways(x => x.gain(0.2)).seed(3)   // 90% of hi-hats at 20% gain
 * ```
 *
 * @category random
 * @tags almostAlways, random, probability, conditional, frequent
 */
@KlangScript.Function
fun SprudelPattern.almostAlways(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    applyAlmostAlways(this, listOf(mapper).asSprudelDslArgs(callInfo))

/** Applies `transform` with 90% probability per event. Shorthand for `sometimesBy(0.9, fn)`. */
@KlangScript.Function
fun String.almostAlways(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).almostAlways(callInfo, mapper)

/** Returns a [PatternMapperFn] that applies `mapper` with 90% probability per event. */
@KlangScript.Function
fun almostAlways(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.almostAlways(callInfo, mapper) }

/** Chains an almostAlways (90% per event) onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.almostAlways(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    this.chain { p -> p.almostAlways(callInfo, mapper) }

// -- never() ----------------------------------------------------------------------------------------------------------

/**
 * Never applies `transform` — the pattern passes through unchanged. Shorthand for
 * `sometimesBy(0, fn)`. Useful as a placeholder during development.
 *
 * @param mapper The transformation — ignored; pattern passes through unchanged.
 * @return The unmodified source pattern.
 *
 * ```KlangScript(Playable)
 * s("bd sd").never(x => x.rev())       // rev is never applied; pattern plays normally
 * ```
 *
 * @category random
 * @tags never, noop, placeholder, conditional
 */
@KlangScript.Function
fun SprudelPattern.never(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern = this

/** Never applies `transform` — the pattern passes through unchanged. */
@KlangScript.Function
fun String.never(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).never(callInfo, mapper)

/** Returns a [PatternMapperFn] that never applies `mapper` — source passes through unchanged. */
@KlangScript.Function
fun never(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.never(callInfo, mapper) }

/** Chains a never (no-op) onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.never(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    this.chain { p -> p.never(callInfo, mapper) }

// -- always() ---------------------------------------------------------------------------------------------------------

private fun applyAlways(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val func = args.getOrNull(0).toPatternMapper()
    return func?.invoke(pattern) ?: pattern
}

/**
 * Always applies `transform` — shorthand for `sometimesBy(1, fn)`. Equivalent to calling
 * `fn` directly, but useful for consistency when toggling between probability variants.
 *
 * @param mapper The transformation to always apply.
 * @return A pattern with `mapper` unconditionally applied.
 *
 * ```KlangScript(Playable)
 * s("bd hh hh sd hh hh").always(x => x.rev())    // rev is always applied
 * ```
 *
 * @category random
 * @tags always, unconditional, transform, conditional
 */
@KlangScript.Function
fun SprudelPattern.always(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    applyAlways(this, listOf(mapper).asSprudelDslArgs(callInfo))

/** Always applies `transform`. Shorthand for `sometimesBy(1, fn)`. */
@KlangScript.Function
fun String.always(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).always(callInfo, mapper)

/** Returns a [PatternMapperFn] that always applies `mapper` unconditionally. */
@KlangScript.Function
fun always(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.always(callInfo, mapper) }

/** Chains an always (unconditional apply) onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.always(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    this.chain { p -> p.always(callInfo, mapper) }

// -- someCyclesBy() ---------------------------------------------------------------------------------------------------

private fun applySomeCyclesBy(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // Delegate to applySometimesBy with seedByCycle = true
    return applySometimesBy(pattern = pattern, args = args, seedByCycle = true)
}

/**
 * Applies `transform` with the given probability, decided once per cycle (not per event).
 *
 * Like [sometimesBy], but the random decision is made at cycle granularity: all events in
 * the same cycle are either all transformed or all untouched.
 *
 * @param prob Probability in [0, 1] that the entire cycle is transformed.
 * @param mapper The transformation to apply to the whole cycle.
 * @return A pattern with `mapper` applied to ~`prob` fraction of cycles.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").someCyclesBy(0.5, x => x.rev())  // entire cycle reversed ~50% of cycles
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").someCyclesBy(0.3, x => x.fast(2)) // ~30% of cycles play at double speed
 * ```
 *
 * @category random
 * @tags someCyclesBy, random, cycle, probability, conditional
 */
@KlangScript.Function
fun SprudelPattern.someCyclesBy(prob: PatternLike, callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    applySomeCyclesBy(this, listOf(prob, mapper).asSprudelDslArgs(callInfo))

/** Applies `transform` with the given probability, decided once per cycle (not per event). */
@KlangScript.Function
fun String.someCyclesBy(prob: PatternLike, callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).someCyclesBy(prob, callInfo, mapper)

/**
 * Returns a [PatternMapperFn] that applies `mapper` with the given probability per cycle.
 *
 * @param prob Probability in [0, 1] that the entire cycle is transformed.
 * @param mapper The transformation to apply to the whole cycle.
 * @return A [PatternMapperFn] that applies the mapper to ~`prob` fraction of cycles.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").apply(someCyclesBy(0.5, x => x.rev()))  // via mapper
 * ```
 *
 * @category random
 * @tags someCyclesBy, random, cycle, probability, conditional
 */
@KlangScript.Function
fun someCyclesBy(prob: PatternLike, callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.someCyclesBy(prob, callInfo, mapper) }

/** Chains a someCyclesBy onto this [PatternMapperFn]; applies inner mapper at the given per-cycle probability. */
@KlangScript.Function
fun PatternMapperFn.someCyclesBy(prob: PatternLike, callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    this.chain { p -> p.someCyclesBy(prob, callInfo, mapper) }

// -- someCycles() -----------------------------------------------------------------------------------------------------

/**
 * Applies `transform` with 50% probability per cycle. Shorthand for `someCyclesBy(0.5, fn)`.
 *
 * @param mapper The transformation to apply to the whole cycle ~50% of the time.
 * @return A pattern with `mapper` applied to roughly half the cycles.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").someCycles(x => x.rev())   // reverse the entire cycle ~50% of the time
 * ```
 *
 * @category random
 * @tags someCycles, someCyclesBy, random, cycle, probability
 */
@KlangScript.Function
fun SprudelPattern.someCycles(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    applySomeCyclesBy(this, listOf(mapper).asSprudelDslArgs(callInfo))

/** Applies `transform` with 50% probability per cycle. Shorthand for `someCyclesBy(0.5, fn)`. */
@KlangScript.Function
fun String.someCycles(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).someCycles(callInfo, mapper)

/** Returns a [PatternMapperFn] that applies `mapper` with 50% probability per cycle. */
@KlangScript.Function
fun someCycles(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.someCycles(callInfo, mapper) }

/** Chains a someCycles (50% per cycle) onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.someCycles(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    this.chain { p -> p.someCycles(callInfo, mapper) }

