package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Wraps a pattern and remaps ContinuousPattern range values (min/max) in the QueryContext.
 */
internal class ContextRangeMapPattern(
    private val source: StrudelPattern,
    private val transformMin: (Double) -> Double,
    private val transformMax: (Double) -> Double,
) : StrudelPattern {
    override val weight: Double get() = source.weight
    override val steps: Rational? get() = source.steps
    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val min = ctx.getOrNull(ContinuousPattern.minKey)
        val max = ctx.getOrNull(ContinuousPattern.maxKey)

        val newCtx = if (min != null && max != null) {
            ctx.update {
                set(ContinuousPattern.minKey, transformMin(min))
                set(ContinuousPattern.maxKey, transformMax(max))
            }
        } else {
            ctx
        }

        return source.queryArcContextual(from, to, newCtx)
    }
}
