package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.StrudelPatternEvent

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
