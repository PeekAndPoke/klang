package io.peekandpoke.klang.strudel.pattern

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.math.Rational

internal class DegradePattern(
    val inner: StrudelPattern,
    val probability: Double = 0.5,
    val inverted: Boolean = false,
) : StrudelPattern {
    companion object {
        fun StrudelPattern.applyDegradeBy(probability: Double): StrudelPattern =
            DegradePattern(this, probability)

        fun StrudelPattern.applyUndegradeBy(probability: Double): StrudelPattern =
            DegradePattern(this, probability, inverted = true)
    }

    override val weight: Double get() = inner.weight

    override fun queryArcContextual(from: Rational, to: Rational, ctx: QueryContext): List<StrudelPatternEvent> {
        val random = ctx.getSeededRandom(from, "DegradePattern")
        val threshold = if (inverted) 1.0 - probability else probability

        return inner.queryArcContextual(from, to, ctx).filter {
            if (inverted) {
                random.nextDouble() <= threshold
            } else {
                random.nextDouble() > threshold
            }
        }
    }
}
