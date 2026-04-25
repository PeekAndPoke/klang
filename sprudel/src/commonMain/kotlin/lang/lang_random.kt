@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel._innerJoin
import io.peekandpoke.klang.sprudel._lift
import io.peekandpoke.klang.sprudel.appLeft
import io.peekandpoke.klang.sprudel.filterValues
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern
import io.peekandpoke.klang.sprudel.pattern.ChoicePattern
import io.peekandpoke.klang.sprudel.pattern.ContinuousPattern
import io.peekandpoke.klang.sprudel.pattern.ControlPattern
import io.peekandpoke.klang.sprudel.pattern.RandLPattern
import io.peekandpoke.klang.sprudel.pattern.RandrunPattern
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpret
import io.peekandpoke.klang.sprudel.pattern.SeedPattern
import io.peekandpoke.klang.sprudel.pattern.SequencePattern
import io.peekandpoke.klang.sprudel.pattern.ShufflePattern
import io.peekandpoke.klang.sprudel.pattern.StructurePattern
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.seed(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyRandomSeed(this, listOf(n).asSprudelDslArgs(callInfo))

/** Sets the random seed for reproducible random operations on a string pattern. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun seed(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.seed(n, callInfo) }

/** Chains a seed onto this [PatternMapperFn]; pins all random operations to the given seed value. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.withSeed(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyRandomSeed(this, listOf(n).asSprudelDslArgs(callInfo))

/** Alias for [seed] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.withSeed(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).withSeed(n, callInfo)

/** Returns a [PatternMapperFn] — alias for [seed] — that pins all random operations to the given seed. */
@SprudelDsl
@KlangScript.Function
fun withSeed(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.withSeed(n, callInfo) }

/** Chains a withSeed (alias for [seed]) onto this [PatternMapperFn]. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Property
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
 * note("c4").sound("supersaw").detune(rand2.range(-10, 10))   // slight random detune
 * ```
 *
 * @category random
 * @tags rand2, random, bipolar, continuous, noise
 */
@SprudelDsl
@KlangScript.Property
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
@SprudelDsl
@KlangScript.Property
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Property
val brand: SprudelPattern = brandBy(0.5)

// -- irand() ----------------------------------------------------------------------------------------------------------

private fun applyIrand(args: List<SprudelDslArg<Any?>>): SprudelPattern {
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
@SprudelDsl
@KlangScript.Function
fun irand(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyIrand(listOf(n).asSprudelDslArgs(callInfo))

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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.degradeBy(prob: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyDegradeBy(this, listOf(prob).asSprudelDslArgs(callInfo))

/** Randomly removes events with the given probability (0 = none removed, 1 = all removed). */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun degradeBy(prob: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.degradeBy(prob, callInfo) }

/** Chains a degradeBy onto this [PatternMapperFn]; randomly removes events at the given probability. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.degrade(prob: PatternLike = 0.5, callInfo: CallInfo? = null): SprudelPattern =
    applyDegradeBy(this, listOf(prob).asSprudelDslArgs(callInfo))

/** Randomly removes events with a 50% probability. Shorthand for `degradeBy(0.5)`. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun degrade(prob: PatternLike = 0.5, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.degrade(prob, callInfo) }

/** Chains a degrade onto this [PatternMapperFn] with the given probability (default 0.5). */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.degrade(prob: PatternLike = 0.5, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.degrade(prob, callInfo) }

// -- degradeByWith() --------------------------------------------------------------------------------------------------

private fun applyDegradeByWith(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // JavaScript: pat.fmap((a) => (_) => a).appLeft(withPat.filterValues((v) => v > x))
    // Keeps events where withPat > x
    // Examples:
    //   degradeByWith(rand, 0.2) -> keep where rand > 0.2 (~80% kept)
    //   degradeByWith(rand, 0.5) -> keep where rand > 0.5 (~50% kept)
    //   degradeByWith(rand, 0.8) -> keep where rand > 0.8 (~20% kept)
    val withPat = args.getOrNull(0)?.toPattern() ?: return pattern
    val xPat = (args.getOrNull(1) ?: SprudelDslArg.of(0.5)).toPattern()

    return pattern._lift(xPat) { x, src ->
        src.appLeft(withPat.filterValues { v -> (v?.asDouble ?: 0.0) > x })
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.degradeByWith(withPat: PatternLike, prob: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyDegradeByWith(this, listOf(withPat, prob).asSprudelDslArgs(callInfo))

/** Randomly removes events using a custom random-value pattern as the randomness source. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun degradeByWith(withPat: PatternLike, prob: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.degradeByWith(withPat, prob, callInfo) }

/** Chains a degradeByWith onto this [PatternMapperFn] using a custom randomness source. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.undegradeBy(prob: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyUndegradeBy(this, listOf(prob).asSprudelDslArgs(callInfo))

/** Inverse of `degradeBy`: keeps events that `degradeBy` would remove (0 = none, 1 = all). */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun undegradeBy(prob: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.undegradeBy(prob, callInfo) }

/** Chains an undegradeBy onto this [PatternMapperFn]; keeps events at the given probability. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.undegradeBy(prob: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.undegradeBy(prob, callInfo) }

// -- undegradeByWith() ------------------------------------------------------------------------------------------------

private fun applyUndegradeByWith(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // Inverse of degradeByWith: keep where withPat >= (1 - x)
    // Keeps events where withPat >= (1 - x), which is equivalent to keeping where withPat > (1 - x) for continuous values
    // Examples:
    //   undegradeByWith(rand, 0.1) -> keep where rand >= 0.9 (~10% kept)
    //   undegradeByWith(rand, 0.5) -> keep where rand >= 0.5 (~50% kept)
    //   undegradeByWith(rand, 1.0) -> keep where rand >= 0.0 (~100% kept)
    val withPat = args.getOrNull(0)?.toPattern() ?: return pattern
    val xPat = (args.getOrNull(1) ?: SprudelDslArg.of(0.5)).toPattern()

    return pattern._lift(xPat) { x, src ->
        src.appLeft(withPat.filterValues { v -> (v?.asDouble ?: 0.0) >= (1 - x) })
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.undegradeByWith(withPat: PatternLike, prob: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyUndegradeByWith(this, listOf(withPat, prob).asSprudelDslArgs(callInfo))

/** Inverse of `degradeByWith` using a custom random-value pattern. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun undegradeByWith(withPat: PatternLike, prob: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.undegradeByWith(withPat, prob, callInfo) }

/** Chains an undegradeByWith onto this [PatternMapperFn] using a custom randomness source. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.undegrade(callInfo: CallInfo? = null): SprudelPattern =
    applyUndegradeBy(this, emptyList<Any>().asSprudelDslArgs(callInfo))

/** Keeps events with 50% probability. Shorthand for `undegradeBy(0.5)`. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun undegrade(callInfo: CallInfo? = null): PatternMapperFn = { p -> p.undegrade(callInfo) }

/** Chains an undegrade (50% keep) onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.undegrade(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.undegrade(callInfo) }

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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.sometimesBy(prob: PatternLike, callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    applySometimesBy(this, listOf(prob, mapper).asSprudelDslArgs(callInfo))

/** Applies `transform` to each event independently with the given probability. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun sometimesBy(prob: PatternLike, callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.sometimesBy(prob, callInfo, mapper) }

/** Chains a sometimesBy onto this [PatternMapperFn]; applies inner mapper at the given probability. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.sometimes(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    applySometimes(this, listOf(mapper).asSprudelDslArgs(callInfo))

/** Applies `transform` with a 50% chance per event. Shorthand for `sometimesBy(0.5, fn)`. */
@SprudelDsl
@KlangScript.Function
fun String.sometimes(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sometimes(callInfo, mapper)

/** Returns a [PatternMapperFn] that applies `mapper` with 50% probability per event. */
@SprudelDsl
@KlangScript.Function
fun sometimes(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.sometimes(callInfo, mapper) }

/** Chains a sometimes (50% per event) onto this [PatternMapperFn]. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.often(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    applyOften(this, listOf(mapper).asSprudelDslArgs(callInfo))

/** Applies `transform` with 75% probability per event. Shorthand for `sometimesBy(0.75, fn)`. */
@SprudelDsl
@KlangScript.Function
fun String.often(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).often(callInfo, mapper)

/** Returns a [PatternMapperFn] that applies `mapper` with 75% probability per event. */
@SprudelDsl
@KlangScript.Function
fun often(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.often(callInfo, mapper) }

/** Chains an often (75% per event) onto this [PatternMapperFn]. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.rarely(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    applyRarely(this, listOf(mapper).asSprudelDslArgs(callInfo))

/** Applies `transform` with 25% probability per event. Shorthand for `sometimesBy(0.25, fn)`. */
@SprudelDsl
@KlangScript.Function
fun String.rarely(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).rarely(callInfo, mapper)

/** Returns a [PatternMapperFn] that applies `mapper` with 25% probability per event. */
@SprudelDsl
@KlangScript.Function
fun rarely(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.rarely(callInfo, mapper) }

/** Chains a rarely (25% per event) onto this [PatternMapperFn]. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.almostNever(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    applyAlmostNever(this, listOf(mapper).asSprudelDslArgs(callInfo))

/** Applies `transform` with 10% probability per event. Shorthand for `sometimesBy(0.1, fn)`. */
@SprudelDsl
@KlangScript.Function
fun String.almostNever(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).almostNever(callInfo, mapper)

/** Returns a [PatternMapperFn] that applies `mapper` with 10% probability per event. */
@SprudelDsl
@KlangScript.Function
fun almostNever(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.almostNever(callInfo, mapper) }

/** Chains an almostNever (10% per event) onto this [PatternMapperFn]. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.almostAlways(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    applyAlmostAlways(this, listOf(mapper).asSprudelDslArgs(callInfo))

/** Applies `transform` with 90% probability per event. Shorthand for `sometimesBy(0.9, fn)`. */
@SprudelDsl
@KlangScript.Function
fun String.almostAlways(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).almostAlways(callInfo, mapper)

/** Returns a [PatternMapperFn] that applies `mapper` with 90% probability per event. */
@SprudelDsl
@KlangScript.Function
fun almostAlways(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.almostAlways(callInfo, mapper) }

/** Chains an almostAlways (90% per event) onto this [PatternMapperFn]. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.never(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern = this

/** Never applies `transform` — the pattern passes through unchanged. */
@SprudelDsl
@KlangScript.Function
fun String.never(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).never(callInfo, mapper)

/** Returns a [PatternMapperFn] that never applies `mapper` — source passes through unchanged. */
@SprudelDsl
@KlangScript.Function
fun never(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.never(callInfo, mapper) }

/** Chains a never (no-op) onto this [PatternMapperFn]. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.always(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    applyAlways(this, listOf(mapper).asSprudelDslArgs(callInfo))

/** Always applies `transform`. Shorthand for `sometimesBy(1, fn)`. */
@SprudelDsl
@KlangScript.Function
fun String.always(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).always(callInfo, mapper)

/** Returns a [PatternMapperFn] that always applies `mapper` unconditionally. */
@SprudelDsl
@KlangScript.Function
fun always(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.always(callInfo, mapper) }

/** Chains an always (unconditional apply) onto this [PatternMapperFn]. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.someCyclesBy(prob: PatternLike, callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    applySomeCyclesBy(this, listOf(prob, mapper).asSprudelDslArgs(callInfo))

/** Applies `transform` with the given probability, decided once per cycle (not per event). */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun someCyclesBy(prob: PatternLike, callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.someCyclesBy(prob, callInfo, mapper) }

/** Chains a someCyclesBy onto this [PatternMapperFn]; applies inner mapper at the given per-cycle probability. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.someCycles(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    applySomeCyclesBy(this, listOf(mapper).asSprudelDslArgs(callInfo))

/** Applies `transform` with 50% probability per cycle. Shorthand for `someCyclesBy(0.5, fn)`. */
@SprudelDsl
@KlangScript.Function
fun String.someCycles(callInfo: CallInfo? = null, mapper: PatternMapperFn): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).someCycles(callInfo, mapper)

/** Returns a [PatternMapperFn] that applies `mapper` with 50% probability per cycle. */
@SprudelDsl
@KlangScript.Function
fun someCycles(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    { p -> p.someCycles(callInfo, mapper) }

/** Chains a someCycles (50% per cycle) onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.someCycles(callInfo: CallInfo? = null, mapper: PatternMapperFn): PatternMapperFn =
    this.chain { p -> p.someCycles(callInfo, mapper) }

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
 * The list is resampled each cycle. Useful for passing multiple random values to
 * parameters that accept lists, such as `partials`.
 *
 * ```KlangScript(Playable)
 * s("saw").n(irand(12)).scale("F1:minor").partials(randL(8))   // 8 random partials
 * ```
 *
 * @param n Number of random values to produce in each list. Any positive integer.
 * @category random
 * @tags randL, random, list, array, partials
 */
@SprudelDsl
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
                    val cycle = evt.part.begin.floor()
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
@SprudelDsl
@KlangScript.Function
fun randrun(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyRandrun(listOf(n).asSprudelDslArgs(callInfo))

// -- shuffle() --------------------------------------------------------------------------------------------------------

private fun applyShuffle(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val nPattern = (args.getOrNull(0) ?: SprudelDslArg.of(4)).toPattern()
    return ShufflePattern(source = p, nPattern = nPattern)
}

/**
 * Slices the pattern into `n` equal parts and plays them in a new random order each cycle.
 *
 * Each slice is played exactly once per cycle — the order changes but nothing is omitted.
 * Compare with [scramble], which may repeat or skip slices.
 *
 * @param n Number of equal slices to divide the pattern into and reorder.
 * @return A pattern with its slices randomly permuted each cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").shuffle(4)                 // random permutation of 4 quarter-cycle slices
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").shuffle(4)                // randomly reorder the 4 drum hits each cycle
 * ```
 *
 * @category random
 * @tags shuffle, random, reorder, slice, permutation
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.shuffle(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyShuffle(this, listOf(n).asSprudelDslArgs(callInfo))

/** Slices the pattern into `n` equal parts and plays them in a new random order each cycle. */
@SprudelDsl
@KlangScript.Function
fun String.shuffle(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).shuffle(n, callInfo)

/**
 * Returns a [PatternMapperFn] that slices the source into `n` parts and plays them in random order.
 *
 * @param n Number of equal slices to divide the pattern into and reorder.
 * @return A [PatternMapperFn] that randomly reorders `n` slices each cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(shuffle(4))          // random permutation via mapper
 * ```
 *
 * @category random
 * @tags shuffle, random, reorder, slice, permutation
 */
@SprudelDsl
@KlangScript.Function
fun shuffle(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.shuffle(n, callInfo) }

/** Chains a shuffle onto this [PatternMapperFn]; randomly reorders `n` equal slices each cycle. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.shuffle(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.shuffle(n, callInfo) }

// -- scramble() -------------------------------------------------------------------------------------------------------

private fun applyScramble(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val nArg: SprudelDslArg<Any?> = args.getOrNull(0) ?: SprudelDslArg.of(4)
    val nValue = nArg.value ?: 4
    val indices = applyIrand(listOf(nArg)).segment(nValue)
    return p.bite(nValue, indices)
}

/**
 * Slices the pattern into `n` equal parts and picks slices at random each cycle.
 *
 * Unlike [shuffle], which plays each slice exactly once, `scramble` picks randomly with
 * replacement: some slices may play multiple times, others not at all.
 *
 * @param n Number of equal slices to divide the pattern into and pick from.
 * @return A pattern with randomly selected (with replacement) slices each cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").scramble(4)         // random selection with repetition of 4 slices
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").scramble(4)        // random drum hit order, repeats allowed
 * ```
 *
 * @category random
 * @tags scramble, random, slice, replacement, selection
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.scramble(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyScramble(this, listOf(n).asSprudelDslArgs(callInfo))

/** Slices the pattern into `n` equal parts and picks slices at random each cycle. */
@SprudelDsl
@KlangScript.Function
fun String.scramble(n: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).scramble(n, callInfo)

/**
 * Returns a [PatternMapperFn] that slices the source into `n` parts and picks randomly with replacement.
 *
 * @param n Number of equal slices to divide the pattern into and pick from.
 * @return A [PatternMapperFn] that randomly picks `n` slices (with repetition) each cycle.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").apply(scramble(4)) // random drum hit order via mapper
 * ```
 *
 * @category random
 * @tags scramble, random, slice, replacement, selection
 */
@SprudelDsl
@KlangScript.Function
fun scramble(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.scramble(n, callInfo) }

/** Chains a scramble onto this [PatternMapperFn]; randomly picks `n` slices with replacement. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.scramble(n: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.scramble(n, callInfo) }

// -- chooseWith() -----------------------------------------------------------------------------------------------------

private fun applyChooseWith(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val xs = args.extractChoiceArgs()
    return ChoicePattern.createFromRaw(p, xs, mode = StructurePattern.Mode.Out)
}

/**
 * Chooses from the given list of values using a selector pattern (values 0–1).
 *
 * The selector pattern controls which value is chosen at each point in time. A value of 0
 * picks the first element, 1 picks the last, and values in between interpolate as an index.
 * Structure comes from the selector pattern (`chooseOut` mode).
 *
 * ```KlangScript(Playable)
 * note("c2 g2 d2 f1").s(chooseWith(sine.fast(2), "sawtooth", "triangle", "bd:6"))
 * ```
 *
 * ```KlangScript(Playable)
 * s(chooseWith(rand, "bd", "sd", "hh"))    // random instrument selection per event
 * ```
 *
 * @param args First argument is the selector pattern (values 0–1); remaining arguments are the values to choose from.
 * @category random
 * @tags chooseWith, choose, selector, random, values
 */
@SprudelDsl
@KlangScript.Function
fun chooseWith(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    val argList = args.toList().asSprudelDslArgs(callInfo)
    val firstArg = argList.firstOrNull()
    return when (val firstVal = firstArg?.value) {
        is SprudelPattern -> applyChooseWith(firstVal, argList.drop(1))
        else -> applyChooseWith(AtomicPattern.pure, argList)
    }
}

/** Uses this pattern (range 0–1) as a selector to choose from the given list. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.chooseWith(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyChooseWith(this, args.toList().asSprudelDslArgs(callInfo))

/** Uses this string pattern as a selector to choose from the given list. */
@SprudelDsl
@KlangScript.Function
fun String.chooseWith(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chooseWith(*args, callInfo = callInfo)

// -- chooseInWith() ---------------------------------------------------------------------------------------------------

private fun applyChooseInWith(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val xs = args.extractChoiceArgs()
    return ChoicePattern.createFromRaw(p, xs, mode = StructurePattern.Mode.In)
}

/**
 * Like `chooseWith` but structure comes from the chosen values, not the selector pattern.
 *
 * The selector pattern (values 0–1) controls which value is chosen. The timing structure is
 * taken from the selected value pattern (`chooseIn` mode).
 *
 * ```KlangScript(Playable)
 * chooseInWith(sine, "c d", "e f g", "a b c d")   // selector picks pattern; its timing wins
 * ```
 *
 * @param args First argument is the selector pattern (values 0–1); remaining arguments are the values to choose from.
 * @category random
 * @tags chooseInWith, chooseWith, selector, random, structure
 */
@SprudelDsl
@KlangScript.Function
fun chooseInWith(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    val argList = args.toList().asSprudelDslArgs(callInfo)
    val firstArg = argList.firstOrNull()
    return when (val firstVal = firstArg?.value) {
        is SprudelPattern -> applyChooseInWith(firstVal, argList.drop(1))
        else -> applyChooseInWith(AtomicPattern.pure, argList)
    }
}

/** Uses this pattern as a selector to choose from the given list (structure from chosen). */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.chooseInWith(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyChooseInWith(this, args.toList().asSprudelDslArgs(callInfo))

/** Uses this string pattern as a selector to choose from the given list (structure from chosen). */
@SprudelDsl
@KlangScript.Function
fun String.chooseInWith(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chooseInWith(*args, callInfo = callInfo)

// -- choose() ---------------------------------------------------------------------------------------------------------

private fun applyChoose(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val xs = args.extractChoiceArgs()
    return ChoicePattern.createFromRaw(p, xs, mode = StructurePattern.Mode.Out)
}

/**
 * Chooses randomly from the given values or patterns at each event.
 *
 * Uses `rand` as the selector, so the choice varies continuously in time. Structure comes
 * from the selector (`chooseOut` mode). For cycle-level choices use `chooseCycles`.
 *
 * ```KlangScript(Playable)
 * note("c2 g2 d2 f1").s(choose("sine", "triangle", "bd:6"))   // random synth each note
 * ```
 *
 * ```KlangScript(Playable)
 * s(choose("bd", "sd", "hh")).fast(8)    // random drum sound every eighth-note
 * ```
 *
 * @param args Values or patterns to randomly choose from at each event.
 * @alias chooseOut
 * @category random
 * @tags choose, random, selection, values, chooseOut
 */
@SprudelDsl
@KlangScript.Function
fun choose(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    val argList = args.toList().asSprudelDslArgs(callInfo)
    val firstArg = argList.firstOrNull()
    return when (val firstVal = firstArg?.value) {
        is SprudelPattern -> applyChoose(firstVal, argList.drop(1))
        else -> applyChoose(AtomicPattern.pure, argList)
    }
}

/**
 * Uses this pattern (range 0–1) as a selector to choose from the given list.
 *
 * The receiver pattern controls the index into the list. Structure comes from the
 * selector pattern (`chooseOut` mode).
 *
 * ```KlangScript(Playable)
 * sine.choose("c", "e", "g", "b")     // sine wave selects among chord tones
 * ```
 *
 * @param args Values or patterns to choose from using the receiver as selector.
 * @alias chooseOut
 * @category random
 * @tags choose, selector, random, chooseOut
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.choose(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyChoose(this, args.toList().asSprudelDslArgs(callInfo))

/** Uses this string pattern as a selector to choose from the given list. */
@SprudelDsl
@KlangScript.Function
fun String.choose(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).choose(*args, callInfo = callInfo)

// -- chooseOut() ------------------------------------------------------------------------------------------------------

/**
 * Alias for [choose].
 *
 * @param args Values or patterns to randomly choose from at each event.
 * @alias choose
 * @category random
 * @tags chooseOut, choose, random, selection
 */
@SprudelDsl
@KlangScript.Function
fun chooseOut(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    choose(*args, callInfo = callInfo)

/** Alias for [choose]. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.chooseOut(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.choose(*args, callInfo = callInfo)

/** Alias for [choose]. */
@SprudelDsl
@KlangScript.Function
fun String.chooseOut(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chooseOut(*args, callInfo = callInfo)

// -- chooseIn() -------------------------------------------------------------------------------------------------------

private fun applyChooseIn(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val xs = args.extractChoiceArgs()
    return ChoicePattern.createFromRaw(p, xs, mode = StructurePattern.Mode.In)
}

/**
 * Like `choose`, but structure comes from the chosen values rather than the selector.
 *
 * Uses `rand` as the selector. The timing structure is inherited from the selected value
 * pattern (`chooseIn` mode), so the chosen pattern's own rhythm determines the events.
 *
 * ```KlangScript(Playable)
 * chooseIn("c d", "e f g", "a b c d")   // random pattern; its timing determines structure
 * ```
 *
 * @param args Values or patterns to randomly choose from; structure comes from the chosen value.
 * @category random
 * @tags chooseIn, random, selection, structure
 */
@SprudelDsl
@KlangScript.Function
fun chooseIn(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    val argList = args.toList().asSprudelDslArgs(callInfo)
    val firstArg = argList.firstOrNull()
    return when (val firstVal = firstArg?.value) {
        is SprudelPattern -> applyChooseIn(firstVal, argList.drop(1))
        else -> applyChooseIn(AtomicPattern.pure, argList)
    }
}

/** Uses this pattern as a selector; structure comes from the chosen value. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.chooseIn(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyChooseIn(this, args.toList().asSprudelDslArgs(callInfo))

/** Uses this string pattern as a selector; structure comes from the chosen value. */
@SprudelDsl
@KlangScript.Function
fun String.chooseIn(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chooseIn(*args, callInfo = callInfo)

// -- choose2() --------------------------------------------------------------------------------------------------------

private fun applyChoose2(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val xs = args.extractChoiceArgs()
    return ChoicePattern.createFromRaw(p.fromBipolar(), xs, mode = StructurePattern.Mode.Out)
}

/**
 * Like `choose`, but the selector pattern should be in the range -1 to 1 (bipolar).
 *
 * The receiver bipolar pattern is converted to 0–1 before indexing into the list, so
 * `rand2` and LFOs centred at zero can be used directly as selectors.
 *
 * ```KlangScript(Playable)
 * rand2.choose2("c", "e", "g", "b")    // bipolar rand selects among chord tones
 * ```
 *
 * @param args Values or patterns to choose from using the bipolar receiver (-1 to 1) as selector.
 * @category random
 * @tags choose2, bipolar, selector, random
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.choose2(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyChoose2(this, args.toList().asSprudelDslArgs(callInfo))

/** Like `choose`, but the selector pattern should be in the range -1 to 1 (bipolar). */
@SprudelDsl
@KlangScript.Function
fun String.choose2(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).choose2(*args, callInfo = callInfo)

// -- chooseCycles() ---------------------------------------------------------------------------------------------------

private fun applyChooseCyclesPattern(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val xs = (listOf(p) + args.map { it.value }).asSprudelDslArgs()
    return ChoicePattern.createFromRaw(rand.segment(1), xs, mode = StructurePattern.Mode.In)
}

/**
 * Picks one of the given values or patterns at random, changing once per cycle.
 *
 * The entire cycle plays the same chosen pattern. Unlike `choose`, which can vary within a
 * cycle, `chooseCycles` makes a fresh random choice only at cycle boundaries.
 *
 * ```KlangScript(Playable)
 * chooseCycles("bd", "hh", "sd").s().fast(8)   // entire cycle uses one random drum sound
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd | hh | sd").fast(8)                    // mini-notation equivalent using |
 * ```
 *
 * @param args Values or patterns to randomly pick from; one is chosen per cycle.
 * @alias randcat
 * @category random
 * @tags chooseCycles, random, cycle, randcat, selection
 */
@SprudelDsl
@KlangScript.Function
fun chooseCycles(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    val argList = args.toList().asSprudelDslArgs(callInfo)
    val firstArg = argList.firstOrNull()
    return when (val firstVal = firstArg?.value) {
        is SprudelPattern -> applyChooseCyclesPattern(firstVal, argList.drop(1))
        else -> applyChooseCyclesPattern(AtomicPattern.pure, argList)
    }
}

/** Picks one of the given values at random, changing once per cycle. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.chooseCycles(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyChooseCyclesPattern(this, args.toList().asSprudelDslArgs(callInfo))

/** Picks one of the given values at random, changing once per cycle. */
@SprudelDsl
@KlangScript.Function
fun String.chooseCycles(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chooseCycles(*args, callInfo = callInfo)

// -- randcat() --------------------------------------------------------------------------------------------------------

/**
 * Alias for [chooseCycles].
 *
 * @param args Values or patterns to randomly pick from; one is chosen per cycle.
 * @alias chooseCycles
 * @category random
 * @tags randcat, chooseCycles, random, cycle
 */
@SprudelDsl
@KlangScript.Function
fun randcat(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    chooseCycles(*args, callInfo = callInfo)

/** Alias for [chooseCycles]. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.randcat(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.chooseCycles(*args, callInfo = callInfo)

/** Alias for [chooseCycles]. */
@SprudelDsl
@KlangScript.Function
fun String.randcat(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).randcat(*args, callInfo = callInfo)

// -- wchoose() --------------------------------------------------------------------------------------------------------

private fun applyWchoose(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val (items, weights) = args.extractWeightedPairs()
    return ChoicePattern.createFromRaw(
        selector = p,
        choices = items,
        weights = weights,
        mode = StructurePattern.Mode.Out,
    )
}

/**
 * Chooses randomly from the given values according to relative weights.
 *
 * Each choice is a `[value, weight]` pair. Higher weights make that value more likely.
 * Uses `rand` as the selector so choices vary within a cycle.
 *
 * ```KlangScript(Playable)
 * note("c2 g2 d2 f1").s(wchoose(listOf("sine", 10), listOf("triangle", 1)))
 * ```
 *
 * ```KlangScript(Playable)
 * s(wchoose(listOf("bd", 8), listOf("sd", 2), listOf("hh", 5))).fast(8)
 * ```
 *
 * @param args `[value, weight]` pairs — higher weight means more likely selection.
 * @category random
 * @tags wchoose, weighted, random, probability, selection
 */
@SprudelDsl
@KlangScript.Function
fun wchoose(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    val argList = args.toList().asSprudelDslArgs(callInfo)
    val firstArg = argList.firstOrNull()
    return when (val firstVal = firstArg?.value) {
        is SprudelPattern -> applyWchoose(firstVal, argList.drop(1))
        else -> applyWchoose(AtomicPattern.pure, argList)
    }
}

/** Uses this pattern (range 0–1) as a weighted selector over the given value/weight pairs. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.wchoose(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyWchoose(this, args.toList().asSprudelDslArgs(callInfo))

/** Uses this string pattern as a weighted selector over the given value/weight pairs. */
@SprudelDsl
@KlangScript.Function
fun String.wchoose(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).wchoose(*args, callInfo = callInfo)

// -- wchooseCycles() --------------------------------------------------------------------------------------------------

private fun applyWchooseCyclesPattern(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val (items, weights) = args.extractWeightedPairs()
    val allItems = (listOf(p) + items.map { it.value }).asSprudelDslArgs()
    val allWeights = (listOf(1.0) + weights.map { it.value }).asSprudelDslArgs()

    return ChoicePattern.createFromRaw(
        selector = rand.segment(1),
        choices = allItems,
        weights = allWeights,
        mode = StructurePattern.Mode.In,
    )
}

/**
 * Picks one of the given values at random each cycle according to relative weights.
 *
 * Like `chooseCycles` but each choice can have a different probability. The entire cycle
 * plays the same chosen value. Each choice is a `[value, weight]` pair.
 *
 * ```KlangScript(Playable)
 * wchooseCycles(listOf("bd", 10), listOf("hh", 1)).s().fast(8)   // bd much more likely
 * ```
 *
 * @param args `[value, weight]` pairs — higher weight means more likely selection. One is chosen per cycle.
 * @alias wrandcat
 * @category random
 * @tags wchooseCycles, weighted, random, cycle, wrandcat
 */
@SprudelDsl
@KlangScript.Function
fun wchooseCycles(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern {
    val argList = args.toList().asSprudelDslArgs(callInfo)
    val firstArg = argList.firstOrNull()
    return when (val firstVal = firstArg?.value) {
        is SprudelPattern -> applyWchooseCyclesPattern(firstVal, argList.drop(1))
        else -> applyWchooseCyclesPattern(AtomicPattern.pure, argList)
    }
}

/** Picks a weighted random value once per cycle. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.wchooseCycles(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyWchooseCyclesPattern(this, args.toList().asSprudelDslArgs(callInfo))

/** Picks a weighted random value once per cycle. */
@SprudelDsl
@KlangScript.Function
fun String.wchooseCycles(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).wchooseCycles(*args, callInfo = callInfo)

// -- wrandcat() -------------------------------------------------------------------------------------------------------

/**
 * Alias for [wchooseCycles].
 *
 * @param args `[value, weight]` pairs — higher weight means more likely selection. One is chosen per cycle.
 * @alias wchooseCycles
 * @category random
 * @tags wrandcat, wchooseCycles, weighted, random, cycle
 */
@SprudelDsl
@KlangScript.Function
fun wrandcat(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    wchooseCycles(*args, callInfo = callInfo)

/** Alias for [wchooseCycles]. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.wrandcat(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.wchooseCycles(*args, callInfo = callInfo)

/** Alias for [wchooseCycles]. */
@SprudelDsl
@KlangScript.Function
fun String.wrandcat(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).wrandcat(*args, callInfo = callInfo)
