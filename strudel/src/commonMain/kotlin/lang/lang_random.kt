package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.pattern.ContextModifierPattern
import io.peekandpoke.klang.strudel.pattern.ContinuousPattern
import io.peekandpoke.klang.strudel.pattern.DegradePattern.Companion.applyDegradeBy
import io.peekandpoke.klang.strudel.pattern.DegradePattern.Companion.applyUndegradeBy
import io.peekandpoke.klang.strudel.pattern.DegradePatternWithControl
import kotlin.random.Random

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangRandomInit = false

// -- Random -----------------------------------------------------------------------------------------------------------

private fun applyRandom(pattern: StrudelPattern, args: List<Any?>): ContextModifierPattern {
    val seed = args.getOrNull(0)?.asIntOrNull() ?: 0

    return ContextModifierPattern(source = pattern) {
        set(StrudelPattern.QueryContext.random, Random(seed))
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
