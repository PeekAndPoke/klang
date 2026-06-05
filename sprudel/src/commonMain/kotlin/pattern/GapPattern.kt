package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime

import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent

/**
 * Silent pattern with a specific steps value.
 */
internal class GapPattern(
    stepsValue: Double,
) : SprudelPattern.FixedWeight {

    override val numSteps: Double = stepsValue
    override val weight: Double = stepsValue

    override fun estimateCycleDuration(): Double = 1.0

    override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> =
        emptyList()
}
