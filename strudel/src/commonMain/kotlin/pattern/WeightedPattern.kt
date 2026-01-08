package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
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
    override fun queryArc(from: Rational, to: Rational): List<StrudelPatternEvent> {
        return inner.queryArc(from, to)
    }
}
