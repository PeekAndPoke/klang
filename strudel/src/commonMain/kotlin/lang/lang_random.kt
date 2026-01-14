package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.pattern.ContextModifierPattern
import io.peekandpoke.klang.strudel.pattern.ContinuousPattern
import io.peekandpoke.klang.strudel.pattern.DegradePattern.Companion.applyDegradeBy
import io.peekandpoke.klang.strudel.pattern.DegradePattern.Companion.applyUndegradeBy
import io.peekandpoke.klang.strudel.pattern.DegradePatternWithControl
import io.peekandpoke.klang.strudel.pattern.StackPattern

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangRandomInit = false

// -- Random -----------------------------------------------------------------------------------------------------------

private fun applyRandom(pattern: StrudelPattern, args: List<Any?>): ContextModifierPattern {
    val seed = args.getOrNull(0)?.asLongOrNull() ?: 0L

    return ContextModifierPattern(source = pattern) {
        set(StrudelPattern.QueryContext.randomSeed, seed)
    }
}

@StrudelDsl
val StrudelPattern.seed by dslPatternExtension { pattern, args -> applyRandom(pattern, args) }

@StrudelDsl
val String.seed by dslStringExtension { pattern, args -> applyRandom(pattern, args) }

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
val irand by dslObject {
    // TODO ... see signal.mjs
    silence
}

// -- degradeBy() ------------------------------------------------------------------------------------------------------

private fun applyDegradeBy(pattern: StrudelPattern, args: List<Any?>): StrudelPattern {
    return when (val first = args.getOrNull(0)) {
        is Number -> pattern.applyDegradeBy(first.toDouble())
        is StrudelPattern -> DegradePatternWithControl(pattern, first)
        is String -> DegradePatternWithControl(pattern, seq(first))
        else -> pattern.applyDegradeBy(0.5)
    }
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
    return when (val first = args.getOrNull(0)) {
        is Number -> pattern.applyUndegradeBy(first.toDouble())
        is StrudelPattern -> DegradePatternWithControl(pattern, first, inverted = true)
        is String -> DegradePatternWithControl(pattern, seq(first), inverted = true)
        else -> pattern.applyUndegradeBy(0.5)
    }
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
private fun applySometimesBy(pattern: StrudelPattern, args: List<Any?>, defaultProb: Double? = null): StrudelPattern {
    var probability = defaultProb
    var func: ((StrudelPattern) -> StrudelPattern)? = null

    // Parse arguments
    // Cases:
    // 1. sometimesBy(0.5, { ... }) -> args[0] = 0.5, args[1] = func
    // 2. sometimes({ ... }) -> args[0] = func (defaultProb set)
    // 3. sometimesBy({ ... }) -> args[0] = func (defaultProb = 0.5 default)

    for (arg in args) {
        when (arg) {
            is Number -> if (probability == null) probability = arg.toDouble()
            is Function1<*, *> -> {
                if (func == null) {
                    @Suppress("UNCHECKED_CAST")
                    func = arg as? (StrudelPattern) -> StrudelPattern
                }
            }
        }
    }

    val finalProb = probability ?: 0.5
    if (func == null) return pattern

    // Part A: Kept unmodified (probability of removal = finalProb, so kept = 1-finalProb)
    // Wait. If sometimesBy(0.1) -> 10% applied. 90% kept.
    // degradeBy(0.1) -> removes 10%. Keeps 90%. -> CORRECT.
    val partA = pattern.applyDegradeBy(finalProb)

    // Part B: Modified (probability of keeping = finalProb)
    // undegradeBy(1 - 0.1) = undegradeBy(0.9).
    // undegradeBy(0.9) keeps if r <= 0.1. (10% chance). -> CORRECT.
    val partB = func(pattern.applyUndegradeBy(1.0 - finalProb))

    return StackPattern(listOf(partA, partB))
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

private fun applySomeCyclesBy(
    pattern: StrudelPattern,
    args: List<Any?>,
    defaultProb: Double? = null,
): StrudelPattern {
    // THIS IS A PLACEHOLDER.
    // Ideally this should use cycle-based randomness.
    // For now falling back to sometimesBy to allow compilation/basic usage,
    // but noting it needs proper cycle-locking.
    return applySometimesBy(pattern, args, defaultProb)
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
