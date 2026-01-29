package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.bind
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Generic pattern that applies bind (inner join) operation.
 *
 * This is a reusable wrapper for any pattern transformation that follows the bind pattern:
 * query an outer pattern, and for each event, generate an inner pattern that gets queried
 * and clipped to the outer event's boundaries.
 *
 * This eliminates the need for many specific pattern classes that just delegate to bind().
 *
 * @param outer The outer pattern that defines the structure
 * @param transform Function that generates an inner pattern from each outer event
 */
internal class BindPattern(
    private val outer: StrudelPattern,
    private val transform: (StrudelPatternEvent) -> StrudelPattern?,
) : StrudelPattern {

    override val weight: Double get() = outer.weight
    override val numSteps: Rational? get() = outer.numSteps
    override fun estimateCycleDuration(): Rational = outer.estimateCycleDuration()

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: StrudelPattern.QueryContext,
    ): List<StrudelPatternEvent> {
        return outer.bind(from, to, ctx, transform)
    }
}
