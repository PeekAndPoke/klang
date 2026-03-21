package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent

/**
 * Wraps a pattern and remaps ContinuousPattern range values (min/max) in the QueryContext.
 */
internal class ContextRangeMapPattern(
    private val source: SprudelPattern,
    private val transformMin: (Double) -> Double,
    private val transformMax: (Double) -> Double,
) : SprudelPattern {
    override val weight: Double get() = source.weight
    override val numSteps: Rational? get() = source.numSteps
    override fun estimateCycleDuration(): Rational = source.estimateCycleDuration()

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent> {
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
