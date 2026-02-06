package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.lcm

/**
 * Stack Pattern: Plays multiple patterns simultaneously.
 * Implementation of `stack(a, b)`.
 */
internal class StackPattern(val patterns: List<StrudelPattern>) : StrudelPattern.FixedWeight {

    override val numSteps: Rational?
        get() {
            val allSteps = patterns.mapNotNull { it.numSteps?.toInt() }
            if (allSteps.isEmpty()) return null
            return lcm(allSteps).takeIf { it > 0 }?.let { Rational(it) }
        }

    override fun estimateCycleDuration(): Rational {
        return patterns.maxOfOrNull { it.estimateCycleDuration() } ?: Rational.ONE
    }

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        return patterns
            .flatMap { it.queryArcContextual(from, to, ctx) }
            .sortedBy { it.part.begin } // Sort them to keep order nice (optional but good for debugging)
    }
}
