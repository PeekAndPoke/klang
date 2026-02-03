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

/** Sets the random seed */
@StrudelDsl
val StrudelPattern.seed by dslPatternExtension { pattern, args, /* callInfo */ _ -> applyRandomSeed(pattern, args) }

/** Sets the random seed */
@StrudelDsl
val StrudelPattern.withSeed by dslPatternExtension { pattern, args, /* callInfo */ _ -> applyRandomSeed(pattern, args) }

/** Sets the random seed */
@StrudelDsl
val String.seed by dslStringExtension { pattern, args, /* callInfo */ _ -> applyRandomSeed(pattern, args) }

/** Sets the random seed */
@StrudelDsl
val String.withSeed by dslStringExtension { pattern, args, /* callInfo */ _ -> applyRandomSeed(pattern, args) }

// -- rand() / rand2() -------------------------------------------------------------------------------------------------

/** Continuous pattern that produces a random value between 0 and 1 */
@StrudelDsl
val rand by dslObject {
    ContinuousPattern { from, _, ctx -> ctx.getSeededRandom(from, "rand").nextDouble() }
}

/** Continuous pattern that produces a random value between -1 and 1 */
@StrudelDsl
val rand2 by dslObject { rand.range(-1.0, 1.0) }

/**
 * Continuous random pattern that is constant within each cycle.
 * All events within the same cycle get the same random value.
 * Different cycles get different random values.
 *
 * This is equivalent to JavaScript's `rand._segment(1)`.
 * Used for cycle-based random decisions (e.g., someCyclesBy).
 *
 * @example
 * s("bd*8").degradeByWith(randCycle, 0.5)
 */
@StrudelDsl
val randCycle by dslObject {
    ContinuousPattern { fromTime, _, ctx ->
        ctx.getSeededRandom(floor(fromTime), "randCycle").nextDouble()
    }
}

// -- brand() / brandBy() ----------------------------------------------------------------------------------------------

/**
 * A continuous pattern of 0 or 1 (binary random), with a probability for the value being 1
 *
 * @name brandBy
 * @param {number} probability - a number between 0 and 1
 * @example
 * s("hh*10").pan(brandBy(0.2))
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

/** A continuous pattern of 0 or 1 (50:50 random) */
@StrudelDsl
val brand by dslObject { brandBy(0.5) }

// -- irand() ----------------------------------------------------------------------------------------------------------

/**
 * A continuous pattern of random integers, between 0 and n-1.
 *
 * @param {number} n max value (exclusive)
 * @example
 * // randomly select scale notes from 0 - 7 (= C to C)
 * n(irand(8)).struct("x x*2 x x*3").scale("C:minor")
 *
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
 * Randomly removes events from the pattern by a given amount.
 * 0 = 0% chance of removal
 * 1 = 100% chance of removal
 *
 * @param [Number] probability
 * @example
 * s("bd sn").degradeBy(0.5)
 */
@StrudelDsl
val StrudelPattern.degradeBy by dslPatternExtension { pattern, args, /* callInfo */ _ -> applyDegradeBy(pattern, args) }

/**
 * Randomly removes events from the pattern with the given probability.
 */
@StrudelDsl
val String.degradeBy by dslStringExtension { pattern, args, /* callInfo */ _ -> applyDegradeBy(pattern, args) }

// -- degrade() --------------------------------------------------------------------------------------------------------

/**
 * Randomly removes events from the pattern with a 50% probability.
 */
@StrudelDsl
val StrudelPattern.degrade by dslPatternExtension { pattern, args, /* callInfo */ _ -> applyDegradeBy(pattern, args) }

/**
 * Randomly removes events from the pattern with a 50% probability.
 */
@StrudelDsl
val String.degrade by dslStringExtension { pattern, args, /* callInfo */ _ -> applyDegradeBy(pattern, args) }

// -- degradeByWith() --------------------------------------------------------------------------------------------------

private fun applyDegradeByWith(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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
 * Randomly removes events from the pattern using a custom random pattern.
 * JavaScript: degradeByWith(withPat, x, pat)
 *
 * @param {withPat} Pattern providing random values for comparison
 * @param {x}       Threshold value (0..1)
 * @example
 * s("bd*8").degradeByWith(rand._segment(1), 0.5)
 */
@StrudelDsl
val StrudelPattern.degradeByWith by dslPatternExtension { pattern, args, _ -> applyDegradeByWith(pattern, args) }

/**
 * Randomly removes events from the pattern using a custom random pattern.
 */
@StrudelDsl
val String.degradeByWith by dslStringExtension { pattern, args, callInfo -> pattern.degradeByWith(args, callInfo) }

// -- undegradeBy() ----------------------------------------------------------------------------------------------------

fun applyUndegradeBy(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    // undegradeBy(x) is just undegradeByWith(rand, x)
    // undegradeBy(0) = 100% removal, undegradeBy(1) = 0% removal
    return applyUndegradeByWith(pattern, listOf(StrudelDslArg.of(rand)) + args)
}

/**
 * Inverse of `degradeBy`: Randomly removes events from the pattern by a given amount.
 * 0 = 100% chance of removal
 * 1 = 0% chance of removal
 * Events that would be removed by degradeBy are let through by undegradeBy and vice versa (see second example).
 *
 * @name undegradeBy
 * @memberof Pattern
 * @param {number} amount - a number between 0 and 1
 * @returns Pattern
 * @example
 * s("hh*8").undegradeBy(0.2)
 * @example
 * s("hh*10").layer(
 *   x => x.degradeBy(0.2).pan(0),
 *   x => x.undegradeBy(0.8).pan(1)
 * )
 */
@StrudelDsl
val StrudelPattern.undegradeBy by dslPatternExtension { pattern, args, /* callInfo */ _ ->
    applyUndegradeBy(pattern, args)
}

/**
 * Inverse of `degradeBy`: Randomly removes events from the pattern by a given amount.
 */
@StrudelDsl
val String.undegradeBy by dslStringExtension { pattern, args, /* callInfo */ _ -> applyUndegradeBy(pattern, args) }

// -- undegradeByWith() --------------------------------------------------------------------------------------------------

private fun applyUndegradeByWith(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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
 * Inverse of degradeByWith using a custom random pattern.
 * Keeps events where the random value is <= threshold (complementary to degradeByWith).
 *
 * @param {withPat} Pattern providing random values for comparison
 * @param {x}       Threshold value (0..1)
 * @example
 * s("bd*8").undegradeByWith(randCycle, 0.5)
 */
@StrudelDsl
val StrudelPattern.undegradeByWith by dslPatternExtension { pattern, args, _ ->
    applyUndegradeByWith(pattern, args)
}

/**
 * Inverse of degradeByWith using a custom random pattern.
 */
@StrudelDsl
val String.undegradeByWith by dslStringExtension { pattern, args, callInfo ->
    pattern.undegradeByWith(args, callInfo)
}

// -- undegrade() --------------------------------------------------------------------------------------------------------

/**
 * Randomly removes events from the pattern with a 50% probability.
 */
@StrudelDsl
val StrudelPattern.undegrade by dslPatternExtension { pattern, args, /* callInfo */ _ ->
    applyUndegradeBy(pattern, args)
}

/**
 * Randomly removes events from the pattern with a 50% probability.
 */
@StrudelDsl
val String.undegrade by dslStringExtension { pattern, args, /* callInfo */ _ -> applyUndegradeBy(pattern, args) }

// -- sometimesBy() ----------------------------------------------------------------------------------------------------

/**
 * Randomly applies the given function by the given probability.
 */
private fun applySometimesBy(
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
 * Randomly applies the given function by the given probability.
 *
 * @param {number} probability - a number between 0 and 1
 * @param {function} function - the transformation to apply
 * @example
 * s("hh*8").sometimesBy(0.4, x=>x.speed("0.5"))
 */
@StrudelDsl
val StrudelPattern.sometimesBy by dslPatternExtension { pattern, args, /* callInfo */ _ ->
    applySometimesBy(pattern, args)
}

/**
 * Randomly applies the given function by the given probability.
 */
@StrudelDsl
val String.sometimesBy by dslStringExtension { pattern, args, /* callInfo */ _ ->
    applySometimesBy(pattern, args)
}

// -- sometimes() ------------------------------------------------------------------------------------------------------

private fun applySometimes(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.5
    return pattern.`when`(rand.lt(x), transform)
}

/**
 * Applies the given function with a 50% chance.
 */
@StrudelDsl
val StrudelPattern.sometimes by dslPatternExtension { pattern, args, /* callInfo */ _ ->
    applySometimes(pattern, args)
}

/**
 * Applies the given function with a 50% chance.
 */
@StrudelDsl
val String.sometimes by dslStringExtension { pattern, args, /* callInfo */ _ ->
    applySometimes(pattern, args)
}

// -- often() ----------------------------------------------------------------------------------------------------------

private fun applyOften(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.75
    return pattern.`when`(rand.lt(x), transform)
}

/** Shorthand for `.sometimesBy(0.75, fn)` */
@StrudelDsl
val StrudelPattern.often by dslPatternExtension { pattern, args, /* callInfo */ _ ->
    applyOften(pattern, args)
}

/** Shorthand for `.sometimesBy(0.75, fn)` */
@StrudelDsl
val String.often by dslStringExtension { pattern, args, /* callInfo */ _ ->
    applyOften(pattern, args)
}

// -- rarely() ---------------------------------------------------------------------------------------------------------

private fun applyRarely(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.25
    return pattern.`when`(rand.lt(x), transform)
}

/** Shorthand for `.sometimesBy(0.25, fn)` */
@StrudelDsl
val StrudelPattern.rarely by dslPatternExtension { pattern, args, /* callInfo */ _ ->
    applyRarely(pattern, args)
}

/** Shorthand for `.sometimesBy(0.25, fn)` */
@StrudelDsl
val String.rarely by dslStringExtension { pattern, args, /* callInfo */ _ ->
    applyRarely(pattern, args)
}

// -- almostNever() ----------------------------------------------------------------------------------------------------

private fun applyAlmostNever(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.1
    return pattern.`when`(rand.lt(x), transform)
}

/** Shorthand for `.sometimesBy(0.1, fn)` */
@StrudelDsl
val StrudelPattern.almostNever by dslPatternExtension { pattern, args, /* callInfo */ _ ->
    applyAlmostNever(pattern, args)
}

/** Shorthand for `.sometimesBy(0.1, fn)` */
@StrudelDsl
val String.almostNever by dslStringExtension { pattern, args, /* callInfo */ _ ->
    applyAlmostNever(pattern, args)
}

// -- almostAlways() ---------------------------------------------------------------------------------------------------

private fun applyAlmostAlways(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val transform = args.getOrNull(0).toPatternMapper() ?: { it }
    val x = 0.9
    return pattern.`when`(rand.lt(x), transform)
}

/** Shorthand for `.sometimesBy(0.9, fn)` */
@StrudelDsl
val StrudelPattern.almostAlways by dslPatternExtension { pattern, args, /* callInfo */ _ ->
    applyAlmostAlways(pattern, args)
}

/** Shorthand for `.sometimesBy(0.9, fn)` */
@StrudelDsl
val String.almostAlways by dslStringExtension { pattern, args, /* callInfo */ _ ->
    applyAlmostAlways(pattern, args)
}

// -- never() ----------------------------------------------------------------------------------------------------------

/** Shorthand for `.sometimesBy(0, fn)` (never calls fn) */
@StrudelDsl
val StrudelPattern.never by dslPatternExtension { pattern, /* args */ _, /* callInfo */ _ -> pattern }

/** Shorthand for `.sometimesBy(0, fn)` (never calls fn) */
@StrudelDsl
val String.never by dslStringExtension { pattern, args, callInfo -> pattern.never(args, callInfo) }

// -- always() ---------------------------------------------------------------------------------------------------------

private fun applyAlways(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val func = args.getOrNull(0).toPatternMapper()

    return func?.invoke(pattern) ?: pattern
}

/** Shorthand for `.sometimesBy(1, fn)` (always calls fn) */
@StrudelDsl
val StrudelPattern.always by dslPatternExtension { pattern, args, /* callInfo */ _ -> applyAlways(pattern, args) }

/** Shorthand for `.sometimesBy(1, fn)` (always calls fn) */
@StrudelDsl
val String.always by dslStringExtension { pattern, args, callInfo -> pattern.always(args, callInfo) }

// -- someCyclesBy() ---------------------------------------------------------------------------------------------------

private fun applySomeCyclesBy(
    pattern: StrudelPattern,
    args: List<StrudelDslArg<Any?>>,
): StrudelPattern {
    // Delegate to applySometimesBy with seedByCycle = true
    return applySometimesBy(pattern = pattern, args = args, seedByCycle = true)
}

/**
 * Randomly applies the given function by the given probability on a cycle by cycle basis.
 *
 * @param {number} probability - a number between 0 and 1
 * @param {function} function - the transformation to apply
 */
@StrudelDsl
val StrudelPattern.someCyclesBy by dslPatternExtension { pattern, args, /* callInfo */ _ ->
    applySomeCyclesBy(pattern, args)
}

@StrudelDsl
val String.someCyclesBy by dslStringExtension { pattern, args, /* callInfo */ _ ->
    applySomeCyclesBy(pattern, args)
}

// -- someCycles() -----------------------------------------------------------------------------------------------------

/** Shorthand for `.someCyclesBy(0.5, fn)` */
@StrudelDsl
val StrudelPattern.someCycles by dslPatternExtension { pattern, args, /* callInfo */ _ ->
    applySomeCyclesBy(pattern, args)
}

@StrudelDsl
val String.someCycles by dslStringExtension { pattern, args, /* callInfo */ _ ->
    applySomeCyclesBy(pattern, args)
}

// -- randL() ----------------------------------------------------------------------------------------------------------

/**
 * Creates a list of random numbers of the given length.
 *
 * @name randL
 * @param {number} n Number of random numbers to sample
 * @example
 * s("saw").seg(16).n(irand(12)).scale("F1:minor")
 *   .partials(randL(8))
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
 * Creates a pattern of random integers of length n, which is a shuffled version of 0..n-1.
 * The shuffle changes every cycle.
 *
 * @name randrun
 * @param {number} n Length of the run / max number (exclusive)
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
 * Slices a pattern into the given number of parts, then plays those parts in random order.
 * Each part will be played exactly once per cycle.
 *
 * @example
 * note("c d e f").sound("piano").shuffle(4)
 * @example
 * seq("c d e f".shuffle(4), "g").note().sound("piano")
 *
 * @param n number of slices
 */
@StrudelDsl
val StrudelPattern.shuffle: DslPatternMethod by dslPatternExtension { p, args, /* callInfo */ _ ->
    val nArg: Any = args.getOrNull(0) ?: 4
    val newArgs = listOf(nArg)
    val indices = randrun(args = newArgs)
    p.bite(nArg, indices)
}

@StrudelDsl
val String.shuffle by dslStringExtension { p, args, /* callInfo */ _ -> p.shuffle(args) }

// -- scramble() -------------------------------------------------------------------------------------------------------

/**
 * Slices a pattern into the given number of parts, then plays those parts at random.
 * Similar to `shuffle`, but parts might be played more than once, or not at all, per cycle.
 *
 * @example
 * note("c d e f").sound("piano").scramble(4)
 * @example
 * seq("c d e f".scramble(4), "g").note().sound("piano")
 *
 * @param n number of slices
 */
@StrudelDsl
val StrudelPattern.scramble by dslPatternExtension { p, args, /* callInfo */ _ ->
    val nArg: StrudelDslArg<Any?> = args.getOrNull(0) ?: StrudelDslArg.of(4)
    val indices = irand(listOf(nArg)).segment(nArg)

    p.bite(nArg, indices)
}

@StrudelDsl
val String.scramble by dslStringExtension { p, args, /* callInfo */ _ ->
    val nArg = args.getOrNull(0) ?: 4
    val indices = irand(listOf(nArg)).segment(nArg)
    p.bite(nArg, indices)
}

// -- chooseWith() -----------------------------------------------------------------------------------------------------

/**
 * Choose from the list of values (or patterns of values) using the given
 * pattern of numbers, which should be in the range of 0..1.
 *
 * @example
 * note("c2 g2!2 d2 f1").s(chooseWith(sine.fast(2), ["sawtooth", "triangle", "bd:6"]))
 *
 * @param {pat} Selector pattern (values 0..1)
 * @param {xs}  List of choices (values or patterns)
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
 * As with {chooseWith}, but the structure comes from the chosen values, rather
 * than the pattern you're using to choose with.
 *
 * @param {pat} Selector pattern (values 0..1)
 * @param {xs}  List of choices
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
 * Chooses randomly from the given list of elements.
 *
 * @example
 * note("c2 g2!2 d2 f1").s(choose("sine", "triangle", "bd:6"))
 *
 * @param {xs}  values / patterns to choose from.
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
 * Chooses from the given list of values (or patterns of values), according
 * to the pattern that the method is called on. The pattern should be in
 * the range 0 .. 1.
 */
@StrudelDsl
val StrudelPattern.choose by dslPatternExtension { p, args, /* callInfo */ _ ->
    val xs = args.extractChoiceArgs()

    ChoicePattern.createFromRaw(p, xs, mode = StructurePattern.Mode.Out)
}

val String.choose by dslStringExtension { p, args, /* callInfo */ _ -> p.choose(args) }

/** Alias for [choose] */
@StrudelDsl
val chooseOut by dslFunction { args, /* callInfo */ _ -> choose(args) }

/** Alias for [choose] */
@StrudelDsl
val StrudelPattern.chooseOut by dslPatternExtension { p, args, /* callInfo */ _ -> p.choose(args) }

/** Alias for [choose] */
@StrudelDsl
val String.chooseOut by dslStringExtension { p, args, /* callInfo */ _ -> p.choose(args) }

// -- chooseIn() -------------------------------------------------------------------------------------------------------

/**
 * Chooses randomly from the given list of elements.
 *
 * @param {xs} values / patterns to choose from.
 */
/**
 * Chooses randomly from the given list of elements.
 *
 * @param {xs} values / patterns to choose from.
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
 * As with choose, but the pattern that this method is called on should be
 * in the range -1 .. 1.
 */
@StrudelDsl
val StrudelPattern.choose2 by dslPatternExtension { p, args, /* callInfo */ _ ->
    val xs = args.extractChoiceArgs()

    ChoicePattern.createFromRaw(p.fromBipolar(), xs, mode = StructurePattern.Mode.Out)
}

@StrudelDsl
val String.choose2 by dslStringExtension { p, args, /* callInfo */ _ -> p.choose2(args) }

// -- chooseCycles() ---------------------------------------------------------------------------------------------------

/**
 * Picks one of the elements at random each cycle.
 *
 * @param {xs} values / patterns to choose from.
 * @example
 * chooseCycles("bd", "hh", "sd").s().fast(8)
 * @example
 * s("bd | hh | sd").fast(8)
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

/** Alias for [chooseCycles] */
@StrudelDsl
val randcat by dslFunction { args, /* callInfo */ _ -> chooseCycles(args) }

/** Alias for [chooseCycles] */
@StrudelDsl
val StrudelPattern.randcat by dslPatternExtension { p, args, /* callInfo */ _ -> p.chooseCycles(args) }

/** Alias for [chooseCycles] */
@StrudelDsl
val String.randcat by dslStringExtension { p, args, /* callInfo */ _ -> p.chooseCycles(args) }

// -- wchoose() --------------------------------------------------------------------------------------------------------

private fun extractWeightedPairs(args: List<StrudelDslArg<Any?>>): Pair<List<StrudelDslArg<Any?>>, List<StrudelDslArg<Any?>>> {
    val items = mutableListOf<StrudelDslArg<Any?>>()
    val weights = mutableListOf<StrudelDslArg<Any?>>()

    val inputs = if (
        args.size == 1 && args[0].value is List<*> && (args[0].value as List<*>).all { it is List<*> }
    ) {
        @Suppress("UNCHECKED_CAST")
        (args[0].value as List<Any?>).asStrudelDslArgs()
    } else {
        args
    }

    inputs.forEach { item ->
        if (item.value is List<*> && item.value.size >= 2) {
            val list = item.value
            items.add(StrudelDslArg(list[0], null))
            weights.add(StrudelDslArg(list[1], null))
        }
    }
    return items to weights
}

/**
 * Chooses randomly from the given list of elements by giving a probability to each element.
 *
 * @param {pairs} arrays of [value, weight]
 * @example
 * note("c2 g2!2 d2 f1").s(wchoose(listOf("sine", 10), listOf("triangle", 1)))
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
    val (items, weights) = extractWeightedPairs(args)

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
 * Picks one of the elements at random each cycle by giving a probability to each element.
 *
 * @param {pairs} arrays of [value, weight]
 * @example
 * wchooseCycles(listOf("bd", 10), listOf("hh", 1)).s().fast(8)
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
    val (items, weights) = extractWeightedPairs(args)
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

/** Alias for [wchooseCycles] */
@StrudelDsl
val wrandcat by dslFunction { args, /* callInfo */ _ -> wchooseCycles(args) }

/** Alias for [wchooseCycles] */
@StrudelDsl
val StrudelPattern.wrandcat by dslPatternExtension { p, args, /* callInfo */ _ -> p.wchooseCycles(args) }

/** Alias for [wchooseCycles] */
@StrudelDsl
val String.wrandcat by dslStringExtension { p, args, /* callInfo */ _ -> p.wchooseCycles(args) }
