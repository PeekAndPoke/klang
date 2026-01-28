package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Generic pattern that applies a transformation to events after querying the source.
 *
 * This is a reusable wrapper for simple event transformations (filtering, mapping, etc.)
 * that preserves the pattern's structure and delegates all properties to the source.
 *
 * This eliminates the need for specific pattern classes that just apply simple transformations.
 *
 * @param source The source pattern to query
 * @param transform Function that transforms the list of events after querying
 */
internal class MapPattern(
    private val source: StrudelPattern,
    private val transform: (List<StrudelPatternEvent>) -> List<StrudelPatternEvent>,
) : StrudelPattern {

    override val weight: Double get() = source.weight
    override val steps: Rational? get() = source.steps
    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
    ): List<StrudelPatternEvent> {
        return transform(source.queryArcContextual(from, to, ctx))
    }
}