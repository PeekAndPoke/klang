package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Silent pattern with a specific steps value.
 */
internal class GapPattern(
    stepsValue: Rational,
) : StrudelPattern.FixedWeight {

    override val numSteps: Rational = stepsValue
    override val weight: Double = stepsValue.toDouble()

    override fun estimateCycleDuration(): Rational = Rational.ONE

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> =
        emptyList()
}
