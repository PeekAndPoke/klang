package io.peekandpoke.klang.sprudel.pattern

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent

/**
 * Silent pattern with a specific steps value.
 */
internal class GapPattern(
    stepsValue: Rational,
) : SprudelPattern.FixedWeight {

    override val numSteps: Rational = stepsValue
    override val weight: Double = stepsValue.toDouble()

    override fun estimateCycleDuration(): Rational = Rational.ONE

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<SprudelPatternEvent> =
        emptyList()
}
