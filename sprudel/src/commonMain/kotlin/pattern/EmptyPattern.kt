package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.CycleTime

import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.lang.silence

/**
 * Empty pattern, f.e. for [silence]
 */
object EmptyPattern : SprudelPattern.FixedWeight {
    override val numSteps: Double = 1.0

    override fun estimateCycleDuration(): Double = 1.0

    override fun queryArcContextual(from: CycleTime, to: CycleTime, ctx: QueryContext): List<SprudelPatternEvent> {
        return emptyList()
    }
}
