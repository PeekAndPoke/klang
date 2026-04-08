@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")

package io.peekandpoke.klang.sprudel.lang

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
import io.peekandpoke.klang.sprudel.pattern.ContextModifierPattern
import io.peekandpoke.klang.sprudel.pattern.ContinuousPattern
import io.peekandpoke.klang.sprudel.pattern.ControlPattern
import io.peekandpoke.klang.sprudel.pattern.RandLPattern
import io.peekandpoke.klang.sprudel.pattern.RandrunPattern
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpret
import io.peekandpoke.klang.sprudel.pattern.SequencePattern
import io.peekandpoke.klang.sprudel.pattern.StructurePattern
import kotlin.math.floor

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangRandomInit = false

// -- Helpers ----------------------------------------------------------------------------------------------------------

fun applyRandomSeed(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val seed = args.getOrNull(0)?.value?.asIntOrNull()

    return ContextModifierPattern(source = pattern) {
        if (seed != null) {
            set(QueryContext.randomSeedKey, seed)
        } else {
            remove(QueryContext.randomSeedKey)
        }
    }
}

// -- seed() -----------------------------------------------------------------------------------------------------------

internal val _seed by dslPatternMapper { args, callInfo -> { p -> p._seed(args, callInfo) } }
internal val SprudelPattern._seed by dslPatternExtension { pattern, args, _ -> applyRandomSeed(pattern, args) }
internal val String._seed by dslStringExtension { pattern, args, callInfo -> pattern._seed(args, callInfo) }
internal val PatternMapperFn._seed by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_seed(args, callInfo))
}

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
fun SprudelPattern.seed(n: PatternLike): SprudelPattern = this._seed(listOf(n).asSprudelDslArgs())

/** Sets the random seed for reproducible random operations on a string pattern. */
@SprudelDsl
fun String.seed(n: PatternLike): SprudelPattern = this._seed(listOf(n).asSprudelDslArgs())

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
fun seed(n: PatternLike): PatternMapperFn = _seed(listOf(n).asSprudelDslArgs())

/** Chains a seed onto this [PatternMapperFn]; pins all random operations to the given seed value. */
@SprudelDsl
fun PatternMapperFn.seed(n: PatternLike): PatternMapperFn = this._seed(listOf(n).asSprudelDslArgs())

// -- withSeed() -------------------------------------------------------------------------------------------------------

internal val _withSeed by dslPatternMapper { args, callInfo -> _seed(args, callInfo) }
internal val SprudelPattern._withSeed by dslPatternExtension { pattern, args, _ -> applyRandomSeed(pattern, args) }
internal val String._withSeed by dslStringExtension { pattern, args, callInfo -> pattern._withSeed(args, callInfo) }
internal val PatternMapperFn._withSeed by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_seed(args, callInfo))
}

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
fun SprudelPattern.withSeed(n: PatternLike): SprudelPattern = this._withSeed(listOf(n).asSprudelDslArgs())

/** Alias for [seed] on a string pattern. */
@SprudelDsl
fun String.withSeed(n: PatternLike): SprudelPattern = this._withSeed(listOf(n).asSprudelDslArgs())

/** Returns a [PatternMapperFn] — alias for [seed] — that pins all random operations to the given seed. */
@SprudelDsl
fun withSeed(n: PatternLike): PatternMapperFn = _withSeed(listOf(n).asSprudelDslArgs())

/** Chains a withSeed (alias for [seed]) onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.withSeed(n: PatternLike): PatternMapperFn = this._withSeed(listOf(n).asSprudelDslArgs())

// -- rand() / rand2() -------------------------------------------------------------------------------------------------

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
internal val _rand by dslObject {
    ContinuousPattern { from, _, ctx ->
        ctx.getSeededRandom(from, "rand").nextDouble()
    }
}

/**
 * Continuous random pattern producing values in the range 0–1.
 */
@SprudelDsl
val rand: SprudelPattern get() = _rand

/**
 * Continuous random pattern producing values in the range -1–1 (bipolar).
 *
 * Equivalent to `rand.range(-1, 1)`. Useful for LFO-style modulation that oscillates
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
internal val _rand2 by dslObject { rand.range(-1.0, 1.0) }

/**
 * Continuous random pattern producing values in the range -1–1 (bipolar).
 */
@SprudelDsl
val rand2: SprudelPattern get() = _rand2

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
internal val _randCycle by dslObject {
    ContinuousPattern { fromTime, _, ctx ->
        ctx.getSeededRandom(floor(fromTime), "randCycle").nextDouble()
    }
}

/**
 * Continuous random pattern that holds a constant value for each full cycle.
 */
@SprudelDsl
val randCycle: SprudelPattern get() = _randCycle

// -- brand() / brandBy() ----------------------------------------------------------------------------------------------

internal val _brandBy by dslPatternFunction { args, /* callInfo */ _ ->
    val probArg = args.getOrNull(0)
    val probVal = probArg?.value

    val probPattern: SprudelPattern = (probArg ?: SprudelDslArg.of("0.5")).toPattern()

    val staticProb = probVal?.asDoubleOrNull()

    if (staticProb != null) {
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
fun brandBy(prob: PatternLike): SprudelPattern = _brandBy(listOf(prob).asSprudelDslArgs())

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
internal val _brand by dslObject { brandBy(0.5) }

/**
 * Binary random pattern with 50% probability — outputs 0 or 1 with equal chance.
 */
@SprudelDsl
val brand: SprudelPattern get() = _brand

// -- irand() ----------------------------------------------------------------------------------------------------------

internal val _irand by dslPatternFunction { args, /* callInfo */ _ ->
    val nArg = args.getOrNull(0)
    val nVal = nArg?.value

    val nPattern: SprudelPattern = (nArg ?: SprudelDslArg.of("0")).toPattern()

    val staticN = nVal?.asIntOrNull()

    if (staticN != null) {
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
fun irand(n: PatternLike): SprudelPattern = _irand(listOf(n).asSprudelDslArgs())

// -- degradeBy() ------------------------------------------------------------------------------------------------------

fun applyDegradeBy(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // degradeBy(x) is just degradeByWith(rand, x)
    return applyDegradeByWith(pattern, listOf(SprudelDslArg.of(rand)) + args)
}

internal val _degradeBy by dslPatternMapper { args, callInfo -> { p -> p._degradeBy(args, callInfo) } }
internal val SprudelPattern._degradeBy by dslPatternExtension { pattern, args, _ -> applyDegradeBy(pattern, args) }
internal val String._degradeBy by dslStringExtension { pattern, args, callInfo -> pattern._degradeBy(args, callInfo) }
internal val PatternMapperFn._degradeBy by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_degradeBy(args, callInfo))
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
fun SprudelPattern.degradeBy(prob: PatternLike): SprudelPattern = this._degradeBy(listOf(prob).asSprudelDslArgs())

/** Randomly removes events with the given probability (0 = none removed, 1 = all removed). */
@SprudelDsl
fun String.degradeBy(prob: PatternLike): SprudelPattern = this._degradeBy(listOf(prob).asSprudelDslArgs())

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
fun degradeBy(prob: PatternLike): PatternMapperFn = _degradeBy(listOf(prob).asSprudelDslArgs())

/** Chains a degradeBy onto this [PatternMapperFn]; randomly removes events at the given probability. */
@SprudelDsl
fun PatternMapperFn.degradeBy(prob: PatternLike): PatternMapperFn = this._degradeBy(listOf(prob).asSprudelDslArgs())

// -- degrade() --------------------------------------------------------------------------------------------------------

internal val _degrade by dslPatternMapper { args, callInfo -> { p -> p._degrade(args, callInfo) } }
internal val SprudelPattern._degrade by dslPatternExtension { pattern, args, _ -> applyDegradeBy(pattern, args) }
internal val String._degrade by dslStringExtension { pattern, args, callInfo -> pattern._degrade(args, callInfo) }
internal val PatternMapperFn._degrade by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_degrade(args, callInfo))
}

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
fun SprudelPattern.degrade(prob: PatternLike = 0.5): SprudelPattern =
    this._degrade(listOf(prob).asSprudelDslArgs())

/** Randomly removes events with a 50% probability. Shorthand for `degradeBy(0.5)`. */
@SprudelDsl
fun String.degrade(prob: PatternLike = 0.5): SprudelPattern = this._degrade(listOf(prob).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that randomly removes events with 50% probability.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").apply(degrade())   // ~half the events play via mapper
 * ```
 *
 * @category random
 * @tags degrade, degradeBy, random, remove, probability
 */
@SprudelDsl
fun degrade(): PatternMapperFn = _degrade(listOf(0.5).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that randomly removes events at the given probability. */
@SprudelDsl
fun degrade(prob: PatternLike): PatternMapperFn = _degrade(listOf(prob).asSprudelDslArgs())

/** Chains a degrade (50% removal) onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.degrade(): PatternMapperFn = this._degrade(listOf(0.5).asSprudelDslArgs())

/** Chains a degrade onto this [PatternMapperFn] with the given probability. */
@SprudelDsl
fun PatternMapperFn.degrade(prob: PatternLike): PatternMapperFn = this._degrade(listOf(prob).asSprudelDslArgs())

// -- degradeByWith() --------------------------------------------------------------------------------------------------

fun applyDegradeByWith(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

internal val _degradeByWith by dslPatternMapper { args, callInfo -> { p -> p._degradeByWith(args, callInfo) } }
internal val SprudelPattern._degradeByWith by dslPatternExtension { pattern, args, _ ->
    applyDegradeByWith(pattern, args)
}
internal val String._degradeByWith by dslStringExtension { pattern, args, callInfo ->
    pattern._degradeByWith(args, callInfo)
}
internal val PatternMapperFn._degradeByWith by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_degradeByWith(args, callInfo))
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
fun SprudelPattern.degradeByWith(withPat: PatternLike, prob: PatternLike): SprudelPattern =
    this._degradeByWith(listOf(withPat, prob).asSprudelDslArgs())

/** Randomly removes events using a custom random-value pattern as the randomness source. */
@SprudelDsl
fun String.degradeByWith(withPat: PatternLike, prob: PatternLike): SprudelPattern =
    this._degradeByWith(listOf(withPat, prob).asSprudelDslArgs())

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
fun degradeByWith(withPat: PatternLike, prob: PatternLike): PatternMapperFn =
    _degradeByWith(listOf(withPat, prob).asSprudelDslArgs())

/** Chains a degradeByWith onto this [PatternMapperFn] using a custom randomness source. */
@SprudelDsl
fun PatternMapperFn.degradeByWith(withPat: PatternLike, prob: PatternLike): PatternMapperFn =
    this._degradeByWith(listOf(withPat, prob).asSprudelDslArgs())

// -- undegradeBy() ----------------------------------------------------------------------------------------------------

fun applyUndegradeBy(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // undegradeBy(x) is just undegradeByWith(rand, x)
    // undegradeBy(0) = 100% removal, undegradeBy(1) = 0% removal
    return applyUndegradeByWith(pattern, listOf(SprudelDslArg.of(rand)) + args)
}

internal val _undegradeBy by dslPatternMapper { args, callInfo -> { p -> p._undegradeBy(args, callInfo) } }
internal val SprudelPattern._undegradeBy by dslPatternExtension { pattern, args, _ ->
    applyUndegradeBy(pattern, args)
}
internal val String._undegradeBy by dslStringExtension { pattern, args, callInfo ->
    pattern._undegradeBy(args, callInfo)
}
internal val PatternMapperFn._undegradeBy by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_undegradeBy(args, callInfo))
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
fun SprudelPattern.undegradeBy(prob: PatternLike): SprudelPattern = this._undegradeBy(listOf(prob).asSprudelDslArgs())

/** Inverse of `degradeBy`: keeps events that `degradeBy` would remove (0 = none, 1 = all). */
@SprudelDsl
fun String.undegradeBy(prob: PatternLike): SprudelPattern = this._undegradeBy(listOf(prob).asSprudelDslArgs())

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
fun undegradeBy(prob: PatternLike): PatternMapperFn = _undegradeBy(listOf(prob).asSprudelDslArgs())

/** Chains an undegradeBy onto this [PatternMapperFn]; keeps events at the given probability. */
@SprudelDsl
fun PatternMapperFn.undegradeBy(prob: PatternLike): PatternMapperFn =
    this._undegradeBy(listOf(prob).asSprudelDslArgs())

// -- undegradeByWith() ------------------------------------------------------------------------------------------------

fun applyUndegradeByWith(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

internal val _undegradeByWith by dslPatternMapper { args, callInfo -> { p -> p._undegradeByWith(args, callInfo) } }
internal val SprudelPattern._undegradeByWith by dslPatternExtension { pattern, args, _ ->
    applyUndegradeByWith(pattern, args)
}
internal val String._undegradeByWith by dslStringExtension { pattern, args, callInfo ->
    pattern._undegradeByWith(args, callInfo)
}
internal val PatternMapperFn._undegradeByWith by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_undegradeByWith(args, callInfo))
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
fun SprudelPattern.undegradeByWith(withPat: PatternLike, prob: PatternLike): SprudelPattern =
    this._undegradeByWith(listOf(withPat, prob).asSprudelDslArgs())

/** Inverse of `degradeByWith` using a custom random-value pattern. */
@SprudelDsl
fun String.undegradeByWith(withPat: PatternLike, prob: PatternLike): SprudelPattern =
    this._undegradeByWith(listOf(withPat, prob).asSprudelDslArgs())

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
fun undegradeByWith(withPat: PatternLike, prob: PatternLike): PatternMapperFn =
    _undegradeByWith(listOf(withPat, prob).asSprudelDslArgs())

/** Chains an undegradeByWith onto this [PatternMapperFn] using a custom randomness source. */
@SprudelDsl
fun PatternMapperFn.undegradeByWith(withPat: PatternLike, prob: PatternLike): PatternMapperFn =
    this._undegradeByWith(listOf(withPat, prob).asSprudelDslArgs())

// -- undegrade() ------------------------------------------------------------------------------------------------------

internal val _undegrade by dslPatternMapper { args, callInfo -> { p -> p._undegrade(args, callInfo) } }
internal val SprudelPattern._undegrade by dslPatternExtension { pattern, args, _ ->
    applyUndegradeBy(pattern, args)
}
internal val String._undegrade by dslStringExtension { pattern, args, callInfo ->
    pattern._undegrade(args, callInfo)
}
internal val PatternMapperFn._undegrade by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_undegrade(args, callInfo))
}

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
fun SprudelPattern.undegrade(): SprudelPattern = this._undegrade(emptyList())

/** Keeps events with 50% probability. Shorthand for `undegradeBy(0.5)`. */
@SprudelDsl
fun String.undegrade(): SprudelPattern = this._undegrade(emptyList())

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
fun undegrade(): PatternMapperFn = _undegrade(emptyList())

/** Chains an undegrade (50% keep) onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.undegrade(): PatternMapperFn = this._undegrade(emptyList())

// -- sometimesBy() ----------------------------------------------------------------------------------------------------

/**
 * Randomly applies the given function by the given probability.
 */
fun applySometimesBy(
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

internal val _sometimesBy by dslPatternMapper { args, callInfo -> { p -> p._sometimesBy(args, callInfo) } }
internal val SprudelPattern._sometimesBy by dslPatternExtension { pattern, args, _ ->
    applySometimesBy(pattern, args)
}
internal val String._sometimesBy by dslStringExtension { pattern, args, callInfo ->
    pattern._sometimesBy(args, callInfo)
}
internal val PatternMapperFn._sometimesBy by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sometimesBy(args, callInfo))
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
fun SprudelPattern.sometimesBy(prob: PatternLike, mapper: PatternMapperFn): SprudelPattern =
    this._sometimesBy(listOf(prob, mapper).asSprudelDslArgs())

/** Applies `transform` to each event independently with the given probability. */
@SprudelDsl
fun String.sometimesBy(prob: PatternLike, mapper: PatternMapperFn): SprudelPattern =
    this._sometimesBy(listOf(prob, mapper).asSprudelDslArgs())

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
fun sometimesBy(prob: PatternLike, mapper: PatternMapperFn): PatternMapperFn =
    _sometimesBy(listOf(prob, mapper).asSprudelDslArgs())

/** Chains a sometimesBy onto this [PatternMapperFn]; applies inner mapper at the given probability. */
@SprudelDsl
fun PatternMapperFn.sometimesBy(prob: PatternLike, mapper: PatternMapperFn): PatternMapperFn =
    this._sometimesBy(listOf(prob, mapper).asSprudelDslArgs())

// -- sometimes() ------------------------------------------------------------------------------------------------------

fun applySometimes(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.5
    return pattern.`when`(rand.lt(x), transform)
}

internal val _sometimes by dslPatternMapper { args, callInfo -> { p -> p._sometimes(args, callInfo) } }
internal val SprudelPattern._sometimes by dslPatternExtension { pattern, args, _ -> applySometimes(pattern, args) }
internal val String._sometimes by dslStringExtension { pattern, args, callInfo -> pattern._sometimes(args, callInfo) }
internal val PatternMapperFn._sometimes by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sometimes(args, callInfo))
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
fun SprudelPattern.sometimes(mapper: PatternMapperFn): SprudelPattern =
    this._sometimes(listOf(mapper).asSprudelDslArgs())

/** Applies `transform` with a 50% chance per event. Shorthand for `sometimesBy(0.5, fn)`. */
@SprudelDsl
fun String.sometimes(mapper: PatternMapperFn): SprudelPattern = this._sometimes(listOf(mapper).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that applies `mapper` with 50% probability per event. */
@SprudelDsl
fun sometimes(mapper: PatternMapperFn): PatternMapperFn = _sometimes(listOf(mapper).asSprudelDslArgs())

/** Chains a sometimes (50% per event) onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.sometimes(mapper: PatternMapperFn): PatternMapperFn =
    this._sometimes(listOf(mapper).asSprudelDslArgs())

// -- often() ----------------------------------------------------------------------------------------------------------

fun applyOften(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.75
    return pattern.`when`(rand.lt(x), transform)
}

internal val _often by dslPatternMapper { args, callInfo -> { p -> p._often(args, callInfo) } }
internal val SprudelPattern._often by dslPatternExtension { pattern, args, _ -> applyOften(pattern, args) }
internal val String._often by dslStringExtension { pattern, args, callInfo -> pattern._often(args, callInfo) }
internal val PatternMapperFn._often by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_often(args, callInfo))
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
fun SprudelPattern.often(mapper: PatternMapperFn): SprudelPattern = this._often(listOf(mapper).asSprudelDslArgs())

/** Applies `transform` with 75% probability per event. Shorthand for `sometimesBy(0.75, fn)`. */
@SprudelDsl
fun String.often(mapper: PatternMapperFn): SprudelPattern = this._often(listOf(mapper).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that applies `mapper` with 75% probability per event. */
@SprudelDsl
fun often(mapper: PatternMapperFn): PatternMapperFn = _often(listOf(mapper).asSprudelDslArgs())

/** Chains an often (75% per event) onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.often(mapper: PatternMapperFn): PatternMapperFn = this._often(listOf(mapper).asSprudelDslArgs())

// -- rarely() ---------------------------------------------------------------------------------------------------------

fun applyRarely(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.25
    return pattern.`when`(rand.lt(x), transform)
}

internal val _rarely by dslPatternMapper { args, callInfo -> { p -> p._rarely(args, callInfo) } }
internal val SprudelPattern._rarely by dslPatternExtension { pattern, args, _ -> applyRarely(pattern, args) }
internal val String._rarely by dslStringExtension { pattern, args, callInfo -> pattern._rarely(args, callInfo) }
internal val PatternMapperFn._rarely by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_rarely(args, callInfo))
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
fun SprudelPattern.rarely(mapper: PatternMapperFn): SprudelPattern = this._rarely(listOf(mapper).asSprudelDslArgs())

/** Applies `transform` with 25% probability per event. Shorthand for `sometimesBy(0.25, fn)`. */
@SprudelDsl
fun String.rarely(mapper: PatternMapperFn): SprudelPattern = this._rarely(listOf(mapper).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that applies `mapper` with 25% probability per event. */
@SprudelDsl
fun rarely(mapper: PatternMapperFn): PatternMapperFn = _rarely(listOf(mapper).asSprudelDslArgs())

/** Chains a rarely (25% per event) onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.rarely(mapper: PatternMapperFn): PatternMapperFn = this._rarely(listOf(mapper).asSprudelDslArgs())

// -- almostNever() ----------------------------------------------------------------------------------------------------

fun applyAlmostNever(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.1
    return pattern.`when`(rand.lt(x), transform)
}

internal val _almostNever by dslPatternMapper { args, callInfo -> { p -> p._almostNever(args, callInfo) } }
internal val SprudelPattern._almostNever by dslPatternExtension { pattern, args, _ ->
    applyAlmostNever(pattern, args)
}
internal val String._almostNever by dslStringExtension { pattern, args, callInfo ->
    pattern._almostNever(args, callInfo)
}
internal val PatternMapperFn._almostNever by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_almostNever(args, callInfo))
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
fun SprudelPattern.almostNever(mapper: PatternMapperFn): SprudelPattern =
    this._almostNever(listOf(mapper).asSprudelDslArgs())

/** Applies `transform` with 10% probability per event. Shorthand for `sometimesBy(0.1, fn)`. */
@SprudelDsl
fun String.almostNever(mapper: PatternMapperFn): SprudelPattern = this._almostNever(listOf(mapper).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that applies `mapper` with 10% probability per event. */
@SprudelDsl
fun almostNever(mapper: PatternMapperFn): PatternMapperFn = _almostNever(listOf(mapper).asSprudelDslArgs())

/** Chains an almostNever (10% per event) onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.almostNever(mapper: PatternMapperFn): PatternMapperFn =
    this._almostNever(listOf(mapper).asSprudelDslArgs())

// -- almostAlways() ---------------------------------------------------------------------------------------------------

fun applyAlmostAlways(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.9
    return pattern.`when`(rand.lt(x), transform)
}

internal val _almostAlways by dslPatternMapper { args, callInfo -> { p -> p._almostAlways(args, callInfo) } }
internal val SprudelPattern._almostAlways by dslPatternExtension { pattern, args, _ ->
    applyAlmostAlways(pattern, args)
}
internal val String._almostAlways by dslStringExtension { pattern, args, callInfo ->
    pattern._almostAlways(args, callInfo)
}
internal val PatternMapperFn._almostAlways by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_almostAlways(args, callInfo))
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
fun SprudelPattern.almostAlways(mapper: PatternMapperFn): SprudelPattern =
    this._almostAlways(listOf(mapper).asSprudelDslArgs())

/** Applies `transform` with 90% probability per event. Shorthand for `sometimesBy(0.9, fn)`. */
@SprudelDsl
fun String.almostAlways(mapper: PatternMapperFn): SprudelPattern = this._almostAlways(listOf(mapper).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that applies `mapper` with 90% probability per event. */
@SprudelDsl
fun almostAlways(mapper: PatternMapperFn): PatternMapperFn = _almostAlways(listOf(mapper).asSprudelDslArgs())

/** Chains an almostAlways (90% per event) onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.almostAlways(mapper: PatternMapperFn): PatternMapperFn =
    this._almostAlways(listOf(mapper).asSprudelDslArgs())

// -- never() ----------------------------------------------------------------------------------------------------------

internal val _never by dslPatternMapper { args, callInfo -> { p -> p._never(args, callInfo) } }
internal val SprudelPattern._never by dslPatternExtension { pattern, _, _ -> pattern }
internal val String._never by dslStringExtension { pattern, args, callInfo -> pattern._never(args, callInfo) }
internal val PatternMapperFn._never by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_never(args, callInfo))
}

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
fun SprudelPattern.never(mapper: PatternMapperFn): SprudelPattern = this._never(listOf(mapper).asSprudelDslArgs())

/** Never applies `transform` — the pattern passes through unchanged. */
@SprudelDsl
fun String.never(mapper: PatternMapperFn): SprudelPattern = this._never(listOf(mapper).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that never applies `mapper` — source passes through unchanged. */
@SprudelDsl
fun never(mapper: PatternMapperFn): PatternMapperFn = _never(listOf(mapper).asSprudelDslArgs())

/** Chains a never (no-op) onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.never(mapper: PatternMapperFn): PatternMapperFn = this._never(listOf(mapper).asSprudelDslArgs())

// -- always() ---------------------------------------------------------------------------------------------------------

fun applyAlways(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val func = args.getOrNull(0).toPatternMapper()
    return func?.invoke(pattern) ?: pattern
}

internal val _always by dslPatternMapper { args, callInfo -> { p -> p._always(args, callInfo) } }
internal val SprudelPattern._always by dslPatternExtension { pattern, args, _ -> applyAlways(pattern, args) }
internal val String._always by dslStringExtension { pattern, args, callInfo -> pattern._always(args, callInfo) }
internal val PatternMapperFn._always by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_always(args, callInfo))
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
fun SprudelPattern.always(mapper: PatternMapperFn): SprudelPattern = this._always(listOf(mapper).asSprudelDslArgs())

/** Always applies `transform`. Shorthand for `sometimesBy(1, fn)`. */
@SprudelDsl
fun String.always(mapper: PatternMapperFn): SprudelPattern = this._always(listOf(mapper).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that always applies `mapper` unconditionally. */
@SprudelDsl
fun always(mapper: PatternMapperFn): PatternMapperFn = _always(listOf(mapper).asSprudelDslArgs())

/** Chains an always (unconditional apply) onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.always(mapper: PatternMapperFn): PatternMapperFn = this._always(listOf(mapper).asSprudelDslArgs())

// -- someCyclesBy() ---------------------------------------------------------------------------------------------------

fun applySomeCyclesBy(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // Delegate to applySometimesBy with seedByCycle = true
    return applySometimesBy(pattern = pattern, args = args, seedByCycle = true)
}

internal val _someCyclesBy by dslPatternMapper { args, callInfo -> { p -> p._someCyclesBy(args, callInfo) } }
internal val SprudelPattern._someCyclesBy by dslPatternExtension { pattern, args, _ ->
    applySomeCyclesBy(pattern, args)
}
internal val String._someCyclesBy by dslStringExtension { pattern, args, callInfo ->
    pattern._someCyclesBy(args, callInfo)
}
internal val PatternMapperFn._someCyclesBy by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_someCyclesBy(args, callInfo))
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
fun SprudelPattern.someCyclesBy(prob: PatternLike, mapper: PatternMapperFn): SprudelPattern =
    this._someCyclesBy(listOf(prob, mapper).asSprudelDslArgs())

/** Applies `transform` with the given probability, decided once per cycle (not per event). */
@SprudelDsl
fun String.someCyclesBy(prob: PatternLike, mapper: PatternMapperFn): SprudelPattern =
    this._someCyclesBy(listOf(prob, mapper).asSprudelDslArgs())

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
fun someCyclesBy(prob: PatternLike, mapper: PatternMapperFn): PatternMapperFn =
    _someCyclesBy(listOf(prob, mapper).asSprudelDslArgs())

/** Chains a someCyclesBy onto this [PatternMapperFn]; applies inner mapper at the given per-cycle probability. */
@SprudelDsl
fun PatternMapperFn.someCyclesBy(prob: PatternLike, mapper: PatternMapperFn): PatternMapperFn =
    this._someCyclesBy(listOf(prob, mapper).asSprudelDslArgs())

// -- someCycles() -----------------------------------------------------------------------------------------------------

internal val _someCycles by dslPatternMapper { args, callInfo -> { p -> p._someCycles(args, callInfo) } }
internal val SprudelPattern._someCycles by dslPatternExtension { pattern, args, _ ->
    applySomeCyclesBy(pattern, args)
}
internal val String._someCycles by dslStringExtension { pattern, args, callInfo ->
    pattern._someCycles(args, callInfo)
}
internal val PatternMapperFn._someCycles by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_someCycles(args, callInfo))
}

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
fun SprudelPattern.someCycles(mapper: PatternMapperFn): SprudelPattern =
    this._someCycles(listOf(mapper).asSprudelDslArgs())

/** Applies `transform` with 50% probability per cycle. Shorthand for `someCyclesBy(0.5, fn)`. */
@SprudelDsl
fun String.someCycles(mapper: PatternMapperFn): SprudelPattern = this._someCycles(listOf(mapper).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that applies `mapper` with 50% probability per cycle. */
@SprudelDsl
fun someCycles(mapper: PatternMapperFn): PatternMapperFn = _someCycles(listOf(mapper).asSprudelDslArgs())

/** Chains a someCycles (50% per cycle) onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.someCycles(mapper: PatternMapperFn): PatternMapperFn =
    this._someCycles(listOf(mapper).asSprudelDslArgs())

// -- randL() ----------------------------------------------------------------------------------------------------------

internal val _randL by dslPatternFunction { args, /* callInfo */ _ ->
    val nArg = args.getOrNull(0)
    val nVal = nArg?.value

    val nPattern: SprudelPattern = (nArg ?: SprudelDslArg.of("0")).toPattern()

    val staticN = nVal?.asIntOrNull()

    RandLPattern.create(nPattern, staticN)
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
fun randL(n: PatternLike): SprudelPattern = _randL(listOf(n).asSprudelDslArgs())

// -- randrun() --------------------------------------------------------------------------------------------------------

internal val _randrun by dslPatternFunction { args, /* callInfo */ _ ->
    val nArg = args.getOrNull(0)
    val nVal = nArg?.value

    val nPattern: SprudelPattern = (nArg ?: SprudelDslArg.of("0")).toPattern()

    val staticN = nVal?.asIntOrNull()

    if (staticN != null) {
        // Static path
        if (staticN < 1) {
            silence
        } else {
            val atom = AtomicPattern.pure
            val events = (0..<staticN).map { index ->
                atom.reinterpret { evt, ctx ->
                    val ctx = ctx.update {
                        setIfAbsent(QueryContext.randomSeedKey, 0)
                    }
                    val cycle = evt.part.begin.floor()
                    val random = ctx.getSeededRandom(cycle, "randrun")
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
fun randrun(n: PatternLike): SprudelPattern = _randrun(listOf(n).asSprudelDslArgs())

// -- shuffle() --------------------------------------------------------------------------------------------------------

internal val _shuffle by dslPatternMapper { args, callInfo -> { p -> p._shuffle(args, callInfo) } }
internal val SprudelPattern._shuffle by dslPatternExtension { p, args, /* callInfo */ _ ->
    val nArg: Any = args.getOrNull(0) ?: 4
    val newArgs = listOf(nArg)
    val indices = _randrun(newArgs)
    p.bite(nArg, indices)
}
internal val String._shuffle by dslStringExtension { p, args, callInfo -> p._shuffle(args, callInfo) }
internal val PatternMapperFn._shuffle by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_shuffle(args, callInfo))
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
fun SprudelPattern.shuffle(n: PatternLike): SprudelPattern = this._shuffle(listOf(n).asSprudelDslArgs())

/** Slices the pattern into `n` equal parts and plays them in a new random order each cycle. */
@SprudelDsl
fun String.shuffle(n: PatternLike): SprudelPattern = this._shuffle(listOf(n).asSprudelDslArgs())

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
fun shuffle(n: PatternLike): PatternMapperFn = _shuffle(listOf(n).asSprudelDslArgs())

/** Chains a shuffle onto this [PatternMapperFn]; randomly reorders `n` equal slices each cycle. */
@SprudelDsl
fun PatternMapperFn.shuffle(n: PatternLike): PatternMapperFn = this._shuffle(listOf(n).asSprudelDslArgs())

// -- scramble() -------------------------------------------------------------------------------------------------------

internal val _scramble by dslPatternMapper { args, callInfo -> { p -> p._scramble(args, callInfo) } }
internal val SprudelPattern._scramble by dslPatternExtension { p, args, /* callInfo */ _ ->
    val nArg: SprudelDslArg<Any?> = args.getOrNull(0) ?: SprudelDslArg.of(4)
    val indices = _irand(listOf(nArg))._segment(nArg)
    p.bite(nArg, indices)
}
internal val String._scramble by dslStringExtension { p, args, callInfo -> p._scramble(args, callInfo) }
internal val PatternMapperFn._scramble by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_scramble(args, callInfo))
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
fun SprudelPattern.scramble(n: PatternLike): SprudelPattern = this._scramble(listOf(n).asSprudelDslArgs())

/** Slices the pattern into `n` equal parts and picks slices at random each cycle. */
@SprudelDsl
fun String.scramble(n: PatternLike): SprudelPattern = this._scramble(listOf(n).asSprudelDslArgs())

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
fun scramble(n: PatternLike): PatternMapperFn = _scramble(listOf(n).asSprudelDslArgs())

/** Chains a scramble onto this [PatternMapperFn]; randomly picks `n` slices with replacement. */
@SprudelDsl
fun PatternMapperFn.scramble(n: PatternLike): PatternMapperFn = this._scramble(listOf(n).asSprudelDslArgs())

// -- chooseWith() -----------------------------------------------------------------------------------------------------

internal val _chooseWith by dslPatternFunction { args, /* callInfo */ _ ->
    val firstArg = args.getOrNull(0)

    when (val firstVal = firstArg?.value) {
        is SprudelPattern -> firstVal._chooseWith(args.drop(1))
        else -> AtomicPattern.pure._chooseWith(args)
    }
}

internal val SprudelPattern._chooseWith by dslPatternExtension { p, args, /* callInfo */ _ ->
    val xs = args.extractChoiceArgs()

    ChoicePattern.createFromRaw(p, xs, mode = StructurePattern.Mode.Out)
}

internal val String._chooseWith by dslStringExtension { p, args, callInfo -> p._chooseWith(args, callInfo) }

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
fun chooseWith(vararg args: PatternLike): SprudelPattern = _chooseWith(args.toList().asSprudelDslArgs())

/** Uses this pattern (range 0–1) as a selector to choose from the given list. */
@SprudelDsl
fun SprudelPattern.chooseWith(vararg args: PatternLike): SprudelPattern =
    this._chooseWith(args.toList().asSprudelDslArgs())

/** Uses this string pattern as a selector to choose from the given list. */
@SprudelDsl
fun String.chooseWith(vararg args: PatternLike): SprudelPattern = this._chooseWith(args.toList().asSprudelDslArgs())

// -- chooseInWith() ---------------------------------------------------------------------------------------------------

internal val _chooseInWith by dslPatternFunction { args, /* callInfo */ _ ->
    val firstArg = args.getOrNull(0)

    when (val firstVal = firstArg?.value) {
        is SprudelPattern -> firstVal._chooseInWith(args.drop(1))
        else -> AtomicPattern.pure._chooseInWith(args)
    }
}

internal val SprudelPattern._chooseInWith by dslPatternExtension { p, args, /* callInfo */ _ ->
    val xs = args.extractChoiceArgs()

    ChoicePattern.createFromRaw(p, xs, mode = StructurePattern.Mode.In)
}

internal val String._chooseInWith by dslStringExtension { p, args, callInfo -> p._chooseInWith(args, callInfo) }

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
fun chooseInWith(vararg args: PatternLike): SprudelPattern = _chooseInWith(args.toList().asSprudelDslArgs())

/** Uses this pattern as a selector to choose from the given list (structure from chosen). */
@SprudelDsl
fun SprudelPattern.chooseInWith(vararg args: PatternLike): SprudelPattern =
    this._chooseInWith(args.toList().asSprudelDslArgs())

/** Uses this string pattern as a selector to choose from the given list (structure from chosen). */
@SprudelDsl
fun String.chooseInWith(vararg args: PatternLike): SprudelPattern =
    this._chooseInWith(args.toList().asSprudelDslArgs())

// -- choose() ---------------------------------------------------------------------------------------------------------

internal val _choose by dslPatternFunction { args, /* callInfo */ _ ->
    val firstArg = args.getOrNull(0)

    when (val firstVal = firstArg?.value) {
        is SprudelPattern -> firstVal._choose(args.drop(1))
        else -> AtomicPattern.pure._choose(args)
    }
}

internal val SprudelPattern._choose by dslPatternExtension { p, args, /* callInfo */ _ ->
    val xs = args.extractChoiceArgs()

    ChoicePattern.createFromRaw(p, xs, mode = StructurePattern.Mode.Out)
}

internal val String._choose by dslStringExtension { p, args, callInfo -> p._choose(args, callInfo) }

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
fun choose(vararg args: PatternLike): SprudelPattern = _choose(args.toList().asSprudelDslArgs())

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
fun SprudelPattern.choose(vararg args: PatternLike): SprudelPattern = this._choose(args.toList().asSprudelDslArgs())

/** Uses this string pattern as a selector to choose from the given list. */
@SprudelDsl
fun String.choose(vararg args: PatternLike): SprudelPattern = this._choose(args.toList().asSprudelDslArgs())

// -- chooseOut() ------------------------------------------------------------------------------------------------------

internal val _chooseOut by dslPatternFunction { args, /* callInfo */ _ -> _choose(args) }
internal val SprudelPattern._chooseOut by dslPatternExtension { p, args, callInfo -> p._choose(args, callInfo) }
internal val String._chooseOut by dslStringExtension { p, args, callInfo -> p._chooseOut(args, callInfo) }

/**
 * Alias for [choose].
 *
 * @param args Values or patterns to randomly choose from at each event.
 * @alias choose
 * @category random
 * @tags chooseOut, choose, random, selection
 */
@SprudelDsl
fun chooseOut(vararg args: PatternLike): SprudelPattern = _chooseOut(args.toList().asSprudelDslArgs())

/** Alias for [choose]. */
@SprudelDsl
fun SprudelPattern.chooseOut(vararg args: PatternLike): SprudelPattern =
    this._chooseOut(args.toList().asSprudelDslArgs())

/** Alias for [choose]. */
@SprudelDsl
fun String.chooseOut(vararg args: PatternLike): SprudelPattern = this._chooseOut(args.toList().asSprudelDslArgs())

// -- chooseIn() -------------------------------------------------------------------------------------------------------

internal val _chooseIn by dslPatternFunction { args, /* callInfo */ _ ->
    val firstArg = args.getOrNull(0)

    when (val firstVal = firstArg?.value) {
        is SprudelPattern -> firstVal._chooseIn(args.drop(1))
        else -> AtomicPattern.pure._chooseIn(args)
    }
}

internal val SprudelPattern._chooseIn by dslPatternExtension { p, args, /* callInfo */ _ ->
    val xs = args.extractChoiceArgs()

    ChoicePattern.createFromRaw(p, xs, mode = StructurePattern.Mode.In)
}

internal val String._chooseIn by dslStringExtension { p, args, callInfo -> p._chooseIn(args, callInfo) }

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
fun chooseIn(vararg args: PatternLike): SprudelPattern = _chooseIn(args.toList().asSprudelDslArgs())

/** Uses this pattern as a selector; structure comes from the chosen value. */
@SprudelDsl
fun SprudelPattern.chooseIn(vararg args: PatternLike): SprudelPattern =
    this._chooseIn(args.toList().asSprudelDslArgs())

/** Uses this string pattern as a selector; structure comes from the chosen value. */
@SprudelDsl
fun String.chooseIn(vararg args: PatternLike): SprudelPattern = this._chooseIn(args.toList().asSprudelDslArgs())

// -- choose2() --------------------------------------------------------------------------------------------------------

internal val SprudelPattern._choose2 by dslPatternExtension { p, args, /* callInfo */ _ ->
    val xs = args.extractChoiceArgs()

    ChoicePattern.createFromRaw(p.fromBipolar(), xs, mode = StructurePattern.Mode.Out)
}

internal val String._choose2 by dslStringExtension { p, args, callInfo -> p._choose2(args, callInfo) }

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
fun SprudelPattern.choose2(vararg args: PatternLike): SprudelPattern = this._choose2(args.toList().asSprudelDslArgs())

/** Like `choose`, but the selector pattern should be in the range -1 to 1 (bipolar). */
@SprudelDsl
fun String.choose2(vararg args: PatternLike): SprudelPattern = this._choose2(args.toList().asSprudelDslArgs())

// -- chooseCycles() ---------------------------------------------------------------------------------------------------

internal val _chooseCycles by dslPatternFunction { args, /* callInfo */ _ ->
    val firstArg = args.getOrNull(0)

    when (val firstVal = firstArg?.value) {
        is SprudelPattern -> firstVal._chooseCycles(args.drop(1))
        else -> AtomicPattern.pure._chooseCycles(args)
    }
}

internal val SprudelPattern._chooseCycles by dslPatternExtension { p, args, /* callInfo */ _ ->
    val xs = (listOf(p) + args.map { it.value }).asSprudelDslArgs()
    ChoicePattern.createFromRaw(rand.segment(1), xs, mode = StructurePattern.Mode.In)
}

internal val String._chooseCycles by dslStringExtension { p, args, /* callInfo */ _ ->
    val xs = (listOf(p) + args.map { it.value }).asSprudelDslArgs()
    ChoicePattern.createFromRaw(rand.segment(1), xs, mode = StructurePattern.Mode.In)
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
fun chooseCycles(vararg args: PatternLike): SprudelPattern = _chooseCycles(args.toList().asSprudelDslArgs())

/** Picks one of the given values at random, changing once per cycle. */
@SprudelDsl
fun SprudelPattern.chooseCycles(vararg args: PatternLike): SprudelPattern =
    this._chooseCycles(args.toList().asSprudelDslArgs())

/** Picks one of the given values at random, changing once per cycle. */
@SprudelDsl
fun String.chooseCycles(vararg args: PatternLike): SprudelPattern =
    this._chooseCycles(args.toList().asSprudelDslArgs())

// -- randcat() --------------------------------------------------------------------------------------------------------

internal val _randcat by dslPatternFunction { args, /* callInfo */ _ -> _chooseCycles(args) }
internal val SprudelPattern._randcat by dslPatternExtension { p, args, callInfo -> p._chooseCycles(args, callInfo) }
internal val String._randcat by dslStringExtension { p, args, callInfo -> p._randcat(args, callInfo) }

/**
 * Alias for [chooseCycles].
 *
 * @param args Values or patterns to randomly pick from; one is chosen per cycle.
 * @alias chooseCycles
 * @category random
 * @tags randcat, chooseCycles, random, cycle
 */
@SprudelDsl
fun randcat(vararg args: PatternLike): SprudelPattern = _randcat(args.toList().asSprudelDslArgs())

/** Alias for [chooseCycles]. */
@SprudelDsl
fun SprudelPattern.randcat(vararg args: PatternLike): SprudelPattern = this._randcat(args.toList().asSprudelDslArgs())

/** Alias for [chooseCycles]. */
@SprudelDsl
fun String.randcat(vararg args: PatternLike): SprudelPattern = this._randcat(args.toList().asSprudelDslArgs())

// -- wchoose() --------------------------------------------------------------------------------------------------------

internal val _wchoose by dslPatternFunction { args, /* callInfo */ _ ->
    val firstArg = args.getOrNull(0)

    when (val firstVal = firstArg?.value) {
        is SprudelPattern -> firstVal._wchoose(args.drop(1))
        else -> AtomicPattern.pure._wchoose(args)
    }
}

internal val SprudelPattern._wchoose by dslPatternExtension { p, args, /* callInfo */ _ ->
    val (items, weights) = args.extractWeightedPairs()

    ChoicePattern.createFromRaw(
        selector = p,
        choices = items,
        weights = weights,
        mode = StructurePattern.Mode.Out,
    )
}

internal val String._wchoose by dslStringExtension { p, args, callInfo -> p._wchoose(args, callInfo) }

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
fun wchoose(vararg args: PatternLike): SprudelPattern = _wchoose(args.toList().asSprudelDslArgs())

/** Uses this pattern (range 0–1) as a weighted selector over the given value/weight pairs. */
@SprudelDsl
fun SprudelPattern.wchoose(vararg args: PatternLike): SprudelPattern = this._wchoose(args.toList().asSprudelDslArgs())

/** Uses this string pattern as a weighted selector over the given value/weight pairs. */
@SprudelDsl
fun String.wchoose(vararg args: PatternLike): SprudelPattern = this._wchoose(args.toList().asSprudelDslArgs())

// -- wchooseCycles() --------------------------------------------------------------------------------------------------

internal val _wchooseCycles by dslPatternFunction { args, /* callInfo */ _ ->
    val firstArg = args.getOrNull(0)

    when (val firstVal = firstArg?.value) {
        is SprudelPattern -> firstVal._wchooseCycles(args.drop(1))
        else -> AtomicPattern.pure._wchooseCycles(args)
    }
}

internal val SprudelPattern._wchooseCycles by dslPatternExtension { p, args, /* callInfo */ _ ->
    val (items, weights) = args.extractWeightedPairs()
    val allItems = (listOf(p) + items.map { it.value }).asSprudelDslArgs()
    val allWeights = (listOf(1.0) + weights.map { it.value }).asSprudelDslArgs()

    ChoicePattern.createFromRaw(
        selector = rand.segment(1),
        choices = allItems,
        weights = allWeights,
        mode = StructurePattern.Mode.In,
    )
}

internal val String._wchooseCycles by dslStringExtension { p, args, callInfo -> p._wchooseCycles(args, callInfo) }

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
fun wchooseCycles(vararg args: PatternLike): SprudelPattern = _wchooseCycles(args.toList().asSprudelDslArgs())

/** Picks a weighted random value once per cycle. */
@SprudelDsl
fun SprudelPattern.wchooseCycles(vararg args: PatternLike): SprudelPattern =
    this._wchooseCycles(args.toList().asSprudelDslArgs())

/** Picks a weighted random value once per cycle. */
@SprudelDsl
fun String.wchooseCycles(vararg args: PatternLike): SprudelPattern =
    this._wchooseCycles(args.toList().asSprudelDslArgs())

// -- wrandcat() -------------------------------------------------------------------------------------------------------

internal val _wrandcat by dslPatternFunction { args, /* callInfo */ _ -> _wchooseCycles(args) }
internal val SprudelPattern._wrandcat by dslPatternExtension { p, args, callInfo -> p._wchooseCycles(args, callInfo) }
internal val String._wrandcat by dslStringExtension { p, args, callInfo -> p._wrandcat(args, callInfo) }

/**
 * Alias for [wchooseCycles].
 *
 * @param args `[value, weight]` pairs — higher weight means more likely selection. One is chosen per cycle.
 * @alias wchooseCycles
 * @category random
 * @tags wrandcat, wchooseCycles, weighted, random, cycle
 */
@SprudelDsl
fun wrandcat(vararg args: PatternLike): SprudelPattern = _wrandcat(args.toList().asSprudelDslArgs())

/** Alias for [wchooseCycles]. */
@SprudelDsl
fun SprudelPattern.wrandcat(vararg args: PatternLike): SprudelPattern =
    this._wrandcat(args.toList().asSprudelDslArgs())

/** Alias for [wchooseCycles]. */
@SprudelDsl
fun String.wrandcat(vararg args: PatternLike): SprudelPattern = this._wrandcat(args.toList().asSprudelDslArgs())
