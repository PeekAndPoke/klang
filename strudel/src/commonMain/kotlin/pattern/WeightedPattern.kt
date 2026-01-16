package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Weighted Pattern: Wraps a pattern with a non-default weight for proportional time distribution.
 * Used by the mini-notation parser for @ operator (e.g., "bd@3" creates WeightedPattern with weight 3.0).
 */
internal class WeightedPattern(
    private val inner: StrudelPattern,
    override val weight: Double,
) : StrudelPattern {
    override val steps: Rational? get() = inner.steps

    override fun estimateCycleDuration(): Rational = inner.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        return inner.queryArcContextual(from, to, ctx)
    }
}
