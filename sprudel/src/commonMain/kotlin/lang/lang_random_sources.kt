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
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern
import io.peekandpoke.klang.sprudel.pattern.ContinuousPattern
import io.peekandpoke.klang.sprudel.pattern.ControlPattern
import io.peekandpoke.klang.sprudel.pattern.RandLPattern
import io.peekandpoke.klang.sprudel.pattern.RandrunPattern
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpret
import io.peekandpoke.klang.sprudel.pattern.SeedPattern
import io.peekandpoke.klang.sprudel.pattern.SequencePattern
import kotlin.math.floor

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all top-level vals (e.g. random constants) are eagerly evaluated.
 */
// -- Helpers ----------------------------------------------------------------------------------------------------------

private fun applyRandomSeed(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val seedArg = args.getOrNull(0) ?: return pattern
    val seedPattern = seedArg.toPattern()
    return SeedPattern(source = pattern, seedPattern = seedPattern)
}

// -- seed() -----------------------------------------------------------------------------------------------------------

/**
 * Sets the random seed for the pattern, making all random operations reproducible.
 *
 * All random-based functions (`rand`, `degradeBy`, `sometimes`, etc.) use the seed to generate
 * deterministic pseudo-random values. Calling `seed(n)` with the same `n` always produces the
 * same random sequence, which is useful for reproducible live performances.
 *
 * @param n Seed value — any integer-compatible pattern or literal.
 * @return A pattern whose random operations are pinned to the given seed.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh").degradeBy(0.3).seed(42)     // reproducible random removal
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").sometimes(x => x.rev()).seed(7)   // fixed random decisions
 * ```
 *
 * @alias withSeed
 * @category random
 * @tags seed, random, reproducible, deterministic
 */
@KlangScript.Function
fun SprudelPattern.seed(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyRandomSeed(this, listOf(n).asSprudelDslArgs(callInfo))

/** Sets the random seed for reproducible random operations on a string pattern. */
@KlangScript.Function
fun String.seed(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).seed(n, callInfo)

/**
 * Returns a [PatternMapperFn] that pins all random operations to the given seed.
 *
 * Apply using `.apply()` to make any random-based transformation reproducible.
 *
 * @param n Seed value — any integer-compatible pattern or literal.
 * @return A [PatternMapperFn] that sets the random seed on the source pattern.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh").apply(degradeBy(0.3).seed(42))   // chain seed onto a mapper
 * ```
 *
 * @alias withSeed
 * @category random
 * @tags seed, random, reproducible, deterministic
 */
@KlangScript.Function
fun seed(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.seed(n, callInfo) }

/** Chains a seed onto this [PatternMapperFn]; pins all random operations to the given seed value. */
@KlangScript.Function
fun PatternMapperFn.seed(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.seed(n, callInfo) }

// -- withSeed() -------------------------------------------------------------------------------------------------------

/**
 * Alias for [seed]. Sets the random seed for reproducible random operations.
 *
 * @param n Seed value — any integer-compatible pattern or literal.
 * @return A pattern whose random operations are pinned to the given seed.
 *
 * @alias seed
 * @category random
 * @tags withSeed, seed, random, reproducible
 */
@KlangScript.Function
fun SprudelPattern.withSeed(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyRandomSeed(this, listOf(n).asSprudelDslArgs(callInfo))

/** Alias for [seed] on a string pattern. */
@KlangScript.Function
fun String.withSeed(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).withSeed(n, callInfo)

/** Returns a [PatternMapperFn] — alias for [seed] — that pins all random operations to the given seed. */
@KlangScript.Function
fun withSeed(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.withSeed(n, callInfo) }

/** Chains a withSeed (alias for [seed]) onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.withSeed(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.withSeed(n, callInfo) }

// -- rand / rand2 / randCycle -----------------------------------------------------------------------------------------

/**
 * Continuous random pattern producing values in the range 0–1.
 *
 * `rand` generates a unique pseudo-random value for each point in time. Use `range()` to
 * re-map the output, `segment(n)` to discretise it into `n` steps per cycle, or pass it
 * directly to parameters that accept patterns.
 *
 * ```KlangScript(Playable)
 * s("hh*8").pan(rand)  // random panning for each hit
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").gain(rand.range(0.5, 1.0))  // random gain in 0.5–1.0
 * ```
 *
 * @category random
 * @tags rand, random, continuous, noise
 */
@KlangScript.Constant
val rand: SprudelPattern = ContinuousPattern { from, _, ctx ->
    ctx.getSeededRandom(from, "rand").nextDouble()
}

/**
 * Continuous random pattern producing values in the range -1–1 (bipolar).
 *
 * Equivalent to `rand.toBipolar()`. Useful for LFO-style modulation that oscillates
 * around zero (e.g., pitch detune, stereo panning centred at 0).
 *
 * ```KlangScript(Playable)
 * s("hh*8").pan(rand2)   // bipolar random panning around centre
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4*8").transpose(rand2.range(-2, 2))   // pitch wobble centred on the note
 * ```
 *
 * @category random
 * @tags rand2, random, bipolar, continuous, noise
 */
@KlangScript.Constant
val rand2: SprudelPattern = rand.toBipolar()

/**
 * Continuous random pattern that holds a constant value for each full cycle.
 *
 * All events within the same cycle get the same random value; adjacent cycles get
 * different values. Useful for cycle-level random decisions (e.g. `someCyclesBy`).
 * Equivalent to `rand.segment(1)`.
 *
 * ```KlangScript(Playable)
 * s("bd*8").degradeByWith(randCycle, 0.5)   // entire cycle either plays or drops
 * ```
 *
 * @category random
 * @tags randCycle, random, cycle, hold, step
 */
@KlangScript.Constant
val randCycle: SprudelPattern = ContinuousPattern { fromTime, _, ctx ->
    ctx.getSeededRandom(floor(fromTime), "randCycle").nextDouble()
}

// -- brand() / brandBy() ----------------------------------------------------------------------------------------------

private fun applyBrandBy(args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val probArg = args.getOrNull(0)
    val probVal = probArg?.value

    val probPattern: SprudelPattern = (probArg ?: SprudelDslArg.of("0.5")).toPattern()

    val staticProb = probVal?.asDoubleOrNull()

    return if (staticProb != null) {
        // Static path
        val probability = staticProb.coerceIn(0.0, 1.0)
        ContinuousPattern { from, _, ctx ->
            val rand = ctx.getSeededRandom(from, "brandBy")
            if (rand.nextDouble() < probability) 1.0 else 0.0
        }
    } else {
        // Dynamic path: use ControlPattern to apply varying probability
        ControlPattern(
            source = ContinuousPattern { from, _, ctx ->
                val rand = ctx.getSeededRandom(from, "brandBy")
                rand.nextDouble()
            },
            control = probPattern,
            mapper = { it },
            combiner = { sourceData, controlData ->
                val randomValue = sourceData.value?.asDouble ?: 0.5
                val prob = (controlData.value?.asDouble ?: 0.5).coerceIn(0.0, 1.0)
                val result = if (randomValue < prob) 1.0 else 0.0
                sourceData.copy(value = result.asVoiceValue())
            }
        )
    }
}

/**
 * Binary random pattern: outputs 0 or 1 with the given probability of returning 1.
 *
 * At each point in time, `brandBy(p)` produces 1 with probability `p` and 0 otherwise.
 * Useful for random gate or switch patterns.
 *
 * ```KlangScript(Playable)
 * s("hh*10").pan(brandBy(0.2))        // 20% chance of value 1, else 0
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").gain(brandBy(0.5))  // random full/zero gain on each event
 * ```
 *
 * @param prob Probability of returning 1 (vs 0). Range: 0.0–1.0. Default: 0.5.
 * @category random
 * @tags brandBy, binary, random, gate, probability
 */
@KlangScript.Function
fun brandBy(prob: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyBrandBy(listOf(prob).asSprudelDslArgs(callInfo))

/**
 * Binary random pattern with 50% probability — outputs 0 or 1 with equal chance.
 *
 * Shorthand for `brandBy(0.5)`. Use it wherever you need a random on/off gate signal.
 *
 * ```KlangScript(Playable)
 * s("hh*8").gain(brand)               // each hi-hat randomly at full or zero gain
 * ```
 *
 * @category random
 * @tags brand, brandBy, binary, random, gate
 */
@KlangScript.Constant
val brand: SprudelPattern = brandBy(0.5)

// -- irand() ----------------------------------------------------------------------------------------------------------

internal fun applyIrand(args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val nArg = args.getOrNull(0)
    val nVal = nArg?.value

    val nPattern: SprudelPattern = (nArg ?: SprudelDslArg.of("0")).toPattern()

    val staticN = nVal?.asIntOrNull()

    return if (staticN != null) {
        // Static path
        if (staticN < 0) {
            silence
        } else if (staticN == 1) {
            signal { 0.0 }
        } else {
            ContinuousPattern { from, _, ctx ->
                val fraction = from - floor(from)
                val seed = (fraction * staticN * 10).toInt()
                val random = ctx.getSeededRandom(seed, "irand")
                random.nextInt(0, staticN).toDouble()
            }
        }
    } else {
        // Dynamic path: use ControlPattern
        ControlPattern(
            source = ContinuousPattern { from, _, ctx ->
                val fraction = from - floor(from)
                val seed = (fraction * 100).toInt()
                val random = ctx.getSeededRandom(seed, "irand")
                random.nextDouble()
            },
            control = nPattern,
            mapper = { it },
            combiner = { sourceData, controlData ->
                val randomValue = sourceData.value?.asDouble ?: 0.0
                val n = controlData.value?.asInt ?: 0
                val result = if (n <= 0) {
                    0.0
                } else if (n == 1) {
                    0.0
                } else {
                    (randomValue * n).toInt().toDouble()
                }
                sourceData.copy(value = result.asVoiceValue())
            }
        )
    }
}

/**
 * Continuous pattern of random integers in the range `0` to `n - 1`.
 *
 * Generates a new random integer at each distinct point in time. Useful for randomly
 * selecting scale degrees, sample numbers, or any indexed choice.
 *
 * ```KlangScript(Playable)
 * n(irand(8)).scale("C:minor").note()         // random scale degree 0–7 each event
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd*8").n(irand(6))                       // random sample variant 0–5 per hit
 * ```
 *
 * @param n Upper bound (exclusive) — random integers are produced in the range 0 to n-1. Any positive integer.
 * @category random
 * @tags irand, random, integer, continuous
 */
@KlangScript.Function
fun irand(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyIrand(listOf(n).asSprudelDslArgs(callInfo))

// -- randL() ----------------------------------------------------------------------------------------------------------

private fun applyRandL(args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val nArg = args.getOrNull(0)
    val nVal = nArg?.value

    val nPattern: SprudelPattern = (nArg ?: SprudelDslArg.of("0")).toPattern()

    val staticN = nVal?.asIntOrNull()

    return RandLPattern.create(nPattern, staticN)
}

/**
 * Creates a pattern that produces a list (array) of `n` random numbers (0–1).
 *
 * The list is resampled each cycle, for parameters that accept a list of values.
 *
 * No example: the only documented consumer was `partials`, which does not exist in the
 * codebase. See docs/tasks/sprudel-function-testing.md.
 *
 * @param n Number of random values to produce in each list. Any positive integer.
 * @category random
 * @tags randL, random, list, array, partials
 */
@KlangScript.Function
fun randL(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyRandL(listOf(n).asSprudelDslArgs(callInfo))

// -- randrun() --------------------------------------------------------------------------------------------------------

private fun applyRandrun(args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val nArg = args.getOrNull(0)
    val nVal = nArg?.value

    val nPattern: SprudelPattern = (nArg ?: SprudelDslArg.of("0")).toPattern()

    val staticN = nVal?.asIntOrNull()

    return if (staticN != null) {
        // Static path
        if (staticN < 1) {
            silence
        } else {
            val atom = AtomicPattern.pure
            val events = (0..<staticN).map { index ->
                atom.reinterpret { evt, ctx ->
                    val updatedCtx = ctx.update {
                        setIfAbsent(QueryContext.randomSeedKey, 0)
                    }
                    val cycle = evt.part.begin.cycleIndex()
                    val random = updatedCtx.getSeededRandom(cycle, "randrun")
                    val permutation = (0 until staticN).toMutableList()
                    permutation.shuffle(random)
                    val value = permutation[index].asVoiceValue()
                    evt.copy(data = evt.data.copy(value = value))
                }
            }
            SequencePattern(events)
        }
    } else {
        // Dynamic path: Create a pattern that varies the sequence length
        RandrunPattern(nPattern)
    }
}

/**
 * Creates a pattern of `n` integers (0 to n-1) in a new random order each cycle.
 *
 * Every cycle, the range `0..(n-1)` is shuffled and played in that random order. Each value
 * appears exactly once per cycle. Useful for randomised but non-repeating sequences.
 *
 * ```KlangScript(Playable)
 * n(randrun(8)).scale("C:pentatonic").note()   // random permutation of 8 scale degrees
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").bite(4, randrun(4))         // random order of 4 slices each cycle
 * ```
 *
 * @param n Number of integers in the random permutation (produces values 0 to n-1). Any positive integer.
 * @category random
 * @tags randrun, random, permutation, shuffle, sequence
 */
@KlangScript.Function
fun randrun(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyRandrun(listOf(n).asSprudelDslArgs(callInfo))

