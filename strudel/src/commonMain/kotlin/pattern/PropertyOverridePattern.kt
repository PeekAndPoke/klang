package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Generic pattern that overrides specific properties while delegating everything else to source.
 *
 * This eliminates the need for multiple specific wrapper classes that just override
 * one property (like WeightedPattern, StepsOverridePattern, etc.)
 *
 * @param source The source pattern to wrap
 * @param weightOverride Optional weight to override (null = use source weight)
 * @param stepsOverride Optional steps to override (null = use source steps)
 * @param cycleDurationOverride Optional cycle duration to override (null = use source estimate)
 */
internal class PropertyOverridePattern(
    private val source: StrudelPattern,
    private val weightOverride: Double? = null,
    private val stepsOverride: Rational? = null,
    private val cycleDurationOverride: Rational? = null,
) : StrudelPattern {

    override val weight: Double
        get() = weightOverride ?: source.weight

    override val numSteps: Rational?
        get() = stepsOverride ?: source.numSteps

    override fun estimateCycleDuration(): Rational =
        cycleDurationOverride ?: source.estimateCycleDuration()

    override fun queryArcContextual(
        from: Rational,
        to: Rational,
        ctx: QueryContext,
    ): List<StrudelPatternEvent> {
        return source.queryArcContextual(from, to, ctx)
    }
}
