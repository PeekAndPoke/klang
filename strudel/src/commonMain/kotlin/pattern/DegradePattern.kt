package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

internal class DegradePattern(
    val inner: StrudelPattern,
    val probability: Double = 0.5,
) : StrudelPattern {
    companion object {
        fun StrudelPattern.degradeBy(probability: Double): StrudelPattern =
            DegradePattern(this, probability)
    }

    override val weight: Double get() = inner.weight

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val random = ctx.getSeededRandom(from, "DegradePattern")

        return inner.queryArcContextual(from, to, ctx).filter {
            random.nextDouble() > probability
        }
    }
}
