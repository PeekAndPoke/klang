@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.*
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.strudel.pattern.*
import io.peekandpoke.klang.strudel.pattern.ReinterpretPattern.Companion.reinterpret
import kotlin.math.floor

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangRandomInit = false

// -- Helpers ----------------------------------------------------------------------------------------------------------

fun applyRandomSeed(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val seed = args.getOrNull(0)?.value?.asLongOrNull()

    return ContextModifierPattern(source = pattern) {
        if (seed != null) {
            set(QueryContext.randomSeedKey, seed)
        } else {
            remove(QueryContext.randomSeedKey)
        }
    }
}

// -- seed() -----------------------------------------------------------------------------------------------------------

/**
 * Sets the random seed for the pattern, making all random operations reproducible.
 *
 * All random-based functions (`rand`, `degradeBy`, `sometimes`, etc.) use the seed to generate
 * deterministic pseudo-random values. Calling `seed(n)` with the same `n` always produces the
 * same random sequence, which is useful for reproducible live performances.
 *
 * ```KlangScript
 * s("bd sd hh").degradeBy(0.3).seed(42)     // reproducible random removal
 * ```
 *
 * ```KlangScript
 * note("c d e f").sometimes { it.rev() }.seed(7)   // fixed random decisions
 * ```
 *
 * @alias withSeed
 * @category random
 * @tags seed, random, reproducible, deterministic
 */
@StrudelDsl
val StrudelPattern.seed by dslPatternExtension { pattern, args, /* callInfo */ _ -> applyRandomSeed(pattern, args) }

/**
 * Alias for `seed`. Sets the random seed for reproducible random operations.
 *
 * @alias seed
 * @category random
 * @tags withSeed, seed, random, reproducible
 */
@StrudelDsl
val StrudelPattern.withSeed by dslPatternExtension { pattern, args, /* callInfo */ _ -> applyRandomSeed(pattern, args) }

/** Sets the random seed for reproducible random operations. */
@StrudelDsl
val String.seed by dslStringExtension { pattern, args, /* callInfo */ _ -> applyRandomSeed(pattern, args) }

/** Sets the random seed for reproducible random operations. */
@StrudelDsl
val String.withSeed by dslStringExtension { pattern, args, /* callInfo */ _ -> applyRandomSeed(pattern, args) }

// -- rand() / rand2() -------------------------------------------------------------------------------------------------

/**
 * Continuous random pattern producing values in the range 0–1.
 *
 * `rand` generates a unique pseudo-random value for each point in time. Use `range()` to
 * re-map the output, `segment(n)` to discretise it into `n` steps per cycle, or pass it
 * directly to parameters that accept patterns.
 *
 * ```KlangScript
 * s("hh*8").pan(rand)                 // random panning for each hit
 * ```
 *
 * ```KlangScript
 * note("c d e f").gain(rand.range(0.5, 1.0))   // random velocity in 0.5–1.0
 * ```
 *
 * @category random
 * @tags rand, random, continuous, noise
 */
@StrudelDsl
val rand by dslObject {
    ContinuousPattern { from, _, ctx -> ctx.getSeededRandom(from, "rand").nextDouble() }
}

/**
 * Continuous random pattern producing values in the range -1–1 (bipolar).
 *
 * Equivalent to `rand.range(-1, 1)`. Useful for LFO-style modulation that oscillates
 * around zero (e.g., pitch detune, stereo panning centred at 0).
 *
 * ```KlangScript
 * s("hh*8").pan(rand2)                // bipolar random panning around centre
 * ```
 *
 * ```KlangScript
 * note("c4").detune(rand2.range(-10, 10))   // slight random detune
 * ```
 *
 * @category random
 * @tags rand2, random, bipolar, continuous, noise
 */
@StrudelDsl
val rand2 by dslObject { rand.range(-1.0, 1.0) }

/**
 * Continuous random pattern that holds a constant value for each full cycle.
 *
 * All events within the same cycle get the same random value; adjacent cycles get
 * different values. Useful for cycle-level random decisions (e.g. `someCyclesBy`).
 * Equivalent to `rand.segment(1)`.
 *
 * ```KlangScript
 * s("bd*8").degradeByWith(randCycle, 0.5)   // entire cycle either plays or drops
 * ```
 *
 * @category random
 * @tags randCycle, random, cycle, hold, step
 */
@StrudelDsl
val randCycle by dslObject {
    ContinuousPattern { fromTime, _, ctx ->
        ctx.getSeededRandom(floor(fromTime), "randCycle").nextDouble()
    }
}

// -- brand() / brandBy() ----------------------------------------------------------------------------------------------

/**
 * Binary random pattern: outputs 0 or 1 with the given probability of returning 1.
 *
 * At each point in time, `brandBy(p)` produces 1 with probability `p` and 0 otherwise.
 * Useful for random gate or switch patterns.
 *
 * ```KlangScript
 * s("hh*10").pan(brandBy(0.2))        // 20% chance of value 1, else 0
 * ```
 *
 * ```KlangScript
 * note("c d e f").gain(brandBy(0.5))  // random full/zero gain on each event
 * ```
 *
 * @category random
 * @tags brandBy, binary, random, gate, probability
 */
@StrudelDsl
val brandBy by dslFunction { args, /* callInfo */ _ ->
    val probArg = args.getOrNull(0)
    val probVal = probArg?.value

    val probPattern: StrudelPattern = (probArg ?: StrudelDslArg.of("0.5")).toPattern()

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
 * Binary random pattern with 50% probability — outputs 0 or 1 with equal chance.
 *
 * Shorthand for `brandBy(0.5)`. Use it wherever you need a random on/off gate signal.
 *
 * ```KlangScript
 * s("hh*8").gain(brand)               // each hi-hat randomly at full or zero gain
 * ```
 *
 * @category random
 * @tags brand, brandBy, binary, random, gate
 */
@StrudelDsl
val brand by dslObject { brandBy(0.5) }

// -- irand() ----------------------------------------------------------------------------------------------------------

/**
 * Continuous pattern of random integers in the range `0` to `n - 1`.
 *
 * Generates a new random integer at each distinct point in time. Useful for randomly
 * selecting scale degrees, sample numbers, or any indexed choice.
 *
 * ```KlangScript
 * n(irand(8)).scale("C:minor").note()         // random scale degree 0–7 each event
 * ```
 *
 * ```KlangScript
 * s("bd*8").n(irand(6))                       // random sample variant 0–5 per hit
 * ```
 *
 * @category random
 * @tags irand, random, integer, continuous
 */
@StrudelDsl
val irand by dslFunction { args, /* callInfo */ _ ->
    val nArg = args.getOrNull(0)
    val nVal = nArg?.value

    val nPattern: StrudelPattern = (nArg ?: StrudelDslArg.of("0")).toPattern()

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

// -- degradeBy() ------------------------------------------------------------------------------------------------------

fun applyDegradeBy(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    // degradeBy(x) is just degradeByWith(rand, x)
    return applyDegradeByWith(pattern, listOf(StrudelDslArg.of(rand)) + args)
}

/**
 * Randomly removes events from the pattern with the given probability.
 *
 * `degradeBy(x)` keeps each event with probability `(1 - x)`. At `0` nothing is removed;
 * at `1` every event is removed. The randomness is deterministic per cycle (seeded).
 *
 * ```KlangScript
 * s("bd sd hh cp").degradeBy(0.3)     // ~30% of hits are silenced randomly
 * ```
 *
 * ```KlangScript
 * note("c d e f").degradeBy(0.5)      // roughly half the notes play each cycle
 * ```
 *
 * @category random
 * @tags degradeBy, random, remove, probability, drop
 */
@StrudelDsl
val StrudelPattern.degradeBy by dslPatternExtension { pattern, args, /* callInfo */ _ -> applyDegradeBy(pattern, args) }

/** Randomly removes events with the given probability (0 = none removed, 1 = all removed). */
@StrudelDsl
val String.degradeBy by dslStringExtension { pattern, args, /* callInfo */ _ -> applyDegradeBy(pattern, args) }

// -- degrade() --------------------------------------------------------------------------------------------------------

/**
 * Randomly removes events with a 50% probability. Shorthand for `degradeBy(0.5)`.
 *
 * ```KlangScript
 * s("bd sd hh cp").degrade()          // ~half the events play per cycle
 * ```
 *
 * @category random
 * @tags degrade, degradeBy, random, remove, probability
 */
@StrudelDsl
val StrudelPattern.degrade by dslPatternExtension { pattern, args, /* callInfo */ _ -> applyDegradeBy(pattern, args) }

/** Randomly removes events with a 50% probability. Shorthand for `degradeBy(0.5)`. */
@StrudelDsl
val String.degrade by dslStringExtension { pattern, args, /* callInfo */ _ -> applyDegradeBy(pattern, args) }

// -- degradeByWith() --------------------------------------------------------------------------------------------------

fun applyDegradeByWith(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    // JavaScript: pat.fmap((a) => (_) => a).appLeft(withPat.filterValues((v) => v > x))
    // Keeps events where withPat > x
    // Examples:
    //   degradeByWith(rand, 0.2) -> keep where rand > 0.2 (~80% kept)
    //   degradeByWith(rand, 0.5) -> keep where rand > 0.5 (~50% kept)
    //   degradeByWith(rand, 0.8) -> keep where rand > 0.8 (~20% kept)
    val withPat = args.getOrNull(0)?.toPattern() ?: return pattern
    val xPat = (args.getOrNull(1) ?: StrudelDslArg.of(0.5)).toPattern()

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
 * ```KlangScript
 * s("bd*8").degradeByWith(rand.segment(1), 0.5)   // whole cycle either plays or drops
 * ```
 *
 * ```KlangScript
 * s("hh*16").degradeByWith(sine.range(0, 1), 0.5) // sine-wave controlled removal
 * ```
 *
 * @category random
 * @tags degradeByWith, random, remove, custom, probability
 */
@StrudelDsl
val StrudelPattern.degradeByWith by dslPatternExtension { pattern, args, _ -> applyDegradeByWith(pattern, args) }

/** Randomly removes events using a custom random-value pattern as the randomness source. */
@StrudelDsl
val String.degradeByWith by dslStringExtension { pattern, args, callInfo -> pattern.degradeByWith(args, callInfo) }

// -- undegradeBy() ----------------------------------------------------------------------------------------------------

fun applyUndegradeBy(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    // undegradeBy(x) is just undegradeByWith(rand, x)
    // undegradeBy(0) = 100% removal, undegradeBy(1) = 0% removal
    return applyUndegradeByWith(pattern, listOf(StrudelDslArg.of(rand)) + args)
}

/**
 * Inverse of `degradeBy`: keeps events that `degradeBy` would remove, and vice versa.
 *
 * `undegradeBy(x)` keeps an event with probability `x` (0 = nothing kept, 1 = everything kept).
 * When paired with `degradeBy` using the same seed, the two are perfectly complementary.
 *
 * ```KlangScript
 * s("hh*8").undegradeBy(0.2)                   // only ~20% of hits play
 * ```
 *
 * ```KlangScript
 * s("hh*10").layer(
 *   x => x.degradeBy(0.2).pan(0),
 *   x => x.undegradeBy(0.8).pan(1)
 * )                                             // complementary panning layers
 * ```
 *
 * @category random
 * @tags undegradeBy, random, inverse, keep, probability
 */
@StrudelDsl
val StrudelPattern.undegradeBy by dslPatternExtension { pattern, args, /* callInfo */ _ ->
    applyUndegradeBy(pattern, args)
}

/** Inverse of `degradeBy`: keeps events that `degradeBy` would remove (0 = none, 1 = all). */
@StrudelDsl
val String.undegradeBy by dslStringExtension { pattern, args, /* callInfo */ _ -> applyUndegradeBy(pattern, args) }

// -- undegradeByWith() --------------------------------------------------------------------------------------------------

fun applyUndegradeByWith(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    // Inverse of degradeByWith: keep where withPat >= (1 - x)
    // Keeps events where withPat >= (1 - x), which is equivalent to keeping where withPat > (1 - x) for continuous values
    // Examples:
    //   undegradeByWith(rand, 0.1) -> keep where rand >= 0.9 (~10% kept)
    //   undegradeByWith(rand, 0.5) -> keep where rand >= 0.5 (~50% kept)
    //   undegradeByWith(rand, 1.0) -> keep where rand >= 0.0 (~100% kept)
    val withPat = args.getOrNull(0)?.toPattern() ?: return pattern
    val xPat = (args.getOrNull(1) ?: StrudelDslArg.of(0.5)).toPattern()

    return pattern._lift(xPat) { x, src ->
        src.appLeft(withPat.filterValues { v -> (v?.asDouble ?: 0.0) >= (1 - x) })
    }
}

/**
 * Inverse of `degradeByWith` using a custom random-value pattern.
 *
 * Keeps an event when `withPat >= (1 - x)`, which is complementary to `degradeByWith`.
 *
 * ```KlangScript
 * s("bd*8").undegradeByWith(randCycle, 0.5)   // cycle-level complement of degradeByWith
 * ```
 *
 * @category random
 * @tags undegradeByWith, random, inverse, custom, probability
 */
@StrudelDsl
val StrudelPattern.undegradeByWith by dslPatternExtension { pattern, args, _ ->
    applyUndegradeByWith(pattern, args)
}

/** Inverse of `degradeByWith` using a custom random-value pattern. */
@StrudelDsl
val String.undegradeByWith by dslStringExtension { pattern, args, callInfo ->
    pattern.undegradeByWith(args, callInfo)
}

// -- undegrade() --------------------------------------------------------------------------------------------------------

/**
 * Keeps events with 50% probability. Inverse of `degrade` — shorthand for `undegradeBy(0.5)`.
 *
 * ```KlangScript
 * s("hh*8").undegrade()               // ~half the events play (complement of degrade)
 * ```
 *
 * @category random
 * @tags undegrade, undegradeBy, random, inverse, probability
 */
@StrudelDsl
val StrudelPattern.undegrade by dslPatternExtension { pattern, args, /* callInfo */ _ ->
    applyUndegradeBy(pattern, args)
}

/** Keeps events with 50% probability. Shorthand for `undegradeBy(0.5)`. */
@StrudelDsl
val String.undegrade by dslStringExtension { pattern, args, /* callInfo */ _ -> applyUndegradeBy(pattern, args) }

// -- sometimesBy() ----------------------------------------------------------------------------------------------------

/**
 * Randomly applies the given function by the given probability.
 */
fun applySometimesBy(
    pattern: StrudelPattern,
    args: List<StrudelDslArg<Any?>>,
    seedByCycle: Boolean = false,
): StrudelPattern {
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
 * transformed by `fn`; otherwise it plays unmodified. For cycle-level decisions (same choice
 * for all events in a cycle) use `someCyclesBy`.
 *
 * ```KlangScript
 * s("hh*8").sometimesBy(0.4) { it.speed(0.5) }   // 40% of hits at half speed
 * ```
 *
 * ```KlangScript
 * note("c d e f").sometimesBy(0.5) { it.add(12) } // 50% of notes an octave higher
 * ```
 *
 * @category random
 * @tags sometimesBy, random, probability, conditional, transform
 */
@StrudelDsl
val StrudelPattern.sometimesBy by dslPatternExtension { pattern, args, /* callInfo */ _ ->
    applySometimesBy(pattern, args)
}

/** Applies `transform` to each event independently with the given probability. */
@StrudelDsl
val String.sometimesBy by dslStringExtension { pattern, args, /* callInfo */ _ -> applySometimesBy(pattern, args) }

// -- sometimes() ------------------------------------------------------------------------------------------------------

fun applySometimes(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.5
    return pattern.`when`(rand.lt(x), transform)
}

/**
 * Applies `transform` with a 50% chance per event. Shorthand for `sometimesBy(0.5, fn)`.
 *
 * ```KlangScript
 * s("bd sd hh cp").sometimes { it.speed(2) }  // half the hits at double speed
 * ```
 *
 * ```KlangScript
 * note("c d e f").sometimes { it.add(7) }     // 50% of notes shifted a fifth up
 * ```
 *
 * @category random
 * @tags sometimes, random, probability, conditional, transform
 */
@StrudelDsl
val StrudelPattern.sometimes by dslPatternExtension { pattern, args, /* callInfo */ _ -> applySometimes(pattern, args) }

/** Applies `transform` with a 50% chance per event. Shorthand for `sometimesBy(0.5, fn)`. */
@StrudelDsl
val String.sometimes by dslStringExtension { pattern, args, /* callInfo */ _ -> applySometimes(pattern, args) }

// -- often() ----------------------------------------------------------------------------------------------------------

fun applyOften(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.75
    return pattern.`when`(rand.lt(x), transform)
}

/**
 * Applies `transform` with 75% probability per event. Shorthand for `sometimesBy(0.75, fn)`.
 *
 * ```KlangScript
 * s("hh*8").often { it.gain(0.5) }    // 75% of hi-hats at half gain
 * ```
 *
 * @category random
 * @tags often, random, probability, conditional, transform
 */
@StrudelDsl
val StrudelPattern.often by dslPatternExtension { pattern, args, /* callInfo */ _ -> applyOften(pattern, args) }

/** Applies `transform` with 75% probability per event. Shorthand for `sometimesBy(0.75, fn)`. */
@StrudelDsl
val String.often by dslStringExtension { pattern, args, /* callInfo */ _ -> applyOften(pattern, args) }

// -- rarely() ---------------------------------------------------------------------------------------------------------

fun applyRarely(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.25
    return pattern.`when`(rand.lt(x), transform)
}

/**
 * Applies `transform` with 25% probability per event. Shorthand for `sometimesBy(0.25, fn)`.
 *
 * ```KlangScript
 * note("c d e f").rarely { it.add(12) }   // only 1 in 4 notes shifted an octave
 * ```
 *
 * @category random
 * @tags rarely, random, probability, conditional, transform
 */
@StrudelDsl
val StrudelPattern.rarely by dslPatternExtension { pattern, args, /* callInfo */ _ ->
    applyRarely(pattern, args)
}

/** Applies `transform` with 25% probability per event. Shorthand for `sometimesBy(0.25, fn)`. */
@StrudelDsl
val String.rarely by dslStringExtension { pattern, args, /* callInfo */ _ ->
    applyRarely(pattern, args)
}

// -- almostNever() ----------------------------------------------------------------------------------------------------

fun applyAlmostNever(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.1
    return pattern.`when`(rand.lt(x), transform)
}

/**
 * Applies `transform` with 10% probability per event. Shorthand for `sometimesBy(0.1, fn)`.
 *
 * ```KlangScript
 * s("bd sd").almostNever { it.speed(0.5) }   // very rarely at half speed
 * ```
 *
 * @category random
 * @tags almostNever, random, probability, conditional, rare
 */
@StrudelDsl
val StrudelPattern.almostNever by dslPatternExtension { pattern, args, /* callInfo */ _ ->
    applyAlmostNever(pattern, args)
}

/** Applies `transform` with 10% probability per event. Shorthand for `sometimesBy(0.1, fn)`. */
@StrudelDsl
val String.almostNever by dslStringExtension { pattern, args, /* callInfo */ _ -> applyAlmostNever(pattern, args) }

// -- almostAlways() ---------------------------------------------------------------------------------------------------

fun applyAlmostAlways(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.9
    return pattern.`when`(rand.lt(x), transform)
}

/**
 * Applies `transform` with 90% probability per event. Shorthand for `sometimesBy(0.9, fn)`.
 *
 * ```KlangScript
 * s("hh*8").almostAlways { it.gain(0.8) }   // 90% of hi-hats at 80% gain
 * ```
 *
 * @category random
 * @tags almostAlways, random, probability, conditional, frequent
 */
@StrudelDsl
val StrudelPattern.almostAlways by dslPatternExtension { pattern, args, /* callInfo */ _ ->
    applyAlmostAlways(pattern, args)
}

/** Applies `transform` with 90% probability per event. Shorthand for `sometimesBy(0.9, fn)`. */
@StrudelDsl
val String.almostAlways by dslStringExtension { pattern, args, /* callInfo */ _ -> applyAlmostAlways(pattern, args) }

// -- never() ----------------------------------------------------------------------------------------------------------

/**
 * Never applies `transform` — the pattern passes through unchanged. Shorthand for
 * `sometimesBy(0, fn)`. Useful as a placeholder during development.
 *
 * ```KlangScript
 * s("bd sd").never { it.rev() }       // rev is never applied; pattern plays normally
 * ```
 *
 * @category random
 * @tags never, noop, placeholder, conditional
 */
@StrudelDsl
val StrudelPattern.never by dslPatternExtension { pattern, /* args */ _, /* callInfo */ _ -> pattern }

/** Never applies `transform` — the pattern passes through unchanged. */
@StrudelDsl
val String.never by dslStringExtension { pattern, args, callInfo -> pattern.never(args, callInfo) }

// -- always() ---------------------------------------------------------------------------------------------------------

fun applyAlways(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val func = args.getOrNull(0).toPatternMapper()
    return func?.invoke(pattern) ?: pattern
}

/**
 * Always applies `transform` — shorthand for `sometimesBy(1, fn)`. Equivalent to calling
 * `fn` directly, but useful for consistency when toggling between probability variants.
 *
 * ```KlangScript
 * s("bd sd").always { it.rev() }      // rev is always applied
 * ```
 *
 * @category random
 * @tags always, unconditional, transform, conditional
 */
@StrudelDsl
val StrudelPattern.always by dslPatternExtension { pattern, args, /* callInfo */ _ -> applyAlways(pattern, args) }

/** Always applies `transform`. Shorthand for `sometimesBy(1, fn)`. */
@StrudelDsl
val String.always by dslStringExtension { pattern, args, callInfo -> pattern.always(args, callInfo) }

// -- someCyclesBy() ---------------------------------------------------------------------------------------------------

fun applySomeCyclesBy(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    // Delegate to applySometimesBy with seedByCycle = true
    return applySometimesBy(pattern = pattern, args = args, seedByCycle = true)
}

/**
 * Applies `transform` with the given probability, decided once per cycle (not per event).
 *
 * Like `sometimesBy`, but the random decision is made at cycle granularity: all events in
 * the same cycle are either all transformed or all untouched.
 *
 * ```KlangScript
 * s("bd sd hh cp").someCyclesBy(0.5) { it.rev() }  // entire cycle reversed ~50% of cycles
 * ```
 *
 * ```KlangScript
 * note("c d e f").someCyclesBy(0.3) { it.fast(2) } // ~30% of cycles play at double speed
 * ```
 *
 * @category random
 * @tags someCyclesBy, random, cycle, probability, conditional
 */
@StrudelDsl
val StrudelPattern.someCyclesBy by dslPatternExtension { pattern, args, /* callInfo */ _ ->
    applySomeCyclesBy(pattern, args)
}

/** Applies `transform` with the given probability, decided once per cycle (not per event). */
@StrudelDsl
val String.someCyclesBy by dslStringExtension { pattern, args, /* callInfo */ _ -> applySomeCyclesBy(pattern, args) }

// -- someCycles() -----------------------------------------------------------------------------------------------------

/**
 * Applies `transform` with 50% probability per cycle. Shorthand for `someCyclesBy(0.5, fn)`.
 *
 * ```KlangScript
 * s("bd sd hh cp").someCycles { it.rev() }   // reverse the entire cycle ~50% of the time
 * ```
 *
 * @category random
 * @tags someCycles, someCyclesBy, random, cycle, probability
 */
@StrudelDsl
val StrudelPattern.someCycles by dslPatternExtension { pattern, args, /* callInfo */ _ ->
    applySomeCyclesBy(pattern, args)
}

/** Applies `transform` with 50% probability per cycle. Shorthand for `someCyclesBy(0.5, fn)`. */
@StrudelDsl
val String.someCycles by dslStringExtension { pattern, args, /* callInfo */ _ -> applySomeCyclesBy(pattern, args) }

// -- randL() ----------------------------------------------------------------------------------------------------------

/**
 * Creates a pattern that produces a list (array) of `n` random numbers (0–1).
 *
 * The list is resampled each cycle. Useful for passing multiple random values to
 * parameters that accept lists, such as `partials`.
 *
 * ```KlangScript
 * s("saw").n(irand(12)).scale("F1:minor").partials(randL(8))   // 8 random partials
 * ```
 *
 * @category random
 * @tags randL, random, list, array, partials
 */
@StrudelDsl
val randL by dslFunction { args, /* callInfo */ _ ->
    val nArg = args.getOrNull(0)
    val nVal = nArg?.value

    val nPattern: StrudelPattern = (nArg ?: StrudelDslArg.of("0")).toPattern()

    val staticN = nVal?.asIntOrNull()

    RandLPattern.create(nPattern, staticN)
}

// -- randrun() --------------------------------------------------------------------------------------------------------

/**
 * Creates a pattern of `n` integers (0 to n-1) in a new random order each cycle.
 *
 * Every cycle, the range `0..(n-1)` is shuffled and played in that random order. Each value
 * appears exactly once per cycle. Useful for randomised but non-repeating sequences.
 *
 * ```KlangScript
 * n(randrun(8)).scale("C:pentatonic").note()   // random permutation of 8 scale degrees
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").bite(4, randrun(4))         // random order of 4 slices each cycle
 * ```
 *
 * @category random
 * @tags randrun, random, permutation, shuffle, sequence
 */
@StrudelDsl
val randrun: DslFunction by dslFunction { args, /* callInfo */ _ ->
    val nArg = args.getOrNull(0)
    val nVal = nArg?.value

    val nPattern: StrudelPattern = (nArg ?: StrudelDslArg.of("0")).toPattern()

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

// -- shuffle() --------------------------------------------------------------------------------------------------------

/**
 * Slices the pattern into `n` equal parts and plays them in a new random order each cycle.
 *
 * Each slice is played exactly once per cycle — the order changes but nothing is omitted.
 * Compare with `scramble`, which may repeat or skip slices.
 *
 * ```KlangScript
 * note("c d e f").shuffle(4)                 // random permutation of 4 quarter-cycle slices
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").shuffle(4)                // randomly reorder the 4 drum hits each cycle
 * ```
 *
 * @category random
 * @tags shuffle, random, reorder, slice, permutation
 */
@StrudelDsl
val StrudelPattern.shuffle: DslPatternMethod by dslPatternExtension { p, args, /* callInfo */ _ ->
    val nArg: Any = args.getOrNull(0) ?: 4
    val newArgs = listOf(nArg)
    val indices = randrun(args = newArgs)
    p.bite(nArg, indices)
}

/** Slices the pattern into `n` equal parts and plays them in a new random order each cycle. */
@StrudelDsl
val String.shuffle by dslStringExtension { p, args, /* callInfo */ _ -> p.shuffle(args) }

// -- scramble() -------------------------------------------------------------------------------------------------------

/**
 * Slices the pattern into `n` equal parts and picks slices at random each cycle.
 *
 * Unlike `shuffle`, which plays each slice exactly once, `scramble` picks randomly with
 * replacement: some slices may play multiple times, others not at all.
 *
 * ```KlangScript
 * note("c d e f").scramble(4)         // random selection with repetition of 4 slices
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").scramble(4)        // random drum hit order, repeats allowed
 * ```
 *
 * @category random
 * @tags scramble, random, slice, replacement, selection
 */
@StrudelDsl
val StrudelPattern.scramble by dslPatternExtension { p, args, /* callInfo */ _ ->
    val nArg: StrudelDslArg<Any?> = args.getOrNull(0) ?: StrudelDslArg.of(4)
    val indices = irand(listOf(nArg))._segment(nArg)

    p.bite(nArg, indices)
}

/** Slices the pattern into `n` equal parts and picks slices at random each cycle. */
@StrudelDsl
val String.scramble by dslStringExtension { p, args, /* callInfo */ _ ->
    val nArg = args.getOrNull(0) ?: 4
    val indices = irand(listOf(nArg))._segment(nArg)
    p.bite(nArg, indices)
}

// -- chooseWith() -----------------------------------------------------------------------------------------------------

/**
 * Chooses from the given list of values using a selector pattern (values 0–1).
 *
 * The selector pattern controls which value is chosen at each point in time. A value of 0
 * picks the first element, 1 picks the last, and values in between interpolate as an index.
 * Structure comes from the selector pattern (`chooseOut` mode).
 *
 * ```KlangScript
 * note("c2 g2 d2 f1").s(chooseWith(sine.fast(2), "sawtooth", "triangle", "bd:6"))
 * ```
 *
 * ```KlangScript
 * s(chooseWith(rand, "bd", "sd", "hh"))    // random instrument selection per event
 * ```
 *
 * @category random
 * @tags chooseWith, choose, selector, random, values
 */
@StrudelDsl
val chooseWith: DslFunction by dslFunction { args, /* callInfo */ _ ->
    val firstArg = args.getOrNull(0)

    when (val firstVal = firstArg?.value) {
        is StrudelPattern -> firstVal.chooseWith(args.drop(1))
        else -> AtomicPattern.pure.chooseWith(args)
    }
}

@StrudelDsl
val StrudelPattern.chooseWith by dslPatternExtension { p, args, /* callInfo */ _ ->
    val xs = args.extractChoiceArgs()

    ChoicePattern.createFromRaw(p, xs, mode = StructurePattern.Mode.Out)
}

@StrudelDsl
val String.chooseWith by dslStringExtension { p, args, /* callInfo */ _ -> p.chooseWith(args) }

// -- chooseInWith() ---------------------------------------------------------------------------------------------------

/**
 * Like `chooseWith` but structure comes from the chosen values, not the selector pattern.
 *
 * The selector pattern (values 0–1) controls which value is chosen. The timing structure is
 * taken from the selected value pattern (`chooseIn` mode).
 *
 * ```KlangScript
 * chooseInWith(sine, "c d", "e f g", "a b c d")   // selector picks pattern; its timing wins
 * ```
 *
 * @category random
 * @tags chooseInWith, chooseWith, selector, random, structure
 */
@StrudelDsl
val chooseInWith by dslFunction { args, /* callInfo */ _ ->
    val firstArg = args.getOrNull(0)

    when (val firstVal = firstArg?.value) {
        is StrudelPattern -> firstVal.chooseInWith(args.drop(1))
        else -> AtomicPattern.pure.chooseInWith(args)
    }
}

@StrudelDsl
val StrudelPattern.chooseInWith by dslPatternExtension { p, args, /* callInfo */ _ ->
    val xs = args.extractChoiceArgs()

    ChoicePattern.createFromRaw(p, xs, mode = StructurePattern.Mode.In)
}

@StrudelDsl
val String.chooseInWith by dslStringExtension { p, args, /* callInfo */ _ -> p.chooseInWith(args) }

// -- choose() ---------------------------------------------------------------------------------------------------------

/**
 * Chooses randomly from the given values or patterns at each event.
 *
 * Uses `rand` as the selector, so the choice varies continuously in time. Structure comes
 * from the selector (`chooseOut` mode). For cycle-level choices use `chooseCycles`.
 *
 * ```KlangScript
 * note("c2 g2 d2 f1").s(choose("sine", "triangle", "bd:6"))   // random synth each note
 * ```
 *
 * ```KlangScript
 * s(choose("bd", "sd", "hh")).fast(8)    // random drum sound every eighth-note
 * ```
 *
 * @alias chooseOut
 * @category random
 * @tags choose, random, selection, values, chooseOut
 */
@StrudelDsl
val choose by dslFunction { args, /* callInfo */ _ ->
    val firstArg = args.getOrNull(0)

    when (val firstVal = firstArg?.value) {
        is StrudelPattern -> firstVal.choose(args.drop(1))
        else -> AtomicPattern.pure.choose(args)
    }
}

/**
 * Uses this pattern (range 0–1) as a selector to choose from the given list.
 *
 * The receiver pattern controls the index into the list. Structure comes from the
 * selector pattern (`chooseOut` mode).
 *
 * ```KlangScript
 * sine.choose("c", "e", "g", "b")     // sine wave selects among chord tones
 * ```
 *
 * @alias chooseOut
 * @category random
 * @tags choose, selector, random, chooseOut
 */
@StrudelDsl
val StrudelPattern.choose by dslPatternExtension { p, args, /* callInfo */ _ ->
    val xs = args.extractChoiceArgs()

    ChoicePattern.createFromRaw(p, xs, mode = StructurePattern.Mode.Out)
}

/** Uses this pattern as a selector to choose from the given list. */
@StrudelDsl
val String.choose by dslStringExtension { p, args, /* callInfo */ _ -> p.choose(args) }

/**
 * Alias for `choose`.
 *
 * @alias choose
 * @category random
 * @tags chooseOut, choose, random, selection
 */
@StrudelDsl
val chooseOut by dslFunction { args, /* callInfo */ _ -> choose(args) }

/** Alias for `choose`. */
@StrudelDsl
val StrudelPattern.chooseOut by dslPatternExtension { p, args, /* callInfo */ _ -> p.choose(args) }

/** Alias for `choose`. */
@StrudelDsl
val String.chooseOut by dslStringExtension { p, args, /* callInfo */ _ -> p.choose(args) }

// -- chooseIn() -------------------------------------------------------------------------------------------------------

/**
 * Like `choose`, but structure comes from the chosen values rather than the selector.
 *
 * Uses `rand` as the selector. The timing structure is inherited from the selected value
 * pattern (`chooseIn` mode), so the chosen pattern's own rhythm determines the events.
 *
 * ```KlangScript
 * chooseIn("c d", "e f g", "a b c d")   // random pattern; its timing determines structure
 * ```
 *
 * @category random
 * @tags chooseIn, random, selection, structure
 */
@StrudelDsl
val chooseIn by dslFunction { args, /* callInfo */ _ ->
    val firstArg = args.getOrNull(0)

    when (val firstVal = firstArg?.value) {
        is StrudelPattern -> firstVal.chooseIn(args.drop(1))
        else -> AtomicPattern.pure.chooseIn(args)
    }
}

@StrudelDsl
val StrudelPattern.chooseIn by dslPatternExtension { p, args, /* callInfo */ _ ->
    val xs = args.extractChoiceArgs()

    ChoicePattern.createFromRaw(p, xs, mode = StructurePattern.Mode.In)
}

@StrudelDsl
val String.chooseIn by dslStringExtension { p, args, /* callInfo */ _ -> p.chooseIn(args) }

// -- choose2() --------------------------------------------------------------------------------------------------------

/**
 * Like `choose`, but the selector pattern should be in the range -1 to 1 (bipolar).
 *
 * The receiver bipolar pattern is converted to 0–1 before indexing into the list, so
 * `rand2` and LFOs centred at zero can be used directly as selectors.
 *
 * ```KlangScript
 * rand2.choose2("c", "e", "g", "b")    // bipolar rand selects among chord tones
 * ```
 *
 * @category random
 * @tags choose2, bipolar, selector, random
 */
@StrudelDsl
val StrudelPattern.choose2 by dslPatternExtension { p, args, /* callInfo */ _ ->
    val xs = args.extractChoiceArgs()

    ChoicePattern.createFromRaw(p.fromBipolar(), xs, mode = StructurePattern.Mode.Out)
}

/** Like `choose`, but the selector pattern should be in the range -1 to 1 (bipolar). */
@StrudelDsl
val String.choose2 by dslStringExtension { p, args, /* callInfo */ _ -> p.choose2(args) }

// -- chooseCycles() ---------------------------------------------------------------------------------------------------

/**
 * Picks one of the given values or patterns at random, changing once per cycle.
 *
 * The entire cycle plays the same chosen pattern. Unlike `choose`, which can vary within a
 * cycle, `chooseCycles` makes a fresh random choice only at cycle boundaries.
 *
 * ```KlangScript
 * chooseCycles("bd", "hh", "sd").s().fast(8)   // entire cycle uses one random drum sound
 * ```
 *
 * ```KlangScript
 * s("bd | hh | sd").fast(8)                    // mini-notation equivalent using |
 * ```
 *
 * @alias randcat
 * @category random
 * @tags chooseCycles, random, cycle, randcat, selection
 */
@StrudelDsl
val chooseCycles by dslFunction { args, /* callInfo */ _ ->
    val firstArg = args.getOrNull(0)

    when (val firstVal = firstArg?.value) {
        is StrudelPattern -> firstVal.chooseCycles(args.drop(1))
        else -> AtomicPattern.pure.chooseCycles(args)
    }
}

@StrudelDsl
val StrudelPattern.chooseCycles by dslPatternExtension { p, args, /* callInfo */ _ ->
    val xs = (listOf(p) + args.map { it.value }).asStrudelDslArgs()
    ChoicePattern.createFromRaw(rand.segment(1), xs, mode = StructurePattern.Mode.In)
}

@StrudelDsl
val String.chooseCycles by dslStringExtension { p, args, /* callInfo */ _ ->
    val xs = (listOf(p) + args.map { it.value }).asStrudelDslArgs()
    ChoicePattern.createFromRaw(rand.segment(1), xs, mode = StructurePattern.Mode.In)
}

/**
 * Alias for `chooseCycles`.
 *
 * @alias chooseCycles
 * @category random
 * @tags randcat, chooseCycles, random, cycle
 */
@StrudelDsl
val randcat by dslFunction { args, /* callInfo */ _ -> chooseCycles(args) }

/** Alias for `chooseCycles`. */
@StrudelDsl
val StrudelPattern.randcat by dslPatternExtension { p, args, /* callInfo */ _ -> p.chooseCycles(args) }

/** Alias for `chooseCycles`. */
@StrudelDsl
val String.randcat by dslStringExtension { p, args, /* callInfo */ _ -> p.chooseCycles(args) }

// -- wchoose() --------------------------------------------------------------------------------------------------------

/**
 * Chooses randomly from the given values according to relative weights.
 *
 * Each choice is a `[value, weight]` pair. Higher weights make that value more likely.
 * Uses `rand` as the selector so choices vary within a cycle.
 *
 * ```KlangScript
 * note("c2 g2 d2 f1").s(wchoose(listOf("sine", 10), listOf("triangle", 1)))
 * ```
 *
 * ```KlangScript
 * s(wchoose(listOf("bd", 8), listOf("sd", 2), listOf("hh", 5))).fast(8)
 * ```
 *
 * @category random
 * @tags wchoose, weighted, random, probability, selection
 */
@StrudelDsl
val wchoose by dslFunction { args, /* callInfo */ _ ->
    val firstArg = args.getOrNull(0)

    when (val firstVal = firstArg?.value) {
        is StrudelPattern -> firstVal.wchoose(args.drop(1))
        else -> AtomicPattern.pure.wchoose(args)
    }
}

@StrudelDsl
val StrudelPattern.wchoose by dslPatternExtension { p, args, /* callInfo */ _ ->
    val (items, weights) = args.extractWeightedPairs()

    ChoicePattern.createFromRaw(
        selector = p,
        choices = items,
        weights = weights,
        mode = StructurePattern.Mode.Out,
    )
}

@StrudelDsl
val String.wchoose by dslStringExtension { p, args, /* callInfo */ _ -> p.wchoose(args) }

// -- wchooseCycles() --------------------------------------------------------------------------------------------------

/**
 * Picks one of the given values at random each cycle according to relative weights.
 *
 * Like `chooseCycles` but each choice can have a different probability. The entire cycle
 * plays the same chosen value. Each choice is a `[value, weight]` pair.
 *
 * ```KlangScript
 * wchooseCycles(listOf("bd", 10), listOf("hh", 1)).s().fast(8)   // bd much more likely
 * ```
 *
 * @alias wrandcat
 * @category random
 * @tags wchooseCycles, weighted, random, cycle, wrandcat
 */
@StrudelDsl
val wchooseCycles by dslFunction { args, /* callInfo */ _ ->
    val firstArg = args.getOrNull(0)

    when (val firstVal = firstArg?.value) {
        is StrudelPattern -> firstVal.wchooseCycles(args.drop(1))
        else -> AtomicPattern.pure.wchooseCycles(args)
    }
}

@StrudelDsl
val StrudelPattern.wchooseCycles by dslPatternExtension { p, args, /* callInfo */ _ ->
    val (items, weights) = args.extractWeightedPairs()
    val allItems = (listOf(p) + items.map { it.value }).asStrudelDslArgs()
    val allWeights = (listOf(1.0) + weights.map { it.value }).asStrudelDslArgs()

    ChoicePattern.createFromRaw(
        selector = rand.segment(1),
        choices = allItems,
        weights = allWeights,
        mode = StructurePattern.Mode.In,
    )
}

@StrudelDsl
val String.wchooseCycles by dslStringExtension { p, args, /* callInfo */ _ -> p.wchooseCycles(args) }

/**
 * Alias for `wchooseCycles`.
 *
 * @alias wchooseCycles
 * @category random
 * @tags wrandcat, wchooseCycles, weighted, random, cycle
 */
@StrudelDsl
val wrandcat by dslFunction { args, /* callInfo */ _ -> wchooseCycles(args) }

/** Alias for `wchooseCycles`. */
@StrudelDsl
val StrudelPattern.wrandcat by dslPatternExtension { p, args, /* callInfo */ _ -> p.wchooseCycles(args) }

/** Alias for `wchooseCycles`. */
@StrudelDsl
val String.wrandcat by dslStringExtension { p, args, /* callInfo */ _ -> p.wchooseCycles(args) }
