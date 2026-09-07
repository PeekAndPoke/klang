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
import io.peekandpoke.klang.sprudel._lift
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.DegradePattern

// -- degradeBy() ------------------------------------------------------------------------------------------------------

private fun applyDegradeBy(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // degradeBy(x) is just degradeByWith(rand, x)
    return applyDegradeByWith(pattern, listOf(SprudelDslArg.of(rand)) + args)
}

/**
 * Randomly removes events from the pattern with the given probability.
 *
 * `degradeBy(x)` keeps each event with probability `(1 - x)`. At `0` nothing is removed;
 * at `1` every event is removed. The randomness is deterministic per cycle (seeded).
 *
 * @param prob Removal probability in [0, 1]; 0 = keep all, 1 = remove all.
 * @return A pattern with events randomly removed at the given probability.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").degradeBy(0.3)     // ~30% of hits are silenced randomly
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").degradeBy(0.5)      // roughly half the notes play each cycle
 * ```
 *
 * @category random
 * @tags degradeBy, random, remove, probability, drop
 */
@KlangScript.Function
fun SprudelPattern.degradeBy(prob: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyDegradeBy(this, listOf(prob).asSprudelDslArgs(callInfo))

/** Randomly removes events with the given probability (0 = none removed, 1 = all removed). */
@KlangScript.Function
fun String.degradeBy(prob: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).degradeBy(prob, callInfo)

/**
 * Returns a [PatternMapperFn] that randomly removes events with the given probability.
 *
 * @param prob Removal probability in [0, 1].
 * @return A [PatternMapperFn] that drops events randomly at the given probability.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").apply(degradeBy(0.3))     // ~30% of hits silenced via mapper
 * ```
 *
 * @category random
 * @tags degradeBy, random, remove, probability, drop
 */
@KlangScript.Function
fun degradeBy(prob: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.degradeBy(prob, callInfo) }

/** Chains a degradeBy onto this [PatternMapperFn]; randomly removes events at the given probability. */
@KlangScript.Function
fun PatternMapperFn.degradeBy(prob: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.degradeBy(prob, callInfo) }

// -- degrade() --------------------------------------------------------------------------------------------------------

/**
 * Randomly removes events with a 50% probability. Shorthand for `degradeBy(0.5)`.
 *
 * @param prob Removal probability; defaults to `0.5`.
 * @return A pattern with roughly half its events removed each cycle.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").degrade()          // ~half the events play per cycle
 * ```
 *
 * @category random
 * @tags degrade, degradeBy, random, remove, probability
 */
@KlangScript.Function
fun SprudelPattern.degrade(prob: PatternLike = 0.5, callInfo: CallInfo? = null): SprudelPattern =
    applyDegradeBy(this, listOf(prob).asSprudelDslArgs(callInfo))

/** Randomly removes events with a 50% probability. Shorthand for `degradeBy(0.5)`. */
@KlangScript.Function
fun String.degrade(prob: PatternLike = 0.5, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).degrade(prob, callInfo)

/**
 * Returns a [PatternMapperFn] that randomly removes events at the given probability (default 0.5).
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").apply(degrade())   // ~half the events play via mapper
 * ```
 *
 * @category random
 * @tags degrade, degradeBy, random, remove, probability
 */
@KlangScript.Function
fun degrade(prob: PatternLike = 0.5, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.degrade(prob, callInfo) }

/** Chains a degrade onto this [PatternMapperFn] with the given probability (default 0.5). */
@KlangScript.Function
fun PatternMapperFn.degrade(prob: PatternLike = 0.5, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.degrade(prob, callInfo) }

// -- degradeByWith() --------------------------------------------------------------------------------------------------

private fun applyDegradeByWith(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // Keeps each event where the randomness source exceeds the threshold x:
    //   degradeByWith(rand, 0.2) -> keep where rand > 0.2 (~80% kept)
    //   degradeByWith(rand, 0.5) -> keep where rand > 0.5 (~50% kept)
    //   degradeByWith(rand, 0.8) -> keep where rand > 0.8 (~20% kept)
    val withPat = args.getOrNull(0)?.toPattern() ?: return pattern
    val xPat = (args.getOrNull(1) ?: SprudelDslArg.of(0.5)).toPattern()

    return pattern._lift(xPat) { x, src ->
        DegradePattern(source = src, randomness = withPat, threshold = x, keepStrictlyAbove = true)
    }
}

/**
 * Randomly removes events using a custom random-value pattern as the randomness source.
 *
 * `degradeByWith(withPat, x)` keeps an event when `withPat > x` at that point in time.
 * By supplying your own randomness source (e.g. `rand.segment(1)` for cycle-level decisions)
 * you can control exactly how the randomness is sampled.
 *
 * @param withPat Custom random-value pattern (values in [0, 1]).
 * @param prob Threshold; event kept when `withPat > prob`.
 * @return A pattern with events removed where the custom source falls below the threshold.
 *
 * ```KlangScript(Playable)
 * s("bd*8").degradeByWith(rand.segment(1), 0.5)   // whole cycle either plays or drops
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*16").degradeByWith(sine.range(0, 1), 0.5) // sine-wave controlled removal
 * ```
 *
 * @category random
 * @tags degradeByWith, random, remove, custom, probability
 */
@KlangScript.Function
fun SprudelPattern.degradeByWith(withPat: PatternLike, prob: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyDegradeByWith(this, listOf(withPat, prob).asSprudelDslArgs(callInfo))

/** Randomly removes events using a custom random-value pattern as the randomness source. */
@KlangScript.Function
fun String.degradeByWith(withPat: PatternLike, prob: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).degradeByWith(withPat, prob, callInfo)

/**
 * Returns a [PatternMapperFn] that removes events using a custom random-value pattern.
 *
 * @param withPat Custom random-value pattern (values in [0, 1]).
 * @param prob Threshold; event kept when `withPat > prob`.
 * @return A [PatternMapperFn] that filters events via the custom randomness source.
 *
 * ```KlangScript(Playable)
 * s("bd*8").apply(degradeByWith(rand.segment(1), 0.5))  // cycle-level drops via mapper
 * ```
 *
 * @category random
 * @tags degradeByWith, random, remove, custom, probability
 */
@KlangScript.Function
fun degradeByWith(withPat: PatternLike, prob: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.degradeByWith(withPat, prob, callInfo) }

/** Chains a degradeByWith onto this [PatternMapperFn] using a custom randomness source. */
@KlangScript.Function
fun PatternMapperFn.degradeByWith(withPat: PatternLike, prob: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.degradeByWith(withPat, prob, callInfo) }

// -- undegradeBy() ----------------------------------------------------------------------------------------------------

private fun applyUndegradeBy(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // undegradeBy(x) is just undegradeByWith(rand, x)
    // undegradeBy(0) = 100% removal, undegradeBy(1) = 0% removal
    return applyUndegradeByWith(pattern, listOf(SprudelDslArg.of(rand)) + args)
}

/**
 * Inverse of `degradeBy`: keeps events that `degradeBy` would remove, and vice versa.
 *
 * `undegradeBy(x)` keeps an event with probability `x` (0 = nothing kept, 1 = everything kept).
 * When paired with `degradeBy` using the same seed, the two are perfectly complementary.
 *
 * @param prob Keep probability in [0, 1]; 0 = remove all, 1 = keep all.
 * @return A pattern keeping only the events that `degradeBy(1-x)` would remove.
 *
 * ```KlangScript(Playable)
 * s("hh*8").undegradeBy(0.2)                   // only ~20% of hits play
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*10").layer(
 *   x => x.degradeBy(0.2).pan(0),
 *   x => x.undegradeBy(0.8).pan(1)
 * )                                             // complementary panning layers
 * ```
 *
 * @category random
 * @tags undegradeBy, random, inverse, keep, probability
 */
@KlangScript.Function
fun SprudelPattern.undegradeBy(prob: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyUndegradeBy(this, listOf(prob).asSprudelDslArgs(callInfo))

/** Inverse of `degradeBy`: keeps events that `degradeBy` would remove (0 = none, 1 = all). */
@KlangScript.Function
fun String.undegradeBy(prob: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).undegradeBy(prob, callInfo)

/**
 * Returns a [PatternMapperFn] that keeps only events `degradeBy` would remove.
 *
 * @param prob Keep probability in [0, 1].
 * @return A [PatternMapperFn] that is the complement of [degradeBy].
 *
 * ```KlangScript(Playable)
 * s("hh*8").apply(undegradeBy(0.2))    // ~20% of hits play via mapper
 * ```
 *
 * @category random
 * @tags undegradeBy, random, inverse, keep, probability
 */
@KlangScript.Function
fun undegradeBy(prob: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.undegradeBy(prob, callInfo) }

/** Chains an undegradeBy onto this [PatternMapperFn]; keeps events at the given probability. */
@KlangScript.Function
fun PatternMapperFn.undegradeBy(prob: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.undegradeBy(prob, callInfo) }

// -- undegradeByWith() ------------------------------------------------------------------------------------------------

private fun applyUndegradeByWith(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // Inverse of degradeByWith: keep each event where the randomness source reaches (1 - x):
    //   undegradeByWith(rand, 0.1) -> keep where rand >= 0.9 (~10% kept)
    //   undegradeByWith(rand, 0.5) -> keep where rand >= 0.5 (~50% kept)
    //   undegradeByWith(rand, 1.0) -> keep where rand >= 0.0 (~100% kept)
    val withPat = args.getOrNull(0)?.toPattern() ?: return pattern
    val xPat = (args.getOrNull(1) ?: SprudelDslArg.of(0.5)).toPattern()

    return pattern._lift(xPat) { x, src ->
        DegradePattern(source = src, randomness = withPat, threshold = 1 - x, keepStrictlyAbove = false)
    }
}

/**
 * Inverse of `degradeByWith` using a custom random-value pattern.
 *
 * Keeps an event when `withPat >= (1 - x)`, which is complementary to `degradeByWith`.
 *
 * @param withPat Custom random-value pattern (values in [0, 1]).
 * @param prob Threshold; event kept when `withPat >= (1 - prob)`.
 * @return A pattern that is the complement of `degradeByWith(withPat, prob)`.
 *
 * ```KlangScript(Playable)
 * s("bd*8").undegradeByWith(randCycle, 0.5)   // cycle-level complement of degradeByWith
 * ```
 *
 * @category random
 * @tags undegradeByWith, random, inverse, custom, probability
 */
@KlangScript.Function
fun SprudelPattern.undegradeByWith(withPat: PatternLike, prob: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyUndegradeByWith(this, listOf(withPat, prob).asSprudelDslArgs(callInfo))

/** Inverse of `degradeByWith` using a custom random-value pattern. */
@KlangScript.Function
fun String.undegradeByWith(withPat: PatternLike, prob: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).undegradeByWith(withPat, prob, callInfo)

/**
 * Returns a [PatternMapperFn] that is the inverse of `degradeByWith`.
 *
 * @param withPat Custom random-value pattern (values in [0, 1]).
 * @param prob Threshold; event kept when `withPat >= (1 - prob)`.
 * @return A [PatternMapperFn] that is the complement of [degradeByWith].
 *
 * ```KlangScript(Playable)
 * s("bd*8").apply(undegradeByWith(randCycle, 0.5))  // cycle-level complement via mapper
 * ```
 *
 * @category random
 * @tags undegradeByWith, random, inverse, custom, probability
 */
@KlangScript.Function
fun undegradeByWith(withPat: PatternLike, prob: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.undegradeByWith(withPat, prob, callInfo) }

/** Chains an undegradeByWith onto this [PatternMapperFn] using a custom randomness source. */
@KlangScript.Function
fun PatternMapperFn.undegradeByWith(withPat: PatternLike, prob: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.undegradeByWith(withPat, prob, callInfo) }

// -- undegrade() ------------------------------------------------------------------------------------------------------

/**
 * Keeps events with 50% probability. Inverse of `degrade` — shorthand for `undegradeBy(0.5)`.
 *
 * @return A pattern keeping roughly half its events each cycle (complement of [degrade]).
 *
 * ```KlangScript(Playable)
 * s("hh*8").undegrade()               // ~half the events play (complement of degrade)
 * ```
 *
 * @category random
 * @tags undegrade, undegradeBy, random, inverse, probability
 */
@KlangScript.Function
fun SprudelPattern.undegrade(callInfo: CallInfo? = null): SprudelPattern =
    applyUndegradeBy(this, emptyList<Any>().asSprudelDslArgs(callInfo))

/** Keeps events with 50% probability. Shorthand for `undegradeBy(0.5)`. */
@KlangScript.Function
fun String.undegrade(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).undegrade(callInfo)

/**
 * Returns a [PatternMapperFn] that keeps events with 50% probability (inverse of [degrade]).
 *
 * ```KlangScript(Playable)
 * s("hh*8").apply(undegrade())        // ~half the events play via mapper
 * ```
 *
 * @category random
 * @tags undegrade, undegradeBy, random, inverse, probability
 */
@KlangScript.Function
fun undegrade(callInfo: CallInfo? = null): PatternMapperFn = { p -> p.undegrade(callInfo) }

/** Chains an undegrade (50% keep) onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.undegrade(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.undegrade(callInfo) }

