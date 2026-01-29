package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.lang.late
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Layers a modified version of the pattern on top of itself, shifted in time.
 *
 * Unlike a simple stack with time shifting, this pattern is cycle-aware:
 * it only includes delayed events that originate from the same cycle as the query range,
 * preventing events from previous or future cycles from bleeding in.
 *
 * @param source The source pattern
 * @param time The time offset in cycles (positive = delay)
 * @param transform Function to apply to the delayed layer
 */
internal class OffPattern(
    val source: StrudelPattern,
    val time: Double,
    val transform: (StrudelPattern) -> StrudelPattern,
) : StrudelPattern {

    override val weight: Double get() = source.weight

    override val numSteps: Rational? get() = source.numSteps

    private val transformed = transform(source.late(time))

    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        return source.queryArcContextual(from, to, ctx) +
                transformed.queryArcContextual(from, to, ctx).filter {
                    it.begin >= from
                }
    }
}
