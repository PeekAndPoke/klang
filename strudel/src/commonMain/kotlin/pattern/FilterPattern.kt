package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Filters events from a source pattern using a predicate.
 */
internal class FilterPattern(
    private val source: StrudelPattern,
    private val predicate: (StrudelPatternEvent) -> Boolean,
) : StrudelPattern {

    override val weight: Double get() = source.weight
    override val steps: Rational? get() = source.steps
    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
    ): List<StrudelPatternEvent> {
        return source.queryArcContextual(from, to, ctx).filter(predicate)
    }
}
