package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime

import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent

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
    private val source: SprudelPattern,
    private val weightOverride: Double? = null,
    private val stepsOverride: Double? = null,
    private val cycleDurationOverride: Double? = null,
) : SprudelPattern {

    override val weight: Double
        get() = weightOverride ?: source.weight

    override val numSteps: Double?
        get() = stepsOverride ?: source.numSteps

    override fun estimateCycleDuration(): Double =
        cycleDurationOverride ?: source.estimateCycleDuration()

    override fun queryArcContextual(
        from: CycleTime,
        to: CycleTime,
        ctx: QueryContext,
    ): List<SprudelPatternEvent> {
        return source.queryArcContextual(from, to, ctx)
    }
}
