package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.pattern.ContextModifierPattern
import io.peekandpoke.klang.strudel.pattern.ContinuousPattern
import io.peekandpoke.klang.strudel.pattern.DegradePattern.Companion.degradeBy
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
        is Number -> pattern.degradeBy(first.toDouble())
        is StrudelPattern -> DegradePatternWithControl(pattern, first)
        is String -> DegradePatternWithControl(pattern, seq(first))
        else -> pattern.degradeBy(0.5)
    }
}

/**
 * Randomly removes events from the pattern with the given probability.
 *
 * @name degradeBy
 * @param {number} probability - a number between 0 and 1. 0 means no events are removed, 1 means all events are removed.
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
val String.degrade by dslStringExtension { pattern, _ ->
    note(pattern).degradeBy(0.5)
}
