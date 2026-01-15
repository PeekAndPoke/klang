package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.VoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.lang.addons.oneMinusValue
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.strudel.pattern.*
import io.peekandpoke.klang.strudel.pattern.ReinterpretPattern.Companion.reinterpret
import kotlin.math.floor

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangRandomInit = false

// -- Helpers ----------------------------------------------------------------------------------------------------------

private fun applyRandomSeed(pattern: StrudelPattern, args: List<Any?>): ContextModifierPattern {
    val seed = args.getOrNull(0)?.asLongOrNull()

    return ContextModifierPattern(source = pattern) {
        if (seed != null) {
            set(QueryContext.randomSeed, seed)
        } else {
            remove(QueryContext.randomSeed)
        }
    }
}

// -- seed() -----------------------------------------------------------------------------------------------------------

/** Sets the random seed */
@StrudelDsl
val StrudelPattern.seed by dslPatternExtension { pattern, args -> applyRandomSeed(pattern, args) }

/** Sets the random seed */
@StrudelDsl
val StrudelPattern.withSeed by dslPatternExtension { pattern, args -> applyRandomSeed(pattern, args) }

/** Sets the random seed */
@StrudelDsl
val String.seed by dslStringExtension { pattern, args -> applyRandomSeed(pattern, args) }

/** Sets the random seed */
@StrudelDsl
val String.withSeed by dslStringExtension { pattern, args -> applyRandomSeed(pattern, args) }

// -- rand() / rand2() -------------------------------------------------------------------------------------------------

/** Continuous pattern that produces a random value between 0 and 1 */
@StrudelDsl
val rand by dslObject {
    ContinuousPattern { from, _, ctx ->
        val rand = ctx.getSeededRandom(from, "rand")

        rand.nextDouble()
    }
}

/** Continuous pattern that produces a random value between -1 and 1 */
@StrudelDsl
val rand2 by dslObject { rand.range(-1.0, 1.0) }

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
val brandBy by dslFunction { args ->
    val probability = args.getOrNull(0)?.asDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.5

    ContinuousPattern { from, _, ctx ->
        val rand = ctx.getSeededRandom(from, "brandBy")

        if (rand.nextDouble() < probability) 1.0 else 0.0
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
val irand by dslFunction { args ->
    val n = args.getOrNull(0)?.asIntOrNull() ?: 0

    if (n < 0) {
        silence
    } else if (n == 1) {
        signal { 0.0 }
    } else {
        ContinuousPattern { from, _, ctx ->
            val fraction = from - floor(from)
            val seed = (fraction * n * 10).toInt()
            val random = ctx.getSeededRandom(seed, "irand")

            random.nextInt(0, n).toDouble()
        }
    }
}

// -- degradeBy() ------------------------------------------------------------------------------------------------------

private fun applyDegradeBy(pattern: StrudelPattern, args: List<Any?>): StrudelPattern {
    val probArg = args.getOrNull(0)

    val probPattern = when (probArg) {
        is StrudelPattern -> probArg
        is String -> parseMiniNotation(input = probArg) {
            AtomicPattern(VoiceData.empty.defaultModifier(it))
        }

        else -> null
    }

    if (probPattern != null) {
        return SometimesPattern.discardOnMatch(source = pattern, probabilityPattern = probPattern)
    }

    if (probArg is Number) {
        return SometimesPattern.discardOnMatch(source = pattern, probabilityValue = probArg.toDouble())
    }

    return SometimesPattern.discardOnMatch(source = pattern, probabilityValue = 0.5)
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
val StrudelPattern.degradeBy by dslPatternExtension { pattern, args -> applyDegradeBy(pattern, args) }

/**
 * Randomly removes events from the pattern with the given probability.
 */
@StrudelDsl
val String.degradeBy by dslStringExtension { pattern, args -> applyDegradeBy(pattern, args) }

// -- degrade() --------------------------------------------------------------------------------------------------------

/**
 * Randomly removes events from the pattern with a 50% probability.
 */
@StrudelDsl
val StrudelPattern.degrade by dslPatternExtension { pattern, args -> applyDegradeBy(pattern, args) }

/**
 * Randomly removes events from the pattern with a 50% probability.
 */
@StrudelDsl
val String.degrade by dslStringExtension { pattern, args -> applyDegradeBy(pattern, args) }

// -- undegradeBy() ----------------------------------------------------------------------------------------------------

private fun applyUndegradeBy(pattern: StrudelPattern, args: List<Any?>): StrudelPattern {
    val probArg = args.getOrNull(0)

    val probPattern = when (probArg) {
        is StrudelPattern -> probArg
        is String -> parseMiniNotation(input = probArg) {
            AtomicPattern(VoiceData.empty.defaultModifier(it))
        }

        else -> null
    }?.oneMinusValue()

    if (probPattern != null) {
        return SometimesPattern.discardOnMiss(source = pattern, probabilityPattern = probPattern)
    }

    if (probArg is Number) {
        return SometimesPattern.discardOnMiss(source = pattern, probabilityValue = 1.0 - probArg.toDouble())
    }

    return SometimesPattern.discardOnMiss(source = pattern, probabilityValue = 0.5)
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
val StrudelPattern.undegradeBy by dslPatternExtension { pattern, args -> applyUndegradeBy(pattern, args) }

/**
 * Inverse of `degradeBy`: Randomly removes events from the pattern by a given amount.
 */
@StrudelDsl
val String.undegradeBy by dslStringExtension { pattern, args -> applyUndegradeBy(pattern, args) }

// -- undegrade() --------------------------------------------------------------------------------------------------------

/**
 * Randomly removes events from the pattern with a 50% probability.
 */
@StrudelDsl
val StrudelPattern.undegrade by dslPatternExtension { pattern, args -> applyUndegradeBy(pattern, args) }

/**
 * Randomly removes events from the pattern with a 50% probability.
 */
@StrudelDsl
val String.undegrade by dslStringExtension { pattern, args -> applyUndegradeBy(pattern, args) }

// -- sometimesBy() ----------------------------------------------------------------------------------------------------

/**
 * Randomly applies the given function by the given probability.
 */
private fun applySometimesBy(
    pattern: StrudelPattern,
    args: List<Any?>,
    defaultProb: Double? = null,
    seedByCycle: Boolean = false,
): StrudelPattern {
    var probArg: Any? = null
    var func: ((StrudelPattern) -> StrudelPattern)? = null

    for (arg in args) {
        when (arg) {
            is Number -> if (probArg == null) probArg = arg
            is StrudelPattern -> if (probArg == null) probArg = arg
            is String -> if (probArg == null) probArg = parseMiniNotation(input = arg) {
                AtomicPattern(VoiceData.empty.defaultModifier(it))
            }

            is Function1<*, *> -> {
                if (func == null) {
                    @Suppress("UNCHECKED_CAST")
                    func = arg as? (StrudelPattern) -> StrudelPattern
                }
            }
        }
    }

    val pVal: Double
    val pPat: StrudelPattern?

    if (probArg is StrudelPattern) {
        pPat = probArg
        pVal = 0.5
    } else {
        pPat = null
        pVal = (probArg as? Number)?.toDouble() ?: defaultProb ?: 0.5
    }

    if (func == null) return pattern

    return SometimesPattern.applyOnMatch(
        source = pattern,
        probabilityPattern = pPat,
        probabilityValue = pVal,
        seedStrategy = if (seedByCycle) { it -> it.begin.floor() } else { it -> it.begin },
        onMatch = func
    )
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
val StrudelPattern.sometimesBy by dslPatternExtension { pattern, args -> applySometimesBy(pattern, args) }

/**
 * Randomly applies the given function by the given probability.
 */
@StrudelDsl
val String.sometimesBy by dslStringExtension { pattern, args -> applySometimesBy(pattern, args) }

// -- sometimes() ------------------------------------------------------------------------------------------------------

/**
 * Applies the given function with a 50% chance.
 */
@StrudelDsl
val StrudelPattern.sometimes by dslPatternExtension { pattern, args -> applySometimesBy(pattern, args, 0.5) }

/**
 * Applies the given function with a 50% chance.
 */
@StrudelDsl
val String.sometimes by dslStringExtension { pattern, args -> applySometimesBy(pattern, args, 0.5) }

// -- often() ----------------------------------------------------------------------------------------------------------

/** Shorthand for `.sometimesBy(0.75, fn)` */
@StrudelDsl
val StrudelPattern.often by dslPatternExtension { pattern, args -> applySometimesBy(pattern, args, 0.75) }

/** Shorthand for `.sometimesBy(0.75, fn)` */
@StrudelDsl
val String.often by dslStringExtension { pattern, args -> applySometimesBy(pattern, args, 0.75) }

// -- rarely() ---------------------------------------------------------------------------------------------------------

/** Shorthand for `.sometimesBy(0.25, fn)` */
@StrudelDsl
val StrudelPattern.rarely by dslPatternExtension { pattern, args -> applySometimesBy(pattern, args, 0.25) }

/** Shorthand for `.sometimesBy(0.25, fn)` */
@StrudelDsl
val String.rarely by dslStringExtension { pattern, args -> applySometimesBy(pattern, args, 0.25) }

// -- almostNever() ----------------------------------------------------------------------------------------------------

/** Shorthand for `.sometimesBy(0.1, fn)` */
@StrudelDsl
val StrudelPattern.almostNever by dslPatternExtension { pattern, args -> applySometimesBy(pattern, args, 0.1) }

/** Shorthand for `.sometimesBy(0.1, fn)` */
@StrudelDsl
val String.almostNever by dslStringExtension { pattern, args -> applySometimesBy(pattern, args, 0.1) }

// -- almostAlways() ---------------------------------------------------------------------------------------------------

/** Shorthand for `.sometimesBy(0.9, fn)` */
@StrudelDsl
val StrudelPattern.almostAlways by dslPatternExtension { pattern, args -> applySometimesBy(pattern, args, 0.9) }

/** Shorthand for `.sometimesBy(0.9, fn)` */
@StrudelDsl
val String.almostAlways by dslStringExtension { pattern, args -> applySometimesBy(pattern, args, 0.9) }

// -- never() ----------------------------------------------------------------------------------------------------------

/** Shorthand for `.sometimesBy(0, fn)` (never calls fn) */
@StrudelDsl
val StrudelPattern.never by dslPatternExtension { pattern, _ -> pattern }

/** Shorthand for `.sometimesBy(0, fn)` (never calls fn) */
@StrudelDsl
val String.never by dslStringExtension { pattern, _ -> note(pattern) }

// -- always() ---------------------------------------------------------------------------------------------------------

private fun applyAlways(pattern: StrudelPattern, args: List<Any?>): StrudelPattern {
    @Suppress("UNCHECKED_CAST")
    val func = args.firstNotNullOfOrNull { it as? (StrudelPattern) -> StrudelPattern }
    return func?.invoke(pattern) ?: pattern
}

/** Shorthand for `.sometimesBy(1, fn)` (always calls fn) */
@StrudelDsl
val StrudelPattern.always by dslPatternExtension { pattern, args -> applyAlways(pattern, args) }

/** Shorthand for `.sometimesBy(1, fn)` (always calls fn) */
@StrudelDsl
val String.always by dslStringExtension { pattern, args -> applyAlways(note(pattern), args) }

// -- someCyclesBy() ---------------------------------------------------------------------------------------------------

// TODO: Implement someCyclesBy when we have logic for per-cycle randomness
// For now, we can alias it to sometimesBy but it's not semantically correct.
// Or we can implement it using ContextModifierPattern that sets a seeded random based on cycle number?

private fun applySomeCyclesBy(pattern: StrudelPattern, args: List<Any?>, defaultProb: Double? = null): StrudelPattern {
    // Delegate to applySometimesBy with seedByCycle = true
    return applySometimesBy(
        pattern = pattern,
        args = args,
        defaultProb = defaultProb,
        seedByCycle = true,
    )
}

/**
 * Randomly applies the given function by the given probability on a cycle by cycle basis.
 *
 * @param {number} probability - a number between 0 and 1
 * @param {function} function - the transformation to apply
 */
@StrudelDsl
val StrudelPattern.someCyclesBy by dslPatternExtension { pattern, args -> applySomeCyclesBy(pattern, args) }

@StrudelDsl
val String.someCyclesBy by dslStringExtension { pattern, args -> applySomeCyclesBy(pattern, args) }

// -- someCycles() -----------------------------------------------------------------------------------------------------

/** Shorthand for `.someCyclesBy(0.5, fn)` */
@StrudelDsl
val StrudelPattern.someCycles by dslPatternExtension { pattern, args -> applySomeCyclesBy(pattern, args, 0.5) }

@StrudelDsl
val String.someCycles by dslStringExtension { pattern, args -> applySomeCyclesBy(pattern, args, 0.5) }

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
val randL by dslFunction { args ->
    val n = args.getOrNull(0)?.asIntOrNull() ?: 0

    if (n < 1) {
        silence
    } else {
        val atom = AtomicPattern.pure

        val events = (0..<n).map {
            atom.reinterpret { evt, ctx ->

                val fraction = evt.begin - evt.begin.floor()
                val seed = (fraction * n * 10).toInt()

                val random = ctx.getSeededRandom(seed, it, "randL")
                val value = random.nextInt(0, 8).asVoiceValue()
                evt.copy(data = evt.data.copy(value = value))
            }
        }

        SequencePattern(events)
    }
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
val randrun by dslFunction { args ->
    val n = args.getOrNull(0)?.asIntOrNull() ?: 0

    if (n < 1) {
        silence
    } else {
        val atom = AtomicPattern.pure

        val events = (0..<n).map { index ->
            atom.reinterpret { evt, ctx ->
                // Make sure we have a seeded random and not the default one
                val ctx = ctx.update {
                    setIfAbsent(QueryContext.randomSeed, 0)
                }

                val cycle = evt.begin.floor()
                val random = ctx.getSeededRandom(cycle, "randrun")

                val permutation = (0 until n).toMutableList()
                permutation.shuffle(random)

                val value = permutation[index].asVoiceValue()
                evt.copy(data = evt.data.copy(value = value))
            }
        }

        SequencePattern(events)
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
val StrudelPattern.shuffle by dslPatternExtension { p, args ->
    val n = args.getOrNull(0)?.asIntOrNull() ?: 4
    val indices = randrun(n)
    p.bite(n, indices)
}

@StrudelDsl
val String.shuffle by dslStringExtension { p, args ->
    val n = args.getOrNull(0)?.asIntOrNull() ?: 4
    val indices = randrun(n)
    p.bite(n, indices)
}

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
val StrudelPattern.scramble by dslPatternExtension { p, args ->
    val n = args.getOrNull(0)?.asIntOrNull() ?: 4
    val indices = irand(n).segment(n)
    p.bite(n, indices)
}

@StrudelDsl
val String.scramble by dslStringExtension { p, args ->
    val n = args.getOrNull(0)?.asIntOrNull() ?: 4
    val indices = irand(n).segment(n)
    p.bite(n, indices)
}

// TODO: see signals.mjs: chooseInWith, choose, ...
